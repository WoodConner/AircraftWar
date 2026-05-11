package edu.hitsz.db;

import android.content.Context;
import androidx.room.*;

/**
 * Room 数据库单例（v2：新增 users 表）
 * fallbackToDestructiveMigration → 版本升级时自动重建（demo可接受）
 */
@Database(entities = {ScoreEntity.class, UserEntity.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract ScoreDao scoreDao();
    public abstract UserDao  userDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class, "aircraft_war.db"
                    )
                    .fallbackToDestructiveMigration()   // v1→v2 直接重建
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
