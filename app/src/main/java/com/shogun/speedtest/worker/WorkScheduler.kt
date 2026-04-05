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
    private const val HEADLESS_SUFFIX = ".headless"
    private const val HALF_HOUR_MINUTES = 30
    private const val PERIODIC_INTERVAL_MINUTES = 30L
    private const val HEADLESS_OFFSET_MINUTES = 15

    /**
     * 30分毎（毎時00分/30分）に計測を実行するスケジュールを設定
     */
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val initialDelay = calculateInitialDelay(context)

        val workRequest = PeriodicWorkRequestBuilder<SpeedtestWorker>(
            PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES,
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
     * アプリ種別から実行位相を自動判定し、次のスロットまでの待ち時間を計算する
     */
    fun calculateInitialDelay(context: Context): Long {
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.MINUTE)
        val second = now.get(Calendar.SECOND)
        val offsetMinutes = getExecutionOffsetMinutes(context)
        val nextSlot = if (minute < offsetMinutes) {
            offsetMinutes.toLong()
        } else if (minute < offsetMinutes + HALF_HOUR_MINUTES) {
            (offsetMinutes + HALF_HOUR_MINUTES).toLong()
        } else {
            (60 + offsetMinutes).toLong()
        }

        val delayMinutes = nextSlot - minute.toLong()
        val delayMs = (delayMinutes * 60 - second) * 1000L

        return if (delayMs <= 0) 0 else delayMs
    }

    private fun getExecutionOffsetMinutes(context: Context): Int =
        if (context.packageName.endsWith(HEADLESS_SUFFIX)) HEADLESS_OFFSET_MINUTES else 0

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

        val initialDelay = calculateInitialDelay(context)

        val workRequest = PeriodicWorkRequestBuilder<SpeedtestWorker>(
            PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES,
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
