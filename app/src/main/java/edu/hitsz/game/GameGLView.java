package edu.hitsz.game;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import edu.hitsz.R;
import edu.hitsz.audio.SoundManager;
import edu.hitsz.db.ScoreRepository;
import edu.hitsz.factory.EnemyFactory;
import edu.hitsz.model.AbstractFlyingObject;
import edu.hitsz.model.HeroAircraft;
import edu.hitsz.model.bullet.AbstractBullet;
import edu.hitsz.model.bullet.EnemyBullet;
import edu.hitsz.model.bullet.HeroBullet;
import edu.hitsz.model.enemy.AbstractEnemyAircraft;
import edu.hitsz.model.enemy.BossEnemy;
import edu.hitsz.model.enemy.EliteEnemy;
import edu.hitsz.model.prop.AbstractProp;
import edu.hitsz.render.AircraftMeshFactory;
import edu.hitsz.render.GlUtil;
import edu.hitsz.render.Mesh;

/**
 * 三渲二 OpenGL ES 2.0 游戏主视图。
 *
 * 渲染管线（每帧）:
 *   1. 背景滚动纹理四边形（不写深度缓冲）
 *   2. 描边通道（Inverted-Hull，正面剔除，纯黑膨胀）
 *   3. 三渲二着色通道（3级量化 Lambertian + 边缘光）
 *   4. HUD 血条（无深度测试，永远在最前）
 *
 * 与原 GameSurfaceView 的区别：
 *   - 使用 GLSurfaceView (RENDERMODE_CONTINUOUSLY) 替代 SurfaceView + 手动线程
 *   - 游戏逻辑在 GL 线程的 onDrawFrame() 中执行（同步，无多线程竞争）
 *   - 所有游戏对象 Bitmap 传 null（不需要 Canvas 绘制）
 */
public class GameGLView extends GLSurfaceView implements GLSurfaceView.Renderer {

    // ═══════════════ GL 着色器句柄 ═══════════════

    // 三渲二着色器
    private int celProg;
    private int uCelMvp, aCelPos, aCelNorm, aCelColor;

    // 描边着色器
    private int outProg;
    private int uOutMvp, aOutPos, aOutNorm, uOutPx;

    // 背景着色器
    private int bgProg;
    private int uBgScroll, uBgTex, aBgPos, aBgUv;

    // ═══════════════ GL 资源 ═══════════════

    private Mesh heroMesh, mobMesh, eliteMesh, bossMesh;
    private Mesh heroBulletMesh, enemyBulletMesh;
    private Mesh propBloodMesh, propBombMesh, propBulletMesh;
    // 简单矩形 mesh，用于 HUD 血条
    private Mesh hudBarBgMesh, hudBarFgMesh;

    private int bgTexId;
    private int bgVbo;   // 背景四边形 VBO (NDC 位置 + UV)

    // ═══════════════ 矩阵 ═══════════════

    private final float[] proj  = new float[16]; // 正交投影矩阵
    private final float[] model = new float[16];
    private final float[] mvp   = new float[16];

    // ═══════════════ 背景滚动 ═══════════════

    private float bgScrollY = 0f;
    private static final float BG_SPEED = 0.00055f; // 每帧滚动量 (UV 单位)

    // ═══════════════ 游戏状态 ═══════════════

    private volatile GameState gameState = GameState.PLAYING;
    private int screenW, screenH;
    private volatile boolean initialized = false;
    private volatile float heroTargetX, heroTargetY;

    private HeroAircraft hero;
    private final List<AbstractEnemyAircraft> enemies      = new ArrayList<>();
    private final List<AbstractBullet>        heroBullets  = new ArrayList<>();
    private final List<AbstractBullet>        enemyBullets = new ArrayList<>();
    private final List<AbstractProp>          props        = new ArrayList<>();

    private int  score         = 0;
    private long nextBossScore = GameConstant.BOSS_SPAWN_SCORE_THRESHOLD;
    private long lastMobSpawn, lastEliteSpawn;

    // ═══════════════ 外部依赖 ═══════════════

    private final SoundManager    soundManager;
    private final ScoreRepository scoreRepository;

    public interface GameOverCallback { void onGameOver(int score); }
    private GameOverCallback gameOverCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String difficulty = GameConstant.DIFFICULTY_EASY;

    private final Context appCtx; // 用于 GL 线程资源加载

    // ═══════════════ 构造函数 ═══════════════

    public GameGLView(Context ctx) {
        super(ctx);
        appCtx = ctx.getApplicationContext();
        setEGLContextClientVersion(2);  // OpenGL ES 2.0
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY); // 持续渲染 (vsync 限速 ~60fps)
        setFocusable(true);
        soundManager   = new SoundManager(ctx);
        scoreRepository = new ScoreRepository(ctx);
    }

    public void setGameOverCallback(GameOverCallback cb) { gameOverCallback = cb; }
    public void setDifficulty(String d)                  { difficulty = d; }

    // ═══════════════ GLSurfaceView.Renderer ═══════════════

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0.02f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // 加载 GLSL 源码
        String celV = GlUtil.loadRaw(appCtx, R.raw.cel_vert);
        String celF = GlUtil.loadRaw(appCtx, R.raw.cel_frag);
        String outV = GlUtil.loadRaw(appCtx, R.raw.outline_vert);
        String outF = GlUtil.loadRaw(appCtx, R.raw.outline_frag);
        String bgV  = GlUtil.loadRaw(appCtx, R.raw.bg_vert);
        String bgF  = GlUtil.loadRaw(appCtx, R.raw.bg_frag);

        celProg = GlUtil.linkProgram(celV, celF);
        outProg = GlUtil.linkProgram(outV, outF);
        bgProg  = GlUtil.linkProgram(bgV,  bgF);

        // 缓存 uniform / attribute 位置 (避免每帧查询)
        uCelMvp   = GLES20.glGetUniformLocation(celProg, "u_mvp");
        aCelPos   = GLES20.glGetAttribLocation (celProg, "a_pos");
        aCelNorm  = GLES20.glGetAttribLocation (celProg, "a_norm");
        aCelColor = GLES20.glGetAttribLocation (celProg, "a_color");

        uOutMvp = GLES20.glGetUniformLocation(outProg, "u_mvp");
        aOutPos = GLES20.glGetAttribLocation (outProg, "a_pos");
        aOutNorm= GLES20.glGetAttribLocation (outProg, "a_norm");
        uOutPx  = GLES20.glGetUniformLocation(outProg, "u_outline_px");

        uBgScroll = GLES20.glGetUniformLocation(bgProg, "u_scroll");
        uBgTex    = GLES20.glGetUniformLocation(bgProg, "u_tex");
        aBgPos    = GLES20.glGetAttribLocation (bgProg, "a_pos");
        aBgUv     = GLES20.glGetAttribLocation (bgProg, "a_uv");
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        screenW = w; screenH = h;
        GLES20.glViewport(0, 0, w, h);

        // 正交投影：像素坐标 → 裁剪坐标，Y 轴向下
        Matrix.orthoM(proj, 0, 0, w, h, 0, -500f, 500f);

        buildMeshes();
        buildBgQuad();
        bgTexId = GlUtil.loadTexture(appCtx, R.drawable.bg);

        if (!initialized) {
            initGame();
            initialized = true;
        }
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        // ── 更新游戏逻辑 (GL线程，无并发问题) ──
        long now = System.currentTimeMillis();
        if (initialized && gameState == GameState.PLAYING) {
            update(now);
        }

        // ── 清屏 ──
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_BLEND);

        // ── 1. 背景（禁用深度测试，全屏四边形） ──
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        drawBackground();
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        if (!initialized) return;

        // ── 2. 描边通道（正面剔除 + 法线膨胀 → 黑色轮廓）──
        float outlineSize = Math.max(screenW, screenH) * 0.0038f;
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_FRONT);
        GLES20.glUseProgram(outProg);
        GLES20.glUniform1f(uOutPx, outlineSize);
        renderOutlines();
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        // ── 3. 三渲二着色通道 ──
        GLES20.glUseProgram(celProg);
        renderCelShaded();

        // ── 4. HUD（无深度测试） ──
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        renderHud();
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    // ═══════════════ 网格构建 ═══════════════

    private void buildMeshes() {
        float hw = screenW / 14f, hh = hw * 1.5f;
        float mw = screenW / 18f, mh = mw * 1.1f;
        float ew = screenW / 16f, eh = ew * 1.1f;
        float bw = screenW / 6f,  bh = bw * 1.2f;
        float blW= screenW / 70f, blH= blW * 3f;
        float ps = screenW / 22f;

        heroMesh        = AircraftMeshFactory.heroAircraft(hw, hh);
        mobMesh         = AircraftMeshFactory.mobEnemy(mw, mh);
        eliteMesh       = AircraftMeshFactory.eliteEnemy(ew, eh);
        bossMesh        = AircraftMeshFactory.bossEnemy(bw, bh);
        heroBulletMesh  = AircraftMeshFactory.heroBullet(blW, blH);
        enemyBulletMesh = AircraftMeshFactory.enemyBullet(blW * 1.3f, blH * 0.75f);
        propBloodMesh   = AircraftMeshFactory.propBlood(ps);
        propBombMesh    = AircraftMeshFactory.propBomb(ps);
        propBulletMesh  = AircraftMeshFactory.propBullet(ps);
    }

    /** 背景全屏四边形 VBO：[NDC_x, NDC_y, uv_x, uv_y] */
    private void buildBgQuad() {
        float[] v = { -1f,-1f, 0f,1f,
                       1f,-1f, 1f,1f,
                       1f, 1f, 1f,0f,
                      -1f, 1f, 0f,0f };
        int[] buf = new int[1];
        GLES20.glGenBuffers(1, buf, 0);
        bgVbo = buf[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bgVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, v.length * 4,
                GlUtil.floatBuf(v), GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    // ═══════════════ 渲染函数 ═══════════════

    private void drawBackground() {
        bgScrollY = (bgScrollY + BG_SPEED) % 1f;

        GLES20.glUseProgram(bgProg);
        GLES20.glUniform1f(uBgScroll, bgScrollY);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTexId);
        GLES20.glUniform1i(uBgTex, 0);

        int stride = 4 * 4;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bgVbo);
        GLES20.glEnableVertexAttribArray(aBgPos);
        GLES20.glVertexAttribPointer(aBgPos, 2, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(aBgUv);
        GLES20.glVertexAttribPointer(aBgUv, 2, GLES20.GL_FLOAT, false, stride, 2 * 4);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4);
        GLES20.glDisableVertexAttribArray(aBgPos);
        GLES20.glDisableVertexAttribArray(aBgUv);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    /** 计算 MVP = proj * translate(x,y,z) 并存入 mvp[] */
    private void setMVP(float x, float y, float z) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        Matrix.multiplyMM(mvp, 0, proj, 0, model, 0);
    }

    /** 三渲二着色器绘制单个网格 */
    private void drawCel(Mesh mesh, float x, float y, float z) {
        setMVP(x, y, z);
        GLES20.glUniformMatrix4fv(uCelMvp, 1, false, mvp, 0);
        mesh.bind(aCelPos, aCelNorm, aCelColor);
        mesh.draw();
        mesh.unbind(aCelPos, aCelNorm, aCelColor);
    }

    /** 描边通道绘制（只需位置+法线，无颜色） */
    private void drawOutl(Mesh mesh, float x, float y, float z) {
        setMVP(x, y, z);
        GLES20.glUniformMatrix4fv(uOutMvp, 1, false, mvp, 0);
        mesh.bind(aOutPos, aOutNorm, -1);
        mesh.draw();
        mesh.unbind(aOutPos, aOutNorm, -1);
    }

    private Mesh meshFor(AbstractEnemyAircraft e) {
        if (e instanceof BossEnemy)  return bossMesh;
        if (e instanceof EliteEnemy) return eliteMesh;
        return mobMesh;
    }

    private Mesh meshFor(AbstractProp p) {
        switch (p.getType()) {
            case BLOOD: return propBloodMesh;
            case BOMB:  return propBombMesh;
            default:    return propBulletMesh;
        }
    }

    private void renderCelShaded() {
        for (AbstractProp p : props)
            if (p.isValid()) drawCel(meshFor(p), p.getLocationX(), p.getLocationY(), 20);
        for (AbstractEnemyAircraft e : enemies)
            if (e.isValid()) drawCel(meshFor(e), e.getLocationX(), e.getLocationY(), 50);
        for (AbstractBullet b : heroBullets)
            if (b.isValid()) drawCel(heroBulletMesh, b.getLocationX(), b.getLocationY(), 70);
        for (AbstractBullet b : enemyBullets)
            if (b.isValid()) drawCel(enemyBulletMesh, b.getLocationX(), b.getLocationY(), 70);
        if (hero != null && hero.isValid())
            drawCel(heroMesh, hero.getLocationX(), hero.getLocationY(), 80);
    }

    private void renderOutlines() {
        for (AbstractProp p : props)
            if (p.isValid()) drawOutl(meshFor(p), p.getLocationX(), p.getLocationY(), 20);
        for (AbstractEnemyAircraft e : enemies)
            if (e.isValid()) drawOutl(meshFor(e), e.getLocationX(), e.getLocationY(), 50);
        for (AbstractBullet b : heroBullets)
            if (b.isValid()) drawOutl(heroBulletMesh, b.getLocationX(), b.getLocationY(), 70);
        for (AbstractBullet b : enemyBullets)
            if (b.isValid()) drawOutl(enemyBulletMesh, b.getLocationX(), b.getLocationY(), 70);
        if (hero != null && hero.isValid())
            drawOutl(heroMesh, hero.getLocationX(), hero.getLocationY(), 80);
    }

    /** HUD：绘制血条（彩色矩形网格，无光照） */
    private void renderHud() {
        if (hero == null) return;
        // 血条位置：屏幕底部居中
        int barTotalW = screenW / 2;
        int barH      = Math.max(18, screenH / 80);
        int barX      = (screenW - barTotalW) / 2;
        int barY      = screenH - barH - 12;

        float hpRatio = (float) hero.getHp() / Math.max(1, hero.getMaxHp());
        hpRatio = Math.max(0f, Math.min(1f, hpRatio));

        // 背景灰条
        drawHudRect(barX, barY, barTotalW, barH,
                0.20f, 0.20f, 0.25f, 0.90f, 300);
        // 绿色血量
        if (hpRatio > 0)
            drawHudRect(barX, barY, (int)(barTotalW * hpRatio), barH,
                    0.15f + 0.70f * (1 - hpRatio),
                    0.80f - 0.60f * (1 - hpRatio),
                    0.15f, 1.0f, 310);
    }

    /**
     * 在屏幕像素坐标绘制一个纯色矩形（用于 HUD）。
     * 使用 cel 着色器，法线朝上 (0,0,1) → 三渲二最亮档。
     */
    private void drawHudRect(float x, float y, float w, float h,
                              float r, float g, float b, float a, float z) {
        // 动态构建一个矩形 Mesh（仅顶面，法线(0,0,1)）
        float[] verts = {
            x,   y,   z,  0,0,1,  r,g,b,a,
            x+w, y,   z,  0,0,1,  r,g,b,a,
            x+w, y+h, z,  0,0,1,  r,g,b,a,
            x,   y+h, z,  0,0,1,  r,g,b,a,
        };
        short[] idx = {0,1,2, 0,2,3};
        Mesh rect = new Mesh(verts, idx);

        Matrix.setIdentityM(model, 0);
        Matrix.multiplyMM(mvp, 0, proj, 0, model, 0);
        GLES20.glUniformMatrix4fv(uCelMvp, 1, false, mvp, 0);
        rect.bind(aCelPos, aCelNorm, aCelColor);
        rect.draw();
        rect.unbind(aCelPos, aCelNorm, aCelColor);
        rect.delete();
    }

    // ═══════════════ 游戏逻辑（同 GameSurfaceView）═══════════════

    private void initGame() {
        score = 0;
        nextBossScore  = GameConstant.BOSS_SPAWN_SCORE_THRESHOLD;
        lastMobSpawn   = System.currentTimeMillis();
        lastEliteSpawn = lastMobSpawn;
        // Bitmap 传 null — GL 渲染器不使用 Canvas 位图
        hero = new HeroAircraft(
                (int)(screenW / 2f),
                (int)(screenH * 3 / 4f),
                null, null,
                GameConstant.HERO_HP);
        heroTargetX = screenW / 2f;
        heroTargetY = screenH * 3 / 4f;
        enemies.clear(); heroBullets.clear();
        enemyBullets.clear(); props.clear();
        gameState = GameState.PLAYING;
        soundManager.startBgm();
    }

    private void update(long now) {
        if (hero.isValid()) {
            hero.setTouchTarget(heroTargetX, heroTargetY);
            hero.forward();
            List<HeroBullet> nb = hero.shoot(now);
            if (!nb.isEmpty()) { heroBullets.addAll(nb); soundManager.playShoot(); }
        }
        spawnEnemies(now);

        for (AbstractEnemyAircraft e : enemies) {
            e.forward();
            if (e instanceof BossEnemy) ((BossEnemy) e).checkBounds(screenW);
            if (e.isOutOfScreen(screenH)) e.vanish();
            List<EnemyBullet> eb = e.shoot(now, null, screenW);
            enemyBullets.addAll(eb);
        }
        for (AbstractBullet b : heroBullets)  b.forward(screenW, screenH);
        for (AbstractBullet b : enemyBullets) b.forward(screenW, screenH);
        for (AbstractProp   p : props) { p.forward(); if (p.isOutOfScreen(screenH)) p.vanish(); }

        checkCollisions(now);

        enemies.removeIf(e -> !e.isValid());
        heroBullets.removeIf(b -> !b.isValid());
        enemyBullets.removeIf(b -> !b.isValid());
        props.removeIf(p -> !p.isValid());

        if (!hero.isValid()) triggerGameOver();
    }

    private void spawnEnemies(long now) {
        if (now - lastMobSpawn >= GameConstant.MOB_SPAWN_INTERVAL) {
            enemies.add(EnemyFactory.createMob(screenW, null));
            lastMobSpawn = now;
        }
        if (now - lastEliteSpawn >= GameConstant.ELITE_SPAWN_INTERVAL) {
            enemies.add(EnemyFactory.createElite(screenW, null));
            lastEliteSpawn = now;
        }
        if (score >= nextBossScore) {
            enemies.add(EnemyFactory.createBoss(screenW, null));
            nextBossScore += GameConstant.BOSS_SPAWN_SCORE_THRESHOLD;
        }
    }

    private void checkCollisions(long now) {
        for (AbstractBullet b : heroBullets) {
            if (!b.isValid()) continue;
            for (AbstractEnemyAircraft e : enemies) {
                if (!e.isValid() || !b.crash(e)) continue;
                e.decreaseHp(b.getPower()); b.vanish();
                if (!e.isValid()) {
                    score += e.getScoreValue();
                    soundManager.playEnemyDie();
                    AbstractProp p = (e instanceof BossEnemy)
                            ? EnemyFactory.createPropFromBoss(
                                    e.getLocationX(), e.getLocationY(), null, null, null)
                            : EnemyFactory.createPropIfDropped(
                                    e.getLocationX(), e.getLocationY(), null, null, null);
                    if (p != null) props.add(p);
                }
                break;
            }
        }
        for (AbstractBullet b : enemyBullets)
            if (b.isValid() && hero.isValid() && b.crash(hero))
            { hero.decreaseHp(b.getPower()); b.vanish(); }

        for (AbstractEnemyAircraft e : enemies)
            if (e.isValid() && hero.isValid() && e.crash(hero))
            { hero.decreaseHp(e.getHp()); e.vanish(); }

        for (AbstractProp p : props) {
            if (!p.isValid() || !p.crash(hero)) continue;
            soundManager.playPropGet();
            switch (p.getType()) {
                case BLOOD:  hero.heal(GameConstant.BLOOD_PROP_HEAL); break;
                case BOMB:
                    soundManager.playBomb();
                    for (AbstractEnemyAircraft e : enemies)
                    { score += e.getScoreValue(); e.vanish(); }
                    enemyBullets.forEach(AbstractFlyingObject::vanish);
                    break;
                case BULLET: hero.activateSpreadShoot(now); break;
            }
            p.vanish();
        }
    }

    private void triggerGameOver() {
        gameState = GameState.GAME_OVER;
        soundManager.playHeroDie();
        soundManager.pauseBgm();
        scoreRepository.saveScore(GameConstant.DEFAULT_PLAYER_NAME, score, difficulty);
        mainHandler.postDelayed(() -> {
            if (gameOverCallback != null) gameOverCallback.onGameOver(score);
        }, 800);
    }

    // ═══════════════ 输入（UI 线程） ═══════════════

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getAction();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            heroTargetX = e.getX();
            heroTargetY = e.getY();
        }
        return true;
    }

    // ═══════════════ 生命周期 ═══════════════

    public void pause()      { gameState = GameState.PAUSED;  soundManager.pauseBgm();  }
    public void resumeGame() { gameState = GameState.PLAYING; soundManager.resumeBgm(); }
    public int  getScore()   { return score; }

    public void onDestroy() {
        soundManager.release();
    }
}
