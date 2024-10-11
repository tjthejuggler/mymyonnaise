package it.ncorti.emgvisualizer

import android.content.Context
import androidx.preference.PreferenceManager

class SensorManager(private val context: Context) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun isUsingPhoneSensors(): Boolean {
        return sharedPreferences.getBoolean("use_phone_sensors", false)
    }

    fun getSensorData(): SensorData {
        return if (isUsingPhoneSensors()) {
            getPhoneSensorData()
        } else {
            getMyoSensorData()
        }
    }

    private fun getPhoneSensorData(): SensorData {
        // Implement logic to get data from phone sensors
        return SensorData() // Placeholder return statement
    }

    private fun getMyoSensorData(): SensorData {
        // Implement logic to get data from Myo armband
        return SensorData() // Placeholder return statement
    }
}

// Define the SensorData class
data class SensorData(
    val data: String = "" // Placeholder property
)