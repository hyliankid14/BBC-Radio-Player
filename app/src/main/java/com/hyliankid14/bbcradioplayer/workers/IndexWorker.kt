package com.hyliankid14.bbcradioplayer.workers

import android.content.Context
import android.util.Log
import com.hyliankid14.bbcradioplayer.Podcast
import com.hyliankid14.bbcradioplayer.db.IndexStore
import com.hyliankid14.bbcradioplayer.PodcastRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * IndexWorker: performs an on-disk FTS index build for podcasts and episodes using SQLite FTS4.
 * Implemented without Room to avoid KAPT dependency issues.
 */
object IndexWorker {
    private const val TAG = "IndexWorker"

    suspend fun reindexAll(context: Context, onProgress: (String) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            try {
                onProgress("Fetching podcasts...")
                val repo = PodcastRepository(context)
                val podcasts = repo.fetchPodcasts(forceRefresh = true)
                onProgress("Indexing ${podcasts.size} podcasts...")
                val store = IndexStore.getInstance(context)
                store.replaceAllPodcasts(podcasts)

                // Fetch episodes for podcasts (best-effort); aggregate into a single list
                val allEpisodes = mutableListOf<com.hyliankid14.bbcradioplayer.Episode>()
                var count = 0
                for (p in podcasts) {
                    onProgress("Fetching episodes for: ${p.title}")
                    val eps = repo.fetchEpisodesIfNeeded(p)
                    allEpisodes.addAll(eps)
                    count += eps.size
                }

                onProgress("Indexing $count episodes...")
                store.replaceAllEpisodes(allEpisodes)

                onProgress("Index complete: ${podcasts.size} podcasts, $count episodes")
                Log.d(TAG, "Reindex complete: podcasts=${podcasts.size}, episodes=$count")
            } catch (e: Exception) {
                Log.e(TAG, "Reindex failed", e)
                onProgress("Index failed: ${e.message}")
            }
        }
    }
}
