package com.shogun.speedtest.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shogun.speedtest.data.CsvExporter
import com.shogun.speedtest.data.SpeedtestDatabase
import com.shogun.speedtest.data.SpeedtestResult
import com.shogun.speedtest.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val deviceName: String = "",
    val deviceId: String = "",
    val lastResult: SpeedtestResult? = null,
    val unsyncedCount: Int = 0,
    val isExporting: Boolean = false,
    val exportResult: String? = null,
    val requestLegacyStoragePermission: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)
    private val db = SpeedtestDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = SettingsUiState(
            deviceName = settings.getDeviceName(),
            deviceId = settings.getDeviceId()
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
