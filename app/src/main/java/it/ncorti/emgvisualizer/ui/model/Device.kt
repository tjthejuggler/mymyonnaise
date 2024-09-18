package it.ncorti.emgvisualizer.ui.model

import android.bluetooth.BluetoothDevice

data class Device(val bluetoothDevice: BluetoothDevice) {
    val name: String? get() = bluetoothDevice.name
    val address: String get() = bluetoothDevice.address
}