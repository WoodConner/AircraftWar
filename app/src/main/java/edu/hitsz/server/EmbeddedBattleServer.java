package edu.hitsz.server;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import edu.hitsz.network.LanDiscovery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android内置HTTP服务器
 * 用于在Android设备上运行联机对战服务器
 */
public class EmbeddedBattleServer {
    
    private static final String TAG = "EmbeddedServer";
    private static final Gson gson = new Gson();
    private static final Map<String, BattleRoom> rooms = new ConcurrentHashMap<>();
    
    private int port;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private volatile boolean isRunning = false;
    private Thread serverThread;
    private Thread broadcastThread;
    
    public interface ServerCallback {
        void onServerStarted(int port);
        void onServerStopped();
        void onError(String error);
    }
    
    private ServerCallback callback;
    
    public EmbeddedBattleServer(ServerCallback callback) {
        this.callback = callback;
        this.port = 8080; // 默认端口
    }
    
    /**
     * 设置端口号（必须在start()之前调用）
     */
    public void setPort(int port) {
        if (!isRunning) {
            this.port = port;
        } else {
            Log.w(TAG, "服务器运行中，无法更改端口");
        }
    }
    
    /**
     * 获取当前端口号
     */
    public int getPort() {
        return port;
    }
    
    /**
     * 启动服务器
     */
    public void start() {
        if (isRunning) {
            Log.w(TAG, "服务器已在运行");
            return;
        }
        
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                serverSocket.setSoTimeout(1000); // 设置超时避免阻塞
                executorService = Executors.newFixedThreadPool(10);
                isRunning = true;
                
                // 启动局域网广播
                broadcastThread = LanDiscovery.startServerBroadcast(port, "AircraftWar", () -> rooms.size());
                
                Log.i(TAG, "服务器已启动，端口: " + port);
                if (callback != null) {
                    callback.onServerStarted(port);
                }
                
                while (isRunning && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        executorService.execute(() -> handleClient(clientSocket));
                    } catch (SocketTimeoutException e) {
                        // 超时是正常的，继续循环
                    } catch (IOException e) {
                        if (isRunning) {
                            Log.e(TAG, "接受连接失败", e);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "服务器启动失败", e);
                if (callback != null) {
                    callback.onError("服务器启动失败: " + e.getMessage());
                }
            } finally {
                cleanup();
            }
        });
        serverThread.setName("EmbeddedServer-" + port);
        serverThread.start();
    }
    
    /**
     * 停止服务器
     */
    public void stop() {
        isRunning = false;
        
        // 停止广播线程
        if (broadcastThread != null) {
            broadcastThread.interrupt();
            broadcastThread = null;
        }
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (executorService != null) {
                executorService.shutdown();
            }
        } catch (IOException e) {
            Log.e(TAG, "关闭服务器失败", e);
        }
    }
    
    private void cleanup() {
        isRunning = false;
        rooms.clear();
        Log.i(TAG, "服务器已停止");
        if (callback != null) {
            callback.onServerStopped();
        }
    }
    
    /**
     * 处理客户端请求
     */
    private void handleClient(Socket clientSocket) {
        try {
            clientSocket.setSoTimeout(5000); // 5秒超时
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream outputStream = clientSocket.getOutputStream();
            
            // 读取HTTP请求行
            String requestLine = reader.readLine();
            if (requestLine == null) {
                clientSocket.close();
                return;
            }
            
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                clientSocket.close();
                return;
            }
            
            String method = parts[0];
            String path = parts[1];
            
            // 读取请求头
            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }
            
            // 读取请求体
            String body = "";
            if (contentLength > 0) {
                char[] buffer = new char[contentLength];
                int read = reader.read(buffer, 0, contentLength);
                if (read > 0) {
                    body = new String(buffer, 0, read);
                }
            }
            
            // 路由处理
            String response = handleRequest(method, path, body);
            
            // 发送响应
            sendResponse(outputStream, 200, response);
            
            outputStream.close();
            reader.close();
            clientSocket.close();
            
        } catch (Exception e) {
            Log.e(TAG, "处理请求失败", e);
            try {
                clientSocket.close();
            } catch (IOException ex) {
                // 忽略
            }
        }
    }
    
    /**
     * 处理不同的API请求
     */
    private String handleRequest(String method, String path, String body) {
        try {
            if ("POST".equals(method) && path.startsWith("/battle/create")) {
                return handleCreateRoom(body);
            } else if ("POST".equals(method) && path.startsWith("/battle/join")) {
                return handleJoinRoom(body);
            } else if ("POST".equals(method) && path.startsWith("/battle/update")) {
                return handleUpdateScore(body);
            } else if ("GET".equals(method) && path.startsWith("/battle/status")) {
                return handleGetStatus(path);
            } else if ("POST".equals(method) && path.startsWith("/battle/end")) {
                return handleEndBattle(body);
            } else {
                return "{\"error\":\"未知的API路径\"}";
            }
        } catch (Exception e) {
            Log.e(TAG, "处理请求异常", e);
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
    
    private String handleCreateRoom(String body) {
        JsonObject req = gson.fromJson(body, JsonObject.class);
        String playerName = req.get("playerName").getAsString();
        
        String roomId = "ROOM_" + System.currentTimeMillis();
        BattleRoom room = new BattleRoom(roomId, playerName);
        rooms.put(roomId, room);
        
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("roomId", roomId);
        response.addProperty("playerId", room.player1Id);
        response.addProperty("message", "房间创建成功");
        
        Log.i(TAG, "[创建房间] " + roomId + " by " + playerName);
        return gson.toJson(response);
    }
    
    private String handleJoinRoom(String body) {
        JsonObject req = gson.fromJson(body, JsonObject.class);
        String roomId = req.get("roomId").getAsString();
        String playerName = req.get("playerName").getAsString();
        
        BattleRoom room = rooms.get(roomId);
        if (room == null) {
            return "{\"error\":\"房间不存在\"}";
        }
        
        if (room.player2Id != null) {
            return "{\"error\":\"房间已满\"}";
        }
        
        room.player2Name = playerName;
        room.player2Id = "P2_" + System.currentTimeMillis();
        room.status = "ready"; // 改为ready状态
        room.startTime = System.currentTimeMillis();
        
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("playerId", room.player2Id);
        response.addProperty("opponentName", room.player1Name);
        response.addProperty("message", "成功加入房间");
        
        Log.i(TAG, "[加入房间] " + roomId + " by " + playerName);
        return gson.toJson(response);
    }
    
    private String handleUpdateScore(String body) {
        JsonObject req = gson.fromJson(body, JsonObject.class);
        String roomId = req.get("roomId").getAsString();
        String playerId = req.get("playerId").getAsString();
        int score = req.get("score").getAsInt();
        boolean isDead = req.has("isDead") && req.get("isDead").getAsBoolean();
        
        BattleRoom room = rooms.get(roomId);
        if (room == null) {
            return "{\"error\":\"房间不存在\"}";
        }
        
        if (playerId.equals(room.player1Id)) {
            room.player1Score = score;
            room.player1Dead = isDead;
        } else if (playerId.equals(room.player2Id)) {
            room.player2Score = score;
            room.player2Dead = isDead;
        } else {
            return "{\"error\":\"无效的玩家ID\"}";
        }
        
        room.lastUpdateTime = System.currentTimeMillis();
        
        // 更新状态
        if ("ready".equals(room.status) && room.player2Id != null) {
            room.status = "playing";
        }
        
        if (room.player1Dead && room.player2Dead && !"finished".equals(room.status)) {
            room.status = "finished";
            room.endTime = System.currentTimeMillis();
            Log.i(TAG, "[对战结束] " + roomId + " | P1: " + room.player1Score + " vs P2: " + room.player2Score);
        }
        
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("roomStatus", room.status);
        
        if (playerId.equals(room.player1Id)) {
            response.addProperty("opponentScore", room.player2Score);
            response.addProperty("opponentDead", room.player2Dead);
            response.addProperty("opponentName", room.player2Name);
        } else {
            response.addProperty("opponentScore", room.player1Score);
            response.addProperty("opponentDead", room.player1Dead);
            response.addProperty("opponentName", room.player1Name);
        }
        
        response.addProperty("battleStatus", room.status);
        return gson.toJson(response);
    }
    
    private String handleGetStatus(String path) {
        // 解析查询参数
        String query = path.contains("?") ? path.substring(path.indexOf("?") + 1) : "";
        String roomId = getQueryParam(query, "roomId");
        String playerId = getQueryParam(query, "playerId");
        
        BattleRoom room = rooms.get(roomId);
        if (room == null) {
            return "{\"error\":\"房间不存在\"}";
        }
        
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("status", room.status);
        response.addProperty("player1Name", room.player1Name);
        response.addProperty("player2Name", room.player2Name != null ? room.player2Name : "等待中...");
        response.addProperty("hasPlayer2", room.player2Id != null);
        
        if (playerId != null) {
            if (playerId.equals(room.player1Id)) {
                response.addProperty("myScore", room.player1Score);
                response.addProperty("opponentScore", room.player2Score);
                response.addProperty("myDead", room.player1Dead);
                response.addProperty("opponentDead", room.player2Dead);
                response.addProperty("opponentName", room.player2Name);
            } else if (playerId.equals(room.player2Id)) {
                response.addProperty("myScore", room.player2Score);
                response.addProperty("opponentScore", room.player1Score);
                response.addProperty("myDead", room.player2Dead);
                response.addProperty("opponentDead", room.player1Dead);
                response.addProperty("opponentName", room.player1Name);
            }
        }
        
        return gson.toJson(response);
    }
    
    private String handleEndBattle(String body) {
        JsonObject req = gson.fromJson(body, JsonObject.class);
        String roomId = req.get("roomId").getAsString();
        
        BattleRoom room = rooms.remove(roomId);
        if (room != null) {
            Log.i(TAG, "[房间关闭] " + roomId);
        }
        
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("message", "对战已结束");
        
        return gson.toJson(response);
    }
    
    private void sendResponse(OutputStream outputStream, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        
        String httpResponse = "HTTP/1.1 " + statusCode + " OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        
        outputStream.write(httpResponse.getBytes(StandardCharsets.UTF_8));
        outputStream.write(bytes);
        outputStream.flush();
    }
    
    private String getQueryParam(String query, String key) {
        if (query == null || query.isEmpty()) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && pair[0].equals(key)) {
                return pair[1];
            }
        }
        return null;
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * 房间数据类
     */
    static class BattleRoom {
        String roomId;
        String status;
        
        String player1Id;
        String player1Name;
        int player1Score;
        boolean player1Dead;
        
        String player2Id;
        String player2Name;
        int player2Score;
        boolean player2Dead;
        
        long startTime;
        long endTime;
        long lastUpdateTime;
        
        BattleRoom(String roomId, String player1Name) {
            this.roomId = roomId;
            this.player1Name = player1Name;
            this.player1Id = "P1_" + System.currentTimeMillis();
            this.status = "waiting";
            this.player1Score = 0;
            this.player2Score = 0;
            this.player1Dead = false;
            this.player2Dead = false;
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }
}
