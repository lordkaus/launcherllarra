package com.launcher.larla.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;

/** Formas de ícone como Path + máscara de bitmap. IDs 0..3 preservados. */
public class IconShape {

    public static final int SQUARE = 0;
    public static final int ROUND = 1;
    public static final int CIRCLE = 2;
    public static final int DROP = 3;
    public static final int SQUIRCLE = 4;
    public static final int FLOWER = 5;
    public static final int CLOVER = 6;
    public static final int SCALLOP = 7;
    public static final int SUN = 8;
    public static final int CAPSULE = 9;
    public static final int DIAMOND = 10;
    public static final int TRIANGLE = 11;
    public static final int PENTAGON = 12;
    public static final int LEAF_LEFT = 13;
    public static final int LEAF_RIGHT = 14;
    public static final int CUSTOM = 15;
    public static final int COUNT = 16;

    public static int clamp(int shape) {
        return Math.max(0, Math.min(COUNT - 1, shape));
    }

    public static void build(int shape, float w, float h, Path out) {
        build(shape, w, h, out, null);
    }

    public static void build(int shape, float w, float h, Path out, int[] custom) {
        out.rewind();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f;
        shape = clamp(shape);
        if (shape == CUSTOM) {
            buildCustom(out, w, h, custom);
            return;
        }
        switch (shape) {
            case ROUND:
                out.addRoundRect(new RectF(0, 0, w, h), r * 0.56f, r * 0.56f, Path.Direction.CW);
                return;
            case CIRCLE:
                out.addOval(new RectF(0, 0, w, h), Path.Direction.CW);
                return;
            case DROP:
                buildDrop(out, cx, cy, r, w, h);
                return;
            case SQUIRCLE:
                buildSquircle(out, cx, cy, r);
                return;
            case FLOWER:
                flower(out, cx, cy, r);
                return;
            case CLOVER:
                buildClover(out, cx, cy, r);
                return;
            case SCALLOP:
                wavy(out, cx, cy, r, r * 0.60f, 4, (float) Math.PI / 4);
                return;
            case SUN:
                wavy(out, cx, cy, r, r * 0.84f, 12, 0);
                return;
            case CAPSULE: {
                float iw = w * 0.80f;
                out.addRoundRect(new RectF(cx - iw / 2f, 0, cx + iw / 2f, h),
                        iw / 2f, iw / 2f, Path.Direction.CW);
                return;
            }
            case DIAMOND:
                roundedPoly(out, cx, cy, r, 4, (float) -Math.PI / 2, 0.30f);
                return;
            case TRIANGLE:
                roundedPoly(out, cx, cy, r, 3, (float) -Math.PI / 2, 0.35f);
                return;
            case PENTAGON:
                roundedPoly(out, cx, cy, r, 5, (float) -Math.PI / 2, 0.30f);
                return;
            case LEAF_LEFT:
                cornerLeaf(out, w, h, r * 0.60f, false);
                return;
            case LEAF_RIGHT:
                cornerLeaf(out, w, h, r * 0.60f, true);
                return;
            default:
                out.addRect(0, 0, w, h, Path.Direction.CW);
        }
    }

    private static void buildDrop(Path out, float cx, float cy, float r, float w, float h) {
        float rr = r * 0.72f;
        float cyy = h - rr - h * 0.08f;
        float apexX = cx + rr * 0.25f;
        float apexY = h * 0.06f;
        out.addCircle(cx, cyy, rr, Path.Direction.CW);
        out.moveTo(cx - rr * 0.62f, cyy - rr * 0.62f);
        out.lineTo(apexX, apexY);
        out.lineTo(cx + rr * 0.72f, cyy - rr * 0.5f);
        out.close();
    }

    private static void buildSquircle(Path out, float cx, float cy, float r) {
        float k = r * 0.78f;
        out.moveTo(cx + r, cy);
        out.cubicTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        out.cubicTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        out.cubicTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        out.cubicTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        out.close();
    }

    /** Flor: pétalas redondas (âncoras fora, cintura dentro, 2 controles por quadrante). */
    private static void flower(Path out, float cx, float cy, float r) {
        float ri = r * 0.60f;
        float rc = r * 0.98f;
        float[] ox = new float[4];
        float[] oy = new float[4];
        for (int i = 0; i < 4; i++) {
            float a = (float) (i * Math.PI / 2);
            ox[i] = cx + r * (float) Math.cos(a);
            oy[i] = cy + r * (float) Math.sin(a);
        }
        out.moveTo(ox[0], oy[0]);
        for (int i = 0; i < 4; i++) {
            float a0 = (float) (i * Math.PI / 2);
            float c1x = cx + rc * (float) Math.cos(a0 + Math.PI / 8);
            float c1y = cy + rc * (float) Math.sin(a0 + Math.PI / 8);
            float wx = cx + ri * (float) Math.cos(a0 + Math.PI / 4);
            float wy = cy + ri * (float) Math.sin(a0 + Math.PI / 4);
            float c2x = cx + rc * (float) Math.cos(a0 + 3 * Math.PI / 8);
            float c2y = cy + rc * (float) Math.sin(a0 + 3 * Math.PI / 8);
            out.quadTo(c1x, c1y, wx, wy);
            out.quadTo(c2x, c2y, ox[(i + 1) % 4], oy[(i + 1) % 4]);
        }
        out.close();
    }

    private static void buildClover(Path out, float cx, float cy, float r) {
        float d = r * 0.44f, cr = r * 0.52f;
        out.addCircle(cx, cy, cr, Path.Direction.CW);
        out.addCircle(cx - d, cy, cr, Path.Direction.CW);
        out.addCircle(cx + d, cy, cr, Path.Direction.CW);
        out.addCircle(cx, cy - d, cr, Path.Direction.CW);
        out.addCircle(cx, cy + d, cr, Path.Direction.CW);
    }

    /** Quadrado arredondado com um canto em ponta (folha). */
    private static void cornerLeaf(Path out, float w, float h, float radius, boolean pointRight) {
        float sharp = 0.02f * Math.min(w, h);
        float[] radii = pointRight
                ? new float[]{radius, radius, radius, radius, sharp, sharp, radius, radius}
                : new float[]{radius, radius, radius, radius, radius, radius, sharp, sharp};
        out.addRoundRect(new RectF(0, 0, w, h), radii, Path.Direction.CW);
    }

    /** Personalizado: cada canto 0 (seco) .. 100 (redondo total). */
    public static void buildCustom(Path out, float w, float h, int[] pct) {
        out.rewind();
        float m = Math.min(w, h) / 2f;
        float tl = pct != null && pct.length > 0 ? Math.max(0, Math.min(100, pct[0])) / 100f * m : 0;
        float tr = pct != null && pct.length > 1 ? Math.max(0, Math.min(100, pct[1])) / 100f * m : 0;
        float br = pct != null && pct.length > 2 ? Math.max(0, Math.min(100, pct[2])) / 100f * m : 0;
        float bl = pct != null && pct.length > 3 ? Math.max(0, Math.min(100, pct[3])) / 100f * m : 0;
        out.addRoundRect(new RectF(0, 0, w, h),
                new float[]{tl, tl, tr, tr, br, br, bl, bl}, Path.Direction.CW);
    }

    /** Curva fechada ondulada: n lobos entre rOut e rIn, suavizada. */
    private static void wavy(Path out, float cx, float cy, float rOut, float rIn, int n, float phase) {
        int steps = n * 2;
        float[] xs = new float[steps + 1];
        float[] ys = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float a = phase + (float) (i * 2 * Math.PI / steps);
            float rr = (i % 2 == 0) ? rOut : rIn;
            xs[i] = cx + rr * (float) Math.cos(a);
            ys[i] = cy + rr * (float) Math.sin(a);
        }
        out.moveTo((xs[0] + xs[steps - 1]) / 2f, (ys[0] + ys[steps - 1]) / 2f);
        for (int i = 0; i < steps; i++) {
            int j = (i + 1) % steps;
            out.quadTo(xs[i], ys[i], (xs[i] + xs[j]) / 2f, (ys[i] + ys[j]) / 2f);
        }
        out.close();
    }

    /** Polígono de n lados com vértices arredondados (round = fração da aresta). */
    private static void roundedPoly(Path out, float cx, float cy, float r, int n, float rot, float round) {
        float[] vx = new float[n];
        float[] vy = new float[n];
        for (int i = 0; i < n; i++) {
            float a = rot + (float) (i * 2 * Math.PI / n);
            vx[i] = cx + r * (float) Math.cos(a);
            vy[i] = cy + r * (float) Math.sin(a);
        }
        for (int i = 0; i < n; i++) {
            int prev = (i + n - 1) % n;
            int next = (i + 1) % n;
            float sx = vx[i] + (vx[prev] - vx[i]) * round;
            float sy = vy[i] + (vy[prev] - vy[i]) * round;
            float ex = vx[i] + (vx[next] - vx[i]) * round;
            float ey = vy[i] + (vy[next] - vy[i]) * round;
            if (i == 0) out.moveTo(sx, sy);
            else out.lineTo(sx, sy);
            out.quadTo(vx[i], vy[i], ex, ey);
        }
        out.close();
    }

    /** Recorta o bitmap na forma. Recicla a origem se gerar um novo bitmap. */
    public static Bitmap mask(Bitmap src, int shape) {
        return mask(src, shape, null);
    }

    public static Bitmap mask(Bitmap src, int shape, int[] custom) {
        if (src == null || src.isRecycled()) return src;
        shape = clamp(shape);
        if (shape == SQUARE) return src;
        try {
            int w = src.getWidth(), h = src.getHeight();
            if (w <= 0 || h <= 0) return src;
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            c.drawBitmap(src, 0, 0, null);
            Path p = new Path();
            build(shape, w, h, p, custom);
            Paint xf = new Paint(Paint.ANTI_ALIAS_FLAG);
            xf.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            c.drawPath(p, xf);
            src.recycle();
            return out;
        } catch (Exception e) {
            return src;
        }
    }
}
