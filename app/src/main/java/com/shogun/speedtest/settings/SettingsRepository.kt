package com.shogun.speedtest.settings

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import com.shogun.speedtest.device.DeviceIdentifier

class SettingsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("speedtest_prefs", Context.MODE_PRIVATE)

    fun getDeviceName(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )?.takeLast(4) ?: "xxxx"
        return prefs.getString("device_name", "端末-$androidId") ?: "端末-$androidId"
    }

    fun setDeviceName(value: String) =
        prefs.edit { putString("device_name", value) }

    fun getDeviceId(): String = DeviceIdentifier.getId(context)

    fun getServerId(): Int =
        prefs.getInt("server_id", 48463)

    fun setServerId(value: Int) =
        prefs.edit { putInt("server_id", value) }
}
