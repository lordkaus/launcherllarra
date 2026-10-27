package com.launcher.larla.widget;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

public class RoundedAppWidgetHostView extends AppWidgetHostView {

    private float cornerRadiusPx;

    public RoundedAppWidgetHostView(Context context) {
        super(context);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                if (w > 0 && h > 0 && cornerRadiusPx > 0) {
                    outline.setRoundRect(0, 0, w, h, cornerRadiusPx);
                }
            }
        });
    }

    public void setCornerRadiusPx(float px) {
        if (cornerRadiusPx != px) {
            cornerRadiusPx = px;
            invalidateOutline();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (cornerRadiusPx > 0) {
            invalidateOutline();
        }
    }
}
