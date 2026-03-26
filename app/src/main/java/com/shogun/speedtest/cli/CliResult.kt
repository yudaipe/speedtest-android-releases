package com.shogun.speedtest.cli

import com.google.gson.annotations.SerializedName

// speedtest-go (showwin/speedtest-go) JSON output format
// Top level: {"timestamp": "...", "user_info": {...}, "servers": [...]}
data class CliResult(
    val timestamp: String,
    @field:SerializedName("user_info") val userInfo: UserInfo?,
    val servers: List<ServerResult>?
) {
    // User struct fields are uppercase in Go (no json tags)
    data class UserInfo(
        @field:SerializedName("IP") val ip: String?,
        @field:SerializedName("Isp") val isp: String?,
        @field:SerializedName("Lat") val lat: String?,
        @field:SerializedName("Lon") val lon: String?
    )

    // Server struct: latency/jitter in nanoseconds (time.Duration = int64)
    // dl_speed/ul_speed: ByteRate = float64 bytes/sec
    data class ServerResult(
        val id: String?,
        val name: String?,
        val country: String?,
        val sponsor: String?,
        val latency: Long?,
        val jitter: Long?,
        @field:SerializedName("dl_speed") val dlSpeed: Double?,
        @field:SerializedName("ul_speed") val ulSpeed: Double?,
        val distance: Double?
    )

    private val firstServer: ServerResult? get() = servers?.firstOrNull()

    // ByteRate (bytes/sec) → Mbps: / 125000.0
    val downloadMbps: Double get() = (firstServer?.dlSpeed ?: 0.0) / 125000.0
    val uploadMbps: Double get() = (firstServer?.ulSpeed ?: 0.0) / 125000.0

    // latency/jitter: nanoseconds → ms
    val pingMs: Double get() = (firstServer?.latency ?: 0L) / 1_000_000.0
    val jitterMs: Double get() = (firstServer?.jitter ?: 0L) / 1_000_000.0

    val packetLoss: Double? get() = null
    val isp: String? get() = userInfo?.isp
    val serverName: String? get() = firstServer?.sponsor ?: firstServer?.name
    val serverId: Int get() = firstServer?.id?.toIntOrNull() ?: 48463
    val serverCountry: String? get() = firstServer?.country
    val lat: Double? get() = userInfo?.lat?.toDoubleOrNull()
    val lon: Double? get() = userInfo?.lon?.toDoubleOrNull()
    val distanceKm: Double? get() = firstServer?.distance
    val resultUrl: String? get() = null
    val externalIp: String? get() = userInfo?.ip
}
