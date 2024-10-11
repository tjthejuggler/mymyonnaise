package it.ncorti.emgvisualizer.ui.control

import android.content.ContentValues.TAG
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import dagger.android.support.AndroidSupportInjection
import it.ncorti.emgvisualizer.BaseFragment
import it.ncorti.emgvisualizer.R
import it.ncorti.emgvisualizer.databinding.LayoutControlDeviceBinding
import javax.inject.Inject

class ControlDeviceFragment : BaseFragment<ControlDeviceContract.Presenter>(), ControlDeviceContract.View, SensorEventListener {

    companion object {
        fun newInstance() = ControlDeviceFragment()
    }

    @Inject
    lateinit var controlDevicePresenter: ControlDevicePresenter

    private var _binding: LayoutControlDeviceBinding? = null
    private val binding get() = _binding!!

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private var pendingImuDataListener: ControlDevicePresenter.ImuDataListener? = null

    fun setImuDataListenerWhenReady(listener: ControlDevicePresenter.ImuDataListener) {
        if (::controlDevicePresenter.isInitialized) {
            controlDevicePresenter.imuDataListener = listener
            Log.d(TAG, "ImuDataListener set immediately")
        } else {
            pendingImuDataListener = listener
            Log.d(TAG, "ImuDataListener pending")
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        AndroidSupportInjection.inject(this)
        attachPresenter(controlDevicePresenter)

        // Set the pending listener if there is one
        pendingImuDataListener?.let { listener ->
            controlDevicePresenter.imuDataListener = listener
            pendingImuDataListener = null
            Log.d(TAG, "Pending ImuDataListener set")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event.let {
            when (it?.sensor?.type) {
                Sensor.TYPE_ACCELEROMETER -> controlDevicePresenter.onAccelerometerData(it.values)
                Sensor.TYPE_GYROSCOPE -> controlDevicePresenter.onGyroscopeData(it.values)
            }
        }
        Log.d(TAG, "onSensorChanged called")

        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            Log.d(TAG, "Accelerometer: x=${event.values[0]}, y=${event.values[1]}, z=${event.values[2]}")
        } else if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            Log.d(TAG, "Gyroscope: x=${event.values[0]}, y=${event.values[1]}, z=${event.values[2]}")
        }


    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutControlDeviceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.switchSensorSource.setOnCheckedChangeListener { _, isChecked ->
            controlDevicePresenter.onSensorSourceChanged(isChecked)
        }

        binding.buttonConnect.setOnClickListener {
            controlDevicePresenter.onConnectionToggleClicked()
        }

        binding.buttonStartStreaming.setOnClickListener {
            controlDevicePresenter.onStreamingToggleClicked()
        }

        binding.buttonVibrate1.setOnClickListener { controlDevicePresenter.onVibrateClicked(1) }
        binding.buttonVibrate2.setOnClickListener { controlDevicePresenter.onVibrateClicked(2) }
        binding.buttonVibrate3.setOnClickListener { controlDevicePresenter.onVibrateClicked(3) }

        binding.seekbarFrequency.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                controlDevicePresenter.onProgressSelected(progress)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        Log.d(TAG, "Accelerometer available: ${accelerometer != null}")
        Log.d(TAG, "Gyroscope available: ${gyroscope != null}")

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }

        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)}

        Log.d(TAG, "Sensor listeners registered")
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
        binding.progressConnect.visibility = View.VISIBLE
    }

    override fun hideConnectionProgress() {
        binding.progressConnect.visibility = View.GONE
    }

    override fun showConnected() {
        binding.deviceStatus.text = getString(R.string.connected)
        binding.buttonConnect.text = getString(R.string.disconnect)
    }

    override fun showDisconnected() {
        binding.deviceStatus.text = getString(R.string.disconnected)
        binding.buttonConnect.text = getString(R.string.connect)
    }

    override fun showConnecting() {
        binding.deviceStatus.text = getString(R.string.connecting)
    }

    override fun showConnectionError() {
        Toast.makeText(context, "Connection failed", Toast.LENGTH_SHORT).show()
    }

    override fun enableConnectButton() {
        binding.buttonConnect.isEnabled = true
    }

    override fun disableConnectButton() {
        binding.buttonConnect.isEnabled = false
    }

    override fun enableControlPanel() {
        binding.buttonStartStreaming.isEnabled = true
        binding.buttonVibrate1.isEnabled = true
        binding.buttonVibrate2.isEnabled = true
        binding.buttonVibrate3.isEnabled = true
        binding.seekbarFrequency.isEnabled = true
    }

    override fun disableControlPanel() {
        binding.buttonStartStreaming.isEnabled = false
        binding.buttonVibrate1.isEnabled = false
        binding.buttonVibrate2.isEnabled = false
        binding.buttonVibrate3.isEnabled = false
        binding.seekbarFrequency.isEnabled = false
    }

    override fun showStreaming() {
        binding.deviceStreamingStatus.text = getString(R.string.currently_streaming)
        binding.buttonStartStreaming.text = getString(R.string.stop)
    }

    override fun showNotStreaming() {
        binding.deviceStreamingStatus.text = getString(R.string.not_streaming)
        binding.buttonStartStreaming.text = getString(R.string.start)
    }

    override fun updateEmgData(emgData: FloatArray) {
        // Update EMG data display
    }

    override fun showFrequency(frequency: Int) {
        binding.deviceFrequencyValue.text = getString(R.string.templated_hz, frequency)
    }

    override fun updateImuData(orientation: FloatArray, accelerometer: FloatArray, gyroscope: FloatArray) {
        binding.imuDataQuaternion.text = "Quaternion: ${orientation.joinToString(", ") { "%.2f".format(it) }}"
        binding.imuDataAccelerometer.text = "Accelerometer: ${accelerometer.joinToString(", ") { "%.2f".format(it) }}"
        binding.imuDataGyroscope.text = "Gyroscope: ${gyroscope.joinToString(", ") { "%.2f".format(it) }}"
    }

    override fun updateAveragedEmgData(halfSecondAvg: FloatArray, oneSecondAvg: FloatArray, fiveSecondAvg: FloatArray) {
        binding.emgDataHalfSecond.text = "0.5s Avg: ${halfSecondAvg.joinToString(", ") { "%.2f".format(it) }}"
        binding.emgDataOneSecond.text = "1s Avg: ${oneSecondAvg.joinToString(", ") { "%.2f".format(it) }}"
        binding.emgDataFiveSeconds.text = "5s Avg: ${fiveSecondAvg.joinToString(", ") { "%.2f".format(it) }}"
    }

    override fun updateStreamingSource(source: String) {
        binding.currentStreamingSource.text = "Current Source: $source"
    }



    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    fun startPhoneSensorStreaming() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stopPhoneSensorStreaming() {
        sensorManager.unregisterListener(this)
    }
}