package com.shogun.speedtest.shizuku

import android.content.Context
import android.content.pm.PackageManager
import com.shogun.speedtest.collector.HiddenRadioCollector
import com.shogun.speedtest.data.HiddenRadioSnapshot
import com.shogun.speedtest.debug.HiddenRadioDebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

enum class ShizukuAccessState {
    Unavailable,
    PermissionDenied,
    Granted
}

class ShizukuManager(private val context: Context) {

    private val _state = MutableStateFlow(resolveState())
    val state: StateFlow<ShizukuAccessState> = _state
    private var lastLoggedState: ShizukuAccessState? = null

    private val _hiddenRadioFlow = MutableStateFlow<HiddenRadioSnapshot?>(null)
    val hiddenRadioFlow: StateFlow<HiddenRadioSnapshot?> = _hiddenRadioFlow

    private val collector by lazy { HiddenRadioCollector(context) }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refreshState()
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, _ ->
            refreshState()
        }

    init {
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        refreshState()
    }

    fun refreshState() {
        val newState = resolveState()
        _state.value = newState
        if (lastLoggedState != newState) {
            HiddenRadioDebugLog.add("permission_change", newState.name)
            lastLoggedState = newState
        }
        if (_state.value == ShizukuAccessState.Granted) {
            collectHiddenRadio()
        } else {
            _hiddenRadioFlow.value = null
        }
    }

    fun collectHiddenRadio() {
        if (_state.value != ShizukuAccessState.Granted) return
        _hiddenRadioFlow.value = runCatching { collector.collect() }.getOrNull()
    }

    fun requestPermission() {
        refreshState()
        if (_state.value != ShizukuAccessState.PermissionDenied) return
        runCatching {
            Shizuku.requestPermission(REQUEST_CODE)
        }.onFailure {
            refreshState()
        }
    }

    fun dispose() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    private fun resolveState(): ShizukuAccessState {
        return runCatching {
            if (Shizuku.isPreV11()) {
                ShizukuAccessState.Unavailable
            } else if (!Shizuku.pingBinder()) {
                ShizukuAccessState.Unavailable
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuAccessState.Granted
            } else {
                ShizukuAccessState.PermissionDenied
            }
        }.getOrDefault(ShizukuAccessState.Unavailable)
    }

    private companion object {
        const val REQUEST_CODE = 1001
    }
}
