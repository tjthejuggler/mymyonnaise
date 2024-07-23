package it.ncorti.emgvisualizer.dagger

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.ncorti.myonnaise.Myo
import io.reactivex.Flowable
import it.ncorti.emgvisualizer.ui.model.Device
import javax.inject.Inject
import com.ncorti.myonnaise.ImuData

class DeviceManager @Inject constructor() {
    init {
        Log.d("DeviceManager", "DeviceManager initialized")
    }
    var selectedIndex: Int = -1
        set(value) {
            Log.d("DeviceManager", "Selected device index changing from $field to $value")
            if (value != field) {
                myo = null
                Log.d("DeviceManager", "Myo object cleared due to index change")
            }
            field = value
        }

    var scannedDeviceList: MutableList<Device> = mutableListOf()
        set(value) {
            field = value
            Log.d("DeviceManager", "Scanned device list updated, size: ${value.size}")
        }

    var myo: Myo? = null
        set(value) {
            field = value
            Log.d("DeviceManager", "Myo object ${if (value == null) "cleared" else "set"}: ${value?.address}")
        }

    fun getImuDataFlowable(): Flowable<ImuData>? {
        return myo?.imuDataFlowable()
    }

    var connected: Boolean = false
        get() = myo?.isConnected() ?: false
        set(value) {
            field = value
            Log.d("DeviceManager", "Connected status set to $value")
        }
}