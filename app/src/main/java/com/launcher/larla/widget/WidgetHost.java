package com.launcher.larla.widget;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.util.Log;

public class WidgetHost extends AppWidgetHost {

    private static final String TAG = "WidgetHost";

    public WidgetHost(Context context, int hostId) {
        super(context, hostId);
    }

    @Override
    protected AppWidgetHostView onCreateView(Context context, int appWidgetId, AppWidgetProviderInfo appWidget) {
        try {
            return new RoundedAppWidgetHostView(context);
        } catch (Exception e) {
            Log.e(TAG, "Error creating widget view for id=" + appWidgetId, e);
            AppWidgetHostView fallback = new AppWidgetHostView(context);
            return fallback;
        }
    }
}
