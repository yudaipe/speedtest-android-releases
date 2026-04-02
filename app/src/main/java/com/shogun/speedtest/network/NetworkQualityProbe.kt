package com.shogun.speedtest.network

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

data class NetworkQualityMetrics(
    val dnsResolveMs: Double? = null,
    val ttfbMs: Double? = null,
    val tcpConnectMs: Double? = null
)

class NetworkQualityProbe {

    fun collect(): NetworkQualityMetrics {
        return NetworkQualityMetrics(
            dnsResolveMs = measure("dns") { InetAddress.getByName("google.com") },
            ttfbMs = measure("ttfb") {
                val connection = (URL("https://www.google.com").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                    instanceFollowRedirects = true
                }
                connection.useCaches = false
                connection.connect()
                connection.inputStream.use { it.read() }
                connection.disconnect()
            },
            tcpConnectMs = measure("tcp") {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("8.8.8.8", 443), 3000)
                }
            }
        )
    }

    private fun measure(label: String, block: () -> Unit): Double? {
        return try {
            val start = System.nanoTime()
            block()
            (System.nanoTime() - start) / 1_000_000.0
        } catch (_: Exception) {
            null
        }
    }
}
