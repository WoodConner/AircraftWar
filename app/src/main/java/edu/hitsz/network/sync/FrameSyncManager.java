package edu.hitsz.network.sync;

import android.util.Log;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 帧同步（Lockstep）管理器 —— 多人对战联网核心
 *
 * 设计原则：确定性帧同步（Lockstep）
 *   1. 每隔 SYNC_TICK_MS（50ms = 20Hz）打包本地输入发送给对方
 *   2. 双方在同一 tick 内使用彼此的输入推进同一份确定性游戏逻辑
 *   3. 游戏逻辑只依赖输入，不依赖时钟，保证双方完全同步
 *
 * 使用方式（由 GameActivity 注入 GameGLView）：
 *   FrameSyncManager mgr = new FrameSyncManager("ws://your-server/ws");
 *   gameGlView.setFrameSyncManager(mgr);
 *
 * 替代原Windows版：无网络对战功能（原版单机）
 * Android实现：OkHttp WebSocket（全双工，低延迟）
 */
public class FrameSyncManager extends WebSocketListener {

    private static final String TAG = "FrameSyncMgr";
    /** 帧同步tick间隔：50ms = 20次/秒 */
    public static final long SYNC_TICK_MS = 50;
    /** 接收队列容量 */
    private static final int QUEUE_CAPACITY = 128;

    // ─── 网络 ─────────────────────────────────────────────────────────────
    private final OkHttpClient httpClient;
    private WebSocket webSocket;
    private volatile boolean connected = false;

    // ─── 帧队列（接收的远端输入） ─────────────────────────────────────────
    private final BlockingQueue<FrameInput> remoteInputQueue =
            new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    // ─── 本地输入缓冲 ─────────────────────────────────────────────────────
    private volatile float localTouchX = -1f;
    private volatile float localTouchY = -1f;

    // ─── tick计数 ─────────────────────────────────────────────────────────
    private long localTick  = 0;
    private long remoteTick = 0;
    private long lastSyncMs = 0;

    // ─── 玩家ID ───────────────────────────────────────────────────────────
    private final String playerId;
    private String remotePlayerId = "remote";

    // ─── 回调 ─────────────────────────────────────────────────────────────
    private FrameSyncListener listener;

    public interface FrameSyncListener {
        /** 收到远端帧输入 */
        void onRemoteInput(FrameInput input);
        /** 连接状态变化 */
        void onConnectionState(boolean connected);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 构造 & 连接
    // ═══════════════════════════════════════════════════════════════════════
    public FrameSyncManager(String playerId) {
        this.playerId = (playerId != null) ? playerId : "player_" + System.currentTimeMillis();
        this.httpClient = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 连接到帧同步服务器
     * @param wsUrl WebSocket地址，例如 "ws://192.168.1.100:8080/game"
     */
    public void connect(String wsUrl) {
        if (wsUrl == null || wsUrl.isEmpty()) {
            Log.w(TAG, "No server URL, running in local-only mode");
            return;
        }
        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = httpClient.newWebSocket(request, this);
        Log.i(TAG, "Connecting to " + wsUrl);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // WebSocketListener 回调（在OkHttp的IO线程）
    // ═══════════════════════════════════════════════════════════════════════
    @Override
    public void onOpen(WebSocket ws, Response resp) {
        connected = true;
        Log.i(TAG, "WebSocket connected");
        // 发送握手：告知服务器我的ID
        sendJson(buildHandshake());
        if (listener != null) listener.onConnectionState(true);
    }

    @Override
    public void onMessage(WebSocket ws, String text) {
        try {
            JSONObject obj = new JSONObject(text);
            String type = obj.optString("type");

            if ("input".equals(type)) {
                // 收到远端玩家的输入帧
                FrameInput fi = FrameInput.fromJson(obj);
                remoteInputQueue.offer(fi);   // 非阻塞，丢弃若队列满
                remoteTick = fi.tick;
                if (listener != null) listener.onRemoteInput(fi);

            } else if ("handshake".equals(type)) {
                remotePlayerId = obj.optString("playerId", "remote");
                Log.i(TAG, "Remote player: " + remotePlayerId);
            }
        } catch (Exception e) {
            Log.w(TAG, "Parse error: " + e.getMessage());
        }
    }

    @Override
    public void onFailure(WebSocket ws, Throwable t, Response resp) {
        connected = false;
        Log.e(TAG, "WebSocket failure: " + t.getMessage());
        if (listener != null) listener.onConnectionState(false);
    }

    @Override
    public void onClosed(WebSocket ws, int code, String reason) {
        connected = false;
        Log.i(TAG, "WebSocket closed: " + reason);
        if (listener != null) listener.onConnectionState(false);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 由 GameGLView 在每帧末尾调用
    // ═══════════════════════════════════════════════════════════════════════
    /**
     * 游戏逻辑帧结束时调用
     * 按 SYNC_TICK_MS 节拍向服务器推送本地输入帧
     */
    public void onFrameEnd(long nowMs, int score) {
        if (!connected) return;
        if (nowMs - lastSyncMs < SYNC_TICK_MS) return;
        lastSyncMs = nowMs;
        localTick++;

        // 打包本地输入并发送
        FrameInput local = new FrameInput(localTick, playerId, localTouchX, localTouchY, score);
        sendJson(local.toJson());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 由 GameGLView 的 onTouchListener 调用
    // ═══════════════════════════════════════════════════════════════════════
    /** 更新本地触屏坐标（将在下一个 tick 发出） */
    public void pushLocalInput(float x, float y) {
        localTouchX = x;
        localTouchY = y;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 消费远端输入（游戏逻辑线程调用）
    // ═══════════════════════════════════════════════════════════════════════
    /**
     * 取出下一帧远端输入（非阻塞）
     * 若远端帧未到则返回null（实际项目应加帧等待逻辑）
     */
    public FrameInput pollRemoteInput() {
        return remoteInputQueue.poll();
    }

    /**
     * 帧同步延迟状态：本地tick超前远端多少帧
     * 大于2帧时游戏逻辑应等待远端追上
     */
    public long getFrameLead() {
        return localTick - remoteTick;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 工具
    // ═══════════════════════════════════════════════════════════════════════
    private void sendJson(JSONObject obj) {
        if (webSocket != null && connected) {
            webSocket.send(obj.toString());
        }
    }

    private JSONObject buildHandshake() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "handshake");
            obj.put("playerId", playerId);
            return obj;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    public void setListener(FrameSyncListener l) { this.listener = l; }
    public boolean isConnected() { return connected; }

    /** Activity.onDestroy 时调用 */
    public void close() {
        if (webSocket != null) {
            webSocket.close(1000, "game over");
            webSocket = null;
        }
        httpClient.dispatcher().executorService().shutdown();
        connected = false;
    }
}
