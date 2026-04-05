package com.shogun.speedtest.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val WORK_NAME = "speedtest_periodic"

    /**
     * 30分毎（毎時00分/30分）に計測を実行するスケジュールを設定
     */
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val initialDelay = calculateInitialDelay()

        val workRequest = PeriodicWorkRequestBuilder<SpeedtestWorker>(
            30, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES  // flex: ±5分の誤差許容
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * 次の毎時00分 or 30分までのミリ秒を計算
     */
    fun calculateInitialDelay(): Long {
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.MINUTE)
        val second = now.get(Calendar.SECOND)

        val nextSlot = if (minute < 30) 30 else 60
        val delayMinutes = nextSlot - minute
        val delayMs = (delayMinutes * 60 - second) * 1000L

        return if (delayMs <= 0) 0 else delayMs
    }

    fun cancelWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * 計測完了後に呼び出し、次の:00/:30に再アライン
     * PeriodicWorkを一度キャンセルしてREPLACEで再登録
     */
    fun reschedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val initialDelay = calculateInitialDelay()

        val workRequest = PeriodicWorkRequestBuilder<SpeedtestWorker>(
            30, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }
}
