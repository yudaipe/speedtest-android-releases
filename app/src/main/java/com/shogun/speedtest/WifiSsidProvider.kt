package com.shogun.speedtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

object WifiSsidProvider {
    private const val TAG = "SpeedtestSSID"

    fun getSsid(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSsidNewApi(context)
        } else {
            getSsidLegacy(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getSsidNewApi(context: Context): String {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            if (network == null) {
                Log.w(TAG, "SSID failed: no active network")
                return ""
            }
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities == null) {
                Log.w(TAG, "SSID failed: no active network")
                return ""
            }
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                Log.w(TAG, "SSID failed: not on WiFi")
                return ""
            }
            val wifiInfo = capabilities.transportInfo as? WifiInfo
            if (wifiInfo == null) {
                Log.w(TAG, "SSID failed: no WifiInfo (permissions?)")
                return ""
            }
            val ssid = wifiInfo.ssid
            if (ssid == null) {
                Log.w(TAG, "SSID failed: no WifiInfo (permissions?)")
                return ""
            }
            if (ssid == "<unknown ssid>") {
                Log.w(TAG, "SSID failed: unknown ssid (location off?)")
                return ""
            }
            ssid.trim('"')
        } catch (e: Exception) {
            Log.w(TAG, "SSID failed: no WifiInfo (permissions?)")
            ""
        }
    }

    @Suppress("DEPRECATION")
    private fun getSsidLegacy(context: Context): String {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ssid = wifiManager.connectionInfo?.ssid
            if (ssid == null) {
                Log.w(TAG, "SSID failed: no WifiInfo (permissions?)")
                return ""
            }
            if (ssid == "<unknown ssid>") {
                Log.w(TAG, "SSID failed: unknown ssid (location off?)")
                return ""
            }
            ssid.trim('"')
        } catch (e: Exception) {
            Log.w(TAG, "SSID failed: no WifiInfo (permissions?)")
            ""
        }
    }
}
