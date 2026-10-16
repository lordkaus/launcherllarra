package com.launcher.llara.view;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class DockEditorView extends LinearLayout {

    private int iconCount = 5;
    private int iconSizeDp = 52;
    private int marginDp = 24;
    private int bgColor = 0;
    private String position = "bottom";
    private boolean hideNames = false;

    private List<Drawable> icons = new ArrayList<>();
    private List<String> iconNames = new ArrayList<>();
    private List<Integer> iconColors = new ArrayList<>();
    private Callback callback;

    public interface Callback {
        void onIconTapped(int index);
    }

    public DockEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
    }

    public void setPreviewData(List<Drawable> newIcons, List<String> newNames) {
        setPreviewData(newIcons, newNames, null);
    }

    public void setPreviewData(List<Drawable> newIcons, List<String> newNames, List<Integer> colors) {
        icons.clear();
        iconNames.clear();
        iconColors.clear();
        if (newIcons != null) icons.addAll(newIcons);
        if (newNames != null) iconNames.addAll(newNames);
        if (colors != null) iconColors.addAll(colors);
        rebuild();
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
            int idx = findIconAt(e.getX(), e.getY());
            if (idx >= 0 && callback != null) {
                callback.onIconTapped(idx);
            }
        }
        return true;
    }

    private int findIconAt(float x, float y) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (x >= child.getLeft() && x <= child.getRight() && y >= child.getTop() && y <= child.getBottom()) {
                return i;
            }
        }
        return -1;
    }

    public void rebuild() {
        removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int sizePx = (int) (iconSizeDp * density);
        int pad = (int) (8 * density);
        boolean vertical = position.equals("left") || position.equals("right");

        int n = Math.min(icons.size(), iconCount);

        float screenDim = vertical ?
                getResources().getDisplayMetrics().heightPixels :
                getResources().getDisplayMetrics().widthPixels;

        int desiredMarginPx = (int) (marginDp * density / 2);
        int marginPx;
        if (n <= 1) {
            marginPx = Math.min(desiredMarginPx, (int) ((screenDim - sizePx) / 2f));
        } else {
            float maxMarginPx = (screenDim + (n - 1) * 4 * density - n * sizePx) / (2f * n);
            marginPx = (int) Math.max(0, Math.min(desiredMarginPx, maxMarginPx));
        }

        setOrientation(vertical ? VERTICAL : HORIZONTAL);
        setGravity(Gravity.CENTER);

        if (vertical) setPadding(pad, marginPx, pad, marginPx);
        else setPadding(marginPx, pad, marginPx, pad);
        setBackgroundColor(bgColor);

        int shape = 0, labelSp = 11;
        int[] corners = null;
        int glyphPct = 70, platePct = 100;
        try {
            com.launcher.llara.util.SettingsManager sm =
                    new com.launcher.llara.util.SettingsManager(getContext());
            shape = sm.getIconShape(com.launcher.llara.util.SettingsManager.SURF_DOCK);
            labelSp = sm.getLabelSize(com.launcher.llara.util.SettingsManager.SURF_DOCK);
            if (shape == com.launcher.llara.util.IconShape.CUSTOM) corners = sm.getCorners(com.launcher.llara.util.SettingsManager.SURF_DOCK);
            glyphPct = sm.getGlyphPct(com.launcher.llara.util.SettingsManager.SURF_DOCK);
            platePct = sm.getPlatePct(com.launcher.llara.util.SettingsManager.SURF_DOCK);
        } catch (Exception ignored) { }

        for (int i = 0; i < n; i++) {
            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(vertical ? HORIZONTAL : VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, (int)(4 * density), 0, (int)(4 * density));

            ImageView iv = new ImageView(getContext());
            iv.setImageDrawable(icons.get(i));
            iv.setLayoutParams(new LayoutParams(sizePx, sizePx));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int plate = (i < iconColors.size()) ? iconColors.get(i)
                    : com.launcher.llara.util.IconStyle.plateColor(null);
            com.launcher.llara.util.IconStyle.applyPlate(iv, shape, sizePx, plate, corners,
                    glyphPct, platePct);
            item.addView(iv);

            if (!hideNames && i < iconNames.size()) {
                TextView tv = new TextView(getContext());
                tv.setText(iconNames.get(i));
                tv.setTextSize(labelSp);
                tv.setTextColor(0xCCFFFFFF);
                tv.setShadowLayer(2, 1, 1, 0x80000000);
                tv.setMaxLines(1);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tv.setGravity(Gravity.CENTER);
                LayoutParams nameLp = new LayoutParams(sizePx, LayoutParams.WRAP_CONTENT);
                nameLp.topMargin = (int)(2 * density);
                tv.setLayoutParams(nameLp);
                item.addView(tv);
            }

            LayoutParams itemLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            int gap = marginPx * 2 - (int)(4 * density);
            if (vertical) { if (i > 0) itemLp.topMargin = gap; }
            else { if (i > 0) itemLp.leftMargin = gap; }
            item.setLayoutParams(itemLp);
            addView(item);
        }
        requestLayout();
    }

    public void setCallback(Callback cb) { this.callback = cb; }

    public void cleanupIcons() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout item = (LinearLayout) child;
                for (int j = 0; j < item.getChildCount(); j++) {
                    View inner = item.getChildAt(j);
                    if (inner instanceof ImageView) {
                        ((ImageView) inner).setImageDrawable(null);
                    }
                }
            }
        }
        icons.clear();
        iconNames.clear();
        iconColors.clear();
    }

    public void updateValues(int count, int size, int margin, int color, String pos, boolean hide) {
        iconCount = count;
        iconSizeDp = size;
        marginDp = margin;
        bgColor = color;
        position = pos != null ? pos : "bottom";
        hideNames = hide;
        rebuild();
    }

    public int getIconCount() { return iconCount; }
    public int getIconSizeDp() { return iconSizeDp; }
    public int getMarginDp() { return marginDp; }
    public int getBgColor() { return bgColor; }
    public String getPosition() { return position; }
    public int getPageCount() { return 1; }
    public boolean getHideNames() { return hideNames; }
}
