package edu.hitsz.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import edu.hitsz.db.UserRepository;
import edu.hitsz.game.GameGLView;
import edu.hitsz.network.BattleClient;

/**
 * 多人对战游戏界面
 * 实时同步双方分数，显示对手状态
 */
public class MultiplayerGameActivity extends AppCompatActivity {

    private GameGLView gameView;
    private HpBarView heroHpBar, bossHpBar;
    private TextView tvMyScore, tvOpponentScore, tvBossLabel, tvOpponentStatus;
    private LinearLayout bossSection;
    
    private BattleClient battleClient;
    private String roomId, playerId, serverIp;
    private int port;
    private boolean isCreator;
    
    private int myScore = 0;
    private int opponentScore = 0;
    private boolean opponentDead = false;
    private boolean myDead = false;
    
    private Handler syncHandler = new Handler(Looper.getMainLooper());
    private static final int SYNC_INTERVAL = 1000; // 每秒同步一次

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat ctrl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctrl.hide(WindowInsetsCompat.Type.systemBars());
        ctrl.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        // 获取房间信息
        roomId = getIntent().getStringExtra("roomId");
        playerId = getIntent().getStringExtra("playerId");
        serverIp = getIntent().getStringExtra("serverIp");
        port = getIntent().getIntExtra("port", 8080);
        isCreator = getIntent().getBooleanExtra("isCreator", false);
        
        // 初始化网络客户端
        battleClient = new BattleClient(serverIp, port);
        battleClient.setRoomId(roomId);
        battleClient.setPlayerId(playerId);

        FrameLayout root = new FrameLayout(this);
        gameView = new GameGLView(this);
        root.addView(gameView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(buildMultiplayerHUD(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        gameView.setHudViews2(this::updateHUD);

        gameView.setGameOverCallback(score -> {
            myScore = score;
            myDead = true;
            
            // 通知服务器我已死亡
            battleClient.updateScore(myScore, true, new BattleClient.BattleCallback() {
                @Override
                public void onRoomCreated(String roomId, String playerId) {}
                
                @Override
                public void onJoinedRoom(String playerId, String opponentName) {}
                
                @Override
                public void onScoreUpdated(int opScore, boolean opDead, String battleStatus) {
                    opponentScore = opScore;
                    opponentDead = opDead;
                    
                    // 如果双方都死亡，结束对战
                    if (myDead && opponentDead) {
                        endBattle();
                    }
                }
                
                @Override
                public void onError(String error) {}
            });
            
            // 等待对手结束
            runOnUiThread(() -> {
                tvOpponentStatus.setText("等待对手结束...");
                tvOpponentStatus.setVisibility(View.VISIBLE);
            });
        });

        setContentView(root);
        
        // 开始定期同步分数
        startScoreSync();
    }

    /** 构建多人对战HUD */
    private View buildMultiplayerHUD() {
        FrameLayout hud = new FrameLayout(this);
        hud.setClickable(false); hud.setFocusable(false);

        // ══ 顶部工具栏 ══════════════════════════════════════════════
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setPadding(dp(12), dp(10), dp(12), dp(10));

        GradientDrawable tbBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xDD060620, 0xBB060620});
        topBar.setBackground(tbBg);

        // 分数行
        LinearLayout scoreRow = new LinearLayout(this);
        scoreRow.setOrientation(LinearLayout.HORIZONTAL);
        scoreRow.setGravity(Gravity.CENTER_VERTICAL);

        // 我的分数（左，绿色）
        tvMyScore = new TextView(this);
        tvMyScore.setText("我: 0");
        tvMyScore.setTextColor(0xFF00E676);
        tvMyScore.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvMyScore.setTypeface(Typeface.DEFAULT_BOLD);
        tvMyScore.setShadowLayer(6, 1, 1, 0xFF000000);
        scoreRow.addView(tvMyScore, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 弹性空间
        View spacer = new View(this);
        scoreRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        // 对手分数（右，红色）
        tvOpponentScore = new TextView(this);
        tvOpponentScore.setText("对手: 0");
        tvOpponentScore.setTextColor(0xFFFF5252);
        tvOpponentScore.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvOpponentScore.setTypeface(Typeface.DEFAULT_BOLD);
        tvOpponentScore.setShadowLayer(6, 1, 1, 0xFF000000);
        scoreRow.addView(tvOpponentScore, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        topBar.addView(scoreRow, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // BOSS血条（可选）
        bossSection = new LinearLayout(this);
        bossSection.setOrientation(LinearLayout.VERTICAL);
        bossSection.setGravity(Gravity.CENTER_HORIZONTAL);
        bossSection.setVisibility(View.GONE);

        tvBossLabel = new TextView(this);
        tvBossLabel.setText("⚔ BOSS");
        tvBossLabel.setTextColor(0xFFFF5555);
        tvBossLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvBossLabel.setTypeface(Typeface.DEFAULT_BOLD);
        bossSection.addView(tvBossLabel);

        bossHpBar = new HpBarView(this, 0xFFFF3333, 0xFFFF8800);
        bossSection.addView(bossHpBar, new LinearLayout.LayoutParams(dp(160), dp(12)));
        topBar.addView(bossSection);

        FrameLayout.LayoutParams tbLP = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        tbLP.gravity = Gravity.TOP;
        hud.addView(topBar, tbLP);

        // ══ 对手状态提示（中央） ═══════════════════════════════════
        tvOpponentStatus = new TextView(this);
        tvOpponentStatus.setText("对手已死亡");
        tvOpponentStatus.setTextColor(0xFFFF5252);
        tvOpponentStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tvOpponentStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tvOpponentStatus.setGravity(Gravity.CENTER);
        tvOpponentStatus.setShadowLayer(8, 0, 0, 0xFF000000);
        tvOpponentStatus.setVisibility(View.GONE);
        
        FrameLayout.LayoutParams statusLP = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        statusLP.gravity = Gravity.CENTER;
        hud.addView(tvOpponentStatus, statusLP);

        // ══ 底部英雄HP血条 ═══════════════════════════════════════════
        LinearLayout hpWrap = new LinearLayout(this);
        hpWrap.setOrientation(LinearLayout.VERTICAL);
        hpWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        hpWrap.setPadding(dp(20), dp(10), dp(20), dp(14));
        GradientDrawable hpBg = new GradientDrawable();
        hpBg.setCornerRadius(dp(18)); hpBg.setColor(0xCC060620);
        hpBg.setStroke(dp(1), 0x44FFFFFF);
        hpWrap.setBackground(hpBg);

        TextView hpLabel = new TextView(this);
        hpLabel.setText("♥  生命值");
        hpLabel.setTextColor(0xFF80CBC4);
        hpLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hpLabel.setGravity(Gravity.CENTER);
        hpWrap.addView(hpLabel, new LinearLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT));

        heroHpBar = new HpBarView(this, 0xFF00E676, 0xFF69F0AE);
        hpWrap.addView(heroHpBar, new LinearLayout.LayoutParams(dp(220), dp(18)));

        FrameLayout.LayoutParams hpLP = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        hpLP.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        hpLP.setMargins(0, 0, 0, dp(24));
        hud.addView(hpWrap, hpLP);

        return hud;
    }

    private void updateHUD(int score, int hp, int maxHp, int bossHp, int bossMax) {
        myScore = score;
        
        if (tvMyScore != null) tvMyScore.setText("我: " + score);
        if (heroHpBar != null) heroHpBar.setProgress(hp, maxHp);
        if (bossSection != null) {
            if (bossHp >= 0) {
                bossSection.setVisibility(View.VISIBLE);
                if (tvBossLabel != null)
                    tvBossLabel.setText("⚔ BOSS  " + bossHp + "/" + bossMax);
                if (bossHpBar != null) bossHpBar.setProgress(bossHp, bossMax);
            } else {
                bossSection.setVisibility(View.GONE);
            }
        }
    }
    
    /** 定期同步分数到服务器 */
    private void startScoreSync() {
        syncHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (myDead && opponentDead) {
                    return; // 双方都死亡，停止同步
                }
                
                // 发送我的分数，获取对手分数
                battleClient.updateScore(myScore, myDead, new BattleClient.BattleCallback() {
                    @Override
                    public void onRoomCreated(String roomId, String playerId) {}
                    
                    @Override
                    public void onJoinedRoom(String playerId, String opponentName) {}
                    
                    @Override
                    public void onScoreUpdated(int opScore, boolean opDead, String battleStatus) {
                        opponentScore = opScore;
                        opponentDead = opDead;
                        
                        runOnUiThread(() -> {
                            if (tvOpponentScore != null) {
                                tvOpponentScore.setText("对手: " + opponentScore);
                            }
                            
                            if (opponentDead && tvOpponentStatus != null) {
                                tvOpponentStatus.setText("对手已死亡");
                                tvOpponentStatus.setVisibility(View.VISIBLE);
                            }
                        });
                        
                        // 如果双方都死亡，结束对战
                        if (myDead && opponentDead && "finished".equals(battleStatus)) {
                            endBattle();
                            return;
                        }
                    }
                    
                    @Override
                    public void onError(String error) {}
                });
                
                // 继续同步
                syncHandler.postDelayed(this, SYNC_INTERVAL);
            }
        }, SYNC_INTERVAL);
    }
    
    /** 结束对战，显示结果 */
    private void endBattle() {
        syncHandler.removeCallbacksAndMessages(null);
        
        // 通知服务器结束对战
        battleClient.endBattle();
        
        runOnUiThread(() -> {
            // 延迟跳转到结算页面
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent intent = new Intent(this, MultiplayerResultActivity.class);
                intent.putExtra("myScore", myScore);
                intent.putExtra("opponentScore", opponentScore);
                intent.putExtra("isWinner", myScore > opponentScore);
                startActivity(intent);
                finish();
            }, 1500);
        });
    }

    @Override protected void onPause()   { super.onPause();   if (gameView!=null) gameView.pause(); }
    @Override protected void onResume()  { super.onResume();  if (gameView!=null) gameView.resumeGame(); }
    @Override protected void onDestroy() { 
        super.onDestroy(); 
        syncHandler.removeCallbacksAndMessages(null);
        if (battleClient != null) battleClient.endBattle();
        if (gameView!=null) gameView.onDestroyView(); 
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    // ════════════════════════════════════════════════════════════
    // 渐变血条 View（复用自GameActivity）
    // ════════════════════════════════════════════════════════════
    public static class HpBarView extends View {
        private float progress = 1f;
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bg  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int c1, c2;

        public HpBarView(Context ctx, int c1, int c2) {
            super(ctx); this.c1=c1; this.c2=c2;
            bg.setColor(0xFF1A2A1A);
        }

        public void setProgress(int hp, int maxHp) {
            progress = maxHp>0 ? Math.max(0f, Math.min(1f, (float)hp/maxHp)) : 0f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w=getWidth(), h=getHeight(), r=h/2f;
            rect.set(0,0,w,h); canvas.drawRoundRect(rect, r, r, bg);
            if (progress > 0) {
                int lc1 = progress>0.3f ? c1 : 0xFFFF3333;
                int lc2 = progress>0.3f ? c2 : 0xFFFF6600;
                LinearGradient g = new LinearGradient(0,0,w*progress,0, lc1, lc2, Shader.TileMode.CLAMP);
                bar.setShader(g);
                rect.set(0,0,w*progress,h); canvas.drawRoundRect(rect,r,r,bar);
                Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG); shine.setColor(0x44FFFFFF);
                rect.set(2,2,w*progress-2,h*0.45f);
                if (rect.width()>0) canvas.drawRoundRect(rect,r*0.5f,r*0.5f,shine);
            }
            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE); border.setStrokeWidth(1.5f); border.setColor(0x55FFFFFF);
            rect.set(0.75f,0.75f,w-0.75f,h-0.75f); canvas.drawRoundRect(rect,r,r,border);
        }
    }
}
