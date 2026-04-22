package com.shogun.speedtest.at

/**
 * ATコマンド送信インターフェース
 * v1: stub実装（UnsupportedOperationException）
 * v2以降: usb-serial-for-android で RouterAtClient を実装
 */
interface IAtCommandInterface {

    /**
     * ATコマンドを送信してレスポンスを返す
     * @throws UnsupportedOperationException v1では未実装
     */
    fun sendCommand(command: String): String

    /**
     * ルーターSIMの電波強度を取得（dBm）
     * @return dBm値。取得不可時はnull
     */
    fun getRssiDbm(): Int?

    /**
     * PDP再接続（ソフトリカバリ）
     */
    fun reconnectPdp()

    /**
     * モデム完全再起動
     */
    fun restartModem()
}
