package com.shogun.speedtest.data

data class HiddenRadioSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val componentCarriers: List<CarrierInfo> = emptyList()
) {
    val ccCount: Int get() = componentCarriers.size
    val isCarrierAggregation: Boolean get() = ccCount > 1
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
