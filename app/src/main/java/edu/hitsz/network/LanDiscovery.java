package edu.hitsz.network;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * 局域网服务器发现
 * 使用UDP广播实现服务器自动发现
 */
public class LanDiscovery {
    
    private static final String TAG = "LanDiscovery";
    private static final int DISCOVERY_PORT = 8888; // 发现端口
    private static final String DISCOVERY_REQUEST = "AIRCRAFT_WAR_DISCOVER";
    private static final String DISCOVERY_RESPONSE_PREFIX = "AIRCRAFT_WAR_SERVER:";
    
    /**
     * 服务器信息
     */
    public static class ServerInfo {
        public String ip;
        public int port;
        public String serverName;
        public int roomCount;
        
        public ServerInfo(String ip, int port, String serverName, int roomCount) {
            this.ip = ip;
            this.port = port;
            this.serverName = serverName;
            this.roomCount = roomCount;
        }
        
        @Override
        public String toString() {
            return serverName + " (" + ip + ":" + port + ") - " + roomCount + "个房间";
        }
    }
    
    /**
     * 扫描局域网内的服务器
     * @param timeoutMs 超时时间（毫秒）
     * @return 发现的服务器列表
     */
    public static List<ServerInfo> scanServers(int timeoutMs) {
        List<ServerInfo> servers = new ArrayList<>();
        DatagramSocket socket = null;
        
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(timeoutMs);
            
            // 发送广播请求
            byte[] requestData = DISCOVERY_REQUEST.getBytes();
            InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
            DatagramPacket requestPacket = new DatagramPacket(
                requestData, requestData.length, broadcastAddress, DISCOVERY_PORT);
            
            Log.d(TAG, "发送广播请求...");
            socket.send(requestPacket);
            
            // 接收响应
            byte[] buffer = new byte[1024];
            long startTime = System.currentTimeMillis();
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(responsePacket);
                    
                    String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
                    Log.d(TAG, "收到响应: " + response);
                    
                    if (response.startsWith(DISCOVERY_RESPONSE_PREFIX)) {
                        ServerInfo server = parseServerInfo(response, responsePacket.getAddress().getHostAddress());
                        if (server != null) {
                            servers.add(server);
                            Log.d(TAG, "发现服务器: " + server);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // 超时，继续等待
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "扫描服务器失败", e);
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
        
        return servers;
    }
    
    /**
     * 解析服务器信息
     * 格式：AIRCRAFT_WAR_SERVER:port:serverName:roomCount
     */
    private static ServerInfo parseServerInfo(String response, String ip) {
        try {
            String data = response.substring(DISCOVERY_RESPONSE_PREFIX.length());
            String[] parts = data.split(":");
            
            if (parts.length >= 3) {
                int port = Integer.parseInt(parts[0]);
                String serverName = parts[1];
                int roomCount = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                
                return new ServerInfo(ip, port, serverName, roomCount);
            }
        } catch (Exception e) {
            Log.e(TAG, "解析服务器信息失败: " + response, e);
        }
        return null;
    }
    
    /**
     * 启动服务器广播（在服务器端调用）
     * @param port 服务器端口
     * @param serverName 服务器名称
     * @param roomCountProvider 房间数量提供者
     */
    public static Thread startServerBroadcast(int port, String serverName, RoomCountProvider roomCountProvider) {
        Thread broadcastThread = new Thread(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket(DISCOVERY_PORT);
                socket.setBroadcast(true);
                
                byte[] buffer = new byte[1024];
                Log.d(TAG, "服务器广播已启动，端口: " + DISCOVERY_PORT);
                
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        
                        String request = new String(packet.getData(), 0, packet.getLength());
                        
                        if (DISCOVERY_REQUEST.equals(request)) {
                            // 构建响应
                            int roomCount = roomCountProvider != null ? roomCountProvider.getRoomCount() : 0;
                            String response = DISCOVERY_RESPONSE_PREFIX + port + ":" + serverName + ":" + roomCount;
                            byte[] responseData = response.getBytes();
                            
                            DatagramPacket responsePacket = new DatagramPacket(
                                responseData, responseData.length,
                                packet.getAddress(), packet.getPort());
                            
                            socket.send(responsePacket);
                            Log.d(TAG, "发送响应到: " + packet.getAddress().getHostAddress());
                        }
                    } catch (SocketTimeoutException e) {
                        // 超时，继续监听
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "服务器广播失败", e);
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                Log.d(TAG, "服务器广播已停止");
            }
        });
        
        broadcastThread.setName("ServerBroadcast");
        broadcastThread.start();
        return broadcastThread;
    }
    
    /**
     * 房间数量提供者接口
     */
    public interface RoomCountProvider {
        int getRoomCount();
    }
}
