package com.example.androidautoradioplayer

import android.content.Context

object ThemePreference {
    private const val PREF_NAME = "theme_prefs"
    private const val THEME_KEY = "selected_theme"
    
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
}
