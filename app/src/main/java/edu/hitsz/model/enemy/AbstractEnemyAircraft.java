package edu.hitsz.model.enemy;

import android.graphics.Bitmap;
import java.util.List;
import edu.hitsz.model.AbstractFlyingObject;
import edu.hitsz.model.bullet.EnemyBullet;

/** 敌机抽象基类（复用自原Windows版 AbstractAircraft） */
public abstract class AbstractEnemyAircraft extends AbstractFlyingObject {
    protected int scoreValue;
    protected long lastShootTime = 0;

    public AbstractEnemyAircraft(int x, int y, int sx, int sy, int hp, int score, Bitmap img) {
        super(x, y, sx, sy, hp, img);
        this.scoreValue = score;
    }

    /** 检测是否出界（飞出屏幕底部） */
    public boolean isOutOfScreen(int screenHeight) {
        return locationY > screenHeight + imageHeight;
    }

    /** 发射子弹（由子类实现各自射击策略） */
    public abstract List<EnemyBullet> shoot(long currentTimeMs, Bitmap bulletImg, int screenWidth);

    public int getScoreValue() { return scoreValue; }
}
