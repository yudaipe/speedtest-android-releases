package com.shogun.speedtest.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Telephony
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellLocation
import android.telephony.CellSignalStrengthLte
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getMainExecutor
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
    val rsrpVariance: Double? = null
)

class CellularInfoCollector(private val context: Context) {

    private val rsrpSamples = Collections.synchronizedList(mutableListOf<Int>())
    private var handoverCount = 0
    private var firstCellLocationObserved = false
    private var listener: PhoneStateListener? = null
    private var telephonyCallback: TrackingTelephonyCallback? = null
    private var lastServingCellKey: String? = null

    fun startTracking() {
        if (!isCellularActiveNetwork()) return
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
        if (!isCellularActiveNetwork()) {
            listener = null
            telephonyCallback = null
            return CellularInfo()
        }
        val telephonyManager = resolveTelephonyManager()
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

        return CellularInfo(
            rsrpDbm = getRsrp(telephonyManager),
            rsrqDb = getRsrq(telephonyManager),
            sinrDb = getSinr(telephonyManager),
            rssiDbm = getRssi(telephonyManager),
            pci = getPci(telephonyManager),
            tac = getTac(telephonyManager),
            earfcn = getEarfcn(telephonyManager),
            bandNumber = getBandNumber(telephonyManager),
            networkType = getNetworkType(telephonyManager),
            carrierName = telephonyManager.networkOperatorName?.takeIf { it.isNotBlank() },
            apn = getApn(),
            isCarrierAggregation = getIsCarrierAggregation(telephonyManager),
            caBandwidthMhz = getCaBandwidthMhz(telephonyManager),
            caBandConfig = getCaBandConfig(telephonyManager),
            nrState = getNrState(telephonyManager),
            mcc = getMcc(telephonyManager),
            mnc = getMnc(telephonyManager),
            cqi = getCqi(telephonyManager),
            timingAdvance = getTimingAdvance(telephonyManager),
            visibleCellCount = getVisibleCellCount(telephonyManager),
            handoverCount = handoverCount,
            endcAvailable = getEndcAvailable(telephonyManager),
            rsrpVariance = getRsrpVariance()
        )
    }

    private fun hasReadPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isCellularActiveNetwork(): Boolean {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (_: Exception) {
            false
        }
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

    private fun getApn(): String? {
        if (!isCellularActiveNetwork()) return null
        return try {
            context.contentResolver.query(
                Telephony.Carriers.CONTENT_URI,
                arrayOf(Telephony.Carriers.APN),
                "current = ?",
                arrayOf("1"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            getPrimaryNrCell(telephonyManager) != null ||
                telephonyManager.dataNetworkType == TelephonyManager.NETWORK_TYPE_NR
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
                    ?.let { (it as? CellIdentityNr)?.nrarfcn }
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
            getPrimaryLteCell(telephonyManager)
                ?.cellIdentity
                ?.let { identity ->
                    getBandFromEarfcn(identity.earfcn.takeIf { it > 0 && it != Int.MAX_VALUE })
                        ?: getLteBand(identity)
                }
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    getPrimaryNrCell(telephonyManager)
                        ?.cellIdentity
                        ?.let { getNrBand(it as? CellIdentityNr) }
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
                getPrimaryLteCell(telephonyManager)
                    ?.cellIdentity
                    ?.bandwidth
                    ?.takeIf { it > 0 && it != Int.MAX_VALUE }
                    ?.div(1000)
                    ?.takeIf { it <= 20 }
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
                        val band = getLteBand(identity)
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
                        val band = getNrBand(cell.cellIdentity as? CellIdentityNr)
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

    private fun getLteBand(identity: CellIdentityLte?): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return identity?.bands?.firstOrNull()
    }

    private fun getBandFromEarfcn(earfcn: Int?): Int? {
        if (earfcn == null || earfcn <= 0 || earfcn == Int.MAX_VALUE) return null
        return when (earfcn) {
            in 0..599 -> 1
            in 600..1199 -> 2
            in 1200..1949 -> 3
            in 1950..2399 -> 4
            in 2400..2649 -> 5
            in 2650..2749 -> 6
            in 2750..3449 -> 7
            in 3450..3799 -> 8
            in 3800..4149 -> 9
            in 4150..4749 -> 10
            in 4750..4949 -> 11
            in 5010..5179 -> 12
            in 5180..5279 -> 13
            in 5280..5379 -> 14
            in 5730..5849 -> 17
            in 5850..5999 -> 18
            in 6000..6149 -> 19
            in 6150..6449 -> 20
            in 6450..6599 -> 21
            in 6600..7399 -> 22
            in 7500..7699 -> 23
            in 7700..8039 -> 24
            in 8040..8689 -> 25
            in 8690..9039 -> 26
            in 9040..9209 -> 27
            in 9210..9659 -> 28
            in 9660..9769 -> 29
            in 9770..9869 -> 30
            in 9870..9919 -> 31
            in 9920..10359 -> 32
            in 36000..36199 -> 33
            in 36200..36349 -> 34
            in 36350..36949 -> 35
            in 36950..37549 -> 36
            in 37550..37749 -> 37
            in 37750..38249 -> 38
            in 38250..38649 -> 39
            in 38650..39649 -> 40
            in 39650..41589 -> 41
            in 41590..43589 -> 42
            in 43590..45589 -> 43
            in 45590..46589 -> 44
            in 46590..46789 -> 45
            in 46790..54539 -> 46
            in 54540..55239 -> 47
            in 55240..56739 -> 48
            in 56740..58239 -> 49
            in 58240..59089 -> 50
            in 59090..59139 -> 51
            in 59140..60139 -> 52
            in 60140..60189 -> 53
            in 65536..66435 -> 65
            in 66436..67335 -> 66
            in 67336..67535 -> 67
            in 67536..67835 -> 68
            in 67836..68335 -> 69
            in 68336..68585 -> 70
            in 68586..68935 -> 71
            in 68936..68985 -> 72
            in 68986..69035 -> 73
            in 69036..69465 -> 74
            in 69466..70315 -> 75
            in 70316..70365 -> 76
            in 70366..70545 -> 85
            in 70546..70595 -> 87
            in 70596..70645 -> 88
            else -> null
        }
    }

    private fun getNrBand(identity: CellIdentityNr?): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return identity?.bands?.firstOrNull()
    }

    private fun getNrState(telephonyManager: TelephonyManager): String? {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            when {
                telephonyManager.dataNetworkType == TelephonyManager.NETWORK_TYPE_NR -> "CONNECTED"
                getPrimaryNrCell(telephonyManager) != null -> "AVAILABLE"
                else -> "NONE"
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
