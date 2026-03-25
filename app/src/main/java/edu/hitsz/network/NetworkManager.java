package edu.hitsz.network;

import android.util.Log;
import okhttp3.*;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 网络管理器
 * 任务书要求：
 *   1. Retrofit+OkHttp → 在线排行榜 RESTful API
 *   2. WebSocket → 多人实时对战（帧同步/状态同步）
 *
 * 实际后端部署后替换 BASE_URL，当前为接口预留占位
 */
public class NetworkManager {
    private static final String TAG = "NetworkManager";
    /** 替换为实际后端地址（SpringBoot服务器） */
    public static final String BASE_URL = "http://your-server-ip:8080/";

    private static volatile NetworkManager instance;
    private final OkHttpClient httpClient;
    private final ScoreApiService apiService;
    private WebSocket webSocket;

    /** 多人实时对战事件监听接口（WebSocket帧同步） */
    public interface MultiplayerListener {
        void onConnected(String sessionId);
        void onOpponentUpdate(float x, float y);   // 对手位置同步
        void onBulletFired(float x, float y);       // 对手子弹事件
        void onGameEvent(String type, String data); // 通用游戏事件（击杀/道具/等）
        void onDisconnected(String reason);
    }

    private MultiplayerListener multiplayerListener;

    private NetworkManager() {
        httpClient = new OkHttpClient.Builder().build();
        apiService = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ScoreApiService.class);
    }

    public static NetworkManager getInstance() {
        if (instance == null) synchronized (NetworkManager.class) {
            if (instance == null) instance = new NetworkManager();
        }
        return instance;
    }

    public ScoreApiService getApiService() { return apiService; }

    // ===== WebSocket 多人联机接口 =====

    /** 连接到对战匹配服务器 */
    public void connectMultiplayer(String serverWsUrl, MultiplayerListener listener) {
        this.multiplayerListener = listener;
        Request request = new Request.Builder().url(serverWsUrl).build();
        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response r) {
                if (listener != null) listener.onConnected(r.header("X-Session-Id", "unknown"));
            }
            @Override public void onMessage(WebSocket ws, String text) {
                if (listener != null) parseMessage(text, listener);
            }
            @Override public void onFailure(WebSocket ws, Throwable t, Response r) {
                Log.e(TAG, "WebSocket error: " + t.getMessage());
                if (listener != null) listener.onDisconnected(t.getMessage());
            }
            @Override public void onClosed(WebSocket ws, int code, String reason) {
                if (listener != null) listener.onDisconnected(reason);
            }
        });
    }

    /** 发送己方位置（状态同步） */
    public void sendPosition(float x, float y) {
        if (webSocket != null) webSocket.send("{\"type\":\"pos\",\"x\":" + x + ",\"y\":" + y + "}");
    }

    /** 发送己方开火事件 */
    public void sendFire(float x, float y) {
        if (webSocket != null) webSocket.send("{\"type\":\"fire\",\"x\":" + x + ",\"y\":" + y + "}");
    }

    /** 断开连接 */
    public void disconnect() {
        if (webSocket != null) { webSocket.close(1000, "Game ended"); webSocket = null; }
    }

    private void parseMessage(String msg, MultiplayerListener listener) {
        // 简单JSON解析（生产环境使用Gson）
        if (msg.contains("\"type\":\"pos\"")) {
            float x = parseFloat(msg, "x");
            float y = parseFloat(msg, "y");
            listener.onOpponentUpdate(x, y);
        } else if (msg.contains("\"type\":\"fire\"")) {
            listener.onBulletFired(parseFloat(msg, "x"), parseFloat(msg, "y"));
        } else {
            listener.onGameEvent("unknown", msg);
        }
    }

    private float parseFloat(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\":") + key.length() + 3;
            int end = json.indexOf(",", idx);
            if (end < 0) end = json.indexOf("}", idx);
            return Float.parseFloat(json.substring(idx, end).trim());
        } catch (Exception e) { return 0; }
    }
}
