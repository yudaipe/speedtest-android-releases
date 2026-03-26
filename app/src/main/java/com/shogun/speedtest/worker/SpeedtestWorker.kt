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
import com.shogun.speedtest.sheets.FormsClient
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

            // 5. Google Forms送信（未同期分まとめて）
            syncToForms(db)

            WorkScheduler.reschedule(applicationContext)
            Result.success()
        } catch (e: SpeedtestExecutionException) {
            Log.w(TAG, "CLI execution failed, will retry: ${e.message}")
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error, will retry: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun syncToForms(db: SpeedtestDatabase) {
        val settings = SettingsRepository(applicationContext)
        val formUrl = settings.getFormUrl()
        if (formUrl.isBlank()) {
            Log.w(TAG, "form_url not set, skipping Forms sync")
            return
        }

        try {
            val client = FormsClient(formUrl)
            val deviceId = DeviceIdentifier.getId(applicationContext)
            val deviceName = settings.getDeviceName()
            val appVersion = try {
                applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName ?: "unknown"
            } catch (e: Exception) { "unknown" }
            val wifiSsid = WifiSsidProvider.getSsid(applicationContext)

            val unsynced = db.speedtestDao().getUnsynced()
            for (result in unsynced) {
                val entryValues = mapOf(
                    settings.getEntryTimestamp() to result.timestampIso,
                    settings.getEntryDeviceId() to deviceId,
                    settings.getEntryDeviceName() to deviceName,
                    settings.getEntryDownload() to result.downloadMbps.toString(),
                    settings.getEntryUpload() to result.uploadMbps.toString(),
                    settings.getEntryPing() to result.pingMs.toString(),
                    settings.getEntryJitter() to result.jitterMs.toString(),
                    settings.getEntryIsp() to (result.isp ?: ""),
                    settings.getEntryServerName() to (result.serverName ?: ""),
                    settings.getEntryServerId() to result.serverId.toString(),
                    settings.getEntryLat() to (result.lat?.toString() ?: ""),
                    settings.getEntryLon() to (result.lon?.toString() ?: ""),
                    settings.getEntryPacketLoss() to result.packetLoss.toString(),
                    settings.getEntryServerCountry() to (result.serverCountry ?: ""),
                    settings.getEntryDistanceKm() to (result.distanceKm?.toString() ?: ""),
                    settings.getEntryResultUrl() to (result.resultUrl ?: ""),
                    settings.getEntryExternalIp() to (result.externalIp ?: ""),
                    settings.getEntrySoftwareVersion() to appVersion,
                    settings.getEntryWifiSsid() to wifiSsid
                ).filter { it.key.isNotBlank() }  // 未設定entryをスキップ

                if (client.postResult(entryValues)) {
                    db.speedtestDao().markAsSynced(result.id)
                    Log.i(TAG, "Synced result id=${result.id} to Forms")
                } else {
                    Log.w(TAG, "Failed to sync result id=${result.id}, will retry next time")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncToForms error: ${e.message}")
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
