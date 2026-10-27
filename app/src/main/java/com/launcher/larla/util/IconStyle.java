package com.launcher.larla.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;

/** Prato de fundo do ícone no formato escolhido, tingido com a cor da borda do ícone. */
public class IconStyle {

    public static final int SHAPE_SQUARE = 0;
    public static final int SHAPE_ROUND = 1;
    public static final int SHAPE_CIRCLE = 2;
    public static final int SHAPE_DROP = 3;

    private static final int FALLBACK = 0xFFFFFFFF;
    private static final int MAX_EDGE = 512;

    private static final java.util.LinkedHashMap<String, Integer> EDGE =
            new java.util.LinkedHashMap<String, Integer>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Integer> e) {
                    return size() > MAX_EDGE;
                }
            };

    /** Média da cor do anel da borda, ignorando pixels transparentes. Roda na thread de decode. */
    public static int edgeColor(Bitmap bm) {
        if (bm == null || bm.isRecycled()) return FALLBACK;
        try {
            int w = bm.getWidth(), h = bm.getHeight();
            if (w < 4 || h < 4) return FALLBACK;
            long r = 0, g = 0, b = 0;
            int n = 0;
            int step = Math.max(2, Math.min(w, h) / 24);
            for (int x = 0; x < w; x += step) {
                int p = bm.getPixel(x, 0);
                if ((p >>> 24) > 128) { r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++; }
                p = bm.getPixel(x, h - 1);
                if ((p >>> 24) > 128) { r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++; }
            }
            for (int y = step; y < h - 1; y += step) {
                int p = bm.getPixel(0, y);
                if ((p >>> 24) > 128) { r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++; }
                p = bm.getPixel(w - 1, y);
                if ((p >>> 24) > 128) { r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++; }
            }
            if (n == 0) return FALLBACK;
            return 0xFF000000 | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
        } catch (Exception e) {
            return FALLBACK;
        }
    }

    public static void rememberEdge(String pkg, Bitmap bm) {
        if (pkg == null || bm == null) return;
        int color = edgeColor(bm);
        synchronized (EDGE) { EDGE.put(pkg, color); }
    }

    public static int plateColor(String pkg) {
        if (pkg == null) return FALLBACK;
        synchronized (EDGE) {
            Integer c = EDGE.get(pkg);
            return c != null ? c : FALLBACK;
        }
    }

    public static void applyPlate(ImageView iv, int shape, int px, int plateColor) {
        applyPlate(iv, shape, px, plateColor, null);
    }

    public static void applyPlate(ImageView iv, int shape, int px, int plateColor, int[] custom) {
        applyPlate(iv, shape, px, plateColor, custom, 70, 100);
    }

    /** Compat: cor fresca do cache ou branco. */
    public static void applyPlate(ImageView iv, int shape, int px) {
        applyPlate(iv, shape, px, FALLBACK);
    }

    /** Prato que redesenha o Path no tamanho real da view. */
    public static class ShapePlateDrawable extends Drawable {
        private final int shape;
        private final int[] custom;
        private float plateScale = 1f;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public ShapePlateDrawable(int shape, int color) {
            this(shape, color, null);
        }

        public ShapePlateDrawable(int shape, int color, int[] custom) {
            this.shape = IconShape.clamp(shape);
            this.custom = custom;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
        }

        public void setPlateScale(float s) {
            s = Math.max(0.6f, Math.min(1f, s));
            if (s != plateScale) { plateScale = s; invalidateSelf(); }
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            rebuild(bounds);
        }

        private void rebuild(Rect bounds) {
            path.rewind();
            if (bounds.width() > 0 && bounds.height() > 0) {
                IconShape.build(shape, bounds.width(), bounds.height(), path, custom);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            if (path.isEmpty()) rebuild(b);
            canvas.save();
            canvas.scale(plateScale, plateScale, b.exactCenterX(), b.exactCenterY());
            canvas.drawPath(path, paint);
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    /** Decodificado fresco -> cor da borda memorizada + bitmap mascarado. */
    public static Bitmap prepare(String pkg, Bitmap src, int shape) {
        return prepare(pkg, src, shape, null);
    }

    public static Bitmap prepare(String pkg, Bitmap src, int shape, int[] custom) {
        if (src == null) return null;
        rememberEdge(pkg, src);
        return IconShape.mask(src, shape, custom);
    }

    public static Bitmap prepareAuto(String pkg, Bitmap src, SettingsManager sm) {
        return prepareAuto(pkg, src, sm, SettingsManager.SURF_DRAWER);
    }

    public static Bitmap prepareAuto(String pkg, Bitmap src, SettingsManager sm, String surface) {
        int sh = sm != null ? sm.getIconShape(surface) : 0;
        AppCustom custom = AppCustom.get();
        if (custom != null) sh = custom.shapeOf(pkg, sh);
        return prepare(pkg, src, sh,
            sh == IconShape.CUSTOM && sm != null ? sm.getCorners(surface) : null);
    }

    public static void applyPlateAuto(ImageView iv, SettingsManager sm, int px, String pkg) {
        applyPlateAuto(iv, sm, px, pkg, SettingsManager.SURF_DRAWER);
    }

    public static void applyPlateAuto(ImageView iv, SettingsManager sm, int px, String pkg, String surface) {
        int sh = sm != null ? sm.getIconShape(surface) : 0;
        AppCustom custom = AppCustom.get();
        if (custom != null) sh = custom.shapeOf(pkg, sh);
        int edge = plateColor(pkg);
        if (custom != null) edge = custom.colorOf(pkg, edge);
        applyPlate(iv, sh, px, edge,
            sh == IconShape.CUSTOM && sm != null ? sm.getCorners(surface) : null,
            sm != null ? sm.getGlyphPct(surface) : 70,
            sm != null ? sm.getPlatePct(surface) : 100);
    }

    public static void applyPlate(ImageView iv, int shape, int px, int plateColor,
            int[] custom, int glyphPct, int platePct) {
        if (iv == null) return;
        shape = IconShape.clamp(shape);
        ShapePlateDrawable d = new ShapePlateDrawable(shape, plateColor, custom);
        d.setPlateScale(Math.max(0.6f, Math.min(1f, platePct / 100f)));
        iv.setBackground(d);
        float g = Math.max(40, Math.min(100, glyphPct)) / 100f;
        int pad = px > 0 ? (int) (px * (1f - g) / 2f) : 0;
        if (iv.getPaddingLeft() != pad) iv.setPadding(pad, pad, pad, pad);
        iv.setClipToOutline(false);
        iv.setOutlineProvider(null);
    }
}
