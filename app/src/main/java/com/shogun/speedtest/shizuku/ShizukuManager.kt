package com.shogun.speedtest.shizuku

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

enum class ShizukuAccessState {
    Unavailable,
    PermissionDenied,
    Granted
}

class ShizukuManager {

    private val _state = MutableStateFlow(resolveState())
    val state: StateFlow<ShizukuAccessState> = _state

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
        _state.value = resolveState()
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
