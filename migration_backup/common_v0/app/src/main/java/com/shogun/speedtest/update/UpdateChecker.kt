package com.shogun.speedtest.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

class UpdateChecker(private val context: Context) {
    private val client = OkHttpClient()

    companion object {
        const val VERSION_JSON_URL = "https://github.com/yudaipe/speedtest-android-releases/releases/latest/download/version.json"
    }

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(VERSION_JSON_URL).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            UpdateInfo(
                versionCode = json.getInt("version_code"),
                versionName = json.getString("version_name"),
                apkUrl = json.getString("apk_url"),
                releaseNotes = json.optString("release_notes", "")
            )
        } catch (e: Exception) {
            android.util.Log.e("UpdateChecker", "version check failed: ${e.message}")
            null
        }
    }

    fun getCurrentVersionCode(): Int {
        return try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .longVersionCode.toInt()
        } catch (e: Exception) {
            1
        }
    }
}
