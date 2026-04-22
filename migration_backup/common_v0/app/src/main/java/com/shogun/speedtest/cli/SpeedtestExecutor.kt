package com.shogun.speedtest.cli

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import java.io.File
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

class SpeedtestExecutor(private val context: Context) {

    // nativeLibraryDir = /data/app/com.shogun.speedtest-xxx/lib/arm64-v8a/
    // libspeedtest.so が展開されているパス（app_lib_file コンテキスト → execute_no_trans 許可）
    private val binaryFile: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libspeedtest.so")

    /**
     * speedtest-go は /etc/resolv.conf の代わりに ./etc/resolv.cnf を読む（バイナリパッチ済み）。
     * 実行前にワーキングディレクトリ（filesDir）に etc/resolv.cnf を作成する。
     */
    private fun ensureResolvConf() {
        val etcDir = File(context.filesDir, "etc")
        etcDir.mkdirs()
        val resolvFile = File(etcDir, "resolv.cnf")

        // Android の ConnectivityManager から DNS サーバーを取得する
        val dnsServers = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val lp: LinkProperties? = net?.let { cm.getLinkProperties(it) }
            lp?.dnsServers?.map { it.hostAddress }?.filterNotNull()?.take(2)
        } catch (e: Exception) { null }

        // フォールバック: Tailscale MagicDNS / Google DNS
        val servers = if (!dnsServers.isNullOrEmpty()) dnsServers
                      else listOf("100.100.100.100", "8.8.8.8")

        val content = servers.joinToString("\n") { "nameserver $it" } + "\n"
        resolvFile.writeText(content)
    }

    /**
     * nativeLibraryDir は Android が自動展開するため手動コピー不要。
     * SELinux対策: app_lib_file コンテキストで execute_no_trans が許可される。
     */
    fun installBinaryIfNeeded() {
        // nativeLibraryDir は Android が自動展開するため手動コピー不要
        if (!binaryFile.exists()) {
            throw RuntimeException(
                "speedtest binary not found at ${binaryFile.absolutePath}. " +
                "Check jniLibs/arm64-v8a/libspeedtest.so in APK."
            )
        }
        // nativeLibraryDir のファイルはシステムが管理するため chmod 不要
    }

    /**
     * WiFiインターフェース名を動的取得
     * wlan0固定をやめてNetworkInterfaceで検索
     */
    private fun getWifiInterface(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.filter { it.isUp && !it.isLoopback }
                ?.filter { iface ->
                    // WiFiインターフェースを優先: wlan0, wlan1, etc.
                    iface.name.startsWith("wlan") ||
                    iface.inetAddresses.asSequence().any { addr ->
                        !addr.isLoopbackAddress && addr is java.net.Inet4Address
                    }
                }
                ?.firstOrNull()?.name
        } catch (e: Exception) {
            null
        }
    }

    /**
     * speedtest-go CLI実行
     * @param serverId 測定サーバーID（デフォルト: 48463 = IPA CyberLab 400G）
     * @return JSON形式の測定結果文字列
     */
    fun runSpeedtest(serverId: Int = 48463): String {
        installBinaryIfNeeded()
        ensureResolvConf()

        val cmd = buildList {
            add(binaryFile.absolutePath)
            add("-s")
            add(serverId.toString())
            add("--json")
        }

        val pb = ProcessBuilder(cmd)
        pb.directory(context.filesDir)

        // HOME/TMPDIR/LD_LIBRARY_PATH 環境変数設定
        // SSL_CERT_DIR: Android の CA 証明書ディレクトリを Go の TLS に渡す
        pb.environment().apply {
            put("HOME", context.filesDir.absolutePath)
            put("TMPDIR", context.cacheDir.absolutePath)
            put("LD_LIBRARY_PATH", context.applicationInfo.nativeLibraryDir)
            put("SSL_CERT_DIR", "/system/etc/security/cacerts")
        }

        // stdin を /dev/null にリダイレクト
        pb.redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))

        // stdout/stderr を一時ファイルにリダイレクト（SELinux TIOCGWINSZ ioctl 回避）
        val tmpOutput = File(context.cacheDir, "speedtest_out_${System.currentTimeMillis()}.txt")
        val tmpError  = File(context.cacheDir, "speedtest_err_${System.currentTimeMillis()}.txt")
        try {
            pb.redirectOutput(tmpOutput)
            pb.redirectError(tmpError)

            val process = pb.start()
            val completed = process.waitFor(120, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                throw RuntimeException("speedtest timeout after 120s")
            }
            val exitCode = process.exitValue()
            val output = if (tmpOutput.exists()) tmpOutput.readText() else ""
            val errOutput = if (tmpError.exists()) tmpError.readText() else ""

            if (exitCode != 0) {
                throw SpeedtestExecutionException(
                    "speedtest exited with code $exitCode:\n$output\n$errOutput"
                )
            }
            return output
        } finally {
            tmpOutput.delete()
            tmpError.delete()
        }
    }
}

class SpeedtestExecutionException(message: String) : RuntimeException(message)
