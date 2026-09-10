package it.smg.libs.aasdk.service;

import androidx.annotation.Keep;

import it.smg.libs.aasdk.messenger.Messenger;
import it.smg.libs.aasdk.projection.ISensor;
import it.smg.libs.common.Log;

public class SensorService implements IService, ISensor.Listener {

    private static final String TAG = "SensorService";

    private ISensor sensor_;
    private final IAndroidAutoEntityEventHandler eventHandler_;

    public SensorService(Messenger messenger, IAndroidAutoEntityEventHandler eventHandler, ISensor sensor) {
        handle_ = nativeSetup(messenger, sensor.isNight());
        eventHandler_ = eventHandler;
        sensor_ = sensor;
        sensor_.setListener(this);
    }

    @Override
    public void start() {
        nativeStart();
    }

    @Override
    public void stop() {
        nativeStop();
        sensor_.stop();
        sensor_.setListener(null);
    }

    @Override
    public void delete() {
        nativeDelete();
    }

    @Override
    public void onDayNightUpdate(boolean isNight) {
        sendNightMode(isNight);
    }

    @Override
    public void onGpsUpdate(double latitude, double longitude, float accuracy, double altitude, float speed, float bearing, long timestampMillis) {
        long timestampUs = timestampMillis * 1000L;
        int latitudeE7 = (int) Math.round(latitude * 1E7);
        int longitudeE7 = (int) Math.round(longitude * 1E7);
        int accuracyM = (int) accuracy;
        int altitudeM = (int) altitude;
        int speedMps = (int) speed;
        int bearingDeg = (int) bearing;

        nativeSendGPSLocation(timestampUs, latitudeE7, longitudeE7, accuracyM, altitudeM, speedMps, bearingDeg);
    }

    @Keep
    @Override
    public void onError(String error, int code){
        // Non-fatal: a sensor channel error must not tear down the session.
        Log.w(TAG, "onError " + error + "/" + code);
    }

    static {
        nativeInit();
    }

    private static native void nativeInit();
    private native long nativeSetup(Messenger messenger, boolean isNight);
    private native void nativeStart();
    private native void nativeStop();
    private native void nativeDelete();
    private native void sendNightMode(boolean isNight);
    private native void nativeSendGPSLocation(long timestampUs, int latitude, int longitude, int accuracy, int altitude, int speed, int bearing);

    @Keep
    protected long handle_;

}
