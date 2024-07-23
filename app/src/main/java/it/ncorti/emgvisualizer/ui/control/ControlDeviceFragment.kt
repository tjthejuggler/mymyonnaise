package it.ncorti.emgvisualizer.ui.control

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import dagger.android.support.AndroidSupportInjection
import it.ncorti.emgvisualizer.BaseFragment
import it.ncorti.emgvisualizer.R
import it.ncorti.emgvisualizer.databinding.LayoutControlDeviceBinding
import javax.inject.Inject

class ControlDeviceFragment : BaseFragment<ControlDeviceContract.Presenter>(), ControlDeviceContract.View {

    companion object {
        fun newInstance() = ControlDeviceFragment()
    }

    @Inject
    lateinit var controlDevicePresenter: ControlDevicePresenter

    private var _binding: LayoutControlDeviceBinding? = null
    private val binding get() = _binding!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d("ControlDeviceFragment", "onAttach() called")
        AndroidSupportInjection.inject(this)
        attachPresenter(controlDevicePresenter)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Log.d("ControlDeviceFragment", "onCreateView() called")
        _binding = LayoutControlDeviceBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        initImuDataViews()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("ControlDeviceFragment", "onViewCreated() called")

        binding.buttonConnect.setOnClickListener {
            Log.d("ControlDeviceFragment", "Connect button clicked")
            controlDevicePresenter.onConnectionToggleClicked()
        }
        binding.buttonStartStreaming.setOnClickListener {
            Log.d("ControlDeviceFragment", "Start streaming button clicked")
            controlDevicePresenter.onStreamingToggleClicked()
        }
        binding.buttonVibrate1.setOnClickListener {
            Log.d("ControlDeviceFragment", "Vibrate 1 button clicked")
            controlDevicePresenter.onVibrateClicked(1)
        }
        binding.buttonVibrate2.setOnClickListener {
            Log.d("ControlDeviceFragment", "Vibrate 2 button clicked")
            controlDevicePresenter.onVibrateClicked(2)
        }
        binding.buttonVibrate3.setOnClickListener {
            Log.d("ControlDeviceFragment", "Vibrate 3 button clicked")
            controlDevicePresenter.onVibrateClicked(3)
        }
        binding.seekbarFrequency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                controlDevicePresenter.onProgressSelected(progress)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        binding.seekbarFrequency.isEnabled = false
        binding.buttonStartStreaming.visibility = View.VISIBLE

        initEmgDataViews()
    }
    private fun initEmgDataViews() {
        // Initialize the new TextViews for EMG data
        binding.emgDataHalfSecond.visibility = View.VISIBLE
        binding.emgDataOneSecond.visibility = View.VISIBLE
        binding.emgDataFiveSeconds.visibility = View.VISIBLE
    }

    override fun updateAveragedEmgData(halfSecondAvg: FloatArray, oneSecondAvg: FloatArray, fiveSecondAvg: FloatArray) {
        binding.emgDataHalfSecond.text = "0.5s Avg: ${halfSecondAvg.joinToString(", ") { "%.2f".format(it) }}"
        binding.emgDataOneSecond.text = "1s Avg: ${oneSecondAvg.joinToString(", ") { "%.2f".format(it) }}"
        binding.emgDataFiveSeconds.text = "5s Avg: ${fiveSecondAvg.joinToString(", ") { "%.2f".format(it) }}"
    }
    private fun enableVibrationButtons(enable: Boolean) {
        binding.buttonVibrate1.isEnabled = enable
        binding.buttonVibrate2.isEnabled = enable
        binding.buttonVibrate3.isEnabled = enable
        Log.d("ControlDeviceFragment", "Vibration buttons enabled: $enable")
    }

    private fun initImuDataViews() {
        binding.imuDataQuaternion.visibility = View.VISIBLE
        binding.imuDataAccelerometer.visibility = View.VISIBLE
        binding.imuDataGyroscope.visibility = View.VISIBLE
    }

    override fun updateImuData(quaternion: FloatArray, accelerometer: FloatArray, gyroscope: FloatArray) {
        binding.imuDataQuaternion.text = "Quaternion: ${quaternion.joinToString(", ") { "%.2f".format(it) }}"
        binding.imuDataAccelerometer.text = "Accelerometer: ${accelerometer.joinToString(", ") { "%.2f".format(it) }}"
        binding.imuDataGyroscope.text = "Gyroscope: ${gyroscope.joinToString(", ") { "%.2f".format(it) }}"
    }
    override fun updateEmgData(emgData: FloatArray) {
        Log.d("ControlDeviceFragment", "EMG Data: ${emgData.contentToString()}")
        // TODO: Update UI to display EMG data
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun showDeviceInformation(name: String?, address: String) {
        binding.deviceName.text = name ?: getString(R.string.unknown_device)
        binding.deviceAddress.text = address
    }

    override fun showConnectionProgress() {
        binding.progressConnect.animate().alpha(1.0f)
    }

    override fun hideConnectionProgress() {
        binding.progressConnect.animate().alpha(0.0f)
    }

    override fun showConnecting() {
        binding.deviceStatus.text = getString(R.string.connecting)
    }

    override fun showConnected() {
        Log.d("ControlDeviceFragment", "showConnected() called")
        binding.deviceStatus.text = getString(R.string.connected)
        binding.buttonConnect.text = getString(R.string.disconnect)
        binding.buttonStartStreaming.isEnabled = true
        enableVibrationButtons(true)
    }

    override fun showDisconnected() {
        Log.d("ControlDeviceFragment", "showDisconnected() called")
        binding.deviceStatus.text = getString(R.string.disconnected)
        binding.buttonConnect.text = getString(R.string.connect)
        binding.buttonStartStreaming.isEnabled = false
        enableVibrationButtons(false)
    }

    override fun showStreaming() {
        Log.d("ControlDeviceFragment", "showStreaming() called")
        binding.deviceStreamingStatus.text = getString(R.string.currently_streaming)
        binding.buttonStartStreaming.text = getString(R.string.stop)
    }

    override fun showNotStreaming() {
        Log.d("ControlDeviceFragment", "showNotStreaming() called")
        binding.deviceStreamingStatus.text = getString(R.string.not_streaming)
        binding.buttonStartStreaming.text = getString(R.string.start)
    }



    override fun showConnectionError() {
        Toast.makeText(context, "Connection failed", Toast.LENGTH_SHORT).show()
        binding.buttonConnect.text = getString(R.string.connect)
    }

    override fun enableConnectButton() {
        binding.buttonConnect.isEnabled = true
    }

    override fun disableConnectButton() {
        binding.buttonConnect.isEnabled = false
    }

    override fun disableControlPanel() {
        binding.buttonStartStreaming.isEnabled = false
        binding.buttonVibrate1.isEnabled = false
        binding.buttonVibrate2.isEnabled = false
        binding.buttonVibrate3.isEnabled = false
        binding.seekbarFrequency.isEnabled = false
    }

    override fun enableControlPanel() {
        binding.buttonStartStreaming.isEnabled = true
        binding.buttonVibrate1.isEnabled = true
        binding.buttonVibrate2.isEnabled = true
        binding.buttonVibrate3.isEnabled = true
        binding.seekbarFrequency.isEnabled = true
    }



    override fun showFrequency(frequency: Int) {
        binding.deviceFrequencyValue.text = getString(R.string.templated_hz, frequency)
    }
}