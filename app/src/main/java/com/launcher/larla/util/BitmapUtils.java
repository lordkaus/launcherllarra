package com.launcher.larla.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class BitmapUtils {

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmapFromResource(String path, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    public static Bitmap decodeSampledBitmapFromResource(int resId, int reqWidth, int reqHeight, Context context) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), resId, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(context.getResources(), resId, options);
    }

    public static Bitmap loadIconScaled(ResolveInfo ri, PackageManager pm, int targetSizePx) {
        if (targetSizePx <= 0) targetSizePx = 96;
        Drawable icon = ri.loadIcon(pm);
        if (icon == null) return null;

        if (icon instanceof BitmapDrawable) {
            Bitmap original = ((BitmapDrawable) icon).getBitmap();
            if (original != null) {
                try {
                    if (original.getWidth() <= targetSizePx && original.getHeight() <= targetSizePx) {
                        // Copia: o bitmap pertence ao cache de recursos e pode estar
                        // compartilhado com outras telas (gaveta, dock, área de trabalho).
                        Bitmap copy = original.copy(Bitmap.Config.ARGB_8888, false);
                        if (copy != null) return copy;
                    } else {
                        return Bitmap.createScaledBitmap(original, targetSizePx, targetSizePx, true);
                    }
                } catch (Exception ignored) { }
            }
        }

        int w = icon.getIntrinsicWidth();
        int h = icon.getIntrinsicHeight();
        if (w <= 0 || h <= 0) {
            w = targetSizePx;
            h = targetSizePx;
        }
        float scale = Math.max(targetSizePx / (float) w, targetSizePx / (float) h);
        int outW = Math.round(w * scale);
        int outH = Math.round(h * scale);
        Bitmap bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        icon.setBounds(0, 0, outW, outH);
        icon.draw(canvas);
        return bitmap;
    }
}
