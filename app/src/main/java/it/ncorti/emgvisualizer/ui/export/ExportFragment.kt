package it.ncorti.emgvisualizer.ui.export

import android.Manifest
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.Toast
import androidx.core.content.ContextCompat
import dagger.android.support.AndroidSupportInjection
import it.ncorti.emgvisualizer.BaseFragment
import it.ncorti.emgvisualizer.R
import it.ncorti.emgvisualizer.databinding.LayoutExportBinding
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import it.ncorti.emgvisualizer.SensorManager
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager


private const val REQUEST_WRITE_EXTERNAL_CODE = 2
private const val REQUEST_LOCATION_PERMISSION_CODE = 3

class ExportFragment : BaseFragment<ExportContract.Presenter>(), ExportContract.View {

    companion object {
        fun newInstance() = ExportFragment()
    }

    @Inject
    lateinit var exportPresenter: ExportPresenter

    private var fileContentToSave: String? = null

    private var _binding: LayoutExportBinding? = null
    private val binding get() = _binding!!
    private lateinit var sensorManager: SensorManager

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        attachPresenter(exportPresenter)
        sensorManager = SensorManager(context)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutExportBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val switchSensorSource: Switch = binding.switchSensorSource
        switchSensorSource.isChecked = sensorManager.isUsingPhoneSensors()

        switchSensorSource.setOnCheckedChangeListener { _, isChecked ->
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            sharedPreferences.edit().putBoolean("use_phone_sensors", isChecked).apply()
        }

        binding.buttonStartCollecting.setOnClickListener {
            val sensorData = sensorManager.getSensorData()
            exportPresenter.onCollectionTogglePressed()
            // Handle sensorData if needed
        }
        binding.buttonResetCollecting.setOnClickListener { exportPresenter.onResetPressed() }
        binding.buttonShare.setOnClickListener { exportPresenter.onSharePressed() }
        binding.buttonSave.setOnClickListener { exportPresenter.onSavePressed() }
    }

    private fun startCollectingData() {
        val sensorData = sensorManager.getSensorData()
        exportPresenter.onCollectionTogglePressed()
        // Handle sensorData if needed
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_WRITE_EXTERNAL_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fileContentToSave?.apply { writeToFile(this) }
                } else {
                    Toast.makeText(
                        activity, getString(R.string.write_permission_denied_message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            REQUEST_LOCATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCollectingData()
                } else {
                    Toast.makeText(activity, getString(R.string.location_permission_denied_message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    override fun enableStartCollectingButton() {
        binding.buttonStartCollecting.isEnabled = true
    }

    override fun disableStartCollectingButton() {
        binding.buttonStartCollecting.isEnabled = false
    }

    override fun showNotStreamingErrorMessage() {
        Toast.makeText(activity, "You can't collect points if Myo is not streaming!", Toast.LENGTH_SHORT).show()
    }

    override fun showCollectionStarted() {
        binding.buttonStartCollecting.text = getString(R.string.stop_collecting)
    }

    override fun showCollectionStopped() {
        binding.buttonStartCollecting.text = getString(R.string.start_collecting)
    }

    override fun showCollectedPoints(totalPoints: Int) {
        binding.pointsCount.text = totalPoints.toString()
    }

    override fun enableResetButton() {
        binding.buttonResetCollecting.isEnabled = true
    }

    override fun disableResetButton() {
        binding.buttonResetCollecting.isEnabled = false
    }

    override fun hideSaveArea() {
        binding.buttonSave.visibility = View.INVISIBLE
        binding.buttonShare.visibility = View.INVISIBLE
        binding.saveExportTitle.visibility = View.INVISIBLE
        binding.saveExportSubtitle.visibility = View.INVISIBLE
    }

    override fun showSaveArea() {
        binding.buttonSave.visibility = View.VISIBLE
        binding.buttonShare.visibility = View.VISIBLE
        binding.saveExportTitle.visibility = View.VISIBLE
        binding.saveExportSubtitle.visibility = View.VISIBLE
    }

    override fun sharePlainText(content: String) {
        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_TEXT, content)
        sendIntent.type = "text/plain"
        startActivity(sendIntent)
    }

    override fun saveCsvFile(content: String) {
        context?.apply {
            val hasPermission = (
                ContextCompat.checkSelfPermission(
                    this,
                    WRITE_EXTERNAL_STORAGE
                ) == PERMISSION_GRANTED
                )
            if (hasPermission) {
                writeToFile(content)
            } else {
                fileContentToSave = content
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_WRITE_EXTERNAL_CODE
                )
            }
        }
    }

    private fun writeToFile(content: String) {
        val storageDir =
            File("${Environment.getExternalStorageDirectory().absolutePath}/myo_emg")
        storageDir.mkdir()
        val outfile = File(storageDir, "myo_emg_export_${System.currentTimeMillis()}.csv")
        val fileOutputStream = FileOutputStream(outfile)
        fileOutputStream.write(content.toByteArray())
        fileOutputStream.close()
        Toast.makeText(activity, "Saved to: ${outfile.path}", Toast.LENGTH_LONG).show()
    }


}
