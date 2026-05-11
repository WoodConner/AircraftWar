package edu.hitsz.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import edu.hitsz.R;
import edu.hitsz.db.ScoreRepository;
import edu.hitsz.game.GameConstant;

/** 游戏结束结算界面：显示分数、再玩一次、查看排行榜 */
public class GameOverActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_over);

        int score = getIntent().getIntExtra("score", 0);
        String difficulty = getIntent().getStringExtra("difficulty");
        if (difficulty == null) difficulty = GameConstant.DIFFICULTY_EASY;

        TextView tvScore = findViewById(R.id.tv_final_score);
        Button btnPlayAgain = findViewById(R.id.btn_play_again);
        Button btnLeaderboard = findViewById(R.id.btn_leaderboard);
        Button btnMenu = findViewById(R.id.btn_menu);

        tvScore.setText("最终得分\n" + score);

        // 提示输入玩家名（简化：直接使用默认名）
        final String finalDifficulty = difficulty;
        btnPlayAgain.setOnClickListener(v -> {
            Intent i = new Intent(this, GameActivity.class);
            i.putExtra("difficulty", finalDifficulty);
            startActivity(i); finish();
        });

        btnLeaderboard.setOnClickListener(v -> {
            startActivity(new Intent(this, LeaderboardActivity.class));
        });

        btnMenu.setOnClickListener(v -> {
            Intent i = new Intent(this, edu.hitsz.sufaceview.MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i); finish();
        });
    }
}
