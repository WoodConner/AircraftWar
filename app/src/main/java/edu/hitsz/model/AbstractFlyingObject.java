package edu.hitsz.model;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * 所有飞行对象的抽象基类
 * 复用自原Windows版 AbstractFlyingObject，移除Java SE依赖，适配Android Bitmap/Canvas
 */
public abstract class AbstractFlyingObject {

    /** 中心坐标 */
    protected int locationX;
    protected int locationY;

    /** 每帧移动速度 */
    protected int speedX;
    protected int speedY;

    /** 生命值 */
    protected int hp;
    protected int maxHp;

    /** 图像资源（Android Bitmap，替代原Windows BufferedImage） */
    protected Bitmap image;
    protected int imageWidth;
    protected int imageHeight;

    /** 是否存活 */
    private volatile boolean valid = true;

    public AbstractFlyingObject(int locationX, int locationY, int speedX, int speedY, int hp, Bitmap image) {
        this.locationX = locationX;
        this.locationY = locationY;
        this.speedX = speedX;
        this.speedY = speedY;
        this.hp = hp;
        this.maxHp = hp;
        this.image = image;
        this.imageWidth = (image != null) ? image.getWidth() : 40;
        this.imageHeight = (image != null) ? image.getHeight() : 40;
    }

    /**
     * 每帧向前移动（由游戏循环线程调用）
     * 复用自原Windows版 forward() 逻辑
     */
    public void forward() {
        locationX += speedX;
        locationY += speedY;
    }

    /**
     * AABB 矩形碰撞检测
     * 复用自原Windows版碰撞算法，与平台无关
     */
    public boolean crash(AbstractFlyingObject other) {
        if (other == null) return false;
        int dx = Math.abs(this.locationX - other.locationX);
        int dy = Math.abs(this.locationY - other.locationY);
        return dx < (this.imageWidth + other.imageWidth) / 2
                && dy < (this.imageHeight + other.imageHeight) / 2;
    }

    /** 减少血量，血量<=0时自动消亡 */
    public void decreaseHp(int amount) {
        hp -= amount;
        if (hp <= 0) {
            hp = 0;
            vanish();
        }
    }

    /** 使对象消亡（标记为无效，游戏循环将在下一帧清除） */
    public void vanish() {
        valid = false;
    }

    /**
     * 绘制对象到Canvas（替代原Windows版 paintComponent/Graphics2D）
     * 以中心坐标绘制
     */
    public void draw(Canvas canvas, Paint paint) {
        if (image != null && valid) {
            canvas.drawBitmap(image, locationX - imageWidth / 2f, locationY - imageHeight / 2f, paint);
        }
    }

    // ===== Getters/Setters =====
    public boolean isValid() { return valid; }
    public int getLocationX() { return locationX; }
    public int getLocationY() { return locationY; }
    public void setLocation(int x, int y) { locationX = x; locationY = y; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public Bitmap getImage() { return image; }
    public int getImageWidth() { return imageWidth; }
    public int getImageHeight() { return imageHeight; }
}
