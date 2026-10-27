package com.launcher.larla.view;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.launcher.larla.R;

import java.util.List;

public class ModernSheet {

    public interface GridCell {
        View cell(int index, boolean checked);
    }

    public interface Callback {
        void onPick(int index);
    }

    public static void showGrid(Activity activity, String title, int count, int checked,
            GridCell binder, Callback cb) {
        float density = activity.getResources().getDisplayMetrics().density;

        android.widget.LinearLayout card = new android.widget.LinearLayout(activity);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_sheet);
        int lr = (int) (8 * density);
        card.setPadding(lr, (int) (8 * density), lr, (int) (12 * density));

        android.view.View handle = new android.view.View(activity);
        int hw = (int) (40 * density), hh = (int) (4 * density);
        android.widget.LinearLayout.LayoutParams hlp =
                new android.widget.LinearLayout.LayoutParams(hw, hh);
        hlp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = (int) (4 * density);
        handle.setLayoutParams(hlp);
        android.graphics.drawable.GradientDrawable hg = new android.graphics.drawable.GradientDrawable();
        hg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        hg.setCornerRadius(hh / 2f);
        hg.setColor(0x44FFFFFF);
        handle.setBackground(hg);
        card.addView(handle);

        TextView tv = new TextView(activity);
        tv.setText(title);
        tv.setTextColor(android.graphics.Color.WHITE);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding((int) (16 * density), (int) (6 * density), (int) (16 * density), (int) (10 * density));
        card.addView(tv);

        android.widget.GridLayout grid = new android.widget.GridLayout(activity);
        grid.setColumnCount(4);
        grid.setAlignmentMode(android.widget.GridLayout.ALIGN_BOUNDS);
        int gap = (int) (8 * density);
        grid.setPadding((int) (8 * density), 0, (int) (8 * density), 0);

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        for (int i = 0; i < count; i++) {
            final int idx = i;
            android.view.View cell = binder.cell(idx, idx == checked);
            android.widget.GridLayout.LayoutParams glp = new android.widget.GridLayout.LayoutParams();
            glp.width = 0;
            glp.columnSpec = android.widget.GridLayout.spec(idx % 4, 1f);
            glp.setMargins(gap / 2, gap / 2, gap / 2, gap / 2);
            cell.setLayoutParams(glp);
            cell.setClickable(true);
            cell.setOnClickListener(v -> { dialog.dismiss(); cb.onPick(idx); });
            grid.addView(cell);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(grid);
        card.addView(scroll);

        dialog.setContentView(card, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(android.view.Gravity.BOTTOM);
            w.getAttributes().windowAnimations = R.style.SheetAnim;
        }
        dialog.setCanceledOnTouchOutside(true);
        track(dialog);
        dialog.show();
    }

    private static final java.util.HashSet<Dialog> OPEN = new java.util.HashSet<>();

    /** Dispensa sheets abertos (chamar no onPause para evitar janela vazada). */
    public static void dismissAll() {
        java.util.ArrayList<Dialog> copy = new java.util.ArrayList<>(OPEN);
        OPEN.clear();
        for (Dialog d : copy) {
            try { if (d.isShowing()) d.dismiss(); } catch (Exception ignored) { }
        }
    }

    private static void track(Dialog dialog) {
        OPEN.add(dialog);
        dialog.setOnDismissListener(d -> OPEN.remove(d));
    }

    public static void show(Activity activity, String title, List<Item> items, Callback cb) {
        show(activity, title, items, -1, cb);
    }

    public static void show(Activity activity, String title, List<Item> items, int checked, Callback cb) {
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);

        java.util.List<Item> safeItems = items == null ? new java.util.ArrayList<>() : items;
        android.widget.LinearLayout card = new android.widget.LinearLayout(activity);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_sheet);
        int lr = (int) (8 * density);
        card.setPadding(lr, (int) (8 * density), lr, (int) (12 * density));

        android.view.View handle = new android.view.View(activity);
        int hw = (int) (40 * density), hh = (int) (4 * density);
        android.widget.LinearLayout.LayoutParams hlp =
                new android.widget.LinearLayout.LayoutParams(hw, hh);
        hlp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = (int) (4 * density);
        handle.setLayoutParams(hlp);
        android.graphics.drawable.GradientDrawable hg = new android.graphics.drawable.GradientDrawable();
        hg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        hg.setCornerRadius(hh / 2f);
        hg.setColor(0x44FFFFFF);
        handle.setBackground(hg);
        card.addView(handle);

        TextView tv = new TextView(activity);
        tv.setText(title);
        tv.setTextColor(android.graphics.Color.WHITE);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding((int) (16 * density), (int) (6 * density), (int) (16 * density), (int) (10 * density));
        card.addView(tv);

        android.widget.LinearLayout list = new android.widget.LinearLayout(activity);
        list.setOrientation(android.widget.LinearLayout.VERTICAL);

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        for (int i = 0; i < safeItems.size(); i++) {
            final int idx = i;
            Item it = safeItems.get(idx);
            android.widget.LinearLayout row = new android.widget.LinearLayout(activity);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(pad, (int) (10 * density), pad, (int) (10 * density));
            row.setClickable(true);
            try {
                android.util.TypedValue t = new android.util.TypedValue();
                activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, t, true);
                row.setBackgroundResource(t.resourceId);
            } catch (Exception ignored) { }
            TextView icon = new TextView(activity);
            icon.setText(it.icon != null ? it.icon : "");
            icon.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22);
            icon.setPadding(0, 0, (int) (12 * density), 0);
            row.addView(icon);
            TextView label = new TextView(activity);
            label.setText(it.label != null ? it.label : "");
            label.setTextColor(android.graphics.Color.WHITE);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
            label.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(label);
            if (idx == checked) {
                TextView check = new TextView(activity);
                check.setText("✓");
                check.setTextColor(0xFF00D4FF);
                check.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
                check.setTypeface(null, android.graphics.Typeface.BOLD);
                row.addView(check);
            }
            row.setOnClickListener(v -> { dialog.dismiss(); cb.onPick(idx); });
            list.addView(row);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(list);
        card.addView(scroll);

        dialog.setContentView(card, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(android.view.Gravity.BOTTOM);
            w.getAttributes().windowAnimations = R.style.SheetAnim;
        }
        dialog.setCanceledOnTouchOutside(true);
        track(dialog);
        dialog.show();
    }

    public static void showConfirm(Activity activity, String title, String message,
            String okLabel, String cancelLabel, Runnable onOk, Runnable onCancel) {
        float density = activity.getResources().getDisplayMetrics().density;
        TextView msg = new TextView(activity);
        msg.setText(message);
        msg.setTextColor(0xFFBDBDBD);
        msg.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        int pad = (int) (16 * density);
        msg.setPadding(pad, (int) (4 * density), pad, (int) (12 * density));
        showCustom(activity, title, msg, okLabel, cancelLabel, onOk, onCancel);
    }

    public static void showCustom(Activity activity, String title, android.view.View content,
            String okLabel, String cancelLabel, Runnable onOk, Runnable onCancel) {
        float density = activity.getResources().getDisplayMetrics().density;

        android.widget.LinearLayout card = new android.widget.LinearLayout(activity);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_sheet);
        int lr = (int) (8 * density);
        card.setPadding(lr, (int) (8 * density), lr, (int) (12 * density));

        android.view.View handle = new android.view.View(activity);
        int hw = (int) (40 * density), hh = (int) (4 * density);
        android.widget.LinearLayout.LayoutParams hlp =
                new android.widget.LinearLayout.LayoutParams(hw, hh);
        hlp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = (int) (4 * density);
        handle.setLayoutParams(hlp);
        android.graphics.drawable.GradientDrawable hg = new android.graphics.drawable.GradientDrawable();
        hg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        hg.setCornerRadius(hh / 2f);
        hg.setColor(0x44FFFFFF);
        handle.setBackground(hg);
        card.addView(handle);

        TextView tv = new TextView(activity);
        tv.setText(title);
        tv.setTextColor(android.graphics.Color.WHITE);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding((int) (16 * density), (int) (6 * density), (int) (16 * density), (int) (10 * density));
        card.addView(tv);
        card.addView(content);

        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(activity);
        btnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        btnRow.setPadding(lr, (int) (8 * density), lr, 0);

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (cancelLabel != null) {
            android.widget.Button cancel = new android.widget.Button(activity);
            cancel.setText(cancelLabel);
            cancel.setTextColor(0xFFBDBDBD);
            cancel.setBackgroundResource(R.drawable.bg_button);
            cancel.setOnClickListener(v -> { dialog.dismiss(); if (onCancel != null) onCancel.run(); });
            btnRow.addView(cancel, new android.widget.LinearLayout.LayoutParams(0,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        android.widget.Button ok = new android.widget.Button(activity);
        ok.setText(okLabel);
        ok.setTextColor(0xFF00D4FF);
        ok.setTypeface(null, android.graphics.Typeface.BOLD);
        ok.setBackgroundResource(R.drawable.bg_button);
        ok.setOnClickListener(v -> { dialog.dismiss(); if (onOk != null) onOk.run(); });
        android.widget.LinearLayout.LayoutParams okLp = new android.widget.LinearLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (cancelLabel != null) okLp.leftMargin = lr;
        btnRow.addView(ok, okLp);
        card.addView(btnRow);

        dialog.setContentView(card, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(android.view.Gravity.BOTTOM);
            w.getAttributes().windowAnimations = R.style.SheetAnim;
        }
        dialog.setCanceledOnTouchOutside(true);
        track(dialog);
        dialog.show();
    }

    public static class Item {
        public String icon, label;
        public Item(String icon, String label) { this.icon = icon; this.label = label; }
    }
}