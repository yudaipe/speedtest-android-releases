package com.shogun.speedtest.device

import android.content.Context
import android.provider.Settings

object DeviceIdentifier {

    /**
     * Settings.Secure.ANDROID_ID を端末IDとして使用
     * - 端末固有（アプリ署名鍵+端末ペアで一意）
     * - ファクトリーリセットで変更される
     * - READ_PHONE_STATE等の危険な権限が不要
     */
    fun getId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }
}
