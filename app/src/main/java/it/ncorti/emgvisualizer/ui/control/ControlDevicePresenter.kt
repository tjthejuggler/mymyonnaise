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

    private val TAG = "ControlDevicePresenter"

    private val emgDataBuffer = LinkedList<FloatArray>()
    private val bufferSizeHalfSecond = 100 // Assuming 200Hz sampling rate
    private val bufferSizeOneSecond = 200
    private val bufferSizeFiveSeconds = 1000

    private var emgSubscription: Disposable? = null
    private var imuSubscription: Disposable? = null
    private var statusSubscription: Disposable? = null
    private var controlSubscription: Disposable? = null

    private var isUsingPhoneSensors = false
    private var isStreaming = false

    interface ImuDataListener {
        fun onImuDataReceived(imuData: Map<String, Float>)
    }

    var imuDataListener: ImuDataListener? = null
        set(value) {
            field = value
            Log.d(TAG, "ImuDataListener set: $value")
        }

    override fun create() {
        Log.d(TAG, "create() called")
    }

    override fun start() {
        Log.d(TAG, "start() called")
        if (deviceManager.selectedIndex == -1) {
            Log.d(TAG, "No device selected, disabling connect button")
            view.disableConnectButton()
            return
        }

        val selectedDevice = deviceManager.scannedDeviceList[deviceManager.selectedIndex]
        Log.d(TAG, "Selected device: ${selectedDevice.name}, ${selectedDevice.address}")

        view.showDeviceInformation(selectedDevice.name, selectedDevice.address)
        view.enableConnectButton()

        deviceManager.myo?.apply {
            Log.d(TAG, "Setting up Myo subscriptions")
            statusSubscription = this.statusObservable()
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
                            stopStreaming()
                        }
                    }
                }
            controlSubscription = this.controlObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (it == MyoControlStatus.STREAMING) {
                        view.showStreaming()
                    } else {
                        view.showNotStreaming()
                    }
                }
        }
    }

    override fun stop() {
        statusSubscription?.dispose()
        controlSubscription?.dispose()
        stopStreaming()
    }

    override fun onConnectionToggleClicked() {
        Log.d(TAG, "onConnectionToggleClicked() called")
        deviceManager.myo?.apply {
            if (!this.isConnected()) {
                Log.d(TAG, "Attempting to connect to Myo")
                this.connect(myonnaise.context)
            } else {
                Log.d(TAG, "Disconnecting from Myo")
                this.disconnect()
                view.showDisconnected()
            }
        }
    }

    override fun onStreamingToggleClicked() {
        if (isStreaming) {
            stopStreaming()
        } else {
            startStreaming()
        }
    }

    fun onSensorSourceChanged(usePhoneSensors: Boolean) {
        if (isStreaming) {
            stopStreaming()
        }
        isUsingPhoneSensors = usePhoneSensors
        view.updateStreamingSource(if (isUsingPhoneSensors) "Phone Sensors" else "Myo Armband")
    }

    private fun startStreaming() {
        Log.d(TAG, "Phone sensor streaming started")
        if (isUsingPhoneSensors) {
            (view as? ControlDeviceFragment)?.startPhoneSensorStreaming()
        } else {
            startMyoStreaming()
        }
        isStreaming = true
        view.showStreaming()
    }

    private fun stopStreaming() {
        if (isUsingPhoneSensors) {
            (view as? ControlDeviceFragment)?.stopPhoneSensorStreaming()
        } else {
            stopMyoStreaming()
        }
        isStreaming = false
        view.showNotStreaming()
    }

    private fun startMyoStreaming() {
        Log.d(TAG, "Starting EMG and IMU streaming")
        deviceManager.myo?.apply {
            val emgSuccess = this.sendCommand(CommandList.emgFilteredOnly())
            val imuSuccess = this.sendCommandWithRetry(byteArrayOf(0x01, 0x03, 0x03, 0x01, 0x01))
            Log.d(TAG, "EMG streaming command sent: $emgSuccess")
            Log.d(TAG, "IMU streaming command sent: $imuSuccess")
            if (emgSuccess && imuSuccess) {
                subscribeToEmgData()
                subscribeToImuData()
            }
        }
    }

    private fun stopMyoStreaming() {
        Log.d(TAG, "Stopping EMG and IMU streaming")
        deviceManager.myo?.apply {
            val success = this.sendCommand(CommandList.stopStreaming())
            Log.d(TAG, "Stop streaming command sent: $success")
        }
        unsubscribeFromEmgData()
        unsubscribeFromImuData()
    }

    override fun onVibrateClicked(duration: Int) {
        Log.d(TAG, "onVibrateClicked() called with duration: $duration")
        deviceManager.myo?.apply {
            val command = when (duration) {
                1 -> CommandList.vibration1()
                2 -> CommandList.vibration2()
                else -> CommandList.vibration3()
            }
            Log.d(TAG, "Sending vibration command: ${command.contentToString()}")
            val success = this.sendCommand(command)
            Log.d(TAG, "Vibration command sent: $success")
        }
    }

    private fun subscribeToEmgData() {
        Log.d(TAG, "Subscribing to EMG data")
        emgSubscription = deviceManager.myo?.dataFlowable()
            ?.subscribeOn(Schedulers.computation())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.buffer(50, TimeUnit.MILLISECONDS)
            ?.subscribe({ emgDataList ->
                for (emgData in emgDataList) {
                    Log.d(TAG, "Received EMG data: ${emgData.contentToString()}")
                    processEmgData(emgData)
                }
            }, { error ->
                Log.e(TAG, "Error receiving EMG data", error)
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
        Log.d(TAG, "Unsubscribing from EMG data")
        emgSubscription?.dispose()
        emgSubscription = null
    }

    private fun subscribeToImuData() {
        Log.d(TAG, "Subscribing to IMU data")
        imuSubscription = deviceManager.getImuDataFlowable()
            ?.subscribeOn(Schedulers.computation())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe({ imuData: ImuData ->
                Log.d(TAG, "IMU data received: orientation=${imuData.orientation.contentToString()}, " +
                        "accelerometer=${imuData.accelerometer.contentToString()}, " +
                        "gyroscope=${imuData.gyroscope.contentToString()}")
                view.updateImuData(imuData.orientation, imuData.accelerometer, imuData.gyroscope)

                val imuDataMap = mapOf(
                    "orientationW" to imuData.orientation[0],
                    "orientationX" to imuData.orientation[1],
                    "orientationY" to imuData.orientation[2],
                    "orientationZ" to imuData.orientation[3],
                    "accelerometerX" to imuData.accelerometer[0],
                    "accelerometerY" to imuData.accelerometer[1],
                    "accelerometerZ" to imuData.accelerometer[2],
                    "gyroscopeX" to imuData.gyroscope[0],
                    "gyroscopeY" to imuData.gyroscope[1],
                    "gyroscopeZ" to imuData.gyroscope[2]
                )

                imuDataListener?.onImuDataReceived(imuDataMap)
            }, { error: Throwable ->
                Log.e(TAG, "Error receiving IMU data", error)
            })
    }

    private fun unsubscribeFromImuData() {
        Log.d(TAG, "Unsubscribing from IMU data")
        imuSubscription?.dispose()
        imuSubscription = null
    }

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

    fun onAccelerometerData(values: FloatArray) {
        val imuData = mapOf(
            "accelerometerX" to values[0],
            "accelerometerY" to values[1],
            "accelerometerZ" to values[2]
        )
        imuDataListener?.onImuDataReceived(imuData)
        // Update view with accelerometer data for acceleration indicators
        view.updateImuData(FloatArray(4), values, FloatArray(3)) // Assuming this updates acceleration indicators
    }

    fun onGyroscopeData(values: FloatArray) {
        val imuData = mapOf(
            "gyroscopeX" to values[0],
            "gyroscopeY" to values[1],
            "gyroscopeZ" to values[2]
        )
        imuDataListener?.onImuDataReceived(imuData)
        // Update view with gyroscope data for rotation rate indicators
        view.updateImuData(FloatArray(4), FloatArray(3), values) // Assuming this updates rotation rate indicators
    }
}