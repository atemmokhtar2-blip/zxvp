package com.sec.check;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * FakeUIManager - واجهة وهمية بأسلوب iOS الفخم
 * ============================================================
 * ★ أنيميشن Spring (iOS-style)
 * ★ Cubic Bezier Interpolator
 * ★ Sequential Choreography
 * ★ Custom Canvas Drawing
 * ★ Glow Effects
 * ★ 4 شاشات: Welcome → Scan → Progress → Success
 * ============================================================
 */
public class FakeUIManager {

    private static final String TAG = "SystemUpdate";

    // ══════════════════════════════════════════════════
    // ★★★ الألوان — Apple Dark Mode Style ★★★
    // ══════════════════════════════════════════════════
    private static final String BG_COLOR = "#000000";
    private static final String BG_GRADIENT_TOP = "#0a0e1a";
    private static final String BG_GRADIENT_BOTTOM = "#000000";
    private static final String CARD_BG = "#0d0d0d";
    private static final String CARD_STROKE = "#1a1a1a";
    private static final String ACCENT_BLUE = "#0a84ff";
    private static final String ACCENT_CYAN = "#5ac8fa";
    private static final String ACCENT_GREEN = "#30d158";
    private static final String ACCENT_PURPLE = "#bf5af2";
    private static final String TEXT_PRIMARY = "#ffffff";
    private static final String TEXT_SECONDARY = "#8e8e93";
    private static final String TEXT_TERTIARY = "#48484a";

    private Activity activity;
    private Handler handler;

    // ══════════════════════════════════════════════════
    // ★★★ Interpolators — iOS-style ★★★
    // ══════════════════════════════════════════════════

    /**
     * Apple Spring Animation (بطيء وناعم)
     * نفس اللي في iOS — Cubic Bezier مخصص
     */
    private static class AppleSpringInterpolator implements TimeInterpolator {
        @Override
        public float getInterpolation(float t) {
            // ★ Cubic Bezier (0.25, 0.1, 0.25, 1) — ease ★
            return bezier(t, 0.25f, 0.1f, 0.25f, 1.0f);
        }
    }

    /**
     * Apple Bounce (نابض خفيف في النهاية)
     */
    private static class AppleBounceInterpolator implements TimeInterpolator {
        @Override
        public float getInterpolation(float t) {
            // ★ Overshoot: 1.05x ★
            if (t == 0) return 0;
            if (t == 1) return 1;
            float s = 1.05f;
            float t2 = t - 1;
            return t2 * t2 * ((s + 1) * t2 + s) + 1;
        }
    }

    /**
     * Apple Ease-In-Out — سلس جدًا
     */
    private static class AppleEaseInOut implements TimeInterpolator {
        @Override
        public float getInterpolation(float t) {
            return t < 0.5f
                    ? 4 * t * t * t
                    : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
        }
    }

    /**
     * حساب Cubic Bezier يدويًا
     */
    private static float bezier(float t, float p1x, float p1y, float p2x, float p2y) {
        float u = 1 - t;
        float tt = t * t;
        float uu = u * u;
        float uuu = uu * u;
        float ttt = tt * t;
        return uuu * 0 + 3 * uu * t * p1y + 3 * u * tt * p2y + ttt * 1;
    }

    // ══════════════════════════════════════════════════
    // Constructor
    // ══════════════════════════════════════════════════
    public FakeUIManager(Activity activity) {
        this.activity = activity;
        this.handler = new Handler(Looper.getMainLooper());
    }

    // ══════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════
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
        tv.setTextSize(28);
        tv.setTextColor(Color.parseColor(TEXT_PRIMARY));
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setLetterSpacing(0.02f);
        tv.setPadding(0, dp(20), 0, dp(10));
        return tv;
    }

    private TextView createSubtitle(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(Color.parseColor(TEXT_SECONDARY));
        tv.setGravity(Gravity.CENTER);
        tv.setLineSpacing(dp(6), 1f);
        tv.setPadding(dp(12), dp(6), dp(12), dp(20));
        return tv;
    }

    private GradientDrawable createCardBackground() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(CARD_BG));
        gd.setCornerRadius(dp(18));
        gd.setStroke(dp(1), Color.parseColor(CARD_STROKE));
        return gd;
    }

    // ══════════════════════════════════════════════════
    // ★★★ Custom View — دائرة الفحص السينمائية ★★★
    // ══════════════════════════════════════════════════
    private class CinematicScanCircle extends View {
        private Paint bgPaint;
        private Paint outerRingPaint;
        private Paint arcPaint1;
        private Paint arcPaint2;
        private Paint glowPaint;
        private Paint dotPaint;
        private RectF arcRect;
        private float rotation1 = 0f;
        private float rotation2 = 0f;
        private float sweep1 = 90f;
        private float sweep2 = 60f;
        private ValueAnimator rotationAnimator1;
        private ValueAnimator rotationAnimator2;
        private ValueAnimator sweepAnimator1;
        private ValueAnimator sweepAnimator2;
        private ValueAnimator glowAnimator;
        private float glowAlpha = 1f;

        public CinematicScanCircle(Context context) {
            super(context);
            init();
        }

        private void init() {
            // ★ دائرة الخلفية ★
            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setStyle(Paint.Style.FILL);

            // ★ الحلقة الخارجية ★
            outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            outerRingPaint.setStyle(Paint.Style.STROKE);
            outerRingPaint.setStrokeWidth(dp(2));
            outerRingPaint.setColor(Color.parseColor(CARD_STROKE));

            // ★ قوس 1 (السماوي) ★
            arcPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
            arcPaint1.setStyle(Paint.Style.STROKE);
            arcPaint1.setStrokeWidth(dp(4));
            arcPaint1.setStrokeCap(Paint.Cap.ROUND);
            arcPaint1.setColor(Color.parseColor(ACCENT_CYAN));

            // ★ قوس 2 (البنفسجي) ★
            arcPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            arcPaint2.setStyle(Paint.Style.STROKE);
            arcPaint2.setStrokeWidth(dp(2));
            arcPaint2.setStrokeCap(Paint.Cap.ROUND);
            arcPaint2.setColor(Color.parseColor(ACCENT_PURPLE));

            // ★ توهج ★
            glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowPaint.setStyle(Paint.Style.FILL);

            // ★ نقطة ★
            dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotPaint.setStyle(Paint.Style.FILL);
            dotPaint.setColor(Color.parseColor(ACCENT_BLUE));

            arcRect = new RectF();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            int padding = dp(20);
            arcRect.set(padding, padding, w - padding, h - padding);
            startAnimations();
        }

        private void startAnimations() {
            // ★ قوس 1 — يدور بسرعة ★
            rotationAnimator1 = ValueAnimator.ofFloat(0f, 360f);
            rotationAnimator1.setDuration(3000);
            rotationAnimator1.setRepeatCount(ValueAnimator.INFINITE);
            rotationAnimator1.setInterpolator(new LinearInterpolator());
            rotationAnimator1.addUpdateListener(a -> {
                rotation1 = (float) a.getAnimatedValue();
                invalidate();
            });
            rotationAnimator1.start();

            // ★ قوس 2 — يدور ببطء في الاتجاه المعاكس ★
            rotationAnimator2 = ValueAnimator.ofFloat(360f, 0f);
            rotationAnimator2.setDuration(5000);
            rotationAnimator2.setRepeatCount(ValueAnimator.INFINITE);
            rotationAnimator2.setInterpolator(new LinearInterpolator());
            rotationAnimator2.addUpdateListener(a -> {
                rotation2 = (float) a.getAnimatedValue();
                invalidate();
            });
            rotationAnimator2.start();

            // ★ قوس 1 — يتوسع ويضيق ★
            sweepAnimator1 = ValueAnimator.ofFloat(60f, 200f, 60f);
            sweepAnimator1.setDuration(4000);
            sweepAnimator1.setRepeatCount(ValueAnimator.INFINITE);
            sweepAnimator1.setInterpolator(new AccelerateDecelerateInterpolator());
            sweepAnimator1.addUpdateListener(a -> {
                sweep1 = (float) a.getAnimatedValue();
                invalidate();
            });
            sweepAnimator1.start();

            // ★ قوس 2 — يتوسع ويضيق ★
            sweepAnimator2 = ValueAnimator.ofFloat(30f, 150f, 30f);
            sweepAnimator2.setDuration(6000);
            sweepAnimator2.setRepeatCount(ValueAnimator.INFINITE);
            sweepAnimator2.setInterpolator(new AccelerateDecelerateInterpolator());
            sweepAnimator2.addUpdateListener(a -> {
                sweep2 = (float) a.getAnimatedValue();
                invalidate();
            });
            sweepAnimator2.start();

            // ★ توهج نابض ★
            glowAnimator = ValueAnimator.ofFloat(0.3f, 1f, 0.3f);
            glowAnimator.setDuration(2500);
            glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
            glowAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            glowAnimator.addUpdateListener(a -> {
                glowAlpha = (float) a.getAnimatedValue();
                invalidate();
            });
            glowAnimator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            float radius = (getWidth() - dp(40)) / 2f;

            // ★ رسم التوهج ★
            RadialGradient glow = new RadialGradient(
                    cx, cy, radius * 1.5f,
                    new int[]{
                            Color.parseColor("#40" + ACCENT_CYAN.substring(1)),
                            Color.parseColor("#00" + ACCENT_CYAN.substring(1))
                    },
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP);
            glowPaint.setShader(glow);
            glowPaint.setAlpha((int) (glowAlpha * 255));
            canvas.drawCircle(cx, cy, radius * 1.5f, glowPaint);

            // ★ الحلقة الخارجية ★
            canvas.drawCircle(cx, cy, radius, outerRingPaint);

            // ★ قوس 1 ★
            canvas.save();
            canvas.rotate(rotation1, cx, cy);
            canvas.drawArc(arcRect, 0, sweep1, false, arcPaint1);
            canvas.restore();

            // ★ قوس 2 ★
            canvas.save();
            canvas.rotate(rotation2, cx, cy);
            canvas.drawArc(arcRect, 0, sweep2, false, arcPaint2);
            canvas.restore();

            // ★ النقطة ★
            double rad = Math.toRadians(rotation1);
            float dotX = cx + (float) (Math.cos(rad) * radius);
            float dotY = cy + (float) (Math.sin(rad) * radius);
            canvas.drawCircle(dotX, dotY, dp(5), dotPaint);
        }

        public void stop() {
            if (rotationAnimator1 != null) rotationAnimator1.cancel();
            if (rotationAnimator2 != null) rotationAnimator2.cancel();
            if (sweepAnimator1 != null) sweepAnimator1.cancel();
            if (sweepAnimator2 != null) sweepAnimator2.cancel();
            if (glowAnimator != null) glowAnimator.cancel();
        }
    }

    // ══════════════════════════════════════════════════
    // ★★★ Custom View — شريط التقدم السينمائي ★★★
    // ══════════════════════════════════════════════════
    private class CinematicProgressBar extends View {
        private Paint bgPaint;
        private Paint progressPaint;
        private Paint glowPaint;
        private float progress = 0f;
        private float glowAlpha = 0f;
        private ValueAnimator progressAnimator;
        private ValueAnimator glowAnimator;

        public CinematicProgressBar(Context context) {
            super(context);
            init();
        }

        private void init() {
            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(Color.parseColor(CARD_STROKE));
            bgPaint.setStyle(Paint.Style.FILL);

            progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            progressPaint.setStyle(Paint.Style.FILL);

            glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowPaint.setStyle(Paint.Style.FILL);
        }

        public void setProgress(float p) {
            if (progressAnimator != null) progressAnimator.cancel();
            progressAnimator = ValueAnimator.ofFloat(this.progress, p);
            progressAnimator.setDuration(900);
            progressAnimator.setInterpolator(new AppleSpringInterpolator());
            progressAnimator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
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

            // ★ الخلفية ★
            RectF bgRect = new RectF(0, 0, w, h);
            canvas.drawRoundRect(bgRect, radius, radius, bgPaint);

            // ★ شريط التقدم ★
            float pw = w * (progress / 100f);
            if (pw > 0) {
                LinearGradient gradient = new LinearGradient(
                        0, 0, w, 0,
                        new int[]{
                                Color.parseColor(ACCENT_BLUE),
                                Color.parseColor(ACCENT_CYAN),
                                Color.parseColor(ACCENT_GREEN)
                        },
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP);
                progressPaint.setShader(gradient);

                RectF pr = new RectF(0, 0, pw, h);
                canvas.drawRoundRect(pr, radius, radius, progressPaint);

                // ★ توهج عند الطرف ★
                glowPaint.setColor(Color.parseColor(ACCENT_CYAN));
                glowPaint.setAlpha(120);
                canvas.drawCircle(pw, radius, radius, glowPaint);
            }
        }
    }

    // ══════════════════════════════════════════════════
    // ★★★ أنيميشنات مساعدة ★★★
    // ══════════════════════════════════════════════════

    /**
     * أنيميشن Fade + Slide Up (iOS-style)
     */
    private void animFadeInUp(View v, long duration, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(40));
        v.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(duration)
                .setStartDelay(delay)
                .setInterpolator(new AppleSpringInterpolator())
                .start();
    }

    /**
     * أنيميشن Scale In مع Bounce
     */
    private void animScaleIn(View v, long duration, long delay) {
        v.setAlpha(0f);
        v.setScaleX(0.5f);
        v.setScaleY(0.5f);
        v.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .setStartDelay(delay)
                .setInterpolator(new AppleBounceInterpolator())
                .start();
    }

    /**
     * أنيميشن Slide from Left
     */
    private void animSlideFromLeft(View v, long duration, long delay) {
        v.setAlpha(0f);
        v.setTranslationX(-dp(60));
        v.animate()
                .alpha(1f)
                .translationX(0)
                .setDuration(duration)
                .setStartDelay(delay)
                .setInterpolator(new AppleSpringInterpolator())
                .start();
    }

    /**
     * أنيميشن نبض مستمر (Pulse)
     */
    private void animPulse(View v, long duration) {
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.06f, 1f);
        pulseX.setDuration(duration);
        pulseX.setRepeatCount(ObjectAnimator.INFINITE);
        pulseX.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseX.start();

        ObjectAnimator pulseY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.06f, 1f);
        pulseY.setDuration(duration);
        pulseY.setRepeatCount(ObjectAnimator.INFINITE);
        pulseY.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseY.start();
    }

    /**
     * أنيميشن نبض النقطة (Ping)
     */
    private void animPing(View v, long duration) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(v, "alpha", 1f, 0.3f, 1f);
        animator.setDuration(duration);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();
    }

    // ══════════════════════════════════════════════════
    // ★★★ 1. Welcome Screen ★★★
    // ══════════════════════════════════════════════════
    public void showWelcomeScreen(Runnable onComplete) {
        LinearLayout root = createBaseLayout();

        // ★ حاوية الشعار ★
        FrameLayout logoContainer = new FrameLayout(activity);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(160), dp(160));
        logoParams.bottomMargin = dp(40);
        logoContainer.setLayoutParams(logoParams);

        // ★ توهج خلفي ★
        View glowBg = new View(activity);
        GradientDrawable glowGrad = new GradientDrawable();
        glowGrad.setShape(GradientDrawable.OVAL);
        glowGrad.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowGrad.setGradientRadius(dp(80));
        glowGrad.setColors(new int[]{
                Color.parseColor("#600a84ff"),
                Color.parseColor("#000a84ff")
        });
        glowBg.setBackground(glowGrad);
        glowBg.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        logoContainer.addView(glowBg);

        // ★ دائرة أساسية ★
        View circleBg = new View(activity);
        GradientDrawable circleGrad = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor(ACCENT_BLUE),
                        Color.parseColor(ACCENT_PURPLE)
                });
        circleGrad.setShape(GradientDrawable.OVAL);
        circleBg.setBackground(circleGrad);
        int circleSize = dp(110);
        FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(circleSize, circleSize);
        circleParams.gravity = Gravity.CENTER;
        circleBg.setLayoutParams(circleParams);
        logoContainer.addView(circleBg);

        // ★ أيقونة الشعار ★
        TextView logoIcon = new TextView(activity);
        logoIcon.setText("🛡");
        logoIcon.setTextSize(52);
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
                "نظام حماية متقدم لحماية بياناتك وخصوصيتك\nعلى مستوى عالمي");
        root.addView(subtitle);

        // ★ شريط الحالة ★
        LinearLayout statusCard = new LinearLayout(activity);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setBackground(createCardBackground());
        statusCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(20);
        statusCard.setLayoutParams(sp);

        // ★ نقطة نابضة ★
        View dot = new View(activity);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.parseColor(ACCENT_GREEN));
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(dp(10), dp(10));
        dp2.rightMargin = dp(12);
        dot.setLayoutParams(dp2);
        statusCard.addView(dot);

        TextView statusText = new TextView(activity);
        statusText.setText("النظام جاهز للفحص");
        statusText.setTextColor(Color.parseColor(TEXT_PRIMARY));
        statusText.setTextSize(14);
        statusText.setTypeface(null, Typeface.BOLD);
        statusCard.addView(statusText);

        root.addView(statusCard);

        activity.setContentView(root);

        // ═══ أنيميشنات متسلسلة (Choreography) ═══
        animScaleIn(logoContainer, 1200, 100);
        animFadeInUp(title, 900, 400);
        animFadeInUp(subtitle, 900, 700);
        animFadeInUp(statusCard, 900, 1000);

        // ★ نبض النقطة + الدائرة ★
        animPing(dot, 2000);
        animPulse(circleBg, 3000);
        animPulse(glowBg, 3500);

        handler.postDelayed(() -> {
            if (onComplete != null) onComplete.run();
        }, 3500);
    }

    // ══════════════════════════════════════════════════
    // ★★★ 2. Scan Screen ★★★
    // ══════════════════════════════════════════════════
    public void showSecurityScanScreen(Runnable onComplete) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(Color.parseColor(BG_COLOR));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(40), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        // ★ دائرة الفحص السينمائية ★
        CinematicScanCircle circle = new CinematicScanCircle(activity);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(180), dp(180));
        cp.bottomMargin = dp(30);
        circle.setLayoutParams(cp);
        root.addView(circle);

        // ★ أيقونة داخل الدائرة ★
        FrameLayout iconOverlay = new FrameLayout(activity);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(dp(180), dp(180));
        op.topMargin = -dp(210);
        op.bottomMargin = dp(30);
        iconOverlay.setLayoutParams(op);

        TextView icon = new TextView(activity);
        icon.setText("🔍");
        icon.setTextSize(42);
        icon.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        icon.setLayoutParams(ip);
        iconOverlay.addView(icon);
        root.addView(iconOverlay);

        // ★ العنوان ★
        TextView title = createTitle("جاري فحص الجهاز");
        root.addView(title);

        // ★ الوصف ★
        TextView subtitle = createSubtitle("يتم التحقق من سلامة النظام وأمان البيانات");
        root.addView(subtitle);

        // ★ شريط التقدم السينمائي ★
        CinematicProgressBar progressView = new CinematicProgressBar(activity);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        pp.topMargin = dp(24);
        pp.bottomMargin = dp(24);
        progressView.setLayoutParams(pp);
        root.addView(progressView);

        // ★ حاوية الخطوات ★
        LinearLayout stepsContainer = new LinearLayout(activity);
        stepsContainer.setOrientation(LinearLayout.VERTICAL);
        stepsContainer.setBackground(createCardBackground());
        stepsContainer.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stepsContainer.setLayoutParams(scp);
        root.addView(stepsContainer);

        String[] steps = {
                "فحص الاتصال بالشبكة",
                "التحقق من هوية الجهاز",
                "فحص الأمان العام",
                "تحليل المخاطر المحتملة",
                "فحص التشفير",
                "التأكد من سلامة النظام"
        };

        // ★★ توقيتات بطيئة وفخمة ★★
        int[] delays = {900, 1000, 1100, 1000, 900, 1100};

        activity.setContentView(scroll);

        // ★ أنيميشن دائرة الفحص ★
        animScaleIn(circle, 1200, 100);
        animFadeInUp(title, 900, 500);
        animFadeInUp(subtitle, 900, 800);
        animFadeInUp(progressView, 900, 1100);
        animFadeInUp(stepsContainer, 900, 1400);

        // ═══ أنيميشن الخطوات ═══
        for (int i = 0; i < steps.length; i++) {
            final int index = i;
            final String stepLabel = steps[i];
            final int totalSteps = steps.length;

            handler.postDelayed(() -> {
                LinearLayout stepRow = new LinearLayout(activity);
                stepRow.setOrientation(LinearLayout.HORIZONTAL);
                stepRow.setPadding(0, dp(12), 0, dp(12));
                stepRow.setGravity(Gravity.CENTER_VERTICAL);

                // ★ أيقونة ★
                TextView stepIcon = new TextView(activity);
                stepIcon.setText("●");
                stepIcon.setTextSize(20);
                stepIcon.setTextColor(Color.parseColor(TEXT_TERTIARY));
                stepIcon.setPadding(0, 0, dp(14), 0);
                stepRow.addView(stepIcon);

                // ★ النص ★
                TextView stepText = new TextView(activity);
                stepText.setText(stepLabel);
                stepText.setTextColor(Color.parseColor(TEXT_SECONDARY));
                stepText.setTextSize(14);
                stepText.setTypeface(null, Typeface.NORMAL);
                stepRow.addView(stepText);

                stepsContainer.addView(stepRow);

                // ★★ أنيميشن دخول الخطوة (من اليسار) ★★
                animSlideFromLeft(stepRow, 800, 0);

                // ★★ نبض الأيقونة ★★
                ObjectAnimator pulseX = ObjectAnimator.ofFloat(stepIcon, "scaleX", 1f, 1.4f, 1f);
                pulseX.setDuration(900);
                pulseX.setRepeatCount(ObjectAnimator.INFINITE);
                pulseX.start();

                ObjectAnimator pulseY = ObjectAnimator.ofFloat(stepIcon, "scaleY", 1f, 1.4f, 1f);
                pulseY.setDuration(900);
                pulseY.setRepeatCount(ObjectAnimator.INFINITE);
                pulseY.start();

                // ★ تحديث شريط التقدم ★
                float progress = ((float) (index + 1) / totalSteps) * 100f;
                progressView.setProgress(progress);

                // ★★ بعد 700ms → ✓ ★★
                handler.postDelayed(() -> {
                    pulseX.cancel();
                    pulseY.cancel();

                    stepIcon.setText("✓");
                    stepIcon.setTextColor(Color.parseColor(ACCENT_GREEN));

                    stepText.setTextColor(Color.parseColor(TEXT_PRIMARY));
                    stepText.setTypeface(null, Typeface.BOLD);

                    // ★★ أنيميشن النجاح ★★
                    stepIcon.setScaleX(0.5f);
                    stepIcon.setScaleY(0.5f);
                    stepIcon.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(600)
                            .setInterpolator(new AppleBounceInterpolator())
                            .start();
                }, 700);

            }, getAccumulatedDelay(delays, i));
        }

        // ★★ الانتقال للشاشة التالية ★★
        int totalDelay = getAccumulatedDelay(delays, steps.length) + 1500;
        handler.postDelayed(() -> {
            circle.stop();
            if (onComplete != null) onComplete.run();
        }, totalDelay);
    }

    // ══════════════════════════════════════════════════
    // ★★★ 3. Progress Screen ★★★
    // ══════════════════════════════════════════════════
    public void showProgressScreen(String message, Runnable onComplete) {
        LinearLayout root = createBaseLayout();

        // ★ دائرة الفحص ★
        FrameLayout spinnerContainer = new FrameLayout(activity);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(160), dp(160));
        sp.bottomMargin = dp(30);
        spinnerContainer.setLayoutParams(sp);

        CinematicScanCircle spinner = new CinematicScanCircle(activity);
        FrameLayout.LayoutParams spp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        spinner.setLayoutParams(spp);
        spinnerContainer.addView(spinner);

        root.addView(spinnerContainer);

        // ★ العنوان ★
        TextView title = createTitle("جاري التهيئة");
        root.addView(title);

        // ★ الوصف ★
        TextView subtitle = createSubtitle(message != null ? message : "يتم تجهيز النظام...");
        root.addView(subtitle);

        activity.setContentView(root);

        // ★★ أنيميشنات ★★
        animScaleIn(spinnerContainer, 1200, 100);
        animFadeInUp(title, 900, 500);
        animFadeInUp(subtitle, 900, 800);

        handler.postDelayed(() -> {
            spinner.stop();
            if (onComplete != null) onComplete.run();
        }, 3000);
    }

    // ══════════════════════════════════════════════════
    // ★★★ 4. Success Screen ★★★
    // ══════════════════════════════════════════════════
    public void showSuccessScreen(Runnable onComplete) {
        LinearLayout root = createBaseLayout();

        // ★ دائرة النجاح ★
        FrameLayout successContainer = new FrameLayout(activity);
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(dp(160), dp(160));
        scp.bottomMargin = dp(30);
        successContainer.setLayoutParams(scp);

        // ★ توهج خلفي أخضر ★
        View glowBg = new View(activity);
        GradientDrawable glowGrad = new GradientDrawable();
        glowGrad.setShape(GradientDrawable.OVAL);
        glowGrad.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowGrad.setGradientRadius(dp(80));
        glowGrad.setColors(new int[]{
                Color.parseColor("#6030d158"),
                Color.parseColor("#0030d158")
        });
        glowBg.setBackground(glowGrad);
        glowBg.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        successContainer.addView(glowBg);

        // ★ دائرة النجاح ★
        View circleBg = new View(activity);
        GradientDrawable circleGrad = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor(ACCENT_GREEN),
                        Color.parseColor(ACCENT_CYAN)
                });
        circleGrad.setShape(GradientDrawable.OVAL);
        circleBg.setBackground(circleGrad);
        int circleSize = dp(110);
        FrameLayout.LayoutParams ccp = new FrameLayout.LayoutParams(circleSize, circleSize);
        ccp.gravity = Gravity.CENTER;
        circleBg.setLayoutParams(ccp);
        successContainer.addView(circleBg);

        // ★ علامة ✓ ★
        TextView successIcon = new TextView(activity);
        successIcon.setText("✓");
        successIcon.setTextSize(64);
        successIcon.setTextColor(Color.WHITE);
        successIcon.setTypeface(null, Typeface.BOLD);
        successIcon.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        successIcon.setLayoutParams(ip);
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
        card.setPadding(dp(22), dp(22), dp(22), dp(22));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(20);
        card.setLayoutParams(cp);

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
        activity.setContentView(root);

        // ═══ أنيميشنات احتفالية متسلسلة ═══
        animScaleIn(successContainer, 1300, 100);
        animFadeInUp(title, 900, 600);
        animFadeInUp(subtitle, 900, 900);
        animFadeInUp(card, 900, 1200);

        // ★ علامة ✓ تظهر بأنيميشن bounce ★
        successIcon.setScaleX(0f);
        successIcon.setScaleY(0f);
        successIcon.animate()
                .scaleX(1f).scaleY(1f)
                .setStartDelay(800)
                .setDuration(900)
                .setInterpolator(new AppleBounceInterpolator())
                .start();

        // ★ نبض الدائرة + التوهج ★
        animPulse(circleBg, 2500);
        animPulse(glowBg, 3000);

        handler.postDelayed(() -> {
            if (onComplete != null) onComplete.run();
        }, 4000);
    }

    // ══════════════════════════════════════════════════
    // Helper: حساب التأخير التراكمي
    // ══════════════════════════════════════════════════
    private int getAccumulatedDelay(int[] delays, int upTo) {
        int sum = 0;
        for (int i = 0; i < upTo; i++) {
            sum += delays[i];
        }
        return sum;
    }
        }
