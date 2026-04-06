package com.shogun.speedtest.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speedtest_results")
data class SpeedtestResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,           // Unix timestamp (seconds)
    val timestampIso: String,      // ISO 8601 string
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double,
    val jitterMs: Double,
    val packetLoss: Double,
    val isp: String?,
    val serverName: String?,
    val serverId: Int,
    val serverCountry: String?,    // サーバー所在国
    val lat: Double?,              // 緯度
    val lon: Double?,              // 経度
    val distanceKm: Double?,       // サーバーまでの距離(km)
    val resultUrl: String?,        // Speedtest結果URL
    val externalIp: String?,       // 外部IPアドレス
    val deviceModel: String,
    val isSynced: Boolean = false, // Supabaseへの同期済みフラグ
    val wifiSsid: String? = null,
    val connectionType: String? = null,
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
    @ColumnInfo(name = "is_ca") val isCa: String? = null,
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
    val dnsResolveMs: Double? = null,
    val ttfbMs: Double? = null,
    val tcpConnectMs: Double? = null,
    val rsrpVariance: Double? = null,
    val ramUsagePercent: Double? = null,
    val cpuUsagePercent: Double? = null,
    val bgAppCount: Int? = null
)
