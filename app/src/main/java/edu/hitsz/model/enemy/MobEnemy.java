package edu.hitsz.model.enemy;
import android.graphics.Bitmap;
import java.util.*;
import edu.hitsz.game.GameConstant;
import edu.hitsz.model.bullet.EnemyBullet;

/** 普通敌机：直线下行，不射击 */
public class MobEnemy extends AbstractEnemyAircraft {
    public MobEnemy(int x, int y, int speed, Bitmap img) {
        super(x, y, 0, speed, GameConstant.MOB_HP, GameConstant.SCORE_MOB, img);
    }
    @Override
    public List<EnemyBullet> shoot(long t, Bitmap b, int w) { return Collections.emptyList(); }
}
