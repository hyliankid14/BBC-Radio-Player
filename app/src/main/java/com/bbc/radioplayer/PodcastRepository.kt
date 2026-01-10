package com.bbc.radioplayer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class PodcastRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("podcasts", Context.MODE_PRIVATE)
    private val cacheKey = "podcast_cache"
    private val cacheTimeKey = "podcast_cache_time"
    private val cacheTTL = 24 * 60 * 60 * 1000 // 24 hours in milliseconds

    suspend fun fetchPodcasts(forceRefresh: Boolean = false): List<Podcast> = withContext(Dispatchers.IO) {
        try {
            val cachedData = if (!forceRefresh) getCachedPodcasts() else null
            if (cachedData != null) {
                Log.d("PodcastRepository", "Returning cached podcasts")
                return@withContext cachedData
            }

            Log.d("PodcastRepository", "Fetching podcasts from BBC OPML feed")
            val podcasts = OPMLParser.fetchAndParseOPML(
                "https://www.bbc.co.uk/radio/opml/bbc_podcast_opml.xml"
            )

            if (podcasts.isNotEmpty()) {
                cachePodcasts(podcasts)
                podcasts
            } else {
                // Try cache as fallback
                getCachedPodcasts() ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("PodcastRepository", "Error fetching podcasts", e)
            getCachedPodcasts() ?: emptyList()
        }
    }

    suspend fun fetchEpisodes(podcast: Podcast): List<Episode> = withContext(Dispatchers.IO) {
        try {
            Log.d("PodcastRepository", "Fetching episodes for ${podcast.title}")
            RSSParser.fetchAndParseRSS(podcast.rssUrl, podcast.id)
        } catch (e: Exception) {
            Log.e("PodcastRepository", "Error fetching episodes", e)
            emptyList()
        }
    }

    fun filterPodcasts(podcasts: List<Podcast>, filter: PodcastFilter): List<Podcast> {
        return podcasts.filter { podcast ->
            val genreMatch = if (filter.genres.isEmpty()) true
            else podcast.genres.any { it in filter.genres }
            val durationMatch = podcast.typicalDurationMins in filter.minDuration..filter.maxDuration
            genreMatch && durationMatch
        }
    }

    fun getUniqueGenres(podcasts: List<Podcast>): List<String> {
        return podcasts
            .flatMap { it.genres }
            .distinct()
            .sorted()
    }

    private fun cachePodcasts(podcasts: List<Podcast>) {
        try {
            val json = podcasts.joinToString(",") { podcast ->
                "\"${podcast.id}\":\"${podcast.title}|${podcast.rssUrl}|${podcast.imageUrl}|${podcast.genres.joinToString(";")}|${podcast.typicalDurationMins}\""
            }
            prefs.edit().apply {
                putString(cacheKey, "{$json}")
                putLong(cacheTimeKey, System.currentTimeMillis())
                apply()
            }
            Log.d("PodcastRepository", "Cached ${podcasts.size} podcasts")
        } catch (e: Exception) {
            Log.e("PodcastRepository", "Error caching podcasts", e)
        }
    }

    private fun getCachedPodcasts(): List<Podcast>? {
        return try {
            val cacheTime = prefs.getLong(cacheTimeKey, 0)
            if (System.currentTimeMillis() - cacheTime > cacheTTL) {
                Log.d("PodcastRepository", "Cache expired")
                return null
            }

            val json = prefs.getString(cacheKey, null) ?: return null
            val podcasts = mutableListOf<Podcast>()
            val entries = json.substring(1, json.length - 1).split(",")
            for (entry in entries) {
                val parts = entry.split("\":\"")
                if (parts.size == 2) {
                    val id = parts[0].substring(1)
                    val data = parts[1].substring(0, parts[1].length - 1).split("|")
                    if (data.size >= 4) {
                        val genres = data[3].split(";").filter { it.isNotEmpty() }
                        val duration = data.getOrNull(4)?.toIntOrNull() ?: 0
                        podcasts.add(
                            Podcast(
                                id = id,
                                title = data[0],
                                description = "",
                                rssUrl = data[1],
                                htmlUrl = "",
                                imageUrl = data[2],
                                genres = genres,
                                typicalDurationMins = duration
                            )
                        )
                    }
                }
            }
            if (podcasts.isNotEmpty()) {
                Log.d("PodcastRepository", "Loaded ${podcasts.size} podcasts from cache")
                return podcasts
            }
            null
        } catch (e: Exception) {
            Log.e("PodcastRepository", "Error reading cache", e)
            null
        }
    }
}
