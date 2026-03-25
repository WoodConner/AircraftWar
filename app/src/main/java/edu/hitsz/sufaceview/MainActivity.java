package edu.hitsz.sufaceview;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import edu.hitsz.R;
import edu.hitsz.game.GameConstant;
import edu.hitsz.ui.GameActivity;
import edu.hitsz.ui.LeaderboardActivity;

/**
 * 主菜单界面 —— 游戏入口（替代原Windows版 Main.java + JFrame/JPanel）
 * 提供难度选择、开始游戏、排行榜入口
 */
public class MainActivity extends AppCompatActivity {

    private String selectedDifficulty = GameConstant.DIFFICULTY_EASY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnEasy = findViewById(R.id.btn_easy);
        Button btnNormal = findViewById(R.id.btn_normal);
        Button btnHard = findViewById(R.id.btn_hard);
        Button btnStart = findViewById(R.id.btn_start);
        Button btnLeaderboard = findViewById(R.id.btn_leaderboard);
        TextView tvDifficulty = findViewById(R.id.tv_selected_difficulty);

        btnEasy.setOnClickListener(v -> {
            selectedDifficulty = GameConstant.DIFFICULTY_EASY;
            tvDifficulty.setText("已选：简单");
        });
        btnNormal.setOnClickListener(v -> {
            selectedDifficulty = GameConstant.DIFFICULTY_NORMAL;
            tvDifficulty.setText("已选：普通");
        });
        btnHard.setOnClickListener(v -> {
            selectedDifficulty = GameConstant.DIFFICULTY_HARD;
            tvDifficulty.setText("已选：困难");
        });

        btnStart.setOnClickListener(v -> {
            Intent i = new Intent(this, GameActivity.class);
            i.putExtra("difficulty", selectedDifficulty);
            startActivity(i);
        });

        btnLeaderboard.setOnClickListener(v ->
            startActivity(new Intent(this, LeaderboardActivity.class))
        );
    }
}
