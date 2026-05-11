package edu.hitsz.game;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import androidx.annotation.NonNull;

import java.util.*;

import edu.hitsz.audio.SoundManager;
import edu.hitsz.db.ScoreRepository;
import edu.hitsz.factory.EnemyFactory;
import edu.hitsz.model.*;
import edu.hitsz.model.bullet.*;
import edu.hitsz.model.enemy.*;
import edu.hitsz.model.prop.*;
import edu.hitsz.R;

/**
 * 游戏主视图 —— SurfaceView + Canvas + Bitmap 实现
 *
 * 迁移对应关系（替代原Windows版）：
 *   BaseGame/JPanel     → GameSurfaceView（SurfaceView子类）
 *   HeroController      → onTouchEvent()（触屏输入，volatile传递给游戏线程）
 *   ImageIO.read()      → BitmapFactory.decodeResource()（图片放res/drawable）
 *   JFrame/Swing定时器  → SurfaceHolder.Callback + Runnable（手动游戏循环）
 *
 * 线程架构：
 *   ① 游戏循环线程（Runnable.run()）：逻辑 + Canvas渲染，60fps
 *   ② 音频线程（SoundManager内部）：SoundPool + MediaPlayer
 *   ③ 数据库线程（ScoreRepository内部）：Room异步存档
 *
 * 线程安全：
 *   触屏输入由UI线程写入 volatile heroTargetX/Y，游戏线程只读
 */
public class GameSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    // ═══ 游戏状态枚举 ════════════════════════════════════════════════════
    private enum GameState { PLAYING, PAUSED, GAME_OVER }

    // ═══ 多线程控制 ══════════════════════════════════════════════════════
    private volatile boolean isRunning = false;
    private Thread gameThread;
    private volatile GameState gameState = GameState.PLAYING;

    // ═══ 屏幕参数 ════════════════════════════════════════════════════════
    private int screenW, screenH;
    private volatile boolean initialized = false;

    // ═══ 触屏输入（UI线程写 → 游戏线程读，volatile保证可见性） ═══════════
    // 替代原Windows版 HeroController 的 mouseMoved() 方法
    private volatile float heroTargetX, heroTargetY;

    // ═══ 游戏对象（仅游戏线程访问） ═════════════════════════════════════
    private HeroAircraft hero;
    private final List<AbstractEnemyAircraft> enemies      = new ArrayList<>();
    private final List<AbstractBullet>        heroBullets  = new ArrayList<>();
    private final List<AbstractBullet>        enemyBullets = new ArrayList<>();
    private final List<AbstractProp>          props        = new ArrayList<>();

    // ═══ 游戏状态数据 ════════════════════════════════════════════════════
    private int  score        = 0;
    private long nextBossScore = GameConstant.BOSS_SPAWN_SCORE_THRESHOLD;
    private long lastMobSpawn, lastEliteSpawn;
    private String difficulty = GameConstant.DIFFICULTY_EASY;

    // ═══ Bitmap资源（BitmapFactory替代Windows版 ImageIO.read()） ════════
    private Bitmap bgBmp, heroBmp, heroBulletBmp;
    private Bitmap mobBmp, eliteBmp, bossBmp, enemyBulletBmp;
    private Bitmap propBloodBmp, propBombBmp, propBulletBmp;
    private float bgY1 = 0, bgY2;

    // ═══ 绘制工具（Paint复用，避免GC） ══════════════════════════════════
    private final Paint paint    = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ═══ 外部依赖 ════════════════════════════════════════════════════════
    private SoundManager    soundManager;
    private ScoreRepository scoreRepository;

    // ═══ 游戏结束回调（主线程执行） ══════════════════════════════════════
    public interface GameOverCallback { void onGameOver(int score); }
    private GameOverCallback gameOverCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ═══════════════════════════════════════════════════════════════════
    // 构造器
    // ═══════════════════════════════════════════════════════════════════
    public GameSurfaceView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
        try { soundManager    = new SoundManager(context);    } catch (Exception e) { soundManager = null; }
        try { scoreRepository = new ScoreRepository(context); } catch (Exception e) { scoreRepository = null; }
        hudPaint.setTextSize(48);
        hudPaint.setColor(Color.WHITE);
        hudPaint.setTypeface(Typeface.DEFAULT_BOLD);
        hudPaint.setShadowLayer(4, 2, 2, Color.BLACK);
    }

    public void setGameOverCallback(GameOverCallback cb) { gameOverCallback = cb; }
    public void setDifficulty(String d) { difficulty = d; }
    public int  getScore()      { return score; }
    public boolean isGameOver() { return gameState == GameState.GAME_OVER; }
    public void pause()         { gameState = GameState.PAUSED;  if (soundManager != null) try { soundManager.pauseBgm();  } catch (Exception ignored) {} }
    public void resumeGame()    { gameState = GameState.PLAYING; if (soundManager != null) try { soundManager.resumeBgm(); } catch (Exception ignored) {} }
    public void onDestroyView() { isRunning = false; if (soundManager != null) try { soundManager.release(); } catch (Exception ignored) {} }

    // ═══════════════════════════════════════════════════════════════════
    // SurfaceHolder.Callback — 替代原 BaseGame surfaceCreated/Destroyed
    // ═══════════════════════════════════════════════════════════════════
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        isRunning = true;
        gameThread = new Thread(this, "GameThread");
        gameThread.start();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int w, int h) {
        screenW = w; screenH = h;
        if (!initialized) {
            loadBitmaps();   // BitmapFactory加载（替代 ImageIO.read()）
            initGame();
            initialized = true;
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        isRunning = false;
        try { if (gameThread != null) gameThread.join(500); } catch (InterruptedException ignored) {}
        if (soundManager != null) try { soundManager.release(); } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════
    // 图片加载（BitmapFactory + 资源ID，替代Windows版 ImageIO.read()）
    // ═══════════════════════════════════════════════════════════════════
    private void loadBitmaps() {
        int hw = screenW / 7;
        heroBmp       = scale(R.drawable.hero,         hw,         hw);
        heroBulletBmp = scale(R.drawable.bullet_hero,  screenW/30, screenW/15);
        mobBmp        = scale(R.drawable.mob,           screenW/9,  screenW/9);
        eliteBmp      = scale(R.drawable.elite,         screenW/8,  screenW/8);
        bossBmp       = scale(R.drawable.boss,          screenW/3,  screenW/3);
        enemyBulletBmp= scale(R.drawable.bullet_enemy, screenW/30, screenW/15);
        propBloodBmp  = scale(R.drawable.prop_blood,   screenW/10, screenW/10);
        propBombBmp   = scale(R.drawable.prop_bomb,    screenW/10, screenW/10);
        propBulletBmp = scale(R.drawable.prop_bullet,  screenW/10, screenW/10);
        bgBmp         = scale(R.drawable.bg,            screenW,    screenH);
        bgY2 = -screenH;
    }

    private Bitmap scale(int resId, int w, int h) {
        try {
            Bitmap raw = BitmapFactory.decodeResource(getResources(), resId);
            if (raw == null) return null;
            return Bitmap.createScaledBitmap(raw, Math.max(1, w), Math.max(1, h), true);
        } catch (Exception e) { return null; }
    }

    private void initGame() {
        score = 0; nextBossScore = GameConstant.BOSS_SPAWN_SCORE_THRESHOLD;
        lastMobSpawn = lastEliteSpawn = System.currentTimeMillis();
        hero = new HeroAircraft(screenW / 2, screenH * 3 / 4,
                heroBmp, heroBulletBmp, GameConstant.HERO_HP);
        heroTargetX = screenW / 2f;
        heroTargetY = screenH * 3 / 4f;
        enemies.clear(); heroBullets.clear(); enemyBullets.clear(); props.clear();
        gameState = GameState.PLAYING;
        if (soundManager != null) try { soundManager.startBgm(); } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════
    // 游戏循环线程（Runnable.run）— 替代原 BaseGame Swing Timer
    // ═══════════════════════════════════════════════════════════════════
    @Override
    public void run() {
        while (isRunning) {
            long now = System.currentTimeMillis();
            if (gameState == GameState.PLAYING && initialized) {
                update(now);
                draw();
            }
            long elapsed = System.currentTimeMillis() - now;
            long sleep = GameConstant.TARGET_FRAME_MS - elapsed;
            if (sleep > 0) try { Thread.sleep(sleep); } catch (InterruptedException e) { break; }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 游戏逻辑更新（游戏线程）
    // ═══════════════════════════════════════════════════════════════════
    private void update(long now) {
        // 1. 英雄跟随触屏（替代原 HeroController 鼠标控制）
        if (hero.isValid()) {
            hero.setTouchTarget(heroTargetX, heroTargetY);
            hero.forward();
            List<HeroBullet> nb = hero.shoot(now);
            if (nb != null && !nb.isEmpty()) {
                heroBullets.addAll(nb);
                if (soundManager != null) try { soundManager.playShoot(); } catch (Exception ignored) {}
            }
        }

        // 2. 敌机生成
        spawnEnemies(now);

        // 3. 敌机移动 + 射击（BossEnemy.forward()内部处理边界反弹）
        for (AbstractEnemyAircraft e : enemies) {
            if (!e.isValid()) continue;
            e.forward();
            if (e.isOutOfScreen(screenH)) { e.vanish(); continue; }
            try {
                List<EnemyBullet> eb = e.shoot(now, enemyBulletBmp, screenW);
                if (eb != null) enemyBullets.addAll(eb);
            } catch (Exception ignored) {}
        }

        // 4. 子弹移动 + 越界消亡
        for (AbstractBullet b : heroBullets) {
            if (!b.isValid()) continue;
            b.forward();
            if (b.getLocationY() < -60) b.vanish();
        }
        for (AbstractBullet b : enemyBullets) {
            if (!b.isValid()) continue;
            b.forward();
            if (b.getLocationY() > screenH + 60) b.vanish();
        }

        // 5. 道具移动 + 越界消亡
        for (AbstractProp p : props) {
            if (!p.isValid()) continue;
            p.forward();
            if (p.isOutOfScreen(screenH)) p.vanish();
        }

        // 6. 碰撞检测（复用自原Windows版矩形碰撞算法）
        checkCollisions(now);

        // 7. 背景双缓冲滚动
        bgY1 += 3; bgY2 += 3;
        if (bgY1 >= screenH) bgY1 = bgY2 - screenH;
        if (bgY2 >= screenH) bgY2 = bgY1 - screenH;

        // 8. 清理无效对象（每帧直接remove，列表规模有限）
        enemies.removeIf(e -> !e.isValid());
        heroBullets.removeIf(b -> !b.isValid());
        enemyBullets.removeIf(b -> !b.isValid());
        props.removeIf(p -> !p.isValid());

        // 9. 检测游戏结束
        if (!hero.isValid()) triggerGameOver();
    }

    private void spawnEnemies(long now) {
        if (now - lastMobSpawn >= GameConstant.MOB_SPAWN_INTERVAL) {
            lastMobSpawn = now;
            enemies.add(EnemyFactory.createMob(screenW, mobBmp));
        }
        if (now - lastEliteSpawn >= GameConstant.ELITE_SPAWN_INTERVAL) {
            lastEliteSpawn = now;
            enemies.add(EnemyFactory.createElite(screenW, eliteBmp));
        }
        if (score >= nextBossScore) {
            boolean hasBoss = false;
            for (AbstractEnemyAircraft e : enemies)
                if (e instanceof BossEnemy && e.isValid()) { hasBoss = true; break; }
            if (!hasBoss) {
                enemies.add(EnemyFactory.createBoss(screenW, bossBmp));
                nextBossScore += GameConstant.BOSS_SPAWN_SCORE_THRESHOLD;
            }
        }
    }

    private void checkCollisions(long now) {
        // 英雄子弹 vs 敌机
        outer:
        for (AbstractBullet b : heroBullets) {
            if (!b.isValid()) continue;
            for (AbstractEnemyAircraft e : enemies) {
                if (!e.isValid()) continue;
                if (b.crash(e)) {
                    e.decreaseHp(GameConstant.HERO_BULLET_POWER);
                    b.vanish();
                    if (!e.isValid()) {
                        score += e.getScoreValue();
                        if (soundManager != null) try { soundManager.playEnemyDie(); } catch (Exception ignored) {}
                        AbstractProp p = (e instanceof BossEnemy)
                            ? EnemyFactory.createPropFromBoss(e.getLocationX(), e.getLocationY(),
                                propBloodBmp, propBombBmp, propBulletBmp)
                            : EnemyFactory.createPropIfDropped(e.getLocationX(), e.getLocationY(),
                                propBloodBmp, propBombBmp, propBulletBmp);
                        if (p != null) props.add(p);
                    }
                    continue outer;
                }
            }
        }
        // 敌机子弹 vs 英雄
        for (AbstractBullet b : enemyBullets) {
            if (b.isValid() && hero.isValid() && b.crash(hero)) {
                hero.decreaseHp(GameConstant.ENEMY_BULLET_POWER);
                b.vanish();
            }
        }
        // 敌机碰撞英雄
        for (AbstractEnemyAircraft e : enemies) {
            if (e.isValid() && hero.isValid() && e.crash(hero)) {
                hero.decreaseHp(e.getHp());
                e.vanish();
            }
        }
        // 道具 vs 英雄
        for (AbstractProp p : props) {
            if (!p.isValid() || !p.crash(hero)) continue;
            if (soundManager != null) try { soundManager.playPropGet(); } catch (Exception ignored) {}
            switch (p.getType()) {
                case BLOOD:
                    hero.heal(GameConstant.BLOOD_PROP_HEAL);
                    break;
                case BOMB:
                    if (soundManager != null) try { soundManager.playBomb(); } catch (Exception ignored) {}
                    for (AbstractEnemyAircraft e : enemies) { score += e.getScoreValue(); e.vanish(); }
                    for (AbstractBullet b : enemyBullets) b.vanish();
                    break;
                case BULLET:
                    hero.activateSpreadShoot(now);
                    break;
            }
            p.vanish();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Canvas渲染（游戏循环线程） — 替代原 paintComponent(Graphics g)
    // ═══════════════════════════════════════════════════════════════════
    private void draw() {
        Canvas canvas = getHolder().lockCanvas();
        if (canvas == null) return;
        try {
            // 双缓冲滚动背景
            if (bgBmp != null) {
                canvas.drawBitmap(bgBmp, 0, bgY1, paint);
                canvas.drawBitmap(bgBmp, 0, bgY2, paint);
            } else {
                canvas.drawColor(Color.rgb(10, 10, 30));
            }
            // 道具
            for (AbstractProp p : props) p.draw(canvas, paint);
            // 敌机
            for (AbstractEnemyAircraft e : enemies) e.draw(canvas, paint);
            // 子弹
            for (AbstractBullet b : heroBullets)  b.draw(canvas, paint);
            for (AbstractBullet b : enemyBullets) b.draw(canvas, paint);
            // 英雄
            if (hero.isValid()) hero.draw(canvas, paint);
            // HUD
            drawHUD(canvas);
        } finally {
            getHolder().unlockCanvasAndPost(canvas);
        }
    }

    private void drawHUD(Canvas canvas) {
        // 分数
        hudPaint.setTextSize(52);
        canvas.drawText("SCORE " + score, 30, 72, hudPaint);

        // 英雄HP条
        int barW = screenW / 2, barH = 22;
        int barX = (screenW - barW) / 2, barY = screenH - 52;
        paint.setColor(Color.argb(160, 50, 50, 50));
        canvas.drawRoundRect(barX, barY, barX + barW, barY + barH, 8, 8, paint);
        float hpRatio = Math.max(0, Math.min(1f, (float) hero.getHp() / hero.getMaxHp()));
        int hpColor = hpRatio > 0.5f ? Color.GREEN : (hpRatio > 0.25f ? Color.YELLOW : Color.RED);
        paint.setColor(hpColor);
        canvas.drawRoundRect(barX, barY, barX + barW * hpRatio, barY + barH, 8, 8, paint);
        hudPaint.setTextSize(28);
        canvas.drawText("HP " + hero.getHp(), barX + 6, barY + 17, hudPaint);

        // Boss HP条
        for (AbstractEnemyAircraft e : enemies) {
            if (e instanceof BossEnemy && e.isValid()) {
                paint.setColor(Color.argb(160, 50, 50, 50));
                canvas.drawRoundRect(barX, 18, barX + barW, 40, 8, 8, paint);
                float bRatio = Math.max(0, (float) e.getHp() / e.getMaxHp());
                paint.setColor(Color.rgb(220, 30, 30));
                canvas.drawRoundRect(barX, 18, barX + barW * bRatio, 40, 8, 8, paint);
                hudPaint.setTextSize(24);
                canvas.drawText("BOSS HP " + e.getHp(), barX + 6, 36, hudPaint);
                break;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 触屏事件（UI线程）— 替代原 HeroController 鼠标输入
    // ═══════════════════════════════════════════════════════════════════
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            heroTargetX = e.getX();
            heroTargetY = e.getY();
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 游戏结束处理
    // ═══════════════════════════════════════════════════════════════════
    private void triggerGameOver() {
        if (gameState == GameState.GAME_OVER) return;
        gameState = GameState.GAME_OVER;
        if (soundManager != null) try { soundManager.playHeroDie(); soundManager.pauseBgm(); } catch (Exception ignored) {}
        if (scoreRepository != null) try {
            scoreRepository.saveScore(GameConstant.DEFAULT_PLAYER_NAME, score, difficulty);
        } catch (Exception ignored) {}
        mainHandler.postDelayed(() -> { if (gameOverCallback != null) gameOverCallback.onGameOver(score); }, 800);
    }
}
