package com.shogun.speedtest.data

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
    val isSynced: Boolean = false  // Google Sheetsへの同期済みフラグ
)
