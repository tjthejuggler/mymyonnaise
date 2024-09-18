package it.ncorti.emgvisualizer.ui.balls

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import com.google.android.material.slider.RangeSlider
import it.ncorti.emgvisualizer.R
import it.ncorti.emgvisualizer.ui.control.ControlDevicePresenter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class BallsFragment : Fragment(), ControlDevicePresenter.ImuDataListener {

    private val colorButtons = mutableMapOf<String, MutableMap<String, Pair<Int, Int>>>()
    private val checkBoxStates = mutableMapOf<String, MutableMap<String, Boolean>>()
    private val checkBoxes = mutableMapOf<String, CheckBox>()
    private val ballIpAddresses = mutableListOf(
        "192.168.207.51",
        "192.168.207.195",
        "192.168.160.151",
        "192.168.207.30",
        "192.168.207.43"
    )
    private lateinit var ballIpSpinner: Spinner
    private lateinit var addIpButton: Button
    private lateinit var removeIpButton: Button
    private lateinit var newIpEditText: EditText
    private val ballPort = 41412

    private lateinit var previewCirclesContainer: LinearLayout
    private val previewCircles = mutableMapOf<String, View>()

    private var lastImuData: Map<String, Float> = emptyMap()

    private val rangeSliders = mutableMapOf<String, RangeSlider>()
    private val rangeValues = mutableMapOf<String, Pair<Float, Float>>()

    // Define initial ranges for each IMU data type
    private val initialRanges = mapOf(
        "orientationW" to Pair(-1f, 1f),
        "orientationX" to Pair(-1f, 1f),
        "orientationY" to Pair(-1f, 1f),
        "orientationZ" to Pair(-1f, 1f),
        "accelerometerX" to Pair(-20f, 20f),
        "accelerometerY" to Pair(-20f, 20f),
        "accelerometerZ" to Pair(-20f, 20f),
        "gyroscopeX" to Pair(-500f, 500f),
        "gyroscopeY" to Pair(-500f, 500f),
        "gyroscopeZ" to Pair(-500f, 500f)
    )

    private val currentValueMarkers = mutableMapOf<String, View>()

    companion object {
        fun newInstance() = BallsFragment()
    }
    override fun onImuDataReceived(imuData: Map<String, Float>) {
        lastImuData = imuData
        updateBallColors(imuData)
        updateImuLabels(imuData)
        updateCurrentValueMarkers(imuData)
    }

    private fun updateCurrentValueMarkers(imuData: Map<String, Float>) {
        imuData.forEach { (dataPoint, value) ->
            val slider = rangeSliders[dataPoint] ?: return@forEach
            val marker = currentValueMarkers[dataPoint] ?: return@forEach
            val (min, max) = rangeValues[dataPoint] ?: return@forEach
            val colors = colorButtons[ballIpSpinner.selectedItem as? String]?.get(dataPoint) ?: return@forEach

            val normalizedValue = (value - slider.valueFrom) / (slider.valueTo - slider.valueFrom)
            val markerX = slider.left + (slider.width * normalizedValue).toInt()

            // Get the Y position of the slider
            val sliderY = slider.y

            // Set both X and Y positions of the marker
            marker.x = markerX.toFloat() - marker.width / 2
            marker.y = sliderY + (slider.height - marker.height) / 2 // Center vertically on the slider

            marker.visibility = View.VISIBLE

            // Calculate the color based on the current value
            val markerColor = when {
                value < min -> colors.first // Low color
                value > max -> colors.second // High color
                else -> {
                    // Interpolate between low and high colors
                    val t = (value - min) / (max - min)
                    interpolateColor(colors.first, colors.second, t)
                }
            }

            marker.setBackgroundColor(markerColor)
        }
    }



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_balls, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ballIpSpinner = view.findViewById(R.id.ballIpSpinner)
        addIpButton = view.findViewById(R.id.addIpButton)
        removeIpButton = view.findViewById(R.id.removeIpButton)
        newIpEditText = view.findViewById(R.id.newIpEditText)

        previewCirclesContainer = view.findViewById(R.id.previewCirclesContainer)


        setupIpControls()
        setupDataPointsUI(view)

        setupRangeSliders(view)
    }

    private fun setupRangeSliders(view: View) {
        val imuDataPoints = listOf(
            "orientationW", "orientationX", "orientationY", "orientationZ",
            "accelerometerX", "accelerometerY", "accelerometerZ",
            "gyroscopeX", "gyroscopeY", "gyroscopeZ"
        )

        imuDataPoints.forEach { dataPoint ->
            val sliderId = resources.getIdentifier("${dataPoint}RangeSlider", "id", requireContext().packageName)
            val slider = view.findViewById<RangeSlider>(sliderId)
            rangeSliders[dataPoint] = slider

            // Set initial range
            val (min, max) = initialRanges[dataPoint] ?: Pair(0f, 1f)
            slider.valueFrom = min
            slider.valueTo = max
            slider.setValues(min, max)  // Set initial selected range to full range

            slider.addOnChangeListener { slider, _, _ ->
                val values = slider.values
                rangeValues[dataPoint] = Pair(values[0], values[1])
                updateBallColors(lastImuData)
            }

            // Initialize range values
            rangeValues[dataPoint] = Pair(min, max)

            // Add current value marker
            val marker = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(8, 40).apply {
                    gravity = Gravity.NO_GRAVITY  // Changed from CENTER_VERTICAL to NO_GRAVITY
                }
                setBackgroundColor(Color.RED)
                visibility = View.INVISIBLE
            }
            (slider.parent as? ViewGroup)?.addView(marker)
            currentValueMarkers[dataPoint] = marker
        }
    }

    private fun setupIpControls() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, ballIpAddresses)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        ballIpSpinner.adapter = adapter

        ballIpSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val selectedIp = ballIpAddresses[position]
                updateUIForSelectedIp(selectedIp)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        addIpButton.setOnClickListener {
            val newIp = newIpEditText.text.toString()
            if (newIp.isNotEmpty() && !ballIpAddresses.contains(newIp)) {
                ballIpAddresses.add(newIp)
                adapter.notifyDataSetChanged()
                newIpEditText.text.clear()
                initializeSettingsForNewIp(newIp)
                addPreviewCircle(newIp)
            }
        }

        removeIpButton.setOnClickListener {
            val selectedIp = ballIpSpinner.selectedItem as? String
            if (selectedIp != null) {
                ballIpAddresses.remove(selectedIp)
                colorButtons.remove(selectedIp)
                checkBoxStates.remove(selectedIp)
                removePreviewCircle(selectedIp)
                adapter.notifyDataSetChanged()
            }
        }

        // Initialize settings for pre-populated IPs
        ballIpAddresses.forEach { ip ->
            initializeSettingsForNewIp(ip)
            addPreviewCircle(ip)
        }
    }

    private fun initializeSettingsForNewIp(ip: String) {
        if (!colorButtons.containsKey(ip)) {
            colorButtons[ip] = mutableMapOf()
            checkBoxStates[ip] = mutableMapOf()

            val imuDataPoints = listOf(
                "orientationW", "orientationX", "orientationY", "orientationZ",
                "accelerometerX", "accelerometerY", "accelerometerZ",
                "gyroscopeX", "gyroscopeY", "gyroscopeZ"
            )

            imuDataPoints.forEach { dataPoint ->
                colorButtons[ip]!![dataPoint] = Pair(Color.WHITE, Color.BLACK)
                checkBoxStates[ip]!![dataPoint] = false
            }
        }
    }

    private fun updateUIForSelectedIp(ip: String) {
        colorButtons[ip]?.forEach { (dataPoint, colors) ->
            updateButtonColor(ip, dataPoint, true, colors.first)
            updateButtonColor(ip, dataPoint, false, colors.second)
        }

        checkBoxStates[ip]?.forEach { (dataPoint, isChecked) ->
            checkBoxes[dataPoint]?.isChecked = isChecked
        }

        updatePreviewCircle(ip, calculateCurrentColor(ip))
    }
    private fun addPreviewCircle(ip: String) {
        val circle = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.preview_circle_size),
                resources.getDimensionPixelSize(R.dimen.preview_circle_size)
            ).apply {
                setMargins(8, 8, 8, 8)
            }
            background = ContextCompat.getDrawable(requireContext(), R.drawable.circle_shape)
        }
        previewCirclesContainer.addView(circle)
        previewCircles[ip] = circle
    }

    private fun removePreviewCircle(ip: String) {
        previewCircles[ip]?.let {
            previewCirclesContainer.removeView(it)
            previewCircles.remove(ip)
        }
    }

    private fun updatePreviewCircle(ip: String, color: Int) {
        previewCircles[ip]?.let { circle ->
            circle.background = ContextCompat.getDrawable(requireContext(), R.drawable.circle_shape)?.apply {
                setColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
        }
        // Send color change command to the ball
        sendColorChange(ip, color)
    }

    private fun setupDataPointsUI(view: View) {
        val imuDataPoints = listOf(
            "orientationW", "orientationX", "orientationY", "orientationZ",
            "accelerometerX", "accelerometerY", "accelerometerZ",
            "gyroscopeX", "gyroscopeY", "gyroscopeZ"
        )

        imuDataPoints.forEach { dataPoint ->
            val checkBox = view.findViewById<CheckBox>(resources.getIdentifier("${dataPoint}CheckBox", "id", requireContext().packageName))
            checkBoxes[dataPoint] = checkBox

            val lowColorButton = view.findViewById<Button>(resources.getIdentifier("${dataPoint}LowColor", "id", requireContext().packageName))
            val highColorButton = view.findViewById<Button>(resources.getIdentifier("${dataPoint}HighColor", "id", requireContext().packageName))

            lowColorButton.setOnClickListener { showColorPickerDialog(dataPoint, true) }
            highColorButton.setOnClickListener { showColorPickerDialog(dataPoint, false) }

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                val selectedIp = ballIpSpinner.selectedItem as? String
                if (selectedIp != null) {
                    checkBoxStates[selectedIp]?.put(dataPoint, isChecked)
                    updateBallColor(selectedIp)
                }
            }

            val label = view.findViewById<TextView>(resources.getIdentifier("${dataPoint}Label", "id", requireContext().packageName))
            label.text = "$dataPoint: No data streaming"
        }
    }


    private fun showColorPickerDialog(dataPoint: String, isLowColor: Boolean) {
        val selectedIp = ballIpSpinner.selectedItem as? String ?: return

        ColorPickerDialogBuilder
            .with(context)
            .setTitle(if (isLowColor) "Choose low color" else "Choose high color")
            .initialColor(Color.WHITE)
            .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
            .density(12)
            .setPositiveButton("Ok") { _, selectedColor, _ ->
                updateButtonColor(selectedIp, dataPoint, isLowColor, selectedColor)
                updateBallColor(selectedIp)
                updateCurrentValueMarkers(lastImuData) // Add this line
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .build()
            .show()
    }

    private fun updateButtonColor(ip: String, dataPoint: String, isLowColor: Boolean, color: Int) {
        val colors = colorButtons[ip]?.get(dataPoint) ?: Pair(Color.WHITE, Color.BLACK)
        val updatedColors = if (isLowColor) Pair(color, colors.second) else Pair(colors.first, color)
        colorButtons[ip]?.put(dataPoint, updatedColors)

        val viewButton = if (isLowColor)
            view?.findViewById<Button>(resources.getIdentifier("${dataPoint}LowColor", "id", requireContext().packageName))
        else
            view?.findViewById<Button>(resources.getIdentifier("${dataPoint}HighColor", "id", requireContext().packageName))
        viewButton?.setBackgroundColor(color)
    }

    private fun updateBallColor(ip: String) {
        val color = calculateCurrentColor(ip)
        updatePreviewCircle(ip, color)
    }

    private fun sendColorChange(ipAddress: String, color: Int) {
        val buffer = ByteArray(12)
        buffer[0] = 66 // 'B' for Ball
        buffer[8] = 0x0a.toByte() // Command for color change
        buffer[9] = Color.red(color).toByte()
        buffer[10] = Color.green(color).toByte()
        buffer[11] = Color.blue(color).toByte()

        thread {
            try {
                val address = InetAddress.getByName(ipAddress)
                val packet = DatagramPacket(buffer, buffer.size, address, ballPort)
                val socket = DatagramSocket()
                socket.send(packet)
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle error (e.g., show a toast message)
                activity?.runOnUiThread {
                    Toast.makeText(context, "Error sending color: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // This method should be called when IMU data is received
    private fun updateBallColors(imuData: Map<String, Float>) {
        val selectedIp = ballIpSpinner.selectedItem as? String ?: return
        val colors = colorButtons[selectedIp] ?: return
        var hasCheckedItem = false
        var interpolatedColor = Color.GRAY

        imuData.forEach { (dataPoint, value) ->
            if (checkBoxStates[selectedIp]?.get(dataPoint) == true) {
                hasCheckedItem = true
                val (lowColor, highColor) = colors[dataPoint] ?: return@forEach
                val normalizedValue = normalizeImuValue(dataPoint, value)
                val dataPointColor = when {
                    normalizedValue <= 0f -> lowColor
                    normalizedValue >= 1f -> highColor
                    else -> interpolateColor(lowColor, highColor, normalizedValue)
                }
                interpolatedColor = if (interpolatedColor == Color.GRAY) {
                    dataPointColor
                } else {
                    blendColors(interpolatedColor, dataPointColor)
                }
            }
        }

        if (hasCheckedItem) {
            updatePreviewCircle(selectedIp, interpolatedColor)
        }
    }
//    private fun normalizeImuValue(dataPoint: String, value: Float): Float {
//        return when {
//            dataPoint.startsWith("orientation") -> (value + 1) / 2 // Orientation values are typically between -1 and 1
//            dataPoint.startsWith("accelerometer") -> (value + 20) / 40 // Assuming accelerometer range of -20 to 20
//            dataPoint.startsWith("gyroscope") -> (value + 500) / 1000 // Assuming gyroscope range of -500 to 500
//            else -> 0.5f // Default to middle value if unknown
//        }.coerceIn(0f, 1f)
//    }
    private fun normalizeImuValue(dataPoint: String, value: Float): Float {
        val (min, max) = rangeValues[dataPoint] ?: return 0.5f
        return when {
            value <= min -> 0f
            value >= max -> 1f
            else -> (value - min) / (max - min)
        }
    }
    private fun blendColors(color1: Int, color2: Int): Int {
        val r = (Color.red(color1) + Color.red(color2)) / 2
        val g = (Color.green(color1) + Color.green(color2)) / 2
        val b = (Color.blue(color1) + Color.blue(color2)) / 2
        return Color.rgb(r, g, b)
    }

    private fun updateImuLabels(imuData: Map<String, Float>) {
        imuData.forEach { (dataPoint, value) ->
            val labelId = resources.getIdentifier("${dataPoint}Label", "id", requireContext().packageName)
            val label = view?.findViewById<TextView>(labelId)
            label?.text = "$dataPoint: $value"
        }
    }

    private fun calculateCurrentColor(ip: String): Int {
        var hasCheckedItem = false
        var r = 0
        var g = 0
        var b = 0
        var totalWeight = 0f

        checkBoxStates[ip]?.forEach { (dataPoint, isChecked) ->
            if (isChecked) {
                hasCheckedItem = true
                val (lowColor, highColor) = colorButtons[ip]?.get(dataPoint) ?: return@forEach
                val normalizedValue = normalizeImuValue(dataPoint, lastImuData[dataPoint] ?: 0f)

                val dataPointColor = when {
                    normalizedValue <= 0f -> lowColor
                    normalizedValue >= 1f -> highColor
                    else -> interpolateColor(lowColor, highColor, normalizedValue)
                }
                r += Color.red(dataPointColor)
                g += Color.green(dataPointColor)
                b += Color.blue(dataPointColor)
                totalWeight += 1f
            }
        }

        return if (hasCheckedItem) {
            Color.rgb(
                (r / totalWeight).toInt().coerceIn(0, 255),
                (g / totalWeight).toInt().coerceIn(0, 255),
                (b / totalWeight).toInt().coerceIn(0, 255)
            )
        } else {
            Color.GRAY
        }
    }

    // Add this helper function to interpolate between two colors
    private fun interpolateColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val startA = Color.alpha(startColor)
        val startR = Color.red(startColor)
        val startG = Color.green(startColor)
        val startB = Color.blue(startColor)

        val endA = Color.alpha(endColor)
        val endR = Color.red(endColor)
        val endG = Color.green(endColor)
        val endB = Color.blue(endColor)

        val a = (startA + (endA - startA) * fraction).toInt()
        val r = (startR + (endR - startR) * fraction).toInt()
        val g = (startG + (endG - startG) * fraction).toInt()
        val b = (startB + (endB - startB) * fraction).toInt()

        return Color.argb(a, r, g, b)
    }
}