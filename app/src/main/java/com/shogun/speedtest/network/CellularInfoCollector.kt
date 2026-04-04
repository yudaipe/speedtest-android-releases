package com.shogun.speedtest.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.CellIdentityLte
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellLocation
import android.telephony.CellSignalStrengthLte
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getMainExecutor
import com.google.gson.Gson
import java.util.Collections

data class CellularInfo(
    val rsrpDbm: Int? = null,
    val rsrqDb: Int? = null,
    val sinrDb: Int? = null,
    val rssiDbm: Int? = null,
    val pci: Int? = null,
    val tac: Int? = null,
    val earfcn: Int? = null,
    val bandNumber: Int? = null,
    val networkType: String? = null,
    val carrierName: String? = null,
    val physicalCarrier: String? = null,
    val apn: String? = null,
    val isCarrierAggregation: Boolean? = null,
    val caBandwidthMhz: Int? = null,
    val caBandConfig: String? = null,
    val nrState: String? = null,
    val mcc: String? = null,
    val mnc: String? = null,
    val cqi: Int? = null,
    val timingAdvance: Int? = null,
    val visibleCellCount: Int? = null,
    val handoverCount: Int? = null,
    val endcAvailable: Boolean? = null,
    val rsrpVariance: Double? = null,
    val bandNumberDirect: Int? = null,
    val caComponentsJson: String? = null,
    val nrType: String? = null,
    val hiddenEndcAvailable: Boolean? = null,
    val neighborCellsJson: String? = null,
    val cellId: Long? = null,
    val enbId: Long? = null
)

class CellularInfoCollector(private val context: Context) {

    private val gson = Gson()
    private val hiddenBandInfoProvider = HiddenBandInfoProvider(context)
    private val rsrpSamples = Collections.synchronizedList(mutableListOf<Int>())
    private var handoverCount = 0
    private var firstCellLocationObserved = false
    private var listener: PhoneStateListener? = null
    private var telephonyCallback: TrackingTelephonyCallback? = null
    private var lastServingCellKey: String? = null

    fun startTracking() {
        if (!hasReadPhoneStatePermission()) return
        val telephonyManager = resolveTelephonyManager()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = TrackingTelephonyCallback(telephonyManager)
            telephonyCallback = callback
            try {
                telephonyManager.registerTelephonyCallback(getMainExecutor(context), callback)
            } catch (_: Exception) {
                telephonyCallback = null
            }
        } else {
            val phoneStateListener = object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                    signalStrength ?: return
                    sampleRsrp(signalStrength, telephonyManager)
                }

                override fun onCellLocationChanged(location: CellLocation?) {
                    if (firstCellLocationObserved) {
                        handoverCount += 1
                    } else {
                        firstCellLocationObserved = true
                    }
                }
            }
            listener = phoneStateListener
            try {
                @Suppress("DEPRECATION")
                telephonyManager.listen(
                    phoneStateListener,
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_CELL_LOCATION
                )
            } catch (_: Exception) {
                listener = null
            }
        }
        try {
            telephonyManager.signalStrength?.let { sampleRsrp(it, telephonyManager) }
        } catch (_: Exception) {
        }
    }

    fun stopAndCollect(): CellularInfo {
        val telephonyManager = resolveTelephonyManager()
        val hiddenBandInfo = hiddenBandInfoProvider.collect(telephonyManager)
        listener?.let {
            try {
                @Suppress("DEPRECATION")
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            } catch (_: Exception) {
            }
        }
        telephonyCallback?.let {
            try {
                telephonyManager.unregisterTelephonyCallback(it)
            } catch (_: Exception) {
            }
        }
        listener = null
        telephonyCallback = null

        val mcc = getMcc(telephonyManager)
        val mnc = getMnc(telephonyManager)

        return CellularInfo(
            rsrpDbm = getRsrp(telephonyManager),
            rsrqDb = getRsrq(telephonyManager),
            sinrDb = getSinr(telephonyManager),
            rssiDbm = getRssi(telephonyManager),
            pci = getPci(telephonyManager),
            tac = getTac(telephonyManager),
            earfcn = getEarfcn(telephonyManager),
            bandNumber = hiddenBandInfo.bandNumberDirect ?: getBandNumber(telephonyManager),
            networkType = getNetworkType(telephonyManager),
            carrierName = telephonyManager.networkOperatorName?.takeIf { it.isNotBlank() },
            physicalCarrier = if (mcc != null && mnc != null) {
                PlmnCarrierMapper.getPhysicalCarrier(mcc, mnc)
            } else {
                null
            },
            apn = getApnName(telephonyManager),
            isCarrierAggregation = hiddenBandInfo.caComponents?.let { it.size > 1 } ?: getIsCarrierAggregation(telephonyManager),
            caBandwidthMhz = hiddenBandInfo.caComponents
                ?.mapNotNull { it.bandwidthMhz }
                ?.takeIf { it.isNotEmpty() }
                ?.sum()
                ?: getCaBandwidthMhz(telephonyManager),
            caBandConfig = hiddenBandInfo.caComponents
                ?.mapNotNull { component ->
                    when {
                        component.band != null && component.bandwidthMhz != null -> "Band${component.band}(${component.bandwidthMhz}MHz)"
                        component.band != null -> "Band${component.band}"
                        else -> null
                    }
                }
                ?.distinct()
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString("+")
                ?: getCaBandConfig(telephonyManager),
            nrState = hiddenBandInfo.nrType ?: getNrState(telephonyManager),
            mcc = mcc,
            mnc = mnc,
            cqi = getCqi(telephonyManager),
            timingAdvance = getTimingAdvance(telephonyManager),
            visibleCellCount = hiddenBandInfo.neighborCells?.let { it.size + (hiddenBandInfo.caComponents?.size ?: 0) }
                ?: getVisibleCellCount(telephonyManager),
            handoverCount = handoverCount,
            endcAvailable = hiddenBandInfo.enDcAvailable ?: getEndcAvailable(telephonyManager),
            rsrpVariance = getRsrpVariance(),
            bandNumberDirect = hiddenBandInfo.bandNumberDirect,
            caComponentsJson = hiddenBandInfo.caComponents?.let(gson::toJson),
            nrType = hiddenBandInfo.nrType,
            hiddenEndcAvailable = hiddenBandInfo.enDcAvailable,
            neighborCellsJson = hiddenBandInfo.neighborCells?.let(gson::toJson),
            cellId = hiddenBandInfo.cellId,
            enbId = hiddenBandInfo.enbId
        )
    }

    private fun hasReadPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun resolveTelephonyManager(): TelephonyManager {
        val baseManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (!hasReadPhoneStatePermission() || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return baseManager
        }

        return try {
            val dataSubId = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> SubscriptionManager.getActiveDataSubscriptionId()
                else -> SubscriptionManager.getDefaultDataSubscriptionId()
            }
            if (dataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                baseManager.createForSubscriptionId(dataSubId)
            } else {
                baseManager
            }
        } catch (_: Throwable) {
            baseManager
        }
    }

    private fun sampleRsrp(signalStrength: SignalStrength, telephonyManager: TelephonyManager) {
        (getRsrpFromSignalStrength(signalStrength) ?: getRsrpFromCells(telephonyManager))
            ?.let { rsrpSamples.add(it) }
    }

    private fun getRsrpVariance(): Double? {
        val samples = synchronized(rsrpSamples) { rsrpSamples.toList() }
        if (samples.size < 2) return null
        val mean = samples.average()
        return samples.map { sample ->
            val diff = sample - mean
            diff * diff
        }.average()
    }

    private fun getNetworkType(telephonyManager: TelephonyManager): String? {
        return try {
            when (telephonyManager.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "NR"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP"
                TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
                TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
                TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
                TelephonyManager.NETWORK_TYPE_UNKNOWN -> "UNKNOWN"
                else -> telephonyManager.dataNetworkType.toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getApnName(telephonyManager: TelephonyManager): String? {
        val queriedApn = try {
            val projection = arrayOf("apn")
            context.contentResolver.query(
                Telephony.Carriers.CONTENT_URI,
                projection,
                "current = ?",
                arrayOf("1"),
                null
            )?.use { cursor ->
                val apnIndex = cursor.getColumnIndex("apn")
                while (cursor.moveToNext()) {
                    if (apnIndex >= 0) {
                        val value = cursor.getString(apnIndex)?.trim()
                        if (!value.isNullOrBlank()) {
                            return@use value
                        }
                    }
                }
                null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }

        return queriedApn
            ?: telephonyManager.networkOperatorName?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun getIsCarrierAggregation(telephonyManager: TelephonyManager): Boolean? {
        val bands = getCaBandConfig(telephonyManager)
        if (!bands.isNullOrBlank() && bands.contains("+")) return true
        val bandwidth = getCaBandwidthMhz(telephonyManager)
        return bandwidth?.let { it > 20 }
    }

    private fun getTimingAdvance(telephonyManager: TelephonyManager): Int? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getPrimaryLteCell(telephonyManager)
                    ?.cellSignalStrength
                    ?.timingAdvance
                    ?.takeIf { it != Int.MAX_VALUE && it >= 0 }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getVisibleCellCount(telephonyManager: TelephonyManager): Int? {
        return try {
            getAllCellInfo(telephonyManager)?.size
        } catch (_: Exception) {
            null
        }
    }

    private fun getEndcAvailable(telephonyManager: TelephonyManager): Boolean? {
        return try {
            val serviceState = telephonyManager.serviceState ?: return null
            val method = ServiceState::class.java.methods.firstOrNull { it.name == "isNrAvailable" } ?: return null
            method.invoke(serviceState) as? Boolean
        } catch (_: Exception) {
            null
        }
    }

    private fun getRsrp(telephonyManager: TelephonyManager): Int? {
        return try {
            telephonyManager.signalStrength?.let { getRsrpFromSignalStrength(it) } ?: getRsrpFromCells(telephonyManager)
        } catch (_: Exception) {
            null
        }
    }

    private fun getRsrq(telephonyManager: TelephonyManager): Int? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.signalStrength
                    ?.cellSignalStrengths
                    ?.filterIsInstance<CellSignalStrengthLte>()
                    ?.firstOrNull()
                    ?.rsrq
                    ?.takeIf { it != Int.MAX_VALUE }
            } else {
                getPrimaryLteCell(telephonyManager)?.cellSignalStrength?.rsrq?.takeIf { it != Int.MAX_VALUE && it != Int.MIN_VALUE }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getSinr(telephonyManager: TelephonyManager): Int? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.signalStrength
                    ?.cellSignalStrengths
                    ?.filterIsInstance<CellSignalStrengthLte>()
                    ?.firstOrNull()
                    ?.rssnr
                    ?.takeIf { it != Int.MAX_VALUE }
            } else {
                getPrimaryLteCell(telephonyManager)?.cellSignalStrength?.rssnr?.takeIf { it != Int.MAX_VALUE && it != Int.MIN_VALUE }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getRssi(telephonyManager: TelephonyManager): Int? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.signalStrength
                    ?.cellSignalStrengths
                    ?.filterIsInstance<CellSignalStrengthLte>()
                    ?.firstOrNull()
                    ?.rssi
                    ?.takeIf { it != Int.MAX_VALUE }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getCqi(telephonyManager: TelephonyManager): Int? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.signalStrength
                    ?.cellSignalStrengths
                    ?.filterIsInstance<CellSignalStrengthLte>()
                    ?.firstOrNull()
                    ?.cqi
                    ?.takeIf { it in 0..15 }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getPci(telephonyManager: TelephonyManager): Int? {
        return try {
            getPrimaryLteCell(telephonyManager)
                ?.cellIdentity
                ?.pci
                ?.takeIf { it != Int.MAX_VALUE }
        } catch (_: Exception) {
            null
        }
    }

    private fun getTac(telephonyManager: TelephonyManager): Int? {
        return try {
            getPrimaryLteCell(telephonyManager)
                ?.cellIdentity
                ?.tac
                ?.takeIf { it != Int.MAX_VALUE }
        } catch (_: Exception) {
            null
        }
    }

    private fun getEarfcn(telephonyManager: TelephonyManager): Int? {
        return try {
            val lte = getPrimaryLteCell(telephonyManager)
            if (lte != null) {
                lte.cellIdentity.earfcn.takeIf { it > 0 && it != Int.MAX_VALUE }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getPrimaryNrCell(telephonyManager)
                    ?.cellIdentity
                    ?.let { getIntViaReflection(it, "getNrarfcn") }
                    ?.takeIf { it > 0 && it != Int.MAX_VALUE }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getBandNumber(telephonyManager: TelephonyManager): Int? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getPrimaryLteCell(telephonyManager)
                    ?.cellIdentity
                    ?.let { getBandsViaReflection(it)?.firstOrNull() }
                    ?: getPrimaryNrCell(telephonyManager)
                        ?.cellIdentity
                        ?.let { getBandsViaReflection(it)?.firstOrNull() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getCaBandwidthMhz(telephonyManager: TelephonyManager): Int? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                telephonyManager.serviceState?.cellBandwidths
                    ?.takeIf { it.isNotEmpty() }
                    ?.sum()
                    ?.div(1000)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getCaBandConfig(telephonyManager: TelephonyManager): String? {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
            val parts = mutableListOf<String>()
            getAllCellInfo(telephonyManager)?.forEach { cell ->
                val isServing = cell.cellConnectionStatus == CellInfo.CONNECTION_PRIMARY_SERVING ||
                    cell.cellConnectionStatus == CellInfo.CONNECTION_SECONDARY_SERVING
                if (!isServing) return@forEach
                val entry = when {
                    cell is CellInfoLte -> {
                        val identity = cell.cellIdentity
                        val band = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            getBandsViaReflection(identity)?.firstOrNull()
                        } else {
                            null
                        }
                        val bandwidthMhz = identity.bandwidth
                            .takeIf { it > 0 && it != Int.MAX_VALUE }
                            ?.div(1000)
                        when {
                            band != null && bandwidthMhz != null -> "Band$band(${bandwidthMhz}MHz)"
                            band != null -> "Band$band"
                            else -> null
                        }
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell is CellInfoNr -> {
                        val band = getBandsViaReflection(cell.cellIdentity)?.firstOrNull()
                        band?.let { "NR-Band$it" }
                    }
                    else -> null
                }
                if (!entry.isNullOrBlank() && !parts.contains(entry)) {
                    parts += entry
                }
            }
            parts.takeIf { it.isNotEmpty() }?.joinToString("+")
        } catch (_: Exception) {
            null
        }
    }

    private fun getNrState(telephonyManager: TelephonyManager): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceState = telephonyManager.serviceState ?: return null
                val method = ServiceState::class.java.getMethod("getNrState")
                when (method.invoke(serviceState) as? Int) {
                    0 -> "NONE"
                    1 -> "RESTRICTED"
                    2 -> "NOT_RESTRICTED"
                    3 -> "CONNECTED"
                    else -> null
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getMcc(telephonyManager: TelephonyManager): String? {
        return try {
            telephonyManager.networkOperator?.takeIf { it.length >= 3 }?.substring(0, 3)
        } catch (_: Exception) {
            null
        }
    }

    private fun getMnc(telephonyManager: TelephonyManager): String? {
        return try {
            telephonyManager.networkOperator?.takeIf { it.length >= 5 }?.substring(3)
        } catch (_: Exception) {
            null
        }
    }

    private fun getRsrpFromSignalStrength(signalStrength: SignalStrength): Int? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                signalStrength.cellSignalStrengths
                    .filterIsInstance<CellSignalStrengthLte>()
                    .firstOrNull()
                    ?.rsrp
                    ?.takeIf { it != Int.MAX_VALUE }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getRsrpFromCells(telephonyManager: TelephonyManager): Int? {
        return try {
            getPrimaryLteCell(telephonyManager)
                ?.cellSignalStrength
                ?.rsrp
                ?.takeIf { it != Int.MAX_VALUE && it != Int.MIN_VALUE }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun getAllCellInfo(telephonyManager: TelephonyManager): List<CellInfo>? {
        if (!hasFineLocationPermission()) return null
        return try {
            telephonyManager.allCellInfo
        } catch (_: Exception) {
            null
        }
    }

    private fun getPrimaryLteCell(telephonyManager: TelephonyManager): CellInfoLte? {
        return getAllCellInfo(telephonyManager)
            ?.filterIsInstance<CellInfoLte>()
            ?.let { cells ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cells.firstOrNull { it.cellConnectionStatus == CellInfo.CONNECTION_PRIMARY_SERVING }
                        ?: cells.firstOrNull { it.isRegistered }
                        ?: cells.firstOrNull()
                } else {
                    cells.firstOrNull { it.isRegistered } ?: cells.firstOrNull()
                }
            }
    }

    private fun getPrimaryNrCell(telephonyManager: TelephonyManager): CellInfoNr? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return getAllCellInfo(telephonyManager)
            ?.filterIsInstance<CellInfoNr>()
            ?.let { cells ->
                cells.firstOrNull { it.isRegistered } ?: cells.firstOrNull()
            }
    }

    private fun getBandsViaReflection(identity: Any): IntArray? {
        return try {
            val method = identity.javaClass.methods.firstOrNull { it.name == "getBands" } ?: return null
            method.invoke(identity) as? IntArray
        } catch (_: Exception) {
            null
        }
    }

    private fun getIntViaReflection(target: Any, methodName: String): Int? {
        return try {
            val method = target.javaClass.methods.firstOrNull { it.name == methodName } ?: return null
            method.invoke(target) as? Int
        } catch (_: Exception) {
            null
        }
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateServingCellState(cells: List<CellInfo>?) {
        val servingKey = cells
            ?.firstOrNull { it.isRegistered }
            ?.cellIdentity
            ?.toString()
            ?: return
        if (lastServingCellKey == null) {
            lastServingCellKey = servingKey
            return
        }
        if (lastServingCellKey != servingKey) {
            handoverCount += 1
            lastServingCellKey = servingKey
        }
    }

    private inner class TrackingTelephonyCallback(
        private val telephonyManager: TelephonyManager
    ) : TelephonyCallback(),
        TelephonyCallback.SignalStrengthsListener,
        TelephonyCallback.CellInfoListener {

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            sampleRsrp(signalStrength, telephonyManager)
        }

        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
            updateServingCellState(cellInfo)
        }
    }
}
