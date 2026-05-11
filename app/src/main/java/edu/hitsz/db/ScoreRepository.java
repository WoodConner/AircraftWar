package edu.hitsz.db;

import android.content.Context;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 分数仓储层：所有数据库操作在专用单线程ExecutorService上执行
 * 保证Room不在主线程运行（Room强制要求），符合Android多线程规范
 */
public class ScoreRepository {
    private final ScoreDao dao;
    /** 单线程执行器，保证DB操作串行化，避免并发冲突 */
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    public ScoreRepository(Context context) {
        dao = AppDatabase.getInstance(context).scoreDao();
    }

    /** 异步保存分数 */
    public void saveScore(String name, int score, String difficulty) {
        dbExecutor.execute(() ->
            dao.insert(new ScoreEntity(name, score, difficulty, System.currentTimeMillis()))
        );
    }

    /** 异步查询排行榜，结果通过回调返回 */
    public void getTopScores(int limit, Callback<List<ScoreEntity>> callback) {
        dbExecutor.execute(() -> {
            List<ScoreEntity> result = dao.getTopScores(limit);
            if (callback != null) callback.onResult(result);
        });
    }

    public interface Callback<T> {
        void onResult(T result);
    }
}
