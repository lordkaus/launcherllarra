package com.launcher.larla.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Repassa broadcasts de pacote ao listener dinâmico da activity. */
public class PackageReceiver extends BroadcastReceiver {

    private static final String EXTRA_REPLACING = "android.intent.extra.REPLACING";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        Uri data = intent.getData();
        if (data == null) return;
        String pkg = data.getSchemeSpecificPart();
        if (pkg == null || pkg.isEmpty()) return;

        PackageListener listener;
        synchronized (PackageReceiver.class) { listener = sListener; }
        if (listener != null) {
            listener.onPackageChanged(intent.getAction(), pkg,
                intent.getBooleanExtra(EXTRA_REPLACING, false));
        }
    }

    /** Listener dinâmico: registrado pela activity enquanto o processo vive. */
    public interface PackageListener {
        void onPackageChanged(String action, String pkg, boolean replacing);
    }

    private static PackageListener sListener;

    public static void setListener(PackageListener l) {
        synchronized (PackageReceiver.class) { sListener = l; }
    }
}
