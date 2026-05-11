package edu.hitsz.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import edu.hitsz.db.UserRepository;
import edu.hitsz.network.BattleClient;
import edu.hitsz.server.ServerManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 多人对战大厅
 * 功能：创建房间、加入房间、等待对手
 */
public class MultiplayerLobbyActivity extends AppCompatActivity {

    private EditText etServerIp, etRoomId, etPort;
    private TextView tvStatus, tvPlayerName, tvServerStatus, tvLocalIp;
    private Button btnCreateRoom, btnJoinRoom, btnStartBattle, btnUseLocalServer;
    private LinearLayout roomInfoPanel;
    
    private BattleClient battleClient;
    private UserRepository userRepo;
    private ServerManager serverManager;
    private String currentRoomId;
    private String currentPlayerId;
    private boolean isRoomCreator = false;
    private Handler pollHandler = new Handler(Looper.getMainLooper());
    private int pollFailCount = 0; // 轮询失败计数
    private static final int MAX_POLL_FAILS = 5; // 最大失败次数
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userRepo = UserRepository.getInstance(this);
        serverManager = ServerManager.getInstance(this);
        
        setContentView(buildUI());
    }
    
    private View buildUI() {
        // 根布局
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        
        // 星空背景
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFF03030F, 0xFF070720, 0xFF0A0A2A}
        );
        root.setBackground(bg);
        
        // 标题
        TextView title = new TextView(this);
        title.setText("⚔️ 多人对战大厅");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(0xFFFFD700);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setShadowLayer(12, 0, 0, 0xCCFF8C00);
        root.addView(title, mw());
        
        vsp(root, 20);
        
        // 玩家名称显示
        tvPlayerName = tv("玩家：" + userRepo.getCurrentUser(), 16, 0xFFCCDDEE);
        tvPlayerName.setGravity(Gravity.CENTER);
        root.addView(tvPlayerName, mw());
        
        vsp(root, 16);
        
        // 本机IP显示
        tvLocalIp = tv("本机IP: " + getLocalIpAddress(), 13, 0xFF66BB6A);
        tvLocalIp.setGravity(Gravity.CENTER);
        tvLocalIp.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(tvLocalIp, mw());
        
        vsp(root, 12);
        
        // 重要提示
        TextView tvTip = tv("⚠️ 模拟器必须用 10.0.2.2 不要用10.0.2.15或10.0.2.16", 11, 0xFFFF0000);
        tvTip.setGravity(Gravity.CENTER);
        tvTip.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(tvTip, mw());
        
        vsp(root, 4);
        
        TextView tvTip2 = tv("只有一个设备启动服务器，其他设备加入房间", 11, 0xFFFFAA00);
        tvTip2.setGravity(Gravity.CENTER);
        root.addView(tvTip2, mw());
        
        vsp(root, 8);
        
        // 服务器IP输入
        TextView lblIp = tv("服务器地址：", 14, 0xFFAABBCC);
        root.addView(lblIp, mw());
        vsp(root, 4);
        
        etServerIp = new EditText(this);
        etServerIp.setHint("模拟器必须用 10.0.2.2");
        etServerIp.setText("10.0.2.2");
        etServerIp.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String ip = etServerIp.getText().toString().trim();
                // 检查是否错误使用了10.0.2.15或10.0.2.16
                if ("10.0.2.15".equals(ip) || "10.0.2.16".equals(ip)) {
                    Toast.makeText(this, "❌ 模拟器不能用" + ip + "，请改为10.0.2.2", Toast.LENGTH_LONG).show();
                    etServerIp.setText("10.0.2.2");
                }
            }
        });
        etServerIp.setTextColor(0xFFEEEEEE);
        etServerIp.setHintTextColor(0xFF666666);
        etServerIp.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0xFF1A2A3A);
        etBg.setCornerRadius(dp(8));
        etBg.setStroke(dp(1), 0xFF2A3A4A);
        etServerIp.setBackground(etBg);
        root.addView(etServerIp, mw());
        
        vsp(root, 8);
        
        // 端口输入
        TextView lblPort = tv("端口号：", 14, 0xFFAABBCC);
        root.addView(lblPort, mw());
        vsp(root, 4);
        
        etPort = new EditText(this);
        etPort.setHint("默认 8080");
        etPort.setText("8080");
        etPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etPort.setTextColor(0xFFEEEEEE);
        etPort.setHintTextColor(0xFF666666);
        etPort.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable etBg3 = new GradientDrawable();
        etBg3.setColor(0xFF1A2A3A);
        etBg3.setCornerRadius(dp(8));
        etBg3.setStroke(dp(1), 0xFF2A3A4A);
        etPort.setBackground(etBg3);
        root.addView(etPort, mw());
        
        vsp(root, 8);
        
        // 本地服务器状态和快捷按钮
        LinearLayout localServerPanel = new LinearLayout(this);
        localServerPanel.setOrientation(LinearLayout.HORIZONTAL);
        localServerPanel.setGravity(Gravity.CENTER_VERTICAL);
        
        tvServerStatus = tv("", 12, 0xFF66BB6A);
        tvServerStatus.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
        ));
        localServerPanel.addView(tvServerStatus);
        
        btnUseLocalServer = new Button(this);
        btnUseLocalServer.setText("📱 使用本地");
        btnUseLocalServer.setTextColor(0xFFFFFFFF);
        btnUseLocalServer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        GradientDrawable localBtnBg = new GradientDrawable();
        localBtnBg.setColor(0xFF7B1FA2);
        localBtnBg.setCornerRadius(dp(6));
        btnUseLocalServer.setBackground(localBtnBg);
        btnUseLocalServer.setPadding(dp(12), dp(6), dp(12), dp(6));
        btnUseLocalServer.setOnClickListener(v -> useLocalServer());
        localServerPanel.addView(btnUseLocalServer);
        
        root.addView(localServerPanel, mw());
        
        vsp(root, 8);
        
        // 扫描按钮
        Button btnScan = new Button(this);
        btnScan.setText("🔍 扫描局域网服务器");
        btnScan.setTextColor(0xFFFFFFFF);
        btnScan.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        GradientDrawable scanBg = new GradientDrawable();
        scanBg.setColor(0xFF00796B);
        scanBg.setCornerRadius(dp(8));
        btnScan.setBackground(scanBg);
        btnScan.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnScan.setOnClickListener(v -> showScanDialog());
        root.addView(btnScan, mw());
        
        // 更新服务器状态显示
        updateServerStatus();
        
        vsp(root, 16);
        
        // 创建房间按钮
        btnCreateRoom = createButton("🎮 创建房间", 0xFF2979FF, 0xFF1565C0);
        btnCreateRoom.setOnClickListener(v -> createRoom());
        root.addView(btnCreateRoom, mw());
        
        vsp(root, 12);
        
        // 分隔线
        TextView divider = tv("━━━━━━ 或 ━━━━━━", 14, 0xFF444444);
        divider.setGravity(Gravity.CENTER);
        root.addView(divider, mw());
        
        vsp(root, 12);
        
        // 房间ID输入
        TextView lblRoom = tv("房间ID：", 14, 0xFFAABBCC);
        root.addView(lblRoom, mw());
        vsp(root, 4);
        
        etRoomId = new EditText(this);
        etRoomId.setHint("输入房间ID");
        etRoomId.setTextColor(0xFFEEEEEE);
        etRoomId.setHintTextColor(0xFF666666);
        etRoomId.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable etBg2 = new GradientDrawable();
        etBg2.setColor(0xFF1A2A3A);
        etBg2.setCornerRadius(dp(8));
        etBg2.setStroke(dp(1), 0xFF2A3A4A);
        etRoomId.setBackground(etBg2);
        root.addView(etRoomId, mw());
        
        vsp(root, 12);
        
        // 加入房间按钮
        btnJoinRoom = createButton("🚪 加入房间", 0xFF00796B, 0xFF00BFA5);
        btnJoinRoom.setOnClickListener(v -> joinRoom());
        root.addView(btnJoinRoom, mw());
        
        vsp(root, 20);
        
        // 房间信息面板（初始隐藏）
        roomInfoPanel = new LinearLayout(this);
        roomInfoPanel.setOrientation(LinearLayout.VERTICAL);
        roomInfoPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        roomInfoPanel.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(0xCC1A2A3A);
        panelBg.setCornerRadius(dp(12));
        panelBg.setStroke(dp(2), 0xFF2A4A5A);
        roomInfoPanel.setBackground(panelBg);
        roomInfoPanel.setVisibility(View.GONE);
        
        tvStatus = tv("等待对手加入...", 16, 0xFFFFD700);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTypeface(Typeface.DEFAULT_BOLD);
        roomInfoPanel.addView(tvStatus, mw());
        
        vsp(roomInfoPanel, 12);
        
        btnStartBattle = createButton("⚔️ 开始对战", 0xFFD32F2F, 0xFFFF5252);
        btnStartBattle.setOnClickListener(v -> startBattle());
        btnStartBattle.setEnabled(false);
        roomInfoPanel.addView(btnStartBattle, mw());
        
        root.addView(roomInfoPanel, mw());
        
        vsp(root, 16);
        
        // 返回按钮
        Button btnBack = new Button(this);
        btnBack.setText("返回主菜单");
        btnBack.setTextColor(0xFF546E7A);
        btnBack.setBackgroundColor(Color.TRANSPARENT);
        btnBack.setOnClickListener(v -> finish());
        root.addView(btnBack, mw());
        
        return root;
    }
    
    private void createRoom() {
        String serverIp = etServerIp.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        
        if (serverIp.isEmpty()) {
            showError("请输入服务器地址\n\n模拟器请使用 10.0.2.2\n真机请使用 localhost");
            return;
        }
        
        // 检查是否使用本地服务器
        if ("10.0.2.2".equals(serverIp) || "localhost".equals(serverIp) || "127.0.0.1".equals(serverIp)) {
            // 检查本地服务器是否运行
            if (!serverManager.isServerRunning()) {
                new AlertDialog.Builder(this)
                    .setTitle("服务器未启动")
                    .setMessage("本地服务器未运行！\n\n请先点击【使用本地】按钮启动服务器，\n然后再创建房间。")
                    .setPositiveButton("启动服务器", (dialog, which) -> {
                        useLocalServer();
                    })
                    .setNegativeButton("取消", null)
                    .show();
                return;
            }
            
            // 检查端口是否匹配
            int port = 8080;
            if (!portStr.isEmpty()) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    // 使用默认端口
                }
            }
            
            int serverPort = serverManager.getServerPort();
            if (port != serverPort) {
                new AlertDialog.Builder(this)
                    .setTitle("端口不匹配")
                    .setMessage("服务器运行在端口 " + serverPort + "\n" +
                            "但您设置的端口是 " + port + "\n\n" +
                            "请修改端口号为 " + serverPort + " 或重启服务器")
                    .setPositiveButton("使用 " + serverPort, (dialog, which) -> {
                        etPort.setText(String.valueOf(serverPort));
                        doCreateRoom(serverIp, String.valueOf(serverPort));
                    })
                    .setNegativeButton("取消", null)
                    .show();
                return;
            }
        }
        
        doCreateRoom(serverIp, portStr);
    }
    
    private void doCreateRoom(String serverIp, String portStr) {
        
        int port = 8080;
        if (!portStr.isEmpty()) {
            try {
                port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535) {
                    showError("端口号必须在 1-65535 之间");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("端口号格式错误");
                return;
            }
        }
        
        btnCreateRoom.setEnabled(false);
        btnJoinRoom.setEnabled(false);
        
        battleClient = new BattleClient(serverIp, port);
        String playerName = userRepo.getCurrentUser();
        
        battleClient.createRoom(playerName, new BattleClient.BattleCallback() {
            @Override
            public void onRoomCreated(String roomId, String playerId) {
                currentRoomId = roomId;
                currentPlayerId = playerId;
                isRoomCreator = true;
                
                runOnUiThread(() -> {
                    roomInfoPanel.setVisibility(View.VISIBLE);
                    tvStatus.setText("房间ID: " + roomId + "\n等待对手加入...");
                    etRoomId.setText(roomId);
                    
                    // 开始轮询房间状态
                    startPollingRoomStatus();
                });
            }
            
            @Override
            public void onJoinedRoom(String playerId, String opponentName) {}
            
            @Override
            public void onScoreUpdated(int opponentScore, boolean opponentDead, String battleStatus) {}
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showError("创建房间失败：" + error);
                    btnCreateRoom.setEnabled(true);
                    btnJoinRoom.setEnabled(true);
                });
            }
        });
    }
    
    private void joinRoom() {
        String serverIp = etServerIp.getText().toString().trim();
        String roomId = etRoomId.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        
        if (serverIp.isEmpty()) {
            showError("请输入服务器地址\n\n模拟器请使用 10.0.2.2");
            return;
        }
        
        if (roomId.isEmpty()) {
            showError("请输入房间ID");
            return;
        }
        
        int port = 8080;
        if (!portStr.isEmpty()) {
            try {
                port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535) {
                    showError("端口号必须在 1-65535 之间");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("端口号格式错误");
                return;
            }
        }
        
        btnCreateRoom.setEnabled(false);
        btnJoinRoom.setEnabled(false);
        
        battleClient = new BattleClient(serverIp, port);
        String playerName = userRepo.getCurrentUser();
        
        battleClient.joinRoom(roomId, playerName, new BattleClient.BattleCallback() {
            @Override
            public void onRoomCreated(String roomId, String playerId) {}
            
            @Override
            public void onJoinedRoom(String playerId, String opponentName) {
                currentRoomId = roomId;
                currentPlayerId = playerId;
                isRoomCreator = false;
                
                runOnUiThread(() -> {
                    roomInfoPanel.setVisibility(View.VISIBLE);
                    tvStatus.setText("已加入房间\n对手: " + opponentName);
                    btnStartBattle.setEnabled(true);
                });
            }
            
            @Override
            public void onScoreUpdated(int opponentScore, boolean opponentDead, String battleStatus) {}
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showError("加入房间失败：" + error);
                    btnCreateRoom.setEnabled(true);
                    btnJoinRoom.setEnabled(true);
                });
            }
        });
    }
    
    private void startPollingRoomStatus() {
        pollFailCount = 0; // 重置失败计数
        
        final Runnable pollRunnable = new Runnable() {
            @Override
            public void run() {
                // 检查是否应该停止轮询
                if (currentRoomId == null || !isRoomCreator || isFinishing()) {
                    Log.d("MultiplayerLobby", "停止轮询：房间已关闭或Activity结束");
                    return;
                }
                
                // 检查是否超过最大失败次数
                if (pollFailCount >= MAX_POLL_FAILS) {
                    runOnUiThread(() -> {
                        showError("无法连接到服务器\n\n请检查：\n1. 服务器是否启动\n2. 地址是否正确（模拟器用10.0.2.2）\n3. 端口号是否匹配");
                        btnCreateRoom.setEnabled(true);
                        btnJoinRoom.setEnabled(true);
                        roomInfoPanel.setVisibility(View.GONE);
                    });
                    return;
                }
                
                final Runnable self = this;
                
                // 使用GET /battle/status接口检查房间状态
                battleClient.checkRoomStatus(new BattleClient.RoomStatusCallback() {
                    @Override
                    public void onStatusReceived(String status, boolean hasPlayer2, String player2Name) {
                        pollFailCount = 0; // 成功则重置计数
                        
                        if (hasPlayer2 && player2Name != null && !"等待中...".equals(player2Name)) {
                            // 对手已加入
                            battleClient.setOpponentName(player2Name);
                            runOnUiThread(() -> {
                                tvStatus.setText("✅ 对手已加入！\n对手: " + player2Name);
                                btnStartBattle.setEnabled(true);
                            });
                            // 停止轮询
                            Log.i("MultiplayerLobby", "对手已加入，停止轮询");
                        } else {
                            // 继续轮询
                            pollHandler.postDelayed(self, 2000);
                        }
                    }
                    
                    @Override
                    public void onError(String error) {
                        pollFailCount++;
                        Log.w("MultiplayerLobby", "轮询失败 (" + pollFailCount + "/" + MAX_POLL_FAILS + "): " + error);
                        
                        if (pollFailCount < MAX_POLL_FAILS) {
                            // 继续轮询（房间可能还在初始化）
                            pollHandler.postDelayed(self, 3000); // 增加到3秒，减少服务器压力
                        } else {
                            // 超过最大失败次数，停止轮询
                            runOnUiThread(() -> {
                                String detailedError = "连接失败 (" + pollFailCount + "次)\n\n" +
                                        "错误信息：" + error + "\n\n" +
                                        "请检查：\n" +
                                        "1. 服务器地址：" + etServerIp.getText().toString() + "\n" +
                                        "2. 端口号：" + etPort.getText().toString() + "\n" +
                                        "3. 模拟器必须用 10.0.2.2";
                                showError(detailedError);
                                btnCreateRoom.setEnabled(true);
                                btnJoinRoom.setEnabled(true);
                                roomInfoPanel.setVisibility(View.GONE);
                            });
                        }
                    }
                });
            }
        };
        pollHandler.postDelayed(pollRunnable, 1500); // 1.5秒后开始第一次轮询，给服务器更多初始化时间
    }
    
    private void startBattle() {
        if (currentRoomId == null || currentPlayerId == null) {
            showError("房间信息不完整");
            return;
        }
        
        String portStr = etPort.getText().toString().trim();
        int port = 8080;
        if (!portStr.isEmpty()) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                port = 8080;
            }
        }
        
        Intent intent = new Intent(this, MultiplayerGameActivity.class);
        intent.putExtra("roomId", currentRoomId);
        intent.putExtra("playerId", currentPlayerId);
        intent.putExtra("serverIp", etServerIp.getText().toString().trim());
        intent.putExtra("port", port);
        intent.putExtra("isCreator", isRoomCreator);
        startActivity(intent);
        finish();
    }
    
    /**
     * 获取本机IP地址
     */
    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        // 如果是模拟器IP (10.0.2.15)，提示使用10.0.2.2
                        if ("10.0.2.15".equals(ip)) {
                            return ip + " (模拟器请用10.0.2.2)";
                        }
                        return ip;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("MultiplayerLobby", "获取IP失败", e);
        }
        return "未知";
    }
    
    /**
     * 更新服务器状态显示
     */
    private void updateServerStatus() {
        int port = serverManager.getServerPort();
        if (serverManager.isServerRunning()) {
            tvServerStatus.setText("✅ 本地服务器运行中 (端口:" + port + ")");
            tvServerStatus.setTextColor(0xFF66BB6A);
        } else {
            tvServerStatus.setText("⚠️ 本地服务器未运行");
            tvServerStatus.setTextColor(0xFFFF9800);
        }
    }
    
    /**
     * 使用本地服务器
     */
    private void useLocalServer() {
        // 获取用户设置的端口
        String portStr = etPort.getText().toString().trim();
        int port = 8080;
        if (!portStr.isEmpty()) {
            try {
                port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535) {
                    showError("端口号必须在 1-65535 之间");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("端口号格式错误");
                return;
            }
        }
        
        final int finalPort = port;
        
        if (!serverManager.isServerRunning()) {
            new AlertDialog.Builder(this)
                .setTitle("启动本地服务器")
                .setMessage("⚠️ 模拟器联机说明：\n\n" +
                        "1. 只在【一个】模拟器上启动服务器\n" +
                        "2. 所有模拟器都使用地址：10.0.2.2\n" +
                        "3. 端口号：" + finalPort + "\n" +
                        "4. 启动服务器的模拟器创建房间\n" +
                        "5. 其他模拟器加入房间\n" +
                        "6. 每个房间支持2人对战\n\n" +
                        "是否启动本地服务器？")
                .setPositiveButton("启动", (dialog, which) -> {
                    serverManager.startServer(finalPort);
                    // 等待服务器启动
                    pollHandler.postDelayed(() -> {
                        updateServerStatus();
                        if (serverManager.isServerRunning()) {
                            etServerIp.setText("10.0.2.2");
                            etPort.setText(String.valueOf(finalPort));
                            Toast.makeText(this, "✅ 服务器已启动\n地址: 10.0.2.2:" + finalPort + "\n所有模拟器都用这个地址", Toast.LENGTH_LONG).show();
                        } else {
                            showError("服务器启动失败，请检查端口是否被占用");
                        }
                    }, 1000);
                })
                .setNegativeButton("取消", null)
                .show();
        } else {
            // 检查端口是否匹配
            int currentPort = serverManager.getServerPort();
            if (currentPort != finalPort) {
                new AlertDialog.Builder(this)
                    .setTitle("端口不匹配")
                    .setMessage("服务器正在端口 " + currentPort + " 运行\n" +
                            "您设置的端口是 " + finalPort + "\n\n" +
                            "是否重启服务器到新端口？")
                    .setPositiveButton("重启", (dialog, which) -> {
                        serverManager.stopServer();
                        pollHandler.postDelayed(() -> {
                            serverManager.startServer(finalPort);
                            pollHandler.postDelayed(() -> {
                                updateServerStatus();
                                if (serverManager.isServerRunning()) {
                                    etServerIp.setText("10.0.2.2");
                                    etPort.setText(String.valueOf(finalPort));
                                    Toast.makeText(this, "✅ 服务器已重启\n地址: 10.0.2.2:" + finalPort, Toast.LENGTH_LONG).show();
                                } else {
                                    showError("服务器重启失败");
                                }
                            }, 1000);
                        }, 500);
                    })
                    .setNegativeButton("使用当前端口", (dialog, which) -> {
                        etServerIp.setText("10.0.2.2");
                        etPort.setText(String.valueOf(currentPort));
                        Toast.makeText(this, "✅ 使用当前服务器\n地址: 10.0.2.2:" + currentPort, Toast.LENGTH_SHORT).show();
                    })
                    .show();
            } else {
                etServerIp.setText("10.0.2.2");
                etPort.setText(String.valueOf(currentPort));
                Toast.makeText(this, "✅ 服务器运行中\n地址: 10.0.2.2:" + currentPort, Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void showScanDialog() {
        ServerScanDialog dialog = new ServerScanDialog(this, (ip, port) -> {
            etServerIp.setText(ip);
            etPort.setText(String.valueOf(port));
            Toast.makeText(this, "已选择服务器: " + ip + ":" + port, Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }
    
    private void showError(String message) {
        new AlertDialog.Builder(this)
            .setTitle("提示")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        pollHandler.removeCallbacksAndMessages(null);
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
