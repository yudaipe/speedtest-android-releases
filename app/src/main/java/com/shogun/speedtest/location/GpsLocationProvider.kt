package com.shogun.speedtest.location

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GpsLocation(val lat: Double, val lon: Double)

class GpsLocationProvider(private val context: Context) {

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCurrentLocation(): GpsLocation? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val task = fusedClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                null
            )
            val location = Tasks.await(task, 10, java.util.concurrent.TimeUnit.SECONDS)
            location?.let { GpsLocation(it.latitude, it.longitude) }
        } catch (e: Exception) {
            android.util.Log.w("GpsLocationProvider", "GPS取得失敗: ${e.message}")
            null
        }
    }
}
