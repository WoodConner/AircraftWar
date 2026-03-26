package edu.hitsz.game;

/**
 * 游戏全局常量
 * 对应原Windows版 Main.java 中的 WINDOW_WIDTH/WINDOW_HEIGHT 等常量
 */
public class GameConstant {
    // 目标帧率
    public static final int TARGET_FPS = 60;
    public static final long TARGET_FRAME_MS = 1000 / TARGET_FPS;

    // 敌机生成间隔（毫秒）
    public static final long MOB_SPAWN_INTERVAL = 600;
    public static final long ELITE_SPAWN_INTERVAL = 2000;
    public static final long BOSS_SPAWN_SCORE_THRESHOLD = 300; // 每300分刷新一次Boss

    // 英雄开枪冷却（毫秒）
    public static final long HERO_SHOOT_INTERVAL = 300;
    public static final long HERO_SHOOT_INTERVAL_FAST = 150; // 道具强化后
    public static final long ENEMY_SHOOT_INTERVAL = 800;

    // 分值
    public static final int SCORE_MOB = 10;
    public static final int SCORE_ELITE = 30;
    public static final int SCORE_BOSS = 100;

    // 血量
    public static final int HERO_HP = 1000;
    public static final int MOB_HP = 30;
    public static final int ELITE_HP = 80;
    public static final int BOSS_HP = 500;

    // 子弹伤害
    public static final int HERO_BULLET_POWER = 25;
    public static final int ENEMY_BULLET_POWER = 10;
    public static final int BOSS_BULLET_POWER = 20;

    // 道具回血量
    public static final int BLOOD_PROP_HEAL = 300;

    // 排行榜显示条数
    public static final int LEADERBOARD_SIZE = 20;

    // 难度标识
    public static final String DIFFICULTY_EASY   = "简单";
    public static final String DIFFICULTY_NORMAL = "普通";
    public static final String DIFFICULTY_HARD   = "困难";
    public static final String DIFFICULTY_HELL   = "地狱";

    // 默认玩家名称
    public static final String DEFAULT_PLAYER_NAME = "玩家";
}
