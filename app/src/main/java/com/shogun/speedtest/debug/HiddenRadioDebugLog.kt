package com.shogun.speedtest.debug

import android.telephony.PhysicalChannelConfig
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

data class DebugLogEntry(
    val timestamp: Long,
    val event: String,
    val detail: String,
    val stacktrace: String?
)

object HiddenRadioDebugLog {
    private const val MAX_ENTRIES = 50

    private val _entries = mutableStateListOf<DebugLogEntry>()
    val entries: List<DebugLogEntry> get() = _entries

    private val _lastRawDump = mutableStateOf<String?>(null)
    val lastRawDump: String? get() = _lastRawDump.value

    private val _lastException = mutableStateOf<DebugLogEntry?>(null)
    val lastException: DebugLogEntry? get() = _lastException.value

    fun add(event: String, detail: String, stacktrace: String? = null) {
        if (_entries.size >= MAX_ENTRIES) {
            _entries.removeAt(0)
        }
        val entry = DebugLogEntry(
            timestamp = System.currentTimeMillis(),
            event = event,
            detail = detail,
            stacktrace = stacktrace
        )
        _entries.add(entry)
        if (stacktrace != null) {
            _lastException.value = entry
        }
    }

    fun updateRawDump(configs: List<PhysicalChannelConfig>) {
        _lastRawDump.value = if (configs.isEmpty()) {
            "0 configs"
        } else {
            configs.mapIndexed { index, config ->
                buildString {
                    append("#")
                    append(index)
                    append('\n')
                    append("band=")
                    append(runCatching { config.band }.getOrNull() ?: "unknown")
                    append('\n')
                    append("bandwidthDownlinkKhz=")
                    append(runCatching { config.cellBandwidthDownlinkKhz }.getOrNull() ?: "unknown")
                    append('\n')
                    append("networkType=")
                    append(runCatching { config.networkType }.getOrNull() ?: "unknown")
                    append('\n')
                    append("connectionStatus=")
                    append(runCatching { config.connectionStatus }.getOrNull() ?: "unknown")
                    append('\n')
                    append("downlinkChannelNumber=")
                    append(runCatching { config.downlinkChannelNumber }.getOrNull() ?: "unknown")
                    append('\n')
                    append("uplinkChannelNumber=")
                    append(runCatching { config.uplinkChannelNumber }.getOrNull() ?: "unknown")
                    append('\n')
                    append("physicalCellId=")
                    append("[redacted]")
                    append('\n')
                    append("contextIds=")
                    append("[redacted]")
                }
            }.joinToString(separator = "\n\n")
        }
    }

    fun updateRawDump(rawDump: String) {
        _lastRawDump.value = rawDump
    }

    fun stacktrace(throwable: Throwable, maxLines: Int = 5): String {
        return throwable.stackTrace
            .take(maxLines)
            .joinToString(separator = "\n") { it.toString() }
    }
}
