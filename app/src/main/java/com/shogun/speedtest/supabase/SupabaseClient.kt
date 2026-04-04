package com.shogun.speedtest.supabase

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SupabaseClient {

    companion object {
        private const val BASE_URL = "https://zjgfqzdhltrhupexowuf.supabase.co"
        private const val ANON_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpqZ2ZxemRobHRyaHVwZXhvd3VmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzUwOTgwODUsImV4cCI6MjA5MDY3NDA4NX0" +
            ".zot1MZUk-i_1ChPsCBfn8w6ZF4eICKij5_w7xAd7b2w"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private val client = OkHttpClient()

    /**
     * speedtest_results テーブルに1件POSTする。
     * Prefer: return=minimal を使用（anon は SELECT 不可のため）。
     * @return HTTP 201 なら true、それ以外は false
     */
    fun postResult(payload: Map<String, Any?>): Boolean {
        val json = JSONObject().apply {
            payload.forEach { (key, value) ->
                // apn を含む追加メタデータは caller 側 payload をそのまま JSON 化する。
                when (value) {
                    null -> put(key, JSONObject.NULL)
                    else -> put(key, value)
                }
            }
        }.toString()

        val body = json.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$BASE_URL/rest/v1/speedtest_results")
            .addHeader("apikey", ANON_KEY)
            .addHeader("Authorization", "Bearer $ANON_KEY")
            .addHeader("Prefer", "return=minimal")
            .post(body)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val success = response.code == 201
            response.close()
            success
        } catch (e: Exception) {
            false
        }
    }
}
