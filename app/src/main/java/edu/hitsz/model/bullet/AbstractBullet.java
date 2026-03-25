package edu.hitsz.model.bullet;

import android.graphics.Bitmap;
import edu.hitsz.model.AbstractFlyingObject;

/** 子弹抽象基类（复用自原Windows版 BaseBullet） */
public abstract class AbstractBullet extends AbstractFlyingObject {
    private int power;

    public AbstractBullet(int x, int y, int sx, int sy, int power, Bitmap img) {
        super(x, y, sx, sy, 1, img);
        this.power = power;
    }

    /** 移动并检测出界 */
    public void forward(int screenWidth, int screenHeight) {
        super.forward();
        if (locationX < 0 || locationX > screenWidth || locationY < 0 || locationY > screenHeight) {
            vanish();
        }
    }

    public int getPower() { return power; }
}
