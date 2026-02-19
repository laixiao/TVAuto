package xyz.mulin.tvauto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * 开机自启动广播接收器
 * 监听 BOOT_COMPLETED 广播，根据用户设置决定是否自动启动 APP
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("TVAuto_Settings", Context.MODE_PRIVATE);
            boolean autoStart = prefs.getBoolean("auto_start_on_boot", false);
            if (autoStart) {
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launchIntent);
                }
            }
        }
    }
}
