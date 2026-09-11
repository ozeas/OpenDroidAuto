package it.smg.hu.projection;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;

import androidx.annotation.Keep;

import java.util.Set;

import it.smg.hu.config.Settings;
import it.smg.libs.common.Log;

public class BluetoothDevice extends it.smg.libs.aasdk.projection.BluetoothDevice {

    private static final String TAG = "BluetoothDevice";

    private final BluetoothAdapter bluetoothAdapter_;

    public BluetoothDevice(){
        bluetoothAdapter_ = BluetoothAdapter.getDefaultAdapter();
    }

    @Keep
    @Override
    public void stop() {
        if (Log.isDebug()) Log.d(TAG, "stop (do nothing)");
    }

    @Keep
    @Override
    public boolean isPaired(String address) {
        if (Log.isInfo()) Log.i(TAG, "isPaired phoneAddress: " + address);

        if (address != null && !address.trim().isEmpty()) {
            Set<android.bluetooth.BluetoothDevice> pairedDevices = bluetoothAdapter_.getBondedDevices();
            // If there are paired devices
            if (!pairedDevices.isEmpty()) {
                // Loop through paired devices
                for (android.bluetooth.BluetoothDevice device : pairedDevices) {
                    // Add the name and address to an array adapter to show in a ListView
                    if (Log.isInfo()) Log.i(TAG, "check paired device " + device.getName() + ": " + device.getAddress());
                    if (address.equalsIgnoreCase(device.getAddress())) {
                        if (Log.isInfo()) Log.i(TAG, "found paired device " + device.getName());
                        return true;
                    }
                }
            } else {
                if (Log.isInfo()) Log.i(TAG, "no bonded devices on the head unit");
            }
        }

        if (Log.isInfo()) Log.i(TAG, "phoneAddress null or not paired -> isPaired=false");
        return false;
    }

    @Keep
    @Override
    public boolean pair(String address) {
        if (Log.isInfo()) Log.i(TAG, "pair requested for address: " + address);

        if (address == null || address.trim().isEmpty()) {
            if (Log.isWarn()) Log.w(TAG, "pair: empty address, cannot bond");
            return false;
        }

        try {
            android.bluetooth.BluetoothDevice remote = bluetoothAdapter_.getRemoteDevice(address);
            if (Log.isInfo()) Log.i(TAG, "initiating createBond with " + address);
            boolean started = remote.createBond();
            if (Log.isInfo()) Log.i(TAG, "createBond result: " + started);
            return started;
        } catch (Exception e) {
            Log.e(TAG, "pair error for " + address, e);
            return false;
        }
    }

    @Keep
    @SuppressLint("HardwareIds")
    @Override
    public String getLocalAddress() {
        String macAddress = "";
        if (isAvailable()){
            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                macAddress = bluetoothAdapter_.getAddress();
                if (macAddress == null){
                    macAddress = "";
                }
            } else {
                macAddress = "n/a (SDK > 16)";
            }
        }
        if (Log.isInfo()) Log.i(TAG, "localAddress: '" + macAddress + "'");
        return macAddress;
    }

    @Keep
    @Override
    public boolean isAvailable() {
        if (Log.isInfo()) Log.i(TAG, "isAvailable check");

        if (bluetoothAdapter_ == null) {
            if (Log.isWarn()) Log.w(TAG, "BluetoothAdapter is NULL (not available)");
            return false;
        } else if (!bluetoothAdapter_.isEnabled()) {
            if (Log.isWarn()) Log.w(TAG, "Bluetooth adapter NOT enabled");
            return false;
        } else {
            if (Log.isInfo()) Log.i(TAG, "Bluetooth adapter enabled");
            return true;
        }
    }

    @Keep
    @Override
    public boolean isEnabledAd2p() {
        return Settings.instance().connectivity.enableA2dp();
    }

    @Keep
    @Override
    public boolean isEnabledHfp() {
        return Settings.instance().connectivity.enableHfp();
    }
}
