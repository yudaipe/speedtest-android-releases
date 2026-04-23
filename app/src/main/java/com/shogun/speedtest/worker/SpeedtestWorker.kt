package com.shogun.speedtest.worker

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
import com.shogun.speedtest.supabase.SupabaseFailureKind
import com.shogun.speedtest.supabase.SupabasePostResult
import com.shogun.speedtest.WifiSsidProvider
import com.shogun.speedtest.collector.HiddenRadioCollector
import com.shogun.speedtest.shizuku.ShizukuAccessState
import com.shogun.speedtest.shizuku.ShizukuManager
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
        private const val MAX_SUPABASE_RETRY_ATTEMPTS = 3
        const val CHANNEL_ID = "speedtest_channel"
        const val NOTIFICATION_ID = 1001
        const val ERROR_CHANNEL_ID = "speedtest_error_channel"
        private const val ERROR_NOTIFICATION_ID = 1002
        private const val SYNC_RETRY_NOTIFICATION_ID = 1003
        private const val RETRY_INTERVAL_MINUTES = 30
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val db = SpeedtestDatabase.getInstance(applicationContext)
        recordWorkerLog(db, eventType = "start")

        // ネットワーク不通チェック（ConnectivityManager）
        if (!isNetworkAvailable()) {
            Log.w(TAG, "Network unavailable: skipping speedtest")
            showErrorNotification("no_network")
            recordWorkerLog(db, eventType = "complete", result = "fail", durationMs = System.currentTimeMillis() - startedAt)
            return@withContext Result.failure()
        }

        try {
            // ForegroundService昇格（Doze中でも実行保証）
            setForeground(getForegroundInfo())

            // C-2: sync-onlyモード — 未同期レコードが残っていれば計測をスキップしてsyncだけ実施
            val unsyncedAtStart = db.speedtestDao().getUnsynced()
            if (unsyncedAtStart.isNotEmpty()) {
                Log.i(TAG, "sync-only mode: ${unsyncedAtStart.size} unsynced records found, skipping measurement")
                syncToSupabase(db)
                syncWorkerLogsToSupabase(db)
                recordWorkerLog(db, eventType = "complete", result = "sync_only", durationMs = System.currentTimeMillis() - startedAt)
                return@withContext Result.success()
            }

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
            val deviceModel = Build.MODEL

            val hiddenSnapshot = if (ShizukuManager(applicationContext).state.value == ShizukuAccessState.Granted) {
                try { HiddenRadioCollector(applicationContext).collect() } catch (e: Exception) { null }
            } else null

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
                deviceModel = deviceModel,
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
                apn = cellularInfo.apn,
                isCarrierAggregation = cellularInfo.isCarrierAggregation,
                isCa = cellularInfo.isCa,
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
                rsrpStd = cellularInfo.rsrpStd,
                rsrpVariance = cellularInfo.rsrpVariance,
                ramUsagePercent = deviceMetrics.ramUsagePercent,
                cpuUsagePercent = deviceMetrics.cpuUsagePercent,
                bgAppCount = deviceMetrics.bgAppCount,
                hiddenRadioCcCount = hiddenSnapshot?.ccCount,
                hiddenRadioConfigs = hiddenSnapshot?.let { Gson().toJson(it.componentCarriers) },
                hiddenRadioCollectedAt = hiddenSnapshot?.timestamp,
            )
            db.speedtestDao().insert(entity)

            // 5. Supabase送信（未同期分まとめて）
            val syncOutcome = syncToSupabase(db)
            recordWorkerLog(
                db = db,
                eventType = "complete",
                result = "success",
                durationMs = System.currentTimeMillis() - startedAt
            )
            syncWorkerLogsToSupabase(db)

            // C-2: sync失敗でもResult.retry()は使わない。次回の定期実行で再送する。
            if (syncOutcome.shouldRetry) {
                val unsyncedCount = db.speedtestDao().getUnsynced().size
                Log.w(TAG, "Transient Supabase error, will retry on next scheduled run: ${syncOutcome.logMessage}")
                if (unsyncedCount > 0) {
                    showSyncRetryNotification(unsyncedCount)
                }
            }
            Result.success()
        } catch (e: SpeedtestExecutionException) {
            val durationMs = System.currentTimeMillis() - startedAt
            val errorReason = classifyErrorReason(e)
            recordWorkerLog(db, eventType = "complete", result = "fail", durationMs = durationMs)
            recordWorkerLog(db, eventType = "fail", errorReason = errorReason, durationMs = durationMs)
            syncWorkerLogsToSupabase(db)
            showErrorNotification(errorReason)
            if (canRetryWorker()) {
                Log.w(TAG, "CLI execution failed, will retry: ${e.message}")
                Result.retry()
            } else {
                Log.e(TAG, "CLI execution retry limit reached: ${e.message}")
                Result.failure()
            }
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startedAt
            val errorReason = classifyErrorReason(e)
            recordWorkerLog(db, eventType = "complete", result = "fail", durationMs = durationMs)
            recordWorkerLog(db, eventType = "fail", errorReason = errorReason, durationMs = durationMs)
            syncWorkerLogsToSupabase(db)
            showErrorNotification(errorReason)
            if (canRetryWorker()) {
                Log.e(TAG, "Unexpected error, will retry: ${e.message}")
                Result.retry()
            } else {
                Log.e(TAG, "Unexpected error retry limit reached: ${e.message}")
                Result.failure()
            }
        }
    }

    private suspend fun syncToSupabase(db: SpeedtestDatabase): SyncOutcome {
        val settings = SettingsRepository(applicationContext)
        val deviceId = DeviceIdentifier.getId(applicationContext)
        val deviceName = settings.getDeviceName()
        val appVersion = try {
            applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) { "unknown" }
        try {
            val client = SupabaseClient(
                supabaseUrl = settings.getSupabaseUrl(),
                anonKey = settings.getSupabaseAnonKey()
            )
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
                    "device_model" to result.deviceModel,
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
                    "apn" to result.apn,
                    "is_carrier_aggregation" to result.isCarrierAggregation,
                    "is_ca" to result.isCa,
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
                    "rsrp_std" to result.rsrpStd,
                    "ram_usage_percent" to result.ramUsagePercent,
                    "cpu_usage_percent" to result.cpuUsagePercent,
                    "bg_app_count" to result.bgAppCount,
                    "hidden_radio_cc_count" to result.hiddenRadioCcCount,
                    "hidden_radio_configs" to result.hiddenRadioConfigs,
                    "hidden_radio_collected_at" to result.hiddenRadioCollectedAt?.let {
                        java.time.Instant.ofEpochMilli(it)
                            .atOffset(java.time.ZoneOffset.UTC)
                            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    }
                )

                when (val postResult = client.postResult(payload)) {
                    SupabasePostResult.Success -> {
                        db.speedtestDao().markAsSynced(result.id)
                        Log.i(TAG, "Synced result id=${result.id} to Supabase")
                    }
                    is SupabasePostResult.Failure -> {
                        val detail = formatSupabaseFailure(postResult)
                        if (postResult.kind == SupabaseFailureKind.TRANSIENT) {
                            Log.w(TAG, "Transient Supabase failure for result id=${result.id}: $detail")
                            return SyncOutcome(shouldRetry = true, logMessage = detail)
                        } else {
                            // C-1: FATALエラーは該当レコードをsyncFailed=trueにしてスキップ（永久ブロック防止）
                            Log.e(TAG, "Fatal Supabase failure for result id=${result.id}, marking as sync_failed: $detail")
                            db.speedtestDao().markAsSyncFailed(result.id)
                        }
                    }
                }
            }
            return SyncOutcome()
        } catch (e: Exception) {
            Log.e(TAG, "syncToSupabase error: ${e.message}")
            return SyncOutcome(shouldRetry = true, logMessage = e.message)
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
            val settings = SettingsRepository(applicationContext)
            val client = SupabaseClient(
                supabaseUrl = settings.getSupabaseUrl(),
                anonKey = settings.getSupabaseAnonKey()
            )
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
                when (client.postDiagnostic(payload)) {
                    SupabasePostResult.Success -> db.workerLogDao().markAsSynced(log.id)
                    is SupabasePostResult.Failure -> Unit
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncWorkerLogsToSupabase error: ${e.message}")
        }
    }

    private fun canRetryWorker(): Boolean = runAttemptCount < MAX_SUPABASE_RETRY_ATTEMPTS

    private fun formatSupabaseFailure(failure: SupabasePostResult.Failure): String {
        val code = failure.httpCode?.toString() ?: "n/a"
        val message = failure.message ?: "unknown"
        return "kind=${failure.kind} httpCode=$code message=$message"
    }

    private data class SyncOutcome(
        val shouldRetry: Boolean = false,
        val logMessage: String? = null
    )

    private fun classifyErrorReason(error: Throwable): String {
        val message = error.message?.lowercase().orEmpty()
        return when {
            error is SocketTimeoutException || message.contains("timeout after") || message.contains("timeout") -> "timeout"
            message.contains("dns") || message.contains("no such host") || message.contains("unable to resolve") -> "dns_failure"
            message.contains("connection refused") || message.contains("econnrefused") -> "connection_refused"
            error is IOException ||
                message.contains("network") ||
                message.contains("connection") -> "network_error"
            error is SpeedtestExecutionException -> "cli_error"
            message.isNotBlank() -> "exception"
            else -> "unknown"
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showErrorNotification(errorKind: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureErrorChannel(manager)
        val (title, text) = when (errorKind) {
            "no_network" -> "ネットワーク未接続" to "Wi-Fiまたはモバイルデータが無効です"
            "timeout" -> "計測タイムアウト" to "サーバー応答待ちで120秒超過しました"
            "dns_failure" -> "DNS解決失敗" to "ホスト名の解決に失敗しました。ネットワーク設定を確認してください"
            "connection_refused" -> "接続拒否" to "speedtestサーバーへの接続が拒否されました"
            "network_error" -> "ネットワークエラー" to "ネットワーク接続を確認してください"
            "cli_error" -> "計測失敗" to "speedtest CLIの実行に失敗しました"
            else -> "計測失敗" to "予期しないエラーが発生しました"
        }
        val notification = NotificationCompat.Builder(applicationContext, ERROR_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_speed)
            .setAutoCancel(true)
            .build()
        manager.notify(ERROR_NOTIFICATION_ID, notification)
    }

    private fun showSyncRetryNotification(pendingCount: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureErrorChannel(manager)
        val text = "未送信${pendingCount}件、次回リトライ: 約${RETRY_INTERVAL_MINUTES}分後"
        val notification = NotificationCompat.Builder(applicationContext, ERROR_CHANNEL_ID)
            .setContentTitle("Supabase送信待ち")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_speed)
            .setAutoCancel(true)
            .build()
        manager.notify(SYNC_RETRY_NOTIFICATION_ID, notification)
    }

    private fun ensureErrorChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(ERROR_CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    ERROR_CHANNEL_ID,
                    "Speedtest エラー通知",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "速度計測エラーの通知チャネル" }
            )
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
