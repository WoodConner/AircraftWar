package edu.hitsz.model.bullet;
import android.graphics.Bitmap;
/** 敌机子弹（向下飞行） */
public class EnemyBullet extends AbstractBullet {
    public EnemyBullet(int x, int y, int sx, int sy, int power, Bitmap img) {
        super(x, y, sx, sy, power, img);
    }
}
