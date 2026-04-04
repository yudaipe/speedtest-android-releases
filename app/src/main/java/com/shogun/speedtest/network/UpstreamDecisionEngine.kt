package com.shogun.speedtest.network

data class UpstreamDecision(
    val upstreamType: String,
    val upstreamSource: String,
    val upstreamConfidence: String
)

class UpstreamDecisionEngine {
    fun decide(
        hasBeaconData: Boolean,
        bssidInDeviceConfig: Boolean,
        deviceConfigUpstreamType: String?,
        hasCdrMatch: Boolean,
        auxiliaryEstimate: AuxiliaryUpstreamEstimate
    ): UpstreamDecision {
        return when {
            hasBeaconData -> UpstreamDecision(
                upstreamType = "mobile",
                upstreamSource = "beacon",
                upstreamConfidence = "confirmed"
            )

            bssidInDeviceConfig && deviceConfigUpstreamType != null -> UpstreamDecision(
                upstreamType = deviceConfigUpstreamType,
                upstreamSource = "device_config",
                upstreamConfidence = "confirmed"
            )

            hasCdrMatch -> UpstreamDecision(
                upstreamType = "mobile",
                upstreamSource = "cdr",
                upstreamConfidence = "confirmed"
            )

            auxiliaryEstimate.upstreamType != "unknown" -> UpstreamDecision(
                upstreamType = auxiliaryEstimate.upstreamType,
                upstreamSource = "auxiliary",
                upstreamConfidence = "disputed"
            )

            else -> UpstreamDecision(
                upstreamType = "unknown",
                upstreamSource = "none",
                upstreamConfidence = "unknown"
            )
        }
    }
}
