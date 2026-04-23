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
    val bandwidthKhz: Int?,
    val connectionStatus: String,
    val dlModulation: String
)
