package com.launcher.larla.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WidgetConfig {
    private static final String TAG = "WidgetConfig";
    public static final int CLOCK_ID = -1;

    private final SharedPreferences prefs;
    private static final String KEY = "widget_data";

    public int hostId = 1024;
    private int pageCount = 1;
    private final List<WidgetEntry> widgets = new ArrayList<>();

    public List<WidgetEntry> getWidgets() { return widgets; }
    public int getPageCount() { return pageCount; }
    public void setPageCount(int v) { pageCount = Math.max(1, Math.min(20, v)); save(); }

    public static class WidgetEntry {
        public int id;
        public int page;
        public int x;
        public int y;
        public int width = -1;
        public int height = -1;
        public int opacity = 100;

        public WidgetEntry(int id, int page, int x, int y) {
            this.id = id;
            this.page = page;
            this.x = x;
            this.y = y;
        }

        public WidgetEntry(int id, int page, int x, int y, int width, int height) {
            this.id = id;
            this.page = page;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public WidgetConfig(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("widget_config", Context.MODE_PRIVATE);
        load();
    }

    public boolean hasClock() {
        return findWidget(CLOCK_ID) != null;
    }

    public void addClock(int x, int y) {
        if (!hasClock()) {
            widgets.add(new WidgetEntry(CLOCK_ID, 0, x, y));
            save();
        }
    }

    public void removeClock() {
        removeWidget(CLOCK_ID);
    }

    private void load() {
        String json = prefs.getString(KEY, null);
        if (json == null) return;
        try {
            JSONObject o = new JSONObject(json);
            int tHost = o.optInt("hostId", 1024);
            int tCount = Math.max(1, Math.min(20, o.optInt("pageCount", 1)));
            List<WidgetEntry> tList = new ArrayList<>();
            JSONArray arr = o.optJSONArray("widgets");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    try {
                        JSONObject w = arr.optJSONObject(i);
                        if (w == null || !w.has("id")) continue;
                        WidgetEntry entry = new WidgetEntry(w.optInt("id", -2), w.optInt("page", 0), w.optInt("x", 50), w.optInt("y", 50));
                        if (entry.id == -2) continue;
                        entry.width = w.optInt("width", -1);
                        entry.height = w.optInt("height", -1);
                        entry.opacity = Math.max(0, Math.min(100, w.optInt("opacity", 100)));
                        tList.add(entry);
                    } catch (Exception ignored) { }
                }
            }
            hostId = tHost;
            pageCount = tCount;
            widgets.clear();
            widgets.addAll(tList);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load widget config", e);
        }
    }

    public void save() {
        try {
            JSONObject o = new JSONObject();
            o.put("hostId", hostId);
            int maxPage = 0;
            for (WidgetEntry w : widgets) { if (w.page >= maxPage) maxPage = w.page + 1; }
            if (pageCount < Math.max(1, maxPage)) pageCount = Math.max(1, maxPage);
            o.put("pageCount", pageCount);
            JSONArray arr = new JSONArray();
            for (WidgetEntry w : widgets) {
                JSONObject wo = new JSONObject();
                wo.put("id", w.id);
                wo.put("page", w.page);
                wo.put("x", w.x);
                wo.put("y", w.y);
                if (w.width > 0) wo.put("width", w.width);
                if (w.height > 0) wo.put("height", w.height);
                if (w.opacity < 100) wo.put("opacity", w.opacity);
                arr.put(wo);
            }
            o.put("widgets", arr);
            prefs.edit().putString(KEY, o.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save widget config", e);
        }
    }

    public void addWidget(int id, int page, int x, int y) {
        if (findWidget(id) != null) return;
        widgets.add(new WidgetEntry(id, page, x, y));
        save();
    }

    public void removeWidget(int id) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).id == id) widgets.remove(i);
        }
        save();
    }

    public void moveWidget(int id, int page, int x, int y) {
        for (WidgetEntry w : widgets) {
            if (w.id == id) { w.page = page; w.x = x; w.y = y; break; }
        }
        save();
    }

    public void resizeWidget(int id, int w, int h) {
        if (w <= 0 || h <= 0) return;
        for (WidgetEntry entry : widgets) {
            if (entry.id == id) { entry.width = w; entry.height = h; break; }
        }
        save();
    }

    public void setOpacity(int id, int opacity) {
        opacity = Math.max(0, Math.min(100, opacity));
        for (WidgetEntry entry : widgets) {
            if (entry.id == id) { entry.opacity = opacity; break; }
        }
        save();
    }

    public WidgetEntry findWidget(int id) {
        for (WidgetEntry w : widgets) {
            if (w.id == id) return w;
        }
        return null;
    }
}
