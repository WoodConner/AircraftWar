package edu.hitsz.model.enemy;
import android.graphics.Bitmap;
import java.util.*;
import edu.hitsz.game.GameConstant;
import edu.hitsz.model.bullet.EnemyBullet;

/** 精英敌机：直线下行，可射击，击毁掉落道具 */
public class EliteEnemy extends AbstractEnemyAircraft {
    public EliteEnemy(int x, int y, int speed, Bitmap img) {
        super(x, y, 0, speed, GameConstant.ELITE_HP, GameConstant.SCORE_ELITE, img);
    }
    @Override
    public List<EnemyBullet> shoot(long t, Bitmap b, int w) {
        List<EnemyBullet> list = new ArrayList<>();
        if (b == null || t - lastShootTime < GameConstant.ENEMY_SHOOT_INTERVAL) return list;
        lastShootTime = t;
        list.add(new EnemyBullet(locationX, locationY + imageHeight / 2, 0, 18, GameConstant.ENEMY_BULLET_POWER, b));
        return list;
    }
}
