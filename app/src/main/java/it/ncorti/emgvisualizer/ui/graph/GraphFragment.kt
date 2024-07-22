package it.ncorti.emgvisualizer.ui.graph

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import dagger.android.support.AndroidSupportInjection
import it.ncorti.emgvisualizer.BaseFragment
import it.ncorti.emgvisualizer.R
import it.ncorti.emgvisualizer.databinding.LayoutGraphBinding
import javax.inject.Inject

class GraphFragment : BaseFragment<GraphContract.Presenter>(), GraphContract.View {

    companion object {
        fun newInstance() = GraphFragment()
        const val MYO_CHANNELS = 8
        const val MYO_MAX_VALUE = 127f
        const val MYO_MIN_VALUE = -128f
    }

    @Inject
    lateinit var graphPresenter: GraphPresenter

    private var _binding: LayoutGraphBinding? = null
    private val binding get() = _binding!!

    private lateinit var chart: LineChart
    private var isRunning = false

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        attachPresenter(graphPresenter)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutGraphBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGraphView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupGraphView() {
        chart = binding.sensorGraphView

        val dataSets = List(MYO_CHANNELS) { i ->
            LineDataSet(ArrayList(), "Channel ${i + 1}").apply {
                color = getColorForChannel(i)
                setDrawCircles(false)
                setDrawValues(false)
            }
        }

        chart.data = LineData(dataSets)
        chart.description.isEnabled = false
        chart.setTouchEnabled(false)
        chart.isDragEnabled = false
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.axisLeft.axisMaximum = MYO_MAX_VALUE
        chart.axisLeft.axisMinimum = MYO_MIN_VALUE
        chart.axisRight.isEnabled = false
        chart.xAxis.isEnabled = false
        chart.legend.isEnabled = false
    }

    private fun getColorForChannel(channel: Int): Int {
        val colors = intArrayOf(
            android.graphics.Color.RED,
            android.graphics.Color.GREEN,
            android.graphics.Color.BLUE,
            android.graphics.Color.YELLOW,
            android.graphics.Color.CYAN,
            android.graphics.Color.MAGENTA,
            android.graphics.Color.DKGRAY,
            android.graphics.Color.LTGRAY
        )
        return colors[channel % colors.size]
    }

    override fun showData(data: FloatArray) {
        if (isRunning) {
            for (i in 0 until MYO_CHANNELS) {
                val set = chart.data.getDataSetByIndex(i) as LineDataSet
                set.addEntry(Entry(set.entryCount.toFloat(), data[i]))
                if (set.entryCount > 100) {  // Keep only last 100 points
                    set.removeEntry(0)
                }
            }
            chart.data.notifyDataChanged()
            chart.notifyDataSetChanged()
            chart.invalidate()
        }
    }

    override fun startGraph(running: Boolean) {
        isRunning = running
        if (!running) {
            for (i in 0 until MYO_CHANNELS) {
                val set = chart.data.getDataSetByIndex(i) as LineDataSet
                set.clear()
            }
            chart.invalidate()
        }
    }

    override fun showNoStreamingMessage() {
        binding.textEmptyGraph.visibility = View.VISIBLE
        chart.visibility = View.GONE
    }

    override fun hideNoStreamingMessage() {
        binding.textEmptyGraph.visibility = View.INVISIBLE
        chart.visibility = View.VISIBLE
    }
}