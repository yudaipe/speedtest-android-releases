package com.shogun.speedtest.at

/**
 * v1 Stub実装
 * v2以降: usb-serial-for-android で RouterAtClient を実装
 */
class AtCommandStub : IAtCommandInterface {
    override fun sendCommand(command: String): String =
        throw UnsupportedOperationException("ATコマンドはv2以降で実装予定")

    override fun getRssiDbm(): Int? = null

    override fun reconnectPdp() =
        throw UnsupportedOperationException("ATコマンドはv2以降で実装予定")

    override fun restartModem() =
        throw UnsupportedOperationException("ATコマンドはv2以降で実装予定")
}
