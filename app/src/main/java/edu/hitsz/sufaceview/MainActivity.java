package edu.hitsz.sufaceview;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import edu.hitsz.db.UserRepository;
import edu.hitsz.game.GameConstant;
import edu.hitsz.ui.*;

/**
 * 主菜单 — 皇室战争 / 海岛奇兵风格大厅
 *
 * 布局：
 *   [顶部] 用户信息栏（头像 | 名称 | 💎水晶）
 *   [中央] HeroPreviewView（动态展示所选英雄，带浮动动画）
 *   [下部] 主菜单按钮组
 */
public class MainActivity extends AppCompatActivity {

    private UserRepository repo;
    private HeroPreviewView heroPreview;
    private TextView tvGems;

    // 英雄定义（与 GachaActivity 同步）
    static final String[] HERO_KEYS  = {"phoenix","thunder","nova","viper","aurora","glacier",
                                         "tempest","leviathan","omega","spark","sentinel","cipher"};
    static final String[] HERO_EMOJI = {"🔥","⚡","💫","🐍","🌌","🧊",
                                         "🌪️","🐋","☢️","✨","🛡️","🔮"};
    static final String[] HERO_NAMES = {"凤凰","雷神","新星","毒蛇","极光","冰川",
                                         "风暴","巨鲸","奥米伽","闪电","哨兵","密码"};
    static final String[] HERO_RARITY= {"SSR","SSR","SSR","SR","SR","SR","SR","R","R","R","R","R"};
    static final int[]    RARITY_CLR = {0xFFFFD700,0xFFFF6B00,0xFFC0C0C0}; // SSR金/SR橙/R银

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = UserRepository.getInstance(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } catch (Exception ignored) {}
        setContentView(buildUI());
        refreshGems();
    }

    @Override protected void onResume() {
        super.onResume();
        try { setContentView(buildUI()); refreshGems(); } catch (Exception ignored) {}
    }

    private void refreshGems() {
        repo.getGems(gems -> new Handler(Looper.getMainLooper()).post(() -> {
            if (tvGems != null) tvGems.setText("💎 " + gems);
            // 缓存到 SharedPreferences 供 LoginActivity.getGems 快速读取
            getSharedPreferences("AircraftWarSession", MODE_PRIVATE)
                .edit().putInt("cached_gems", gems).apply();
        }));
    }

    private View buildUI() {
        String user     = repo.getCurrentUser();
        String heroKey  = repo.getSelectedHeroCached();
        int    heroIdx  = keyToIdx(heroKey);

        // 根布局
        FrameLayout root = new FrameLayout(this);
        // 星空渐变背景
        GradientDrawable sky = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF03030F, 0xFF070720, 0xFF0A0A2A});
        root.setBackground(sky);

        // 主内容（垂直线性）
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        // ── 顶部用户信息栏 ──────────────────────────────────────────
        LinearLayout topBar = buildTopBar(user);
        col.addView(topBar, mw());

        // ── 游戏标题 ────────────────────────────────────────────────
        vsp(col, 8);
        TextView title = new TextView(this);
        title.setText("⚡ 飞机大战");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        title.setTextColor(0xFFFFD700);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setShadowLayer(18, 0, 0, 0xCCFF8C00);
        col.addView(title, mw());

        // ── 英雄展示区域（皇室战争风格） ────────────────────────────
        vsp(col, 12);
        heroPreview = new HeroPreviewView(this, heroIdx);
        int previewH = (int)(getResources().getDisplayMetrics().heightPixels * 0.30f);
        col.addView(heroPreview, new LinearLayout.LayoutParams(mw().width, previewH));

        // 英雄信息条（姓名 + 稀有度）
        LinearLayout heroInfo = new LinearLayout(this);
        heroInfo.setOrientation(LinearLayout.HORIZONTAL);
        heroInfo.setGravity(Gravity.CENTER);
        heroInfo.setPadding(0, dp(4), 0, dp(4));

        String rarity = HERO_RARITY[heroIdx];
        int rarClr = "SSR".equals(rarity) ? RARITY_CLR[0] : "SR".equals(rarity) ? RARITY_CLR[1] : RARITY_CLR[2];
        TextView tvHeroName = tv(HERO_EMOJI[heroIdx] + "  " + HERO_NAMES[heroIdx], 18, 0xFFEEEEEE, true);
        TextView tvRarity   = tv("  [" + rarity + "]", 16, rarClr, true);
        tvRarity.setShadowLayer(6, 0, 0, rarClr);
        heroInfo.addView(tvHeroName); heroInfo.addView(tvRarity);
        col.addView(heroInfo, mw());

        // 点击英雄展示区 → 去抽卡/选英雄
        heroPreview.setOnClickListener(v ->
            startActivity(new Intent(this, GachaActivity.class)));

        // ── 按钮组 ───────────────────────────────────────────────────
        vsp(col, 14);
        col.addView(menuBtn("🎮  开始游戏", 0xFF2979FF, 0xFF1565C0, v -> showDifficultyDialog()), mw());
        vsp(col, 10);
        col.addView(menuBtn("✨  英雄召唤", 0xFF7B1FA2, 0xFFAA00FF,
            v -> startActivity(new Intent(this, GachaActivity.class))), mw());
        vsp(col, 10);
        col.addView(menuBtn("🏆  排行榜", 0xFF00796B, 0xFF00BFA5,
            v -> startActivity(new Intent(this, LeaderboardActivity.class))), mw());
        vsp(col, 10);
        col.addView(menuBtn("⚙  设置", 0xFF37474F, 0xFF546E7A,
            v -> startActivity(new Intent(this, SettingsActivity.class))), mw());
        vsp(col, 16);

        Button btnExit = new Button(this);
        btnExit.setText("退出游戏"); btnExit.setTextColor(0xFF546E7A);
        btnExit.setBackgroundColor(Color.TRANSPARENT);
        btnExit.setOnClickListener(v -> finishAffinity());
        col.addView(btnExit, mw());

        // 版本号
        vsp(col, 8);
        TextView ver = tv("v3.0-OpenGL · HeroSystem ©2026", 11, 0xFF2E3E4E, false);
        ver.setGravity(Gravity.CENTER); col.addView(ver, mw());

        // 外边距
        FrameLayout.LayoutParams colLP = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        col.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.addView(col, colLP);
        return root;
    }

    private LinearLayout buildTopBar(String user) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC111827); bg.setStroke(dp(1), 0xFF1E3050);
        bar.setBackground(bg);

        // 用户名
        TextView tvName = tv("👤  " + user, 15, 0xFFCCDDEE, false);
        bar.addView(tvName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 水晶
        tvGems = tv("💎 ...", 15, 0xFFFFD700, false);
        bar.addView(tvGems);

        // 切换账户
        Button btnSwitch = new Button(this);
        btnSwitch.setText("切换"); btnSwitch.setTextColor(0xFF546E7A);
        btnSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnSwitch.setBackgroundColor(Color.TRANSPARENT);
        btnSwitch.setPadding(dp(8),0,dp(4),0);
        btnSwitch.setOnClickListener(v -> {
            repo.logout();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        bar.addView(btnSwitch);
        return bar;
    }

    private void showDifficultyDialog() {
        final String[] items = {"😊  简单（推荐新手）", "🔥  困难（高手挑战）", "💀  地狱（极限挑战）"};
        final String[] keys  = {GameConstant.DIFFICULTY_EASY, GameConstant.DIFFICULTY_HARD, GameConstant.DIFFICULTY_HELL};
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择难度")
            .setItems(items, (d, i) -> {
                Intent intent = new Intent(this, GameActivity.class);
                intent.putExtra("difficulty", keys[i]);
                startActivity(intent);
            }).show();
    }

    /** 主菜单按钮（渐变 + 点击缩放） */
    private View menuBtn(String text, int c1, int c2, View.OnClickListener l) {
        Button b = new Button(this); b.setText(text);
        b.setTextColor(Color.WHITE); b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        bg.setCornerRadius(dp(14)); bg.setStroke(dp(1), 0x33FFFFFF);
        b.setBackground(bg); b.setPadding(0, dp(12), 0, dp(12));
        b.setOnClickListener(l);
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN)
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            return false;
        });
        return b;
    }

    // ════════════════════════════════════════════════════════════
    // HeroPreviewView — 皇室战争风格英雄展示
    // ════════════════════════════════════════════════════════════
    static class HeroPreviewView extends View {
        private final int heroIdx;
        private float floatOffset = 0f;   // 浮动动画偏移（像素）
        private float glowAlpha   = 0.4f; // 发光脉冲
        private float particlePhase = 0f; // 粒子旋转相位
        private ValueAnimator floatAnim, glowAnim;

        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint platPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF ovalRect  = new RectF();

        HeroPreviewView(Context ctx, int idx) {
            super(ctx);
            this.heroIdx = Math.max(0, Math.min(HERO_KEYS.length - 1, idx));
            textPaint.setTextAlign(Paint.Align.CENTER);

            // 浮动上下动画 ±8dp
            int dp8 = (int)(8 * ctx.getResources().getDisplayMetrics().density);
            floatAnim = ValueAnimator.ofFloat(-dp8, dp8);
            floatAnim.setDuration(2000); floatAnim.setRepeatCount(ValueAnimator.INFINITE);
            floatAnim.setRepeatMode(ValueAnimator.REVERSE);
            floatAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            floatAnim.addUpdateListener(a -> { floatOffset = (float) a.getAnimatedValue(); invalidate(); });

            // 发光脉冲 0.3~0.7
            glowAnim = ValueAnimator.ofFloat(0.3f, 0.7f);
            glowAnim.setDuration(1400); glowAnim.setRepeatCount(ValueAnimator.INFINITE);
            glowAnim.setRepeatMode(ValueAnimator.REVERSE);
            glowAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            glowAnim.addUpdateListener(a -> { glowAlpha = (float) a.getAnimatedValue(); });

            floatAnim.start(); glowAnim.start();
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            floatAnim.cancel(); glowAnim.cancel();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            float cx = w / 2f, cy = h / 2f;

            // ── 台座（椭圆渐变平台，类海岛奇兵） ─────────────────────
            float platW = w * 0.55f, platH = h * 0.18f;
            float platCy = h * 0.82f;
            ovalRect.set(cx - platW/2, platCy - platH/2, cx + platW/2, platCy + platH/2);

            // 台座阴影
            platPaint.setColor(0x55000000);
            canvas.drawOval(ovalRect, platPaint);

            // 台座主体渐变
            int rarClr = rarityColor(heroIdx);
            int rarDark = blendColor(rarClr, 0xFF111111, 0.6f);
            RadialGradient platGrad = new RadialGradient(cx, platCy, platW/2,
                    new int[]{rarClr & 0x99FFFFFF | (rarDark & 0xFFFFFF), 0x00000000},
                    new float[]{0.4f, 1f}, Shader.TileMode.CLAMP);
            platPaint.setShader(platGrad);
            canvas.drawOval(ovalRect, platPaint);
            platPaint.setShader(null);

            // ── 稀有度光环（径向发光圆） ───────────────────────────────
            float glowR = h * 0.33f;
            float glowCy = platCy - h * 0.22f; // 英雄中心Y
            int glowColor = (int)(glowAlpha * 0xFF) << 24 | (rarClr & 0xFFFFFF);
            RadialGradient glow = new RadialGradient(cx, glowCy, glowR,
                    new int[]{glowColor, 0x00000000}, null, Shader.TileMode.CLAMP);
            glowPaint.setShader(glow);
            canvas.drawCircle(cx, glowCy, glowR, glowPaint);
            glowPaint.setShader(null);

            // ── 粒子环（SSR：8颗，SR：5颗，R：无） ────────────────────
            String rarity = HERO_RARITY[heroIdx];
            int particles = "SSR".equals(rarity) ? 8 : "SR".equals(rarity) ? 5 : 0;
            if (particles > 0) {
                particlePhase = (particlePhase + 0.008f) % (2 * (float)Math.PI);
                Paint pPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                pPaint.setColor(rarClr);
                float pR = h * 0.30f;
                for (int i = 0; i < particles; i++) {
                    float ang = particlePhase + i * (float)(2 * Math.PI / particles);
                    float px = cx + pR * (float)Math.cos(ang);
                    float py = glowCy + pR * 0.6f * (float)Math.sin(ang);
                    float sr = 3f + (float)Math.sin(ang + particlePhase * 2) * 2f;
                    pPaint.setAlpha((int)(180 + 60 * Math.sin(ang * 2)));
                    canvas.drawCircle(px, py, sr, pPaint);
                }
                invalidate(); // 粒子需要持续重绘
            }

            // ── 英雄 Emoji ─────────────────────────────────────────────
            float emojiSize = h * 0.42f;
            textPaint.setTextSize(emojiSize);
            textPaint.setColor(0xFFFFFFFF);
            float emojiY = platCy - h * 0.16f + floatOffset;
            // 投影
            textPaint.setColor(0x55000000);
            canvas.drawText(HERO_EMOJI[heroIdx], cx + 4, emojiY + 6, textPaint);
            // 主体
            textPaint.setColor(0xFFFFFFFF);
            canvas.drawText(HERO_EMOJI[heroIdx], cx, emojiY, textPaint);

            // ── 提示 ─────────────────────────────────────────────────
            textPaint.setTextSize(h * 0.085f);
            textPaint.setColor(0x88AABBCC);
            canvas.drawText("点击更换英雄", cx, platCy + platH / 2 + h * 0.07f, textPaint);
        }

        private int rarityColor(int idx) {
            String r = HERO_RARITY[idx];
            return "SSR".equals(r) ? 0xFFFFD700 : "SR".equals(r) ? 0xFFFF6B00 : 0xFFAAAAAA;
        }

        private int blendColor(int a, int b, float t) {
            int ra = (a>>16&0xFF), ga = (a>>8&0xFF), ba2 = (a&0xFF);
            int rb = (b>>16&0xFF), gb = (b>>8&0xFF), bb2 = (b&0xFF);
            return 0xFF000000 | ((int)(ra+t*(rb-ra))<<16) | ((int)(ga+t*(gb-ga))<<8) | (int)(ba2+t*(bb2-ba2));
        }
    }

    // ════════════════════════════════════════════════════════════
    // 工具
    // ════════════════════════════════════════════════════════════
    static int keyToIdx(String key) {
        if (key == null || key.isEmpty()) return 0;
        for (int i = 0; i < HERO_KEYS.length; i++) if (HERO_KEYS[i].equals(key)) return i;
        return 0;
    }
    private TextView tv(String text, int sp, int clr, boolean bold) {
        TextView t = new TextView(this); t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); t.setTextColor(clr);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t;
    }
    private void vsp(ViewGroup p, int dpVal) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dpVal)));
        p.addView(v);
    }
    private LinearLayout.LayoutParams mw() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
