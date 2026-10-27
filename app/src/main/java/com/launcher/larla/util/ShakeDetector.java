package com.launcher.larla.util;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/** Detector de chacoalhar estilo Moto: pico de força-g com slope e cooldown. */
public class ShakeDetector implements SensorEventListener {

    public interface Listener { void onShake(); }

    private static final float THRESHOLD_G = 2.7f;
    private static final long SLOPE_MS = 500;
    private static final long COOLDOWN_MS = 1500;

    private final SensorManager manager;
    private final Sensor sensor;
    private final Listener listener;
    private long lastPeak;
    private long lastFire;

    public ShakeDetector(Context context, Listener listener) {
        Context app = context.getApplicationContext();
        this.manager = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        this.sensor = manager != null
                ? manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
        this.listener = listener;
    }

    public boolean isAvailable() { return manager != null && sensor != null; }

    public void start() {
        if (!isAvailable()) return;
        lastPeak = 0;
        lastFire = 0;
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
    }

    public void stop() {
        if (manager != null) {
            try { manager.unregisterListener(this); } catch (Exception ignored) { }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null || event.values.length < 3) return;
        float x = event.values[0] / SensorManager.GRAVITY_EARTH;
        float y = event.values[1] / SensorManager.GRAVITY_EARTH;
        float z = event.values[2] / SensorManager.GRAVITY_EARTH;
        float g = (float) Math.sqrt(x * x + y * y + z * z);
        long now = System.currentTimeMillis();
        if (g > THRESHOLD_G) {
            if (lastPeak != 0 && now - lastPeak < SLOPE_MS && now - lastFire > COOLDOWN_MS) {
                lastFire = now;
                if (listener != null) listener.onShake();
            }
            lastPeak = now;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
