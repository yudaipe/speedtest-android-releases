package com.shogun.speedtest.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class NetworkType(val wireValue: String) {
    WIFI("wifi"),
    MOBILE("mobile"),
    UNKNOWN("unknown")
}

class NetworkTypeDetector(private val context: Context) {

    fun detect(): NetworkType {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return NetworkType.UNKNOWN
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                ?: return NetworkType.UNKNOWN

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
                else -> NetworkType.UNKNOWN
            }
        } catch (_: Exception) {
            NetworkType.UNKNOWN
        }
    }
}
