package edu.hitsz.db;

import androidx.room.*;

/** Room DAO — 用户账户 CRUD */
@Dao
public interface UserDao {

    /** 注册：用户名唯一，冲突时忽略，返回rowId（-1=已存在） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertUser(UserEntity user);

    /** 登录验证：按用户名查找 */
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    UserEntity findByName(String username);

    /** 更新水晶（相对） */
    @Query("UPDATE users SET gems = MAX(0, gems + :delta) WHERE username = :username")
    void addGems(String username, int delta);

    /** 设置水晶（绝对） */
    @Query("UPDATE users SET gems = :gems WHERE username = :username")
    void setGems(String username, int gems);

    /** 更新选中英雄 */
    @Query("UPDATE users SET selectedHeroKey = :heroKey WHERE username = :username")
    void setSelectedHero(String username, String heroKey);

    /** 整体更新（hero key + gems） */
    @Update
    void update(UserEntity user);
}
