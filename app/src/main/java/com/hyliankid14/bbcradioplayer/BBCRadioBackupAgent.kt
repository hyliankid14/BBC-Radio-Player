package com.hyliankid14.bbcradioplayer

import android.app.backup.BackupAgentHelper
import android.app.backup.SharedPreferencesBackupHelper

/**
 * Backup agent for persisting app preferences across uninstall/reinstall.
 * Backs up:
 * - Favorites (radio stations)
 * - Podcast subscriptions
 * - Playback preferences (last station, auto-resume settings)
 * - Scrolling preferences (view mode: all stations vs favorites)
 */
class BBCRadioBackupAgent : BackupAgentHelper() {
    override fun onCreate() {
        super.onCreate()
        
        // Backup favorites
        addHelper("favorites", SharedPreferencesBackupHelper(this, "favorites_prefs"))
        
        // Backup podcast subscriptions
        addHelper("subscriptions", SharedPreferencesBackupHelper(this, "podcast_subscriptions"))
        
        // Backup playback preferences (last station, auto-resume Android Auto setting)
        addHelper("playback", SharedPreferencesBackupHelper(this, "playback_prefs"))
        
        // Backup scrolling/view preferences (all stations vs favorites mode)
        addHelper("scrolling", SharedPreferencesBackupHelper(this, "scrolling_prefs"))
    }
}
