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

    suspend fun reindexAll(context: Context, onProgress: (String, Int, Boolean) -> Unit = { _, _, _ -> }) {
        withContext(Dispatchers.IO) {
            try {
                onProgress("Starting index...", -1, false)
                onProgress("Fetching podcasts...", 0, false)
                val repo = PodcastRepository(context)
                val podcasts = repo.fetchPodcasts(forceRefresh = true)
                if (podcasts.isEmpty()) {
                    onProgress("No podcasts to index", 100, false)
                    return@withContext
                }

                onProgress("Indexing ${podcasts.size} podcasts...", 5, false)
                val store = IndexStore.getInstance(context)
                store.replaceAllPodcasts(podcasts)

                // Fetch episodes for podcasts (best-effort); aggregate into a single list
                val allEpisodes = mutableListOf<com.hyliankid14.bbcradioplayer.Episode>()
                var count = 0
                for ((i, p) in podcasts.withIndex()) {
                    // map podcast-fetch progress to 5..40%
                    val fetchPct = 5 + ((i + 1) * 35 / podcasts.size)
                    onProgress("Fetching episodes for: ${p.title}", fetchPct, true)
                    val eps = repo.fetchEpisodesIfNeeded(p)
                    allEpisodes.addAll(eps)
                    count += eps.size
                }

                onProgress("Indexing $count episodes...", 40, true)

                // Index episodes and map episode progress into 40..99%
                store.replaceAllEpisodes(allEpisodes) { processed, total ->
                    val percent = if (total <= 0) 99 else 40 + (processed * 59 / total)
                    onProgress("Indexing episodes", percent.coerceIn(0, 99), true)
                }

                onProgress("Index complete: ${podcasts.size} podcasts, $count episodes", 100, false)
                Log.d(TAG, "Reindex complete: podcasts=${podcasts.size}, episodes=$count")
            } catch (e: Exception) {
                Log.e(TAG, "Reindex failed", e)
                onProgress("Index failed: ${e.message}", -1, false)
            }
        }
    }
}
