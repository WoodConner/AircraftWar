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
 * 游戏主视图 —— Android多线程架构
 *
 * 线程架构：
 *   ① 游戏循环线程（本类 Runnable）：逻辑更新 + Canvas渲染，60fps
 *   ② 音频线程（SoundManager内部 HandlerThread）：SoundPool+MediaPlayer
 *   ③ 数据库线程（ScoreRepository内 ExecutorService）：Room异步存档
 *   ④ 网络线程（Retrofit+OkHttp内置线程池）：在线排行榜/WebSocket
 *
 * 线程安全措施：
 *   - 触屏输入（UI线程）→ volatile heroTargetX/Y，游戏线程只读
 *   - 游戏对象列表仅由游戏循环线程修改，draw()与update()在同一线程
 *   - 游戏结束回调通过 Handler(MainLooper) 投递到主线程
 */
public class GameSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    // ===== 多线程控制 =====
    private volatile boolean isRunning = false;
    private Thread gameThread;
    private volatile GameState gameState = GameState.PLAYING;

    // ===== 屏幕参数 =====
    private int screenW, screenH;
    private volatile boolean initialized = false;

    // ===== 触屏输入（UI线程写入，游戏线程读取，volatile保证可见性） =====
    private volatile float heroTargetX, heroTargetY;

    // ===== 游戏对象（仅游戏线程访问，无需同步） =====
    private HeroAircraft hero;
    private final List<AbstractEnemyAircraft> enemies = new ArrayList<>();
    private final List<AbstractBullet> heroBullets = new ArrayList<>();
    private final List<AbstractBullet> enemyBullets = new ArrayList<>();
    private final List<AbstractProp> props = new ArrayList<>();

    // ===== 游戏状态 =====
    private int score = 0;
    private long nextBossScore = GameConstant.BOSS_SPAWN_SCORE_THRESHOLD;
    private long lastMobSpawn, lastEliteSpawn;

    // ===== Bitmap资源（在surfaceChanged中加载，screenW/H确定后） =====
    private Bitmap bgBmp, heroBmp, heroBulletBmp;
    private Bitmap mobBmp, eliteBmp, bossBmp, enemyBulletBmp;
    private Bitmap propBloodBmp, propBombBmp, propBulletBmp;
    private float bgY1 = 0, bgY2;

    // ===== 绘制工具 =====
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ===== 外部依赖（音频线程+数据库线程） =====
    private SoundManager soundManager;
    private ScoreRepository scoreRepository;

    // ===== 游戏结束回调（主线程） =====
    public interface GameOverCallback { void onGameOver(int score); }
    private GameOverCallback gameOverCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String difficulty = GameConstant.DIFFICULTY_EASY;

    public GameSurfaceView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
        // 初始化音频（HandlerThread异步，不阻塞UI）
        soundManager = new SoundManager(context);
        // 初始化数据库（ExecutorService异步）
        scoreRepository = new ScoreRepository(context);
        hudPaint.setTextSize(48);
        hudPaint.setColor(Color.WHITE);
        hudPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public void setGameOverCallback(GameOverCallback cb) { gameOverCallback = cb; }
    public void setDifficulty(String d) { difficulty = d; }

    // ===== SurfaceHolder.Callback =====

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
            loadBitmaps();
            initGame();
            initialized = true;
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        isRunning = false;
        try { if (gameThread != null) gameThread.join(500); } catch (InterruptedException ignored) {}
        soundManager.release();
    }

    /** 加载并缩放所有Bitmap（BitmapFactory替代Windows版ImageIO.read()） */
    private void loadBitmaps() {
        int hw = screenW / 7, hh = screenW / 7;
        heroBmp = scale(R.drawable.hero, hw, hh);
        heroBulletBmp = scale(R.drawable.bullet_hero, screenW / 30, screenW / 15);
        mobBmp = scale(R.drawable.mob, screenW / 9, screenW / 9);
        eliteBmp = scale(R.drawable.elite, screenW / 8, screenW / 8);
        bossBmp = scale(R.drawable.boss, screenW / 3, screenW / 3);
        enemyBulletBmp = scale(R.drawable.bullet_enemy, screenW / 30, screenW / 15);
        propBloodBmp = scale(R.drawable.prop_blood, screenW / 10, screenW / 10);
        propBombBmp = scale(R.drawable.prop_bomb, screenW / 10, screenW / 10);
        propBulletBmp = scale(R.drawable.prop_bullet, screenW / 10, screenW / 10);
        bgBmp = scale(R.drawable.bg, screenW, screenH);
        bgY2 = -screenH;
    }

    private Bitmap scale(int resId, int w, int h) {
        Bitmap raw = BitmapFactory.decodeResource(getResources(), resId);
        if (raw == null) return null;
        return Bitmap.createScaledBitmap(raw, Math.max(1, w), Math.max(1, h), true);
    }

    private void initGame() {
        score = 0; nextBossScore = GameConstant.BOSS_SPAWN_SCORE_THRESHOLD;
        lastMobSpawn = lastEliteSpawn = System.currentTimeMillis();
        hero = new HeroAircraft(screenW / 2, screenH * 3 / 4, heroBmp, heroBulletBmp, GameConstant.HERO_HP);
        heroTargetX = screenW / 2f; heroTargetY = screenH * 3 / 4f;
        enemies.clear(); heroBullets.clear(); enemyBullets.clear(); props.clear();
        gameState = GameState.PLAYING;
        soundManager.startBgm();
    }

    // ===== 游戏循环线程 Runnable =====

    @Override
    public void run() {
        long prev = System.currentTimeMillis();
        while (isRunning) {
            long now = System.currentTimeMillis();
            long delta = now - prev; prev = now;

            if (gameState == GameState.PLAYING && initialized) {
                update(now);
                draw();
            }

            long elapsed = System.currentTimeMillis() - now;
            long sleep = GameConstant.TARGET_FRAME_MS - elapsed;
            if (sleep > 0) try { Thread.sleep(sleep); } catch (InterruptedException e) { break; }
        }
    }

    // ===== 游戏逻辑更新（仅游戏线程调用） =====

    private void update(long now) {
        // 1. 英雄跟随触屏
        if (hero.isValid()) {
            hero.setTouchTarget(heroTargetX, heroTargetY);
            hero.forward();
            // 英雄自动开枪
            List<HeroBullet> newBullets = hero.shoot(now);
            if (!newBullets.isEmpty()) { heroBullets.addAll(newBullets); soundManager.playShoot(); }
        }

        // 2. 生成敌机
        spawnEnemies(now);

        // 3. 移动所有敌机
        for (AbstractEnemyAircraft e : enemies) {
            e.forward();
            if (e instanceof BossEnemy) ((BossEnemy) e).checkBounds(screenW);
            if (e.isOutOfScreen(screenH)) e.vanish();
            // 敌机射击
            List<EnemyBullet> eb = e.shoot(now, enemyBulletBmp, screenW);
            enemyBullets.addAll(eb);
        }

        // 4. 移动子弹和道具
        for (AbstractBullet b : heroBullets) b.forward(screenW, screenH);
        for (AbstractBullet b : enemyBullets) b.forward(screenW, screenH);
        for (AbstractProp p : props) { p.forward(); if (p.isOutOfScreen(screenH)) p.vanish(); }

        // 5. 碰撞检测（复用自原Windows版矩形碰撞算法）
        checkCollisions(now);

        // 6. 滚动背景
        bgY1 += 3; bgY2 += 3;
        if (bgY1 >= screenH) bgY1 = bgY2 - screenH;
        if (bgY2 >= screenH) bgY2 = bgY1 - screenH;

        // 7. 清理无效对象
        enemies.removeIf(e -> !e.isValid());
        heroBullets.removeIf(b -> !b.isValid());
        enemyBullets.removeIf(b -> !b.isValid());
        props.removeIf(p -> !p.isValid());

        // 8. 检测游戏结束
        if (!hero.isValid()) triggerGameOver();
    }

    private void spawnEnemies(long now) {
        if (now - lastMobSpawn >= GameConstant.MOB_SPAWN_INTERVAL && mobBmp != null) {
            enemies.add(EnemyFactory.createMob(screenW, mobBmp));
            lastMobSpawn = now;
        }
        if (now - lastEliteSpawn >= GameConstant.ELITE_SPAWN_INTERVAL && eliteBmp != null) {
            enemies.add(EnemyFactory.createElite(screenW, eliteBmp));
            lastEliteSpawn = now;
        }
        if (score >= nextBossScore && bossBmp != null) {
            enemies.add(EnemyFactory.createBoss(screenW, bossBmp));
            nextBossScore += GameConstant.BOSS_SPAWN_SCORE_THRESHOLD;
        }
    }

    private void checkCollisions(long now) {
        // 英雄子弹 vs 敌机
        for (AbstractBullet b : heroBullets) {
            if (!b.isValid()) continue;
            for (AbstractEnemyAircraft e : enemies) {
                if (!e.isValid() || !b.crash(e)) continue;
                e.decreaseHp(b.getPower()); b.vanish();
                if (!e.isValid()) {
                    score += e.getScoreValue(); soundManager.playEnemyDie();
                    AbstractProp p = (e instanceof BossEnemy)
                        ? EnemyFactory.createPropFromBoss(e.getLocationX(), e.getLocationY(), propBloodBmp, propBombBmp, propBulletBmp)
                        : EnemyFactory.createPropIfDropped(e.getLocationX(), e.getLocationY(), propBloodBmp, propBombBmp, propBulletBmp);
                    if (p != null) props.add(p);
                }
                break;
            }
        }
        // 敌机子弹 vs 英雄
        for (AbstractBullet b : enemyBullets) {
            if (b.isValid() && hero.isValid() && b.crash(hero)) { hero.decreaseHp(b.getPower()); b.vanish(); }
        }
        // 敌机 vs 英雄（碰撞伤害）
        for (AbstractEnemyAircraft e : enemies) {
            if (e.isValid() && hero.isValid() && e.crash(hero)) { hero.decreaseHp(e.getHp()); e.vanish(); }
        }
        // 道具 vs 英雄
        for (AbstractProp p : props) {
            if (!p.isValid() || !p.crash(hero)) continue;
            soundManager.playPropGet();
            switch (p.getType()) {
                case BLOOD: hero.heal(GameConstant.BLOOD_PROP_HEAL); break;
                case BOMB: // 清屏：所有敌机消亡
                    soundManager.playBomb();
                    for (AbstractEnemyAircraft e : enemies) { score += e.getScoreValue(); e.vanish(); }
                    enemyBullets.forEach(AbstractFlyingObject::vanish);
                    break;
                case BULLET: hero.activateSpreadShoot(now); break;
            }
            p.vanish();
        }
    }

    // ===== Canvas渲染（游戏循环线程） =====

    private void draw() {
        Canvas canvas = getHolder().lockCanvas();
        if (canvas == null) return;
        try {
            // 滚动背景
            if (bgBmp != null) { canvas.drawBitmap(bgBmp, 0, bgY1, null); canvas.drawBitmap(bgBmp, 0, bgY2, null); }
            else canvas.drawColor(Color.BLACK);

            // 绘制游戏对象
            props.forEach(p -> p.draw(canvas, paint));
            enemies.forEach(e -> e.draw(canvas, paint));
            heroBullets.forEach(b -> b.draw(canvas, paint));
            enemyBullets.forEach(b -> b.draw(canvas, paint));
            if (hero.isValid()) hero.draw(canvas, paint);

            // 绘制HUD（分数、血条）
            drawHUD(canvas);
        } finally {
            getHolder().unlockCanvasAndPost(canvas);
        }
    }

    private void drawHUD(Canvas canvas) {
        // 分数
        hudPaint.setColor(Color.WHITE); hudPaint.setTextSize(52);
        canvas.drawText("SCORE: " + score, 30, 70, hudPaint);

        // 英雄HP条
        int barW = screenW / 2, barH = 24;
        int barX = (screenW - barW) / 2, barY = screenH - 50;
        paint.setColor(Color.DKGRAY);
        canvas.drawRect(barX, barY, barX + barW, barY + barH, paint);
        paint.setColor(Color.GREEN);
        float hpRatio = (float) hero.getHp() / hero.getMaxHp();
        canvas.drawRect(barX, barY, barX + barW * hpRatio, barY + barH, paint);
        hudPaint.setTextSize(32);
        canvas.drawText("HP " + hero.getHp(), barX + 4, barY + 20, hudPaint);

        // Boss HP条（若有Boss）
        for (AbstractEnemyAircraft e : enemies) {
            if (e instanceof BossEnemy && e.isValid()) {
                paint.setColor(Color.DKGRAY);
                canvas.drawRect(barX, 20, barX + barW, 44, paint);
                paint.setColor(Color.RED);
                float bRatio = (float) e.getHp() / e.getMaxHp();
                canvas.drawRect(barX, 20, barX + barW * bRatio, 44, paint);
                hudPaint.setTextSize(28);
                canvas.drawText("BOSS HP " + e.getHp(), barX + 4, 40, hudPaint);
                break;
            }
        }
    }

    // ===== 触屏事件（UI线程，只写volatile字段） =====

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            heroTargetX = e.getX();
            heroTargetY = e.getY();
        }
        return true;
    }

    // ===== 游戏结束处理 =====

    private void triggerGameOver() {
        gameState = GameState.GAME_OVER;
        soundManager.playHeroDie();
        soundManager.pauseBgm();
        // 保存分数到本地数据库（数据库线程异步执行）
        scoreRepository.saveScore(GameConstant.DEFAULT_PLAYER_NAME, score, difficulty);
        // 回调到主线程更新UI
        mainHandler.postDelayed(() -> { if (gameOverCallback != null) gameOverCallback.onGameOver(score); }, 800);
    }

    public void pause() { gameState = GameState.PAUSED; soundManager.pauseBgm(); }
    public void resumeGame() { gameState = GameState.PLAYING; soundManager.resumeBgm(); }
    public int getScore() { return score; }
}
