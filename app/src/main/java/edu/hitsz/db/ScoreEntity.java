package edu.hitsz.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room数据库实体：本地分数记录
 * 对应原Windows版 ScoreRecord 类，迁移至Android Room ORM
 */
@Entity(tableName = "scores")
public class ScoreEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String playerName;
    public int score;
    public String difficulty;
    public long timestamp; // System.currentTimeMillis()

    public ScoreEntity(String playerName, int score, String difficulty, long timestamp) {
        this.playerName = playerName;
        this.score = score;
        this.difficulty = difficulty;
        this.timestamp = timestamp;
    }
}
