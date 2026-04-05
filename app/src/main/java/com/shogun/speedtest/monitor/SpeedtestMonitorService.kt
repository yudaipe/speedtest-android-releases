package com.shogun.speedtest.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.shogun.speedtest.worker.WorkScheduler

class SpeedtestMonitorService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        WorkScheduler.schedule(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        WorkScheduler.schedule(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        ensureChannel()
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("speedtest監視中")
            .setContentText("バックグラウンドで速度計測を待機しています")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "speedtest監視",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "speedtest の自動計測を継続する通知"
                    }
                )
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "speedtest_monitor_service"
        private const val NOTIFICATION_ID = 1201
    }
}
