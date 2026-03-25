package edu.hitsz.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import edu.hitsz.game.GameSurfaceView;

/**
 * 游戏主界面 Activity
 * 持有 GameSurfaceView，处理生命周期（暂停/恢复音乐、停止游戏线程）
 */
public class GameActivity extends AppCompatActivity {
    private GameSurfaceView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 全屏沉浸式（API 30+ 使用 WindowInsetsController 替代废弃的 FLAG_FULLSCREEN）
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat ctrl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctrl.hide(WindowInsetsCompat.Type.systemBars());
        ctrl.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        String difficulty = getIntent().getStringExtra("difficulty");

        gameView = new GameSurfaceView(this);
        if (difficulty != null) gameView.setDifficulty(difficulty);

        // 游戏结束后跳转结算界面（由主线程Handler回调）
        gameView.setGameOverCallback(score -> {
            Intent intent = new Intent(this, GameOverActivity.class);
            intent.putExtra("score", score);
            intent.putExtra("difficulty", difficulty);
            startActivity(intent);
            finish();
        });

        setContentView(gameView);
    }

    @Override protected void onPause() { super.onPause(); if (gameView != null) gameView.pause(); }
    @Override protected void onResume() { super.onResume(); if (gameView != null) gameView.resumeGame(); }
}
