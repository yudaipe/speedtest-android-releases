package com.shogun.speedtest.network

import android.app.ActivityManager
import android.content.Context
import kotlin.math.max

data class DeviceMetrics(
    val ramUsagePercent: Double? = null,
    val cpuUsagePercent: Double? = null,
    val bgAppCount: Int? = null
)

class DeviceMetricsCollector(private val context: Context) {

    fun collect(): DeviceMetrics {
        return DeviceMetrics(
            ramUsagePercent = getRamUsagePercent(),
            cpuUsagePercent = getCpuUsagePercent(),
            bgAppCount = getBackgroundAppCount()
        )
    }

    private fun getRamUsagePercent(): Double? {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            if (memoryInfo.totalMem <= 0L) {
                null
            } else {
                ((memoryInfo.totalMem - memoryInfo.availMem).toDouble() / memoryInfo.totalMem.toDouble()) * 100.0
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getCpuUsagePercent(): Double? {
        return try {
            val first = readCpuStat() ?: return null
            Thread.sleep(100)
            val second = readCpuStat() ?: return null
            val totalDelta = second.total - first.total
            val idleDelta = second.idle - first.idle
            if (totalDelta <= 0L) null else ((totalDelta - idleDelta).toDouble() / totalDelta.toDouble()) * 100.0
        } catch (_: Exception) {
            null
        }
    }

    private fun getBackgroundAppCount(): Int? {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.runningAppProcesses
                ?.count { it.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND }
        } catch (_: Exception) {
            null
        }
    }

    private fun readCpuStat(): CpuSnapshot? {
        return try {
            val parts = java.io.File("/proc/stat")
                .useLines { lines -> lines.firstOrNull() }
                ?.trim()
                ?.split(Regex("\\s+"))
                ?: return null
            if (parts.size < 8 || parts[0] != "cpu") return null
            val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
            if (values.size < 7) return null
            val idle = values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L }
            CpuSnapshot(total = values.sum(), idle = idle)
        } catch (_: Exception) {
            null
        }
    }

    private data class CpuSnapshot(val total: Long, val idle: Long)
}
