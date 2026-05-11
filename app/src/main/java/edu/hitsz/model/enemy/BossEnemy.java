package edu.hitsz.model.enemy;
import android.graphics.Bitmap;
import java.util.*;
import edu.hitsz.game.GameConstant;
import edu.hitsz.model.bullet.EnemyBullet;

/** Boss敌机：横向来回移动，三路射击，高血量 */
public class BossEnemy extends AbstractEnemyAircraft {
    private int hDir = 1; // 水平方向：1右/-1左

    public BossEnemy(int x, int y, Bitmap img) {
        super(x, y, 4, 1, GameConstant.BOSS_HP, GameConstant.SCORE_BOSS, img);
    }

    @Override
    public void forward() {
        locationX += speedX * hDir;
        locationY += speedY;
    }

    /** 碰到边界反向 */
    public void checkBounds(int screenWidth) {
        if (locationX <= imageWidth / 2 || locationX >= screenWidth - imageWidth / 2) {
            hDir = -hDir;
        }
    }

    /** 三路散射 */
    @Override
    public List<EnemyBullet> shoot(long t, Bitmap b, int w) {
        List<EnemyBullet> list = new ArrayList<>();
        if (b == null || t - lastShootTime < 600) return list;
        lastShootTime = t;
        list.add(new EnemyBullet(locationX - 30, locationY + imageHeight / 2, -4, 20, GameConstant.BOSS_BULLET_POWER, b));
        list.add(new EnemyBullet(locationX, locationY + imageHeight / 2, 0, 22, GameConstant.BOSS_BULLET_POWER, b));
        list.add(new EnemyBullet(locationX + 30, locationY + imageHeight / 2, 4, 20, GameConstant.BOSS_BULLET_POWER, b));
        return list;
    }
}
