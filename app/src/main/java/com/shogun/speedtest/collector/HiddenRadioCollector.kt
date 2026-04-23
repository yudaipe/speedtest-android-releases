package com.shogun.speedtest.collector

import android.content.Context
import android.os.Build
import android.telephony.PhysicalChannelConfig
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import com.shogun.speedtest.data.CarrierInfo
import com.shogun.speedtest.data.HiddenRadioSnapshot
import com.shogun.speedtest.debug.HiddenRadioDebugLog
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HiddenRadioCollector(private val context: Context) {

    @Suppress("UNCHECKED_CAST")
    fun collect(): HiddenRadioSnapshot {
        val sdkInt = Build.VERSION.SDK_INT
        HiddenRadioDebugLog.add("collect_start", "sdk=$sdkInt")
        HiddenRadioDebugLog.add("sdk_version", sdkInt.toString())
        HiddenRadioDebugLog.add(
            "privileged_context",
            "Shizuku permission is granted, but app-side TelephonyManager is still used"
        )
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val configs: List<PhysicalChannelConfig> = when {
            sdkInt >= Build.VERSION_CODES.S -> collectViaListener(tm)
            else -> collectViaReflection(tm, sdkInt)
        }
        HiddenRadioDebugLog.updateRawDump(configs)
        if (configs.isEmpty()) {
            HiddenRadioDebugLog.add("empty_result", "sdk=$sdkInt configs=0")
        }

        val carriers = configs.map { config -> buildCarrierInfo(config) }
        Log.d(TAG, "collect mapped carrierCount=${carriers.size}")
        HiddenRadioDebugLog.add("collect_success", "configs=${configs.size}")
        return HiddenRadioSnapshot(componentCarriers = carriers)
    }

    @Suppress("UNCHECKED_CAST")
    private fun collectViaReflection(
        telephonyManager: TelephonyManager,
        sdkInt: Int
    ): List<PhysicalChannelConfig> {
        HiddenRadioDebugLog.add("collect_path", "reflection sdk=$sdkInt")
        return try {
            val method = TelephonyManager::class.java
                .getDeclaredMethod("getPhysicalChannelConfigList")
            method.isAccessible = true
            ((method.invoke(telephonyManager) as? List<PhysicalChannelConfig>) ?: emptyList()).also {
                Log.d(TAG, "getPhysicalChannelConfigList succeeded size=${it.size}")
            }
        } catch (e: NoSuchMethodException) {
            logFailure("method_not_found", sdkInt, e)
            emptyList()
        } catch (e: SecurityException) {
            logFailure("security_denied", sdkInt, e)
            emptyList()
        } catch (e: Exception) {
            logFailure("collect_fail", sdkInt, e)
            emptyList()
        }
    }

    private fun collectViaListener(
        telephonyManager: TelephonyManager
    ): List<PhysicalChannelConfig> {
        HiddenRadioDebugLog.add("collect_path", "listener sdk=${Build.VERSION.SDK_INT}")
        val latch = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val configsRef = AtomicReference<List<PhysicalChannelConfig>>(emptyList())
        val callback = object : TelephonyCallback(), TelephonyCallback.PhysicalChannelConfigListener {
            override fun onPhysicalChannelConfigChanged(
                physicalChannelConfigs: List<PhysicalChannelConfig>
            ) {
                configsRef.set(physicalChannelConfigs)
                HiddenRadioDebugLog.add(
                    "listener_callback",
                    "configs=${physicalChannelConfigs.size}"
                )
                latch.countDown()
            }
        }

        return try {
            telephonyManager.registerTelephonyCallback(executor, callback)
            val received = latch.await(LISTENER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!received) {
                HiddenRadioDebugLog.add(
                    "empty_result",
                    "sdk=${Build.VERSION.SDK_INT} listener_timeout=${LISTENER_TIMEOUT_MS}ms"
                )
            }
            configsRef.get()
        } catch (e: SecurityException) {
            logFailure("security_denied", Build.VERSION.SDK_INT, e)
            emptyList()
        } catch (e: Exception) {
            logFailure("collect_fail", Build.VERSION.SDK_INT, e)
            emptyList()
        } finally {
            runCatching {
                telephonyManager.unregisterTelephonyCallback(callback)
            }
            executor.shutdown()
        }
    }

    private fun logFailure(reason: String, sdkInt: Int, throwable: Exception) {
        Log.w(
            TAG,
            "$reason while collecting PCC; app-side TelephonyManager is not binder-elevated",
            throwable
        )
        HiddenRadioDebugLog.add(
            event = reason,
            detail = "sdk=$sdkInt ${throwable.javaClass.simpleName}: ${throwable.message ?: "unknown"}",
            stacktrace = HiddenRadioDebugLog.stacktrace(throwable, 5)
        )
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
        const val LISTENER_TIMEOUT_MS = 1500L
    }
}
