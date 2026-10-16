package com.launcher.llara.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FolderConfig {
    private static final String TAG = "FolderConfig";
    private final SharedPreferences prefs;
    private static final String KEY = "folders";

    public static final String LOC_WORKSPACE = "ws";
    public static final String LOC_DRAWER = "drawer";

    public static class Folder {
        public String id;
        public String name;
        public String location;
        public int page;
        public int x, y;
        public final List<String> pkgs = new ArrayList<>();
        public int bgColor = 0x33000000;
        public int cornerRadiusDp = 16;
        public boolean showName = true;
        public int columns = 4;
        public int iconSizePct = 100;

        public Folder(String id, String name, String location) {
            this.id = id;
            this.name = name;
            this.location = location;
        }
    }

    private final List<Folder> folders = new ArrayList<>();

    public FolderConfig(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("folders", Context.MODE_PRIVATE);
        load();
    }

    public List<Folder> getFolders() { return folders; }

    public Folder find(String id) {
        if (id == null) return null;
        for (Folder f : folders) {
            if (id.equals(f.id)) return f;
        }
        return null;
    }

    public List<Folder> byLocation(String location) {
        List<Folder> out = new ArrayList<>();
        for (Folder f : folders) {
            if (location.equals(f.location)) out.add(f);
        }
        return out;
    }

    public Folder create(String name, String location, int page, int x, int y, List<String> pkgs) {
        Folder f = new Folder(UUID.randomUUID().toString(), name, location);
        f.page = page;
        f.x = x;
        f.y = y;
        if (pkgs != null) {
            for (String p : pkgs) {
                if (p != null && !p.isEmpty() && !f.pkgs.contains(p)) f.pkgs.add(p);
            }
        }
        folders.add(f);
        save();
        return f;
    }

    public boolean addApp(String id, String pkg) {
        Folder f = find(id);
        if (f == null || pkg == null) return false;
        if (f.pkgs.contains(pkg)) return false;
        f.pkgs.add(pkg);
        save();
        return true;
    }

    public boolean removeApp(String id, String pkg) {
        Folder f = find(id);
        if (f == null) return false;
        boolean removed = f.pkgs.remove(pkg);
        if (removed) {
            if (f.pkgs.isEmpty()) {
                folders.remove(f);
            }
            save();
        }
        return removed;
    }

    public boolean rename(String id, String name) {
        Folder f = find(id);
        if (f == null) return false;
        f.name = name != null ? name : "";
        save();
        return true;
    }

    public boolean move(String id, int page, int x, int y) {
        Folder f = find(id);
        if (f == null) return false;
        f.page = page;
        f.x = x;
        f.y = y;
        save();
        return true;
    }

    public boolean dissolve(String id) {
        Folder f = find(id);
        if (f == null) return false;
        folders.remove(f);
        save();
        return true;
    }

    /** Remove pkg de todas as pastas; apaga pastas que ficarem vazias. Retorna true se mudou algo. */
    public boolean purgePackage(String pkg) {
        if (pkg == null) return false;
        boolean changed = false;
        for (int i = folders.size() - 1; i >= 0; i--) {
            Folder f = folders.get(i);
            if (f.pkgs.remove(pkg)) {
                changed = true;
                if (f.pkgs.isEmpty()) folders.remove(i);
            }
        }
        if (changed) save();
        return changed;
    }

    public void reload() { load(); }

    public void saveNow() { save(); }

    private void load() {
        String json = prefs.getString(KEY, null);
        if (json == null) return;
        try {
            List<Folder> tList = new ArrayList<>();
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                try {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String id = o.optString("id", null);
                    if (id == null || id.isEmpty()) continue;
                    Folder f = new Folder(id, o.optString("name", ""),
                            o.optString("loc", LOC_WORKSPACE));
                    if (!LOC_DRAWER.equals(f.location)) f.location = LOC_WORKSPACE;
                    f.page = o.optInt("page", 0);
                    f.x = o.optInt("x", 50);
                    f.y = o.optInt("y", 50);
                    f.bgColor = o.optInt("bg", 0x33000000);
                    f.cornerRadiusDp = Math.max(0, Math.min(48, o.optInt("radius", 16)));
                    f.showName = o.optBoolean("showName", true);
                    f.columns = Math.max(2, Math.min(6, o.optInt("cols", 4)));
                    f.iconSizePct = Math.max(50, Math.min(150, o.optInt("iconPct", 100)));
                    JSONArray pa = o.optJSONArray("pkgs");
                    if (pa != null) {
                        for (int j = 0; j < pa.length(); j++) {
                            String p = pa.optString(j, null);
                            if (p != null && !p.isEmpty() && !f.pkgs.contains(p)) f.pkgs.add(p);
                        }
                    }
                    tList.add(f);
                } catch (Exception ignored) { }
            }
            folders.clear();
            folders.addAll(tList);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load folders", e);
        }
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Folder f : folders) {
                JSONObject o = new JSONObject();
                o.put("id", f.id);
                o.put("name", f.name);
                o.put("loc", f.location);
                o.put("page", f.page);
                o.put("x", f.x);
                o.put("y", f.y);
                o.put("bg", f.bgColor);
                o.put("radius", f.cornerRadiusDp);
                o.put("showName", f.showName);
                o.put("cols", f.columns);
                o.put("iconPct", f.iconSizePct);
                JSONArray pa = new JSONArray();
                for (String p : f.pkgs) pa.put(p);
                o.put("pkgs", pa);
                arr.put(o);
            }
            prefs.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save folders", e);
        }
    }
}
