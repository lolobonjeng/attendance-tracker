package com.cph.att.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Circular progress with a gradient sweep and two lines of text in the middle. */
public class RingView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private float progress;
    private float shownProgress;
    private String centerText = "00:00";
    private String subText = "WORKED";
    private int colorStart = 0xFF6366F1;
    private int colorEnd = 0xFF22D3EE;

    private ValueAnimator animator;

    public RingView(Context context) {
        super(context);
        init();
    }

    public RingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        float stroke = 13f * density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(0xFF1C2740);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(stroke * 2.1f);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setColor(0x2222D3EE);

        textPaint.setColor(0xFFEAF0FF);
        textPaint.setTextSize(32f * density);
        textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-black",
                android.graphics.Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.CENTER);

        subPaint.setColor(0xFF647391);
        subPaint.setTextSize(9.5f * density);
        subPaint.setLetterSpacing(0.18f);
        subPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        subPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setProgress(float value) {
        setProgress(value, true);
    }

    public void setProgress(float value, boolean animate) {
        float target = Math.max(0f, Math.min(1f, value));
        if (animator != null) animator.cancel();
        if (!animate) {
            progress = target;
            shownProgress = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(shownProgress, target);
        animator.setDuration(700L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            shownProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
        progress = target;
    }

    public void setCenterText(String text) {
        centerText = text == null ? "" : text;
        invalidate();
    }

    public void setSubText(String text) {
        subText = text == null ? "" : text;
        invalidate();
    }

    public void setColors(int start, int end) {
        colorStart = start;
        colorEnd = end;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int w = getMeasuredWidth();
        int h = getMeasuredHeight();
        int size = Math.min(w, h);
        if (w != h) {
            setMeasuredDimension(size, size);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float padding = 10f * getResources().getDisplayMetrics().density;
        float size = Math.min(getWidth(), getHeight());
        oval.set(padding, padding, size - padding, size - padding);

        canvas.drawArc(oval, -90f, 360f, false, trackPaint);

        if (shownProgress > 0.002f) {
            float sweep = 360f * shownProgress;
            float cx = oval.centerX();
            float cy = oval.centerY();
            SweepGradient gradient = new SweepGradient(cx, cy,
                    new int[]{colorStart, colorEnd, colorEnd},
                    new float[]{0f, 0.55f, 1f});
            arcPaint.setShader(gradient);
            glowPaint.setShader(new LinearGradient(oval.left, oval.top, oval.right, oval.bottom,
                    colorStart, colorEnd, Shader.TileMode.CLAMP));
            canvas.drawArc(oval, -90f, sweep, false, glowPaint);
            canvas.drawArc(oval, -90f, sweep, false, arcPaint);
            arcPaint.setShader(null);
        }

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.drawText(centerText, cx, cy + textPaint.getTextSize() * 0.30f, textPaint);
        canvas.drawText(subText, cx, cy + textPaint.getTextSize() * 0.30f
                + subPaint.getTextSize() * 1.9f, subPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    public void setTrackColor(int color) {
        trackPaint.setColor(color);
        invalidate();
    }

    public void setTextColor(int color) {
        textPaint.setColor(color);
        invalidate();
    }

    public void setSubColor(int color) {
        subPaint.setColor(color);
        invalidate();
    }

    public static int alpha(int color, int a) {
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}
