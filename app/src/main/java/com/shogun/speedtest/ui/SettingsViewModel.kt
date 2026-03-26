package com.shogun.speedtest.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shogun.speedtest.data.SpeedtestDatabase
import com.shogun.speedtest.data.SpeedtestResult
import com.shogun.speedtest.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val formUrl: String = "",
    val entryTimestamp: String = "",
    val entryDeviceId: String = "",
    val entryDeviceName: String = "",
    val entryDownload: String = "",
    val entryUpload: String = "",
    val entryPing: String = "",
    val entryJitter: String = "",
    val entryIsp: String = "",
    val entryServerName: String = "",
    val entryServerId: String = "",
    val deviceName: String = "",
    val deviceId: String = "",
    val lastResult: SpeedtestResult? = null,
    val unsyncedCount: Int = 0
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)
    private val db = SpeedtestDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = SettingsUiState(
            formUrl = settings.getFormUrl(),
            entryTimestamp = settings.getEntryTimestamp(),
            entryDeviceId = settings.getEntryDeviceId(),
            entryDeviceName = settings.getEntryDeviceName(),
            entryDownload = settings.getEntryDownload(),
            entryUpload = settings.getEntryUpload(),
            entryPing = settings.getEntryPing(),
            entryJitter = settings.getEntryJitter(),
            entryIsp = settings.getEntryIsp(),
            entryServerName = settings.getEntryServerName(),
            entryServerId = settings.getEntryServerId(),
            deviceName = settings.getDeviceName(),
            deviceId = settings.getDeviceId()
        )
        refreshDbState()
    }

    fun setFormUrl(value: String) {
        settings.setFormUrl(value)
        _uiState.value = _uiState.value.copy(formUrl = value)
    }

    fun setEntryTimestamp(value: String) {
        settings.setEntryTimestamp(value)
        _uiState.value = _uiState.value.copy(entryTimestamp = value)
    }

    fun setEntryDeviceId(value: String) {
        settings.setEntryDeviceId(value)
        _uiState.value = _uiState.value.copy(entryDeviceId = value)
    }

    fun setEntryDeviceName(value: String) {
        settings.setEntryDeviceName(value)
        _uiState.value = _uiState.value.copy(entryDeviceName = value)
    }

    fun setEntryDownload(value: String) {
        settings.setEntryDownload(value)
        _uiState.value = _uiState.value.copy(entryDownload = value)
    }

    fun setEntryUpload(value: String) {
        settings.setEntryUpload(value)
        _uiState.value = _uiState.value.copy(entryUpload = value)
    }

    fun setEntryPing(value: String) {
        settings.setEntryPing(value)
        _uiState.value = _uiState.value.copy(entryPing = value)
    }

    fun setEntryJitter(value: String) {
        settings.setEntryJitter(value)
        _uiState.value = _uiState.value.copy(entryJitter = value)
    }

    fun setEntryIsp(value: String) {
        settings.setEntryIsp(value)
        _uiState.value = _uiState.value.copy(entryIsp = value)
    }

    fun setEntryServerName(value: String) {
        settings.setEntryServerName(value)
        _uiState.value = _uiState.value.copy(entryServerName = value)
    }

    fun setEntryServerId(value: String) {
        settings.setEntryServerId(value)
        _uiState.value = _uiState.value.copy(entryServerId = value)
    }

    fun setDeviceName(value: String) {
        settings.setDeviceName(value)
        _uiState.value = _uiState.value.copy(deviceName = value)
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
