package com.shogun.speedtest.collector

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.shogun.speedtest.data.CarrierInfo
import com.shogun.speedtest.data.HiddenRadioSnapshot
import com.shogun.speedtest.debug.HiddenRadioDebugLog
import rikka.shizuku.Shizuku

class HiddenRadioCollector(private val context: Context) {

    fun collect(): HiddenRadioSnapshot {
        val sdkInt = Build.VERSION.SDK_INT
        HiddenRadioDebugLog.add("collect_start", "sdk=$sdkInt")
        HiddenRadioDebugLog.add("sdk_version", sdkInt.toString())
        val dumpResult = collectViaDumpsys()
        HiddenRadioDebugLog.updateRawDump(dumpResult.debugDump)
        if (dumpResult.configs.isEmpty()) {
            HiddenRadioDebugLog.add("empty_result", "sdk=$sdkInt configs=0")
        }

        val carriers = dumpResult.configs.map(::buildCarrierInfo)
        Log.d(TAG, "collect mapped carrierCount=${carriers.size}")
        HiddenRadioDebugLog.add("collect_success", "configs=${dumpResult.configs.size}")
        return HiddenRadioSnapshot(componentCarriers = carriers)
    }

    private fun collectViaDumpsys(): DumpResult {
        HiddenRadioDebugLog.add("collect_path", "dumpsys telephony.registry")
        HiddenRadioDebugLog.add("privileged_context", "Shizuku.newProcess(dumpsys telephony.registry)")
        return try {
            val process = newProcess(arrayOf("sh", "-c", "dumpsys telephony.registry"))
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            HiddenRadioDebugLog.add("dumpsys_exit", "exitCode=$exitCode stderr=${stderr.ifBlank { "-" }}")
            if (exitCode != 0) {
                HiddenRadioDebugLog.add("dumpsys_failed", "exitCode=$exitCode")
                return DumpResult(
                    configs = emptyList(),
                    debugDump = buildDebugDump(stdout, stderr, exitCode)
                )
            }
            val configs = parsePhysicalChannelConfigs(stdout)
            HiddenRadioDebugLog.add("dumpsys_parse", "configs=${configs.size}")
            DumpResult(
                configs = configs,
                debugDump = buildDebugDump(stdout, stderr, exitCode)
            )
        } catch (e: SecurityException) {
            logFailure("dumpsys_security_denied", e)
            DumpResult(emptyList(), e.stackTraceToString())
        } catch (e: Exception) {
            logFailure("dumpsys_collect_fail", e)
            DumpResult(emptyList(), e.stackTraceToString())
        }
    }

    private fun parsePhysicalChannelConfigs(output: String): List<ParsedPhysicalChannelConfig> {
        val section = output.lineSequence()
            .filter { it.contains("mPhysicalChannelConfigs=") }
            .joinToString(separator = "\n")
        if (section.isBlank()) {
            HiddenRadioDebugLog.add("dumpsys_section_missing", "mPhysicalChannelConfigs not found")
            return emptyList()
        }

        val blocks = CONFIG_BLOCK_REGEX.findAll(section)
            .map { it.value.trim().removePrefix("{").removeSuffix("}") }
            .toList()
        if (blocks.isEmpty()) {
            HiddenRadioDebugLog.add("dumpsys_parse_empty", "mPhysicalChannelConfigs present but no config blocks")
            return emptyList()
        }

        return blocks.map { block ->
            ParsedPhysicalChannelConfig(
                connectionStatus = findField(block, "mConnectionStatus"),
                bandwidthDownlinkKhz = findInt(block, "mCellBandwidthDownlinkKhz"),
                bandwidthUplinkKhz = findInt(block, "mCellBandwidthUplinkKhz"),
                networkType = findInt(block, "mNetworkType"),
                frequencyRange = findInt(block, "mFrequencyRange"),
                downlinkFrequencyKhz = findInt(
                    block,
                    "mDownlinkFrequency",
                    "mDownlinkFrequencyKhz"
                ),
                downlinkChannelNumber = findInt(block, "mDownlinkChannelNumber"),
                physicalCellId = findInt(block, "mPhysicalCellId"),
                band = findInt(block, "mBand")
            )
        }
    }

    private fun findField(block: String, vararg fieldNames: String): String? {
        return fieldNames.firstNotNullOfOrNull { fieldName ->
            FIELD_REGEX_TEMPLATE
                .replace("%FIELD%", Regex.escape(fieldName))
                .toRegex()
                .find(block)
                ?.groupValues
                ?.getOrNull(1)
        }
    }

    private fun findInt(block: String, vararg fieldNames: String): Int? {
        return fieldNames.firstNotNullOfOrNull { fieldName ->
            findField(block, fieldName)?.toIntOrNull()
        }
    }

    private fun buildDebugDump(stdout: String, stderr: String, exitCode: Int): String {
        return buildString {
            appendLine("command=dumpsys telephony.registry")
            appendLine("exitCode=$exitCode")
            appendLine("stderr=${stderr.ifBlank { "-" }}")
            appendLine("stdout:")
            append(stdout.ifBlank { "<empty>" })
        }
    }

    private fun newProcess(command: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, command, null, null) as Process
    }

    private fun logFailure(reason: String, throwable: Exception) {
        Log.w(TAG, "$reason while collecting via dumpsys", throwable)
        HiddenRadioDebugLog.add(
            event = reason,
            detail = "sdk=${Build.VERSION.SDK_INT} ${throwable.javaClass.simpleName}: ${throwable.message ?: "unknown"}",
            stacktrace = HiddenRadioDebugLog.stacktrace(throwable, 5)
        )
    }

    private fun buildCarrierInfo(config: ParsedPhysicalChannelConfig): CarrierInfo {
        return CarrierInfo(
            band = config.band,
            bandwidthDownlinkKhz = config.bandwidthDownlinkKhz,
            bandwidthUplinkKhz = config.bandwidthUplinkKhz,
            physicalCellId = config.physicalCellId,
            networkType = config.networkType?.let(::networkTypeLabel),
            frequencyRange = config.frequencyRange?.let(::frequencyRangeLabel),
            connectionStatus = connectionStatusLabel(config.connectionStatus),
            downlinkFrequencyKhz = config.downlinkFrequencyKhz,
            downlinkChannelNumber = config.downlinkChannelNumber
        )
    }

    private fun connectionStatusLabel(value: String?): String {
        if (value == null) return "−"
        return when {
            value == "1" || value == "PRIMARY_SERVING" || value.contains("PRIMARY") -> "PCC"
            value == "2" || value == "SECONDARY_SERVING" || value.contains("SECONDARY") -> "SCC"
            value == "4" || value == "ACTIVE" -> "ACTIVE"
            else -> "−"
        }
    }

    private fun networkTypeLabel(value: Int): String {
        val name = when (value) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO_0"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO_A"
            TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_IDEN -> "IDEN"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO_B"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_EHRPD -> "EHRPD"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP"
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD_SCDMA"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
            TelephonyManager.NETWORK_TYPE_NR -> "NR"
            else -> "TYPE_$value"
        }
        return "$name ($value)"
    }

    private fun frequencyRangeLabel(value: Int): String {
        val name = when (value) {
            0 -> "LOW"
            1 -> "MID"
            2 -> "HIGH"
            3 -> "MMWAVE"
            else -> "RANGE_$value"
        }
        return "$name ($value)"
    }

    private companion object {
        const val TAG = "HiddenRadioCollector"
        val CONFIG_BLOCK_REGEX = Regex("""\{[^{}]+\}""")
        const val FIELD_REGEX_TEMPLATE = """%FIELD%=([^, }]+)"""
    }

    private data class DumpResult(
        val configs: List<ParsedPhysicalChannelConfig>,
        val debugDump: String
    )

    private data class ParsedPhysicalChannelConfig(
        val connectionStatus: String?,
        val bandwidthDownlinkKhz: Int?,
        val bandwidthUplinkKhz: Int?,
        val networkType: Int?,
        val frequencyRange: Int?,
        val downlinkFrequencyKhz: Int?,
        val downlinkChannelNumber: Int?,
        val physicalCellId: Int?,
        val band: Int?
    )
}
