package com.shogun.speedtest

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.shogun.speedtest.data.SpeedtestDatabase
import com.shogun.speedtest.data.SpeedtestResult
import com.shogun.speedtest.settings.SettingsRepository
import com.shogun.speedtest.update.UpdateChecker
import com.shogun.speedtest.update.UpdateInfo
import com.shogun.speedtest.worker.SpeedtestWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = SpeedtestDatabase.getInstance(application).speedtestDao()
    private val settingsRepo = SettingsRepository(application)

    val isMeasuring = MutableStateFlow(false)
    val latestResult = MutableStateFlow<SpeedtestResult?>(null)
    val history = MutableStateFlow<List<SpeedtestResult>>(emptyList())
    val realtimePoints = MutableStateFlow<List<Float>>(emptyList())
    val gaugeValue = MutableStateFlow(0f)
    val errorMessage = MutableStateFlow<String?>(null)

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    val deviceName: String get() = settingsRepo.getDeviceName()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { dao.getRecent(5) }
            history.value = results
            latestResult.value = results.firstOrNull()
            results.firstOrNull()?.let { gaugeValue.value = it.downloadMbps.toFloat() }
        }
    }

    fun checkForUpdate(context: Context) {
        viewModelScope.launch {
            val checker = UpdateChecker(context)
            val info = checker.checkForUpdate() ?: return@launch
            if (info.versionCode > checker.getCurrentVersionCode()) {
                _updateInfo.value = info
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }

    fun startMeasurement(context: Context) {
        if (isMeasuring.value) return
        isMeasuring.value = true
        realtimePoints.value = emptyList()
        gaugeValue.value = 0f
        errorMessage.value = null

        // Enqueue WorkManager
        val workRequest = OneTimeWorkRequestBuilder<SpeedtestWorker>()
            .addTag("speedtest_one_shot")
            .build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(workRequest)

        // Observe WorkManager completion
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workRequest.id).collect { info ->
                if (info?.state?.isFinished == true) {
                    isMeasuring.value = false
                    val results = withContext(Dispatchers.IO) { dao.getRecent(5) }
                    history.value = results
                    latestResult.value = results.firstOrNull()
                    if (results.isNotEmpty()) {
                        gaugeValue.value = results.first().downloadMbps.toFloat()
                        val finalPts = realtimePoints.value.toMutableList()
                        finalPts.add(results.first().downloadMbps.toFloat())
                        realtimePoints.value = finalPts
                    }
                    if (info.state == androidx.work.WorkInfo.State.FAILED) {
                        errorMessage.value = "計測失敗。再試行してください。"
                    }
                }
            }
        }
    }
}
