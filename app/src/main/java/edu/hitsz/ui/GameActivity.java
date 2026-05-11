package edu.hitsz.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import edu.hitsz.db.UserRepository;
import edu.hitsz.game.GameGLView;

/**
 * 游戏主界面
 *
 * HUD 布局（不重叠版）：
 *   ┌──[分数]────────────[BOSS HP]──┐  ← 顶部单行工具栏
 *   │           游戏画面             │
 *   └──────────[英雄HP]─────────────┘  ← 底部血条
 */
public class GameActivity extends AppCompatActivity {

    private GameGLView gameView;
    private HpBarView heroHpBar, bossHpBar;
    private TextView tvScore, tvBossLabel;
    private LinearLayout bossSection;  // BOSS 血条容器（右侧或隐藏）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat ctrl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctrl.hide(WindowInsetsCompat.Type.systemBars());
        ctrl.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        String difficulty = getIntent().getStringExtra("difficulty");

        FrameLayout root = new FrameLayout(this);
        gameView = new GameGLView(this);
        root.addView(gameView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(buildHUD(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        if (difficulty != null) gameView.setDifficulty(difficulty);
        gameView.setHudViews2(this::updateHUD);

        gameView.setGameOverCallback(score -> {
            int gemReward = score / 100;
            if (gemReward > 0) UserRepository.getInstance(this).addGems(gemReward);
            Intent i = new Intent(this, GameOverActivity.class);
            i.putExtra("score", score);
            i.putExtra("difficulty", difficulty);
            i.putExtra("gem_reward", gemReward);
            startActivity(i); finish();
        });

        setContentView(root);
    }

    /** 构建 HUD — 顶部单行工具栏，底部血条，无重叠 */
    private View buildHUD() {
        FrameLayout hud = new FrameLayout(this);
        hud.setClickable(false); hud.setFocusable(false);

        // ══ 顶部工具栏（一整行：左分数 | 中间弹性 | 右BOSS HP） ══════
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(10), dp(12), dp(10));

        // 半透明背景
        GradientDrawable tbBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xDD060620, 0xBB060620});
        topBar.setBackground(tbBg);

        // 分数（左）
        tvScore = new TextView(this);
        tvScore.setText("⚡ 0");
        tvScore.setTextColor(0xFFFFD700);
        tvScore.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tvScore.setTypeface(Typeface.DEFAULT_BOLD);
        tvScore.setShadowLayer(6, 1, 1, 0xFF000000);
        topBar.addView(tvScore, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 弹性中间
        View spacer = new View(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        // BOSS HP（右，隐藏时 GONE）
        bossSection = new LinearLayout(this);
        bossSection.setOrientation(LinearLayout.VERTICAL);
        bossSection.setGravity(Gravity.CENTER_HORIZONTAL);
        bossSection.setVisibility(View.GONE);

        tvBossLabel = new TextView(this);
        tvBossLabel.setText("⚔ BOSS");
        tvBossLabel.setTextColor(0xFFFF5555);
        tvBossLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvBossLabel.setTypeface(Typeface.DEFAULT_BOLD);
        bossSection.addView(tvBossLabel);

        bossHpBar = new HpBarView(this, 0xFFFF3333, 0xFFFF8800);
        bossSection.addView(bossHpBar, new LinearLayout.LayoutParams(dp(160), dp(12)));
        topBar.addView(bossSection);

        FrameLayout.LayoutParams tbLP = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        tbLP.gravity = Gravity.TOP;
        hud.addView(topBar, tbLP);

        // ══ 底部英雄 HP 血条 ═══════════════════════════════════════
        LinearLayout hpWrap = new LinearLayout(this);
        hpWrap.setOrientation(LinearLayout.VERTICAL);
        hpWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        hpWrap.setPadding(dp(20), dp(10), dp(20), dp(14));
        GradientDrawable hpBg = new GradientDrawable();
        hpBg.setCornerRadius(dp(18)); hpBg.setColor(0xCC060620);
        hpBg.setStroke(dp(1), 0x44FFFFFF);
        hpWrap.setBackground(hpBg);

        TextView hpLabel = new TextView(this);
        hpLabel.setText("♥  生命值");
        hpLabel.setTextColor(0xFF80CBC4);
        hpLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hpLabel.setGravity(Gravity.CENTER);
        hpWrap.addView(hpLabel, new LinearLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT));

        heroHpBar = new HpBarView(this, 0xFF00E676, 0xFF69F0AE);
        hpWrap.addView(heroHpBar, new LinearLayout.LayoutParams(dp(220), dp(18)));

        FrameLayout.LayoutParams hpLP = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        hpLP.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        hpLP.setMargins(0, 0, 0, dp(24));
        hud.addView(hpWrap, hpLP);

        return hud;
    }

    private void updateHUD(int score, int hp, int maxHp, int bossHp, int bossMax) {
        if (tvScore != null) tvScore.setText("⚡ " + score);
        if (heroHpBar != null) heroHpBar.setProgress(hp, maxHp);
        if (bossSection != null) {
            if (bossHp >= 0) {
                bossSection.setVisibility(View.VISIBLE);
                if (tvBossLabel != null)
                    tvBossLabel.setText("⚔ BOSS  " + bossHp + "/" + bossMax);
                if (bossHpBar != null) bossHpBar.setProgress(bossHp, bossMax);
            } else {
                bossSection.setVisibility(View.GONE);
            }
        }
    }

    @Override protected void onPause()   { super.onPause();   if (gameView!=null) gameView.pause(); }
    @Override protected void onResume()  { super.onResume();  if (gameView!=null) gameView.resumeGame(); }
    @Override protected void onDestroy() { super.onDestroy(); if (gameView!=null) gameView.onDestroyView(); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    // ════════════════════════════════════════════════════════════
    // 渐变血条 View
    // ════════════════════════════════════════════════════════════
    public static class HpBarView extends View {
        private float progress = 1f;
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bg  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int c1, c2;

        public HpBarView(Context ctx, int c1, int c2) {
            super(ctx); this.c1=c1; this.c2=c2;
            bg.setColor(0xFF1A2A1A);
        }

        public void setProgress(int hp, int maxHp) {
            progress = maxHp>0 ? Math.max(0f, Math.min(1f, (float)hp/maxHp)) : 0f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w=getWidth(), h=getHeight(), r=h/2f;
            rect.set(0,0,w,h); canvas.drawRoundRect(rect, r, r, bg);
            if (progress > 0) {
                int lc1 = progress>0.3f ? c1 : 0xFFFF3333;
                int lc2 = progress>0.3f ? c2 : 0xFFFF6600;
                LinearGradient g = new LinearGradient(0,0,w*progress,0, lc1, lc2, Shader.TileMode.CLAMP);
                bar.setShader(g);
                rect.set(0,0,w*progress,h); canvas.drawRoundRect(rect,r,r,bar);
                // 高光
                Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG); shine.setColor(0x44FFFFFF);
                rect.set(2,2,w*progress-2,h*0.45f);
                if (rect.width()>0) canvas.drawRoundRect(rect,r*0.5f,r*0.5f,shine);
            }
            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE); border.setStrokeWidth(1.5f); border.setColor(0x55FFFFFF);
            rect.set(0.75f,0.75f,w-0.75f,h-0.75f); canvas.drawRoundRect(rect,r,r,border);
        }
    }

    public interface HudCallback {
        void update(int score, int hp, int maxHp, int bossHp, int bossMax);
    }
}
