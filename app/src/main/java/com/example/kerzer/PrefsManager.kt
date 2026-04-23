package com.example.kerzer

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("kerzer_prefs", Context.MODE_PRIVATE)

    fun isFocusModeActive(): Boolean = prefs.getBoolean("focus_mode_active", false)
    
    fun setFocusModeActive(active: Boolean) {
        prefs.edit().putBoolean("focus_mode_active", active).apply()
    }

    fun getBlockedApps(): Set<String> {
        return prefs.getStringSet("blocked_apps", mutableSetOf()) ?: setOf()
    }

    fun setBlockedApp(packageName: String, blocked: Boolean) {
        val apps = getBlockedApps().toMutableSet()
        if (blocked) apps.add(packageName) else apps.remove(packageName)
        prefs.edit().putStringSet("blocked_apps", apps).apply()
    }

    fun getAppLimit(packageName: String): Int {
        return prefs.getInt("limit_$packageName", -1) // -1 means no limit
    }

    fun setAppLimit(packageName: String, limitMinutes: Int) {
        prefs.edit().putInt("limit_$packageName", limitMinutes).apply()
    }
}
