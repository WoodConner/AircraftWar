package edu.hitsz.ui;

import android.animation.*;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import java.util.*;
import java.util.concurrent.Executors;

import edu.hitsz.db.AppDatabase;
import edu.hitsz.db.UserDao;

/**
 * 抽卡界面 — 英雄飞机抽取系统
 *
 * 卡池：
 *   SSR（超稀有）5%  — 特殊战机（双倍火力、高HP、全屏炸弹技能）
 *   SR （稀有）  22% — 精英战机（高速、追踪弹）
 *   R  （普通）  73% — 标准战机（普通属性提升）
 *
 * 费用：单抽 160 水晶，十连 1500 水晶（优惠）
 * 保底：每 90 抽必出一张 SSR
 */
public class GachaActivity extends AppCompatActivity {

    // ── 英雄定义 ──────────────────────────────────────────────────
    public enum Rarity { R, SR, SSR }

    public static class Hero {
        public final String key, name, desc, emoji;
        public final Rarity rarity;
        public final int hpBonus, fireBonus, speedBonus;
        Hero(String key, String name, String desc, String emoji, Rarity r, int hp, int fire, int spd) {
            this.key=key; this.name=name; this.desc=desc; this.emoji=emoji;
            rarity=r; hpBonus=hp; fireBonus=fire; speedBonus=spd;
        }
    }

    private static final List<Hero> POOL = Arrays.asList(
        // SSR
        new Hero("aurora",   "极光战神",  "传说级战机，双重等离子炮，超高护盾", "🌟", Rarity.SSR, 150, 100, 30),
        new Hero("phoenix",  "不死凤凰",  "涅槃重生，每20秒自动回血30点",      "🔥", Rarity.SSR, 200, 80,  20),
        new Hero("storm",    "风暴领主",  "风暴护甲，所有子弹速度+50%",         "⚡", Rarity.SSR, 100, 120, 50),
        // SR
        new Hero("falcon",   "游隼战机",  "极速突击，机动性+40%",              "🦅", Rarity.SR,  80,  60,  60),
        new Hero("specter",  "幽灵战机",  "隐形技能，子弹穿透敌机",             "👻", Rarity.SR,  60,  80,  40),
        new Hero("viper",    "毒蛇战机",  "追踪导弹，自动瞄准最近敌机",          "🐍", Rarity.SR,  70,  70,  30),
        new Hero("titan",    "泰坦战机",  "重装甲，HP+80，移速略低",            "🛡", Rarity.SR,  100, 50,  -10),
        // R
        new Hero("eagle",    "鹰式战机",  "标准战机，火力平衡",                 "✈",  Rarity.R,   40,  30,  20),
        new Hero("swift",    "迅捷战机",  "轻型战机，机动性强",                 "💨", Rarity.R,   30,  25,  40),
        new Hero("blaze",    "烈焰战机",  "火焰弹，对敌机持续伤害",             "🔶", Rarity.R,   35,  40,  15),
        new Hero("frost",    "冰晶战机",  "冰冻子弹，命中减速敌机",             "❄",  Rarity.R,   35,  35,  20),
        new Hero("thunder",  "雷霆战机",  "闪电链，子弹弹射",                   "🌩", Rarity.R,   30,  45,  15)
    );

    private static final String PREFS    = "AircraftWarUser";
    private static final String PITY_KEY = "gacha_pity_"; // + username
    private static final String GEMS_KEY = "gems_";       // + username

    private UserDao userDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView tvGems, tvResult;
    private LinearLayout cardContainer;
    private ScrollView scrollResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try { userDao = AppDatabase.getInstance(this).userDao(); } catch (Exception e) { userDao = null; }
        setContentView(buildLayout());
        refreshGems();
    }

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF08081A);

        // ── 顶部栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        topBar.setBackgroundColor(0xFF0D0D26);

        Button btnBack = iconBtn("← 返回");
        btnBack.setOnClickListener(v -> finish());
        topBar.addView(btnBack);

        TextView title = new TextView(this);
        title.setText("✨ 英雄召唤");
        title.setTextColor(0xFFFFD700);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        topBar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        tvGems = new TextView(this);
        tvGems.setTextColor(0xFFFFD700);
        tvGems.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        topBar.addView(tvGems);

        root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 卡池说明
        TextView poolInfo = new TextView(this);
        poolInfo.setText("SSR 5%（保底90抽）  ·  SR 22%  ·  R 73%\n单抽 160 💎  十连 1500 💎（优惠-100）");
        poolInfo.setTextColor(0xFF90A4AE);
        poolInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        poolInfo.setGravity(Gravity.CENTER);
        poolInfo.setPadding(dp(16), dp(10), dp(16), dp(10));
        root.addView(poolInfo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 抽卡按钮
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(dp(24), dp(8), dp(24), dp(8));

        Button btn1 = pullBtn("单 抽\n160 💎", 0xFF1565C0, 0xFF2979FF);
        btn1.setOnClickListener(v -> doPull(1));
        btnRow.addView(btn1, new LinearLayout.LayoutParams(0, dp(72), 1f));

        View gap = new View(this); gap.setLayoutParams(new LinearLayout.LayoutParams(dp(16), 1));
        btnRow.addView(gap);

        Button btn10 = pullBtn("十 连\n1500 💎", 0xFF6A0080, 0xFFAA00FF);
        btn10.setOnClickListener(v -> doPull(10));
        btnRow.addView(btn10, new LinearLayout.LayoutParams(0, dp(72), 1f));

        root.addView(btnRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 结果区域
        scrollResult = new ScrollView(this);
        LinearLayout resultWrap = new LinearLayout(this);
        resultWrap.setOrientation(LinearLayout.VERTICAL);
        resultWrap.setPadding(dp(12), dp(8), dp(12), dp(8));

        tvResult = new TextView(this);
        tvResult.setText("➡ 点击抽卡获取强力战机英雄！");
        tvResult.setTextColor(0xFF78909C);
        tvResult.setGravity(Gravity.CENTER);
        tvResult.setPadding(0, dp(20), 0, dp(20));
        resultWrap.addView(tvResult, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        cardContainer = new LinearLayout(this);
        cardContainer.setOrientation(LinearLayout.VERTICAL);
        resultWrap.addView(cardContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollResult.addView(resultWrap);
        root.addView(scrollResult, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ── 底部：已拥有英雄
        TextView ownedTitle = new TextView(this);
        ownedTitle.setText("── 我的英雄库 ──");
        ownedTitle.setTextColor(0xFF607D8B);
        ownedTitle.setGravity(Gravity.CENTER);
        ownedTitle.setPadding(0, dp(6), 0, dp(4));
        root.addView(ownedTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView ownedScroll = new HorizontalScrollView(this);
        LinearLayout ownedRow = new LinearLayout(this);
        ownedRow.setOrientation(LinearLayout.HORIZONTAL);
        ownedRow.setPadding(dp(8), dp(4), dp(8), dp(12));
        ownedScroll.addView(ownedRow);
        root.addView(ownedScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        refreshOwnedRow(ownedRow);
        return root;
    }

    private void doPull(int count) {
        int cost = count == 1 ? 160 : 1500;
        if (!spendGems(cost)) {
            toast("💎 水晶不足！玩游戏赢取水晶"); return;
        }
        refreshGems();

        // 抽取结果
        List<Hero> results = new ArrayList<>();
        String user = LoginActivity.getCurrentUser(this);
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int pity = sp.getInt(PITY_KEY + user, 0);

        for (int i = 0; i < count; i++) {
            pity++;
            Hero h = roll(pity);
            if (h.rarity == Rarity.SSR) pity = 0;
            results.add(h);
            saveHero(h);
        }
        sp.edit().putInt(PITY_KEY + user, pity).apply();

        showResults(results);
    }

    private Hero roll(int pity) {
        double r = Math.random();
        // 保底：90抽必出SSR
        if (pity >= 90) return randOf(Rarity.SSR);
        // 软保底：75抽后SSR概率递增
        double ssrRate = (pity >= 75) ? 0.05 + (pity - 75) * 0.06 : 0.05;
        if (r < ssrRate)       return randOf(Rarity.SSR);
        if (r < ssrRate + 0.22) return randOf(Rarity.SR);
        return randOf(Rarity.R);
    }

    private Hero randOf(Rarity rarity) {
        List<Hero> pool = new ArrayList<>();
        for (Hero h : POOL) if (h.rarity == rarity) pool.add(h);
        return pool.get((int)(Math.random() * pool.size()));
    }

    private void saveHero(Hero h) {
        String user = LoginActivity.getCurrentUser(this);
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        // 保存为：已拥有列表，重复则记录数量
        int count = sp.getInt("hero_" + user + "_" + h.key, 0);
        sp.edit().putInt("hero_" + user + "_" + h.key, count + 1).apply();
        // 如果没有当前选中英雄，自动设置
        if (!sp.contains("selected_hero_" + user))
            sp.edit().putString("selected_hero_" + user, h.key).apply();
    }

    private void showResults(List<Hero> results) {
        cardContainer.removeAllViews();
        tvResult.setText("🎉 获得了 " + results.size() + " 张英雄卡！");

        for (Hero h : results) {
            View card = makeCard(h);
            cardContainer.addView(card);
            // 简单入场动画
            card.setAlpha(0f);
            card.animate().alpha(1f).setDuration(300).start();
        }

        // 滚动到结果
        scrollResult.post(() -> scrollResult.smoothScrollTo(0, 0));

        // 刷新英雄库（在 ownedRow 里）
        View root = getWindow().getDecorView().getRootView();
        if (root instanceof ViewGroup) {
            // 找到 ownedRow 并刷新
            LinearLayout ownedRow = findLinear((ViewGroup) root, "ownedRow");
            if (ownedRow != null) refreshOwnedRow(ownedRow);
        }
    }

    private View makeCard(Hero h) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setGravity(Gravity.CENTER_VERTICAL);

        int border, bg;
        switch (h.rarity) {
            case SSR: border=0xFFFFD700; bg=0xFF2A1A00; break;
            case SR:  border=0xFFB388FF; bg=0xFF1A0A2A; break;
            default:  border=0xFF78909C; bg=0xFF0D1A26; break;
        }
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(dp(12));
        gd.setColor(bg); gd.setStroke(dp(2), border);
        card.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        card.setLayoutParams(lp);

        // 图标
        TextView icon = new TextView(this);
        icon.setText(h.emoji);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        card.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));

        // 信息
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, 0, 0);

        TextView name = new TextView(this);
        name.setText(h.name + "  " + rarityBadge(h.rarity));
        name.setTextColor(border);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(name);

        TextView desc = new TextView(this);
        desc.setText(h.desc);
        desc.setTextColor(0xFFAABBCC);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        info.addView(desc);

        TextView stats = new TextView(this);
        stats.setText(String.format("♥+%d  ⚡+%d  💨+%d", h.hpBonus, h.fireBonus, h.speedBonus));
        stats.setTextColor(0xFF80CBC4);
        stats.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        info.addView(stats);

        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        return card;
    }

    private void refreshOwnedRow(LinearLayout row) {
        row.removeAllViews();
        String user = LoginActivity.getCurrentUser(this);
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String selected = sp.getString("selected_hero_" + user, "");
        boolean hasAny = false;
        for (Hero h : POOL) {
            int cnt = sp.getInt("hero_" + user + "_" + h.key, 0);
            if (cnt <= 0) continue;
            hasAny = true;
            boolean isSel = h.key.equals(selected);
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(8), dp(6), dp(8), dp(6));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMargins(dp(4), 0, dp(4), 0);
            chip.setLayoutParams(clp);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(dp(8));
            gd.setColor(isSel ? 0xFF1A3A2A : 0xFF111827);
            gd.setStroke(dp(isSel?2:1), isSel ? 0xFF00E676 : rarityColor(h.rarity));
            chip.setBackground(gd);

            TextView emo = new TextView(this);
            emo.setText(h.emoji); emo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22); emo.setGravity(Gravity.CENTER);
            chip.addView(emo);
            TextView nm = new TextView(this);
            nm.setText(h.name); nm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10); nm.setTextColor(0xFFCCDDEE); nm.setGravity(Gravity.CENTER);
            chip.addView(nm);
            if (cnt > 1) {
                TextView dup = new TextView(this);
                dup.setText("×" + cnt); dup.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10); dup.setTextColor(0xFFFFD700); dup.setGravity(Gravity.CENTER);
                chip.addView(dup);
            }

            final String heroKey = h.key;
            chip.setOnClickListener(v -> {
                sp.edit().putString("selected_hero_" + user, heroKey).apply();
                refreshOwnedRow(row);
                toast("已选择：" + h.name);
            });
            row.addView(chip);
        }
        if (!hasAny) {
            TextView empty = new TextView(this);
            empty.setText("还没有英雄，快去抽卡吧！");
            empty.setTextColor(0xFF607D8B);
            empty.setPadding(dp(16), dp(8), dp(16), dp(8));
            row.addView(empty);
        }
    }

    // ── 本地扣除水晶（SharedPreferences 同步，Room DB 异步同步）──────
    private boolean spendGems(int cost) {
        String user = LoginActivity.getCurrentUser(this);
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int gems = sp.getInt(GEMS_KEY + user, LoginActivity.getGems(this));
        if (gems < cost) return false;
        int newGems = gems - cost;
        sp.edit().putInt(GEMS_KEY + user, newGems).apply();
        // 异步写入 Room DB（绝对值覆盖）
        if (userDao != null) {
            final int gemsToSave = newGems;
            Executors.newSingleThreadExecutor().execute(() -> {
                try { userDao.setGems(user, gemsToSave); } catch (Exception ignored) {}
            });
        }
        return true;
    }

    private void refreshGems() {
        String user = LoginActivity.getCurrentUser(this);
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int gems = sp.getInt(GEMS_KEY + user, LoginActivity.getGems(this));
        if (tvGems != null) tvGems.setText("💎 " + gems);
    }

    private String rarityBadge(Rarity r) {
        switch(r) { case SSR: return "【SSR】"; case SR: return "【SR】"; default: return "【R】"; }
    }

    private int rarityColor(Rarity r) {
        switch(r) { case SSR: return 0xFFFFD700; case SR: return 0xFFB388FF; default: return 0xFF78909C; }
    }

    private LinearLayout findLinear(ViewGroup vg, String tag) { return null; } // simple impl

    private Button iconBtn(String text) {
        Button b = new Button(this);
        b.setText(text); b.setTextColor(0xFF90A4AE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private Button pullBtn(String text, int c1, int c2) {
        Button b = new Button(this);
        b.setText(text); b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{c1,c2});
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        return b;
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    // ── 供 GameGLView 读取当前选中英雄加成 ─────────────────────────
    public static Hero getSelectedHero(android.content.Context ctx) {
        String user = LoginActivity.getCurrentUser(ctx);
        String key = ctx.getSharedPreferences("AircraftWarUser", android.content.Context.MODE_PRIVATE)
                       .getString("selected_hero_" + user, "");
        for (Hero h : POOL) if (h.key.equals(key)) return h;
        return null; // 无选中英雄，使用默认属性
    }
}
