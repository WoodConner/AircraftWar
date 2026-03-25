package edu.hitsz.factory;

import android.graphics.Bitmap;
import java.util.Random;
import edu.hitsz.model.enemy.*;
import edu.hitsz.model.prop.*;

/**
 * 敌机和道具工厂（工厂模式）
 * 复用自原Windows版工厂模式设计，与平台无关的纯Java逻辑
 */
public class EnemyFactory {
    private static final Random rand = new Random();

    public static MobEnemy createMob(int screenWidth, Bitmap img) {
        int x = rand.nextInt(Math.max(1, screenWidth - img.getWidth())) + img.getWidth() / 2;
        return new MobEnemy(x, -img.getHeight(), 6, img);
    }

    public static EliteEnemy createElite(int screenWidth, Bitmap img) {
        int x = rand.nextInt(Math.max(1, screenWidth - img.getWidth())) + img.getWidth() / 2;
        return new EliteEnemy(x, -img.getHeight(), 5, img);
    }

    public static BossEnemy createBoss(int screenWidth, Bitmap img) {
        return new BossEnemy(screenWidth / 2, -img.getHeight(), img);
    }

    /** 精英被击毁后随机生成道具（50%概率） */
    public static AbstractProp createPropIfDropped(int x, int y, Bitmap blood, Bitmap bomb, Bitmap bullet) {
        if (rand.nextFloat() > 0.5f) return null;
        int type = rand.nextInt(3);
        if (type == 0 && blood != null) return new BloodProp(x, y, blood);
        if (type == 1 && bomb != null) return new BombProp(x, y, bomb);
        if (bullet != null) return new BulletProp(x, y, bullet);
        return null;
    }

    /** Boss被击毁后必然生成道具 */
    public static AbstractProp createPropFromBoss(int x, int y, Bitmap blood, Bitmap bomb, Bitmap bullet) {
        int type = rand.nextInt(3);
        if (type == 0 && blood != null) return new BloodProp(x, y, blood);
        if (type == 1 && bomb != null) return new BombProp(x, y, bomb);
        if (bullet != null) return new BulletProp(x, y, bullet);
        return null;
    }
}
