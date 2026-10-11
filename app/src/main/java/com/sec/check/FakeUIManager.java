package com.sec.check;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * FakeUIManager - واجهة وهمية بأنيميشن حقيقي
 * ============================================================
 * ★ أنيميشن ValueAnimator + ObjectAnimator
 * ★ دوائر متحركة بتدور
 * ★ شريط تقدم حقيقي متحرك
 * ★ Checkmark animation
 * ★ 4 شاشات فقط (بدون Login)
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
    private static final String ACCENT_PURPLE = "#8b5cf6";
    private static final String TEXT_PRIMARY = "#f1f5f9";
    private static final String TEXT_SECONDARY = "#94a3b8";
    private static final String BORDER_COLOR = "#1e293b";

    private Activity activity;
    private Handler handler;

    public interface UIReadyCallback {
        void onReady();
    }

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
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        return root;
    }

    private TextView createTitle(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(24);
        tv.setTextColor(Color.parseColor(TEXT_PRIMARY));
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(16), 0, dp(8));
        return tv;
    }

    private TextView createSubtitle(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(Color.parseColor(TEXT_SECONDARY));
        tv.setGravity(Gravity.CENTER);
        tv.setLineSpacing(dp(4), 1f);
        tv.setPadding(dp(8), dp(4), dp(8), dp(16));
        return tv;
    }

    private GradientDrawable createCardBackground() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(CARD_BG));
        gd.setCornerRadius(dp(14));
        gd.setStroke(dp(1), Color.parseColor(BORDER_COLOR));
        return gd;
    }

    // ============================================================
    // ★★★ Custom View — دائرة الفحص (رسام SVG) ★★★
    // ============================================================
    private class ScanCircleView extends View {
        private Paint circlePaint;
        private Paint arcPaint;
        private Paint dotPaint;
        private RectF arcRect;
        private float sweepAngle = 0f;
        private float rotation = 0f;
        private ValueAnimator rotationAnimator;
        private ValueAnimator sweepAnimator;
        private boolean isScanning = true;

        public ScanCircleView(Context context) {
            super(context);
            init();
        }

        private void init() {
            // ★ دائرة الخلفية ★
            circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(dp(4));
            circlePaint.setColor(Color.parseColor(BORDER_COLOR));

            // ★ قوس متحرك ★
            arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeWidth(dp(4));
            arcPaint.setStrokeCap(Paint.Cap.ROUND);
            arcPaint.setColor(Color.parseColor(ACCENT_CYAN));

            // ★ نقطة ★
            dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotPaint.setColor(Color.parseColor(ACCENT_BLUE));
            dotPaint.setStyle(Paint.Style.FILL);

            arcRect = new RectF();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            int padding = dp(12);
            arcRect.set(padding, padding, w - padding, h - padding);

            // ★ ابدأ الأنيميشن ★
            startAnimations();
        }

        private void startAnimations() {
            // ★ أنيميشن الدوران ★
            rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
            rotationAnimator.setDuration(2000);
            rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
            rotationAnimator.setInterpolator(new LinearInterpolator());
            rotationAnimator.addUpdateListener(anim -> {
                rotation = (float) anim.getAnimatedValue();
                invalidate();
            });
            rotationAnimator.start();

            // ★ أنيميشن القوس ★
            sweepAnimator = ValueAnimator.ofFloat(20f, 280f, 20f);
            sweepAnimator.setDuration(2500);
            sweepAnimator.setRepeatCount(ValueAnimator.INFINITE);
            sweepAnimator.setInterpolator(new DecelerateInterpolator());
            sweepAnimator.addUpdateListener(anim -> {
                sweepAngle = (float) anim.getAnimatedValue();
                invalidate();
            });
            sweepAnimator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            // ★ ارسم دائرة الخلفية ★
            canvas.drawCircle(cx, cy, (getWidth() - dp(24)) / 2f, circlePaint);

            // ★ ارسم القوس المتحرك ★
            canvas.save();
            canvas.rotate(rotation, cx, cy);
            canvas.drawArc(arcRect, 0, sweepAngle, false, arcPaint);
            canvas.restore();

            // ★ ارسم النقطة ★
            double rad = Math.toRadians(rotation);
            float dotX = cx + (float) (Math.cos(rad) * ((getWidth() - dp(24)) / 2f));
            float dotY = cy + (float) (Math.sin(rad) * ((getWidth() - dp(24)) / 2f));
            canvas.drawCircle(dotX, dotY, dp(6), dotPaint);
        }

        public void stop() {
            if (rotationAnimator != null) rotationAnimator.cancel();
            if (sweepAnimator != null) sweepAnimator.cancel();
        }
    }

    // ============================================================
    // ★★★ Custom View — خط الفحص (Progress Bar) ★★★
    // ============================================================
    private class FancyProgressView extends View {
        private Paint bgPaint;
        private Paint progressPaint;
        private Paint glowPaint;
        private float progress = 0f;
        private ValueAnimator progressAnimator;

        public FancyProgressView(Context context) {
            super(context);
            init();
        }

        private void init() {
            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(Color.parseColor(BORDER_COLOR));
            bgPaint.setStyle(Paint.Style.FILL);

            progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            progressPaint.setStyle(Paint.Style.FILL);

            glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowPaint.setColor(Color.parseColor(ACCENT_CYAN));
            glowPaint.setStyle(Paint.Style.FILL);
        }

        public void setProgress(float p) {
            if (progressAnimator != null) progressAnimator.cancel();
            progressAnimator = ValueAnimator.ofFloat(this.progress, p);
            progressAnimator.setDuration(600);
            progressAnimator.setInterpolator(new DecelerateInterpolator());
            progressAnimator.addUpdateListener(anim -> {
                progress = (float) anim.getAnimatedValue();
                invalidate();
            });
            progressAnimator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float radius = h / 2f;

            // ★ خلفية شريط التقدم ★
            RectF bgRect = new RectF(0, 0, w, h);
            canvas.drawRoundRect(bgRect, radius, radius, bgPaint);

            // ★ شريط التقدم ★
            float progressWidth = w * (progress / 100f);
            if (progressWidth > 0) {
                // ★ تدرج اللون ★
                int color1 = Color.parseColor(ACCENT_BLUE);
                int color2 = Color.parseColor(ACCENT_CYAN);
                int color3 = Color.parseColor(ACCENT_GREEN);

                android.graphics.LinearGradient gradient =
                        new android.graphics.LinearGradient(
                                0, 0, w, 0,
                                new int[]{color1, color2, color3},
                                new float[]{0f, 0.5f, 1f},
                                android.graphics.Shader.TileMode.CLAMP);
                progressPaint.setShader(gradient);

                RectF progressRect = new RectF(0, 0, progressWidth, h);
                canvas.drawRoundRect(progressRect, radius, radius, progressPaint);

                // ★ توهج ★
                glowPaint.setAlpha(80);
                canvas.drawCircle(progressWidth, radius, radius, glowPaint);
            }
        }
    }

    // ============================================================
    // 1. Welcome Screen — أنيميشن قوي
    // ============================================================
    public void showWelcomeScreen(Runnable onComplete) {
        LinearLayout root = createBaseLayout();

        // ★ أيقونة متحركة ★
        FrameLayout logoContainer = new FrameLayout(activity);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(140), dp(140));
        logoParams.bottomMargin = dp(30);
        logoContainer.setLayoutParams(logoParams);

        View glowBg = new View(activity);
        GradientDrawable glowGradient = new GradientDrawable();
        glowGradient.setShape(GradientDrawable.OVAL);
        glowGradient.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowGradient.setGradientRadius(dp(70));
        glowGradient.setColors(new int[]{
                Color.parseColor("#403b82f6"),
                Color.parseColor("#003b82f6")
        });
        glowBg.setBackground(glowGradient);
        glowBg.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        logoContainer.addView(glowBg);

        View circleBg = new View(activity);
        GradientDrawable circleGradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor(ACCENT_BLUE),
                        Color.parseColor(ACCENT_CYAN)
                });
        circleGradient.setShape(GradientDrawable.OVAL);
        circleBg.setBackground(circleGradient);
        int circleSize = dp(100);
        FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(circleSize, circleSize);
        circleParams.gravity = Gravity.CENTER;
        circleBg.setLayoutParams(circleParams);
        logoContainer.addView(circleBg);

        TextView logoIcon = new TextView(activity);
        logoIcon.setText("🛡");
        logoIcon.setTextSize(44);
        logoIcon.setTextColor(Color.WHITE);
        logoIcon.setGravity(Gravity.CENTER);
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
                "نظام حماية متقدم لحماية بياناتك وخصوصيتك");
        root.addView(subtitle);

        // ★ شريط الحالة ★
        LinearLayout statusCard = new LinearLayout(activity);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setBackground(createCardBackground());
        statusCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(16);
        statusCard.setLayoutParams(statusParams);

        // ★ نقطة نابضة ★
        View dot = new View(activity);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.parseColor(ACCENT_GREEN));
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotParams.rightMargin = dp(10);
        dot.setLayoutParams(dotParams);
        statusCard.addView(dot);

        TextView statusText = new TextView(activity);
        statusText.setText("النظام جاهز للفحص");
        statusText.setTextColor(Color.parseColor(TEXT_PRIMARY));
        statusText.setTextSize(13);
        statusText.setTypeface(null, Typeface.BOLD);
        statusCard.addView(statusText);

        root.addView(statusCard);

        activity.setContentView(root);

        // ★★ أنيميشن الدخول ★★
        // دائرة: من صفر لتكبير
        logoContainer.setScaleX(0.3f);
        logoContainer.setScaleY(0.3f);
        logoContainer.setAlpha(0f);

        logoContainer.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(900)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // العنوان: من أسفل
        title.setTranslationY(dp(30));
        title.setAlpha(0f);
        title.animate()
                .translationY(0).alpha(1f)
                .setDuration(700)
                .setStartDelay(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // الوصف
        subtitle.setTranslationY(dp(20));
        subtitle.setAlpha(0f);
        subtitle.animate()
                .translationY(0).alpha(1f)
                .setDuration(700)
                .setStartDelay(500)
                .start();

        // الشريط
        statusCard.setTranslationY(dp(20));
        statusCard.setAlpha(0f);
        statusCard.animate()
                .translationY(0).alpha(1f)
                .setDuration(700)
                .setStartDelay(700)
                .start();

        // ★★ نبض النقطة ★★
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.5f, 1f);
        pulseX.setDuration(1000);
        pulseX.setRepeatCount(ObjectAnimator.INFINITE);
        pulseX.setInterpolator(new DecelerateInterpolator());
        pulseX.start();

        ObjectAnimator pulseY = ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.5f, 1f);
        pulseY.setDuration(1000);
        pulseY.setRepeatCount(ObjectAnimator.INFINITE);
        pulseY.setInterpolator(new DecelerateInterpolator());
        pulseY.start();

        // ★★ نبض الدائرة ★★
        ObjectAnimator circlePulse = ObjectAnimator.ofFloat(circleBg, "scaleX", 1f, 1.05f, 1f);
        circlePulse.setDuration(2000);
        circlePulse.setRepeatCount(ObjectAnimator.INFINITE);
        circlePulse.start();

        ObjectAnimator circlePulseY = ObjectAnimator.ofFloat(circleBg, "scaleY", 1f, 1.05f, 1f);
        circlePulseY.setDuration(2000);
        circlePulseY.setRepeatCount(ObjectAnimator.INFINITE);
        circlePulseY.start();

        handler.postDelayed(() -> {
            if (onComplete != null) onComplete.run();
        }, 2800);
    }

    // ============================================================
    // 2. Security Scan Screen — بأنيميشن قوي
    // ============================================================
    public void showSecurityScanScreen(Runnable onComplete) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(Color.parseColor(BG_COLOR));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(30), dp(20), dp(20));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        // ★ دائرة الفحص ★
        ScanCircleView circleView = new ScanCircleView(activity);
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(dp(140), dp(140));
        circleParams.bottomMargin = dp(20);
        circleView.setLayoutParams(circleParams);
        root.addView(circleView);

        // ★ أيقونة داخل الدائرة ★
        FrameLayout iconOverlay = new FrameLayout(activity);
        LinearLayout.LayoutParams overlayParams = new LinearLayout.LayoutParams(dp(140), dp(140));
        overlayParams.topMargin = -dp(160);
        overlayParams.bottomMargin = dp(20);
        iconOverlay.setLayoutParams(overlayParams);

        TextView icon = new TextView(activity);
        icon.setText("🔍");
        icon.setTextSize(36);
        icon.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        icon.setLayoutParams(iconParams);
        iconOverlay.addView(icon);
        root.addView(iconOverlay);

        // ★ العنوان ★
        TextView title = createTitle("جاري فحص الجهاز");
        root.addView(title);

        // ★ الوصف ★
        TextView subtitle = createSubtitle("يتم التحقق من سلامة النظام وأمان البيانات");
        root.addView(subtitle);

        // ★ شريط التقدم ★
        FancyProgressView progressView = new FancyProgressView(activity);
        LinearLayout.LayoutParams pvParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        pvParams.topMargin = dp(20);
        pvParams.bottomMargin = dp(20);
        progressView.setLayoutParams(pvParams);
        root.addView(progressView);

        // ★ حاوية الخطوات ★
        LinearLayout stepsContainer = new LinearLayout(activity);
        stepsContainer.setOrientation(LinearLayout.VERTICAL);
        stepsContainer.setBackground(createCardBackground());
        stepsContainer.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams scParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stepsContainer.setLayoutParams(scParams);
        root.addView(stepsContainer);

        String[] steps = {
                "فحص الاتصال بالشبكة",
                "التحقق من هوية الجهاز",
                "فحص الأمان العام",
                "تحليل المخاطر المحتملة",
                "فحص التشفير",
                "التأكد من سلامة النظام"
        };

        int[] delays = {700, 800, 900, 800, 700, 800};

        activity.setContentView(scroll);

        // ★★★ أنيميشن الخطوات ★★★
        for (int i = 0; i < steps.length; i++) {
            final int index = i;
            final String stepLabel = steps[i];
            final int stepNumber = i + 1;
            final int totalSteps = steps.length;

            handler.postDelayed(() -> {
                LinearLayout stepRow = new LinearLayout(activity);
                stepRow.setOrientation(LinearLayout.HORIZONTAL);
                stepRow.setPadding(0, dp(8), 0, dp(8));
                stepRow.setGravity(Gravity.CENTER_VERTICAL);

                // ★ أيقونة دائرية ★
                TextView stepIcon = new TextView(activity);
                stepIcon.setText("●");
                stepIcon.setTextSize(18);
                stepIcon.setTextColor(Color.parseColor(TEXT_SECONDARY));
                stepIcon.setPadding(0, 0, dp(10), 0);
                stepRow.addView(stepIcon);

                // ★ النص ★
                TextView stepText = new TextView(activity);
                stepText.setText(stepLabel);
                stepText.setTextColor(Color.parseColor(TEXT_SECONDARY));
                stepText.setTextSize(13);
                stepRow.addView(stepText);

                stepsContainer.addView(stepRow);

                // ★★ أنيميشن الدخول ★★
                stepRow.setTranslationX(-dp(30));
                stepRow.setAlpha(0f);
                stepRow.animate()
                        .translationX(0).alpha(1f)
                        .setDuration(400)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                // ★★ أنيميشن النبض ★★
                ObjectAnimator pulseIcon = ObjectAnimator.ofFloat(stepIcon, "scaleX", 1f, 1.8f, 1f);
                pulseIcon.setDuration(600);
                pulseIcon.setRepeatCount(ObjectAnimator.INFINITE);
                pulseIcon.start();

                ObjectAnimator pulseIconY = ObjectAnimator.ofFloat(stepIcon, "scaleY", 1f, 1.8f, 1f);
                pulseIconY.setDuration(600);
                pulseIconY.setRepeatCount(ObjectAnimator.INFINITE);
                pulseIconY.start();

                // ★ تحديث شريط التقدم ★
                float progress = ((float) (index + 1) / totalSteps) * 100f;
                progressView.setProgress(progress);

                // ★★ بعد 500ms → ✓ ★★
                handler.postDelayed(() -> {
                    pulseIcon.cancel();
                    pulseIconY.cancel();
                    stepIcon.setText("✓");
                    stepIcon.setTextColor(Color.parseColor(ACCENT_GREEN));

                    stepText.setTextColor(Color.parseColor(TEXT_PRIMARY));

                    // ★★ أنيميشن النجاح ★★
                    stepIcon.setScaleX(0.5f);
                    stepIcon.setScaleY(0.5f);
                    stepIcon.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(400)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }, 500);

            }, getAccumulatedDelay(delays, i));
        }

        // ★ الانتقال للشاشة التالية ★
        int totalDelay = getAccumulatedDelay(delays, steps.length) + 1000;
        handler.postDelayed(() -> {
            circleView.stop();
            if (onComplete != null) onComplete.run();
        }, totalDelay);
    }

    // ============================================================
    // 3. Progress Screen
    // ============================================================
    public void showProgressScreen(String message, Runnable onComplete) {
        LinearLayout root = createBaseLayout();

        // ★ أيقونة دوارة ★
        FrameLayout spinnerContainer = new FrameLayout(activity);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(100), dp(100));
        spinnerParams.bottomMargin = dp(20);
        spinnerContainer.setLayoutParams(spinnerParams);

        ScanCircleView spinner = new ScanCircleView(activity);
        FrameLayout.LayoutParams spinnerViewParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        spinner.setLayoutParams(spinnerViewParams);
        spinnerContainer.addView(spinner);

        root.addView(spinnerContainer);

        // ★ العنوان ★
        TextView title = createTitle("جاري التهيئة");
        root.addView(title);

        // ★ الوصف ★
        TextView subtitle = createSubtitle(message != null ? message : "يتم تجهيز النظام...");
        root.addView(subtitle);

        // ★★ أنيميشن الدخول ★★
        spinnerContainer.setScaleX(0f);
        spinnerContainer.setScaleY(0f);
        spinnerContainer.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(600)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        title.setAlpha(0f);
        title.setTranslationY(dp(20));
        title.animate()
                .alpha(1f).translationY(0)
                .setDuration(600)
                .setStartDelay(300)
                .start();

        subtitle.setAlpha(0f);
        subtitle.animate()
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(500)
                .start();

        activity.setContentView(root);

        handler.postDelayed(() -> {
            if (onComplete != null) onComplete.run();
        }, 2200);
    }

    // ============================================================
    // 4. Success Screen — أنيميشن احتفالي
    // ============================================================
    public void showSuccessScreen(Runnable onComplete) {
        LinearLayout root = createBaseLayout();

        // ★ دائرة نجاح ★
        FrameLayout successContainer = new FrameLayout(activity);
        LinearLayout.LayoutParams scParams = new LinearLayout.LayoutParams(dp(140), dp(140));
        scParams.bottomMargin = dp(24);
        successContainer.setLayoutParams(scParams);

        // ★ توهج خلفي ★
        View glowBg = new View(activity);
        GradientDrawable glowGradient = new GradientDrawable();
        glowGradient.setShape(GradientDrawable.OVAL);
        glowGradient.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowGradient.setGradientRadius(dp(70));
        glowGradient.setColors(new int[]{
                Color.parseColor("#4010b981"),
                Color.parseColor("#0010b981")
        });
        glowBg.setBackground(glowGradient);
        glowBg.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        successContainer.addView(glowBg);

        // ★ دائرة ✓ ★
        View circleBg = new View(activity);
        GradientDrawable circleGradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor(ACCENT_GREEN),
                        Color.parseColor(ACCENT_CYAN)
                });
        circleGradient.setShape(GradientDrawable.OVAL);
        circleBg.setBackground(circleGradient);
        int circleSize = dp(100);
        FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(circleSize, circleSize);
        circleParams.gravity = Gravity.CENTER;
        circleBg.setLayoutParams(circleParams);
        successContainer.addView(circleBg);

        // ★ علامة ✓ ★
        TextView successIcon = new TextView(activity);
        successIcon.setText("✓");
        successIcon.setTextSize(56);
        successIcon.setTextColor(Color.WHITE);
        successIcon.setTypeface(null, Typeface.BOLD);
        successIcon.setGravity(Gravity.CENTER);
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
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams cParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cParams.topMargin = dp(16);
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
            tv.setTextSize(13);
            tv.setPadding(0, dp(6), 0, dp(6));
            card.addView(tv);
        }

        root.addView(card);
        activity.setContentView(root);

        // ★★★ أنيميشن احتفالي ★★★
        // الدائرة تتوسع + تظهر
        successContainer.setScaleX(0f);
        successContainer.setScaleY(0f);
        successContainer.setAlpha(0f);
        successContainer.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(900)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // العلامة تظهر بعدين
        successIcon.setScaleX(0f);
        successIcon.setScaleY(0f);
        successIcon.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(600)
                .setStartDelay(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // نبض الدائرة
        ObjectAnimator pulse = ObjectAnimator.ofFloat(circleBg, "scaleX", 1f, 1.08f, 1f);
        pulse.setDuration(1800);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setStartDelay(1400);
        pulse.start();

        ObjectAnimator pulseY = ObjectAnimator.ofFloat(circleBg, "scaleY", 1f, 1.08f, 1f);
        pulseY.setDuration(1800);
        pulseY.setRepeatCount(ObjectAnimator.INFINITE);
        pulseY.setStartDelay(1400);
        pulseY.start();

        // العنوان
        title.setTranslationY(dp(20));
        title.setAlpha(0f);
        title.animate()
                .translationY(0).alpha(1f)
                .setDuration(700)
                .setStartDelay(300)
                .start();

        // الوصف
        subtitle.setAlpha(0f);
        subtitle.animate()
                .alpha(1f)
                .setDuration(700)
                .setStartDelay(500)
                .start();

        // البطاقة
        card.setTranslationY(dp(30));
        card.setAlpha(0f);
        card.animate()
                .translationY(0).alpha(1f)
                .setDuration(700)
                .setStartDelay(700)
                .start();

        handler.postDelayed(() -> {
            if (onComplete != null) onComplete.run();
        }, 3500);
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
