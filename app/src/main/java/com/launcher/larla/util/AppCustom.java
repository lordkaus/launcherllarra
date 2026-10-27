package com.launcher.larla.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Personalizações por app: nome, oculto, forma e cor do prato. */
public class AppCustom {

    private static final String TAG = "AppCustom";
    private static AppCustom instance;

    private final SharedPreferences prefs;
    private static final String KEY = "app_custom";

    private AppCustom(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences("app_custom", Context.MODE_PRIVATE);
    }

    public static synchronized void init(Context context) {
        if (instance == null) instance = new AppCustom(context);
    }

    public static AppCustom get() { return instance; }

    private JSONObject all() {
        try {
            String json = prefs.getString(KEY, null);
            if (json != null) return new JSONObject(json);
        } catch (Exception e) {
            Log.w(TAG, "read failed", e);
        }
        return new JSONObject();
    }

    private JSONObject entryOf(JSONObject all, String pkg, boolean create) {
        JSONObject e = all.optJSONObject(pkg);
        if (e == null && create) {
            e = new JSONObject();
            try { all.put(pkg, e); } catch (Exception ignored) { }
        }
        return e;
    }

    private void save(JSONObject all) {
        try { prefs.edit().putString(KEY, all.toString()).apply(); }
        catch (Exception e) { Log.w(TAG, "save failed", e); }
    }

    private static boolean isEmptyEntry(JSONObject e) {
        return e.optString("name", "").isEmpty()
            && !e.optBoolean("hidden", false)
            && e.optInt("shape", -1) < 0
            && e.optInt("color", 0) == 0;
    }

    public String label(String pkg, String fallback) {
        if (pkg == null) return fallback;
        JSONObject n = all().optJSONObject(pkg);
        if (n != null) {
            String custom = n.optString("name", "");
            if (!custom.isEmpty()) return custom;
        }
        return fallback;
    }

    public void setLabel(String pkg, String name) {
        if (pkg == null) return;
        JSONObject all = all();
        if (name == null || name.trim().isEmpty()) {
            JSONObject e = all.optJSONObject(pkg);
            if (e != null) {
                e.remove("name");
                if (isEmptyEntry(e)) all.remove(pkg);
                save(all);
            }
            return;
        }
        JSONObject e = entryOf(all, pkg, true);
        try { e.put("name", name.trim()); } catch (Exception ignored) { }
        save(all);
    }

    public boolean isHidden(String pkg) {
        if (pkg == null) return false;
        JSONObject e = all().optJSONObject(pkg);
        return e != null && e.optBoolean("hidden", false);
    }

    public void setHidden(String pkg, boolean hidden) {
        if (pkg == null) return;
        JSONObject all = all();
        JSONObject e = entryOf(all, pkg, hidden);
        if (e == null) return;
        try {
            if (hidden) e.put("hidden", true);
            else {
                e.remove("hidden");
                if (isEmptyEntry(e)) all.remove(pkg);
            }
        } catch (Exception ignored) { }
        save(all);
    }

    public List<String> hiddenPkgs() {
        List<String> out = new ArrayList<>();
        JSONObject all = all();
        Iterator<String> it = all.keys();
        while (it.hasNext()) {
            String pkg = it.next();
            JSONObject e = all.optJSONObject(pkg);
            if (e != null && e.optBoolean("hidden", false)) out.add(pkg);
        }
        return out;
    }

    /** -1 = automático (segue global). */
    public int shapeOf(String pkg, int def) {
        if (pkg == null) return def;
        JSONObject e = all().optJSONObject(pkg);
        if (e == null) return def;
        int s = e.optInt("shape", -1);
        return s < 0 ? def : IconShape.clamp(s);
    }

    public void setShape(String pkg, int shape) {
        if (pkg == null) return;
        JSONObject all = all();
        JSONObject e = entryOf(all, pkg, shape >= 0);
        if (e == null) return;
        try {
            if (shape < 0) {
                e.remove("shape");
                if (isEmptyEntry(e)) all.remove(pkg);
            } else {
                e.put("shape", IconShape.clamp(shape));
            }
        } catch (Exception ignored) { }
        save(all);
    }

    /** 0 = automático (cor da borda). */
    public int colorOf(String pkg, int defColor) {
        if (pkg == null) return defColor;
        JSONObject e = all().optJSONObject(pkg);
        if (e == null) return defColor;
        int c = e.optInt("color", 0);
        return c == 0 ? defColor : c;
    }

    public void setColor(String pkg, int color) {
        if (pkg == null) return;
        JSONObject all = all();
        JSONObject e = entryOf(all, pkg, color != 0);
        if (e == null) return;
        try {
            if (color == 0) {
                e.remove("color");
                if (isEmptyEntry(e)) all.remove(pkg);
            } else {
                e.put("color", color);
            }
        } catch (Exception ignored) { }
        save(all);
    }

    public void resetIcon(String pkg) {
        if (pkg == null) return;
        JSONObject all = all();
        JSONObject e = all.optJSONObject(pkg);
        if (e == null) return;
        e.remove("shape");
        e.remove("color");
        try {
            if (isEmptyEntry(e)) all.remove(pkg);
            save(all);
        } catch (Exception ignored) { }
    }

    public void purgePackage(String pkg) {
        if (pkg == null) return;
        JSONObject all = all();
        if (all.optJSONObject(pkg) != null) {
            all.remove(pkg);
            save(all);
        }
    }
}
