package com.shogun.speedtest.settings

import android.content.Context
import androidx.core.content.edit

class DebugLogPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("speedtest_prefs", Context.MODE_PRIVATE)

    fun isDebugLogEnabled(): Boolean = prefs.getBoolean("pref_debug_log_enabled", false)

    fun setDebugLogEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("pref_debug_log_enabled", enabled) }
    }
}
