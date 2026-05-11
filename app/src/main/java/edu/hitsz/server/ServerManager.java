package edu.hitsz.server;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 服务器管理器 - 单例模式
 * 在应用启动时自动启动内置服务器
 */
public class ServerManager {
    
    private static final String TAG = "ServerManager";
    private static final String PREF_NAME = "ServerSettings";
    private static final String KEY_AUTO_START = "auto_start_server";
    
    private static ServerManager instance;
    private EmbeddedBattleServer server;
    private Context context;
    private boolean isServerRunning = false;
    
    private ServerManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public static synchronized ServerManager getInstance(Context context) {
        if (instance == null) {
            instance = new ServerManager(context);
        }
        return instance;
    }
    
    /**
     * 初始化并自动启动服务器（如果启用）
     */
    public void initialize() {
        if (isAutoStartEnabled()) {
            startServer();
        }
    }
    
    /**
     * 启动服务器（使用默认端口8080）
     */
    public void startServer() {
        startServer(8080);
    }
    
    /**
     * 启动服务器（指定端口）
     */
    public void startServer(int port) {
        // 如果服务器已运行且端口不同，先停止
        if (isServerRunning && server != null && server.getPort() != port) {
            Log.i(TAG, "端口变更，重启服务器: " + server.getPort() + " -> " + port);
            stopServer();
            // 等待服务器完全停止
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        if (isServerRunning) {
            Log.w(TAG, "服务器已在运行，端口: " + port);
            return;
        }
        
        server = new EmbeddedBattleServer(new EmbeddedBattleServer.ServerCallback() {
            @Override
            public void onServerStarted(int port) {
                isServerRunning = true;
                Log.i(TAG, "内置服务器已启动，端口: " + port);
            }
            
            @Override
            public void onServerStopped() {
                isServerRunning = false;
                Log.i(TAG, "内置服务器已停止");
            }
            
            @Override
            public void onError(String error) {
                isServerRunning = false;
                Log.e(TAG, "服务器错误: " + error);
            }
        });
        
        // 设置端口（必须在start之前）
        server.setPort(port);
        server.start();
        
        Log.i(TAG, "正在启动服务器，端口: " + port);
    }
    
    /**
     * 停止服务器
     */
    public void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
        isServerRunning = false;
    }
    
    /**
     * 检查服务器是否运行
     */
    public boolean isServerRunning() {
        return isServerRunning && server != null && server.isRunning();
    }
    
    /**
     * 设置是否自动启动服务器
     */
    public void setAutoStart(boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply();
    }
    
    /**
     * 检查是否启用自动启动
     */
    public boolean isAutoStartEnabled() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_AUTO_START, false); // 默认禁用，让用户手动启动
    }
    
    /**
     * 获取服务器地址（用于本地连接）
     */
    public String getServerAddress() {
        return "localhost"; // 或 "127.0.0.1"
    }
    
    /**
     * 获取服务器端口
     */
    public int getServerPort() {
        if (server != null) {
            return server.getPort();
        }
        return 8080; // 默认端口
    }
    
    /**
     * 获取服务器实例（用于高级配置）
     */
    public EmbeddedBattleServer getServer() {
        return server;
    }
}
