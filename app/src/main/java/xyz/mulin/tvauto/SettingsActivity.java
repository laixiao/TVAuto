package xyz.mulin.tvauto;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * 设置页面
 * 提供开机自启动等设置选项
 */
public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences settingsPrefs;
    private Switch switchAutoStart;
    private Switch switchDevConsole;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        enableImmersiveMode();
        setContentView(R.layout.activity_settings);

        settingsPrefs = getSharedPreferences("TVAuto_Settings", MODE_PRIVATE);

        initViews();
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private void initViews() {
        switchAutoStart = findViewById(R.id.switchAutoStart);
        LinearLayout rowAutoStart = findViewById(R.id.rowAutoStart);
        switchDevConsole = findViewById(R.id.switchDevConsole);
        LinearLayout rowDevConsole = findViewById(R.id.rowDevConsole);
        TextView btnBack = findViewById(R.id.btnBack);

        // 读取当前设置
        boolean autoStart = settingsPrefs.getBoolean("auto_start_on_boot", false);
        switchAutoStart.setChecked(autoStart);

        boolean devConsole = settingsPrefs.getBoolean("dev_console_enabled", false);
        switchDevConsole.setChecked(devConsole);

        // 点击整行切换开关
        rowAutoStart.setOnClickListener(v -> toggleAutoStart());
        rowDevConsole.setOnClickListener(v -> toggleDevConsole());

        // 返回按钮
        btnBack.setOnClickListener(v -> finish());

        // 返回按钮焦点样式
        btnBack.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                ((TextView) v).setTextColor(Color.BLACK);
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
            } else {
                ((TextView) v).setTextColor(Color.parseColor("#88FFFFFF"));
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }
        });

        // 设置行焦点样式
        rowAutoStart.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).start();
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }
        });

        rowDevConsole.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).start();
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }
        });
    }

    private void toggleAutoStart() {
        boolean newValue = !switchAutoStart.isChecked();
        switchAutoStart.setChecked(newValue);
        settingsPrefs.edit().putBoolean("auto_start_on_boot", newValue).apply();
    }

    private void toggleDevConsole() {
        boolean newValue = !switchDevConsole.isChecked();
        switchDevConsole.setChecked(newValue);
        settingsPrefs.edit().putBoolean("dev_console_enabled", newValue).apply();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        // 遥控器 OK 键在设置行上时切换开关
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            View focus = getCurrentFocus();
            if (focus != null) {
                focus.performClick();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableImmersiveMode();
    }

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
}
