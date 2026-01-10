package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.*

class PodcastRepository(private val context: Context) {
    private val cacheFile = File(context.cacheDir, "podcasts_cache.json")
    private val cacheTTL = 24 * 60 * 60 * 1000 // 24 hours in milliseconds

    suspend fun fetchPodcasts(forceRefresh: Boolean = false): List<Podcast> = withContext(Dispatchers.IO) {
        try {
            val cachedData = if (!forceRefresh) getCachedPodcasts() else null
            if (cachedData != null && cachedData.isNotEmpty()) {
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
            val searchMatch = if (filter.searchQuery.isEmpty()) true
            else podcast.title.contains(filter.searchQuery, ignoreCase = true) || 
                 podcast.description.contains(filter.searchQuery, ignoreCase = true)
            genreMatch && durationMatch && searchMatch
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
            val jsonArray = JSONArray()
            podcasts.forEach { podcast ->
                val jsonObj = JSONObject().apply {
                    put("id", podcast.id)
                    put("title", podcast.title)
                    put("description", podcast.description)
                    put("rssUrl", podcast.rssUrl)
                    put("htmlUrl", podcast.htmlUrl)
                    put("imageUrl", podcast.imageUrl)
                    put("typicalDurationMins", podcast.typicalDurationMins)
                    put("genres", JSONArray(podcast.genres))
                }
                jsonArray.put(jsonObj)
            }
            
            val rootObj = JSONObject()
            rootObj.put("timestamp", System.currentTimeMillis())
            rootObj.put("data", jsonArray)
            
            cacheFile.writeText(rootObj.toString())
            Log.d("PodcastRepository", "Cached ${podcasts.size} podcasts")
        } catch (e: Exception) {
            Log.e("PodcastRepository", "Error caching podcasts", e)
        }
    }

    private fun getCachedPodcasts(): List<Podcast>? {
        if (!cacheFile.exists()) return null
        
        return try {
            val content = cacheFile.readText()
            if (content.isEmpty()) return null
            
            val rootObj = JSONObject(content)
            val timestamp = rootObj.optLong("timestamp", 0)
            
            if (System.currentTimeMillis() - timestamp > cacheTTL) {
                Log.d("PodcastRepository", "Cache expired")
                return null
            }
            
            val jsonArray = rootObj.getJSONArray("data")
            val podcasts = mutableListOf<Podcast>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val genresJson = obj.getJSONArray("genres")
                val genres = mutableListOf<String>()
                for (j in 0 until genresJson.length()) {
                    genres.add(genresJson.getString(j))
                }
                
                podcasts.add(Podcast(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    description = obj.optString("description", ""),
                    rssUrl = obj.getString("rssUrl"),
                    htmlUrl = obj.optString("htmlUrl", ""),
                    imageUrl = obj.optString("imageUrl", ""),
                    genres = genres,
                    typicalDurationMins = obj.optInt("typicalDurationMins", 0)
                ))
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
