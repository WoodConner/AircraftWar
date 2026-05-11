package edu.hitsz.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

/** Room DAO：对应原Windows版 ScoreDao 接口，适配Android Room注解 */
@Dao
public interface ScoreDao {
    @Insert
    void insert(ScoreEntity score);

    @Query("SELECT * FROM scores ORDER BY score DESC LIMIT :limit")
    List<ScoreEntity> getTopScores(int limit);

    @Query("SELECT * FROM scores ORDER BY score DESC")
    List<ScoreEntity> getAllScores();

    @Query("DELETE FROM scores")
    void deleteAll();
}
