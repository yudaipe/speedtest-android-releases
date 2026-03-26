package com.shogun.speedtest.sheets

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class FormsClient(
    private val formUrl: String  // 例: "https://docs.google.com/forms/d/{FORM_ID}/formResponse"
) {
    private val client = OkHttpClient()

    /**
     * Google Forms に計測結果を POST する
     * entryValues: フォームのentry ID → 値のマップ
     * 例: mapOf("entry.111111111" to result.timestampIso, ...)
     */
    fun postResult(entryValues: Map<String, String>): Boolean {
        val formBody = FormBody.Builder().apply {
            entryValues.forEach { (key, value) -> add(key, value) }
        }.build()

        val request = Request.Builder()
            .url(formUrl)
            .post(formBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            response.close()
            true  // Forms は成功でも失敗でも200を返すことがある
        } catch (e: Exception) {
            false
        }
    }
}
