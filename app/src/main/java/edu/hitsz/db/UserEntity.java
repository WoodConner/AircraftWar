package edu.hitsz.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room 用户表 — 统一账户数据库（所有设备用户共用同一 SQLite 库）
 */
@Entity(tableName = "users")
public class UserEntity {
    @PrimaryKey
    @NonNull
    public String username = "";
    public String password;    // 明文（本地 demo，生产环境应 bcrypt）
    public int    gems    = 0;
    public String selectedHeroKey = ""; // 已选英雄
    public long   createdAt;

    public UserEntity() {}
    public UserEntity(@NonNull String username, String password, int gems) {
        this.username  = username;
        this.password  = password;
        this.gems      = gems;
        this.createdAt = System.currentTimeMillis();
    }
}
