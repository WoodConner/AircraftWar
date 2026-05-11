package edu.hitsz;

import android.app.Application;
import android.util.Log;

import edu.hitsz.server.ServerManager;

/**
 * 应用程序类
 * 在应用启动时自动启动内置服务器
 */
public class AircraftWarApplication extends Application {
    
    private static final String TAG = "AircraftWarApp";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.i(TAG, "应用启动");
        
        // 初始化并自动启动内置服务器
        ServerManager serverManager = ServerManager.getInstance(this);
        serverManager.initialize();
        
        if (serverManager.isServerRunning()) {
            Log.i(TAG, "内置服务器已自动启动");
        }
    }
}
