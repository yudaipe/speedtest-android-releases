package com.shogun.speedtest.network

import android.telephony.TelephonyDisplayInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class CellularInfoCollectorTest {

    @Test
    fun determineIsCa_returnsYes_forLteCaOverride() {
        val result = CellularInfoCollector.determineIsCa(
            activeTransport = CellularInfoCollector.ActiveTransport.CELLULAR,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
            hasSecondaryServingCell = false,
            caBandConfig = null,
            supportsDisplayInfoOverride = true
        )

        assertEquals("yes", result)
    }

    @Test
    fun determineIsCa_returnsYes_forLteAdvancedProOverride() {
        val result = CellularInfoCollector.determineIsCa(
            activeTransport = CellularInfoCollector.ActiveTransport.CELLULAR,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO,
            hasSecondaryServingCell = false,
            caBandConfig = null,
            supportsDisplayInfoOverride = true
        )

        assertEquals("yes", result)
    }

    @Test
    fun determineIsCa_returnsYes_forSecondaryServingFallback() {
        val result = CellularInfoCollector.determineIsCa(
            activeTransport = CellularInfoCollector.ActiveTransport.CELLULAR,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
            hasSecondaryServingCell = true,
            caBandConfig = null,
            supportsDisplayInfoOverride = true
        )

        assertEquals("yes", result)
    }

    @Test
    fun determineIsCa_returnsYes_forCaBandConfigFallback() {
        val result = CellularInfoCollector.determineIsCa(
            activeTransport = CellularInfoCollector.ActiveTransport.CELLULAR,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
            hasSecondaryServingCell = false,
            caBandConfig = "B1+B3",
            supportsDisplayInfoOverride = true
        )

        assertEquals("yes", result)
    }

    @Test
    fun determineIsCa_returnsNo_whenNoSignalMatches() {
        val result = CellularInfoCollector.determineIsCa(
            activeTransport = CellularInfoCollector.ActiveTransport.CELLULAR,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
            hasSecondaryServingCell = false,
            caBandConfig = "B3",
            supportsDisplayInfoOverride = true
        )

        assertEquals("no", result)
    }
}
