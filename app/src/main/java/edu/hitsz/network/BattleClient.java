package edu.hitsz.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 联机对战客户端 - 简化版HTTP实现
 */
public class BattleClient {
    
    private static final String TAG = "BattleClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient client;
    private final Gson gson;
    private final Handler mainHandler;
    private String baseUrl;
    private int port;
    
    private String roomId;
    private String playerId;
    private String playerName;
    private String opponentName;
    
    public interface BattleCallback {
        void onRoomCreated(String roomId, String playerId);
        void onJoinedRoom(String playerId, String opponentName);
        void onScoreUpdated(int opponentScore, boolean opponentDead, String battleStatus);
        void onError(String error);
    }
    
    public interface RoomStatusCallback {
        void onStatusReceived(String status, boolean hasPlayer2, String player2Name);
        void onError(String error);
    }
    
    public BattleClient(String serverIp, int port) {
        this.port = port;
        this.baseUrl = "http://" + serverIp + ":" + port;
        Log.d(TAG, "初始化BattleClient: " + baseUrl);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void createRoom(String playerName, BattleCallback callback) {
        this.playerName = playerName;
        
        JsonObject json = new JsonObject();
        json.addProperty("playerName", playerName);
        
        String jsonString = gson.toJson(json);
        Log.d(TAG, "创建房间请求: " + baseUrl + "/battle/create");
        Log.d(TAG, "请求体: " + jsonString);
        
        RequestBody body = RequestBody.create(jsonString, JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/battle/create")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "创建房间网络失败", e);
                mainHandler.post(() -> callback.onError("网络错误：" + e.getMessage()));
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                Log.d(TAG, "创建房间响应码: " + response.code());
                Log.d(TAG, "创建房间响应: " + responseBody);
                
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                        if (json.has("success") && json.get("success").getAsBoolean()) {
                            roomId = json.get("roomId").getAsString();
                            playerId = json.get("playerId").getAsString();
                            mainHandler.post(() -> callback.onRoomCreated(roomId, playerId));
                        } else {
                            String error = json.has("error") ? json.get("error").getAsString() : "创建房间失败";
                            mainHandler.post(() -> callback.onError(error));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析响应失败", e);
                        mainHandler.post(() -> callback.onError("解析响应失败: " + e.getMessage()));
                    }
                } else {
                    String errorMsg = "服务器错误 " + response.code() + ": " + response.message();
                    if (response.code() == 405) {
                        errorMsg = "方法不被允许 - 服务器可能未正确启动";
                    }
                    Log.e(TAG, errorMsg + "\n响应: " + responseBody);
                    String finalErrorMsg = errorMsg;
                    mainHandler.post(() -> callback.onError(finalErrorMsg));
                }
            }
        });
    }
    
    public void joinRoom(String roomId, String playerName, BattleCallback callback) {
        this.roomId = roomId;
        this.playerName = playerName;
        
        JsonObject json = new JsonObject();
        json.addProperty("roomId", roomId);
        json.addProperty("playerName", playerName);
        
        RequestBody body = RequestBody.create(gson.toJson(json), JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/battle/join")
                .post(body)
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "加入房间失败", e);
                mainHandler.post(() -> callback.onError("网络错误：" + e.getMessage()));
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                Log.d(TAG, "加入房间响应: " + responseBody);
                
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                        
                        // 先检查是否有错误
                        if (json.has("error")) {
                            String error = json.get("error").getAsString();
                            mainHandler.post(() -> callback.onError(error));
                            return;
                        }
                        
                        // 检查成功标志
                        if (json.has("success") && json.get("success").getAsBoolean()) {
                            playerId = json.get("playerId").getAsString();
                            opponentName = json.get("opponentName").getAsString();
                            mainHandler.post(() -> callback.onJoinedRoom(playerId, opponentName));
                        } else {
                            mainHandler.post(() -> callback.onError("加入房间失败"));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析加入房间响应失败", e);
                        mainHandler.post(() -> callback.onError("解析响应失败: " + e.getMessage()));
                    }
                } else {
                    mainHandler.post(() -> callback.onError("服务器错误: " + response.code()));
                }
            }
        });
    }
    
    public void updateScore(int score, boolean isDead, BattleCallback callback) {
        if (roomId == null || playerId == null) {
            Log.w(TAG, "房间信息不完整");
            return;
        }
        
        JsonObject json = new JsonObject();
        json.addProperty("roomId", roomId);
        json.addProperty("playerId", playerId);
        json.addProperty("score", score);
        json.addProperty("isDead", isDead);
        
        RequestBody body = RequestBody.create(gson.toJson(json), JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/battle/update")
                .post(body)
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "更新分数失败", e);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                        
                        // 检查是否有错误
                        if (json.has("error")) {
                            Log.w(TAG, "更新分数错误: " + json.get("error").getAsString());
                            return;
                        }
                        
                        // 检查成功标志
                        if (json.has("success") && json.get("success").getAsBoolean()) {
                            int opponentScore = json.get("opponentScore").getAsInt();
                            boolean opponentDead = json.get("opponentDead").getAsBoolean();
                            String battleStatus = json.get("battleStatus").getAsString();
                            
                            // 如果对手名称还未设置，尝试从响应中获取
                            if (opponentName == null && json.has("opponentName")) {
                                opponentName = json.get("opponentName").getAsString();
                            }
                            
                            mainHandler.post(() -> callback.onScoreUpdated(opponentScore, opponentDead, battleStatus));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析更新分数响应失败", e);
                    }
                }
            }
        });
    }
    
    /**
     * 检查房间状态（用于轮询）
     */
    public void checkRoomStatus(RoomStatusCallback callback) {
        if (roomId == null || playerId == null) {
            Log.w(TAG, "房间信息不完整");
            mainHandler.post(() -> callback.onError("房间信息不完整"));
            return;
        }
        
        String url = baseUrl + "/battle/status?roomId=" + roomId + "&playerId=" + playerId;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "检查房间状态失败", e);
                mainHandler.post(() -> callback.onError("网络错误：" + e.getMessage()));
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                        
                        // 检查是否有错误
                        if (json.has("error")) {
                            String error = json.get("error").getAsString();
                            mainHandler.post(() -> callback.onError(error));
                            return;
                        }
                        
                        // 检查成功标志
                        if (json.has("success") && json.get("success").getAsBoolean()) {
                            String status = json.get("status").getAsString();
                            boolean hasPlayer2 = json.has("hasPlayer2") && json.get("hasPlayer2").getAsBoolean();
                            String player2Name = json.has("player2Name") ? json.get("player2Name").getAsString() : null;
                            
                            mainHandler.post(() -> callback.onStatusReceived(status, hasPlayer2, player2Name));
                        } else {
                            mainHandler.post(() -> callback.onError("获取状态失败"));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析房间状态响应失败", e);
                        mainHandler.post(() -> callback.onError("解析响应失败: " + e.getMessage()));
                    }
                } else {
                    mainHandler.post(() -> callback.onError("服务器错误: " + response.code()));
                }
            }
        });
    }
    
    public void endBattle() {
        if (roomId == null) return;
        
        JsonObject json = new JsonObject();
        json.addProperty("roomId", roomId);
        
        RequestBody body = RequestBody.create(gson.toJson(json), JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/battle/end")
                .post(body)
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "结束对战失败", e);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d(TAG, "对战已结束");
            }
        });
    }
    
    public String getRoomId() { return roomId; }
    public String getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public String getOpponentName() { return opponentName; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public void setOpponentName(String opponentName) { this.opponentName = opponentName; }
}
