package edu.hitsz.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import edu.hitsz.game.GameGLView;

/**
 * 游戏主界面 Activity
 * 持有 GameGLView（OpenGL ES 2.0 三渲二渲染），处理生命周期。
 */
public class GameActivity extends AppCompatActivity {
    private GameGLView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 全屏沉浸式（API 30+ 使用 WindowInsetsController）
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat ctrl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctrl.hide(WindowInsetsCompat.Type.systemBars());
        ctrl.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        String difficulty = getIntent().getStringExtra("difficulty");

        gameView = new GameGLView(this);
        if (difficulty != null) gameView.setDifficulty(difficulty);

        // 游戏结束后跳转结算界面（由主线程 Handler 回调）
        gameView.setGameOverCallback(score -> {
            Intent intent = new Intent(this, GameOverActivity.class);
            intent.putExtra("score", score);
            intent.putExtra("difficulty", difficulty);
            startActivity(intent);
            finish();
        });

        setContentView(gameView);
    }

    @Override protected void onPause()   { super.onPause();   if (gameView != null) gameView.pause(); }
    @Override protected void onResume()  { super.onResume();  if (gameView != null) gameView.resumeGame(); }
    @Override protected void onDestroy() { super.onDestroy(); if (gameView != null) gameView.onDestroy(); }
}
