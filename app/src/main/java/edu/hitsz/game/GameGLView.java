package edu.hitsz.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
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
import edu.hitsz.render.AircraftMesh;

/**
 * 2.5D + 真3D 飞机大战渲染器 — OpenGL ES 2.0
 *
 * 渲染流水线：
 *   背景      — sprite 精灵（滚动贴图）
 *   飞机/子弹 — model3d 三Pass（投影阴影 → 卡通描边 → Cel主体）
 *   道具      — sprite 精灵（贴图，带2D描边）
 *
 * 翻滚动画：
 *   smoothBank = lerp(smoothBank, clamp(heroΔX×-0.006, ±35°), 0.15)
 */
public class GameGLView extends GLSurfaceView implements GLSurfaceView.Renderer {

    private static final String TAG = "GameGLView";

    // ════ 游戏状态 ════════════════════════════════════════════════
    private enum GameState { PLAYING, PAUSED, GAME_OVER }
    private volatile GameState gameState = GameState.PLAYING;
    private boolean initialized = false;

    // ════ 屏幕 ════════════════════════════════════════════════════
    private int screenW, screenH;

    // ════ 触屏（UI线程写 → GL线程读，volatile保证可见性） ═════════
    private volatile float heroTargetX, heroTargetY;

    // ════ 翻滚动画 ════════════════════════════════════════════════
    private float smoothBank = 0f;
    private float prevHeroX  = 0f;
    private static final float MAX_BANK_RAD = 0.61f; // ≈35°

    // ════ 游戏对象 ════════════════════════════════════════════════
    private HeroAircraft                      hero;
    private final List<AbstractEnemyAircraft> enemies      = new ArrayList<>();
    private final List<AbstractBullet>        heroBullets  = new ArrayList<>();
    private final List<AbstractBullet>        enemyBullets = new ArrayList<>();
    private final List<AbstractProp>          props        = new ArrayList<>();

    // ════ 分数/生成 ═══════════════════════════════════════════════
    private int  score         = 0;
    private long nextBossScore;
    private long lastMobSpawn, lastEliteSpawn;
    private String difficulty  = GameConstant.DIFFICULTY_EASY;

    // ════ 性能优化 ════════════════════════════════════════════════
    private int hudFrameCounter = 0;
    private static final int HUD_THROTTLE = 6;

    // ════ 精灵尺寸（像素） ════════════════════════════════════════
    private int heroSz, mobSz, eliteSz, bossSz, bulletW, bulletH, propSz;

    // ════ 背景滚动 ════════════════════════════════════════════════
    private float bgY1 = 0f, bgY2;

    // ════ GL 程序 ═════════════════════════════════════════════════
    private int spriteProg, bgProg, model3dProg;

    // sprite program locations
    private int spAPos, spAUV;
    private int spUMVP, spUBankRad, spUBankY, spUTint, spUCelSteps, spUOutline, spUTex;

    // bg program locations
    private int bgAPos, bgAUV, bgUMVP, bgUTex;

    // model3d program locations
    private int m3APos, m3ANormal, m3AColor;
    private int m3UMVP, m3UView, m3UProj3d, m3UTint, m3UOutline;

    // ════ VBO / IBO ═══════════════════════════════════════════════
    private int quadVBO, quadIBO;

    // 3D 网格 VBO（[vboId, vertexCount]）
    private int vboHero3d,    vtxHero3d;
    private int vboMob3d,     vtxMob3d;
    private int vboElite3d,   vtxElite3d;
    private int vboBoss3d,    vtxBoss3d;
    private int vboBulletH3d, vtxBulletH3d;
    private int vboBulletE3d, vtxBulletE3d;
    private int vboProp3d,    vtxProp3d;

    // ════ 纹理 ID ═════════════════════════════════════════════════
    private int texPropBlood, texPropBomb, texPropBullet, texBg;

    // ════ 矩阵工作区 ═════════════════════════════════════════════
    private final float[] mProj   = new float[16]; // 2D 正交
    private final float[] mModel  = new float[16];
    private final float[] mMVP    = new float[16];

    private final float[] mProj3d = new float[16]; // 3D 透视
    private final float[] mView3d = new float[16];
    private final float[] mModel3d= new float[16];
    private final float[] mVM3d   = new float[16];
    private final float[] mMVP3d  = new float[16];

    // ════ Tint 常量 ═══════════════════════════════════════════════
    private static final float[] T_NORMAL = {1f, 1f, 1f, 1f};
    private static final float[] T_SHADOW = {0f, 0f, 0f, 0.38f};

    // ════ 外部依赖 ════════════════════════════════════════════════
    private SoundManager    soundManager;
    private ScoreRepository scoreRepository;

    // ════ HUD ════════════════════════════════════════════════════
    private volatile TextView hudScoreView, hudHPView, hudBossView;
    private volatile edu.hitsz.ui.GameActivity.HudCallback hudCallback;

    // ════ 游戏结束回调 ════════════════════════════════════════════
    public interface GameOverCallback { void onGameOver(int score); }
    private GameOverCallback gameOverCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ════════════════════════════════════════════════════════════
    // 构造器
    // ════════════════════════════════════════════════════════════
    public GameGLView(Context ctx) {
        super(ctx);
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        try { soundManager    = new SoundManager(ctx);    } catch (Exception e) { soundManager    = null; }
        try { scoreRepository = new ScoreRepository(ctx); } catch (Exception e) { scoreRepository = null; }
    }

    public void setGameOverCallback(GameOverCallback cb)                { gameOverCallback = cb; }
    public void setDifficulty(String d)                                  { difficulty = d; }
    public int  getScore()                                               { return score; }
    public void setHudViews(TextView sv, TextView hv, TextView bv)      { hudScoreView = sv; hudHPView = hv; hudBossView = bv; }
    public void setHudViews2(edu.hitsz.ui.GameActivity.HudCallback cb)  { hudCallback = cb; }
    public void pause()         { gameState = GameState.PAUSED;  sfx(() -> soundManager.pauseBgm()); }
    public void resumeGame()    { gameState = GameState.PLAYING; sfx(() -> soundManager.resumeBgm()); }
    public void onDestroyView() { sfx(() -> soundManager.release()); }

    // ════════════════════════════════════════════════════════════
    // GLSurfaceView.Renderer
    // ════════════════════════════════════════════════════════════

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig cfg) {
        GLES20.glClearColor(0.04f, 0.04f, 0.14f, 1f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // ── 编译着色器
        spriteProg  = linkProg(raw(R.raw.sprite_vert),  raw(R.raw.sprite_frag));
        bgProg      = linkProg(raw(R.raw.bg_vert),      raw(R.raw.bg_frag));
        model3dProg = linkProg(raw(R.raw.model3d_vert), raw(R.raw.model3d_frag));

        // sprite locations
        if (spriteProg != 0) {
            spAPos      = GLES20.glGetAttribLocation (spriteProg, "aPosition");
            spAUV       = GLES20.glGetAttribLocation (spriteProg, "aTexCoord");
            spUMVP      = GLES20.glGetUniformLocation(spriteProg, "uMVP");
            spUBankRad  = GLES20.glGetUniformLocation(spriteProg, "uBankRad");
            spUBankY    = GLES20.glGetUniformLocation(spriteProg, "uBankY");
            spUTint     = GLES20.glGetUniformLocation(spriteProg, "uTint");
            spUCelSteps = GLES20.glGetUniformLocation(spriteProg, "uCelSteps");
            spUOutline  = GLES20.glGetUniformLocation(spriteProg, "uOutline");
            spUTex      = GLES20.glGetUniformLocation(spriteProg, "uTex");
        }
        // bg locations
        if (bgProg != 0) {
            bgAPos = GLES20.glGetAttribLocation (bgProg, "aPosition");
            bgAUV  = GLES20.glGetAttribLocation (bgProg, "aTexCoord");
            bgUMVP = GLES20.glGetUniformLocation(bgProg, "uMVP");
            bgUTex = GLES20.glGetUniformLocation(bgProg, "uTex");
        }
        // model3d locations
        if (model3dProg != 0) {
            m3APos    = GLES20.glGetAttribLocation (model3dProg, "aPosition");
            m3ANormal = GLES20.glGetAttribLocation (model3dProg, "aNormal");
            m3AColor  = GLES20.glGetAttribLocation (model3dProg, "aColor");
            m3UMVP    = GLES20.glGetUniformLocation(model3dProg, "uMVP");
            m3UView   = GLES20.glGetUniformLocation(model3dProg, "uView");
            m3UProj3d = GLES20.glGetUniformLocation(model3dProg, "uProj");
            m3UTint   = GLES20.glGetUniformLocation(model3dProg, "uTint");
            m3UOutline= GLES20.glGetUniformLocation(model3dProg, "uOutline");
        }

        // ── 共享 Quad（用于背景 + 道具精灵）
        buildQuad();

        // ── 3D 飞机网格 VBO
        int[] r;
        r = buildMeshVBO(AircraftMesh.HERO);     vboHero3d    = r[0]; vtxHero3d    = r[1];
        r = buildMeshVBO(AircraftMesh.MOB);      vboMob3d     = r[0]; vtxMob3d     = r[1];
        r = buildMeshVBO(AircraftMesh.ELITE);    vboElite3d   = r[0]; vtxElite3d   = r[1];
        r = buildMeshVBO(AircraftMesh.BOSS);     vboBoss3d    = r[0]; vtxBoss3d    = r[1];
        r = buildMeshVBO(AircraftMesh.BULLET_H); vboBulletH3d = r[0]; vtxBulletH3d = r[1];
        r = buildMeshVBO(AircraftMesh.BULLET_E); vboBulletE3d = r[0]; vtxBulletE3d = r[1];
        r = buildMeshVBO(AircraftMesh.PROP);     vboProp3d    = r[0]; vtxProp3d    = r[1];

        // ── 道具贴图（sprite模式）
        texPropBlood = tex(R.drawable.prop_blood);
        texPropBomb  = tex(R.drawable.prop_bomb);
        texPropBullet= tex(R.drawable.prop_bullet);
        texBg        = tex(R.drawable.bg);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        screenW = w; screenH = h;

        // 2D 正交投影 Y↓
        Matrix.orthoM(mProj, 0, 0f, w, h, 0f, -100f, 100f);

        // 3D 透视摄像机 — 斜俯视角（约30°仰角）
        float aspect = (float) w / h;
        Matrix.perspectiveM(mProj3d, 0, 40f, aspect, 0.1f, 200f);
        Matrix.setLookAtM(mView3d, 0,
            0f, 1.8f, 2.0f,   // eye（上方偏后）
            0f, 0f,   0f,     // center
            0f, 1f,   0f);    // up

        // 精灵显示尺寸
        heroSz  = w / 7;
        mobSz   = w / 9;
        eliteSz = w / 8;
        bossSz  = w / 3;
        bulletW = Math.max(6,  w / 30);
        bulletH = Math.max(12, w / 15);
        propSz  = w / 10;
        bgY2    = -h;

        if (!initialized) { initGame(); initialized = true; }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (gameState == GameState.PLAYING && initialized) update();
        render();
    }

    // ════════════════════════════════════════════════════════════
    // 游戏逻辑（GL线程）
    // ════════════════════════════════════════════════════════════

    private void initGame() {
        score = 0;
        nextBossScore = GameConstant.BOSS_SPAWN_SCORE_THRESHOLD;
        lastMobSpawn = lastEliteSpawn = System.currentTimeMillis();
        hero = new HeroAircraft(screenW / 2, screenH * 3 / 4,
                mkBmp(heroSz, heroSz), mkBmp(bulletW, bulletH), GameConstant.HERO_HP);
        heroTargetX = screenW / 2f;
        heroTargetY = screenH * 3 / 4f;
        smoothBank = 0; prevHeroX = screenW / 2f;
        enemies.clear(); heroBullets.clear(); enemyBullets.clear(); props.clear();
        gameState = GameState.PLAYING;
        sfx(() -> soundManager.startBgm());
    }

    private void update() {
        long now = System.currentTimeMillis();

        // ── 翻滚平滑角
        float hx = hero.isValid() ? hero.getLocationX() : prevHeroX;
        float targetBank = Math.max(-MAX_BANK_RAD, Math.min(MAX_BANK_RAD, -(hx - prevHeroX) * 0.006f));
        smoothBank += (targetBank - smoothBank) * 0.15f;
        prevHeroX = hx;

        // ── 英雄
        if (hero.isValid()) {
            hero.setTouchTarget(heroTargetX, heroTargetY);
            hero.forward();
            List<HeroBullet> nb = hero.shoot(now);
            if (nb != null && !nb.isEmpty()) { heroBullets.addAll(nb); sfx(() -> soundManager.playShoot()); }
        }

        // ── 生成敌机
        if (now - lastMobSpawn   >= GameConstant.MOB_SPAWN_INTERVAL)
            { lastMobSpawn   = now; enemies.add(EnemyFactory.createMob(screenW,   mkBmp(mobSz,   mobSz))); }
        if (now - lastEliteSpawn >= GameConstant.ELITE_SPAWN_INTERVAL)
            { lastEliteSpawn = now; enemies.add(EnemyFactory.createElite(screenW, mkBmp(eliteSz, eliteSz))); }
        if (score >= nextBossScore) {
            boolean has = false;
            for (AbstractEnemyAircraft e : enemies) if (e instanceof BossEnemy && e.isValid()) { has = true; break; }
            if (!has) { enemies.add(EnemyFactory.createBoss(screenW, mkBmp(bossSz, bossSz))); nextBossScore += GameConstant.BOSS_SPAWN_SCORE_THRESHOLD; }
        }

        // ── 移动敌机 + 射击
        for (AbstractEnemyAircraft e : enemies) {
            if (!e.isValid()) continue;
            e.forward();
            if (e.isOutOfScreen(screenH)) { e.vanish(); continue; }
            try { List<EnemyBullet> eb = e.shoot(now, mkBmp(bulletW, bulletH), screenW); if (eb != null) enemyBullets.addAll(eb); } catch (Exception ex) {}
        }

        // ── 子弹/道具移动
        for (AbstractBullet b : heroBullets)  { if (b.isValid()) { b.forward(); if (b.getLocationY() < -80) b.vanish(); } }
        for (AbstractBullet b : enemyBullets) { if (b.isValid()) { b.forward(); if (b.getLocationY() > screenH + 80) b.vanish(); } }
        for (AbstractProp   p : props)        { if (p.isValid()) { p.forward(); if (p.isOutOfScreen(screenH)) p.vanish(); } }

        collide(now);

        // ── 背景滚动
        bgY1 += 2.5f; bgY2 += 2.5f;
        if (bgY1 >= screenH) bgY1 = bgY2 - screenH;
        if (bgY2 >= screenH) bgY2 = bgY1 - screenH;

        // ── 清理
        enemies.removeIf(e -> !e.isValid());
        heroBullets.removeIf(b -> !b.isValid());
        enemyBullets.removeIf(b -> !b.isValid());
        props.removeIf(p -> !p.isValid());

        if (++hudFrameCounter >= HUD_THROTTLE) { hudFrameCounter = 0; postHUD(); }
        if (!hero.isValid()) gameOver();
    }

    private void collide(long now) {
        outer:
        for (AbstractBullet b : heroBullets) {
            if (!b.isValid()) continue;
            for (AbstractEnemyAircraft e : enemies) {
                if (!e.isValid()) continue;
                if (b.crash(e)) {
                    e.decreaseHp(GameConstant.HERO_BULLET_POWER); b.vanish();
                    if (!e.isValid()) {
                        score += e.getScoreValue(); sfx(() -> soundManager.playEnemyDie());
                        AbstractProp p = (e instanceof BossEnemy)
                            ? EnemyFactory.createPropFromBoss(e.getLocationX(), e.getLocationY(), mkBmp(propSz, propSz), mkBmp(propSz, propSz), mkBmp(propSz, propSz))
                            : EnemyFactory.createPropIfDropped(e.getLocationX(), e.getLocationY(), mkBmp(propSz, propSz), mkBmp(propSz, propSz), mkBmp(propSz, propSz));
                        if (p != null) props.add(p);
                    }
                    continue outer;
                }
            }
        }
        for (AbstractBullet b : enemyBullets) {
            if (b.isValid() && hero.isValid() && b.crash(hero)) { hero.decreaseHp(GameConstant.ENEMY_BULLET_POWER); b.vanish(); }
        }
        for (AbstractEnemyAircraft e : enemies) {
            if (e.isValid() && hero.isValid() && e.crash(hero)) { hero.decreaseHp(e.getHp()); e.vanish(); }
        }
        for (AbstractProp p : props) {
            if (!p.isValid() || !p.crash(hero)) continue;
            sfx(() -> soundManager.playPropGet());
            switch (p.getType()) {
                case BLOOD:  hero.heal(GameConstant.BLOOD_PROP_HEAL); break;
                case BOMB:
                    sfx(() -> soundManager.playBomb());
                    for (AbstractEnemyAircraft e : enemies) { score += e.getScoreValue(); e.vanish(); }
                    for (AbstractBullet b : enemyBullets) b.vanish();
                    break;
                case BULLET: hero.activateSpreadShoot(now); break;
            }
            p.vanish();
        }
    }

    // ════════════════════════════════════════════════════════════
    // OpenGL 渲染（GL线程）
    // ════════════════════════════════════════════════════════════

    private void render() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (screenW == 0) return;

        // ── 1. 背景（精灵）
        renderBg();
        if (!initialized) return;

        // ── 2. 道具（2D 精灵，保留贴图质感）
        if (spriteProg != 0) {
            GLES20.glUseProgram(spriteProg);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVBO);
            GLES20.glVertexAttribPointer(spAPos, 2, GLES20.GL_FLOAT, false, 16, 0);
            GLES20.glVertexAttribPointer(spAUV,  2, GLES20.GL_FLOAT, false, 16, 8);
            GLES20.glEnableVertexAttribArray(spAPos);
            GLES20.glEnableVertexAttribArray(spAUV);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, quadIBO);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glUniform1i(spUTex, 0);
            for (AbstractProp p : props) {
                if (!p.isValid()) continue;
                spr(propTex(p), p.getLocationX(), p.getLocationY(), propSz, propSz, 0f, false);
            }
        }

        // ── 3. 子弹（3D 菱形网格）
        for (AbstractBullet b : heroBullets)
            if (b.isValid()) spr3d(vboBulletH3d, vtxBulletH3d, b.getLocationX(), b.getLocationY(), bulletW, bulletH, 0f, false);
        for (AbstractBullet b : enemyBullets)
            if (b.isValid()) spr3d(vboBulletE3d, vtxBulletE3d, b.getLocationX(), b.getLocationY(), bulletW, bulletH, 0f, false);

        // ── 4. 敌机（3D 网格 + 描边）
        for (AbstractEnemyAircraft e : enemies) {
            if (!e.isValid()) continue;
            if      (e instanceof BossEnemy)  spr3d(vboBoss3d,  vtxBoss3d,  e.getLocationX(), e.getLocationY(), bossSz,  bossSz,  0f, true);
            else if (e instanceof EliteEnemy) spr3d(vboElite3d, vtxElite3d, e.getLocationX(), e.getLocationY(), eliteSz, eliteSz, 0f, true);
            else                              spr3d(vboMob3d,   vtxMob3d,   e.getLocationX(), e.getLocationY(), mobSz,   mobSz,   0f, true);
        }

        // ── 5. 英雄（3D 网格 + 翻滚 + 描边）
        if (hero.isValid())
            spr3d(vboHero3d, vtxHero3d, hero.getLocationX(), hero.getLocationY(), heroSz, heroSz, smoothBank, true);
    }

    private void renderBg() {
        if (bgProg == 0 || texBg == 0) return;
        GLES20.glUseProgram(bgProg);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVBO);
        GLES20.glVertexAttribPointer(bgAPos, 2, GLES20.GL_FLOAT, false, 16, 0);
        GLES20.glVertexAttribPointer(bgAUV,  2, GLES20.GL_FLOAT, false, 16, 8);
        GLES20.glEnableVertexAttribArray(bgAPos);
        GLES20.glEnableVertexAttribArray(bgAUV);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, quadIBO);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texBg);
        GLES20.glUniform1i(bgUTex, 0);
        bgQuad(screenW / 2f, screenH / 2f + bgY1);
        bgQuad(screenW / 2f, screenH / 2f + bgY2);
    }

    private void bgQuad(float cx, float cy) {
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, cx, cy, 0);
        Matrix.scaleM(mModel, 0, screenW, screenH, 1);
        Matrix.multiplyMM(mMVP, 0, mProj, 0, mModel, 0);
        GLES20.glUniformMatrix4fv(bgUMVP, 1, false, mMVP, 0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 0);
    }

    // ════════════════════════════════════════════════════════════
    // 3D 网格渲染（三Pass：阴影 → 描边 → Cel主体）
    // ════════════════════════════════════════════════════════════

    /**
     * 绘制程序化 3D 飞机网格（model3d 着色器）
     *
     * 顶点格式（stride=36 bytes）：[x,y,z | nx,ny,nz | r,g,b]  9 floats
     *
     * @param vbo      网格 VBO ID
     * @param vtxCnt   顶点数
     * @param cx, cy   屏幕像素坐标（中心）
     * @param w, h     显示尺寸（像素），转换为 3D 缩放
     * @param bankRad  翻滚弧度（Z轴旋转，仅英雄非零）
     * @param outline  是否绘制卡通描边
     */
    private void spr3d(int vbo, int vtxCnt, float cx, float cy, float w, float h,
                       float bankRad, boolean outline) {
        if (vbo == 0 || vtxCnt == 0 || model3dProg == 0) return;

        GLES20.glUseProgram(model3dProg);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        final int STRIDE = 36; // 9 × 4 bytes
        GLES20.glVertexAttribPointer(m3APos,    3, GLES20.GL_FLOAT, false, STRIDE,  0);
        GLES20.glVertexAttribPointer(m3ANormal, 3, GLES20.GL_FLOAT, false, STRIDE, 12);
        GLES20.glVertexAttribPointer(m3AColor,  3, GLES20.GL_FLOAT, false, STRIDE, 24);
        GLES20.glEnableVertexAttribArray(m3APos);
        GLES20.glEnableVertexAttribArray(m3ANormal);
        GLES20.glEnableVertexAttribArray(m3AColor);

        // View / Proj 上传（每 spr3d 一次，不随Pass改变）
        GLES20.glUniformMatrix4fv(m3UView,   1, false, mView3d, 0);
        GLES20.glUniformMatrix4fv(m3UProj3d, 1, false, mProj3d, 0);

        // 屏幕像素 → 3D 世界坐标映射
        // 摄像机以正交等效的方式将屏幕范围映射到 ±aspect × ±1
        float aspect = (float) screenW / screenH;
        float wx = (cx / screenW * 2f - 1f) * aspect * 1.85f;
        float wy = -(cy / screenH * 2f - 1f) * 1.85f; // Y轴翻转
        float scale = w / (float) screenW * aspect * 3.6f;

        // Pass 1 — 投影阴影（压扁 + 偏移 + 半透明）
        mvp3d(wx + 0.04f, wy - 0.07f, 0f, scale * 1.12f, scale * 0.12f, scale * 1.08f, 0f);
        GLES20.glUniform4fv(m3UTint,   1, T_SHADOW, 0);
        GLES20.glUniform1f(m3UOutline, 0f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vtxCnt);

        // Pass 2 — 卡通描边（片元着色器输出近黑色，略大）
        if (outline) {
            mvp3d(wx, wy, 0f, scale * 1.08f, scale * 1.08f, scale * 1.08f, bankRad);
            GLES20.glUniform4fv(m3UTint,   1, T_NORMAL, 0);
            GLES20.glUniform1f(m3UOutline, 1f);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vtxCnt);
        }

        // Pass 3 — Cel 主体着色
        mvp3d(wx, wy, 0f, scale, scale, scale, bankRad);
        GLES20.glUniform4fv(m3UTint,   1, T_NORMAL, 0);
        GLES20.glUniform1f(m3UOutline, 0f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vtxCnt);

        GLES20.glDisableVertexAttribArray(m3APos);
        GLES20.glDisableVertexAttribArray(m3ANormal);
        GLES20.glDisableVertexAttribArray(m3AColor);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    /** 设置 3D Model 矩阵并上传 uMVP = Proj × View × Model */
    private void mvp3d(float tx, float ty, float tz,
                       float sx, float sy, float sz, float bankRad) {
        Matrix.setIdentityM(mModel3d, 0);
        Matrix.translateM(mModel3d, 0, tx, ty, tz);
        if (bankRad != 0f)
            Matrix.rotateM(mModel3d, 0, (float) Math.toDegrees(bankRad), 0f, 0f, 1f);
        Matrix.scaleM(mModel3d, 0, sx, sy, sz);
        Matrix.multiplyMM(mVM3d,  0, mView3d, 0, mModel3d, 0);
        Matrix.multiplyMM(mMVP3d, 0, mProj3d, 0, mVM3d,    0);
        GLES20.glUniformMatrix4fv(m3UMVP, 1, false, mMVP3d, 0);
    }

    // ════════════════════════════════════════════════════════════
    // 2D 精灵渲染（道具 + 背景用）
    // ════════════════════════════════════════════════════════════

    /** 绘制 2D 精灵（三Pass：阴影 → 描边 → 主体），需先 useProgram(spriteProg) */
    private void spr(int texId, float cx, float cy, float w, float h,
                     float bank, boolean outline) {
        if (texId == 0) return;
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);

        // Pass 1 — 投影阴影
        mvp2d(cx + w * 0.07f, cy + h * 0.09f, w * 1.1f, h * 1.05f, 0f, 0f);
        GLES20.glUniform4fv(spUTint, 1, T_SHADOW, 0);
        GLES20.glUniform1f(spUCelSteps, 0f);
        GLES20.glUniform1f(spUOutline,  0f);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 0);

        // Pass 2 — 卡通描边
        if (outline) {
            mvp2d(cx, cy, w * 1.08f, h * 1.08f, bank, bank * 0.65f);
            GLES20.glUniform4fv(spUTint, 1, T_NORMAL, 0);
            GLES20.glUniform1f(spUCelSteps, 0f);
            GLES20.glUniform1f(spUOutline,  1f);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 0);
        }

        // Pass 3 — 主体
        mvp2d(cx, cy, w, h, bank, bank * 0.65f);
        GLES20.glUniform4fv(spUTint, 1, T_NORMAL, 0);
        GLES20.glUniform1f(spUCelSteps, 3f);
        GLES20.glUniform1f(spUOutline,  0f);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 0);
    }

    private void mvp2d(float cx, float cy, float w, float h, float bankRad, float bankY) {
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, cx, cy, 0);
        Matrix.scaleM(mModel, 0, w, h, 1);
        Matrix.multiplyMM(mMVP, 0, mProj, 0, mModel, 0);
        GLES20.glUniformMatrix4fv(spUMVP,   1, false, mMVP, 0);
        GLES20.glUniform1f(spUBankRad, bankRad);
        GLES20.glUniform1f(spUBankY,   bankY);
    }

    // ════════════════════════════════════════════════════════════
    // GL 工具
    // ════════════════════════════════════════════════════════════

    private void buildQuad() {
        float[] verts = {
            -0.5f, -0.5f, 0f, 0f,
            -0.5f,  0.5f, 0f, 1f,
             0.5f,  0.5f, 1f, 1f,
             0.5f, -0.5f, 1f, 0f,
        };
        short[] idx = {0, 1, 2, 0, 2, 3};

        FloatBuffer vb = ByteBuffer.allocateDirect(verts.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vb.put(verts).position(0);

        ShortBuffer ib = ByteBuffer.allocateDirect(idx.length * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        ib.put(idx).position(0);

        int[] bufs = new int[2];
        GLES20.glGenBuffers(2, bufs, 0);
        quadVBO = bufs[0]; quadIBO = bufs[1];

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVBO);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.length * 4, vb, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, quadIBO);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, idx.length * 2, ib, GLES20.GL_STATIC_DRAW);
    }

    /**
     * 将 AircraftMesh 生成的 float[] 数据上传为 GL VBO
     * @return [vboId, vertexCount]
     */
    private int[] buildMeshVBO(int meshType) {
        float[] data = AircraftMesh.build(meshType);
        int vtxCnt = data.length / 9; // 9 floats per vertex
        FloatBuffer fb = ByteBuffer.allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(data).position(0);
        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.length * 4, fb, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        return new int[]{ids[0], vtxCnt};
    }

    private int tex(int resId) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            Bitmap bmp = BitmapFactory.decodeResource(getResources(), resId, o);
            if (bmp != null) { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0); bmp.recycle(); }
        } catch (Exception e) { Log.e(TAG, "tex load fail " + resId, e); }
        return ids[0];
    }

    private String raw(int resId) {
        try (InputStream is = getContext().getResources().openRawResource(resId)) {
            byte[] b = new byte[is.available()]; is.read(b); return new String(b);
        } catch (Exception e) { return "void main(){}"; }
    }

    private int linkProg(String vert, String frag) {
        int v = sh(GLES20.GL_VERTEX_SHADER,   vert);
        int f = sh(GLES20.GL_FRAGMENT_SHADER, frag);
        if (v == 0 || f == 0) return 0;
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] st = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, st, 0);
        if (st[0] == 0) { Log.e(TAG, "link: " + GLES20.glGetProgramInfoLog(p)); return 0; }
        return p;
    }

    private int sh(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src); GLES20.glCompileShader(s);
        int[] st = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, st, 0);
        if (st[0] == 0) { Log.e(TAG, "shader: " + GLES20.glGetShaderInfoLog(s)); GLES20.glDeleteShader(s); return 0; }
        return s;
    }

    // ════════════════════════════════════════════════════════════
    // 辅助
    // ════════════════════════════════════════════════════════════

    private static Bitmap mkBmp(int w, int h) {
        return Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ALPHA_8);
    }

    private int propTex(AbstractProp p) {
        switch (p.getType()) {
            case BOMB:   return texPropBomb;
            case BULLET: return texPropBullet;
            default:     return texPropBlood;
        }
    }

    private void postHUD() {
        final int s  = score;
        final int hp = hero.isValid() ? hero.getHp() : 0;
        final int mx = hero.getMaxHp();
        int bh = -1, bm = 1;
        for (AbstractEnemyAircraft e : enemies)
            if (e instanceof BossEnemy && e.isValid()) { bh = e.getHp(); bm = e.getMaxHp(); break; }
        final int bossHp = bh, bossMax = bm;
        mainHandler.post(() -> {
            if (hudCallback != null) hudCallback.update(s, hp, mx, bossHp, bossMax);
            if (hudScoreView != null) hudScoreView.setText("SCORE   " + s);
            if (hudHPView    != null) hudHPView.setText("♥  " + hp + " / " + mx);
            if (hudBossView  != null) {
                if (bossHp >= 0) { hudBossView.setVisibility(View.VISIBLE); hudBossView.setText("BOSS  " + bossHp + " / " + bossMax); }
                else             { hudBossView.setVisibility(View.GONE); }
            }
        });
    }

    private void gameOver() {
        if (gameState == GameState.GAME_OVER) return;
        gameState = GameState.GAME_OVER;
        sfx(() -> { soundManager.playHeroDie(); soundManager.pauseBgm(); });
        if (scoreRepository != null) try { scoreRepository.saveScore(GameConstant.DEFAULT_PLAYER_NAME, score, difficulty); } catch (Exception e) {}
        mainHandler.postDelayed(() -> { if (gameOverCallback != null) gameOverCallback.onGameOver(score); }, 800);
    }

    private void sfx(Runnable r) { if (soundManager != null) try { r.run(); } catch (Exception e) {} }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getAction();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            heroTargetX = e.getX(); heroTargetY = e.getY();
        }
        return true;
    }
}
