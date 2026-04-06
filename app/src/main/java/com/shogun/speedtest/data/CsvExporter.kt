package com.shogun.speedtest.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    fun export(context: Context, results: List<SpeedtestResult>): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "speedtest_export_${sdf.format(Date())}.csv"
        val csvContent = buildString {
            appendLine(header())
            results.forEach { appendLine(toCsvRow(it)) }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: throw IOException("Downloadsへのファイル作成に失敗しました")

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(csvContent.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("OutputStream取得に失敗しました")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)

            "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/$fileName"
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, fileName)
            file.writeText(csvContent, Charsets.UTF_8)
            file.absolutePath
        }
    }

    private fun header(): String = listOf(
        "id", "timestamp", "timestampIso", "downloadMbps", "uploadMbps",
        "pingMs", "jitterMs", "packetLoss", "isp", "serverName",
        "serverId", "serverCountry", "lat", "lon", "distanceKm",
        "resultUrl", "externalIp", "deviceModel", "isSynced", "wifiSsid",
        "connectionType", "rsrpDbm", "rsrqDb", "sinrDb", "rssiDbm",
        "pci", "tac", "earfcn", "bandNumber", "networkType",
        "carrierName", "apn", "isCarrierAggregation", "is_ca", "caBandwidthMhz",
        "caBandConfig", "nrState", "mcc", "mnc", "cqi",
        "timingAdvance", "visibleCellCount", "handoverCount", "endcAvailable",
        "dnsResolveMs", "ttfbMs", "tcpConnectMs", "rsrpStd", "rsrpVariance",
        "ramUsagePercent", "cpuUsagePercent", "bgAppCount", "sync_failed"
    ).joinToString(",")

    private fun toCsvRow(r: SpeedtestResult): String = listOf(
        r.id, r.timestamp, esc(r.timestampIso),
        r.downloadMbps, r.uploadMbps, r.pingMs, r.jitterMs, r.packetLoss,
        esc(r.isp), esc(r.serverName), r.serverId,
        esc(r.serverCountry), r.lat, r.lon, r.distanceKm,
        esc(r.resultUrl), esc(r.externalIp),
        esc(r.deviceModel), r.isSynced, esc(r.wifiSsid),
        esc(r.connectionType), r.rsrpDbm, r.rsrqDb, r.sinrDb, r.rssiDbm,
        r.pci, r.tac, r.earfcn, r.bandNumber, esc(r.networkType),
        esc(r.carrierName), esc(r.apn), r.isCarrierAggregation,
        esc(r.isCa), r.caBandwidthMhz, esc(r.caBandConfig),
        esc(r.nrState), esc(r.mcc), esc(r.mnc),
        r.cqi, r.timingAdvance, r.visibleCellCount, r.handoverCount,
        r.endcAvailable, r.dnsResolveMs, r.ttfbMs, r.tcpConnectMs,
        r.rsrpStd, r.rsrpVariance, r.ramUsagePercent, r.cpuUsagePercent, r.bgAppCount, r.syncFailed
    ).joinToString(",")

    private fun esc(value: Any?): String {
        if (value == null) return ""
        val s = value.toString()
        return if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            "\"${s.replace("\"", "\"\"")}\""
        } else s
    }
}
