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

    /**
     * Compute a monotonic overall percent for episode-indexing by giving each podcast an
     * equal slice of the 40..99% range. Designed to be lightweight (no extra network I/O)
     * and predictable so the UI never moves backwards when later podcasts add more
     * episodes than earlier ones.
     *
     * Parameters are 0-based podcastIndex, total podcastsCount, number processed in the
     * current podcast and the current podcast's episode count. Returns an int in [40,99].
     */
    internal fun computeOverallEpisodePercent(podcastIndex: Int, podcastsCount: Int, processedInPodcast: Int, podcastEpisodeCount: Int): Int {
        if (podcastsCount <= 0) return 100
        val base = 40.0
        val totalRange = 59.0 // map episodes to 40..99
        val weightPerPodcast = totalRange / podcastsCount.toDouble()
        val idx = podcastIndex.coerceIn(0, podcastsCount - 1)
        val perPodcastProgress = if (podcastEpisodeCount <= 0) 1.0 else (processedInPodcast.toDouble() / podcastEpisodeCount.toDouble()).coerceIn(0.0, 1.0)
        val pct = base + (idx * weightPerPodcast) + (perPodcastProgress * weightPerPodcast)
        return pct.toInt().coerceIn(40, 99)
    }

    /**
     * Compute a simple, monotonic per-podcast completion percent mapped into 40..99.
     * This advances only when a podcast is fully indexed so the UI progress moves
     * steadily from left->right as podcasts complete.
     */
    internal fun computePodcastCompletePercent(podcastIndexZeroBased: Int, podcastsCount: Int): Int {
        if (podcastsCount <= 0) return 100
        val base = 40
        val end = 99
        val idx = (podcastIndexZeroBased + 1).coerceIn(1, podcastsCount) // 1..N
        val pct = base + ((idx * (end - base)) / podcastsCount)
        return pct.coerceIn(base, end)
    }

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
                    // Fetch episodes for this podcast and report monotonic overall-percent updates
                    val eps = try { repo.fetchEpisodesIfNeeded(p) } catch (e: Exception) { emptyList() }
                    if (eps.isEmpty()) {
                        // No episodes discovered — treat this podcast as complete and emit the
                        // per-podcast completion percent (monotonic mapping).
                        val completedPct = computePodcastCompletePercent(i, podcasts.size)
                        onProgress("Indexed episodes for: ${p.title}", completedPct, true)
                        continue
                    }

                    // Count discovered episodes for diagnostics only (do NOT use for UI percent)
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

                            // Report monotonic overall episode percent based on processedInPodcast
                            val overallPct = computeOverallEpisodePercent(i, podcasts.size, inserted, eps.size)
                            onProgress("Indexing episodes for: ${p.title}", overallPct, true)

                            // Give SQLite a chance to service other threads / GC
                            try { Thread.yield() } catch (_: Throwable) {}
                        }

                        // When we've finished inserting *all* episodes for this podcast, advance the
                        // overall episode progress to the podcast-complete mark so the UI progress
                        // bar reaches the per-podcast completion point.
                        val completedPct = computePodcastCompletePercent(i, podcasts.size)
                        onProgress("Indexed episodes for: ${p.title}", completedPct, true)
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
