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
import com.shogun.speedtest.device.DeviceIdentifier
import com.shogun.speedtest.location.GpsLocationProvider
import com.shogun.speedtest.settings.SettingsRepository
import com.shogun.speedtest.supabase.SupabaseClient
import com.shogun.speedtest.WifiSsidProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        try {
            // ForegroundService昇格（Doze中でも実行保証）
            setForeground(getForegroundInfo())

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

            val db = SpeedtestDatabase.getInstance(applicationContext)
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
                isSynced = false
            )
            db.speedtestDao().insert(entity)

            // 5. Supabase送信（未同期分まとめて）
            syncToSupabase(db)

            Result.success()
        } catch (e: SpeedtestExecutionException) {
            Log.w(TAG, "CLI execution failed, will retry: ${e.message}")
            Result.retry()
        } catch (e: Exception) {
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
        val wifiSsid = WifiSsidProvider.getSsid(applicationContext)

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
                    "wifi_ssid" to wifiSsid
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
