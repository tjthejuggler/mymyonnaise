@file:Suppress("ComplexMethod")

package it.ncorti.emgvisualizer.ui.control

import android.util.Log
import com.ncorti.myonnaise.CommandList
import com.ncorti.myonnaise.ImuData
import com.ncorti.myonnaise.MYO_MAX_FREQUENCY
import com.ncorti.myonnaise.MyoControlStatus
import com.ncorti.myonnaise.MyoStatus
import com.ncorti.myonnaise.Myonnaise
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import it.ncorti.emgvisualizer.dagger.DeviceManager
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ControlDevicePresenter @Inject constructor(
    override val view: ControlDeviceContract.View,
    private val myonnaise: Myonnaise,
    private val deviceManager: DeviceManager
) : ControlDeviceContract.Presenter(view) {


    private val emgDataBuffer = LinkedList<FloatArray>()
    private val bufferSizeHalfSecond = 100 // Assuming 200Hz sampling rate
    private val bufferSizeOneSecond = 200
    private val bufferSizeFiveSeconds = 1000

    private var emgSubscription: Disposable? = null
    private var imuSubscription: Disposable? = null

    init {
        Log.d("ControlDevicePresenter", "Presenter initialized")
    }



    internal var statusSubscription: Disposable? = null
    internal var controlSubscription: Disposable? = null

    override fun create() {
        Log.d("ControlDevicePresenter", "create() called")
    }

    override fun start() {
        Log.d("ControlDevicePresenter", "start() called")
        Log.d("ControlDevicePresenter", "DeviceManager instance: $deviceManager")
        Log.d("ControlDevicePresenter", "DeviceManager selectedIndex: ${deviceManager.selectedIndex}")
        Log.d("ControlDevicePresenter", "DeviceManager scannedDeviceList size: ${deviceManager.scannedDeviceList.size}")
        Log.d("ControlDevicePresenter", "Myo object: ${deviceManager.myo}")

        if (deviceManager.selectedIndex == -1) {
            Log.d("ControlDevicePresenter", "No device selected, disabling connect button")
            view.disableConnectButton()
            return
        }

        val selectedDevice = deviceManager.scannedDeviceList[deviceManager.selectedIndex]
        Log.d("ControlDevicePresenter", "Selected device: ${selectedDevice.name}, ${selectedDevice.address}")

        view.showDeviceInformation(selectedDevice.name, selectedDevice.address)
        view.enableConnectButton()

        deviceManager.myo?.apply {
            Log.d("ControlDevicePresenter", "Myo object exists, setting up subscriptions")
            deviceManager.myo = myonnaise.getMyo(selectedDevice.bluetoothDevice)
        }

        deviceManager.myo?.apply {
            Log.d("ControlDevicePresenter", "Setting up Myo subscriptions")
            statusSubscription =
                this.statusObservable()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        when (it) {
                            MyoStatus.CONNECTED -> {
                                view.showConnected()
                                view.enableControlPanel()
                            }
                            MyoStatus.CONNECTING -> {
                                view.showConnecting()
                            }
                            MyoStatus.READY -> {
                                view.enableControlPanel()
                            }
                            else -> {
                                view.showDisconnected()
                                view.disableControlPanel()
                                stopStreaming() // Stop streaming when disconnected
                            }
                        }
                    }
            controlSubscription =
                this.controlObservable()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        if (it == MyoControlStatus.STREAMING) {
                            view.showStreaming()
                        } else {
                            view.showNotStreaming()
                        }
                    }
        } ?: Log.e("ControlDevicePresenter", "Myo object is null after attempt to create it")
    }

    override fun stop() {
        statusSubscription?.dispose()
        controlSubscription?.dispose()
        unsubscribeFromEmgData()
        unsubscribeFromImuData()
    }

    override fun onConnectionToggleClicked() {
        Log.d("ControlDevicePresenter", "onConnectionToggleClicked() called")
        deviceManager.myo?.apply {
            if (!this.isConnected()) {
                Log.d("ControlDevicePresenter", "Attempting to connect to Myo")
                this.connect(myonnaise.context)
            } else {
                Log.d("ControlDevicePresenter", "Disconnecting from Myo")
                this.disconnect()
                view.showDisconnected()
            }
        } ?: Log.e("ControlDevicePresenter", "Myo object is null when trying to connect/disconnect")
    }

    override fun onStreamingToggleClicked() {
        Log.d("ControlDevicePresenter", "onStreamingToggleClicked() called")
        deviceManager.myo?.apply {
            if (!this.isStreaming()) {
                startStreaming()
            } else {
                stopStreaming()
            }
        } ?: Log.e("ControlDevicePresenter", "Myo object is null when trying to toggle streaming")
    }

    private fun startStreaming() {
        Log.d("ControlDevicePresenter", "Starting EMG streaming")
        deviceManager.myo?.apply {
            val success = this.sendCommand(CommandList.emgFilteredOnly())
            Log.d("ControlDevicePresenter", "EMG streaming command sent: $success")
            if (success) {
                subscribeToEmgData()
                subscribeToImuData()
                view.showStreaming()
            }
        }
    }

    private fun stopStreaming() {
        Log.d("ControlDevicePresenter", "Stopping EMG streaming")
        deviceManager.myo?.apply {
            val success = this.sendCommand(CommandList.stopStreaming())
            Log.d("ControlDevicePresenter", "Stop streaming command sent: $success")
        }
        unsubscribeFromEmgData()
        unsubscribeFromImuData()
        view.showNotStreaming()
    }

    override fun onVibrateClicked(duration: Int) {
        Log.d("ControlDevicePresenter", "onVibrateClicked() called with duration: $duration")
        deviceManager.myo?.apply {
            val command = when (duration) {
                1 -> CommandList.vibration1()
                2 -> CommandList.vibration2()
                else -> CommandList.vibration3()
            }
            Log.d("ControlDevicePresenter", "Sending vibration command: ${command.contentToString()}")
            val success = this.sendCommand(command)
            Log.d("ControlDevicePresenter", "Vibration command sent: $success")
        } ?: Log.e("ControlDevicePresenter", "Myo object is null when trying to vibrate")
    }


    private fun subscribeToImuData() {
        Log.d("ControlDevicePresenter", "Subscribing to IMU data")
        imuSubscription = deviceManager.getImuDataFlowable()
            ?.subscribeOn(Schedulers.computation())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe({ imuData: ImuData ->

                for(i in 0..2) {
                    Log.d("ControlDevicePresenter", "IMU data received: ${imuData.orientation[i]}")
                }
                view.updateImuData(imuData.orientation, imuData.accelerometer, imuData.gyroscope)
            }, { error: Throwable ->
                Log.e("ControlDevicePresenter", "Error receiving IMU data", error)
            })
    }

    private fun unsubscribeFromImuData() {
        Log.d("ControlDevicePresenter", "Unsubscribing from IMU data")
        imuSubscription?.dispose()
        imuSubscription = null
    }
    private fun subscribeToEmgData() {
        Log.d("ControlDevicePresenter", "Subscribing to EMG data")
        emgSubscription = deviceManager.myo?.dataFlowable()
            ?.subscribeOn(Schedulers.computation())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.buffer(50, TimeUnit.MILLISECONDS) // Buffer for 50ms to reduce UI updates
            ?.subscribe({ emgDataList ->

                for (emgData in emgDataList) {
                    Log.d("ControlDevicePresenter", "Received EMG data: ${emgData.contentToString()}")
                    processEmgData(emgData)
                }
            }, { error ->
                Log.e("ControlDevicePresenter", "Error receiving EMG data", error)
            })
    }



    private fun processEmgData(emgData: FloatArray) {
        emgDataBuffer.add(emgData)
        if (emgDataBuffer.size > bufferSizeFiveSeconds) {
            emgDataBuffer.removeFirst()
        }

        val halfSecondAvg = calculateAverage(bufferSizeHalfSecond)
        val oneSecondAvg = calculateAverage(bufferSizeOneSecond)
        val fiveSecondAvg = calculateAverage(bufferSizeFiveSeconds)

        view.updateAveragedEmgData(halfSecondAvg, oneSecondAvg, fiveSecondAvg)
    }

    private fun calculateAverage(sampleSize: Int): FloatArray {
        val sum = FloatArray(8) { 0f }
        val actualSize = minOf(sampleSize, emgDataBuffer.size)
        for (i in emgDataBuffer.size - actualSize until emgDataBuffer.size) {
            for (j in 0..7) {
                sum[j] += emgDataBuffer[i][j]
            }
        }
        return FloatArray(8) { sum[it] / actualSize }
    }

    private fun unsubscribeFromEmgData() {
        Log.d("ControlDevicePresenter", "Unsubscribing from EMG data")
        emgSubscription?.dispose()
        emgSubscription = null
    }

    @Suppress("MagicNumber")
    override fun onProgressSelected(progress: Int) {
        val selectedFrequency = when (progress) {
            0 -> 1
            1 -> 2
            2 -> 5
            3 -> 10
            4 -> 25
            5 -> 50
            6 -> 100
            else -> MYO_MAX_FREQUENCY
        }
        view.showFrequency(selectedFrequency)
        deviceManager.myo?.apply {
            this.frequency = selectedFrequency
        }
    }


}
