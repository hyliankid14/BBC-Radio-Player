package com.hyliankid14.bbcradioplayer

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    internal fun buildEntry(context: Context, episode: Episode, podcastTitle: String? = null): PodcastPlaylists.Entry {
        // Resolve the best possible audio URL to store. We avoid network calls here — prefer
        // the episode object, then the active playback URI, then cached feed entries.
        fun looksLikePreview(url: String?, dur: Int): Boolean {
            if (url.isNullOrBlank()) return true
            val low = url.lowercase()
            if (low.contains("preview") || low.contains("snippet") || low.contains("sample")) return true
            if (dur <= 0) return true
            if (url.length < 20) return true
            return false
        }

        var resolvedAudio = episode.audioUrl
        var resolvedDuration = episode.durationMins
        var resolvedPubDate = episode.pubDate
        if (looksLikePreview(resolvedAudio, episode.durationMins)) {
            val fromPlayer = PlaybackStateHelper.getCurrentMediaUri()
            if (!fromPlayer.isNullOrBlank() && !looksLikePreview(fromPlayer, episode.durationMins)) {
                resolvedAudio = fromPlayer
            } else {
                try {
                    val repo = PodcastRepository(context)
                    val cached = repo.getEpisodesFromCache(episode.podcastId)
                    val found = cached?.firstOrNull { it.id == episode.id }
                    if (found != null && !looksLikePreview(found.audioUrl, found.durationMins)) {
                        resolvedAudio = found.audioUrl
                        resolvedDuration = found.durationMins
                        if (resolvedPubDate.isBlank()) resolvedPubDate = found.pubDate
                    } else {
                        try {
                            val idx = com.hyliankid14.bbcradioplayer.db.IndexStore.getInstance(context)
                            val ef = idx.findEpisodeById(episode.id)
                            if (ef != null) {
                                val candidate = repo.getEpisodesFromCache(ef.podcastId)?.firstOrNull { it.id == episode.id }
                                if (candidate != null && !looksLikePreview(candidate.audioUrl, candidate.durationMins)) {
                                    resolvedAudio = candidate.audioUrl
                                    resolvedDuration = candidate.durationMins
                                    if (resolvedPubDate.isBlank()) resolvedPubDate = candidate.pubDate
                                }
                            }
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }
            }
        }

        val entry = PodcastPlaylists.Entry(
            id = episode.id,
            title = episode.title,
            description = episode.description,
            imageUrl = episode.imageUrl,
            audioUrl = resolvedAudio,
            pubDate = resolvedPubDate,
            durationMins = resolvedDuration,
            podcastId = episode.podcastId,
            podcastTitle = podcastTitle ?: "",
            savedAtMs = System.currentTimeMillis()
        )

        val episodeForDownload = Episode(
            id = episode.id,
            title = episode.title,
            description = episode.description,
            audioUrl = resolvedAudio,
            imageUrl = episode.imageUrl,
            pubDate = resolvedPubDate,
            durationMins = resolvedDuration,
            podcastId = episode.podcastId
        )
        triggerAutoDownloadForEpisode(context, episodeForDownload, podcastTitle ?: "")
        return entry
    }

    fun getSavedEntries(context: Context): List<Entry> {
        return PodcastPlaylists.getPlaylistEntries(context, PodcastPlaylists.DEFAULT_PLAYLIST_ID).map {
            Entry(
                id = it.id,
                title = it.title,
                description = it.description,
                imageUrl = it.imageUrl,
                audioUrl = it.audioUrl,
                pubDate = it.pubDate,
                durationMins = it.durationMins,
                podcastId = it.podcastId,
                podcastTitle = it.podcastTitle,
                savedAtMs = it.savedAtMs
            )
        }
    }

    fun isSaved(context: Context, episodeId: String): Boolean {
        return PodcastPlaylists.isSavedInDefaultPlaylist(context, episodeId)
    }

    fun toggleSaved(context: Context, episode: Episode, podcastTitle: String? = null): Boolean {
        return PodcastPlaylists.toggleDefaultPlaylist(context, episode, podcastTitle)
    }

    fun remove(context: Context, episodeId: String) {
        PodcastPlaylists.removeEpisode(context, PodcastPlaylists.DEFAULT_PLAYLIST_ID, episodeId)
    }

    /**
     * Persist an existing Entry object back into the saved-set. Removes any existing entry with
     * the same id first to avoid duplicates and preserves the original savedAtMs value so order
     * is retained in the UI.
     */
    fun saveEntry(context: Context, entry: Entry) {
        PodcastPlaylists.saveEntryToDefault(
            context,
            PodcastPlaylists.Entry(
                id = entry.id,
                title = entry.title,
                description = entry.description,
                imageUrl = entry.imageUrl,
                audioUrl = entry.audioUrl,
                pubDate = entry.pubDate,
                durationMins = entry.durationMins,
                podcastId = entry.podcastId,
                podcastTitle = entry.podcastTitle,
                savedAtMs = entry.savedAtMs
            )
        )
    }

    fun clearAll(context: Context) {
        PodcastPlaylists.clearAll(context)
    }

    /**
     * Trigger auto-download for a specific saved episode if the auto-download-saved setting is enabled.
     * This runs in the background and handles errors silently.
     */
    private fun triggerAutoDownloadForEpisode(context: Context, episode: Episode, podcastTitle: String) {
        if (!DownloadPreferences.isAutoDownloadSaved(context)) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Skip if already downloaded
                if (DownloadedEpisodes.isDownloaded(context, episode)) {
                    return@launch
                }
                
                // Download the episode (saved episodes are downloaded regardless of play status)
                EpisodeDownloadManager.downloadEpisode(context, episode, podcastTitle, isAutoDownload = true)
            } catch (_: Exception) {
                // Silently handle errors - this is a best-effort operation
            }
        }
    }

    /**
     * Trigger auto-download for all existing saved episodes.
     * This is useful when the user enables auto-download-saved for the first time.
     */
    fun triggerAutoDownloadForAllSaved(context: Context) {
        if (!DownloadPreferences.isAutoDownloadSaved(context)) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val savedEntries = getSavedEntries(context)
                if (savedEntries.isEmpty()) return@launch
                
                for (entry in savedEntries) {
                    // Skip if already downloaded
                    if (DownloadedEpisodes.isDownloaded(context, entry.id)) {
                        continue
                    }
                    
                    // Convert Entry to Episode and download (saved episodes are downloaded regardless of play status)
                    val episode = Episode(
                        id = entry.id,
                        title = entry.title,
                        description = entry.description,
                        audioUrl = entry.audioUrl,
                        imageUrl = entry.imageUrl,
                        pubDate = entry.pubDate,
                        durationMins = entry.durationMins,
                        podcastId = entry.podcastId
                    )
                    
                    try {
                        EpisodeDownloadManager.downloadEpisode(context, episode, entry.podcastTitle, isAutoDownload = true)
                    } catch (_: Exception) {
                        // Continue with next episode
                    }
                }
            } catch (_: Exception) {
                // Silently handle errors - this is a best-effort operation
            }
        }
    }
}
