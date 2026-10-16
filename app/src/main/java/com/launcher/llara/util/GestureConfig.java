package com.launcher.llara.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

/** Ações globais da área de trabalho: gesto -> ação. */
public class GestureConfig {
    private static final String TAG = "GestureConfig";
    private final SharedPreferences prefs;
    private static final String KEY = "global_gestures";

    public static final String SWIPE_UP = "swipe_up";
    public static final String SWIPE_DOWN = "swipe_down";
    public static final String SWIPE_LEFT = "swipe_left";
    public static final String SWIPE_RIGHT = "swipe_right";
    public static final String DOUBLE_TAP = "double_tap";
    public static final String DT_DRAG_UP = "dt_drag_up";
    public static final String DT_DRAG_DOWN = "dt_drag_down";
    public static final String TWO_UP = "two_up";
    public static final String TWO_DOWN = "two_down";
    public static final String PINCH = "pinch";
    public static final String ZOOM = "zoom";
    public static final String ROT_CCW = "rot_ccw";
    public static final String ROT_CW = "rot_cw";
    public static final String HOME_PRESS = "home_press";
    public static final String SHAKE = "shake";

    public static final String ACT_NONE = "none";
    public static final String ACT_DRAWER = "open_drawer";
    public static final String ACT_SETTINGS = "open_settings";
    public static final String ACT_WALLPAPER = "open_wallpaper";
    public static final String ACT_WIDGETS = "open_widgets";
    public static final String ACT_NEXT_PAGE = "next_page";
    public static final String ACT_PREV_PAGE = "prev_page";
    public static final String ACT_TOGGLE_DOCK = "toggle_dock";
    public static final String ACT_APP_PREFIX = "app:";

    private static final String KEY_HOME_ONLY_HOME = "home_only_home";

    public GestureConfig(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("gestures", Context.MODE_PRIVATE);
        if (!prefs.contains(KEY)) {
            try {
                JSONObject o = new JSONObject();
                o.put(SWIPE_UP, ACT_DRAWER);
                prefs.edit().putString(KEY, o.toString()).apply();
            } catch (Exception e) {
                Log.w(TAG, "defaults failed", e);
            }
        }
    }

    public String getAction(String gesture) {
        if (gesture == null) return ACT_NONE;
        try {
            String json = prefs.getString(KEY, null);
            if (json == null) {
                return SWIPE_UP.equals(gesture) ? ACT_DRAWER : ACT_NONE;
            }
            return new JSONObject(json).optString(gesture, SWIPE_UP.equals(gesture) ? ACT_DRAWER : ACT_NONE);
        } catch (Exception e) {
            return ACT_NONE;
        }
    }

    public void setAction(String gesture, String action) {
        if (gesture == null) return;
        if (action == null) action = ACT_NONE;
        try {
            String json = prefs.getString(KEY, null);
            JSONObject o = json != null ? new JSONObject(json) : new JSONObject();
            if (ACT_NONE.equals(action)) o.remove(gesture);
            else o.put(gesture, action);
            prefs.edit().putString(KEY, o.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "save failed", e);
        }
    }

    public boolean isHomeOnlyHome() {
        return prefs.getBoolean(KEY_HOME_ONLY_HOME, true);
    }

    public void setHomeOnlyHome(boolean v) {
        prefs.edit().putBoolean(KEY_HOME_ONLY_HOME, v).apply();
    }

    public void reload() { /* prefs lidas sob demanda */ }

    public static boolean isAppAction(String action) {
        return action != null && action.startsWith(ACT_APP_PREFIX) && action.length() > ACT_APP_PREFIX.length();
    }

    public static String appOf(String action) {
        return isAppAction(action) ? action.substring(ACT_APP_PREFIX.length()) : null;
    }
}
