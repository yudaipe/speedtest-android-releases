package com.shogun.speedtest.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shogun.speedtest.data.CsvExporter
import com.shogun.speedtest.data.SpeedtestDatabase
import com.shogun.speedtest.data.SpeedtestResult
import com.shogun.speedtest.debug.HiddenRadioDebugLog
import com.shogun.speedtest.settings.DebugLogPreferences
import com.shogun.speedtest.settings.SettingsRepository
import com.shogun.speedtest.shizuku.ShizukuAccessState
import com.shogun.speedtest.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsUiState(
    val deviceName: String = "",
    val deviceId: String = "",
    val lastResult: SpeedtestResult? = null,
    val unsyncedCount: Int = 0,
    val isExporting: Boolean = false,
    val exportResult: String? = null,
    val requestLegacyStoragePermission: Boolean = false,
    val debugLogEnabled: Boolean = false,
    val pendingShareUri: Uri? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)
    private val db = SpeedtestDatabase.getInstance(application)
    private val shizukuManager = ShizukuManager(application)
    private val debugLogPrefs = DebugLogPreferences(application)

    val shizukuState: StateFlow<ShizukuAccessState> = shizukuManager.state

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val debugLogEnabled = debugLogPrefs.isDebugLogEnabled()
        HiddenRadioDebugLog.isEnabled = debugLogEnabled
        _uiState.value = SettingsUiState(
            deviceName = settings.getDeviceName(),
            deviceId = settings.getDeviceId(),
            debugLogEnabled = debugLogEnabled,
        )
        refreshDbState()
    }

    fun setDeviceName(value: String) {
        settings.setDeviceName(value)
        _uiState.value = _uiState.value.copy(deviceName = value)
    }

    fun exportCsv() {
        if (CsvExporter.requiresLegacyWritePermission(getApplication())) {
            _uiState.value = _uiState.value.copy(
                isExporting = false,
                exportResult = null,
                requestLegacyStoragePermission = true
            )
            return
        }
        performExport()
    }

    fun consumeLegacyStoragePermissionRequest() {
        _uiState.value = _uiState.value.copy(requestLegacyStoragePermission = false)
    }

    fun onLegacyStoragePermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(requestLegacyStoragePermission = false)
        if (granted) {
            performExport()
            return
        }
        _uiState.value = _uiState.value.copy(
            isExporting = false,
            exportResult = "エラー: Android 8-9ではストレージ権限が必要です"
        )
    }

    private fun performExport() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportResult = null)
            try {
                val all = withContext(Dispatchers.IO) { db.speedtestDao().getAll() }
                val path = withContext(Dispatchers.IO) { CsvExporter.export(getApplication(), all) }
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = "完了: $path (${all.size}件)"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportResult = "エラー: ${e.message}"
                )
            }
        }
    }

    fun clearExportResult() {
        _uiState.value = _uiState.value.copy(exportResult = null)
    }

    fun setDebugLogEnabled(enabled: Boolean) {
        debugLogPrefs.setDebugLogEnabled(enabled)
        HiddenRadioDebugLog.isEnabled = enabled
        _uiState.value = _uiState.value.copy(debugLogEnabled = enabled)
    }

    fun exportDebugLog() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "speedtest_debug_$timestamp.txt"
            val text = HiddenRadioDebugLog.exportText()
            val file = File(context.cacheDir, fileName)
            withContext(Dispatchers.IO) { file.writeText(text) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            _uiState.value = _uiState.value.copy(pendingShareUri = uri)
        }
    }

    fun clearPendingShareUri() {
        _uiState.value = _uiState.value.copy(pendingShareUri = null)
    }

    override fun onCleared() {
        super.onCleared()
        shizukuManager.dispose()
    }

    private fun refreshDbState() {
        viewModelScope.launch {
            val recent = db.speedtestDao().getRecent(1)
            val unsynced = db.speedtestDao().getUnsynced()
            _uiState.value = _uiState.value.copy(
                lastResult = recent.firstOrNull(),
                unsyncedCount = unsynced.size
            )
        }
    }
}
