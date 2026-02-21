package com.hyliankid14.bbcradioplayer

import android.content.Context
import org.json.JSONObject

/**
 * Manages downloaded podcast episodes. Stores episode metadata and local file paths.
 * Similar to SavedEpisodes but tracks actual downloaded files on disk.
 */
object DownloadedEpisodes {
    private const val PREFS_NAME = "downloaded_episodes_prefs"
    private const val KEY_DOWNLOADED_SET = "downloaded_set"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Entry(
        val id: String,
        val title: String,
        val description: String,
        val imageUrl: String,
        val audioUrl: String,
        val localFilePath: String,
        val pubDate: String,
        val durationMins: Int,
        val podcastId: String,
        val podcastTitle: String,
        val downloadedAtMs: Long,
        val fileSizeBytes: Long
    )

    fun getDownloadedEntries(context: Context): List<Entry> {
        val set = prefs(context).getStringSet(KEY_DOWNLOADED_SET, emptySet()) ?: emptySet()
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
                    localFilePath = j.optString("localFilePath", ""),
                    pubDate = j.optString("pubDate", ""),
                    durationMins = j.optInt("durationMins", 0),
                    podcastId = j.optString("podcastId", ""),
                    podcastTitle = j.optString("podcastTitle", ""),
                    downloadedAtMs = j.optLong("downloadedAtMs", 0L),
                    fileSizeBytes = j.optLong("fileSizeBytes", 0L)
                )
                list.add(e)
            } catch (_: Exception) {}
        }
        // Return in reverse chronological order
        return list.sortedByDescending { it.downloadedAtMs }
    }

    fun isDownloaded(context: Context, episodeId: String): Boolean {
        return getDownloadedEntries(context).any { it.id == episodeId }
    }

    fun getDownloadedEntry(context: Context, episodeId: String): Entry? {
        return getDownloadedEntries(context).firstOrNull { it.id == episodeId }
    }

    fun addDownloaded(context: Context, episode: Episode, localFilePath: String, fileSizeBytes: Long, podcastTitle: String? = null) {
        val current = prefs(context).getStringSet(KEY_DOWNLOADED_SET, emptySet())?.toMutableSet() ?: mutableSetOf()
        
        // Remove existing entry if present
        val existing = current.firstOrNull { 
            try { JSONObject(it).getString("id") == episode.id } 
            catch (_: Exception) { false } 
        }
        if (existing != null) {
            current.remove(existing)
        }

        val j = JSONObject()
        j.put("id", episode.id)
        j.put("title", episode.title)
        j.put("description", episode.description)
        j.put("imageUrl", episode.imageUrl)
        j.put("audioUrl", episode.audioUrl)
        j.put("localFilePath", localFilePath)
        j.put("pubDate", episode.pubDate)
        j.put("durationMins", episode.durationMins)
        j.put("podcastId", episode.podcastId)
        j.put("podcastTitle", podcastTitle ?: "")
        j.put("downloadedAtMs", System.currentTimeMillis())
        j.put("fileSizeBytes", fileSizeBytes)
        
        current.add(j.toString())
        prefs(context).edit().putStringSet(KEY_DOWNLOADED_SET, current).apply()
    }

    fun removeDownloaded(context: Context, episodeId: String): Entry? {
        val current = prefs(context).getStringSet(KEY_DOWNLOADED_SET, emptySet())?.toMutableSet() ?: mutableSetOf()
        val existing = current.firstOrNull { 
            try { JSONObject(it).getString("id") == episodeId } 
            catch (_: Exception) { false } 
        }
        
        var entry: Entry? = null
        if (existing != null) {
            try {
                val j = JSONObject(existing)
                entry = Entry(
                    id = j.getString("id"),
                    title = j.optString("title", ""),
                    description = j.optString("description", ""),
                    imageUrl = j.optString("imageUrl", ""),
                    audioUrl = j.optString("audioUrl", ""),
                    localFilePath = j.optString("localFilePath", ""),
                    pubDate = j.optString("pubDate", ""),
                    durationMins = j.optInt("durationMins", 0),
                    podcastId = j.optString("podcastId", ""),
                    podcastTitle = j.optString("podcastTitle", ""),
                    downloadedAtMs = j.optLong("downloadedAtMs", 0L),
                    fileSizeBytes = j.optLong("fileSizeBytes", 0L)
                )
            } catch (_: Exception) {}
            
            current.remove(existing)
            prefs(context).edit().putStringSet(KEY_DOWNLOADED_SET, current).apply()
        }
        return entry
    }

    fun removeAll(context: Context) {
        prefs(context).edit().putStringSet(KEY_DOWNLOADED_SET, emptySet()).apply()
    }

    fun getDownloadedEpisodesForPodcast(context: Context, podcastId: String): List<Entry> {
        return getDownloadedEntries(context).filter { it.podcastId == podcastId }
    }

    fun getTotalDownloadedSize(context: Context): Long {
        return getDownloadedEntries(context).sumOf { it.fileSizeBytes }
    }
}
