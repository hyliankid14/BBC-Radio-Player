package com.example.androidautoradioplayer

import android.content.Context

object PlaybackPreference {
    private const val PREFS_NAME = "playback_prefs"
    private const val KEY_LAST_STATION_ID = "last_station_id"
    private const val KEY_AUTO_RESUME_ANDROID_AUTO = "auto_resume_android_auto"

    fun setLastStationId(context: Context, stationId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_STATION_ID, stationId).apply()
    }

    fun getLastStationId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_STATION_ID, null)
    }

    fun setAutoResumeAndroidAuto(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_RESUME_ANDROID_AUTO, enabled).apply()
    }

    fun isAutoResumeAndroidAutoEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_RESUME_ANDROID_AUTO, false)
    }
}
