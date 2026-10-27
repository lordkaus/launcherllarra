package com.launcher.larla.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class LongPressIndicator extends View {

    private static final int COLOR_OUTLINE = 0xFF000000;
    private static final int COLOR_FILL = 0xFF00CC00;

    private static final float STROKE = 7f;
    private static final float OUTLINE_STROKE = STROKE + 4f;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private float progress = 0f;
    private ValueAnimator animator;
    private Runnable onComplete;
    private boolean running = false;

    public LongPressIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        fillPaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStrokeWidth(STROKE);
        fillPaint.setColor(COLOR_FILL);
        fillPaint.setStrokeCap(Paint.Cap.ROUND);

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(OUTLINE_STROKE);
        outlinePaint.setColor(COLOR_OUTLINE);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);

        setVisibility(INVISIBLE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - 16f;
        float ir = r - STROKE / 2f;
        float sweep = progress * 360f;

        arcBounds.set(cx - ir, cy - ir, cx + ir, cy + ir);

        if (sweep > 0) {
            canvas.drawArc(arcBounds, -90f, sweep, false, outlinePaint);
            canvas.drawArc(arcBounds, -90f, sweep, false, fillPaint);
        }
    }

    public void setFillColor(int color) {
        fillPaint.setColor(color);
        invalidate();
    }

    public void start(Runnable onComplete) {
        if (running) return;
        running = true;
        progress = 0f;
        setAlpha(1f);
        setVisibility(VISIBLE);
        this.onComplete = onComplete;

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(400);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
            if (progress >= 1f) done();
        });
        animator.start();
    }

    public void cancel() {
        if (animator != null && animator.isRunning()) animator.cancel();
        running = false;
        progress = 0f;
        setVisibility(INVISIBLE);
        setAlpha(1f);
        invalidate();
    }

    private void done() {
        running = false;
        progress = 0f;
        setVisibility(INVISIBLE);
        setAlpha(1f);
        invalidate();
        if (onComplete != null) onComplete.run();
    }

    public boolean isRunning() { return running; }
}
