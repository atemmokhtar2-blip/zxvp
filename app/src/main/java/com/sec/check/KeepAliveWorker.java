package com.sec.check;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class KeepAliveWorker extends Worker {

    private static final String TAG = "SecurityCheck";

    public KeepAliveWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "KeepAliveWorker: restarting service");
        try {
            Intent serviceIntent = new Intent(getApplicationContext(), ServiceRunner.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplicationContext().startForegroundService(serviceIntent);
            } else {
                getApplicationContext().startService(serviceIntent);
            }
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "KeepAliveWorker error: " + e.getMessage());
            return Result.retry();
        }
    }
}
