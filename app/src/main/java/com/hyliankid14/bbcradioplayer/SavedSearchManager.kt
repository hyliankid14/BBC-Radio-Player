package com.hyliankid14.bbcradioplayer

import android.content.Context
import com.hyliankid14.bbcradioplayer.db.IndexStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SavedSearchManager {
    suspend fun checkForUpdates(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val searches = SavedSearchesPreference.getSavedSearches(context)
                    .filter { it.notificationsEnabled && it.query.isNotBlank() }
                if (searches.isEmpty()) return@withContext

                val index = IndexStore.getInstance(context)
                if (!index.hasAnyEpisodes()) return@withContext

                val repo = PodcastRepository(context)
                val allPodcasts = try { repo.fetchPodcasts(forceRefresh = true) } catch (_: Exception) { emptyList() }
                if (allPodcasts.isEmpty()) return@withContext

                for (search in searches) {
                    val filter = PodcastFilter(
                        genres = search.genres.toSet(),
                        minDuration = search.minDuration,
                        maxDuration = search.maxDuration,
                        searchQuery = ""
                    )
                    val allowed = repo.filterPodcasts(allPodcasts, filter).map { it.id }.toSet()
                    if (allowed.isEmpty()) continue

                    val matches = try { index.searchEpisodes(search.query, 200) } catch (_: Exception) { emptyList() }
                    if (matches.isEmpty()) {
                        SavedSearchesPreference.updateLastSeenEpisodeIds(context, search.id, emptyList())
                        continue
                    }

                    val filtered = matches.filter { allowed.contains(it.podcastId) }
                    val ids = filtered.map { it.episodeId }.distinct().take(50)

                    if (ids.isEmpty()) {
                        SavedSearchesPreference.updateLastSeenEpisodeIds(context, search.id, emptyList())
                        continue
                    }

                    val lastSeen = search.lastSeenEpisodeIds.toSet()
                    val newIds = if (lastSeen.isEmpty()) ids else ids.filterNot { lastSeen.contains(it) }

                    if (newIds.isNotEmpty()) {
                        val exampleTitle = filtered.firstOrNull { newIds.contains(it.episodeId) }?.title ?: ""
                        SavedSearchNotifier.notifyNewMatches(context, search, exampleTitle, newIds.size)
                    }

                    SavedSearchesPreference.updateLastSeenEpisodeIds(context, search.id, ids)
                }
            } catch (_: Exception) {
                // best-effort background check
            }
        }
    }
}
