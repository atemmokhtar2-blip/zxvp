package com.sec.check;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "SecurityCheck";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent.getAction();
            Log.d(TAG, "BootReceiver: " + action);

            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                    || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {

                // ★ شغّل الخدمة
                Intent serviceIntent = new Intent(context, ServiceRunner.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }

                // ★ افتح MainActivity لو ناقص صلاحيات
                try {
                    Intent mainIntent = new Intent(context, MainActivity.class);
                    mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(mainIntent);
                } catch (Exception e) {
                    Log.e(TAG, "start MainActivity error: " + e.getMessage());
                }

                Log.d(TAG, "✅ Service started from boot");
            }
        } catch (Exception e) {
            Log.e(TAG, "BootReceiver error: " + e.getMessage());
        }
    }
}
