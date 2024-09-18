package it.ncorti.emgvisualizer.ui.scan

import android.bluetooth.BluetoothDevice
import android.content.Context
import it.ncorti.emgvisualizer.BasePresenter
import it.ncorti.emgvisualizer.BaseView
import it.ncorti.emgvisualizer.ui.model.Device

interface ScanDeviceContract {

    interface View : BaseView {

        fun showStartMessage()

        fun showEmptyListMessage()

        fun hideEmptyListMessage()

        fun wipeDeviceList()

        fun addDeviceToList(device: Device)

        fun populateDeviceList(list: List<Device>)

        fun showScanLoading()

        fun hideScanLoading()

        fun showScanError()

        fun showScanCompleted()

        fun navigateToControlDevice()

        fun showConnectedDevice(device: Device)
        fun showConnectionError(message: String)


        fun getContext(): Context
    }

    abstract class Presenter(override val view: BaseView) : BasePresenter<BaseView>(view) {

        abstract fun onScanToggleClicked()

        abstract fun onDeviceSelected(device: Device)
    }
}
