package com.hyliankid14.bbcradioplayer.workers

import android.content.Context
import android.util.Log
import com.hyliankid14.bbcradioplayer.Podcast
import com.hyliankid14.bbcradioplayer.db.IndexStore
import com.hyliankid14.bbcradioplayer.PodcastRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

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

                // Fetch & index episodes per-podcast (streamed) to avoid building a huge in-memory list.
                var totalEpisodesDiscovered = 0
                var processedEpisodes = 0

                onProgress("Indexing episodes (streamed)...", 40, true)

                // Wipe episode FTS once up-front (append in small batches below)
                try {
                    // We still use the IndexStore's safe append API so each podcast's episodes are
                    // inserted in bounded-size transactions.
                    store.replaceAllEpisodes(emptyList()) // this triggers a fast table DELETE then returns
                } catch (e: Exception) {
                    // ignore - fall back to append behavior below
                }

                for ((i, p) in podcasts.withIndex()) {
                    if (!isActive) break
                    val fetchPct = 5 + ((i + 1) * 35 / podcasts.size)
                    onProgress("Fetching episodes for: ${p.title}", fetchPct, true)

                    val eps = try { repo.fetchEpisodesIfNeeded(p) } catch (e: Exception) { emptyList() }
                    if (eps.isEmpty()) continue

                    // Count discovered episodes so progress can be reported (we don't retain them beyond this loop)
                    totalEpisodesDiscovered += eps.size

                    // Enrich each episode's description with the podcast title (helps joint queries)
                    val enriched = eps.map { ep -> ep.copy(description = listOfNotNull(ep.description, p.title).joinToString(" ")) }

                    // Insert in bounded-size batches via IndexStore.appendEpisodesBatch
                    try {
                        var inserted = 0
                        val batchSize = 500
                        val chunks = enriched.chunked(batchSize)
                        for (chunk in chunks) {
                            if (!isActive) break
                            val added = try { store.appendEpisodesBatch(chunk) } catch (oom: OutOfMemoryError) {
                                // try smaller chunks if we hit memory pressure
                                var fallback = 0
                                for (small in chunk.chunked(50)) fallback += store.appendEpisodesBatch(small)
                                fallback
                            }
                            inserted += added
                            processedEpisodes += added

                            // Map progress to 40..99% (totalEpisodesDiscovered grows as we discover podcasts)
                            val percent = if (totalEpisodesDiscovered <= 0) 40 else 40 + (processedEpisodes * 59 / totalEpisodesDiscovered)
                            onProgress("Indexing episodes", percent.coerceIn(40, 99), true)

                            // Give SQLite a chance to service other threads / GC
                            try { Thread.yield() } catch (_: Throwable) {}
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to append episodes for ${p.id}: ${e.message}")
                    }
                }

                // final progress report (best-effort)
                onProgress("Index complete: ${podcasts.size} podcasts, $processedEpisodes episodes", 100, false)
                Log.d(TAG, "Reindex complete: podcasts=${podcasts.size}, episodes=$processedEpisodes")
                try {
                    store.setLastReindexTime(System.currentTimeMillis())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to persist last reindex time: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reindex failed", e)
                onProgress("Index failed: ${e.message}", -1, false)
            }
        }
    }
}
