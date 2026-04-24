package com.shogun.speedtest.data

data class HiddenRadioSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val componentCarriers: List<CarrierInfo> = emptyList()
) {
    val ccCount: Int get() = componentCarriers.size
    val isCarrierAggregation: Boolean get() = ccCount > 1
    val lteCcCount: Int
        get() = componentCarriers.count { carrier ->
            carrier.networkType?.startsWith("LTE") == true &&
                carrier.connectionStatus in setOf("PCC", "SCC", "ACTIVE")
        }
    val nrCcCount: Int? get() = null
    val lteBandwidthSummary: String?
        get() {
            val bws = componentCarriers
                .filter { carrier ->
                    carrier.networkType?.startsWith("LTE") == true &&
                        carrier.connectionStatus in setOf("PCC", "SCC", "ACTIVE")
                }
                .mapNotNull { carrier -> carrier.bandwidthDownlinkKhz?.let { it / 1000 } }
            return if (bws.isEmpty()) null else "(${bws.joinToString("+")})"
        }
}

data class CarrierInfo(
    val band: Int?,
    val bandwidthDownlinkKhz: Int?,
    val bandwidthUplinkKhz: Int?,
    val physicalCellId: Int?,
    val networkType: String?,
    val frequencyRange: String?,
    val connectionStatus: String,
    val downlinkFrequencyKhz: Int?,
    val downlinkChannelNumber: Int?
)
