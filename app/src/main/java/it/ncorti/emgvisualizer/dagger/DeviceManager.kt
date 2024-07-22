package it.ncorti.emgvisualizer.dagger

import android.bluetooth.BluetoothDevice
import com.ncorti.myonnaise.Myo
import it.ncorti.emgvisualizer.ui.model.Device

class DeviceManager {
    var selectedIndex: Int = -1
        set(value) {
            if (value != field) {
                myo = null
            }
            field = value
        }

    var scannedDeviceList: MutableList<Device> = mutableListOf()
    var myo: Myo? = null
    var connected = myo?.isConnected() ?: false
}