package edu.hitsz.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import edu.hitsz.sufaceview.MainActivity;

/**
 * 多人对战结算页面
 * 显示双方分数和胜负结果
 */
public class MultiplayerResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        int myScore = getIntent().getIntExtra("myScore", 0);
        int opponentScore = getIntent().getIntExtra("opponentScore", 0);
        boolean isWinner = getIntent().getBooleanExtra("isWinner", false);
        
        setContentView(buildUI(myScore, opponentScore, isWinner));
    }
    
    private View buildUI(int myScore, int opponentScore, boolean isWinner) {
        // 根布局
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(32), dp(48), dp(32), dp(48));
        
        // 星空背景
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFF03030F, 0xFF070720, 0xFF0A0A2A}
        );
        root.setBackground(bg);
        
        vsp(root, 40);
        
        // 胜负标题
        TextView tvResult = new TextView(this);
        if (myScore == opponentScore) {
            tvResult.setText("🤝 平局");
            tvResult.setTextColor(0xFFFFD700);
        } else if (isWinner) {
            tvResult.setText("🎉 胜利！");
            tvResult.setTextColor(0xFF00E676);
        } else {
            tvResult.setText("💔 失败");
            tvResult.setTextColor(0xFFFF5252);
        }
        tvResult.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        tvResult.setTypeface(Typeface.DEFAULT_BOLD);
        tvResult.setGravity(Gravity.CENTER);
        tvResult.setShadowLayer(16, 0, 0, 0xFF000000);
        root.addView(tvResult, mw());
        
        vsp(root, 40);
        
        // 分数对比面板
        LinearLayout scorePanel = new LinearLayout(this);
        scorePanel.setOrientation(LinearLayout.VERTICAL);
        scorePanel.setGravity(Gravity.CENTER_HORIZONTAL);
        scorePanel.setPadding(dp(24), dp(24), dp(24), dp(24));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(0xCC1A2A3A);
        panelBg.setCornerRadius(dp(16));
        panelBg.setStroke(dp(2), 0xFF2A4A5A);
        scorePanel.setBackground(panelBg);
        
        // 我的分数
        TextView tvMyLabel = tv("你的分数", 16, 0xFFAABBCC);
        tvMyLabel.setGravity(Gravity.CENTER);
        scorePanel.addView(tvMyLabel, mw());
        
        vsp(scorePanel, 8);
        
        TextView tvMyScore = tv(String.valueOf(myScore), 56, 0xFF00E676);
        tvMyScore.setGravity(Gravity.CENTER);
        tvMyScore.setTypeface(Typeface.DEFAULT_BOLD);
        tvMyScore.setShadowLayer(12, 0, 0, 0xFF000000);
        scorePanel.addView(tvMyScore, mw());
        
        vsp(scorePanel, 24);
        
        // VS分隔
        TextView tvVs = tv("VS", 24, 0xFFFFD700);
        tvVs.setGravity(Gravity.CENTER);
        tvVs.setTypeface(Typeface.DEFAULT_BOLD);
        scorePanel.addView(tvVs, mw());
        
        vsp(scorePanel, 24);
        
        // 对手分数
        TextView tvOpLabel = tv("对手分数", 16, 0xFFAABBCC);
        tvOpLabel.setGravity(Gravity.CENTER);
        scorePanel.addView(tvOpLabel, mw());
        
        vsp(scorePanel, 8);
        
        TextView tvOpScore = tv(String.valueOf(opponentScore), 56, 0xFFFF5252);
        tvOpScore.setGravity(Gravity.CENTER);
        tvOpScore.setTypeface(Typeface.DEFAULT_BOLD);
        tvOpScore.setShadowLayer(12, 0, 0, 0xFF000000);
        scorePanel.addView(tvOpScore, mw());
        
        root.addView(scorePanel, mw());
        
        vsp(root, 40);
        
        // 分数差距
        int diff = Math.abs(myScore - opponentScore);
        if (diff > 0) {
            TextView tvDiff = tv("分数差距: " + diff, 18, 0xFFCCDDEE);
            tvDiff.setGravity(Gravity.CENTER);
            root.addView(tvDiff, mw());
            vsp(root, 24);
        }
        
        // 返回主菜单按钮
        Button btnBack = createButton("返回主菜单", 0xFF2979FF, 0xFF1565C0);
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
        root.addView(btnBack, mw());
        
        vsp(root, 12);
        
        // 再来一局按钮
        Button btnAgain = createButton("再来一局", 0xFF00796B, 0xFF00BFA5);
        btnAgain.setOnClickListener(v -> {
            Intent intent = new Intent(this, MultiplayerLobbyActivity.class);
            startActivity(intent);
            finish();
        });
        root.addView(btnAgain, mw());
        
        return root;
    }
    
    // UI工具方法
    private Button createButton(String text, int c1, int c2) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{c1, c2}
        );
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), 0x33FFFFFF);
        b.setBackground(bg);
        b.setPadding(0, dp(12), 0, dp(12));
        return b;
    }
    
    private TextView tv(String text, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }
    
    private void vsp(ViewGroup parent, int dpVal) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(dpVal)
        ));
        parent.addView(v);
    }
    
    private LinearLayout.LayoutParams mw() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }
    
    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }
}
