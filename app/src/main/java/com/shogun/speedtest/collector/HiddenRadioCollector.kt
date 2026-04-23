package com.shogun.speedtest.collector

import android.content.Context
import android.os.Build
import android.os.IBinder
import android.telephony.PhysicalChannelConfig
import android.telephony.SubscriptionManager
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
        collectViaPrivilegedBinder(telephonyManager)?.let { return it }
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

    private fun collectViaPrivilegedBinder(
        telephonyManager: TelephonyManager
    ): List<PhysicalChannelConfig>? {
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
                    "privileged configs=${physicalChannelConfigs.size}"
                )
                latch.countDown()
            }
        }
        var registry: Any? = null
        var callbackBinder: Any? = null

        return try {
            HiddenRadioDebugLog.add("binder_wrap", "attempting privileged telephony.registry wrap")
            registry = createPrivilegedRegistry()
            initializeTelephonyCallback(callback, executor)
            callbackBinder = getTelephonyCallbackBinder(callback)
            invokeRegistryListen(
                registry = registry,
                telephonyManager = telephonyManager,
                callbackBinder = callbackBinder,
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
                if (registry != null && callbackBinder != null) {
                    invokeRegistryListen(
                        registry = registry,
                        telephonyManager = telephonyManager,
                        callbackBinder = callbackBinder,
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
            executor.shutdown()
        }
    }

    private fun createPrivilegedRegistry(): Any {
        val getSystemService = Class.forName("rikka.shizuku.SystemServiceHelper")
            .getMethod("getSystemService", String::class.java)
        val registryBinder = sequenceOf("telephony.registry", "telephony_registry")
            .mapNotNull { serviceName ->
                runCatching {
                    getSystemService.invoke(null, serviceName) as? IBinder
                }.getOrNull()
            }
            .firstOrNull()
            ?: throw IllegalStateException("telephony registry binder unavailable")
        val wrappedBinder = Class.forName("rikka.shizuku.ShizukuBinderWrapper")
            .getConstructor(IBinder::class.java)
            .newInstance(registryBinder) as IBinder
        val registryStub = Class.forName("com.android.internal.telephony.ITelephonyRegistry\$Stub")
        return registryStub.getMethod("asInterface", IBinder::class.java)
            .invoke(null, wrappedBinder)
            ?: throw IllegalStateException("ITelephonyRegistry unavailable")
    }

    private fun initializeTelephonyCallback(
        callback: TelephonyCallback,
        executor: java.util.concurrent.Executor
    ) {
        val init = TelephonyCallback::class.java.getDeclaredMethod(
            "init",
            java.util.concurrent.Executor::class.java
        )
        init.isAccessible = true
        init.invoke(callback, executor)
    }

    private fun getTelephonyCallbackBinder(callback: TelephonyCallback): Any {
        val field = TelephonyCallback::class.java.getDeclaredField("callback")
        field.isAccessible = true
        return field.get(callback)
            ?: throw IllegalStateException("TelephonyCallback callback binder missing")
    }

    private fun invokeRegistryListen(
        registry: Any,
        telephonyManager: TelephonyManager,
        callbackBinder: Any,
        events: IntArray,
        notifyNow: Boolean
    ) {
        val featureId = context.attributionTag ?: ""
        val subId = telephonyManager.subscriptionId
            .takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            ?: SubscriptionManager.getDefaultSubscriptionId()
        val method = registry.javaClass.methods.firstOrNull { candidate ->
            candidate.name == "listenWithEventList"
        } ?: throw NoSuchMethodException("listenWithEventList")
        val args = when (method.parameterTypes.size) {
            6 -> arrayOf(
                subId,
                context.packageName,
                featureId,
                callbackBinder,
                events,
                notifyNow
            )
            8 -> arrayOf(
                false,
                false,
                subId,
                context.packageName,
                featureId,
                callbackBinder,
                events,
                notifyNow
            )
            else -> throw IllegalStateException(
                "Unsupported listenWithEventList arity=${method.parameterTypes.size}"
            )
        }
        method.invoke(registry, *args)
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
        const val EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED = 33
    }
}
