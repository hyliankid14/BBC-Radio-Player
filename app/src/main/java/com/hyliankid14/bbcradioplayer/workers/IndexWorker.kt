package com.hyliankid14.bbcradioplayer.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hyliankid14.bbcradioplayer.PodcastRepository
import com.hyliankid14.bbcradioplayer.db.AppDatabase
import com.hyliankid14.bbcradioplayer.db.EpisodeFts
import com.hyliankid14.bbcradioplayer.db.PodcastFts
import com.hyliankid14.bbcradioplayer.util.TextNormalizer

class IndexWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        try {
            val repo = PodcastRepository(applicationContext)
            val db = AppDatabase.getInstance(applicationContext)
            val dao = db.ftsDao()

            // Fetch podcasts and index top N first for fast availability
            val podcasts = repo.fetchPodcasts(false)
            if (podcasts.isEmpty()) return Result.success()

            val toIndex = podcasts // we can limit here if desired

            // Build podcast FTS entries
            val podcastEntries = toIndex.map { p ->
                PodcastFts(
                    podcastId = p.id,
                    title = TextNormalizer.normalize(p.title),
                    description = TextNormalizer.normalize(p.description)
                )
            }
            dao.insertPodcasts(podcastEntries)

            // Index episodes in pages to avoid long blocking operations
            val total = toIndex.size
            var done = 0
            for (p in toIndex) {
                try {
                    // Remove previous episodes for this podcast
                    dao.deleteEpisodesForPodcast(p.id)
                    // Fetch paged (full fetch might be fine depending on feed size)
                    val eps = repo.fetchEpisodesIfNeeded(p)
                    if (eps.isEmpty()) {
                        done += 1
                        setProgressAsync(androidx.work.workDataOf("progress" to done, "total" to total))
                        continue
                    }
                    val episodeEntries = eps.map { e ->
                        EpisodeFts(
                            episodeId = e.id,
                            podcastId = p.id,
                            title = TextNormalizer.normalize(e.title),
                            description = TextNormalizer.normalize(e.description ?: "")
                        )
                    }
                    dao.insertEpisodes(episodeEntries)
                    done += 1
                    setProgressAsync(androidx.work.workDataOf("progress" to done, "total" to total))
                } catch (_: Exception) {
                    // best-effort per-podcast
                    done += 1
                    setProgressAsync(androidx.work.workDataOf("progress" to done, "total" to total))
                }
            }

            return Result.success()
        } catch (e: Exception) {
            android.util.Log.e("IndexWorker", "Indexing failed: ${e.message}")
            return Result.retry()
        }
    }
}
