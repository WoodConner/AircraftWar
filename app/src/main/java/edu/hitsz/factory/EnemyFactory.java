package edu.hitsz.factory;

import android.graphics.Bitmap;
import java.util.Random;
import edu.hitsz.model.enemy.*;
import edu.hitsz.model.prop.*;

/**
 * 敌机和道具工厂（工厂模式）
 * Bug修复：所有方法现在支持 null Bitmap（GL渲染模式无需Bitmap）
 */
public class EnemyFactory {
    private static final Random rand = new Random();

    // GL模式下使用的默认尺寸（像素单位与游戏坐标系一致）
    private static final int DEFAULT_MOB_SIZE   = 60;
    private static final int DEFAULT_ELITE_SIZE = 80;
    private static final int DEFAULT_BOSS_SIZE  = 120;

    public static MobEnemy createMob(int screenWidth, Bitmap img) {
        int w = (img != null) ? img.getWidth()  : DEFAULT_MOB_SIZE;
        int h = (img != null) ? img.getHeight() : DEFAULT_MOB_SIZE;
        int x = rand.nextInt(Math.max(1, screenWidth - w)) + w / 2;
        return new MobEnemy(x, -h, 6, img);
    }

    public static EliteEnemy createElite(int screenWidth, Bitmap img) {
        int w = (img != null) ? img.getWidth()  : DEFAULT_ELITE_SIZE;
        int h = (img != null) ? img.getHeight() : DEFAULT_ELITE_SIZE;
        int x = rand.nextInt(Math.max(1, screenWidth - w)) + w / 2;
        return new EliteEnemy(x, -h, 5, img);
    }

    public static BossEnemy createBoss(int screenWidth, Bitmap img) {
        int h = (img != null) ? img.getHeight() : DEFAULT_BOSS_SIZE;
        return new BossEnemy(screenWidth / 2, -h, img);
    }

    /**
     * 精英被击毁后随机生成道具（50%概率）
     * Bug修复：GL渲染模式下即使 bitmap 为 null 也正常创建道具（靠网格渲染）
     */
    public static AbstractProp createPropIfDropped(int x, int y, Bitmap blood, Bitmap bomb, Bitmap bullet) {
        if (rand.nextFloat() > 0.5f) return null;
        return randomProp(x, y, blood, bomb, bullet);
    }

    /** Boss被击毁后必然生成道具 */
    public static AbstractProp createPropFromBoss(int x, int y, Bitmap blood, Bitmap bomb, Bitmap bullet) {
        return randomProp(x, y, blood, bomb, bullet);
    }

    private static AbstractProp randomProp(int x, int y, Bitmap blood, Bitmap bomb, Bitmap bullet) {
        int type = rand.nextInt(3);
        switch (type) {
            case 0: return new BloodProp(x, y, blood);   // blood 可以为 null（GL模式）
            case 1: return new BombProp(x, y, bomb);
            default: return new BulletProp(x, y, bullet);
        }
    }
}
