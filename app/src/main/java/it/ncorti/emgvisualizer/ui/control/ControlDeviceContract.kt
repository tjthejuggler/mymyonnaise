package it.ncorti.emgvisualizer.ui.control

import it.ncorti.emgvisualizer.BasePresenter
import it.ncorti.emgvisualizer.BaseView

interface ControlDeviceContract {

    interface View : BaseView {
        fun showDeviceInformation(name: String?, address: String)
        fun showConnectionProgress()
        fun hideConnectionProgress()
        fun showConnected()
        fun showDisconnected()
        fun showConnecting()
        fun showConnectionError()
        fun enableConnectButton()
        fun disableConnectButton()
        fun enableControlPanel()
        fun disableControlPanel()
        fun showStreaming()
        fun showNotStreaming()
        fun updateEmgData(emgData: FloatArray)
        fun showFrequency(frequency: Int)
        fun updateImuData(orientation: FloatArray, accelerometer: FloatArray, gyroscope: FloatArray)
        fun updateAveragedEmgData(halfSecondAvg: FloatArray, oneSecondAvg: FloatArray, fiveSecondAvg: FloatArray)
        fun updateStreamingSource(source: String)
    }

    abstract class Presenter(override val view: BaseView) : BasePresenter<BaseView>(view) {
        abstract fun onConnectionToggleClicked()
        abstract fun onStreamingToggleClicked()
        abstract fun onVibrateClicked(duration: Int)
        abstract fun onProgressSelected(progress: Int)
    }
}