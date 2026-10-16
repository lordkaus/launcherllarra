package com.launcher.llara.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.launcher.llara.R;
import com.launcher.llara.util.GestureConfig;
import com.launcher.llara.view.ModernSheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GestureSettingsActivity extends Activity {

    private GestureConfig gestures;
    private LinearLayout list;
    private float density;

    private static class GestureRow {
        final String key;
        final String icon;
        final int titleRes;
        final int subRes;
        GestureRow(String key, String icon, int titleRes, int subRes) {
            this.key = key;
            this.icon = icon;
            this.titleRes = titleRes;
            this.subRes = subRes;
        }
    }

    private static final GestureRow[] ROWS = {
        new GestureRow(GestureConfig.SWIPE_UP, "↑", R.string.gesture_swipe_up, 0),
        new GestureRow(GestureConfig.SWIPE_DOWN, "↓", R.string.gesture_swipe_down, 0),
        new GestureRow(GestureConfig.SWIPE_LEFT, "←", R.string.gesture_swipe_left, 0),
        new GestureRow(GestureConfig.SWIPE_RIGHT, "→", R.string.gesture_swipe_right, 0),
        new GestureRow(GestureConfig.DOUBLE_TAP, "👆", R.string.gesture_double_tap, 0),
        new GestureRow(GestureConfig.DT_DRAG_UP, "↑", R.string.gesture_dt_drag_up, 0),
        new GestureRow(GestureConfig.DT_DRAG_DOWN, "↓", R.string.gesture_dt_drag_down, 0),
        new GestureRow(GestureConfig.TWO_UP, "✌️", R.string.gesture_two_up, 0),
        new GestureRow(GestureConfig.TWO_DOWN, "✌️", R.string.gesture_two_down, 0),
        new GestureRow(GestureConfig.PINCH, "🤏", R.string.gesture_pinch, R.string.gesture_pinch_sub),
        new GestureRow(GestureConfig.ZOOM, "🔍", R.string.gesture_zoom, R.string.gesture_zoom_sub),
        new GestureRow(GestureConfig.ROT_CCW, "🔄", R.string.gesture_rot_ccw, 0),
        new GestureRow(GestureConfig.ROT_CW, "🔃", R.string.gesture_rot_cw, 0),
        new GestureRow(GestureConfig.SHAKE, "📳", R.string.gesture_shake, R.string.gesture_shake_sub),
    };

    private static class ActionDef {
        final String id;
        final String icon;
        final int labelRes;
        ActionDef(String id, String icon, int labelRes) {
            this.id = id;
            this.icon = icon;
            this.labelRes = labelRes;
        }
    }

    private static final ActionDef[] ACTIONS = {
        new ActionDef(GestureConfig.ACT_NONE, "🚫", R.string.action_none),
        new ActionDef(GestureConfig.ACT_DRAWER, "📂", R.string.action_open_drawer),
        new ActionDef(GestureConfig.ACT_SETTINGS, "⚙️", R.string.action_open_settings),
        new ActionDef(GestureConfig.ACT_WALLPAPER, "🖼️", R.string.action_open_wallpaper),
        new ActionDef(GestureConfig.ACT_WIDGETS, "🧩", R.string.action_open_widgets),
        new ActionDef(GestureConfig.ACT_NEXT_PAGE, "⏭️", R.string.action_next_page),
        new ActionDef(GestureConfig.ACT_PREV_PAGE, "⏮️", R.string.action_prev_page),
        new ActionDef(GestureConfig.ACT_TOGGLE_DOCK, "⬛", R.string.action_toggle_dock),
        new ActionDef("app:", "📱", R.string.action_open_app),
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        gestures = new GestureConfig(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121212);
        root.setFitsSystemWindows(true);
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF1A1A1A);
        bar.setPadding((int) (4 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        back.setTextColor(0xFF00D4FF);
        back.setGravity(Gravity.CENTER);
        back.setClickable(true);
        back.setFocusable(true);
        back.setBackgroundResource(selectableItemBackgroundBorderless());
        back.setLayoutParams(new LinearLayout.LayoutParams((int) (48 * density), (int) (48 * density)));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText(R.string.gestures_inputs);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(null, Typeface.BOLD);
        bar.addView(title);

        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * density);
        list.setPadding(pad, (int) (8 * density), pad, (int) (24 * density));
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rebuild();
    }

    private int selectableItemBackgroundBorderless() {
        TypedValue t = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, t, true);
        return t.resourceId;
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ModernSheet.dismissAll();
    }

    private void rebuild() {
        list.removeAllViews();
        addSection(getString(R.string.gest_actions_title));
        addHomeRow();
        addHomeOnlyRow();
        addDivider();
        addSection(getString(R.string.gest_section));
        for (GestureRow r : ROWS) {
            addGestureRow(r);
        }
    }

    private void addSection(String text) {
        TextView s = new TextView(this);
        s.setText(text);
        s.setTextColor(0xFF00D4FF);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        s.setTypeface(null, Typeface.BOLD);
        s.setPadding(0, (int) (16 * density), 0, (int) (4 * density));
        list.addView(s);
    }

    private void addDivider() {
        View d = new View(this);
        d.setBackgroundColor(0xFF2A2A2A);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.topMargin = (int) (12 * density);
        lp.bottomMargin = (int) (4 * density);
        d.setLayoutParams(lp);
        list.addView(d);
    }

    private LinearLayout baseRow(String icon, String title, String subtitle, boolean chevron) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight((int) (64 * density));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackgroundResource(selectableItemBackground());
        row.setPadding(0, (int) (8 * density), 0, (int) (8 * density));

        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.rightMargin = (int) (16 * density);
        ic.setLayoutParams(ilp);
        row.addView(ic);

        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tx);

        TextView tt = new TextView(this);
        tt.setText(title);
        tt.setTextColor(Color.WHITE);
        tt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tx.addView(tt);

        if (subtitle != null) {
            TextView st = new TextView(this);
            st.setText(subtitle);
            st.setTextColor(0xFFAAAAAA);
            st.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tx.addView(st);
        }

        if (chevron) {
            TextView ch = new TextView(this);
            ch.setText("❯");
            ch.setTextColor(0xFFAAAAAA);
            ch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            row.addView(ch);
        }
        return row;
    }

    private int selectableItemBackground() {
        TypedValue t = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, t, true);
        return t.resourceId;
    }

    private void addHomeRow() {
        LinearLayout row = baseRow("🏠", getString(R.string.gest_home_btn),
                actionLabel(gestures.getAction(GestureConfig.HOME_PRESS)), true);
        row.setOnClickListener(v -> showActionPicker(GestureConfig.HOME_PRESS));
        list.addView(row);
    }

    private void addHomeOnlyRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight((int) (64 * density));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackgroundResource(selectableItemBackground());
        row.setPadding(0, (int) (8 * density), 0, (int) (8 * density));

        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tx);

        TextView tt = new TextView(this);
        tt.setText(R.string.gest_home_only);
        tt.setTextColor(Color.WHITE);
        tt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tx.addView(tt);

        TextView st = new TextView(this);
        st.setText(R.string.gest_home_only_sub);
        st.setTextColor(0xFFAAAAAA);
        st.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tx.addView(st);

        Switch sw = new Switch(this);
        sw.setChecked(gestures.isHomeOnlyHome());
        sw.setOnCheckedChangeListener((b, v) -> gestures.setHomeOnlyHome(v));
        row.addView(sw);
        row.setOnClickListener(v -> {
            boolean nv = !gestures.isHomeOnlyHome();
            gestures.setHomeOnlyHome(nv);
            sw.setChecked(nv);
        });
        list.addView(row);
    }

    private void addGestureRow(GestureRow r) {
        String sub = r.subRes != 0 ? getString(r.subRes)
                : actionLabel(gestures.getAction(r.key));
        LinearLayout row = baseRow(r.icon, getString(r.titleRes), sub, true);
        row.setOnClickListener(v -> showActionPicker(r.key));
        row.setTag(r.key);
        list.addView(row);
    }

    private String actionLabel(String action) {
        if (action == null || GestureConfig.ACT_NONE.equals(action)) {
            return getString(R.string.action_none);
        }
        if (GestureConfig.isAppAction(action)) {
            String pkg = GestureConfig.appOf(action);
            try {
                CharSequence label = getPackageManager().getApplicationLabel(
                        getPackageManager().getApplicationInfo(pkg, 0));
                if (label != null) return label.toString();
            } catch (Exception ignored) { }
            return pkg;
        }
        for (ActionDef a : ACTIONS) {
            if (a.id.equals(action)) return getString(a.labelRes);
        }
        return getString(R.string.action_none);
    }

    private void showActionPicker(String gestureKey) {
        String cur = gestures.getAction(gestureKey);
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        int checked = 0;
        for (int i = 0; i < ACTIONS.length; i++) {
            ActionDef a = ACTIONS[i];
            boolean isCur = a.id.equals(cur)
                    || (GestureConfig.ACT_APP_PREFIX.equals(a.id) && GestureConfig.isAppAction(cur));
            if (isCur) checked = i;
            String label = GestureConfig.ACT_APP_PREFIX.equals(a.id) && GestureConfig.isAppAction(cur)
                    ? actionLabel(cur) : getString(a.labelRes);
            items.add(new ModernSheet.Item(a.icon, label));
        }
        ModernSheet.show(this, getString(R.string.choose_action), items, checked, idx -> {
            ActionDef a = ACTIONS[idx];
            if (GestureConfig.ACT_APP_PREFIX.equals(a.id)) {
                showAppPicker(gestureKey);
                return;
            }
            gestures.setAction(gestureKey, a.id);
            rebuild();
        });
    }

    private static class AppEntry {
        final String pkg;
        final String label;
        AppEntry(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }

    private void showAppPicker(String gestureKey) {
        List<AppEntry> apps = new ArrayList<>();
        try {
            PackageManager pm = getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(i, 0);
            String self = getPackageName();
            for (ResolveInfo ri : ris) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(self)) continue;
                CharSequence label = ri.loadLabel(pm);
                apps.add(new AppEntry(pkg, label != null ? label.toString() : pkg));
            }
            Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        } catch (Exception ignored) { }
        if (apps.isEmpty()) return;
        String curPkg = GestureConfig.appOf(gestures.getAction(gestureKey));
        int checked = -1;
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).pkg.equals(curPkg)) { checked = i; break; }
        }
        ModernSheet.showGrid(this, getString(R.string.action_open_app), apps.size(), checked,
            (idx, isChecked) -> {
                AppEntry a = apps.get(idx);
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(0, (int) (8 * density), 0, (int) (8 * density));

                TextView av = new TextView(this);
                String letter = a.label.isEmpty() ? "?" : a.label.substring(0, 1).toUpperCase(Locale.getDefault());
                av.setText(letter);
                av.setTextColor(Color.WHITE);
                av.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                av.setTypeface(null, Typeface.BOLD);
                av.setGravity(Gravity.CENTER);
                int sz = (int) (48 * density);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(pastelOf(a.pkg));
                av.setBackground(bg);
                av.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
                cell.addView(av);

                TextView nm = new TextView(this);
                nm.setText(a.label + (isChecked ? "  ✓" : ""));
                nm.setTextColor(0xFFE8E8E8);
                nm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                nm.setGravity(Gravity.CENTER);
                nm.setMaxLines(2);
                nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
                cell.addView(nm);
                return cell;
            },
            idx -> {
                gestures.setAction(gestureKey, GestureConfig.ACT_APP_PREFIX + apps.get(idx).pkg);
                rebuild();
            });
    }

    private static int pastelOf(String pkg) {
        int h = Math.abs(pkg.hashCode()) % 360;
        float[] hsv = {h, 0.45f, 0.75f};
        return Color.HSVToColor(hsv);
    }
}
