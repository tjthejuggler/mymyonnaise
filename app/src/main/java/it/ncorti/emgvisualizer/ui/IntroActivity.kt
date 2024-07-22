package it.ncorti.emgvisualizer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import it.ncorti.emgvisualizer.R

private const val PREFS_GLOBAL = "global"
private const val KEY_COMPLETED_ONBOARDING = "completed_onboarding"
private const val REQUEST_LOCATION_CODE = 1
private const val VIBRATE_INTENSITY = 30

class IntroActivity : AppIntro() {
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startMainActivity()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.location_permission_denied_message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val backgroundColor = ContextCompat.getColor(this, R.color.primaryColor)

        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.onboarding_title_0),
            description = getString(R.string.onboarding_description_0),
            imageDrawable = R.drawable.onboarding_0,
            backgroundColor = backgroundColor
        ))

        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.scan),
            description = getString(R.string.onboarding_description_1),
            imageDrawable = R.drawable.onboarding_1,
            backgroundColor = backgroundColor
        ))

        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.control),
            description = getString(R.string.onboarding_description_2),
            imageDrawable = R.drawable.onboarding_2,
            backgroundColor = backgroundColor
        ))

        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.graph),
            description = getString(R.string.onboarding_description_3),
            imageDrawable = R.drawable.onboarding_3,
            backgroundColor = backgroundColor
        ))

        addSlide(AppIntroFragment.newInstance(
            title = getString(R.string.export),
            description = getString(R.string.onboarding_description_4),
            imageDrawable = R.drawable.onboarding_4,
            backgroundColor = backgroundColor
        ))

        setIndicatorColor(
            selectedIndicatorColor = ContextCompat.getColor(this, R.color.primaryDarkColor),
            unselectedIndicatorColor = ContextCompat.getColor(this, R.color.primaryLightColor)
        )

        isSkipButtonEnabled = false
        setProgressIndicator()
        isWizardMode = true
        isVibrate = true
        vibrateDuration = VIBRATE_INTENSITY.toLong()
    }

    override fun onSkipPressed(currentFragment: androidx.fragment.app.Fragment?) {
        super.onSkipPressed(currentFragment)
        saveOnBoardingCompleted()
        requestPermission()
    }

    override fun onDonePressed(currentFragment: androidx.fragment.app.Fragment?) {
        super.onDonePressed(currentFragment)
        saveOnBoardingCompleted()
        requestPermission()
    }

    private fun saveOnBoardingCompleted() {
        val editor = getSharedPreferences(PREFS_GLOBAL, Context.MODE_PRIVATE).edit()
        editor.putBoolean(KEY_COMPLETED_ONBOARDING, true)
        editor.apply()
    }

    private fun requestPermission() {
        val hasPermission = (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                )
        if (hasPermission) {
            startMainActivity()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun startMainActivity() {
        finish()
        startActivity(Intent(this, MainActivity::class.java))
    }
}