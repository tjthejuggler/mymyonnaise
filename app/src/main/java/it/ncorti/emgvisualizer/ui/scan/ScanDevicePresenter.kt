package it.ncorti.emgvisualizer.ui.scan

import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.fragment.app.Fragment
import com.ncorti.myonnaise.Myo
import com.ncorti.myonnaise.MyoStatus
import com.ncorti.myonnaise.Myonnaise
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import it.ncorti.emgvisualizer.dagger.DeviceManager
import it.ncorti.emgvisualizer.ui.MainActivity
import it.ncorti.emgvisualizer.ui.model.Device
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TAG = "ScanDevicePresenter"
const val SCAN_INTERVAL_SECONDS = 10L

class ScanDevicePresenter @Inject constructor(
    override val view: ScanDeviceContract.View,
    private val myonnaise: Myonnaise,
    private val deviceManager: DeviceManager
) : ScanDeviceContract.Presenter(view) {

    private var statusSubscription: Disposable? = null
    private lateinit var scanFlowable: Flowable<BluetoothDevice>
    internal var scanSubscription: Disposable? = null

    init {
        createScanFlowable()
    }

    private fun createScanFlowable() {
        scanFlowable = myonnaise.startScan(SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    override fun create() {
        Log.d(TAG, "create: Initializing scan flowable")
        scanFlowable = myonnaise.startScan(SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    override fun start() {
        Log.d(TAG, "start: Starting presenter")
        view.wipeDeviceList()
        if (deviceManager.scannedDeviceList.isEmpty()) {
            Log.d(TAG, "start: No devices in list, showing start message")
            view.showStartMessage()
        } else {
            Log.d(TAG, "start: Populating device list with ${deviceManager.scannedDeviceList.size} devices")
            view.populateDeviceList(deviceManager.scannedDeviceList)
        }
    }

    override fun stop() {
        Log.d(TAG, "stop: Stopping presenter")
        scanSubscription?.dispose()
        view.hideScanLoading()
    }

    private val activity: MainActivity?
        get() = (view as? Fragment)?.activity as? MainActivity

    override fun onScanToggleClicked() {
        if (activity?.checkBluetoothPermissions() == true) {
            toggleScan()
        } else {
            activity?.requestBluetoothPermissions()
        }
    }

    private fun toggleScan() {
        if (scanSubscription?.isDisposed == false) {
            Log.d(TAG, "toggleScan: Stopping scan")
            scanSubscription?.dispose()
            view.hideScanLoading()
            if (deviceManager.scannedDeviceList.isEmpty()) {
                Log.d(TAG, "toggleScan: No devices found after scan")
                view.showEmptyListMessage()
            }
        } else {
            Log.d(TAG, "toggleScan: Starting scan")
            view.hideEmptyListMessage()
            view.showScanLoading()
            scanSubscription = scanFlowable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { bluetoothDevice ->
                        Log.d(TAG, "toggleScan: Device found - Name: ${bluetoothDevice.name}, Address: ${bluetoothDevice.address}")
                        val device = Device(bluetoothDevice)
                        if (device !in deviceManager.scannedDeviceList) {
                            view.addDeviceToList(device)
                            deviceManager.scannedDeviceList.add(device)
                        }
                    },
                    { error ->
                        Log.e(TAG, "toggleScan: Error during scan", error)
                        view.hideScanLoading()
                        view.showScanError()
                        if (deviceManager.scannedDeviceList.isEmpty()) {
                            view.showEmptyListMessage()
                        }
                    },
                    {
                        Log.d(TAG, "toggleScan: Scan completed")
                        view.hideScanLoading()
                        view.showScanCompleted()
                        if (deviceManager.scannedDeviceList.isEmpty()) {
                            view.showEmptyListMessage()
                        }
                    }
                )
        }
    }


    override fun onDeviceSelected(device: Device) {
        Log.d("ScanDevicePresenter", "Device selected: ${device.name}, ${device.address}")
        deviceManager.selectedIndex = deviceManager.scannedDeviceList.indexOf(device)
        // Stop scanning
        stopScan()

        // Use the bluetoothDevice property of the Device class
        deviceManager.myo = myonnaise.getMyo(device.bluetoothDevice)

        // Attempt to connect to the selected device
        deviceManager.myo?.connect(view.getContext())

        // Observe the connection status
        statusSubscription = deviceManager.myo?.statusObservable()
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe { status ->
                when (status) {
                    MyoStatus.CONNECTED -> {
                        view.showConnectedDevice(device)
                    }
                    MyoStatus.READY -> {
                        // The device is ready to receive commands
                        view.navigateToControlDevice()
                    }
                    MyoStatus.DISCONNECTED -> {
                        view.showConnectionError("Device disconnected")
                    }
                    else -> {
                        // Handle other states if needed
                    }
                }
            }
    }

    fun onCleared() {
        statusSubscription?.dispose()
    }

    private fun stopScan() {
        scanSubscription?.dispose()
        view.hideScanLoading()
    }
}