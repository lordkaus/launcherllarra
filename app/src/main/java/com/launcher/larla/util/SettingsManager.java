package com.launcher.larla.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.DisplayMetrics;

public class SettingsManager {
    private final SharedPreferences prefs;

    private static final String KEY_GRID = "gridColumns";
    private static final String KEY_ICON = "iconSize";
    private static final String KEY_DOCK = "showDock";
    private static final String KEY_DRAWER_COLOR = "drawerBaseColor";
    private static final String KEY_DRAWER_ALPHA = "drawerAlpha";
    private static final String KEY_SEARCH = "showSearch";
    private static final String KEY_DOCK_ANIM = "dockAnimPercent";
    private static final String KEY_WIDGET_RADIUS = "widgetRadius";

    public SettingsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("launcher_settings", Context.MODE_PRIVATE);
    }

    public int getGridColumns() { return Math.max(2, Math.min(9, prefs.getInt(KEY_GRID, 4))); }
    public void setGridColumns(int v) { prefs.edit().putInt(KEY_GRID, Math.max(2, Math.min(9, v))).apply(); }

    public int getIconSizePercent() {
        if (prefs.contains(KEY_ICON)) {
            try {
                return prefs.getInt(KEY_ICON, 50);
            } catch (ClassCastException e) {
                String old = prefs.getString(KEY_ICON, "medium");
                int pct = "small".equals(old) ? 25 : "large".equals(old) ? 75 : 50;
                prefs.edit().remove(KEY_ICON).putInt(KEY_ICON, pct).apply();
                return pct;
            }
        }
        return 50;
    }
    public void setIconSizePercent(int v) { prefs.edit().putInt(KEY_ICON, Math.max(10, Math.min(100, v))).apply(); }
    public int getIconSizePx(Context ctx) {
        if (ctx == null) return 48;
        int cols = getGridColumns();
        if (cols < 2) cols = 4;
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int screenWidthPx = dm.widthPixels;
        if (screenWidthPx <= 0) return 48;
        int maxPerIcon = screenWidthPx / 2;
        int perCol = screenWidthPx / cols;
        float ratio = Math.max(10, Math.min(100, getIconSizePercent())) / 100f;
        int px = (int) (perCol * ratio);
        if (px > maxPerIcon) px = maxPerIcon;
        return Math.max(1, px);
    }

    public boolean getShowDock() { return prefs.getBoolean(KEY_DOCK, true); }
    public void setShowDock(boolean v) { prefs.edit().putBoolean(KEY_DOCK, v).apply(); }

    private static final String KEY_CLEAR_CACHE = "clearIconCache";
    private static final String KEY_PKG_REFRESH = "pkg_refresh";    private static final String KEY_SHAPE = "iconShape";
    private static final String KEY_LABEL = "labelSize";
    private static final String KEY_WS_DP = "wsIconDp";

    public void setClearCacheFlag() { prefs.edit().putBoolean(KEY_CLEAR_CACHE, true).apply(); }

    public void setPkgRefresh() { prefs.edit().putBoolean(KEY_PKG_REFRESH, true).apply(); }

    public boolean checkPkgRefresh() {
        if (prefs.getBoolean(KEY_PKG_REFRESH, false)) {
            prefs.edit().remove(KEY_PKG_REFRESH).apply();
            return true;
        }
        return false;
    }

    /** Formato do ícone: 0 quadrado .. 15 personalizado. */
    public int getIconShape() { return Math.max(0, Math.min(IconShape.COUNT - 1, prefs.getInt(KEY_SHAPE, 0))); }
    public void setIconShape(int v) { prefs.edit().putInt(KEY_SHAPE, Math.max(0, Math.min(IconShape.COUNT - 1, v))).apply(); }

    private static final String KEY_TL = "cornerTL";
    private static final String KEY_TR = "cornerTR";
    private static final String KEY_BR = "cornerBR";
    private static final String KEY_BL = "cornerBL";

    /** Cantos do personalizado, 0 seco .. 100 redondo total. Ordem: TL, TR, BR, BL. */
    public int[] getCorners() {
        return new int[]{
            Math.max(0, Math.min(100, prefs.getInt(KEY_TL, 30))),
            Math.max(0, Math.min(100, prefs.getInt(KEY_TR, 30))),
            Math.max(0, Math.min(100, prefs.getInt(KEY_BR, 30))),
            Math.max(0, Math.min(100, prefs.getInt(KEY_BL, 30)))};
    }

    public void setCorners(int tl, int tr, int br, int bl) {
        prefs.edit().putInt(KEY_TL, Math.max(0, Math.min(100, tl)))
            .putInt(KEY_TR, Math.max(0, Math.min(100, tr)))
            .putInt(KEY_BR, Math.max(0, Math.min(100, br)))
            .putInt(KEY_BL, Math.max(0, Math.min(100, bl))).apply();
    }

    public String getCornersKey() {
        int[] c = getCorners();
        return c[0] + "," + c[1] + "," + c[2] + "," + c[3];
    }

    /** Tamanho da legenda em sp (8..16). */
    public int getLabelSize() { return Math.max(8, Math.min(16, prefs.getInt(KEY_LABEL, 11))); }
    public void setLabelSize(int v) { prefs.edit().putInt(KEY_LABEL, Math.max(8, Math.min(16, v))).apply(); }

    private static final String KEY_GLYPH = "glyphPct";
    private static final String KEY_PLATE = "platePct";

    /** Glifo em % do prato (40..100). */
    public int getGlyphPct() { return Math.max(40, Math.min(100, prefs.getInt(KEY_GLYPH, 70))); }
    public void setGlyphPct(int v) { prefs.edit().putInt(KEY_GLYPH, Math.max(40, Math.min(100, v))).apply(); }

    /** Prato em % da view (60..100). */
    public int getPlatePct() { return Math.max(60, Math.min(100, prefs.getInt(KEY_PLATE, 100))); }
    public void setPlatePct(int v) { prefs.edit().putInt(KEY_PLATE, Math.max(60, Math.min(100, v))).apply(); }

    public String getStyleKey() {
        return getIconShape() + "|" + getCornersKey() + "|" + getGlyphPct() + "|"
                + getPlatePct() + "|" + getLabelSize();
    }

    // ==================== ESTILO POR SUPERFÍCIE ====================
    // Superfícies: gaveta (grade de apps), dock, área de trabalho (ws).
    // Chaves novas têm sufixo; ausentes herdam as chaves globais legadas.
    public static final String SURF_DRAWER = "drawer";
    public static final String SURF_DOCK = "dock";
    public static final String SURF_WS = "ws";

    private static final String KEY_UNIFY = "unifyIcons";

    /** Quando marcado, dock e área de trabalho seguem o padrão da gaveta. */
    public boolean getUnifyIcons() { return prefs.getBoolean(KEY_UNIFY, true); }
    public void setUnifyIcons(boolean v) { prefs.edit().putBoolean(KEY_UNIFY, v).apply(); }

    /** Superfície efetiva (resolve o unificar). */
    public String effSurface(String s) {
        if (s == null) return SURF_DRAWER;
        if (getUnifyIcons() && !SURF_DRAWER.equals(s)) return SURF_DRAWER;
        return s;
    }

    private int shapeOf(String key, int legacy) {
        int v = prefs.getInt(key, -1);
        if (v >= 0) return Math.max(0, Math.min(IconShape.COUNT - 1, v));
        return legacy;
    }

    public int getIconShape(String s) {
        return shapeOf(KEY_SHAPE + "_" + effSurface(s), getIconShape());
    }
    public void setIconShape(String s, int v) {
        if (s == null) s = SURF_DRAWER;
        prefs.edit().putInt(KEY_SHAPE + "_" + s,
            Math.max(0, Math.min(IconShape.COUNT - 1, v))).apply();
    }

    private int cornerOf(String key, int legacy) {
        int v = prefs.getInt(key, -1);
        if (v >= 0) return Math.max(0, Math.min(100, v));
        return legacy;
    }

    /** Cantos do personalizado por superfície. Ordem: TL, TR, BR, BL. */
    public int[] getCorners(String s) {
        s = effSurface(s);
        int[] legacy = getCorners();
        String[] keys = {KEY_TL + "_" + s, KEY_TR + "_" + s,
            KEY_BR + "_" + s, KEY_BL + "_" + s};
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) out[i] = cornerOf(keys[i], legacy[i]);
        return out;
    }

    public void setCorners(String s, int tl, int tr, int br, int bl) {
        if (s == null) s = SURF_DRAWER;
        prefs.edit().putInt(KEY_TL + "_" + s, Math.max(0, Math.min(100, tl)))
            .putInt(KEY_TR + "_" + s, Math.max(0, Math.min(100, tr)))
            .putInt(KEY_BR + "_" + s, Math.max(0, Math.min(100, br)))
            .putInt(KEY_BL + "_" + s, Math.max(0, Math.min(100, bl))).apply();
    }

    public String getCornersKey(String s) {
        int[] c = getCorners(s);
        return c[0] + "," + c[1] + "," + c[2] + "," + c[3];
    }

    private int intOf(String key, int legacy, int min, int max) {
        int v = prefs.getInt(key, -1);
        if (v >= 0) return Math.max(min, Math.min(max, v));
        return legacy;
    }

    public int getLabelSize(String s) {
        return intOf(KEY_LABEL + "_" + effSurface(s), getLabelSize(), 8, 16);
    }
    public void setLabelSize(String s, int v) {
        if (s == null) s = SURF_DRAWER;
        prefs.edit().putInt(KEY_LABEL + "_" + s, Math.max(8, Math.min(16, v))).apply();
    }

    public int getGlyphPct(String s) {
        return intOf(KEY_GLYPH + "_" + effSurface(s), getGlyphPct(), 40, 100);
    }
    public void setGlyphPct(String s, int v) {
        if (s == null) s = SURF_DRAWER;
        prefs.edit().putInt(KEY_GLYPH + "_" + s, Math.max(40, Math.min(100, v))).apply();
    }

    public int getPlatePct(String s) {
        return intOf(KEY_PLATE + "_" + effSurface(s), getPlatePct(), 60, 100);
    }
    public void setPlatePct(String s, int v) {
        if (s == null) s = SURF_DRAWER;
        prefs.edit().putInt(KEY_PLATE + "_" + s, Math.max(60, Math.min(100, v))).apply();
    }

    /** Chave da máscara (define variantes do cache de ícones). */
    public String maskKey(String s) {
        int sh = getIconShape(s);
        return sh + (sh == IconShape.CUSTOM ? "|" + getCornersKey(s) : "");
    }

    /** Assinatura visual de uma superfície (invalidação de cache/views). */
    public String getStyleKey(String s) {
        return getIconShape(s) + "|" + getCornersKey(s) + "|" + getGlyphPct(s) + "|"
                + getPlatePct(s) + "|" + getLabelSize(s);
    }

    /** Tamanho do ícone da área de trabalho em dp; 0 = segue a grade. */
    public int getWsIconDp() { return Math.max(0, Math.min(96, prefs.getInt(KEY_WS_DP, 0))); }
    public void setWsIconDp(int v) { prefs.edit().putInt(KEY_WS_DP, Math.max(0, Math.min(96, v))).apply(); }

    public int getWsIconPx(Context ctx) {
        int dp = getWsIconDp();
        if (dp <= 0 || ctx == null) return getIconSizePx(ctx);
        float density = ctx.getResources().getDisplayMetrics().density;
        if (density <= 0) return getIconSizePx(ctx);
        return Math.max(1, (int) (dp * density));
    }
    public boolean checkClearCacheFlag() {
        if (prefs.getBoolean(KEY_CLEAR_CACHE, false)) {
            prefs.edit().remove(KEY_CLEAR_CACHE).apply();
            return true;
        }
        return false;
    }

    public int getDrawerBaseColor() { return prefs.getInt(KEY_DRAWER_COLOR, 0xFF000000); }
    public void setDrawerBaseColor(int v) { prefs.edit().putInt(KEY_DRAWER_COLOR, v).apply(); }

    public int getDrawerAlpha() { return Math.max(0, Math.min(255, prefs.getInt(KEY_DRAWER_ALPHA, 102))); }
    public void setDrawerAlpha(int v) { prefs.edit().putInt(KEY_DRAWER_ALPHA, Math.max(0, Math.min(255, v))).apply(); }

    public int getDrawerFinalColor() {
        int base = getDrawerBaseColor();
        int alpha = getDrawerAlpha();
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base));
    }

    public boolean getShowSearch() { return prefs.getBoolean(KEY_SEARCH, true); }
    public void setShowSearch(boolean v) { prefs.edit().putBoolean(KEY_SEARCH, v).apply(); }

    public int getDockAnimPercent() { return Math.max(0, Math.min(100, prefs.getInt(KEY_DOCK_ANIM, 50))); }
    public void setDockAnimPercent(int v) { prefs.edit().putInt(KEY_DOCK_ANIM, Math.max(0, Math.min(100, v))).apply(); }

    private static final String KEY_DRAWER_ANIM = "drawerAnimPercent";

    /** Velocidade da animação da gaveta em % (0 = instantâneo). */
    public int getDrawerAnimPercent() { return Math.max(0, Math.min(100, prefs.getInt(KEY_DRAWER_ANIM, 50))); }
    public void setDrawerAnimPercent(int v) { prefs.edit().putInt(KEY_DRAWER_ANIM, Math.max(0, Math.min(100, v))).apply(); }

    /** Duração base da animação da gaveta em ms (0..400). */
    public int getDrawerAnimMs() { return getDrawerAnimPercent() * 4; }

    public int getWidgetRadius() { return Math.max(0, Math.min(32, prefs.getInt(KEY_WIDGET_RADIUS, 0))); }
    public void setWidgetRadius(int v) { prefs.edit().putInt(KEY_WIDGET_RADIUS, Math.max(0, Math.min(32, v))).apply(); }
}
