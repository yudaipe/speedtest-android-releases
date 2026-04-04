package com.shogun.speedtest.network

import android.content.Context
import android.net.ConnectivityManager
import java.io.File
import java.net.InetAddress
import java.util.Locale

data class AuxiliaryUpstreamEstimate(
    val upstreamType: String,
    val confidence: String = "disputed"
)

class AuxiliaryNetworkEstimator(private val context: Context) {

    companion object {
        private val FIXED_ROUTER_OUIS = mapOf(
            "001D73" to "Buffalo",
            "1062EB" to "Buffalo",
            "D89695" to "Buffalo",
            "0017A5" to "NEC",
            "00259E" to "NEC",
            "3C7C3F" to "NEC",
            "049226" to "ASUS",
            "08606E" to "ASUS",
            "2CFDA1" to "ASUS",
            "50C7BF" to "TP-Link",
            "C46E1F" to "TP-Link",
            "F4F26D" to "TP-Link",
            "001E2A" to "NETGEAR",
            "08BD43" to "NETGEAR",
            "10DA43" to "NETGEAR",
            "001D7E" to "Cisco",
            "00259C" to "Cisco",
            "001349" to "Juniper",
            "00184D" to "Brocade",
            "E0CB4E" to "Elecom"
        )

        private val MOBILE_ROUTER_OUIS = mapOf(
            "08E3AE" to "Huawei",
            "0C96BF" to "Huawei",
            "2CABA4" to "Huawei",
            "40D37A" to "Huawei",
            "6C5AB0" to "Huawei",
            "8C34FD" to "Huawei",
            "6026EF" to "NEC Mobile",
            "74D02B" to "NEC Mobile",
            "A4C7DE" to "NEC Mobile",
            "94DBDA" to "ZTE",
            "CC1AFA" to "ZTE",
            "D46AA8" to "ZTE",
            "E4956E" to "GL.iNet",
            "80A36E" to "GL.iNet",
            "B0B98A" to "Quectel",
            "64CC2E" to "Fibocom",
            "84A8E4" to "Sierra Wireless",
            "D8A98B" to "MikroTik LTE",
            "00A0C6" to "Raspberry Pi / modem host"
        )
    }

    fun estimateUpstreamType(): AuxiliaryUpstreamEstimate {
        val ouiSignal = detectOuiSignal()
        val mtuSignal = detectMtuSignal()

        val upstreamType = when {
            ouiSignal != null && ouiSignal == mtuSignal -> ouiSignal
            ouiSignal == null && mtuSignal != null -> mtuSignal
            mtuSignal == null && ouiSignal != null -> ouiSignal
            else -> "unknown"
        }

        return AuxiliaryUpstreamEstimate(upstreamType = upstreamType)
    }

    private fun detectOuiSignal(): String? {
        val gatewayIp = getDefaultGatewayIp() ?: return null
        val gatewayMac = readGatewayMac(gatewayIp) ?: return null
        val oui = gatewayMac
            .replace(":", "")
            .replace("-", "")
            .uppercase(Locale.US)
            .take(6)

        return when {
            FIXED_ROUTER_OUIS.containsKey(oui) -> "fixed"
            MOBILE_ROUTER_OUIS.containsKey(oui) -> "mobile"
            else -> null
        }
    }

    private fun detectMtuSignal(): String? {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return null
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null
            when {
                linkProperties.mtu == 1500 -> "fixed"
                linkProperties.mtu in 1..1499 -> "mobile"
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getDefaultGatewayIp(): String? {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return null
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null
            linkProperties.routes
                .firstOrNull { route -> route.isDefaultRoute && route.hasGateway() }
                ?.gateway
                ?.hostAddress
                ?.takeIf { it.isNotBlank() && it != InetAddress.getLoopbackAddress().hostAddress }
        } catch (_: Exception) {
            null
        }
    }

    private fun readGatewayMac(gatewayIp: String): String? {
        return try {
            File("/proc/net/arp")
                .useLines { lines ->
                    lines.drop(1)
                        .mapNotNull { line ->
                            val columns = line.trim().split(Regex("\\s+"))
                            if (columns.size >= 4 && columns[0] == gatewayIp) columns[3] else null
                        }
                        .firstOrNull { mac -> mac != "00:00:00:00:00:00" }
                }
        } catch (_: Exception) {
            null
        }
    }
}
