package com.shogun.speedtest.settings

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import com.shogun.speedtest.device.DeviceIdentifier

class SettingsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("speedtest_prefs", Context.MODE_PRIVATE)

    fun getFormUrl(): String =
        prefs.getString("form_url",
            "https://docs.google.com/forms/d/e/1FAIpQLScr2n8o0uDBILC8XeHMNg3AvFv_vEgriQY7cBF5ctXjVGWtGA/formResponse") ?: ""

    fun setFormUrl(value: String) =
        prefs.edit { putString("form_url", value) }

    fun getEntryTimestamp(): String =
        prefs.getString("entry_timestamp", "entry.898858319") ?: ""

    fun setEntryTimestamp(value: String) =
        prefs.edit { putString("entry_timestamp", value) }

    fun getEntryDeviceId(): String =
        prefs.getString("entry_device_id", "entry.233195487") ?: ""

    fun setEntryDeviceId(value: String) =
        prefs.edit { putString("entry_device_id", value) }

    fun getEntryDeviceName(): String =
        prefs.getString("entry_device_name", "entry.529947754") ?: ""

    fun setEntryDeviceName(value: String) =
        prefs.edit { putString("entry_device_name", value) }

    fun getEntryDownload(): String =
        prefs.getString("entry_download", "entry.36661198") ?: ""

    fun setEntryDownload(value: String) =
        prefs.edit { putString("entry_download", value) }

    fun getEntryUpload(): String =
        prefs.getString("entry_upload", "entry.590664997") ?: ""

    fun setEntryUpload(value: String) =
        prefs.edit { putString("entry_upload", value) }

    fun getEntryPing(): String =
        prefs.getString("entry_ping", "entry.1967198033") ?: ""

    fun setEntryPing(value: String) =
        prefs.edit { putString("entry_ping", value) }

    fun getEntryJitter(): String =
        prefs.getString("entry_jitter", "entry.1397300286") ?: ""

    fun setEntryJitter(value: String) =
        prefs.edit { putString("entry_jitter", value) }

    fun getEntryIsp(): String =
        prefs.getString("entry_isp", "entry.1538327306") ?: ""

    fun setEntryIsp(value: String) =
        prefs.edit { putString("entry_isp", value) }

    fun getEntryServerName(): String =
        prefs.getString("entry_server_name", "entry.699079993") ?: ""

    fun setEntryServerName(value: String) =
        prefs.edit { putString("entry_server_name", value) }

    fun getEntryServerId(): String =
        prefs.getString("entry_server_id", "entry.1945227905") ?: ""

    fun setEntryServerId(value: String) =
        prefs.edit { putString("entry_server_id", value) }

    fun getEntryLat(): String = prefs.getString("entry_lat", "entry.2024643082") ?: ""
    fun setEntryLat(value: String) = prefs.edit { putString("entry_lat", value) }

    fun getEntryLon(): String = prefs.getString("entry_lon", "entry.1904652412") ?: ""
    fun setEntryLon(value: String) = prefs.edit { putString("entry_lon", value) }

    fun getEntryPacketLoss(): String = prefs.getString("entry_packet_loss", "entry.594590182") ?: ""
    fun setEntryPacketLoss(value: String) = prefs.edit { putString("entry_packet_loss", value) }

    fun getEntryServerCountry(): String = prefs.getString("entry_server_country", "entry.78726230") ?: ""
    fun setEntryServerCountry(value: String) = prefs.edit { putString("entry_server_country", value) }

    fun getEntryDistanceKm(): String = prefs.getString("entry_distance_km", "entry.1816072893") ?: ""
    fun setEntryDistanceKm(value: String) = prefs.edit { putString("entry_distance_km", value) }

    fun getEntryResultUrl(): String = prefs.getString("entry_result_url", "") ?: ""
    fun setEntryResultUrl(value: String) = prefs.edit { putString("entry_result_url", value) }

    fun getEntryExternalIp(): String = prefs.getString("entry_external_ip", "") ?: ""
    fun setEntryExternalIp(value: String) = prefs.edit { putString("entry_external_ip", value) }

    fun getEntrySoftwareVersion(): String = prefs.getString("entry_software_version", "entry.686132109") ?: ""
    fun setEntrySoftwareVersion(value: String) = prefs.edit { putString("entry_software_version", value) }

    fun getEntryWifiSsid(): String = prefs.getString("entry_wifi_ssid", "entry.129435542") ?: "entry.129435542"
    fun setEntryWifiSsid(value: String) = prefs.edit { putString("entry_wifi_ssid", value) }

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
