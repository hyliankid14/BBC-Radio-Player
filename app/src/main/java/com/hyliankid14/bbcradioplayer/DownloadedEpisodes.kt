package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * Manages downloaded podcast episodes. Stores episode metadata and local file paths.
 * Similar to SavedEpisodes but tracks actual downloaded files on disk.
 */
object DownloadedEpisodes {
    private const val PREFS_NAME = "downloaded_episodes_prefs"
    private const val KEY_DOWNLOADED_SET = "downloaded_set"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun normalizeUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return try {
            val trimmed = url.trim()
            val parsed = android.net.Uri.parse(trimmed)
            if (parsed.scheme.isNullOrBlank()) trimmed.lowercase()
            else parsed.buildUpon().clearQuery().fragment(null).build().toString().lowercase()
        } catch (_: Exception) {
            url.trim().lowercase()
        }
    }

    private fun findDownloadedFileForEpisode(context: Context, episode: Episode): File? {
        return try {
            val baseDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "episodes")
            if (!baseDir.exists() || !baseDir.isDirectory) return null
            val direct = File(baseDir, "${episode.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)}_${episode.id}.mp3")
            if (direct.exists()) return direct

            // Fallback: match by episode id suffix used in our download filename format.
            baseDir.listFiles()?.firstOrNull {
                it.isFile && it.name.endsWith("_${episode.id}.mp3", ignoreCase = true)
            }
        } catch (_: Exception) {
            null
        }
    }

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

    fun isDownloaded(context: Context, episode: Episode): Boolean {
        val byId = getDownloadedEntry(context, episode.id)
        if (byId != null) return true

        val audioKey = normalizeUrl(episode.audioUrl)
        if (audioKey.isNotBlank() && getDownloadedEntries(context).any { normalizeUrl(it.audioUrl) == audioKey }) return true

        return findDownloadedFileForEpisode(context, episode) != null
    }

    fun getDownloadedEntry(context: Context, episodeId: String): Entry? {
        return getDownloadedEntries(context).firstOrNull { it.id == episodeId }
    }

    fun getDownloadedEntry(context: Context, episode: Episode): Entry? {
        getDownloadedEntry(context, episode.id)?.let { return it }

        val audioKey = normalizeUrl(episode.audioUrl)
        if (audioKey.isNotBlank()) {
            getDownloadedEntries(context).firstOrNull { normalizeUrl(it.audioUrl) == audioKey }?.let { return it }
        }

        val fallbackFile = findDownloadedFileForEpisode(context, episode) ?: return null
        return Entry(
            id = episode.id,
            title = episode.title,
            description = episode.description,
            imageUrl = episode.imageUrl,
            audioUrl = episode.audioUrl,
            localFilePath = fallbackFile.absolutePath,
            pubDate = episode.pubDate,
            durationMins = episode.durationMins,
            podcastId = episode.podcastId,
            podcastTitle = "",
            downloadedAtMs = fallbackFile.lastModified(),
            fileSizeBytes = fallbackFile.length()
        )
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
