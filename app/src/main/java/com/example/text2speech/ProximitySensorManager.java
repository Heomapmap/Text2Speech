package com.example.text2speech;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * ProximitySensorManager – quản lý toàn bộ logic cảm biến tiệm cận (TYPE_PROXIMITY).
 *
 * Chức năng: khi người dùng đưa tay/mặt lại gần cảm biến, tự động
 * play hoặc pause TTS mà không cần chạm vào màn hình.
 *
 * Cách dùng từ Activity:
 *   1. Khởi tạo một lần trong onCreate()
 *   2. Gọi register() trong onResume() nếu đang bật
 *   3. Gọi unregister() trong onPause()
 *   4. Gọi setEnabled(true/false) khi người dùng bật/tắt Switch
 */
public class ProximitySensorManager {

    private static final String TAG = "ProximitySensor";

    // Thời gian chờ tối thiểu giữa 2 lần kích hoạt (ms) – tránh kích hoạt nhiều lần liên tiếp
    private static final long COOLDOWN_MS = 1000;

    private final SensorManager sensorManager;
    private final Sensor proximitySensor;
    private final OnProximityTriggeredListener listener;

    private boolean isEnabled = false;
    private long lastTriggerTime = 0;

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_PROXIMITY) return;

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTriggerTime < COOLDOWN_MS) return;

            // Khi giá trị nhỏ hơn max → có vật thể gần cảm biến
            if (event.values[0] < proximitySensor.getMaximumRange()) {
                lastTriggerTime = currentTime;
                if (listener != null) {
                    listener.onProximityTriggered();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    /**
     * Interface callback – Activity implement để nhận sự kiện cảm biến
     */
    public interface OnProximityTriggeredListener {
        void onProximityTriggered();
    }

    /**
     * @param sensorManager  lấy từ getSystemService(Context.SENSOR_SERVICE)
     * @param listener       callback xử lý khi cảm biến kích hoạt
     */
    public ProximitySensorManager(SensorManager sensorManager,
                                   OnProximityTriggeredListener listener) {
        this.sensorManager = sensorManager;
        this.listener = listener;
        this.proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        if (proximitySensor == null) {
            Log.e(TAG, "Thiết bị không hỗ trợ cảm biến tiệm cận!");
        }
    }


    /** Trả về true nếu thiết bị có cảm biến tiệm cận */
    public boolean isSupported() {
        return proximitySensor != null;
    }


    /**
     * Bật hoặc tắt tính năng cảm biến.
     * Khi tắt, listener sẽ không bao giờ được gọi cho dù sensor vẫn đang đăng ký.
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }

    public boolean isEnabled() {
        return isEnabled;
    }


    /**
     * Đăng ký lắng nghe cảm biến – gọi trong onResume() của Activity.
     * Chỉ đăng ký nếu tính năng đang bật và thiết bị hỗ trợ.
     */
    public void register() {
        if (!isSupported() || !isEnabled) return;
        sensorManager.registerListener(
                sensorEventListener,
                proximitySensor,
                SensorManager.SENSOR_DELAY_NORMAL
        );
    }

    /**
     * Huỷ đăng ký cảm biến – gọi trong onPause() của Activity.
     * Tiết kiệm pin khi Activity không hiển thị.
     */
    public void unregister() {
        if (!isSupported()) return;
        sensorManager.unregisterListener(sensorEventListener);
    }
}
