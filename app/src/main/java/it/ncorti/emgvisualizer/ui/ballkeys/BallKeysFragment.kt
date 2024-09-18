package it.ncorti.emgvisualizer.ui.ballkeys

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import it.ncorti.emgvisualizer.R
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class BallKeysFragment : Fragment() {

    private lateinit var ball1IpSpinner: Spinner
    private lateinit var ball2IpSpinner: Spinner
    private lateinit var ball3IpSpinner: Spinner
    private lateinit var ball1ColorView: ImageView
    private lateinit var ball2ColorView: ImageView
    private lateinit var ball3ColorView: ImageView
    private val ballIpAddresses = mutableListOf(
        "192.168.207.51",
        "192.168.207.195",
        "192.168.160.151",
        "192.168.207.30",
        "192.168.207.43"
    )
    private val ballPort = 41412

    private val keyColors = mutableMapOf<Char, Int>()
    private val defaultColors = listOf(
        Color.RED,
        Color.rgb(255, 165, 0), // Orange
        Color.YELLOW,
        Color.GREEN,
        Color.BLUE,
        Color.rgb(255, 0, 255), // Pink
        Color.WHITE,
        Color.BLACK
    )

    private val ballColors = mutableMapOf(
        0 to Color.WHITE,
        1 to Color.WHITE,
        2 to Color.WHITE
    )

    companion object {
        fun newInstance() = BallKeysFragment()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_ball_keys, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ball1IpSpinner = view.findViewById(R.id.ball1IpSpinner)
        ball2IpSpinner = view.findViewById(R.id.ball2IpSpinner)
        ball3IpSpinner = view.findViewById(R.id.ball3IpSpinner)
        ball1ColorView = view.findViewById(R.id.ball1ColorView)
        ball2ColorView = view.findViewById(R.id.ball2ColorView)
        ball3ColorView = view.findViewById(R.id.ball3ColorView)

        setupSpinners()
        setupColorButtons(view)
        initializeKeyColors()
        updateBallColorViews()
    }

    private fun setupSpinners() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, ballIpAddresses)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        ball1IpSpinner.adapter = adapter
        ball2IpSpinner.adapter = adapter
        ball3IpSpinner.adapter = adapter

        ball1IpSpinner.setSelection(0)
        ball2IpSpinner.setSelection(1)
        ball3IpSpinner.setSelection(2)
    }

    private fun setupColorButtons(view: View) {
        val keys = "qwertyuiasdfghjkzxcvbnm,"
        keys.forEach { key ->
            val buttonId = resources.getIdentifier(
                when (key) {
                    ',' -> "button_comma"
                    else -> "button_$key"
                },
                "id",
                requireContext().packageName
            )
            view.findViewById<Button>(buttonId)?.setOnClickListener {
                showColorPickerDialog(key)
            }
        }
    }

    private fun initializeKeyColors() {
        val keys = "qwertyuiasdfghjkzxcvbnm,"
        keys.forEachIndexed { index, key ->
            keyColors[key] = defaultColors[index % defaultColors.size]
        }
        updateButtonColors()
    }

    private fun updateButtonColors() {
        keyColors.forEach { (key, color) ->
            updateButtonColor(key, color)
        }
    }

    private fun showColorPickerDialog(key: Char) {
        ColorPickerDialogBuilder
            .with(context)
            .setTitle("Choose color for key $key")
            .initialColor(keyColors[key] ?: Color.WHITE)
            .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
            .density(12)
            .setPositiveButton("Ok") { _, selectedColor, _ ->
                keyColors[key] = selectedColor
                updateButtonColor(key, selectedColor)
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .build()
            .show()
    }

    private fun updateButtonColor(key: Char, color: Int) {
        val buttonId = resources.getIdentifier(
            when (key) {
                ',' -> "button_comma"
                else -> "button_$key"
            },
            "id",
            requireContext().packageName
        )
        view?.findViewById<Button>(buttonId)?.setBackgroundColor(color)
    }

    fun onKeyPressed(key: Char) {
        val color = keyColors[key] ?: return
        val ballIndex = when {
            "qwertyuio;".contains(key) -> 0
            "asdfghjkl'".contains(key) -> 1
            "zxcvbnm,.".contains(key) -> 2
            else -> return
        }
        val ballIp = when (ballIndex) {
            0 -> ball1IpSpinner.selectedItem as String
            1 -> ball2IpSpinner.selectedItem as String
            2 -> ball3IpSpinner.selectedItem as String
            else -> return
        }
        sendColorChange(ballIp, color)
        updateBallColor(ballIndex, color)
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
            }
        }
    }

    private fun updateBallColor(ballIndex: Int, color: Int) {
        ballColors[ballIndex] = color
        updateBallColorViews()
    }

    private fun updateBallColorViews() {
        updateBallColorView(ball1ColorView, ballColors[0] ?: Color.WHITE)
        updateBallColorView(ball2ColorView, ballColors[1] ?: Color.WHITE)
        updateBallColorView(ball3ColorView, ballColors[2] ?: Color.WHITE)
    }

    private fun updateBallColorView(view: ImageView, color: Int) {
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.circle_shape)?.mutate() as? GradientDrawable
        drawable?.setColor(color)
        view.setImageDrawable(drawable)
    }
}