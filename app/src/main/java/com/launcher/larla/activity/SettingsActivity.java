package com.launcher.larla.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import com.launcher.larla.R;
import com.launcher.larla.util.SettingsManager;
import com.launcher.larla.view.ColorPickerView;
import com.launcher.larla.view.ModernSheet;

public class SettingsActivity extends Activity {

    private SettingsManager settings;

    private static final String[] SHAPE_NAMES = {"Quadrado", "Arredondado", "Círculo", "Gota",
            "Esquilo", "Flor", "Trevo", "Ondulado", "Sol", "Cápsula",
            "Diamante", "Triângulo", "Pentágono", "Folha esq.", "Folha dir.",
            "Personalizado"};

    private static String shapeName(int shape) {
        if (shape < 0 || shape >= SHAPE_NAMES.length) return SHAPE_NAMES[0];
        return SHAPE_NAMES[shape];
    }

    private void setSub(int id, String text) {
        try {
            android.widget.TextView tv = findViewById(id);
            if (tv != null) tv.setText(text);
        } catch (Exception ignored) { }
    }

    /** Resumos visíveis sem abrir cada cartão. */
    private void updateSubtitles() {
        try {
            setSub(R.id.sub_gaveta, settings.getGridColumns() + " colunas \u2022 ícones "
                + settings.getIconSizePercent() + "% \u2022 anim "
                + settings.getDrawerAnimMs() + "ms");
            setSub(R.id.sub_dock, settings.getShowDock()
                ? "Visível \u2022 animação " + (settings.getDockAnimPercent() * 10) + "ms"
                : "Oculta");
            setSub(R.id.sub_widgets, "Cantos " + settings.getWidgetRadius() + "dp");
            setSub(R.id.sub_icones, settings.getUnifyIcons()
                ? "Unificado (segue a gaveta)"
                : "Gaveta: " + shapeName(settings.getIconShape(
                    com.launcher.larla.util.SettingsManager.SURF_DRAWER)));
            com.launcher.larla.util.AppCustom.init(this);
            int hidden = 0;
            try {
                hidden = com.launcher.larla.util.AppCustom.get().hiddenPkgs().size();
            } catch (Exception ignored) { }
            setSub(R.id.sub_dados, hidden == 0 ? "Nenhum app oculto"
                : hidden == 1 ? "1 app oculto" : hidden + " apps ocultos");
        } catch (Exception ignored) { }
    }

    private void setupSection(int headerId, int cardId, int arrowId) {
        View header = findViewById(headerId);
        View card = findViewById(cardId);
        TextView arrow = findViewById(arrowId);
        header.setOnClickListener(v -> {
            boolean open = card.getVisibility() == View.GONE;
            card.setVisibility(open ? View.VISIBLE : View.GONE);
            arrow.animate().rotation(open ? 90f : 0f).setDuration(200).start();
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settings = new SettingsManager(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.arrow_gaveta)).setRotation(90f);

        setupSection(R.id.header_gaveta, R.id.card_gaveta, R.id.arrow_gaveta);
        setupSection(R.id.header_dock, R.id.card_dock, R.id.arrow_dock);
        setupSection(R.id.header_widgets, R.id.card_widgets, R.id.arrow_widgets);
        setupSection(R.id.header_icones, R.id.card_icones, R.id.arrow_icones);
        setupSection(R.id.header_atalhos, R.id.card_atalhos, R.id.arrow_atalhos);
        setupSection(R.id.header_gestos, R.id.card_gestos, R.id.arrow_gestos);
        setupSection(R.id.header_dados, R.id.card_dados, R.id.arrow_dados);
        setupSection(R.id.header_padrao, R.id.card_padrao, R.id.arrow_padrao);
        setupSection(R.id.header_sobre, R.id.card_sobre, R.id.arrow_sobre);

        SeekBar seekCols = findViewById(R.id.seek_columns);
        TextView lblCols = findViewById(R.id.lbl_columns);
        int savedCols = settings.getGridColumns();
        seekCols.setProgress(savedCols - 2);
        lblCols.setText(String.valueOf(savedCols));
        seekCols.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    int cols = progress + 2;
                    settings.setGridColumns(cols);
                    lblCols.setText(String.valueOf(cols));
                    updateSubtitles();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        SeekBar seekIconSize = findViewById(R.id.seek_icon_size);
        TextView lblIconSize = findViewById(R.id.lbl_icon_size);
        android.widget.ImageView iconPreview = findViewById(R.id.icon_size_preview);
        try {
            iconPreview.setImageDrawable(getPackageManager().getApplicationIcon("com.launcher.larla"));
        } catch (Exception e) {
            iconPreview.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        }
        int savedPercent = settings.getIconSizePercent();
        seekIconSize.setProgress(savedPercent - 1);
        lblIconSize.setText(savedPercent + "%");
        Runnable updatePreview = () -> {
            int px = settings.getIconSizePx(SettingsActivity.this);
            android.view.ViewGroup.LayoutParams lp = iconPreview.getLayoutParams();
            lp.width = px;
            lp.height = px;
            iconPreview.setLayoutParams(lp);
        };
        updatePreview.run();
        seekIconSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    int pct = progress + 1;
                    settings.setIconSizePercent(pct);
                    lblIconSize.setText(pct + "%");
                    updatePreview.run();
                    updateSubtitles();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        SeekBar seekRadius = findViewById(R.id.seek_widget_radius);
        TextView lblRadius = findViewById(R.id.lbl_widget_radius);
        int savedRadius = settings.getWidgetRadius();
        seekRadius.setProgress(savedRadius);
        lblRadius.setText(savedRadius + "dp");
        seekRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) {
                    settings.setWidgetRadius(p);
                    lblRadius.setText(p + "dp");
                    updateSubtitles();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        Switch swDock = findViewById(R.id.sw_dock);
        swDock.setChecked(settings.getShowDock());
        swDock.setOnCheckedChangeListener((b, v) -> { settings.setShowDock(v); updateSubtitles(); });

        Switch swSearch = findViewById(R.id.sw_search);
        swSearch.setChecked(settings.getShowSearch());
        swSearch.setOnCheckedChangeListener((b, v) -> settings.setShowSearch(v));

        SeekBar seekDrawerAnim = findViewById(R.id.seek_drawer_anim);
        TextView lblDrawerAnim = findViewById(R.id.lbl_drawer_anim);
        int drawerPct = settings.getDrawerAnimPercent();
        seekDrawerAnim.setProgress(drawerPct);
        lblDrawerAnim.setText(settings.getDrawerAnimMs() + "ms");
        seekDrawerAnim.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) {
                    settings.setDrawerAnimPercent(p);
                    lblDrawerAnim.setText(settings.getDrawerAnimMs() + "ms");
                    updateSubtitles();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        SeekBar seekAnim = findViewById(R.id.seek_dock_anim);
        TextView lblAnim = findViewById(R.id.lbl_dock_anim);
        int pct = settings.getDockAnimPercent();
        seekAnim.setProgress(pct);
        lblAnim.setText(pct * 10 + "ms");
        seekAnim.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) {
                    settings.setDockAnimPercent(p);
                    lblAnim.setText(p * 10 + "ms");
                    updateSubtitles();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        ColorPickerView colorPicker = findViewById(R.id.color_picker);
        SeekBar seekAlpha = findViewById(R.id.seek_drawer_alpha);
        TextView lblAlpha = findViewById(R.id.lbl_drawer_alpha);
        View preview = findViewById(R.id.drawer_color_preview);

        int savedColor = settings.getDrawerBaseColor();
        int savedAlpha = settings.getDrawerAlpha();
        colorPicker.setColor(savedColor);
        seekAlpha.setProgress(savedAlpha);
        lblAlpha.setText(savedAlpha * 100 / 255 + "%");
        preview.setBackgroundColor(settings.getDrawerFinalColor());

        colorPicker.setOnColorChangedListener(color -> {
            settings.setDrawerBaseColor(color);
            preview.setBackgroundColor(settings.getDrawerFinalColor());
        });

        seekAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    settings.setDrawerAlpha(progress);
                    lblAlpha.setText(progress * 100 / 255 + "%");
                    preview.setBackgroundColor(settings.getDrawerFinalColor());
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        findViewById(R.id.btn_edit_dock).setOnClickListener(v ->
                startActivity(new Intent(this, DockEditorActivity.class)));

        findViewById(R.id.btn_edit_icons).setOnClickListener(v ->
                startActivity(new Intent(this, IconEditorActivity.class)));

        findViewById(R.id.btn_wallpaper).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SET_WALLPAPER);
            startActivity(Intent.createChooser(i, getString(R.string.wallpaper)));
        });

        findViewById(R.id.btn_gestures).setOnClickListener(v ->
                startActivity(new Intent(this, GestureSettingsActivity.class)));

        findViewById(R.id.btn_widgets).setOnClickListener(v -> {
            try {
                Intent i = new Intent("android.appwidget.action.ACTION_APPWIDGET_PICKER");
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, R.string.widgets_not_supported, Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_cache).setOnClickListener(v -> {
            settings.setClearCacheFlag();
            Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_hidden).setOnClickListener(v -> showHiddenApps());

        findViewById(R.id.btn_about).setOnClickListener(v -> {
            ModernSheet.showConfirm(this, getString(R.string.about_title),
                getString(R.string.about_message), getString(R.string.ok), null, null, null);
        });

        findViewById(R.id.btn_make_default).setOnClickListener(v -> requestDefaultHome());
        updateDefaultStatus();
        updateSubtitles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDefaultStatus();
        updateSubtitles();
    }

    private void showHiddenApps() {
        com.launcher.larla.util.AppCustom.init(this);
        java.util.List<String> pkgs = com.launcher.larla.util.AppCustom.get().hiddenPkgs();
        if (pkgs.isEmpty()) {
            ModernSheet.showConfirm(this, getString(R.string.hidden_apps),
                getString(R.string.hidden_empty), getString(R.string.ok), null, null, null);
            return;
        }
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        android.content.pm.PackageManager pm = getPackageManager();
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (String pkg : pkgs) {
            String label = pkg;
            try {
                label = pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
                label = com.launcher.larla.util.AppCustom.get().label(pkg, label);
            } catch (Exception ignored) { }
            labels.add(label);
            items.add(new ModernSheet.Item("\uD83D\uDC41️", label));
        }
        ModernSheet.show(this, getString(R.string.hidden_apps), items, w -> {
            if (w < 0 || w >= pkgs.size()) return;
            String pkg = pkgs.get(w);
            com.launcher.larla.util.AppCustom.get().setHidden(pkg, false);
            Toast.makeText(this, R.string.app_unhidden, Toast.LENGTH_SHORT).show();
            settings.setPkgRefresh();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        ModernSheet.dismissAll();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001) updateDefaultStatus();
    }

    private boolean isDefaultHome() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    return rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME);
                }
            } catch (Exception ignored) { }
        }
        try {
            android.content.Intent i = new android.content.Intent(
                android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo ri = getPackageManager()
                .resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null
                && getPackageName().equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    private void updateDefaultStatus() {
        android.widget.TextView lbl = findViewById(R.id.lbl_default_status);
        android.widget.Button btn = findViewById(R.id.btn_make_default);
        if (lbl == null || btn == null) return;
        boolean isDefault = isDefaultHome();
        lbl.setText(isDefault ? "✓ Padrão" : "Não é o padrão");
        btn.setEnabled(!isDefault);
        btn.setAlpha(isDefault ? 0.4f : 1f);
    }

    private void requestDefaultHome() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)
                        && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    startActivityForResult(
                        rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), 1001);
                    return;
                }
            } catch (Exception ignored) { }
        }
        try {
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_SETTINGS));
            } catch (Exception ignored) { }
        }
    }
}
