package com.launcher.larla.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.launcher.larla.R;
import com.launcher.larla.util.DockConfig;
import com.launcher.larla.util.IconStyle;
import com.launcher.larla.view.DockEditorView;
import com.launcher.larla.view.ModernSheet;

import java.util.ArrayList;
import java.util.List;

public class DockEditorActivity extends Activity {

    private static final String TAG = "DockEditor";

    private DockConfig dockConfig;
    private DockEditorView editorView;
    private TextView lblCount, lblSize, lblMargin, lblFundo, lblPosicao, lblPaginas, lblNomes;

    private int iconCount, iconSizeDp, marginDp, bgColor, pageCount;
    private String position = "bottom";
    private boolean hideNames;

    private List<AppPreview> previewApps = new ArrayList<>();

    private static class AppPreview {
        final String pkg;
        final String name;
        final Drawable icon;
        AppPreview(String p, String n, Drawable i) { pkg = p; name = n; icon = i; }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_dock_editor);
        int sbId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (sbId > 0) {
            int sbH = getResources().getDimensionPixelSize(sbId);
            View scrim = findViewById(R.id.status_scrim);
            scrim.getLayoutParams().height = sbH;
            scrim.setVisibility(View.VISIBLE);
        }

        dockConfig = new DockConfig(this);

        iconCount = dockConfig.getIconCount();
        iconSizeDp = dockConfig.getIconSizeDp();
        marginDp = dockConfig.getMarginDp();
        bgColor = dockConfig.getBgColor();
        position = dockConfig.getPosition();
        pageCount = dockConfig.getPageCount();
        hideNames = dockConfig.getHideNames();

        editorView = findViewById(R.id.dock_editor);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        lblCount = findViewById(R.id.lbl_count);
        lblSize = findViewById(R.id.lbl_size);
        lblMargin = findViewById(R.id.lbl_margin);
        lblFundo = findViewById(R.id.lbl_fundo);
        lblPosicao = findViewById(R.id.lbl_posicao);
        lblPaginas = findViewById(R.id.lbl_paginas);
        lblNomes = findViewById(R.id.lbl_nomes);

        editorView.updateValues(iconCount, iconSizeDp, marginDp, bgColor, position, hideNames);
        updatePreviewLayout();
        loadPreviewApps();

        editorView.setCallback(index -> {
            if (index >= 0 && index < previewApps.size()) {
                showGestureDialog(previewApps.get(index));
            }
        });

        setupButtons();
        updateLabels();
    }

    private void setupButtons() {
        Button btnCountMinus = findViewById(R.id.btn_count_minus);
        Button btnCountPlus = findViewById(R.id.btn_count_plus);
        btnCountMinus.setOnClickListener(v -> {
            if (iconCount > 1) { iconCount--; applyToView(); }
        });
        btnCountPlus.setOnClickListener(v -> {
            if (iconCount < 8) { iconCount++; applyToView(); }
        });

        Button btnSizeMinus = findViewById(R.id.btn_size_minus);
        Button btnSizePlus = findViewById(R.id.btn_size_plus);
        btnSizeMinus.setOnClickListener(v -> {
            if (iconSizeDp > 24) { iconSizeDp -= 4; applyToView(); }
        });
        btnSizePlus.setOnClickListener(v -> {
            if (iconSizeDp < 96) { iconSizeDp += 4; applyToView(); }
        });

        Button btnMarginMinus = findViewById(R.id.btn_margin_minus);
        Button btnMarginPlus = findViewById(R.id.btn_margin_plus);
        btnMarginMinus.setOnClickListener(v -> {
            if (marginDp > 0) { marginDp -= 4; applyToView(); }
        });
        btnMarginPlus.setOnClickListener(v -> {
            if (marginDp < 60) { marginDp += 4; applyToView(); }
        });

        findViewById(R.id.btn_bg_color).setOnClickListener(v -> showColorPicker());
        findViewById(R.id.btn_posicao).setOnClickListener(v -> showPositionPicker());
        findViewById(R.id.btn_paginas).setOnClickListener(v -> showPageCountPicker());
        findViewById(R.id.btn_nomes).setOnClickListener(v -> {
            hideNames = !hideNames;
            applyToView();
        });
        findViewById(R.id.btn_save).setOnClickListener(v -> saveAndFinish());
    }

    private void applyToView() {
        editorView.updateValues(iconCount, iconSizeDp, marginDp, bgColor, position, hideNames);
        updatePreviewLayout();
        updateLabels();
    }

    private void updatePreviewLayout() {
        android.widget.FrameLayout.LayoutParams lp =
                (android.widget.FrameLayout.LayoutParams) editorView.getLayoutParams();
        boolean vertical = "left".equals(position) || "right".equals(position);
        if (vertical) {
            lp.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            lp.gravity = ("left".equals(position) ? android.view.Gravity.START : android.view.Gravity.END)
                    | android.view.Gravity.CENTER_VERTICAL;
        } else {
            lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.gravity = ("top".equals(position) ? android.view.Gravity.TOP : android.view.Gravity.BOTTOM)
                    | android.view.Gravity.CENTER_HORIZONTAL;
        }
        editorView.setLayoutParams(lp);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ModernSheet.dismissAll();
        if (isFinishing()) saveConfig();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        editorView.cleanupIcons();
    }

    private void saveConfig() {
        dockConfig.setIconCount(iconCount);
        dockConfig.setIconSizeDp(iconSizeDp);
        dockConfig.setMarginDp(marginDp);
        dockConfig.setBgColor(bgColor);
        dockConfig.setPosition(position);
        dockConfig.setPageCount(pageCount);
        dockConfig.setHideNames(hideNames);
    }

    private void saveAndFinish() {
        saveConfig();
        if (!isFinishing()) finish();
    }

    private void loadPreviewApps() {
        previewApps.clear();
        try {
            PackageManager pm = getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(i, 0);
            float density = getResources().getDisplayMetrics().density;
            int targetSize = (int) (dockConfig.getIconSizeDp() * density);
            com.launcher.larla.util.SettingsManager sm = new com.launcher.larla.util.SettingsManager(this);
            int shape = sm.getIconShape(com.launcher.larla.util.SettingsManager.SURF_DOCK);
            int[] corners = shape == com.launcher.larla.util.IconShape.CUSTOM ? sm.getCorners(com.launcher.larla.util.SettingsManager.SURF_DOCK) : null;
            java.util.HashMap<String, ResolveInfo> byPkg = new java.util.HashMap<>(ris.size());
            for (ResolveInfo ri : ris) {
                if (ri.activityInfo.packageName.equals(getPackageName())) continue;
                byPkg.put(ri.activityInfo.packageName, ri);
            }
            // Página 1 real da dock primeiro: reflete o ambiente utilizável
            List<String> pagePkgs = dockConfig.getPage(0);
            for (String pkg : pagePkgs) {
                ResolveInfo ri = byPkg.remove(pkg);
                if (ri == null) continue;
                android.graphics.Bitmap bmp =
                        com.launcher.larla.util.BitmapUtils.loadIconScaled(ri, pm, targetSize);
                bmp = IconStyle.prepare(pkg, bmp, shape, corners);
                Drawable icon = bmp != null
                        ? new android.graphics.drawable.BitmapDrawable(getResources(), bmp)
                        : ri.loadIcon(pm);
                previewApps.add(new AppPreview(pkg, ri.loadLabel(pm).toString(), icon));
            }
            // Completa com apps instalados se a página 1 não preencher
            for (ResolveInfo ri : ris) {
                if (previewApps.size() >= 20) break;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !byPkg.containsKey(pkg)) continue;
                byPkg.remove(pkg);
                android.graphics.Bitmap bmp =
                        com.launcher.larla.util.BitmapUtils.loadIconScaled(ri, pm, targetSize);
                bmp = IconStyle.prepare(pkg, bmp, shape, corners);
                Drawable icon = bmp != null
                        ? new android.graphics.drawable.BitmapDrawable(getResources(), bmp)
                        : ri.loadIcon(pm);
                previewApps.add(new AppPreview(pkg, ri.loadLabel(pm).toString(), icon));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load preview apps", e);
        }
        java.util.List<Drawable> icons = new java.util.ArrayList<>(previewApps.size());
        java.util.List<String> names = new java.util.ArrayList<>(previewApps.size());
        java.util.List<Integer> colors = new java.util.ArrayList<>(previewApps.size());
        for (AppPreview a : previewApps) {
            icons.add(a.icon);
            names.add(a.name);
            colors.add(com.launcher.larla.util.IconStyle.plateColor(a.pkg));
        }
        editorView.setPreviewData(icons, names, colors);
    }

    private void updateLabels() {
        lblCount.setText(String.valueOf(iconCount));
        lblSize.setText(iconSizeDp + "dp");
        lblMargin.setText(marginDp + "dp");
        lblFundo.setText(bgColor == 0 ? "Transparente" : "Cor");
        String[] posLabels = {"Inferior", "Superior", "Esquerda", "Direita"};
        String[] posNames = {"bottom", "top", "left", "right"};
        int idx = 0;
        for (int i = 0; i < posNames.length; i++) { if (posNames[i].equals(position)) { idx = i; break; } }
        lblPosicao.setText(posLabels[idx]);
        lblPaginas.setText(String.valueOf(pageCount));
        lblNomes.setText(hideNames ? "Não" : "Sim");
    }

    private void showPositionPicker() {
        final String[] pos = {"bottom", "top", "left", "right"};
        final String[] names = {"Inferior", "Superior", "Esquerda", "Direita"};
        final String[] icons = {"⬇️", "⬆️", "⬅️", "➡️"};
        int sel = 0;
        for (int i = 0; i < pos.length; i++) { if (pos[i].equals(position)) { sel = i; break; } }
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < pos.length; i++) items.add(new ModernSheet.Item(icons[i], names[i]));
        ModernSheet.show(this, "Posição do dock", items, sel, w -> {
                position = pos[w];
                applyToView();
            });
    }

    private void showPageCountPicker() {
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) items.add(new ModernSheet.Item("\uD83D\uDCC4", String.valueOf(i + 1)));
        ModernSheet.show(this, "Páginas", items, pageCount - 1, w -> {
                pageCount = w + 1;
                applyToView();
            });
    }

    private void showColorPicker() {
        final int[] colors = {0, 0x33000000, 0xAA000000, 0xAA333333, 0xAA1976D2, 0xAAD32F2F, 0xAA388E3C};
        final String[] names = {"Transparente", "Semi-preto", "Preto", "Cinza", "Azul", "Vermelho", "Verde"};
        final String[] icons = {"◻️", "⬛", "⬛", "🔲", "\uD83D\uDFE6", "\uD83D\uDFE5", "\uD83D\uDFE9"};
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) items.add(new ModernSheet.Item(icons[i], names[i]));
        int sel = 0;
        for (int i = 0; i < colors.length; i++) { if (colors[i] == bgColor) { sel = i; break; } }
        ModernSheet.show(this, "Cor do fundo", items, sel, w -> {
                bgColor = colors[w];
                applyToView();
            });
    }

    private String gestureActionLabel(String action) {
        if (action == null || "none".equals(action)) return "Nada";
        switch (action) {
            case "open_drawer": return "Abrir gaveta";
            case "open_settings": return "Configurações";
            case "open_wallpaper": return "Papel de parede";
            case "open_widgets": return "Widgets";
            default: {
                String pkg = com.launcher.larla.util.GestureConfig.appOf(action);
                if (pkg == null) return "Nada";
                String label = pkg;
                try {
                    label = getPackageManager().getApplicationInfo(pkg, 0)
                        .loadLabel(getPackageManager()).toString();
                } catch (Exception ignored) { }
                return "Abrir app: " + label;
            }
        }
    }

    private void showGestureDialog(AppPreview app) {
        final String[] gestures = {"swipe_up", "swipe_down", "swipe_left", "swipe_right", "double_tap"};
        final String[] labels = {"Deslizar ↑", "Deslizar ↓", "Deslizar ←", "Deslizar →", "Dois toques"};
        final String[] icons = {"⬆️", "⬇️", "⬅️", "➡️", "\uD83D\uDC46"};
        final String[] actions = {"none", "open_drawer", "open_settings", "open_wallpaper", "open_widgets"};
        final String[] actionLabels = {"Nada", "Abrir gaveta", "Configurações", "Papel de parede", "Widgets"};

        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < gestures.length; i++) {
            String cur = dockConfig.getGesture(app.pkg, gestures[i]);
            items.add(new ModernSheet.Item(icons[i], labels[i] + ": " + gestureActionLabel(cur)));
        }

        ModernSheet.show(this, app.name, items, w -> showGestureActionPicker(app, gestures[w], labels[w]));
    }

    private void showGestureActionPicker(AppPreview app, String gesture, String gestureLabel) {
        final String[] actions = {"none", "open_drawer", "open_settings", "open_wallpaper", "open_widgets"};
        final String[] labels = {"Nada", "Abrir gaveta", "Configurações", "Papel de parede", "Widgets"};
        final String[] icons = {"\uD83D\uDEAB", "\uD83D\uDCC2", "⚙️", "\uD83D\uDDBC\uFE0F", "\uD83E\uDDE9"};
        String cur = dockConfig.getGesture(app.pkg, gesture);
        int sel = 0;
        boolean curIsApp = com.launcher.larla.util.GestureConfig.isAppAction(cur);
        for (int i = 0; i < actions.length && !curIsApp; i++) {
            if (actions[i].equals(cur)) { sel = i; break; }
        }
        if (curIsApp) sel = actions.length;
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < actions.length; i++) items.add(new ModernSheet.Item(icons[i], labels[i]));
        items.add(new ModernSheet.Item("\uD83D\uDCF1", "Abrir app…"));
        ModernSheet.show(this, app.name + " — " + gestureLabel, items, sel, w -> {
                if (w >= actions.length) showDockAppChooser(app, gesture);
                else dockConfig.setGesture(app.pkg, gesture, actions[w]);
            });
    }

    /** Escolhe o app alvo da ação "abrir app" de um gesto da dock. */
    private void showDockAppChooser(AppPreview app, String gesture) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.Intent i = new android.content.Intent(
                android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER);
            java.util.List<android.content.pm.ResolveInfo> ris =
                pm.queryIntentActivities(i, 0);
            java.util.ArrayList<String> pkgs = new java.util.ArrayList<>();
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            for (android.content.pm.ResolveInfo ri : ris) {
                if (ri == null || ri.activityInfo == null) continue;
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName())) continue;
                try {
                    names.add(ri.loadLabel(pm).toString());
                    pkgs.add(p);
                } catch (Exception ignored) { }
            }
            java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
            for (String n : names) items.add(new ModernSheet.Item("\uD83D\uDCF1", n));
            ModernSheet.show(this, "Abrir app…", items, w2 -> {
                if (w2 >= 0 && w2 < pkgs.size()) {
                    dockConfig.setGesture(app.pkg, gesture, "app:" + pkgs.get(w2));
                }
            });
        } catch (Exception e) {
            android.util.Log.w("DockEditor", "app chooser failed", e);
        }
    }
}
