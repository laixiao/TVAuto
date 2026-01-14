/***    
private void injectVideoResizeJs(WebView view) {
        String c = channels[currentChannelIndex];
        if (!c.startsWith("file:///") && !c.startsWith("https://test.ustc.edu.cn/")) {
            String encodedJs ="全屏播放的JS注入代码Base64编码"; 
            try {
                byte[] decodedBytes = Base64.decode(encodedJs, Base64.DEFAULT);
                String jsCode = new String(decodedBytes);
                view.evaluateJavascript(jsCode, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
***/
package xyz.mulin.tvauto;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TVAuto 主活动类
 * 功能：电视直播源播放器，支持 Web 源、自定义频道管理、遥控器交互及触摸手势。
 */
public class MainActivity extends AppCompatActivity {

    // =============================================================================================
    // 1. 变量声明区域
    // =============================================================================================

    // --- UI 组件 ---
    private DrawerLayout drawerLayout;      // 侧边栏容器
    private WebView webView;                // 核心播放器容器
    private View touchLayer;                // 覆盖在WebView上的透明触控层（用于手势）
    private RecyclerView rvChannels;        // 侧边栏频道列表
    private LinearLayout btnSettings;       // 侧边栏顶部的“频道管理”按钮
    private TextView tvOsd;                 // 屏幕左上角的 OSD (On-Screen Display) 提示

    // --- 数据与存储 ---
    private SharedPreferences configPrefs;  // 保存配置（如上次播放位置）
    private SharedPreferences programPrefs; // 保存用户自定义频道数据
    private final LinkedHashMap<String, String> channelsMap = new LinkedHashMap<>(); // 内存中的频道数据 (URL -> Name)
    private String[] channels;              // 频道 URL 数组（用于通过索引快速访问）
    private int currentChannelIndex = 0;    // 当前播放的频道索引

    // --- 逻辑工具 ---
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ChannelAdapter adapter;         // 列表适配器
    private GestureDetector gestureDetector;// 手势识别器

    // --- 防抖与延迟任务配置 ---
    private int pendingChannelIndex = -1;           // 待切换的频道索引（防抖用）
    private static final long AUTO_CLOSE_DELAY = 5000; // 侧边栏自动关闭时间 (ms)
    private static final long SWITCH_DELAY = 1000;     // 换台防抖延迟 (ms)

    // --- Runnable 任务定义 ---
    // 任务：自动关闭侧边栏
    private final Runnable autoCloseRunnable = () -> {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END);
        }
    };

    // 任务：隐藏 OSD 提示
    private final Runnable hideOsdRunnable = () -> {
        if (tvOsd != null) tvOsd.setVisibility(View.GONE);
    };

    // 任务：【核心防抖】确认切换频道 (延迟加载 WebView)
    private final Runnable confirmChannelSwitchRunnable = () -> {
        if (channels != null && pendingChannelIndex >= 0 && pendingChannelIndex < channels.length) {
            Log.d("ChannelSwitch", "Loading URL for index: " + pendingChannelIndex);
            webView.loadUrl(channels[pendingChannelIndex]);
        }
    };

    // 数字键选台缓存
    private int digitBuffer = -1;      // 缓存输入的数字
    private long lastDigitTime = 0;    // 上次按键时间
    // 任务：确认数字选台
    private final Runnable digitConfirmRunnable = this::confirmDigitInput;


    // =============================================================================================
    // 2. 生命周期 (Lifecycle)
    // =============================================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 强制横屏 & 深色模式
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        enableImmersiveMode(); // 开启沉浸式全屏
        setContentView(R.layout.activity_main);

        // 初始化存储
        configPrefs = getSharedPreferences("TVAuto_Config", MODE_PRIVATE);
        programPrefs = getSharedPreferences("TVAuto_Program", Context.MODE_PRIVATE);

        // 加载数据与恢复状态
        loadUserChannels();
        currentChannelIndex = configPrefs.getInt("lastChannel", 0);
        // 索引越界保护
        if (channels != null && channels.length > 0) {
            if (currentChannelIndex >= channels.length) currentChannelIndex = 0;
        }

        // 初始化各模块
        initViews();
        setupWebView();
        setupGestures();

        // 首次加载直接播放，无需防抖
        if (channels != null && channels.length > 0) {
            loadChannelDirectly(currentChannelIndex);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // 确保应用失去焦点再回来时（如弹窗关闭后），依然保持沉浸全屏
        if (hasFocus) enableImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理所有未执行的 Handler 任务，防止内存泄漏
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * 开启沉浸模式（隐藏状态栏、导航栏）
     */
    @SuppressWarnings("deprecation")
    private void enableImmersiveMode() {
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        getWindow().getDecorView().setSystemUiVisibility(uiOptions);
    }


    // =============================================================================================
    // 3. 初始化与视图配置 (Initialization)
    // =============================================================================================

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        drawerLayout.setScrimColor(Color.TRANSPARENT); // 【视觉优化】去掉侧边栏打开时的阴影遮罩

        webView = findViewById(R.id.webView);
        touchLayer = findViewById(R.id.touchLayer);
        rvChannels = findViewById(R.id.rvChannels);
        btnSettings = findViewById(R.id.btnSettings);
        tvOsd = findViewById(R.id.tvOsd);

        // 配置列表
        rvChannels.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChannelAdapter();
        rvChannels.setAdapter(adapter);

        // 1. 设置按钮点击事件
        btnSettings.setOnClickListener(v -> {
            resetAutoTimer();
            drawerLayout.closeDrawer(GravityCompat.END);
            manageTvChannels();
        });
        rvChannels.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    // 手指按在屏幕上时，移除自动关闭任务，保持常亮
                    handler.removeCallbacks(autoCloseRunnable);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // 手指离开屏幕后，重新开始 5 秒倒计时
                    resetAutoTimer();
                    break;
            }
            // 返回 false，表示我不拦截事件，继续交给 RecyclerView 去处理滑动
            return false;
        });
        // 2. 设置按钮焦点样式监听
        // 解决按钮选中时文字看不清的问题：选中时变黑底白字并放大，未选中时恢复透明
        btnSettings.setOnFocusChangeListener((v, hasFocus) -> {
            // 根据XML结构获取子控件：Child 1=Badge("设置"), Child 2=Title("频道管理")
            TextView tvBadge = (TextView) btnSettings.getChildAt(1);
            TextView tvTitle = (TextView) btnSettings.getChildAt(2);

            if (hasFocus) {
                tvTitle.setTextColor(Color.BLACK);
                tvBadge.setTextColor(Color.DKGRAY);
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).start();
                v.setBackgroundResource(R.drawable.selector_channel_card);
            } else {
                tvTitle.setTextColor(Color.WHITE);
                tvBadge.setTextColor(Color.parseColor("#AAFFFFFF"));
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }
        });

        // 3. 抽屉状态监听
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View d) {
                resetAutoTimer();
            } // 打开时重置计时

            @Override
            public void onDrawerClosed(View d) {
                handler.removeCallbacks(autoCloseRunnable);
            } // 关闭时取消计时
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setDisplayZoomControls(false);
        settings.setBuiltInZoomControls(false);
        settings.setSupportZoom(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36");

        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                injectVideoResizeJs(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript("window.__VIDEO_RESIZE_INJECTED__", value -> {
                    if ("true".equals(value)) {
                        Log.d("TJS", "onPageStarted 阶段注入成功");
                    } else {
                        Log.d("TJS", "onPageStarted 阶段注入失败，onPageFinished 二次注入");
                        injectVideoResizeJs(view);
                    }
                });
            }
        });
    }
    // 注入 JavaScript  (全屏)
    private void injectVideoResizeJs(WebView view) {
        String c = channels[currentChannelIndex];
        if (!c.startsWith("file:///") && !c.startsWith("https://test.ustc.edu.cn/")) {
            String encodedJs ="全屏播放的JS注入代码Base64编码";
            try {
                byte[] decodedBytes = Base64.decode(encodedJs, Base64.DEFAULT);
                String jsCode = new String(decodedBytes);
                view.evaluateJavascript(jsCode, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                openSidebar(); // 单击呼出菜单
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                // 判定为垂直滑动
                if (Math.abs(diffY) > Math.abs(diffX)) {
                    if (Math.abs(diffY) > 100 && Math.abs(velocityY) > 100) {
                        if (diffY > 0) switchToPrevChannel(); // 下滑 -> 上一台
                        else switchToNextChannel();           // 上滑 -> 下一台
                        return true;
                    }
                }
                return false;
            }
        });
        // 将触摸层的事件委托给手势识别器
        touchLayer.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }


    // =============================================================================================
    // 4. 输入控制与按键逻辑 (Input Controller)
    // =============================================================================================
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (!drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    Log.d("KeyDebug", "dispatchKeyEvent: 强行拦截 OK 键，呼出侧边栏");
                    openSidebar();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }
    /**
     * 处理物理按键事件
     * 实现了【双模兼容】：同时支持 电视遥控器按键 和 电脑键盘映射
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d("keyCode",":"+keyCode);
        switch (keyCode) {
            // 数字键选台
            case KeyEvent.KEYCODE_0:
            case KeyEvent.KEYCODE_1:
            case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_3:
            case KeyEvent.KEYCODE_4:
            case KeyEvent.KEYCODE_5:
            case KeyEvent.KEYCODE_6:
            case KeyEvent.KEYCODE_7:
            case KeyEvent.KEYCODE_8:
            case KeyEvent.KEYCODE_9:
                handleDigitInput(keyCode - KeyEvent.KEYCODE_0);
                return true;
        }
        // 场景 A: 侧边栏已打开 (Drawer Opened)
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            resetAutoTimer(); // 操作重置倒计时
            switch (keyCode) {
                // 1. 关闭菜单 (返回/左/A)
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_A:
                    drawerLayout.closeDrawer(GravityCompat.END);
                    return true;

                // 2. 聚焦设置按钮 (菜单/M)
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_M:
                    btnSettings.requestFocus();
                    return true;

                // 3. 确认/点击 (OK/空格/回车)
                // 兼容逻辑：强制触发当前焦点的点击事件
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    View focus = getCurrentFocus();
                    if (focus != null) focus.performClick();
                    return true;

                // 4. 向上移动 (上/W)
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_W:
                    // 循环逻辑：在设置按钮按上 -> 跳到列表底部
                    if (btnSettings.hasFocus()) {
                        int max = adapter.getItemCount() - 1;
                        if (max >= 0) jumpToPosition(max);
                        return true;
                    }
                    simulateFocusMove(View.FOCUS_UP);
                    return true;

                // 5. 向下移动 (下/S)
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_S:
                    // 在设置按钮按下 -> 强制跳到列表顶部(0)
                    // 防止系统 FocusSearch 错误地跳到屏幕中间可见的 Item
                    if (btnSettings.hasFocus()) {
                        jumpToPosition(0);
                        return true;
                    }
                    simulateFocusMove(View.FOCUS_DOWN);
                    return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        // 场景 B: 全屏播放时 (Fullscreen Player)
        switch (keyCode) {
            // 呼出菜单 (菜单/M)
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_M:
                manageTvChannels();
                return true;

            // 呼出侧边栏 (OK/右/D/空格/回车)
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                openSidebar();
                return true;

            // 上一台 (上/W)
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
                switchToPrevChannel();
                return true;

            // 下一台 (下/S)
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_S:
                switchToNextChannel();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 辅助方法：手动控制焦点移动
     * 作用：解决 W/S 等键盘映射键无法被系统 View 识别为方向键的问题
     */
    private void simulateFocusMove(int direction) {
        View currentFocus = getCurrentFocus();
        if (currentFocus == null) return;
        View nextFocus = currentFocus.focusSearch(direction);
        if (nextFocus != null) nextFocus.requestFocus();
    }


    // =============================================================================================
    // 5. 频道切换与播放逻辑 (Channel Player Logic)
    // =============================================================================================

    // 切换到上一台
    private void switchToPrevChannel() {
        if (channels == null || channels.length == 0) return;
        // 计算索引（防止负数）
        currentChannelIndex = (currentChannelIndex - 1 + channels.length) % channels.length;
        loadChannelWithThrottling(currentChannelIndex); // 使用防抖加载
        saveChannelIndex();
    }

    // 切换到下一台
    private void switchToNextChannel() {
        if (channels == null || channels.length == 0) return;
        currentChannelIndex = (currentChannelIndex + 1) % channels.length;
        loadChannelWithThrottling(currentChannelIndex); // 使用防抖加载
        saveChannelIndex();
    }

    // 【核心功能】带防抖机制的频道加载
    // 作用：快速换台时只更新 OSD 文字，停止按键后再加载视频，防止卡顿
    private void loadChannelWithThrottling(int index) {
        if (channels == null || channels.length == 0) return;

        // 1. 立即更新 OSD (UI反馈必须快)
        String name = channelsMap.get(channels[index]);
        tvOsd.setText((index + 1) + "  " + name);
        tvOsd.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOsdRunnable);
        handler.postDelayed(hideOsdRunnable, 3000);

        // 2. 更新待加载索引
        pendingChannelIndex = index;

        // 3. 重置并延迟执行 WebView 加载
        handler.removeCallbacks(confirmChannelSwitchRunnable);
        handler.postDelayed(confirmChannelSwitchRunnable, SWITCH_DELAY);

        Log.d("ChannelSwitch", "Scheduled channel: " + index);
    }

    // 直接加载频道 (用于列表点击、APP启动时)
    private void loadChannelDirectly(int index) {
        if (channels == null || channels.length == 0) return;

        // 立即更新 OSD
        String name = channelsMap.get(channels[index]);
        tvOsd.setText((index + 1) + "  " + name);
        tvOsd.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOsdRunnable);
        handler.postDelayed(hideOsdRunnable, 3000);

        // 立即加载网页
        webView.loadUrl(channels[index]);
    }

    // 保存当前频道索引到 SharedPreferences
    private void saveChannelIndex() {
        configPrefs.edit().putInt("lastChannel", currentChannelIndex).apply();
    }

    // 数字键输入处理 (带超时判断)
    private void handleDigitInput(int d) {
        long now = System.currentTimeMillis();
        // 如果间隔超过1秒，视为新输入，否则追加数字
        if (now - lastDigitTime > 1000) digitBuffer = d;
        else digitBuffer = digitBuffer * 10 + d;

        lastDigitTime = now;

        if (digitBuffer > 0) {
            tvOsd.setText(digitBuffer + " ");
            tvOsd.setVisibility(View.VISIBLE);
            handler.removeCallbacks(hideOsdRunnable);
            handler.postDelayed(hideOsdRunnable, 3000);
        }
        // 1秒后确认输入
        handler.removeCallbacks(digitConfirmRunnable);
        handler.postDelayed(digitConfirmRunnable, 1000);
    }

    // 确认数字跳转
    private void confirmDigitInput() {
        int idx = digitBuffer - 1;
        if (channels != null && idx >= 0 && idx < channels.length) {
            currentChannelIndex = idx;
            loadChannelWithThrottling(idx);
            saveChannelIndex();
        } else {
            tvOsd.setText("无此频道");
        }
        digitBuffer = -1;
    }


    // =============================================================================================
    // 6. 侧边栏与 UI 交互逻辑 (Sidebar UI Logic)
    // =============================================================================================

    private void openSidebar() {
        adapter.notifyDataSetChanged();
        drawerLayout.openDrawer(GravityCompat.END);
        rvChannels.post(() -> {
            LinearLayoutManager layoutManager = (LinearLayoutManager) rvChannels.getLayoutManager();
            if (layoutManager != null) {
                int listHeight = rvChannels.getHeight();
                int itemHeight = 0;
                View firstChild = rvChannels.getChildAt(0);
                if (firstChild != null) {
                    itemHeight = firstChild.getHeight();
                } else {
                    itemHeight = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 60, getResources().getDisplayMetrics());
                }
                int offset = (listHeight / 2) - (itemHeight / 2);
                layoutManager.scrollToPositionWithOffset(currentChannelIndex, offset);
            }
        });
        // 延时聚焦保持不变（等待抽屉动画和滚动完成）
        rvChannels.postDelayed(() -> {
            rvChannels.requestFocus();
            RecyclerView.ViewHolder holder = rvChannels.findViewHolderForAdapterPosition(currentChannelIndex);
            if (holder != null) holder.itemView.requestFocus();
        }, 200);
    }

    // 列表滚动辅助方法 (带越界修正)
    private void jumpToPosition(int index) {
        if (channels == null || channels.length == 0) return;
        if (index < 0) index = 0;
        if (index >= channels.length) index = channels.length - 1;

        rvChannels.scrollToPosition(index);
        int finalIndex = index;
        // 延时聚焦，确保 RecyclerView 滚动到位
        rvChannels.postDelayed(() -> {
            RecyclerView.ViewHolder holder = rvChannels.findViewHolderForAdapterPosition(finalIndex);
            if (holder != null) holder.itemView.requestFocus();
            else rvChannels.requestFocus();
        }, 50);
    }

    // 重置侧边栏自动关闭计时器
    private void resetAutoTimer() {
        handler.removeCallbacks(autoCloseRunnable);
        handler.postDelayed(autoCloseRunnable, AUTO_CLOSE_DELAY);
    }


    // =============================================================================================
    // 7. 数据管理与弹窗 (Data Management)
    // =============================================================================================

    // 从 Prefs 加载用户频道数据 (JSON)
    private void loadUserChannels() {
        channelsMap.clear();
        String json = programPrefs.getString("user_channels", "[]");
        try {
            JSONArray array = new JSONArray(json);
            if (array.length() == 0) {
                // 默认数据
                channelsMap.put("file:///android_asset/add_channel_help.html", "频道添加指南");
            } else {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    channelsMap.put(obj.getString("url"), obj.getString("name"));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        channels = channelsMap.keySet().toArray(new String[0]);
    }

    // ===============================
// 频道管理弹窗
// ===============================
    @SuppressLint("ClickableViewAccessibility")
    private void manageTvChannels() {

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog);
        builder.setTitle("频道管理");

        ScrollView scrollView = new ScrollView(this);
        int bgSemiTransparent = Color.parseColor("#B3000000");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 32, 48, 32);
        root.setGravity(Gravity.START);
        scrollView.addView(root);

        int textColor = Color.parseColor("#FFFFFF");
        int hintColor = Color.parseColor("#777777");
        int inputBgColor = Color.parseColor("#22FFFFFF");

        // ===============================
        // 1. 单条添加（输入 + 右侧按钮）
        // ===============================
        EditText name = createCompactEditText("频道名称", textColor, hintColor, inputBgColor);
        MaterialButton addBtn = createCompactButton("添加", "#006CE0");

        root.addView(createInputActionRow(name, addBtn));

        EditText url = createCompactEditText("直播 URL", textColor, hintColor, inputBgColor);
        root.addView(url);

        addStrongDivider(root);

        // ===============================
        // 2. 批量导入（多行输入 + 右侧按钮）
        // ===============================
        EditText batch = createCompactEditText(
                "批量导入: \"名称\",\"URL\"; ...\n留空导入默认频道",
                textColor, hintColor, inputBgColor
        );
        batch.setMinLines(2);
        batch.setGravity(Gravity.TOP | Gravity.START);

        // 限制最大高度，并启用内部滚动
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.2f);
        batch.setMaxHeight(maxHeight);
        batch.setVerticalScrollBarEnabled(true);
        batch.setMovementMethod(new ScrollingMovementMethod());
        batch.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);

        // 滚动事件处理，避免 ScrollView 拦截
        batch.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });

        MaterialButton importBtn = createCompactButton("导入", "#006CE0");

        root.addView(createInputActionRow(batch, importBtn));

        addStrongDivider(root);

        // ===============================
        // 3. 管理操作（并排）
        // ===============================
        MaterialButton deleteBtn = createCompactButton("删除当前", "#7A3333");
        MaterialButton updateBtn = createCompactButton("更新检测", "#5C6F82");

        root.addView(createButtonPairRow(deleteBtn, updateBtn));

        // ===============================
        // 4. 关于信息
        // ===============================
        TextView info = new TextView(this);
        info.setText(R.string.tvauto_v);
        info.setTextColor(Color.LTGRAY);
        info.setPadding(8, 24, 0, 0);
        root.addView(info);

        builder.setView(scrollView);
        AlertDialog dialog = builder.create();
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(20);
            bg.setColor(bgSemiTransparent);
            scrollView.setBackground(bg);

            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.dimAmount = 0.3f;
            dialog.getWindow().setAttributes(lp);

            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.6f);
            dialog.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        // ===============================
        // 事件绑定
        // ===============================
        addBtn.setOnClickListener(v -> addOneChannel(name, url));
        importBtn.setOnClickListener(v -> addAllChannels(batch, 1));
        deleteBtn.setOnClickListener(v -> deleteCurrentChannel());
        updateBtn.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://pan.baidu.com/s/1ma_jq-9wbR4IQ5_lQO_Eng?pwd=5555"));
            startActivity(i);
        });
    }


// =================================================
// UI 组件方法（最终定版）
// =================================================

    private MaterialButton createCompactButton(String text, String colorHex) {
        MaterialButton btn = new MaterialButton(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setCornerRadius(14);
        btn.setGravity(Gravity.CENTER);
        btn.setAlpha(0.83f);
        btn.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor(colorHex))
        );
        return btn;
    }

    private EditText createCompactEditText(String hint, int textColor, int hintColor, int bgColor) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(hintColor);
        et.setTextColor(textColor);
        et.setPadding(32, 24, 32, 24);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(14);
        et.setBackground(bg);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        lp.setMargins(0, 6, 0, 12);
        et.setLayoutParams(lp);

        return et;
    }

    // 输入框 + 右侧操作按钮
    private LinearLayout createInputActionRow(EditText et, MaterialButton btn) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams etLp =
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        etLp.setMargins(0, 6, 12, 12);
        et.setLayoutParams(etLp);

        int heightDp = 48; // 按钮高度
        float scale = getResources().getDisplayMetrics().density;
        int heightPx = (int) (heightDp * scale + 0.5f);

        LinearLayout.LayoutParams btnLp =
                new LinearLayout.LayoutParams(120+heightPx, heightPx);
        btn.setLayoutParams(btnLp);

        row.addView(et);
        row.addView(btn);
        return row;
    }

    // 并排按钮行
    private LinearLayout createButtonPairRow(MaterialButton left, MaterialButton right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int heightDp = 48; // 按钮高度
        float scale = getResources().getDisplayMetrics().density;
        int heightPx = (int) (heightDp * scale + 0.5f);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, heightPx, 1f);
        lp.setMargins(6, 6, 6, 6);

        left.setLayoutParams(lp);
        right.setLayoutParams(lp);

        row.addView(left);
        row.addView(right);
        return row;
    }

    private void addStrongDivider(LinearLayout parent) {
        View v = new View(this);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 3);
        lp.setMargins(0, 24, 0, 32);
        v.setLayoutParams(lp);
        v.setBackgroundColor(Color.parseColor("#444444"));
        parent.addView(v);
    }
    // 添加单个频道
    private void addOneChannel(EditText n, EditText u) {
        String name = n.getText().toString().trim();
        String url = u.getText().toString().trim();
        if (!name.isEmpty() && !url.isEmpty()) {
            if (channelsMap.containsKey(url)) showToast("频道已存在");
            else {
                saveUserChannel(url, name);
                showToast("已添加");
                restartApp();
            }
        } else showToast("信息不完整");
    }

    // 批量导入频道
    private void addAllChannels(EditText inputAll, int mode) {
        String input = inputAll.getText().toString().trim();
        if (input.isEmpty()) {
            inputAll.setText(R.string.DefaultChannel);
            showToast("已填入默认频道，请再次点击导入");
            return;
        }
        String[] entries = input.split(";");
        Pattern p = Pattern.compile("^\\s*\"(.*?)\"\\s*,\\s*\"(.*?)\"\\s*$");
        int count = 0;
        for (String e : entries) {
            Matcher m = p.matcher(e);
            if (m.matches()) {
                String url = m.group(2).trim();
                if (!channelsMap.containsKey(url)) {
                    saveUserChannel(url, m.group(1).trim());
                    channelsMap.put(url, m.group(1).trim());
                    count++;
                }
            }
        }
        if (count > 0) {
            showToast("导入 " + count + " 个频道");
            restartApp();
        } else showToast("无有效新频道");
    }

    // 保存数据到 SharedPrefs
    private void saveUserChannel(String u, String n) {
        try {
            JSONArray a = new JSONArray(programPrefs.getString("user_channels", "[]"));
            JSONObject o = new JSONObject();
            o.put("name", n);
            o.put("url", u);
            a.put(o);
            programPrefs.edit().putString("user_channels", a.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 删除当前播放的频道
    private void deleteCurrentChannel() {
        if (channels == null || channels.length == 0) return;
        String c = channels[currentChannelIndex];
        if (c.startsWith("file:///")) {
            showToast("内置无法删除");
            return;
        }
        try {
            JSONArray a = new JSONArray(programPrefs.getString("user_channels", "[]"));
            JSONArray b = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                if (!a.getJSONObject(i).getString("url").equals(c)) b.put(a.getJSONObject(i));
            }
            programPrefs.edit().putString("user_channels", b.toString()).apply();
            showToast("已删除");
            restartApp();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // =============================================================================================
    // 8. 辅助工具与适配器 (Helpers & Adapter)
    // =============================================================================================

    private void restartApp() {
        startActivity(getPackageManager().getLaunchIntentForPackage(getPackageName()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private void showToast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }



    // 内部类：频道列表适配器
    private class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            if (channels == null || position >= channels.length) return;

            // 设置 UI 内容
            holder.tvNum.setText(String.valueOf(position + 1));
            holder.tvName.setText(channelsMap.get(channels[position]));

            // 播放指示器 (小绿点)
            boolean isPlaying = (position == currentChannelIndex);
            holder.indicator.setVisibility(isPlaying ? View.VISIBLE : View.GONE);

            updateItemStyle(holder, false);

            // 点击事件
            holder.itemView.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    resetAutoTimer();
                    currentChannelIndex = pos;
                    loadChannelDirectly(pos); // 点击列表直接加载，无需防抖
                    saveChannelIndex();
                    drawerLayout.closeDrawer(GravityCompat.END);
                }
            });

            // 焦点变化监听 (卡片放大/变色)
            holder.itemView.setOnFocusChangeListener((v, hasFocus) -> updateItemStyle(holder, hasFocus));

            // 按键监听 (实现列表首尾与设置按钮的循环导航)
            holder.itemView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        // 1. 首项按上/W -> 跳到设置按钮
                        if (pos == 0 && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_W)) {
                            btnSettings.requestFocus();
                            return true;
                        }
                        // 2. 末项按下/S -> 跳到设置按钮
                        if (pos == adapter.getItemCount() - 1 && (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_S)) {
                            btnSettings.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });
        }

        // 更新卡片样式 (选中/播放/普通)
        private void updateItemStyle(Holder holder, boolean hasFocus) {
            if (hasFocus) {
                // 获得焦点：文字黑、深灰，卡片放大
                holder.tvName.setTextColor(Color.BLACK);
                holder.tvNum.setTextColor(Color.DKGRAY);
                holder.itemView.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).start();
            } else {
                // 失去焦点：根据是否播放显示绿色或白色
                int pos = holder.getAdapterPosition();
                boolean isPlaying = (pos == currentChannelIndex);
                holder.tvName.setTextColor(isPlaying ? Color.parseColor("#0079FB") : Color.WHITE);
                holder.tvNum.setTextColor(Color.parseColor("#88FFFFFF"));
                holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }
        }

        @Override
        public int getItemCount() {
            return channels == null ? 0 : channels.length;
        }

        class Holder extends RecyclerView.ViewHolder {
            TextView tvNum, tvName;
            View indicator;

            public Holder(View itemView) {
                super(itemView);
                tvNum = itemView.findViewById(R.id.tvNum);
                tvName = itemView.findViewById(R.id.tvName);
                indicator = itemView.findViewById(R.id.indicator);
            }
        }
    }
}