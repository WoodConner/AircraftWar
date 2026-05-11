package edu.hitsz.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;

import edu.hitsz.network.LanDiscovery;

import java.util.List;

/**
 * 局域网服务器扫描对话框
 */
public class ServerScanDialog extends Dialog {
    
    private LinearLayout serverList;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private Button btnScan, btnClose;
    private Handler handler = new Handler(Looper.getMainLooper());
    private OnServerSelectedListener listener;
    
    public interface OnServerSelectedListener {
        void onServerSelected(String ip, int port);
    }
    
    public ServerScanDialog(@NonNull Context context, OnServerSelectedListener listener) {
        super(context);
        this.listener = listener;
        setContentView(buildUI());
        setCancelable(true);
    }
    
    private View buildUI() {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setLayoutParams(new ViewGroup.LayoutParams(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT));
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1A2A3A);
        bg.setCornerRadius(dp(12));
        root.setBackground(bg);
        
        // 标题
        TextView title = new TextView(getContext());
        title.setText("🔍 扫描局域网服务器");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTextColor(0xFFFFD700);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, mw());
        
        vsp(root, 16);
        
        // 状态文本
        tvStatus = new TextView(getContext());
        tvStatus.setText("点击扫描按钮开始搜索");
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvStatus.setTextColor(0xFFCCCCCC);
        tvStatus.setGravity(Gravity.CENTER);
        root.addView(tvStatus, mw());
        
        vsp(root, 12);
        
        // 进度条
        progressBar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));
        
        vsp(root, 16);
        
        // 服务器列表
        ScrollView scrollView = new ScrollView(getContext());
        serverList = new LinearLayout(getContext());
        serverList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(serverList);
        root.addView(scrollView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(200)));
        
        vsp(root, 16);
        
        // 按钮行
        LinearLayout buttonRow = new LinearLayout(getContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        
        btnScan = createButton("扫描", 0xFF2979FF);
        btnScan.setOnClickListener(v -> startScan());
        buttonRow.addView(btnScan, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        
        hsp(buttonRow, 12);
        
        btnClose = createButton("关闭", 0xFF666666);
        btnClose.setOnClickListener(v -> dismiss());
        buttonRow.addView(btnClose, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        
        root.addView(buttonRow, mw());
        
        return root;
    }
    
    private void startScan() {
        btnScan.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("正在扫描局域网...");
        serverList.removeAllViews();
        
        new Thread(() -> {
            List<LanDiscovery.ServerInfo> servers = LanDiscovery.scanServers(3000);
            
            handler.post(() -> {
                progressBar.setVisibility(View.GONE);
                btnScan.setEnabled(true);
                
                if (servers.isEmpty()) {
                    tvStatus.setText("未发现服务器");
                    TextView emptyView = new TextView(getContext());
                    emptyView.setText("💡 提示：\n1. 确保有设备启动了服务器\n2. 确保在同一局域网内");
                    emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    emptyView.setTextColor(0xFF999999);
                    emptyView.setGravity(Gravity.CENTER);
                    emptyView.setPadding(dp(16), dp(16), dp(16), dp(16));
                    serverList.addView(emptyView);
                } else {
                    tvStatus.setText("发现 " + servers.size() + " 个服务器");
                    for (LanDiscovery.ServerInfo server : servers) {
                        serverList.addView(createServerItem(server));
                    }
                }
            });
        }).start();
    }
    
    private View createServerItem(LanDiscovery.ServerInfo server) {
        LinearLayout item = new LinearLayout(getContext());
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(12), dp(12), dp(12), dp(12));
        item.setClickable(true);
        item.setFocusable(true);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2A3A4A);
        bg.setCornerRadius(dp(8));
        item.setBackground(bg);
        
        item.setOnClickListener(v -> {
            if (listener != null) {
                listener.onServerSelected(server.ip, server.port);
            }
            dismiss();
        });
        
        TextView tvName = new TextView(getContext());
        tvName.setText("📡 " + server.serverName);
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        item.addView(tvName);
        
        TextView tvInfo = new TextView(getContext());
        tvInfo.setText(server.ip + ":" + server.port + " | " + server.roomCount + " 个房间");
        tvInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvInfo.setTextColor(0xFFCCCCCC);
        item.addView(tvInfo);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        item.setLayoutParams(lp);
        
        return item;
    }
    
    private Button createButton(String text, int color) {
        Button btn = new Button(getContext());
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(8));
        btn.setBackground(bg);
        btn.setPadding(0, dp(10), 0, dp(10));
        
        return btn;
    }
    
    private LinearLayout.LayoutParams mw() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    
    private void vsp(ViewGroup parent, int dpVal) {
        View v = new View(getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(dpVal)));
        parent.addView(v);
    }
    
    private void hsp(ViewGroup parent, int dpVal) {
        View v = new View(getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(dpVal), 1));
        parent.addView(v);
    }
    
    private int dp(int v) {
        return (int)(v * getContext().getResources().getDisplayMetrics().density);
    }
}
