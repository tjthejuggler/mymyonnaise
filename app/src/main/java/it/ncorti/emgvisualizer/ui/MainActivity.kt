package it.ncorti.emgvisualizer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.adapter.FragmentStateAdapter
import dagger.android.AndroidInjection
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerAppCompatActivity
import it.ncorti.emgvisualizer.R
import it.ncorti.emgvisualizer.databinding.ActivityMainBinding
import it.ncorti.emgvisualizer.ui.balls.BallsFragment
import it.ncorti.emgvisualizer.ui.control.ControlDeviceFragment
import it.ncorti.emgvisualizer.ui.export.ExportFragment
import it.ncorti.emgvisualizer.ui.graph.GraphFragment
import it.ncorti.emgvisualizer.ui.scan.ScanDeviceFragment
import javax.inject.Inject

private const val PREFS_GLOBAL = "global"
private const val KEY_COMPLETED_ONBOARDING = "completed_onboarding"

class MainActivity : DaggerAppCompatActivity() {
    companion object {
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 1
    }

    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    private lateinit var binding: ActivityMainBinding

    override fun androidInjector(): AndroidInjector<Any> = androidInjector

    private lateinit var controlDeviceFragment: ControlDeviceFragment
    private lateinit var ballsFragment: BallsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        // Checking if we should on-board the user the first time.
        val prefs = getSharedPreferences(PREFS_GLOBAL, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_COMPLETED_ONBOARDING, false)) {
            finish()
            startActivity(Intent(this, IntroActivity::class.java))
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.newToolbar)

        controlDeviceFragment = ControlDeviceFragment.newInstance()
        ballsFragment = BallsFragment.newInstance()

        val fragmentList = listOf(
            ScanDeviceFragment.newInstance(),
            controlDeviceFragment,
            GraphFragment.newInstance(),
            ballsFragment,
            ExportFragment.newInstance()
        )
        binding.viewPager.adapter = MyAdapter(supportFragmentManager, fragmentList)

        binding.viewPager.offscreenPageLimit = 4  // Update this line
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            var prevMenuItem: MenuItem? = null

            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                if (position == 1) { // ControlDeviceFragment position
                    setupImuDataCommunication()
                }
                if (prevMenuItem != null) {
                    prevMenuItem?.isChecked = false
                } else {
                    binding.bottomNavigation.menu.getItem(0).isChecked = false
                }
                binding.bottomNavigation.menu.getItem(position).isChecked = true
                prevMenuItem = binding.bottomNavigation.menu.getItem(position)
            }
        })

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.item_scan -> binding.viewPager.currentItem = 0
                R.id.item_control -> navigateToControlDevice()
                R.id.item_graph -> binding.viewPager.currentItem = 2
                R.id.item_balls -> binding.viewPager.currentItem = 3  // Add this line
                R.id.item_export -> binding.viewPager.currentItem = 4  // Update this line
            }
            true
        }


    }

    private fun setupImuDataCommunication() {
        if (::controlDeviceFragment.isInitialized && ::ballsFragment.isInitialized) {
            controlDeviceFragment.setImuDataListenerWhenReady(ballsFragment)
        }
    }

    fun navigateToControlDevice() {
        Log.d("Navigation", "Navigating to ControlDeviceFragment")
        binding.viewPager.currentItem = 1
    }

    fun navigateToPage(pageId: Int) {
        binding.viewPager.currentItem = pageId
    }

    fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            BLUETOOTH_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, start scanning
                    val currentFragment = supportFragmentManager.fragments[binding.viewPager.currentItem]
                    if (currentFragment is ScanDeviceFragment) {
                        currentFragment.startScanning()
                    }
                } else {
                    // Permission denied, show a message to the user
                    Toast.makeText(this, "Bluetooth permission is required to scan for devices", Toast.LENGTH_LONG).show()
                }
            }
        }
    }




    inner class MyAdapter(fm: FragmentManager, private val fragmentList: List<Fragment>) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getCount(): Int = fragmentList.size
        override fun getItem(position: Int): Fragment = fragmentList[position]
        override fun getPageTitle(position: Int): CharSequence? = "Page $position"
    }
}