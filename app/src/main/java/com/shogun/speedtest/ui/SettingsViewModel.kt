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
            deviceName = settings.getDeviceName(),
            deviceId = settings.getDeviceId()
        )
        refreshDbState()
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
