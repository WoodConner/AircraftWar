package edu.hitsz.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import edu.hitsz.R;
import edu.hitsz.db.ScoreEntity;
import edu.hitsz.db.ScoreRepository;
import edu.hitsz.game.GameConstant;

/**
 * 排行榜界面：RecyclerView展示本地分数
 * 数据库查询在 ExecutorService 线程执行，结果在主线程刷新UI
 */
public class LeaderboardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        RecyclerView rv = findViewById(R.id.rv_leaderboard);
        rv.setLayoutManager(new LinearLayoutManager(this));
        LeaderboardAdapter adapter = new LeaderboardAdapter(new ArrayList<>());
        rv.setAdapter(adapter);

        // 从数据库异步加载排行榜（ExecutorService线程），主线程Handler更新UI
        Handler main = new Handler(Looper.getMainLooper());
        new ScoreRepository(this).getTopScores(GameConstant.LEADERBOARD_SIZE, scores -> {
            main.post(() -> adapter.setData(scores));
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }
}
