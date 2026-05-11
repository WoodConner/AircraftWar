package edu.hitsz.model.bullet;
import android.graphics.Bitmap;
/** 英雄子弹（向上飞行） */
public class HeroBullet extends AbstractBullet {
    public HeroBullet(int x, int y, int sx, int sy, int power, Bitmap img) {
        super(x, y, sx, sy, power, img);
    }
}
