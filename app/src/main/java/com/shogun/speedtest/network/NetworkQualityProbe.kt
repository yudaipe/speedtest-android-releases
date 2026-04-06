package com.shogun.speedtest.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.floor

data class NetworkQualityMetrics(
    val dnsResolveMs: Double? = null,
    val ttfbMs: Double? = null,
    val tcpConnectMs: Double? = null
)

data class ProbeTarget(
    val label: String,
    val dnsHost: String,
    val tcpHost: String,
    val tcpPort: Int = 443,
    val httpUrl: String
)

class NetworkQualityProbe(
    private val targets: List<ProbeTarget> = DEFAULT_TARGETS,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS
) {

    suspend fun collect(): NetworkQualityMetrics {
        val probeTargets = targets.takeIf { it.size >= 3 } ?: DEFAULT_TARGETS
        return NetworkQualityMetrics(
            dnsResolveMs = measureAcrossTargets(probeTargets) { target ->
                InetAddress.getByName(target.dnsHost)
            },
            ttfbMs = measureAcrossTargets(probeTargets) { target ->
                val connection = (URL(target.httpUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    instanceFollowRedirects = true
                    useCaches = false
                }
                try {
                    connection.connect()
                    val stream = if (connection.responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                        connection.errorStream
                    } else {
                        connection.inputStream
                    }
                    stream?.use { it.read() }
                } finally {
                    connection.disconnect()
                }
            },
            tcpConnectMs = measureAcrossTargets(probeTargets) { target ->
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(target.tcpHost, target.tcpPort), timeoutMs)
                }
            }
        )
    }

    private suspend fun measureAcrossTargets(
        probeTargets: List<ProbeTarget>,
        block: (ProbeTarget) -> Unit
    ): Double? {
        val samples = probeTargets.mapNotNull { target -> measure(target, block) }
        return selectRepresentative(samples)
    }

    private suspend fun measure(
        target: ProbeTarget,
        block: (ProbeTarget) -> Unit
    ): Double? = try {
        withContext(Dispatchers.IO) {
            val start = System.nanoTime()
            block(target)
            (System.nanoTime() - start) / 1_000_000.0
        }
    } catch (_: Exception) {
        null
    }

    private fun selectRepresentative(samples: List<Double>): Double? {
        if (samples.isEmpty()) return null
        val sorted = samples.sorted()
        return if (sorted.size >= 3) {
            sorted[floor(sorted.size / 2.0).toInt()]
        } else {
            sorted.first()
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 5_000

        val DEFAULT_TARGETS = listOf(
            ProbeTarget(
                label = "google",
                dnsHost = "google.com",
                tcpHost = "8.8.8.8",
                httpUrl = "https://www.google.com/generate_204"
            ),
            ProbeTarget(
                label = "cloudflare",
                dnsHost = "one.one.one.one",
                tcpHost = "1.1.1.1",
                httpUrl = "https://www.cloudflare.com/cdn-cgi/trace"
            ),
            ProbeTarget(
                label = "aws-ap-northeast-1",
                dnsHost = "ec2.ap-northeast-1.amazonaws.com",
                tcpHost = "ec2.ap-northeast-1.amazonaws.com",
                httpUrl = "https://ec2.ap-northeast-1.amazonaws.com"
            )
        )
    }
}
