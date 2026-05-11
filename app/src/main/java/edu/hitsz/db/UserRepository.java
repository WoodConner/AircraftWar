package edu.hitsz.db;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户仓储层 — 统一 Room 数据库账户操作
 *
 * 策略：
 *   • 注册/登录 → Room DB（持久，跨 SharedPreferences 重置）
 *   • 当前登录会话（session）→ SharedPreferences（快速读取，app重启仍保持）
 */
public class UserRepository {

    private static final String SESSION_PREFS = "AircraftWarSession";
    private static final String KEY_CURRENT   = "current_user";

    private final UserDao dao;
    private final SharedPreferences session;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private static volatile UserRepository INSTANCE;

    private UserRepository(Context ctx) {
        dao     = AppDatabase.getInstance(ctx).userDao();
        session = ctx.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE);
    }

    public static UserRepository getInstance(Context ctx) {
        if (INSTANCE == null) synchronized (UserRepository.class) {
            if (INSTANCE == null) INSTANCE = new UserRepository(ctx.getApplicationContext());
        }
        return INSTANCE;
    }

    // ── 注册 ──────────────────────────────────────────────────────
    public interface RegisterCallback { void onResult(boolean success, String msg); }

    public void register(String username, String password, RegisterCallback cb) {
        exec.execute(() -> {
            UserEntity u = new UserEntity(username, password, 3000);
            long rowId = dao.insertUser(u);
            if (rowId == -1) cb.onResult(false, "用户名已存在");
            else             cb.onResult(true,  "注册成功");
        });
    }

    // ── 登录 ──────────────────────────────────────────────────────
    public interface LoginCallback { void onResult(boolean success, String msg); }

    public void login(String username, String password, LoginCallback cb) {
        exec.execute(() -> {
            UserEntity u = dao.findByName(username);
            if (u == null)                    cb.onResult(false, "用户不存在，请先注册");
            else if (!u.password.equals(password)) cb.onResult(false, "密码错误");
            else {
                session.edit().putString(KEY_CURRENT, username).apply();
                cb.onResult(true, "登录成功");
            }
        });
    }

    // ── 游客登录（无需 DB） ────────────────────────────────────────
    public void loginAsGuest() {
        session.edit().putString(KEY_CURRENT, "游客").apply();
    }

    // ── 登出 ──────────────────────────────────────────────────────
    public void logout() { session.edit().remove(KEY_CURRENT).apply(); }

    // ── 当前用户（同步，SharedPreferences） ───────────────────────
    public String getCurrentUser() { return session.getString(KEY_CURRENT, ""); }
    public boolean isLoggedIn()    { return !getCurrentUser().isEmpty(); }

    // ── 水晶（异步 DB） ───────────────────────────────────────────
    public interface IntCallback { void onResult(int value); }

    public void getGems(IntCallback cb) {
        String user = getCurrentUser();
        if ("游客".equals(user)) { cb.onResult(0); return; }
        exec.execute(() -> {
            UserEntity u = dao.findByName(user);
            cb.onResult(u != null ? u.gems : 0);
        });
    }

    public void addGems(int delta) {
        String user = getCurrentUser();
        if ("游客".equals(user)) return;
        exec.execute(() -> dao.addGems(user, delta));
    }

    /** 扣除水晶，通过回调返回是否成功 */
    public interface BoolCallback { void onResult(boolean success); }
    public void spendGems(int cost, BoolCallback cb) {
        String user = getCurrentUser();
        if ("游客".equals(user)) { cb.onResult(false); return; }
        exec.execute(() -> {
            UserEntity u = dao.findByName(user);
            if (u == null || u.gems < cost) { cb.onResult(false); return; }
            dao.setGems(user, u.gems - cost);
            cb.onResult(true);
        });
    }

    // ── 英雄选择 ──────────────────────────────────────────────────
    public void setSelectedHero(String heroKey) {
        String user = getCurrentUser();
        if ("游客".equals(user)) {
            session.edit().putString("guest_hero", heroKey).apply();
            return;
        }
        exec.execute(() -> dao.setSelectedHero(user, heroKey));
        session.edit().putString("cached_hero", heroKey).apply(); // 缓存供UI快速读取
    }

    /** 快速同步读取（缓存，不阻塞UI） */
    public String getSelectedHeroCached() {
        String user = getCurrentUser();
        if ("游客".equals(user)) return session.getString("guest_hero", "");
        return session.getString("cached_hero", "");
    }

    public void getSelectedHero(ScoreRepository.Callback<String> cb) {
        String user = getCurrentUser();
        if ("游客".equals(user)) { cb.onResult(session.getString("guest_hero", "")); return; }
        exec.execute(() -> {
            UserEntity u = dao.findByName(user);
            String key = (u != null) ? u.selectedHeroKey : "";
            session.edit().putString("cached_hero", key).apply();
            cb.onResult(key);
        });
    }

    // ── gacha 拥有英雄记录（仍用 SharedPreferences，复杂结构） ────
    /** 增加英雄数量 */
    public void addHeroCount(String heroKey, int count) {
        String user = getCurrentUser();
        SharedPreferences sp = session; // 同一个 prefs
        int cur = sp.getInt("hero_" + user + "_" + heroKey, 0);
        sp.edit().putInt("hero_" + user + "_" + heroKey, cur + count).apply();
    }

    public int getHeroCount(String heroKey) {
        String user = getCurrentUser();
        return session.getInt("hero_" + user + "_" + heroKey, 0);
    }

    /** pity counter */
    public int getPity()          { return session.getInt("pity_" + getCurrentUser(), 0); }
    public void setPity(int p)    { session.edit().putInt("pity_" + getCurrentUser(), p).apply(); }
}
