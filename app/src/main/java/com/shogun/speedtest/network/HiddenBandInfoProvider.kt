package com.shogun.speedtest.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.telephony.CellIdentityLte
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.ServiceState
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

data class CaComponent(
    val band: Int? = null,
    val bandwidthMhz: Int? = null,
    val rsrp: Int? = null
)

data class NeighborCell(
    val pci: Int? = null,
    val rsrp: Int? = null,
    val earfcn: Int? = null
)

data class HiddenBandInfo(
    val bandNumberDirect: Int? = null,
    val caComponents: List<CaComponent>? = null,
    val nrType: String? = null,
    val enDcAvailable: Boolean? = null,
    val neighborCells: List<NeighborCell>? = null,
    val cellId: Long? = null,
    val enbId: Long? = null
)

class HiddenBandInfoProvider(private val context: Context) {

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun collect(telephonyManager: TelephonyManager): HiddenBandInfo {
        if (!isShizukuAvailable()) return HiddenBandInfo()

        val iTelephony = getITelephony() ?: return HiddenBandInfo()
        val cells = getRestrictedCellInfo(iTelephony) ?: getPublicCellInfo(telephonyManager)
        val serviceState = getRestrictedServiceState(iTelephony) ?: telephonyManager.serviceState
        val servingLte = selectServingLte(cells)
        val servingNr = selectServingNr(cells)
        val caComponents = buildCaComponents(cells)
        val neighborCells = buildNeighborCells(cells)
        val cellId = extractCellId(servingLte, servingNr)

        return HiddenBandInfo(
            bandNumberDirect = extractBand(servingLte, servingNr),
            caComponents = caComponents.takeIf { it.isNotEmpty() },
            nrType = resolveNrType(cells, serviceState),
            enDcAvailable = extractEnDcAvailable(serviceState),
            neighborCells = neighborCells.takeIf { it.isNotEmpty() },
            cellId = cellId,
            enbId = deriveEnbId(servingLte, cellId)
        )
    }

    private fun getITelephony(): Any? {
        return try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getService = serviceManagerClass.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "phone") as? IBinder ?: return null
            val wrappedBinder = ShizukuBinderWrapper(binder)
            val stubClass = Class.forName("com.android.internal.telephony.ITelephony\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            asInterface.invoke(null, wrappedBinder)
        } catch (_: Throwable) {
            null
        }
    }

    private fun getRestrictedCellInfo(iTelephony: Any): List<CellInfo>? {
        val raw = invokeFirstCompatible(
            target = iTelephony,
            methodNames = listOf("getAllCellInfo", "getAllCellInfoForSubscriber")
        ) ?: return null
        return when (raw) {
            is List<*> -> raw.filterIsInstance<CellInfo>()
            else -> invokeNoArg(raw, "getList") as? List<*>
        }?.filterIsInstance<CellInfo>()
    }

    private fun getRestrictedServiceState(iTelephony: Any): ServiceState? {
        val raw = invokeFirstCompatible(
            target = iTelephony,
            methodNames = listOf("getServiceState", "getDataRegistrationState")
        ) ?: return null
        return when (raw) {
            is ServiceState -> raw
            else -> invokeNoArg(raw, "getServiceState") as? ServiceState
        }
    }

    private fun getPublicCellInfo(telephonyManager: TelephonyManager): List<CellInfo>? {
        return try {
            telephonyManager.allCellInfo
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildCaComponents(cells: List<CellInfo>?): List<CaComponent> {
        return cells.orEmpty()
            .filter { isServingCell(it) }
            .mapNotNull { cell ->
                when {
                    cell is CellInfoLte -> {
                        val identity = cell.cellIdentity
                        CaComponent(
                            band = getBands(identity)?.firstOrNull(),
                            bandwidthMhz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                identity.bandwidth.takeIf { it > 0 && it != Int.MAX_VALUE }?.div(1000)
                            } else {
                                null
                            },
                            rsrp = cell.cellSignalStrength.rsrp.takeIf { it != Int.MAX_VALUE && it != Int.MIN_VALUE }
                        )
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell is CellInfoNr -> {
                        CaComponent(
                            band = getBands(cell.cellIdentity)?.firstOrNull(),
                            bandwidthMhz = getInt(cell.cellIdentity, "getBandwidth")?.takeIf { it > 0 }?.div(1000),
                            rsrp = getInt(cell.cellSignalStrength, "getSsRsrp")?.takeIf { it != Int.MAX_VALUE }
                        )
                    }
                    else -> null
                }
            }
    }

    private fun buildNeighborCells(cells: List<CellInfo>?): List<NeighborCell> {
        return cells.orEmpty()
            .filterNot { isServingCell(it) }
            .mapNotNull { cell ->
                when {
                    cell is CellInfoLte -> NeighborCell(
                        pci = cell.cellIdentity.pci.takeIf { it != Int.MAX_VALUE },
                        rsrp = cell.cellSignalStrength.rsrp.takeIf { it != Int.MAX_VALUE && it != Int.MIN_VALUE },
                        earfcn = cell.cellIdentity.earfcn.takeIf { it > 0 && it != Int.MAX_VALUE }
                    )
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell is CellInfoNr -> NeighborCell(
                        pci = getInt(cell.cellIdentity, "getPci")?.takeIf { it != Int.MAX_VALUE },
                        rsrp = getInt(cell.cellSignalStrength, "getSsRsrp")?.takeIf { it != Int.MAX_VALUE },
                        earfcn = getInt(cell.cellIdentity, "getNrarfcn")?.takeIf { it > 0 && it != Int.MAX_VALUE }
                    )
                    else -> null
                }
            }
    }

    private fun extractBand(servingLte: CellInfoLte?, servingNr: CellInfoNr?): Int? {
        return servingLte?.cellIdentity?.let { getBands(it)?.firstOrNull() }
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                servingNr?.cellIdentity?.let { getBands(it)?.firstOrNull() }
            } else {
                null
            }
    }

    private fun extractCellId(servingLte: CellInfoLte?, servingNr: CellInfoNr?): Long? {
        return servingLte?.cellIdentity?.ci?.takeIf { it != Int.MAX_VALUE }?.toLong()
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                servingNr?.cellIdentity?.let { getLong(it, "getNci") }
            } else {
                null
            }
    }

    private fun deriveEnbId(servingLte: CellInfoLte?, cellId: Long?): Long? {
        val lteCi = servingLte?.cellIdentity?.ci?.takeIf { it != Int.MAX_VALUE } ?: return null
        if (lteCi <= 0) return cellId?.takeIf { it > 0 }?.div(256)
        return lteCi.toLong() / 256L
    }

    private fun resolveNrType(cells: List<CellInfo>?, serviceState: ServiceState?): String? {
        val hasServingNr = cells.orEmpty().any { it is CellInfoNr && it.isRegistered }
        val hasServingLte = cells.orEmpty().any { it is CellInfoLte && it.isRegistered }
        if (hasServingNr && hasServingLte) return "NSA"
        if (hasServingNr) return "SA"

        val nrState = getInt(serviceState, "getNrState")
        return when {
            nrState == null || nrState == 0 -> "NONE"
            extractEnDcAvailable(serviceState) == true -> "NSA"
            else -> "SA"
        }
    }

    private fun extractEnDcAvailable(serviceState: ServiceState?): Boolean? {
        return invokeBoolean(serviceState, "isEnDcAvailable")
            ?: invokeBoolean(serviceState, "isNrAvailable")
            ?: invokeBoolean(serviceState, "getEnDcAvailable")
    }

    private fun selectServingLte(cells: List<CellInfo>?): CellInfoLte? {
        return cells.orEmpty()
            .filterIsInstance<CellInfoLte>()
            .firstOrNull { isServingCell(it) }
            ?: cells.orEmpty().filterIsInstance<CellInfoLte>().firstOrNull { it.isRegistered }
    }

    private fun selectServingNr(cells: List<CellInfo>?): CellInfoNr? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return cells.orEmpty()
            .filterIsInstance<CellInfoNr>()
            .firstOrNull { isServingCell(it) }
            ?: cells.orEmpty().filterIsInstance<CellInfoNr>().firstOrNull { it.isRegistered }
    }

    private fun isServingCell(cell: CellInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cell.cellConnectionStatus == CellInfo.CONNECTION_PRIMARY_SERVING ||
                cell.cellConnectionStatus == CellInfo.CONNECTION_SECONDARY_SERVING
        } else {
            cell.isRegistered
        }
    }

    private fun getBands(identity: Any): IntArray? {
        return invokeNoArg(identity, "getBands") as? IntArray
    }

    private fun getInt(target: Any?, methodName: String): Int? {
        val value = invokeNoArg(target, methodName) ?: return null
        return value as? Int
    }

    private fun getLong(target: Any?, methodName: String): Long? {
        val value = invokeNoArg(target, methodName) ?: return null
        return value as? Long
    }

    private fun invokeBoolean(target: Any?, methodName: String): Boolean? {
        val value = invokeNoArg(target, methodName) ?: return null
        return value as? Boolean
    }

    private fun invokeNoArg(target: Any?, methodName: String): Any? {
        if (target == null) return null
        return try {
            val method = target.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 0
            } ?: return null
            method.isAccessible = true
            method.invoke(target)
        } catch (_: Throwable) {
            null
        }
    }

    private fun invokeFirstCompatible(target: Any, methodNames: List<String>): Any? {
        for (methodName in methodNames) {
            val candidates = target.javaClass.methods.filter { it.name == methodName }
            for (method in candidates) {
                try {
                    method.isAccessible = true
                    return method.invoke(target, *buildArgs(method.parameterTypes))
                } catch (_: Throwable) {
                }
            }
        }
        return null
    }

    private fun buildArgs(parameterTypes: Array<Class<*>>): Array<Any?> {
        var stringIndex = 0
        return Array(parameterTypes.size) { index ->
            val type = parameterTypes[index]
            when {
                type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType -> {
                    getActiveDataSubId()
                }
                type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType -> {
                    getActiveDataSubId().toLong()
                }
                type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType -> false
                type == String::class.java -> {
                    stringIndex += 1
                    if (stringIndex == 1) context.packageName else context.attributionTag
                }
                else -> null
            }
        }
    }

    private fun getActiveDataSubId(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                SubscriptionManager.getActiveDataSubscriptionId()
            } else {
                SubscriptionManager.getDefaultDataSubscriptionId()
            }
        } catch (_: Throwable) {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }
}
