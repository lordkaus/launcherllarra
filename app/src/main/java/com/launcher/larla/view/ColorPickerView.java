package com.launcher.larla.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ColorPickerView extends View {

    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }

    private OnColorChangedListener listener;
    private float[] hsv = new float[]{0f, 1f, 1f};
    private float cursorX, cursorY;

    private final Paint huePaint = new Paint();
    private final Paint satPaint = new Paint();
    private final Paint basePaint = new Paint();
    private final Paint blackPaint = new Paint();
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursorBorder = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int[] hueColors = new int[360];
    private final RectF tempRect = new RectF();
    private LinearGradient hueGradient;
    private LinearGradient satGradient;
    private LinearGradient blackGradient;
    private int lastGradientWidth = 0;
    private float lastSatTop = -1, lastSatHeight = -1;

    private static final int HUE_HEIGHT_DP = 32;
    private static final float CURSOR_RADIUS_DP = 10f;

    public ColorPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        cursorPaint.setColor(Color.WHITE);
        cursorPaint.setStyle(Paint.Style.FILL);
        cursorBorder.setColor(Color.BLACK);
        cursorBorder.setStyle(Paint.Style.STROKE);
        cursorBorder.setStrokeWidth(2f);
    }

    public void setOnColorChangedListener(OnColorChangedListener l) {
        this.listener = l;
    }

    public int getColor() {
        return Color.HSVToColor(hsv);
    }

    public void setColor(int color) {
        Color.colorToHSV(color, hsv);
        updateCursorFromHSV();
        invalidate();
    }

    private void updateCursorFromHSV() {
        float w = getWidth();
        float hueH = getHueBarHeight();
        float areaH = getHeight() - hueH - 8f;
        cursorX = hsv[0] / 360f * w;
        cursorY = hueH + 4f + (1f - hsv[2]) * areaH;
    }

    private float getHueBarHeight() {
        return HUE_HEIGHT_DP * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float hueH = getHueBarHeight();
        float areaTop = hueH + 4f;
        float areaH = h - areaTop;

        // hue bar — cache gradient
        int wInt = (int) w;
        if (hueGradient == null || lastGradientWidth != wInt) {
            for (int i = 0; i < 360; i++) {
                hueColors[i] = Color.HSVToColor(255, new float[]{i, 1f, 1f});
            }
            hueGradient = new LinearGradient(0, 0, w, 0, hueColors, null, Shader.TileMode.CLAMP);
            huePaint.setShader(hueGradient);
            lastGradientWidth = wInt;
        }
        canvas.drawRect(0, 0, w, hueH, huePaint);

        // saturation/brightness area
        float[] baseHue = new float[]{hsv[0], 1f, 1f};
        int baseColor = Color.HSVToColor(baseHue);

        // base color layer
        basePaint.setColor(baseColor);
        canvas.drawRect(0, areaTop, w, areaTop + areaH, basePaint);

        if (satGradient == null || lastSatTop != areaTop || lastSatHeight != areaH) {
            satGradient = new LinearGradient(0, areaTop, 0, areaTop + areaH,
                    Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP);
            blackGradient = new LinearGradient(0, areaTop, w, areaTop,
                    Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
            lastSatTop = areaTop;
            lastSatHeight = areaH;
        }
        satPaint.setShader(satGradient);
        canvas.drawRect(0, areaTop, w, areaTop + areaH, satPaint);

        blackPaint.setShader(blackGradient);
        canvas.drawRect(0, areaTop, w, areaTop + areaH, blackPaint);

        // cursor
        float r = CURSOR_RADIUS_DP * getResources().getDisplayMetrics().density;
        canvas.drawCircle(cursorX, cursorY, r, cursorPaint);
        canvas.drawCircle(cursorX, cursorY, r, cursorBorder);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = (int) (w * 0.6f);
        setMeasuredDimension(w, h);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = Math.max(0, Math.min(getWidth(), event.getX()));
        float y = Math.max(0, Math.min(getHeight(), event.getY()));

        float hueH = getHueBarHeight();
        float areaTop = hueH + 4f;
        float areaH = getHeight() - areaTop;

        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            if (y < hueH) {
                // touching hue bar
                hsv[0] = x / getWidth() * 360f;
            } else if (y >= areaTop) {
                // touching saturation/brightness area
                hsv[1] = x / getWidth();
                hsv[2] = 1f - (y - areaTop) / areaH;
                hsv[1] = Math.max(0f, Math.min(1f, hsv[1]));
                hsv[2] = Math.max(0f, Math.min(1f, hsv[2]));
            }
            cursorX = x;
            cursorY = y;
            invalidate();
            if (listener != null) listener.onColorChanged(getColor());
            return true;
        }
        return super.onTouchEvent(event);
    }
}
