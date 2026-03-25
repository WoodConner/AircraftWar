package edu.hitsz.model;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;
import edu.hitsz.game.GameConstant;
import edu.hitsz.model.bullet.HeroBullet;

/**
 * 英雄飞机
 * 复用自原Windows版 HeroAircraft 逻辑，适配Android触屏控制（替代HeroController鼠标输入）
 */
public class HeroAircraft extends AbstractFlyingObject {

    /** 子弹图像 */
    private Bitmap bulletImage;

    /** 是否开启扩散射击（三发子弹道具激活） */
    private boolean spreadShoot = false;
    private long spreadShootExpireTime = 0;

    /** 上次射击时间 */
    private long lastShootTime = 0;

    /** 是否正在触屏控制中 */
    private volatile float targetX;
    private volatile float targetY;

    public HeroAircraft(int locationX, int locationY, Bitmap heroImage, Bitmap bulletImage, int hp) {
        super(locationX, locationY, 0, 0, hp, heroImage);
        this.bulletImage = bulletImage;
        this.targetX = locationX;
        this.targetY = locationY;
    }

    /**
     * 触屏跟随移动：英雄飞机平滑跟随手指位置
     * 替代原Windows版 HeroController 的 mouseMoved() 方法
     */
    @Override
    public void forward() {
        float dx = targetX - locationX;
        float dy = targetY - locationY;
        // 平滑插值：每帧移动25%距离，避免生硬跳变
        locationX += (int)(dx * 0.25f);
        locationY += (int)(dy * 0.25f);
    }

    /** 设置触屏目标位置（由UI线程写入，游戏线程读取，volatile保证可见性） */
    public void setTouchTarget(float x, float y) {
        targetX = x;
        targetY = y;
    }

    /**
     * 发射子弹（复用自原Windows版shoot逻辑）
     * 根据是否有扩散道具决定单发或三发
     */
    public List<HeroBullet> shoot(long currentTimeMs) {
        List<HeroBullet> bullets = new ArrayList<>();
        long interval = spreadShoot ? GameConstant.HERO_SHOOT_INTERVAL_FAST : GameConstant.HERO_SHOOT_INTERVAL;
        if (currentTimeMs - lastShootTime < interval) return bullets;
        lastShootTime = currentTimeMs;

        // 检查扩散道具是否过期
        if (spreadShoot && currentTimeMs > spreadShootExpireTime) {
            spreadShoot = false;
        }

        int bulletSpeed = -30; // 向上
        if (spreadShoot) {
            // 三发：左斜、直线、右斜
            bullets.add(new HeroBullet(locationX - 20, locationY - imageHeight / 2, -5, bulletSpeed, GameConstant.HERO_BULLET_POWER, bulletImage));
            bullets.add(new HeroBullet(locationX, locationY - imageHeight / 2, 0, bulletSpeed, GameConstant.HERO_BULLET_POWER, bulletImage));
            bullets.add(new HeroBullet(locationX + 20, locationY - imageHeight / 2, 5, bulletSpeed, GameConstant.HERO_BULLET_POWER, bulletImage));
        } else {
            bullets.add(new HeroBullet(locationX, locationY - imageHeight / 2, 0, bulletSpeed, GameConstant.HERO_BULLET_POWER, bulletImage));
        }
        return bullets;
    }

    /** 激活扩散射击道具（持续10秒） */
    public void activateSpreadShoot(long currentTimeMs) {
        spreadShoot = true;
        spreadShootExpireTime = currentTimeMs + 10000;
    }

    /** 回血（血量道具） */
    public void heal(int amount) {
        hp = Math.min(maxHp, hp + amount);
    }
}
