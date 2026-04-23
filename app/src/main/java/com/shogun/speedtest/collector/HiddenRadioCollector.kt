package com.shogun.speedtest.collector

import android.content.Context
import android.os.Build
import android.util.Log
import android.telephony.PhysicalChannelConfig
import android.telephony.TelephonyManager
import com.shogun.speedtest.data.CarrierInfo
import com.shogun.speedtest.data.HiddenRadioSnapshot
import com.shogun.speedtest.debug.HiddenRadioDebugLog

class HiddenRadioCollector(private val context: Context) {

    @Suppress("UNCHECKED_CAST")
    fun collect(): HiddenRadioSnapshot {
        HiddenRadioDebugLog.add("collect_start", "sdk=${Build.VERSION.SDK_INT}")
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val configs: List<PhysicalChannelConfig> = try {
            val method = TelephonyManager::class.java.getDeclaredMethod("getPhysicalChannelConfigs")
            method.isAccessible = true
            ((method.invoke(tm) as? List<PhysicalChannelConfig>) ?: emptyList()).also {
                Log.d(TAG, "getPhysicalChannelConfigs succeeded size=${it.size}")
                HiddenRadioDebugLog.updateRawDump(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "getPhysicalChannelConfigs failed; Shizuku permission alone does not elevate this app process", e)
            HiddenRadioDebugLog.add(
                event = "collect_fail",
                detail = "${e.javaClass.simpleName}: ${e.message ?: "unknown"}",
                stacktrace = HiddenRadioDebugLog.stacktrace(e, 5)
            )
            emptyList()
        }

        val carriers = configs.map { config -> buildCarrierInfo(config) }
        Log.d(TAG, "collect mapped carrierCount=${carriers.size}")
        HiddenRadioDebugLog.add("collect_success", "configs=${configs.size}")
        return HiddenRadioSnapshot(componentCarriers = carriers)
    }

    private fun buildCarrierInfo(config: PhysicalChannelConfig): CarrierInfo {
        val band: Int? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                config.band.takeIf { it != PhysicalChannelConfig.BAND_UNKNOWN }
            }.getOrNull()
        } else null

        val bandwidthKhz: Int? = runCatching {
            config.cellBandwidthDownlinkKhz
        }.getOrNull()

        val connectionStatus: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (runCatching { config.connectionStatus }.getOrDefault(PhysicalChannelConfig.CONNECTION_UNKNOWN)) {
                PhysicalChannelConfig.CONNECTION_PRIMARY_SERVING -> "PCC"
                PhysicalChannelConfig.CONNECTION_SECONDARY_SERVING -> "SCC"
                else -> "UNKNOWN"
            }
        } else "UNKNOWN"

        val dlModulation: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val method = PhysicalChannelConfig::class.java.getDeclaredMethod("getDownlinkModulation")
                method.isAccessible = true
                when (method.invoke(config) as? Int) {
                    1 -> "QPSK"
                    2 -> "16QAM"
                    3 -> "64QAM"
                    4 -> "256QAM"
                    else -> "-"
                }
            } catch (_: Exception) {
                "-"
            }
        } else "-"

        return CarrierInfo(
            band = band,
            bandwidthKhz = bandwidthKhz,
            connectionStatus = connectionStatus,
            dlModulation = dlModulation
        )
    }

    private companion object {
        const val TAG = "HiddenRadioCollector"
    }
}
