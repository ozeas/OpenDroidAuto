package it.smg.hu.service;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.SurfaceView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import it.smg.hu.config.Settings;
import it.smg.hu.manager.HondaConnectManager;
import it.smg.hu.manager.USBManager;
import it.smg.hu.manager.WIFIManager;
import it.smg.hu.projection.InputDevice;
import it.smg.hu.ui.notification.NotificationFactory;
import it.smg.libs.aasdk.tcp.TCPConnectException;
import it.smg.libs.common.Log;
import it.smg.libs.aasdk.service.AndroidAutoEntity;
import it.smg.libs.aasdk.service.IAndroidAutoEntityEventHandler;
import it.smg.libs.aasdk.tcp.TCPEndpoint;
import it.smg.libs.aasdk.usb.LibUsbDevice;

public class ODAService extends Service implements IAndroidAutoEntityEventHandler {

    public static final String START_ACTION = "it.smg.hu.service.ODAService.START_ACTION";
    public static final String STOP_ACTION = "it.smg.hu.service.ODAService.STOP_ACTION";
    public static final String STOP_VIDEO_INDICATION = "it.smg.hu.service.ODAService.STOP_VIDEO_INDICATION";
    public static final String FORCE_CLOSE_ACTION = "it.smg.hu.service.ODAService.FORCE_CLOSE_ACTION";

    public static final String EXTRA_START_MODE = "startMode";
    public static final String MODE_USB = "modeUSB";
    public static final String MODE_WIFI = "modeWifi";

    private static final String TAG = "ODAService";

    private final IBinder mBinder = new ServiceBinder();

    private LocalBroadcastManager localBroadcastManager_;
    private NotificationFactory notificationFactory_;
    private AndroidAutoEntity androidAutoEntity_;

    private  USBManager usbManager_;
    private  WIFIManager wifiManager_;

    private Thread startThread_;

    private Handler mainHandler_;

    private boolean isRunning_;

    private SurfaceView surfaceView_;
    private InputDevice.OnKeyHolder keyHolder_;
    private String startMode_;
    private int reconnectAttempts_ = 0;
    private boolean reconnecting_ = false;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final long RECONNECT_BASE_DELAY_MS = 2000;
    // A session that survives this long without an error is considered stable;
    // the reconnect budget resets so later isolated errors don't accumulate.
    private static final long STABLE_WINDOW_MS = 30000;
    private long lastErrorTimeMs_ = 0;

    private BroadcastReceiver usbDetachReceiver_;

    public ODAService() {}

    @Override
    public void onCreate() {
        if (Log.isInfo()) Log.i(TAG, "create");
        super.onCreate();

        NotificationFactory.init(getApplicationContext());
        notificationFactory_ = NotificationFactory.instance();

        usbManager_ = USBManager.instance();
        wifiManager_ = WIFIManager.instance();
        mainHandler_ = new Handler(Looper.getMainLooper());

        localBroadcastManager_ = LocalBroadcastManager.getInstance(this);

        usbDetachReceiver_ = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction()) && isRunning_) {
                    if (Log.isInfo()) Log.i(TAG, "AOAP device detached, shutting down cleanly");
                    reconnectAttempts_ = MAX_RECONNECT_ATTEMPTS;
                    onAndroidAutoQuit();
                }
            }
        };
        IntentFilter detachFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbDetachReceiver_, detachFilter);
    }

    public void startUsb(SurfaceView surfaceView, InputDevice.OnKeyHolder keyHolder){
        surfaceView_ = surfaceView;
        keyHolder_ = keyHolder;
        startMode_ = MODE_USB;
        if (!reconnecting_) {
            reconnectAttempts_ = 0;
        }

        startThread_ = new Thread(() -> {
            Looper.prepare();

            if (usbManager_.aoapDevice() != null) {
                isRunning_ = true;

                if (Log.isVerbose()) Log.v(TAG, "aoap device available, start in usb mode");
                try {
                    LibUsbDevice device = usbManager_.aoapDevice();
                    if (device.open()) {
                        if (Log.isInfo()) Log.i(TAG, "device opened");
                        androidAutoEntity_ = AndroidAutoEntityFactory.create(this, device, surfaceView, keyHolder);
                        androidAutoEntity_.start(this);
                    } else {
                        Log.e(TAG, "Error in open usb device");
                        onAndroidAutoQuitOnError("USB OPEN DEVICE", -1);
                        return;
                    }
                } catch (Exception e){
                    Log.e(TAG, "error", e);
                    onAndroidAutoQuitOnError("USB GENERIC ERROR", -1);
//                    onAndroidAutoQuit();
                    return;
                }
            }
            if (Log.isInfo()) Log.i(TAG, "start usb thead completed");
        });
        startThread_.start();
    }

    public void startWifi(SurfaceView surfaceView, InputDevice.OnKeyHolder keyHolder){
        surfaceView_ = surfaceView;
        keyHolder_ = keyHolder;
        startMode_ = MODE_WIFI;
        if (!reconnecting_) {
            reconnectAttempts_ = 0;
        }

        startThread_ = new Thread(() -> {
            Looper.prepare();

            try {
                String ipAddress = wifiManager_.getIpAddress();
                if (ipAddress != null) {
                    isRunning_ = true;

//                    if (Log.isInfo()) Log.i(TAG, "Connect to ip " + ipAddress);
                    TCPEndpoint tcpEndpoint = new TCPEndpoint(ipAddress);
                    androidAutoEntity_ = AndroidAutoEntityFactory.create(this, tcpEndpoint, surfaceView, keyHolder);
                    androidAutoEntity_.start(this);
                }
                if (Log.isInfo()) Log.i(TAG, "start wifi thead completed");
            } catch (TCPConnectException e){
                Log.e(TAG, "TCP Connection error", e);
                stop();
            }
        });
        startThread_.start();
    }

    public void shutdown(){
        androidAutoEntity_.shutdown();
    }

    public void releaseFocus(){
        if (androidAutoEntity_ != null) {
            androidAutoEntity_.releaseFocus();
        }
    }

    public void gainFocus(){
        androidAutoEntity_.gainFocus();
    }

    public void stop(){
        if (!isRunning_) {
            if (Log.isInfo()) Log.i(TAG, "service not running, already stopped?");
            return;
        }

        isRunning_ = false;

        if (Log.isInfo()) Log.i(TAG, "Stop");

        if (Settings.instance().advanced.hondaIntegrationEnabled()){
            HondaConnectManager.instance().endAudioBinding();
        }

        if (androidAutoEntity_ != null) {
            androidAutoEntity_.stop();
        }

        if (androidAutoEntity_ != null) {
            androidAutoEntity_.delete();
            androidAutoEntity_ = null;
        }

        startThread_ = null;

        finishService();
    }

    private void finishService(){
        Intent stopIntent = new Intent(ODAService.STOP_ACTION);
        localBroadcastManager_.sendBroadcast(stopIntent);

        Intent service = new Intent(this, ODAService.class);
        stopService(service);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void onDestroy() {
        if (Log.isDebug()) Log.d(TAG, "onDestroy");
        if (usbDetachReceiver_ != null) {
            try {
                unregisterReceiver(usbDetachReceiver_);
            } catch (IllegalArgumentException ignored) {}
            usbDetachReceiver_ = null;
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (Log.isDebug()) Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Log.isDebug()) Log.d(TAG, "Received start id " + startId + ": " + intent);

        Notification notification = notificationFactory_.create();
        startForeground(123456, notification);

//        return START_NOT_STICKY;
        return START_STICKY;
    }

    @Keep
    @Override
    public void onAndroidAutoQuit() {
        stop();
    }

    private void teardownEntity() {
        if (androidAutoEntity_ != null) {
            androidAutoEntity_.stop();
            androidAutoEntity_.delete();
            androidAutoEntity_ = null;
        }
        isRunning_ = false;
        startThread_ = null;
    }

    private void scheduleReconnect() {
        if (reconnectAttempts_ >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "max reconnect attempts reached, stopping");
            if (Settings.instance().advanced.hondaIntegrationEnabled()) {
                HondaConnectManager.instance().endAudioBinding();
            }
            finishService();
            return;
        }

        reconnectAttempts_++;
        long delay = RECONNECT_BASE_DELAY_MS * (1L << (reconnectAttempts_ - 1));
        Log.i(TAG, "scheduling reconnect attempt " + reconnectAttempts_ + "/" + MAX_RECONNECT_ATTEMPTS + " in " + delay + "ms (mode=" + startMode_ + ")");

        mainHandler_.postDelayed(() -> {
            reconnecting_ = true;
            try {
                if (MODE_USB.equals(startMode_)) {
                    if (usbManager_.aoapDevice() == null) {
                        usbManager_.searchForAoapDevice();
                    }
                    if (usbManager_.aoapDevice() != null) {
                        startUsb(surfaceView_, keyHolder_);
                    } else {
                        Log.w(TAG, "no AOAP device available for reconnect, stopping");
                        if (Settings.instance().advanced.hondaIntegrationEnabled()) {
                            HondaConnectManager.instance().endAudioBinding();
                        }
                        finishService();
                    }
                } else if (MODE_WIFI.equals(startMode_)) {
                    startWifi(surfaceView_, keyHolder_);
                } else {
                    Log.w(TAG, "unknown start mode for reconnect, stopping");
                    if (Settings.instance().advanced.hondaIntegrationEnabled()) {
                        HondaConnectManager.instance().endAudioBinding();
                    }
                    finishService();
                }
            } finally {
                reconnecting_ = false;
            }
        }, delay);
    }

    @Keep
    @Override
    public void onAndroidAutoQuitOnError(String error, int nativeErrorCode){
        Log.e(TAG, "closing with error " + error + "(" + nativeErrorCode + ")");

        mainHandler_.post(() -> {
            Toast.makeText(this, "Closed due to " + error + " error", Toast.LENGTH_LONG).show();
        });

        long now = System.currentTimeMillis();
        if (lastErrorTimeMs_ > 0 && now - lastErrorTimeMs_ > STABLE_WINDOW_MS) {
            Log.i(TAG, "session was stable for " + (now - lastErrorTimeMs_) + "ms, resetting reconnect budget");
            reconnectAttempts_ = 0;
        }
        lastErrorTimeMs_ = now;

        teardownEntity();
        scheduleReconnect();
    }

    @Keep
    @Override
    public void onAVChannelStopIndication() {
        if (Log.isInfo()) Log.i(TAG, "stop video indication");
        Intent stopIntent = new Intent(ODAService.STOP_VIDEO_INDICATION);
        localBroadcastManager_.sendBroadcast(stopIntent);
    }

    public class ServiceBinder extends Binder {
        public ODAService getService() {
            return ODAService.this;
        }
    }

}
