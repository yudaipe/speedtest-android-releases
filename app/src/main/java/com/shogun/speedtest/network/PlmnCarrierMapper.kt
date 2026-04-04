package com.shogun.speedtest.network

class PlmnCarrierMapper private constructor() {
    companion object {
        private val PLMN_MAP = mapOf(
            "44000" to "ワイモバイル",
            "44001" to "UQ mobile",
            "44002" to "KDDI",
            "44003" to "IIJmio",
            "44004" to "Starnet",
            "44010" to "NTTドコモ",
            "44011" to "楽天モバイル",
            "44012" to "ケーブルメディアワイワイ",
            "44020" to "SoftBank",
            "44021" to "SoftBank",
            "44024" to "日本通信",
            "44025" to "SoftBank",
            "44050" to "KDDI",
            "44051" to "KDDI",
            "44052" to "KDDI",
            "44053" to "KDDI",
            "44054" to "KDDI",
            "44055" to "KDDI",
            "44070" to "SoftBank",
            "44071" to "SoftBank",
            "44072" to "SoftBank",
            "44073" to "SoftBank",
            "44074" to "SoftBank",
            "44075" to "SoftBank",
            "44090" to "ワイモバイル",
            "44091" to "東京2020組織委員会",
            "44098" to "NTTドコモ",
            "44099" to "NTTドコモ",
            "44101" to "NTTドコモ",
            "44140" to "NTTドコモ",
            "44141" to "NTTドコモ",
            "44142" to "NTTドコモ",
            "44143" to "NTTドコモ",
            "44144" to "NTTドコモ",
            "44150" to "TU-KA",
            "44151" to "SoftBank",
            "44161" to "SoftBank"
        )

        fun getPhysicalCarrier(mcc: String, mnc: String): String? = PLMN_MAP[mcc + mnc]
    }
}
