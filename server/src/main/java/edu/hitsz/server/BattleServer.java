package edu.hitsz.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * 飞机大战联机对战服务器
 * 功能：房间管理、实时分数同步、对战状态管理
 * 端口：8080
 */
public class BattleServer {
    
    private static final int PORT = 8080;
    private static final Gson gson = new Gson();
    private static final Map<String, BattleRoom> rooms = new ConcurrentHashMap<>();
    
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        server.createContext("/battle/create", new CreateRoomHandler());
        server.createContext("/battle/join", new JoinRoomHandler());
        server.createContext("/battle/update", new UpdateScoreHandler());
        server.createContext("/battle/status", new GetStatusHandler());
        server.createContext("/battle/end", new EndBattleHandler());
        
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        
        System.out.println("═══════════════════════════════════════════════");
        System.out.println("  飞机大战联机对战服务器已启动");
        System.out.println("  端口: " + PORT);
        System.out.println("  时间: " + new java.util.Date());
        System.out.println("═══════════════════════════════════════════════");
        System.out.println("\n客户端连接地址: http://localhost:" + PORT);
        System.out.println("模拟器使用: http://10.0.2.2:" + PORT);
        System.out.println("\n按 Ctrl+C 停止服务器\n");
    }
    
    static class CreateRoomHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            
            try {
                String body = readRequestBody(exchange);
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
                
                sendResponse(exchange, 200, gson.toJson(response));
                System.out.println("[创建房间] " + roomId + " by " + playerName);
                
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }
    
    static class JoinRoomHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            
            try {
                String body = readRequestBody(exchange);
                JsonObject req = gson.fromJson(body, JsonObject.class);
                String roomId = req.get("roomId").getAsString();
                String playerName = req.get("playerName").getAsString();
                
                BattleRoom room = rooms.get(roomId);
                if (room == null) {
                    sendResponse(exchange, 404, "{\"error\":\"房间不存在\"}");
                    return;
                }
                
                if (room.player2Id != null) {
                    sendResponse(exchange, 400, "{\"error\":\"房间已满\"}");
                    return;
                }
                
                room.player2Name = playerName;
                room.player2Id = "P2_" + System.currentTimeMillis();
                room.status = "playing";
                room.startTime = System.currentTimeMillis();
                
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("playerId", room.player2Id);
                response.addProperty("opponentName", room.player1Name);
                response.addProperty("message", "成功加入房间");
                
                sendResponse(exchange, 200, gson.toJson(response));
                System.out.println("[加入房间] " + roomId + " by " + playerName);
                
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }
    
    static class UpdateScoreHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            
            try {
                String body = readRequestBody(exchange);
                JsonObject req = gson.fromJson(body, JsonObject.class);
                String roomId = req.get("roomId").getAsString();
                String playerId = req.get("playerId").getAsString();
                int score = req.get("score").getAsInt();
                boolean isDead = req.has("isDead") && req.get("isDead").getAsBoolean();
                
                BattleRoom room = rooms.get(roomId);
                if (room == null) {
                    sendResponse(exchange, 404, "{\"error\":\"房间不存在\"}");
                    return;
                }
                
                if (playerId.equals(room.player1Id)) {
                    room.player1Score = score;
                    room.player1Dead = isDead;
                } else if (playerId.equals(room.player2Id)) {
                    room.player2Score = score;
                    room.player2Dead = isDead;
                } else {
                    sendResponse(exchange, 403, "{\"error\":\"无效的玩家ID\"}");
                    return;
                }
                
                room.lastUpdateTime = System.currentTimeMillis();
                
                if (room.player1Dead && room.player2Dead && !"finished".equals(room.status)) {
                    room.status = "finished";
                    room.endTime = System.currentTimeMillis();
                    System.out.println("[对战结束] " + roomId + 
                        " | P1: " + room.player1Score + " vs P2: " + room.player2Score);
                }
                
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                
                if (playerId.equals(room.player1Id)) {
                    response.addProperty("opponentScore", room.player2Score);
                    response.addProperty("opponentDead", room.player2Dead);
                } else {
                    response.addProperty("opponentScore", room.player1Score);
                    response.addProperty("opponentDead", room.player1Dead);
                }
                
                response.addProperty("battleStatus", room.status);
                
                sendResponse(exchange, 200, gson.toJson(response));
                
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }
    
    static class GetStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            
            try {
                String query = exchange.getRequestURI().getQuery();
                String roomId = getQueryParam(query, "roomId");
                String playerId = getQueryParam(query, "playerId");
                
                BattleRoom room = rooms.get(roomId);
                if (room == null) {
                    sendResponse(exchange, 404, "{\"error\":\"房间不存在\"}");
                    return;
                }
                
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("status", room.status);
                response.addProperty("player1Name", room.player1Name);
                response.addProperty("player2Name", room.player2Name != null ? room.player2Name : "等待中...");
                response.addProperty("hasPlayer2", room.player2Id != null);
                
                if (playerId != null && playerId.equals(room.player1Id)) {
                    response.addProperty("myScore", room.player1Score);
                    response.addProperty("opponentScore", room.player2Score);
                    response.addProperty("myDead", room.player1Dead);
                    response.addProperty("opponentDead", room.player2Dead);
                } else if (playerId != null && playerId.equals(room.player2Id)) {
                    response.addProperty("myScore", room.player2Score);
                    response.addProperty("opponentScore", room.player1Score);
                    response.addProperty("myDead", room.player2Dead);
                    response.addProperty("opponentDead", room.player1Dead);
                }
                
                sendResponse(exchange, 200, gson.toJson(response));
                
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }
    
    static class EndBattleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            
            try {
                String body = readRequestBody(exchange);
                JsonObject req = gson.fromJson(body, JsonObject.class);
                String roomId = req.get("roomId").getAsString();
                
                BattleRoom room = rooms.remove(roomId);
                if (room != null) {
                    System.out.println("[房间关闭] " + roomId);
                }
                
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "对战已结束");
                
                sendResponse(exchange, 200, gson.toJson(response));
                
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }
    
    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        byte[] bytes = is.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }
    
    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
    
    private static String getQueryParam(String query, String key) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && pair[0].equals(key)) {
                return pair[1];
            }
        }
        return null;
    }
    
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
