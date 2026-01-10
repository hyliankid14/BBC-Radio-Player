package com.hyliankid14.bbcradioplayer

import android.content.Context

object ThemePreference {
    private const val PREF_NAME = "theme_prefs"
    private const val THEME_KEY = "selected_theme"
    private const val QUALITY_KEY = "audio_quality"
    private const val AUTO_QUALITY_KEY = "auto_detect_quality"
    
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM = "system"
    
    fun getTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(THEME_KEY, THEME_SYSTEM) ?: THEME_SYSTEM
    }
    
    fun setTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(THEME_KEY, theme).apply()
    }
    
    fun getAutoDetectQuality(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(AUTO_QUALITY_KEY, true) // Default to auto-detect enabled
    }
    
    fun setAutoDetectQuality(context: Context, autoDetect: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(AUTO_QUALITY_KEY, autoDetect).apply()
    }
    
    fun getHighQuality(context: Context): Boolean {
        val autoDetect = getAutoDetectQuality(context)
        return if (autoDetect) {
            NetworkQualityDetector.shouldUseHighQuality(context)
        } else {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.getBoolean(QUALITY_KEY, true) // Default to high quality when not auto-detecting
        }
    }
    
    fun setHighQuality(context: Context, highQuality: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(QUALITY_KEY, highQuality).apply()
    }
}
