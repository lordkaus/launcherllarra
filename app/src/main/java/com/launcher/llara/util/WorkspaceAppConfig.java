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

public class WorkspaceAppConfig {
    private static final String TAG = "WorkspaceAppConfig";
    private final SharedPreferences prefs;
    private static final String KEY = "workspace_apps";

    private final List<ShortcutEntry> shortcuts = new ArrayList<>();
    private Map<String, Map<String, String>> gestures = new HashMap<>();
    private int pageCount = 1;

    public List<ShortcutEntry> getShortcuts() { return shortcuts; }
    public int getPageCount() { return pageCount; }
    public void setPageCount(int v) { pageCount = Math.max(1, Math.min(20, v)); save(); }

    public static class ShortcutEntry {
        public String pkg;
        public int page;
        public int x, y;

        public ShortcutEntry(String pkg, int page, int x, int y) {
            this.pkg = pkg;
            this.page = page;
            this.x = x;
            this.y = y;
        }
    }

    public WorkspaceAppConfig(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("workspace_apps", Context.MODE_PRIVATE);
        load();
    }

    public void add(String pkg, int page, int x, int y) {
        if (pkg == null || has(pkg)) return;
        shortcuts.add(new ShortcutEntry(pkg, page, x, y));
        save();
    }

    public void remove(String pkg) {
        if (pkg == null) return;
        for (int i = shortcuts.size() - 1; i >= 0; i--) {
            if (pkg.equals(shortcuts.get(i).pkg)) {
                shortcuts.remove(i);
                break;
            }
        }
        if (gestures.remove(pkg) != null) { /* purga gestos do atalho removido */ }
        save();
    }

    /** Remove mapeamentos que abrem o app desinstalado (app:pkg). */
    public void purgeAppTarget(String pkg) {
        if (pkg == null) return;
        String target = "app:" + pkg;
        boolean changed = false;
        for (Map.Entry<String, Map<String, String>> e : gestures.entrySet()) {
            Map<String, String> m = e.getValue();
            if (m == null) continue;
            for (String g : new java.util.ArrayList<>(m.keySet())) {
                if (target.equals(m.get(g))) { m.remove(g); changed = true; }
            }
        }
        if (changed) save();
    }

    public void move(String pkg, int page, int x, int y) {
        if (pkg == null) return;
        for (ShortcutEntry e : shortcuts) {
            if (pkg.equals(e.pkg)) {
                e.page = page;
                e.x = x;
                e.y = y;
                break;
            }
        }
        save();
    }

    public boolean has(String pkg) {
        if (pkg == null) return false;
        for (ShortcutEntry e : shortcuts) {
            if (pkg.equals(e.pkg)) return true;
        }
        return false;
    }

    public ShortcutEntry find(String pkg) {
        if (pkg == null) return null;
        for (ShortcutEntry e : shortcuts) {
            if (pkg.equals(e.pkg)) return e;
        }
        return null;
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

    public void reload() { load(); }

    private void load() {
        String json = prefs.getString(KEY, null);
        if (json == null) return;
        try {
            JSONObject o = new JSONObject(json);
            int tCount = Math.max(1, Math.min(20, o.optInt("pageCount", 1)));
            List<ShortcutEntry> tList = new ArrayList<>();
            JSONArray arr = o.optJSONArray("shortcuts");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    try {
                        JSONObject e = arr.optJSONObject(i);
                        if (e == null) continue;
                        String pkg = e.optString("pkg", null);
                        if (pkg == null || pkg.isEmpty()) continue;
                        tList.add(new ShortcutEntry(pkg, e.optInt("page", 0), e.optInt("x", 50), e.optInt("y", 50)));
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
            shortcuts.clear();
            shortcuts.addAll(tList);
            gestures = tGest;
            pageCount = tCount;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load workspace config", e);
        }
    }

    private void save() {
        try {
            JSONObject o = new JSONObject();
            int maxPage = 0;
            for (ShortcutEntry e : shortcuts) { if (e.page >= maxPage) maxPage = e.page + 1; }
            if (pageCount < Math.max(1, maxPage)) pageCount = Math.max(1, maxPage);
            o.put("pageCount", pageCount);
            JSONArray arr = new JSONArray();
            for (ShortcutEntry e : shortcuts) {
                JSONObject eo = new JSONObject();
                eo.put("pkg", e.pkg);
                eo.put("page", e.page);
                eo.put("x", e.x);
                eo.put("y", e.y);
                arr.put(eo);
            }
            o.put("shortcuts", arr);

            JSONObject g = new JSONObject();
            for (Map.Entry<String, Map<String, String>> e : gestures.entrySet()) {
                g.put(e.getKey(), new JSONObject(e.getValue()));
            }
            o.put("gestures", g);

            prefs.edit().putString(KEY, o.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save workspace config", e);
        }
    }
}
