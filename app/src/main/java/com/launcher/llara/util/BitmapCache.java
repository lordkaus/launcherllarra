package com.launcher.llara.util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

public class BitmapCache {

    private static BitmapCache instance;
    private final LruCache<String, Drawable> cache;

    private BitmapCache() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 16;
        cache = new LruCache<String, Drawable>(cacheSize) {
            @Override
            protected int sizeOf(String key, Drawable drawable) {
                if (drawable instanceof BitmapDrawable) {
                    Bitmap bm = ((BitmapDrawable) drawable).getBitmap();
                    if (bm != null) return bm.getByteCount() / 1024;
                }
                return 8;
            }
        };
    }

    public static synchronized BitmapCache getInstance() {
        if (instance == null) {
            instance = new BitmapCache();
        }
        return instance;
    }

    public Drawable get(String key) {
        if (key == null) return null;
        return cache.get(key);
    }

    public void put(String key, Drawable drawable) {
        if (key == null || drawable == null) return;
        cache.put(key, drawable);
    }

    public void remove(String key) {
        if (key != null) cache.remove(key);
    }

    /** Remove a chave exata e todas as variantes de máscara (pkg|...). */
    public void removeAllFor(String pkg) {
        if (pkg == null) return;
        try {
            cache.remove(pkg);
            java.util.Map<String, Drawable> snap = cache.snapshot();
            if (snap == null) return;
            String prefix = pkg + "|";
            for (String k : new java.util.ArrayList<>(snap.keySet())) {
                if (k != null && k.startsWith(prefix)) cache.remove(k);
            }
        } catch (Exception ignored) { }
    }

    public void evictAll() {
        cache.evictAll();
    }

    public void trimTo(int maxKb) {
        try { cache.trimToSize(maxKb); } catch (Exception ignored) { cache.evictAll(); }
    }

    public int size() {
        return cache.size();
    }

    public int maxSize() {
        return cache.maxSize();
    }

    public static Drawable bitmapToDrawable(Resources res, Bitmap bitmap) {
        if (bitmap == null) return null;
        return new BitmapDrawable(res, bitmap);
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        return null;
    }
}
