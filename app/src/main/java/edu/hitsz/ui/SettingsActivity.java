package edu.hitsz.ui;

import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 设置界面：画质 / 阴影 / 分辨率 / 卡通描边
 * 存储于 SharedPreferences "AircraftSettings"
 */
public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS        = "AircraftSettings";
    public static final String KEY_QUALITY  = "quality";    // 0=低 1=中 2=高
    public static final String KEY_SHADOW   = "shadow";     // boolean
    public static final String KEY_OUTLINE  = "outline";    // boolean
    public static final String KEY_CEL      = "cel_steps";  // 1/2/3
    public static final String KEY_RES      = "res_scale";  // 50/75/100

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUI());
    }

    private View buildUI() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF08081A);
        root.setPadding(dp(16), dp(8), dp(16), dp(32));

        // ── 顶部栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(12), 0, dp(16));

        Button back = new Button(this);
        back.setText("← 返回"); back.setTextColor(0xFF90A4AE);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText("⚙  游戏设置");
        title.setTextColor(0xFFE0E0E0);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 画质预设
        sectionHeader(root, "画质预设");
        String[] qLabels = {"低画质（省电）", "中画质（推荐）", "高画质（旗舰）"};
        String[] qDesc   = {"关闭阴影和描边，帧率最高", "阴影+描边，均衡体验", "全效果+高清纹理，需旗舰手机"};
        int curQ = sp.getInt(KEY_QUALITY, 1);
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < 3; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(16), dp(10), dp(16), dp(10));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.setMargins(0, dp(4), 0, 0);
            item.setLayoutParams(ilp);
            GradientDrawable bg = new GradientDrawable(); bg.setShape(GradientDrawable.RECTANGLE); bg.setCornerRadius(dp(10));
            bg.setColor(i == curQ ? 0xFF1A2A3A : 0xFF111827);
            bg.setStroke(dp(1), i == curQ ? 0xFF2979FF : 0xFF263238);
            item.setBackground(bg);

            RadioButton rb = new RadioButton(this);
            rb.setText(qLabels[i]); rb.setTextColor(Color.WHITE); rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF2979FF));
            rb.setChecked(i == curQ); rb.setTag(i);
            item.addView(rb);

            TextView dv = new TextView(this);
            dv.setText(qDesc[i]); dv.setTextColor(0xFF607D8B); dv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            dv.setPadding(dp(32), 0, 0, 0);
            item.addView(dv);

            int idx = i; item.setOnClickListener(v -> {
                sp.edit().putInt(KEY_QUALITY, idx).apply();
                // 低画质预设
                boolean shadow = idx >= 1, outline = idx >= 1;
                int cel = idx == 0 ? 1 : idx == 1 ? 3 : 3;
                int res = idx == 0 ? 50 : idx == 1 ? 75 : 100;
                sp.edit().putBoolean(KEY_SHADOW, shadow).putBoolean(KEY_OUTLINE, outline)
                  .putInt(KEY_CEL, cel).putInt(KEY_RES, res).apply();
                recreate();
            });
            rg.addView(item);
        }
        root.addView(rg, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        divider(root);

        // ── 高级设置
        sectionHeader(root, "高级设置");

        // 阴影
        addToggle(root, sp, KEY_SHADOW, "投影阴影", "飞机下方的投影（1次额外DrawCall）", true);
        addToggle(root, sp, KEY_OUTLINE, "卡通描边", "黑色外轮廓效果（1次额外DrawCall）", true);

        divider(root);

        // 卡通步数
        sectionHeader(root, "卡通着色步数（Cel Shading）");
        int[] celVals = {1, 2, 3};
        String[] celLabels = {"1步（平涂）", "2步（简单明暗）", "3步（标准卡通）"};
        int curCel = sp.getInt(KEY_CEL, 3);
        addRadioRow(root, sp, KEY_CEL, celVals, celLabels, curCel);

        divider(root);

        // 分辨率
        sectionHeader(root, "渲染分辨率");
        int[] resVals = {50, 75, 100};
        String[] resLabels = {"50%（省电）", "75%（推荐）", "100%（原生）"};
        int curRes = sp.getInt(KEY_RES, 75);
        addRadioRow(root, sp, KEY_RES, resVals, resLabels, curRes);

        divider(root);

        // ── 重置
        Button reset = new Button(this);
        reset.setText("重置为默认设置");
        reset.setTextColor(0xFFEF9A9A);
        reset.setBackgroundColor(Color.TRANSPARENT);
        reset.setOnClickListener(v -> {
            sp.edit().putInt(KEY_QUALITY,1).putBoolean(KEY_SHADOW,true)
              .putBoolean(KEY_OUTLINE,true).putInt(KEY_CEL,3).putInt(KEY_RES,75).apply();
            recreate();
        });
        root.addView(reset, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        scroll.addView(root);
        return scroll;
    }

    private void sectionHeader(LinearLayout p, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF2979FF); tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(16), 0, dp(6));
        tv.setLayoutParams(lp);
        p.addView(tv);
    }

    private void addToggle(LinearLayout p, SharedPreferences sp, String key, String label, String desc, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, dp(4), 0, 0);
        row.setLayoutParams(rlp);
        GradientDrawable bg = new GradientDrawable(); bg.setShape(GradientDrawable.RECTANGLE); bg.setCornerRadius(dp(10)); bg.setColor(0xFF111827);
        row.setBackground(bg);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView lbl = new TextView(this); lbl.setText(label); lbl.setTextColor(Color.WHITE); lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        textCol.addView(lbl);
        TextView dv = new TextView(this); dv.setText(desc); dv.setTextColor(0xFF607D8B); dv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textCol.addView(dv);
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener((v, checked) -> sp.edit().putBoolean(key, checked).apply());
        row.addView(sw);

        p.addView(row);
    }

    private void addRadioRow(LinearLayout p, SharedPreferences sp, String key, int[] vals, String[] labels, int cur) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < vals.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]); rb.setTextColor(0xFFCCDDEE); rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF2979FF));
            rb.setChecked(vals[i] == cur);
            int v = vals[i];
            rb.setOnCheckedChangeListener((btn, checked) -> { if (checked) sp.edit().putInt(key, v).apply(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(4), dp(2), 0, dp(2)); rb.setLayoutParams(lp);
            row.addView(rb);
        }
        p.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void divider(LinearLayout p) {
        View d = new View(this); d.setBackgroundColor(0xFF1E2A3A);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(0, dp(12), 0, 0); d.setLayoutParams(lp);
        p.addView(d);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    // ── 静态读取（供 GameGLView 使用）
    public static boolean shadowEnabled(android.content.Context ctx) {
        return ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean(KEY_SHADOW, true);
    }
    public static boolean outlineEnabled(android.content.Context ctx) {
        return ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean(KEY_OUTLINE, true);
    }
    public static int celSteps(android.content.Context ctx) {
        return ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getInt(KEY_CEL, 3);
    }
}
