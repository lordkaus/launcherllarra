package com.launcher.llara.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DockConfig {
    private static final String TAG = "DockConfig";
    private final SharedPreferences prefs;
    private static final String KEY = "dock_config";

    private int bgColor = 0;
    private int marginDp = 24;
    private int iconSizeDp = 52;
    private int iconCount = 5;
    private String position = "bottom";
    private int pageCount = 1;
    private boolean hideNames = false;
    private List<List<String>> pages = new ArrayList<>();
    private Map<String, Map<String, String>> gestures = new HashMap<>();

    public DockConfig(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("dock_config", Context.MODE_PRIVATE);
        load();
    }

    public int getBgColor() { return bgColor; }
    public void setBgColor(int v) { bgColor = v; save(); }

    public int getMarginDp() { return marginDp; }
    public void setMarginDp(int v) { marginDp = Math.max(0, Math.min(64, v)); save(); }

    public int getIconSizeDp() { return iconSizeDp; }
    public void setIconSizeDp(int v) { iconSizeDp = Math.max(24, Math.min(96, v)); save(); }

    public int getIconCount() { return iconCount; }
    public void setIconCount(int v) { iconCount = Math.max(1, Math.min(12, v)); save(); }

    public String getPosition() { return position; }
    public void setPosition(String v) { if (!"top".equals(v) && !"left".equals(v) && !"right".equals(v)) v = "bottom"; position = v; save(); }

    public int getPageCount() { return pageCount; }
    public void setPageCount(int v) { pageCount = Math.max(1, Math.min(8, v)); save(); }

    public boolean getHideNames() { return hideNames; }
    public void setHideNames(boolean v) { hideNames = v; save(); }

    public List<List<String>> getPages() { return pages; }
    public Map<String, Map<String, String>> getGestures() { return gestures; }

    private void load() {
        String json = prefs.getString(KEY, null);
        if (json == null) return;
        try {
            JSONObject o = new JSONObject(json);
            int tBg = o.optInt("bgColor", 0);
            int tMargin = Math.max(0, Math.min(64, o.optInt("marginDp", 24)));
            int tSize = Math.max(24, Math.min(96, o.optInt("iconSizeDp", 52)));
            int tCount = Math.max(1, Math.min(12, o.optInt("iconCount", 5)));
            String tPos = o.optString("position", "bottom");
            if (!"top".equals(tPos) && !"left".equals(tPos) && !"right".equals(tPos)) tPos = "bottom";
            int tPages = Math.max(1, Math.min(8, o.optInt("pageCount", 1)));
            boolean tHide = o.optBoolean("hideNames", false);

            List<List<String>> tList = new ArrayList<>();
            JSONArray pa = o.optJSONArray("pages");
            if (pa != null) {
                for (int i = 0; i < pa.length() && i < 8; i++) {
                    try {
                        JSONArray pg = pa.optJSONArray(i);
                        if (pg == null) continue;
                        List<String> list = new ArrayList<>();
                        for (int j = 0; j < pg.length() && j < 12; j++) {
                            String s = pg.optString(j, null);
                            if (s != null && !s.isEmpty()) list.add(s);
                        }
                        tList.add(list);
                    } catch (Exception ignored) { }
                }
            }

            Map<String, Map<String, String>> tGest = new HashMap<>();
            JSONObject g = o.optJSONObject("gestures");
            if (g != null) {
                for (Iterator<String> it = g.keys(); it.hasNext(); ) {
                    try {
                        String pkg = it.next();
                        if (pkg == null) continue;
                        JSONObject gi = g.optJSONObject(pkg);
                        if (gi == null) continue;
                        Map<String, String> m = new HashMap<>();
                        for (Iterator<String> it2 = gi.keys(); it2.hasNext(); ) {
                            String k = it2.next();
                            String val = gi.optString(k, null);
                            if (k != null && val != null) m.put(k, val);
                        }
                        if (!m.isEmpty()) tGest.put(pkg, m);
                    } catch (Exception ignored) { }
                }
            }

            bgColor = tBg;
            marginDp = tMargin;
            iconSizeDp = tSize;
            iconCount = tCount;
            position = tPos;
            pageCount = tPages;
            hideNames = tHide;
            pages = tList;
            gestures = tGest;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load dock config", e);
        }
    }

    public void reload() { load(); }

    public void save() {
        try {
            JSONObject o = new JSONObject();
            o.put("bgColor", bgColor);
            o.put("marginDp", marginDp);
            o.put("iconSizeDp", iconSizeDp);
            o.put("iconCount", iconCount);
            o.put("position", position);
            o.put("pageCount", pageCount);
            o.put("hideNames", hideNames);

            JSONArray pa = new JSONArray();
            for (List<String> pg : pages) pa.put(new JSONArray(pg));
            o.put("pages", pa);

            JSONObject g = new JSONObject();
            for (Map.Entry<String, Map<String, String>> e : gestures.entrySet()) {
                g.put(e.getKey(), new JSONObject(e.getValue()));
            }
            o.put("gestures", g);

            prefs.edit().putString(KEY, o.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save dock config", e);
        }
    }

    public void initPages(List<String> allPkgs) {
        pages.clear();
        if (allPkgs.isEmpty()) return;
        for (int p = 0; p < pageCount; p++) {
            List<String> page = new ArrayList<>();
            int n = Math.min(iconCount, allPkgs.size());
            for (int i = 0; i < n; i++) {
                page.add(allPkgs.get((p * n + i) % allPkgs.size()));
            }
            pages.add(page);
        }
        save();
    }

    public List<String> getPage(int idx) {
        if (idx < 0 || idx >= pages.size()) return new ArrayList<>();
        return pages.get(idx);
    }

    public void removeFromPage(int pageIdx, int itemIdx) {
        if (pageIdx < 0 || pageIdx >= pages.size()) return;
        List<String> pg = pages.get(pageIdx);
        if (itemIdx < 0 || itemIdx >= pg.size()) return;
        pg.remove(itemIdx);
        save();
    }

    public void addToPage(int pageIdx, String pkg) {
        if (pkg == null || pageIdx < 0 || pageIdx >= pages.size()) return;
        List<String> pg = pages.get(pageIdx);
        if (pg.contains(pkg) || pg.size() >= Math.max(1, Math.min(12, iconCount))) return;
        pg.add(pkg);
        save();
    }

    public void removePackageEverywhere(String pkg) {
        if (pkg == null) return;
        boolean changed = false;
        for (List<String> pg : pages) {
            while (pg.remove(pkg)) changed = true;
        }
        Map<String, Map<String, String>> g = gestures;
        if (g.remove(pkg) != null) changed = true;
        if (changed) save();
    }

    /** Remove mapeamentos que abrem o app desinstalado (app:pkg). */
    public void purgeAppTarget(String pkg) {
        if (pkg == null) return;
        String target = "app:" + pkg;
        boolean changed = false;
        for (Map.Entry<String, Map<String, String>> e : gestures.entrySet()) {
            Map<String, String> m = e.getValue();
            if (m == null) continue;
            for (String gk : new java.util.ArrayList<>(m.keySet())) {
                if (target.equals(m.get(gk))) { m.remove(gk); changed = true; }
            }
        }
        if (changed) save();
    }

    public String getGesture(String pkg, String gesture) {
        Map<String, String> m = gestures.get(pkg);
        if (m == null) return "none";
        String a = m.get(gesture);
        return a != null ? a : "none";
    }

    public void setGesture(String pkg, String gesture, String action) {
        Map<String, String> m = gestures.get(pkg);
        if (m == null) { m = new HashMap<>(); gestures.put(pkg, m); }
        if ("none".equals(action)) m.remove(gesture);
        else m.put(gesture, action);
        if (m.isEmpty()) gestures.remove(pkg);
        save();
    }
}
