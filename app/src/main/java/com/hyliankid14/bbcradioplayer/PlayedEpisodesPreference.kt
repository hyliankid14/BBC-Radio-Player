package com.hyliankid14.bbcradioplayer

import android.content.Context

object PlayedEpisodesPreference {
    private const val PREFS_NAME = "played_episodes_prefs"
    private const val KEY_PLAYED_IDS = "played_ids"
    const val ACTION_PLAYED_STATUS_CHANGED = "com.hyliankid14.bbcradioplayer.action.PLAYED_STATUS_CHANGED"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPlayedIds(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_PLAYED_IDS, emptySet()) ?: emptySet()
    }

    fun isPlayed(context: Context, episodeId: String): Boolean {
        return getPlayedIds(context).contains(episodeId)
    }

    fun markPlayed(context: Context, episodeId: String) {
        val current = getPlayedIds(context).toMutableSet()
        if (!current.contains(episodeId)) {
            current.add(episodeId)
            // Remove saved progress when episode is considered completed
            removeProgress(context, episodeId)
            prefs(context).edit().putStringSet(KEY_PLAYED_IDS, current).apply()
            // Broadcast change so UI can update
            val intent = android.content.Intent(ACTION_PLAYED_STATUS_CHANGED)
            context.sendBroadcast(intent)
        }
    }

    fun markUnplayed(context: Context, episodeId: String) {
        val current = getPlayedIds(context).toMutableSet()
        if (current.contains(episodeId)) {
            current.remove(episodeId)
            prefs(context).edit().putStringSet(KEY_PLAYED_IDS, current).apply()
            val intent = android.content.Intent(ACTION_PLAYED_STATUS_CHANGED)
            context.sendBroadcast(intent)
        }
    }

    // Playback position storage (milliseconds) --------------------------------------------------
    private fun progressKey(episodeId: String) = "progress_$episodeId"

    fun setProgress(context: Context, episodeId: String, positionMs: Long) {
        prefs(context).edit().putLong(progressKey(episodeId), positionMs).apply()
        // Broadcast change so UI (episode lists) can refresh progress display
        val intent = android.content.Intent(ACTION_PLAYED_STATUS_CHANGED)
        context.sendBroadcast(intent)
    }

    fun getProgress(context: Context, episodeId: String): Long {
        return prefs(context).getLong(progressKey(episodeId), 0L)
    }

    fun removeProgress(context: Context, episodeId: String) {
        prefs(context).edit().remove(progressKey(episodeId)).apply()
    }

    fun getAllProgresses(context: Context): Map<String, Long> {
        val all = prefs(context).all
        val map = mutableMapOf<String, Long>()
        for ((k, v) in all) {
            if (k.startsWith("progress_") && v is Long) {
                val id = k.removePrefix("progress_")
                map[id] = v
            }
        }
        return map
    }
}
