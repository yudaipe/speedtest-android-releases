package com.shogun.speedtest.collector

import android.content.Context
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.telephony.PhysicalChannelConfig
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import com.shogun.speedtest.data.CarrierInfo
import com.shogun.speedtest.data.HiddenRadioSnapshot
import com.shogun.speedtest.debug.HiddenRadioDebugLog
import com.shogun.speedtest.shizuku.PhysicalChannelConfigListenerBinder
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class HiddenRadioCollector(private val context: Context) {

    @Suppress("UNCHECKED_CAST")
    fun collect(): HiddenRadioSnapshot {
        val sdkInt = Build.VERSION.SDK_INT
        HiddenRadioDebugLog.add("collect_start", "sdk=$sdkInt")
        HiddenRadioDebugLog.add("sdk_version", sdkInt.toString())
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
        collectViaPrivilegedBinder()?.let { return it }
        HiddenRadioDebugLog.add(
            "privileged_context",
            "binder wrap unavailable; falling back to app-side TelephonyManager"
        )
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

    private fun collectViaPrivilegedBinder(): List<PhysicalChannelConfig>? {
        val latch = CountDownLatch(1)
        val configsRef = AtomicReference<List<PhysicalChannelConfig>>(emptyList())
        val callback = PhysicalChannelConfigListenerBinder { physicalChannelConfigs ->
            configsRef.set(physicalChannelConfigs)
            latch.countDown()
        }
        var registry: PrivilegedRegistry? = null

        return try {
            HiddenRadioDebugLog.add("binder_wrap", "attempting privileged telephony.registry wrap")
            registry = createPrivilegedRegistry()
            invokeRegistryListen(
                registry = registry,
                callback = callback,
                events = intArrayOf(EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED),
                notifyNow = true
            )
            HiddenRadioDebugLog.add("binder_wrap", "privileged binder in use")
            HiddenRadioDebugLog.add("privileged_context", "privileged binder in use")
            val received = latch.await(LISTENER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!received) {
                HiddenRadioDebugLog.add(
                    "empty_result",
                    "sdk=${Build.VERSION.SDK_INT} privileged_listener_timeout=${LISTENER_TIMEOUT_MS}ms"
                )
            }
            configsRef.get()
        } catch (e: SecurityException) {
            logFailure("binder_wrap_fail", Build.VERSION.SDK_INT, e)
            null
        } catch (e: Exception) {
            logFailure("binder_wrap_fail", Build.VERSION.SDK_INT, e)
            null
        } finally {
            runCatching {
                if (registry != null) {
                    invokeRegistryListen(
                        registry = registry,
                        callback = callback,
                        events = intArrayOf(),
                        notifyNow = false
                    )
                }
            }.onFailure {
                HiddenRadioDebugLog.add(
                    "binder_unwrap_fail",
                    "${it.javaClass.simpleName}: ${it.message ?: "unknown"}"
                )
            }
        }
    }

    private fun createPrivilegedRegistry(): PrivilegedRegistry {
        val registryBinder = sequenceOf("telephony.registry", "telephony_registry")
            .mapNotNull { serviceName ->
                runCatching {
                    SystemServiceHelper.getSystemService(serviceName)
                }.getOrNull()
            }
            .firstOrNull()
            ?: throw IllegalStateException("telephony registry binder unavailable")
        val wrappedBinder: IBinder = ShizukuBinderWrapper(registryBinder)
        val registryStub = Class.forName("com.android.internal.telephony.ITelephonyRegistry\$Stub")
        val registry = registryStub.getMethod("asInterface", IBinder::class.java)
            .invoke(null, wrappedBinder)
            ?: throw IllegalStateException("ITelephonyRegistry unavailable")
        val listenMethod = registry.javaClass.methods.firstOrNull { candidate ->
            candidate.name == "listenWithEventList"
        } ?: throw NoSuchMethodException("listenWithEventList")
        return PrivilegedRegistry(
            binder = wrappedBinder,
            listenArity = listenMethod.parameterTypes.size
        )
    }

    private fun invokeRegistryListen(
        registry: PrivilegedRegistry,
        callback: IBinder,
        events: IntArray,
        notifyNow: Boolean
    ) {
        val featureId = context.attributionTag ?: ""
        val subId = SubscriptionManager.getDefaultSubscriptionId()
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(TELEPHONY_REGISTRY_DESCRIPTOR)
            if (registry.listenArity != 8) {
                throw IllegalStateException(
                    "Unsupported listenWithEventList arity=${registry.listenArity}"
                )
            }
            data.writeInt(0)
            data.writeInt(0)
            data.writeInt(subId)
            data.writeString(context.packageName)
            data.writeString(featureId)
            data.writeStrongBinder(callback)
            data.writeIntArray(events)
            data.writeInt(if (notifyNow) 1 else 0)
            val success = registry.binder.transact(
                TRANSACTION_LISTEN_WITH_EVENT_LIST,
                data,
                reply,
                0
            )
            if (!success) {
                throw IllegalStateException("listenWithEventList transact failed")
            }
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
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
        const val EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED = 19
        const val TELEPHONY_REGISTRY_DESCRIPTOR = "com.android.internal.telephony.ITelephonyRegistry"
        const val TRANSACTION_LISTEN_WITH_EVENT_LIST = IBinder.FIRST_CALL_TRANSACTION + 3
    }

    private data class PrivilegedRegistry(
        val binder: IBinder,
        val listenArity: Int
    )
}
