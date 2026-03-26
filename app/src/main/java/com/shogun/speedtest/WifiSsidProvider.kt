package com.shogun.speedtest

import android.content.Context
import android.net.wifi.WifiManager

object WifiSsidProvider {
    fun getSsid(context: Context): String {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ssid = wifiManager.connectionInfo.ssid
            // Android returns "<unknown ssid>" or with quotes like "\"MyNetwork\""
            when {
                ssid == null || ssid == "<unknown ssid>" -> ""
                ssid.startsWith("\"") && ssid.endsWith("\"") -> ssid.drop(1).dropLast(1)
                else -> ssid
            }
        } catch (e: Exception) {
            ""
        }
    }
}
