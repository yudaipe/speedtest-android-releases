package com.shogun.speedtest.worker

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.shogun.speedtest.R
import com.shogun.speedtest.cli.CliResult
import com.shogun.speedtest.cli.SpeedtestExecutor
import com.shogun.speedtest.cli.SpeedtestExecutionException
import com.shogun.speedtest.data.SpeedtestDatabase
import com.shogun.speedtest.data.SpeedtestResult
import com.shogun.speedtest.data.WorkerLog
import com.shogun.speedtest.device.DeviceIdentifier
import com.shogun.speedtest.location.GpsLocationProvider
import com.shogun.speedtest.network.CellularInfoCollector
import com.shogun.speedtest.network.DeviceMetricsCollector
import com.shogun.speedtest.network.NetworkQualityProbe
import com.shogun.speedtest.network.NetworkType
import com.shogun.speedtest.network.NetworkTypeDetector
import com.shogun.speedtest.settings.SettingsRepository
import com.shogun.speedtest.supabase.SupabaseClient
import com.shogun.speedtest.WifiSsidProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SpeedtestWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SpeedtestWorker"
        const val CHANNEL_ID = "speedtest_channel"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val db = SpeedtestDatabase.getInstance(applicationContext)
        recordWorkerLog(db, eventType = "start")

        try {
            // ForegroundService昇格（Doze中でも実行保証）
            setForeground(getForegroundInfo())

            val networkType = NetworkTypeDetector(applicationContext).detect()
            val cellularCollector = CellularInfoCollector(applicationContext)
            withContext(Dispatchers.Main) {
                cellularCollector.startTracking()
            }
            val networkProbe = NetworkQualityProbe()
            val networkQuality = networkProbe.collect()

            // 1. GPS座標を取得（計測前に実施）
            val gpsProvider = GpsLocationProvider(applicationContext)
            val gpsLocation = gpsProvider.getCurrentLocation()

            // 2. speedtest-go CLI実行
            val settings = SettingsRepository(applicationContext)
            val executor = SpeedtestExecutor(applicationContext)
            val rawResult = executor.runSpeedtest(serverId = settings.getServerId())

            // 3. JSONパース（speedtest-go は単一JSONオブジェクトを出力）
            val gson = Gson()
            val cliResult = rawResult.trim().takeIf { it.isNotBlank() }
                ?.let { gson.fromJson(it, CliResult::class.java) }
                ?: throw SpeedtestExecutionException("No valid JSON output")

            Log.i(TAG, "speedtest: down=${cliResult.downloadMbps}Mbps up=${cliResult.uploadMbps}Mbps ping=${cliResult.pingMs}ms")

            // 4. Room DB保存
            val jstFormatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.of("Asia/Tokyo"))
            val timestampJst = jstFormatter.format(Instant.now())
            val wifiSsid = if (networkType == NetworkType.WIFI) {
                WifiSsidProvider.getSsid(applicationContext).ifBlank { null }
            } else {
                null
            }
            val cellularInfo = cellularCollector.stopAndCollect()
            val deviceMetrics = DeviceMetricsCollector(applicationContext).collect()

            val entity = SpeedtestResult(
                timestamp = System.currentTimeMillis() / 1000,
                timestampIso = timestampJst,
                downloadMbps = cliResult.downloadMbps,
                uploadMbps = cliResult.uploadMbps,
                pingMs = cliResult.pingMs,
                jitterMs = cliResult.jitterMs,
                packetLoss = cliResult.packetLoss ?: 0.0,
                isp = cliResult.isp,
                serverName = cliResult.serverName,
                serverId = cliResult.serverId,
                serverCountry = cliResult.serverCountry,
                lat = gpsLocation?.lat ?: cliResult.lat,
                lon = gpsLocation?.lon ?: cliResult.lon,
                distanceKm = cliResult.distanceKm,
                resultUrl = cliResult.resultUrl,
                externalIp = cliResult.externalIp,
                isSynced = false,
                wifiSsid = wifiSsid,
                connectionType = networkType.wireValue,
                rsrpDbm = cellularInfo.rsrpDbm,
                rsrqDb = cellularInfo.rsrqDb,
                sinrDb = cellularInfo.sinrDb,
                rssiDbm = cellularInfo.rssiDbm,
                pci = cellularInfo.pci,
                tac = cellularInfo.tac,
                earfcn = cellularInfo.earfcn,
                bandNumber = cellularInfo.bandNumber,
                networkType = cellularInfo.networkType,
                carrierName = cellularInfo.carrierName,
                isCarrierAggregation = cellularInfo.isCarrierAggregation,
                caBandwidthMhz = cellularInfo.caBandwidthMhz,
                caBandConfig = cellularInfo.caBandConfig,
                nrState = cellularInfo.nrState,
                mcc = cellularInfo.mcc,
                mnc = cellularInfo.mnc,
                cqi = cellularInfo.cqi,
                timingAdvance = cellularInfo.timingAdvance,
                visibleCellCount = cellularInfo.visibleCellCount,
                handoverCount = cellularInfo.handoverCount,
                endcAvailable = cellularInfo.endcAvailable,
                dnsResolveMs = networkQuality.dnsResolveMs,
                ttfbMs = networkQuality.ttfbMs,
                tcpConnectMs = networkQuality.tcpConnectMs,
                rsrpVariance = cellularInfo.rsrpVariance,
                ramUsagePercent = deviceMetrics.ramUsagePercent,
                cpuUsagePercent = deviceMetrics.cpuUsagePercent,
                bgAppCount = deviceMetrics.bgAppCount
            )
            db.speedtestDao().insert(entity)

            // 5. Supabase送信（未同期分まとめて）
            syncToSupabase(db)
            recordWorkerLog(
                db = db,
                eventType = "complete",
                result = "success",
                durationMs = System.currentTimeMillis() - startedAt
            )
            syncWorkerLogsToSupabase(db)

            Result.success()
        } catch (e: SpeedtestExecutionException) {
            val durationMs = System.currentTimeMillis() - startedAt
            val errorReason = classifyErrorReason(e)
            recordWorkerLog(db, eventType = "complete", result = "fail", durationMs = durationMs)
            recordWorkerLog(db, eventType = "fail", errorReason = errorReason, durationMs = durationMs)
            syncWorkerLogsToSupabase(db)
            Log.w(TAG, "CLI execution failed, will retry: ${e.message}")
            Result.retry()
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startedAt
            val errorReason = classifyErrorReason(e)
            recordWorkerLog(db, eventType = "complete", result = "fail", durationMs = durationMs)
            recordWorkerLog(db, eventType = "fail", errorReason = errorReason, durationMs = durationMs)
            syncWorkerLogsToSupabase(db)
            Log.e(TAG, "Unexpected error, will retry: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun syncToSupabase(db: SpeedtestDatabase) {
        val settings = SettingsRepository(applicationContext)
        val deviceId = DeviceIdentifier.getId(applicationContext)
        val deviceName = settings.getDeviceName()
        val appVersion = try {
            applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) { "unknown" }
        try {
            val client = SupabaseClient()
            val unsynced = db.speedtestDao().getUnsynced()
            for (result in unsynced) {
                val payload: Map<String, Any?> = mapOf(
                    "timestamp" to result.timestampIso,
                    "device_id" to deviceId,
                    "device_name" to deviceName,
                    "app_type" to "android",
                    "download_mbps" to result.downloadMbps,
                    "upload_mbps" to result.uploadMbps,
                    "ping_ms" to result.pingMs,
                    "jitter_ms" to result.jitterMs,
                    "packet_loss" to result.packetLoss,
                    "isp" to result.isp,
                    "server_name" to result.serverName,
                    "server_id" to result.serverId,
                    "server_country" to result.serverCountry,
                    "lat" to result.lat,
                    "lon" to result.lon,
                    "distance_km" to result.distanceKm,
                    "result_url" to result.resultUrl,
                    "external_ip" to result.externalIp,
                    "software_version" to appVersion,
                    "wifi_ssid" to result.wifiSsid,
                    "connection_type" to result.connectionType,
                    "rsrp_dbm" to result.rsrpDbm,
                    "rsrq_db" to result.rsrqDb,
                    "sinr_db" to result.sinrDb,
                    "rssi_dbm" to result.rssiDbm,
                    "pci" to result.pci,
                    "tac" to result.tac,
                    "earfcn" to result.earfcn,
                    "band_number" to result.bandNumber,
                    "network_type" to result.networkType,
                    "carrier_name" to result.carrierName,
                    "is_carrier_aggregation" to result.isCarrierAggregation,
                    "ca_bandwidth_mhz" to result.caBandwidthMhz,
                    "ca_band_config" to result.caBandConfig,
                    "nr_state" to result.nrState,
                    "mcc" to result.mcc,
                    "mnc" to result.mnc,
                    "cqi" to result.cqi,
                    "timing_advance" to result.timingAdvance,
                    "visible_cell_count" to result.visibleCellCount,
                    "handover_count" to result.handoverCount,
                    "endc_available" to result.endcAvailable,
                    "dns_resolve_ms" to result.dnsResolveMs,
                    "ttfb_ms" to result.ttfbMs,
                    "tcp_connect_ms" to result.tcpConnectMs,
                    "rsrp_variance" to result.rsrpVariance,
                    "ram_usage_percent" to result.ramUsagePercent,
                    "cpu_usage_percent" to result.cpuUsagePercent,
                    "bg_app_count" to result.bgAppCount
                )

                if (client.postResult(payload)) {
                    db.speedtestDao().markAsSynced(result.id)
                    Log.i(TAG, "Synced result id=${result.id} to Supabase")
                } else {
                    Log.w(TAG, "Failed to sync result id=${result.id} to Supabase, will retry next time")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncToSupabase error: ${e.message}")
        }
    }

    private suspend fun recordWorkerLog(
        db: SpeedtestDatabase,
        eventType: String,
        result: String? = null,
        errorReason: String? = null,
        durationMs: Long? = null
    ) {
        val timestamp = Instant.now().toString()

        db.workerLogDao().insert(
            WorkerLog(
                timestamp = timestamp,
                eventType = eventType,
                result = result,
                errorReason = errorReason,
                durationMs = durationMs
            )
        )
    }

    private suspend fun syncWorkerLogsToSupabase(db: SpeedtestDatabase) {
        try {
            val client = SupabaseClient()
            val unsynced = db.workerLogDao().getUnsynced()
            for (log in unsynced) {
                val payload = mapOf(
                    "timestamp" to log.timestamp,
                    "event_type" to log.eventType,
                    "result" to log.result,
                    "error_reason" to log.errorReason,
                    "duration_ms" to log.durationMs,
                    "app_type" to "android"
                )
                if (client.postDiagnostic(payload)) {
                    db.workerLogDao().markAsSynced(log.id)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncWorkerLogsToSupabase error: ${e.message}")
        }
    }

    private fun classifyErrorReason(error: Throwable): String {
        val message = error.message?.lowercase().orEmpty()
        return when {
            error is SocketTimeoutException || message.contains("timeout") -> "timeout"
            error is IOException ||
                message.contains("network") ||
                message.contains("connection") ||
                message.contains("dns") -> "network_error"
            error is SpeedtestExecutionException -> "exception"
            message.isNotBlank() -> "exception"
            else -> "unknown"
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(
            applicationContext, CHANNEL_ID
        )
            .setContentTitle("速度計測中")
            .setContentText("バックグラウンドで速度計測を実行中")
            .setSmallIcon(R.drawable.ic_speed)
            .setOngoing(true)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}
