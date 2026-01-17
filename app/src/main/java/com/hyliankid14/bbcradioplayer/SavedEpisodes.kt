package com.hyliankid14.bbcradioplayer

import android.content.Context
import org.json.JSONObject

/**
 * Simple persisted store for saved podcast episodes (stored as JSON entries in SharedPreferences).
 * Stores minimal metadata so saved episodes can be shown without fetching full feeds.
 */
object SavedEpisodes {
    private const val PREFS_NAME = "saved_episodes_prefs"
    private const val KEY_SAVED_SET = "saved_set"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Entry(
        val id: String,
        val title: String,
        val description: String,
        val imageUrl: String,
        val audioUrl: String,
        val pubDate: String,
        val durationMins: Int,
        val podcastId: String,
        val podcastTitle: String,
        val savedAtMs: Long
    )

    fun getSavedEntries(context: Context): List<Entry> {
        val set = prefs(context).getStringSet(KEY_SAVED_SET, emptySet()) ?: emptySet()
        val list = mutableListOf<Entry>()
        for (s in set) {
            try {
                val j = JSONObject(s)
                val e = Entry(
                    id = j.getString("id"),
                    title = j.optString("title", ""),
                    description = j.optString("description", ""),
                    imageUrl = j.optString("imageUrl", ""),
                    audioUrl = j.optString("audioUrl", ""),
                    pubDate = j.optString("pubDate", ""),
                    durationMins = j.optInt("durationMins", 0),
                    podcastId = j.optString("podcastId", ""),
                    podcastTitle = j.optString("podcastTitle", ""),
                    savedAtMs = j.optLong("savedAtMs", 0L)
                )
                list.add(e)
            } catch (_: Exception) {}
        }
        // Return in reverse chronological order
        return list.sortedByDescending { it.savedAtMs }
    }

    fun isSaved(context: Context, episodeId: String): Boolean {
        return getSavedEntries(context).any { it.id == episodeId }
    }

    fun toggleSaved(context: Context, episode: Episode, podcastTitle: String? = null): Boolean {
        val current = prefs(context).getStringSet(KEY_SAVED_SET, emptySet())?.toMutableSet() ?: mutableSetOf()
        val existing = current.firstOrNull { try { JSONObject(it).getString("id") == episode.id } catch (_: Exception) { false } }
        if (existing != null) {
            current.remove(existing)
            prefs(context).edit().putStringSet(KEY_SAVED_SET, current).apply()
            return false
        } else {
            val j = JSONObject()
            j.put("id", episode.id)
            j.put("title", episode.title ?: "")
            j.put("description", episode.description ?: "")
            j.put("imageUrl", episode.imageUrl ?: "")
            j.put("audioUrl", episode.audioUrl ?: "")
            j.put("pubDate", episode.pubDate ?: "")
            j.put("durationMins", episode.durationMins)
            j.put("podcastId", episode.podcastId)
            j.put("podcastTitle", podcastTitle ?: "")
            j.put("savedAtMs", System.currentTimeMillis())
            current.add(j.toString())
            prefs(context).edit().putStringSet(KEY_SAVED_SET, current).apply()
            return true
        }
    }

    fun remove(context: Context, episodeId: String) {
        val current = prefs(context).getStringSet(KEY_SAVED_SET, emptySet())?.toMutableSet() ?: mutableSetOf()
        val existing = current.firstOrNull { try { JSONObject(it).getString("id") == episodeId } catch (_: Exception) { false } }
        if (existing != null) {
            current.remove(existing)
            prefs(context).edit().putStringSet(KEY_SAVED_SET, current).apply()
        }
    }

    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY_SAVED_SET).apply()
    }
}
