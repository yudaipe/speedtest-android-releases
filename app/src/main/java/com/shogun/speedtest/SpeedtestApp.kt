package com.shogun.speedtest

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.work.Configuration
import com.shogun.speedtest.monitor.SpeedtestMonitorService
import com.shogun.speedtest.worker.SpeedtestWorker
import com.shogun.speedtest.worker.WorkScheduler

class SpeedtestApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        WorkScheduler.schedule(this)
        startForegroundService(Intent(this, SpeedtestMonitorService::class.java))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    SpeedtestWorker.CHANNEL_ID,
                    "Speedtest Monitor",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "バックグラウンド速度計測の通知チャネル"
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    SpeedtestWorker.ERROR_CHANNEL_ID,
                    "Speedtest エラー通知",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "速度計測エラーの通知チャネル"
                }
            )
        }
    }
}
