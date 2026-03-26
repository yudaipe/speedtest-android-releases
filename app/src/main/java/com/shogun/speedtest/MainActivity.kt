package com.shogun.speedtest

import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        android.util.Log.d("MainActivity", "Location permission granted: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBatteryOptimizationExemption()
        requestLocationPermissionIfNeeded()
        viewModel.checkForUpdate(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0D0D0D),
                    surface = Color(0xFF1A1A1A)
                )
            ) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }
}
