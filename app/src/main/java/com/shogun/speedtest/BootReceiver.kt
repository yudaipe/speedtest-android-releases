package com.shogun.speedtest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shogun.speedtest.monitor.SpeedtestMonitorService
import com.shogun.speedtest.worker.WorkScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            android.util.Log.i("SpeedtestMonitor", "起動完了を受信したため監視を再開します")
            WorkScheduler.schedule(context)
            val serviceIntent = Intent(context, SpeedtestMonitorService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
