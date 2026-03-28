package com.shogun.speedtest

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

class WifiSsidProvider {
    companion object {
        private const val TAG = "SpeedtestSSID"

        @Suppress("DEPRECATION")
        fun getSsid(context: Context): String {
            return try {
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ssid = wifiManager.connectionInfo?.ssid
                when {
                    ssid == null -> {
                        Log.w(TAG, "SSID failed: no WifiInfo (permissions?)")
                        ""
                    }
                    ssid == "<unknown ssid>" -> {
                        Log.w(TAG, "SSID failed: unknown ssid (location off?)")
                        ""
                    }
                    else -> ssid.trim('"')
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SSID failed: SecurityException ${e.message}")
                ""
            } catch (e: Exception) {
                Log.w(TAG, "SSID failed: ${e.message}")
                ""
            }
        }
    }
}
