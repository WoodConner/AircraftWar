package edu.hitsz.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import edu.hitsz.db.UserRepository;

/**
 * 登录 / 注册界面 — Room数据库版本（本地 SQLite 共用）
 */
public class LoginActivity extends AppCompatActivity {

    private EditText etUser, etPass;
    private TextView tvMsg;
    private View     btnLogin, btnReg;
    private UserRepository repo;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = UserRepository.getInstance(this);
        if (repo.isLoggedIn()) { jump(); return; }
        setContentView(buildLayout());
    }

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(32), dp(48), dp(32), dp(48));
        root.setBackground(grad(0xFF0A0A1E, 0xFF1A1A3E));

        // 标题
        TextView title = tv("⚡  飞机大战", 38, 0xFFFFD700, true);
        title.setGravity(Gravity.CENTER);
        title.setShadowLayer(12, 0, 0, 0xFFFF8C00);
        root.addView(title, mw());

        root.addView(tv("用户登录 / 注册", 15, 0xFFAABBCC, false), mw());
        sp(root, 36);

        etUser = field("用户名"); root.addView(etUser, mw()); sp(root, 10);
        etPass = field("密码");
        etPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(etPass, mw()); sp(root, 8);

        tvMsg = tv("", 13, 0xFFFF6B6B, false);
        tvMsg.setGravity(Gravity.CENTER); tvMsg.setVisibility(View.INVISIBLE);
        root.addView(tvMsg, mw()); sp(root, 14);

        // 按钮行（修复：用 dp 宽度间隔，不用 MATCH_PARENT）
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        btnLogin = btn("登  录", 0xFF2979FF, 0xFF1565C0, v -> doLogin());
        row.addView(btnLogin, new LinearLayout.LayoutParams(0, dp(52), 1f));
        row.addView(spacer(dp(14))); // 固定宽度间隔，非MATCH_PARENT
        btnReg = btn("注  册", 0xFF00BFA5, 0xFF00796B, v -> doRegister());
        row.addView(btnReg,   new LinearLayout.LayoutParams(0, dp(52), 1f));
        root.addView(row, mw()); sp(root, 20);

        Button guest = new Button(this);
        guest.setText("游客模式（不保存进度）");
        guest.setTextColor(0xFF78909C); guest.setBackgroundColor(Color.TRANSPARENT);
        guest.setOnClickListener(v -> { repo.loginAsGuest(); jump(); });
        root.addView(guest, mw());

        return root;
    }

    private void doLogin() {
        String user = etUser.getText().toString().trim();
        String pass = etPass.getText().toString().trim();
        if (TextUtils.isEmpty(user) || TextUtils.isEmpty(pass)) { msg("请填写用户名和密码"); return; }
        setLoading(true);
        repo.login(user, pass, (ok, text) -> ui.post(() -> {
            setLoading(false);
            if (ok) jump(); else msg(text);
        }));
    }

    private void doRegister() {
        String user = etUser.getText().toString().trim();
        String pass = etPass.getText().toString().trim();
        if (TextUtils.isEmpty(user) || TextUtils.isEmpty(pass)) { msg("请填写用户名和密码"); return; }
        if (user.length() < 2) { msg("用户名至少 2 位"); return; }
        if (pass.length() < 4) { msg("密码至少 4 位"); return; }
        setLoading(true);
        repo.register(user, pass, (ok, text) -> ui.post(() -> {
            setLoading(false);
            if (!ok) { msg(text); return; }
            // 注册成功后自动登录
            repo.login(user, pass, (ok2, t2) -> ui.post(() -> { if (ok2) jump(); else msg(t2); }));
        }));
    }

    private void jump() {
        startActivity(new Intent(this, edu.hitsz.sufaceview.MainActivity.class));
        finish();
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading); btnReg.setEnabled(!loading);
    }
    private void msg(String s) { tvMsg.setText(s); tvMsg.setVisibility(View.VISIBLE); }

    // ── 静态工具（供其他类使用，保持向后兼容） ─────────────────────
    public static String getCurrentUser(Context ctx) {
        return UserRepository.getInstance(ctx).getCurrentUser();
    }
    public static int getGems(Context ctx) {
        // 从 SharedPreferences 缓存读取（快速同步）
        return ctx.getSharedPreferences("AircraftWarSession", Context.MODE_PRIVATE)
                  .getInt("cached_gems", 0);
    }
    public static void addGems(Context ctx, int amount) {
        UserRepository.getInstance(ctx).addGems(amount);
    }
    public static boolean spendGems(Context ctx, int cost) {
        // 注意：这是同步占位，实际扣费在 GachaActivity 里通过异步回调做
        // 此处仅供兼容旧代码调用（不做实际扣费，由 GachaActivity 统一管理）
        return false;
    }

    // ── UI 工具 ──────────────────────────────────────────────────
    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(0xFF607D8B);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(dp(10));
        bg.setColor(0xFF1E2A3A); bg.setStroke(dp(1), 0xFF2979FF);
        e.setBackground(bg); return e;
    }
    private Button btn(String text, int c1, int c2, View.OnClickListener l) {
        Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16); b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        bg.setCornerRadius(dp(10)); b.setBackground(bg); b.setOnClickListener(l); return b;
    }
    private TextView tv(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t;
    }
    private GradientDrawable grad(int c1, int c2) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{c1, c2});
    }
    private View spacer(int widthPx) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.MATCH_PARENT));
        return v;
    }
    private void sp(ViewGroup p, int dpVal) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dpVal)));
        p.addView(v);
    }
    private LinearLayout.LayoutParams mw() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
