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
    private val updatesCacheFile = File(context.cacheDir, "podcast_updates_cache.json")
    private val updatesCacheTTL = 6 * 60 * 60 * 1000 // 6 hours

    // In-memory cache of fetched episode metadata to support searching episode titles/descriptions
    // Prefill this in the background when the podcast list is loaded so searches don't trigger network on each keystroke
    private val episodesCache: MutableMap<String, List<Episode>> = mutableMapOf()

    // Lowercased index for episodes for fast, case-insensitive phrase checks. Kept in same order as episodesCache
    private val episodesIndex: MutableMap<String, List<Pair<String, String>>> = mutableMapOf()

    // Lightweight lowercased index for fast case-insensitive podcast title/description checks
    private val podcastSearchIndex: MutableMap<String, Pair<String, String>> = mutableMapOf()

    private fun indexPodcasts(podcasts: List<Podcast>) {
        podcasts.forEach { p ->
            podcastSearchIndex[p.id] = p.title.lowercase(Locale.getDefault()) to p.description.lowercase(Locale.getDefault())
        }
    }

    private fun containsPhraseOrAllTokens(textLower: String, queryLower: String): Boolean {
        // Normalize both text and query by removing non-alphanumeric characters (retain unicode letters/digits)
        val normalize = { s: String -> s.replace(Regex("[^\\p{L}0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim() }
        val textNorm = normalize(textLower)
        val queryNorm = normalize(queryLower)

        if (textNorm.contains(queryNorm)) return true
        val tokens = queryNorm.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size <= 1) return false
        return tokens.all { textNorm.contains(it) }
    }

    fun podcastMatches(podcast: Podcast, queryLower: String): Boolean {
        val pair = podcastSearchIndex[podcast.id]
        if (pair != null) {
            val (titleLower, descLower) = pair
            if (containsPhraseOrAllTokens(titleLower, queryLower)) return true
            if (containsPhraseOrAllTokens(descLower, queryLower)) return true
            return false
        }
        // Fallback
        val qTokens = queryLower.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (qTokens.size > 1) {
            val tl = podcast.title.lowercase(Locale.getDefault())
            val dl = podcast.description.lowercase(Locale.getDefault())
            return containsPhraseOrAllTokens(tl, queryLower) || containsPhraseOrAllTokens(dl, queryLower)
        }
        return podcast.title.contains(queryLower, ignoreCase = true) || podcast.description.contains(queryLower, ignoreCase = true)
    }

    /**
     * Return whether the query matches podcast title or description, or null if none. Returns
     * "title" or "description" to let callers prioritise where it matched.
     */
    fun podcastMatchKind(podcast: Podcast, queryLower: String): String? {
        val pair = podcastSearchIndex[podcast.id]
        if (pair != null) {
            val (titleLower, descLower) = pair
            if (containsPhraseOrAllTokens(titleLower, queryLower)) return "title"
            if (containsPhraseOrAllTokens(descLower, queryLower)) return "description"
            return null
        }
        val tl = podcast.title.lowercase(Locale.getDefault())
        val dl = podcast.description.lowercase(Locale.getDefault())
        if (containsPhraseOrAllTokens(tl, queryLower)) return "title"
        if (containsPhraseOrAllTokens(dl, queryLower)) return "description"
        return null
    }

    /**
     * Search cached episodes for a podcast quickly using precomputed lowercase index.
     * Returns up to maxResults Episode objects (keeps original Episode objects from cache).
     */
    fun searchCachedEpisodes(podcastId: String, queryLower: String, maxResults: Int = 3): List<Episode> {
        val eps = episodesCache[podcastId] ?: return emptyList()
        val idx = episodesIndex[podcastId] ?: return emptyList()
        val results = mutableListOf<Episode>()
        for (i in idx.indices) {
            val (titleLower, descLower) = idx[i]
            if (containsPhraseOrAllTokens(titleLower, queryLower) || containsPhraseOrAllTokens(descLower, queryLower)) {
                results.add(eps[i])
                if (results.size >= maxResults) break
            }
        }
        return results
    }

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

    suspend fun fetchEpisodesPaged(podcast: Podcast, startIndex: Int, count: Int): List<Episode> = withContext(Dispatchers.IO) {
        try {
            Log.d("PodcastRepository", "Fetching episodes page for ${podcast.title} start=$startIndex count=$count")
            // Fetch the full feed and page from newest -> oldest so page 0 is the most recent episodes.
            val all = RSSParser.fetchAndParseRSS(podcast.rssUrl, podcast.id)
            if (all.isEmpty()) return@withContext emptyList()

            fun parsePubDate(raw: String): Long {
                val patterns = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy")
                for (pattern in patterns) {
                    try {
                        val t = java.text.SimpleDateFormat(pattern, java.util.Locale.US).parse(raw)?.time
                        if (t != null) return t
                    } catch (_: Exception) { }
                }
                return 0L
            }

            val sorted = all.sortedByDescending { parsePubDate(it.pubDate) }
            val from = startIndex.coerceAtLeast(0)
            val to = kotlin.math.min(sorted.size, startIndex + count)
            if (from >= to) return@withContext emptyList()
            return@withContext sorted.subList(from, to)
        } catch (e: Exception) {
            Log.e("PodcastRepository", "Error fetching paged episodes", e)
            emptyList()
        }
    }

    /**
     * Prefetch episode metadata for the provided podcasts and store in-memory.
     * This is intentionally best-effort and failures are silently ignored so we don't
     * surface network errors to the filter/search flow.
     */
    suspend fun prefetchEpisodesForPodcasts(podcasts: List<Podcast>, limit: Int = Int.MAX_VALUE) = withContext(Dispatchers.IO) {
        // Best-effort prefetch for a limited set of podcasts (default: all). Failures are ignored.
        podcasts.take(limit).forEach { p ->
            if (episodesCache.containsKey(p.id)) return@forEach
            try {
                val eps = RSSParser.fetchAndParseRSS(p.rssUrl, p.id)
                if (eps.isNotEmpty()) {
                    episodesCache[p.id] = eps
                    // Build lowercased index for quick phrase lookups
                    episodesIndex[p.id] = eps.map { it.title.lowercase(Locale.getDefault()) to it.description.lowercase(Locale.getDefault()) }
                }
            } catch (e: Exception) {
                Log.w("PodcastRepository", "Failed to prefetch episodes for ${p.title}: ${e.message}")
            }
        }
    }

    /**
     * Return cached episodes for a podcast if available; null if not cached yet.
     */
    fun getEpisodesFromCache(podcastId: String): List<Episode>? {
        return episodesCache[podcastId]
    }

    /**
     * Fetch episodes for a podcast if not already cached. Returns cached value immediately when present
     * and otherwise fetches from network and caches the result. This is intended for use from a background
     * coroutine so it may perform network I/O.
     */
    suspend fun fetchEpisodesIfNeeded(podcast: Podcast): List<Episode> = withContext(Dispatchers.IO) {
        val cached = episodesCache[podcast.id]
        if (!cached.isNullOrEmpty()) {
            Log.d("PodcastRepository", "Using cached episodes for ${podcast.title}: ${cached.size} items")
            return@withContext cached
        }
        try {
            Log.d("PodcastRepository", "Fetching episodes for ${podcast.title}")
            val eps = RSSParser.fetchAndParseRSS(podcast.rssUrl, podcast.id)
            if (eps.isNotEmpty()) {
                episodesCache[podcast.id] = eps
                episodesIndex[podcast.id] = eps.map { it.title.lowercase(Locale.getDefault()) to it.description.lowercase(Locale.getDefault()) }
                Log.d("PodcastRepository", "Fetched ${eps.size} episodes for ${podcast.title}")
            }
            return@withContext eps
        } catch (e: Exception) {
            Log.w("PodcastRepository", "fetchEpisodesIfNeeded failed for ${podcast.title}: ${e.message}")
            return@withContext emptyList()
        }
    }

    suspend fun fetchLatestUpdates(podcasts: List<Podcast>): Map<String, Long> = withContext(Dispatchers.IO) {
        try {
            // Try cache first
            val cached = readUpdatesCache()
            val now = System.currentTimeMillis()
            val result = mutableMapOf<String, Long>()
            podcasts.forEach { p ->
                val cachedVal = cached[p.id]
                if (cachedVal != null && (now - cachedVal.second) < updatesCacheTTL) {
                    result[p.id] = cachedVal.first
                } else {
                    val latest = RSSParser.fetchLatestPubDateEpoch(p.rssUrl)
                    if (latest != null) {
                        result[p.id] = latest
                        cached[p.id] = latest to now
                    }
                }
            }
            writeUpdatesCache(cached)
            result
        } catch (e: Exception) {
            Log.e("PodcastRepository", "Error fetching latest updates", e)
            emptyMap()
        }
    }

    private fun readUpdatesCache(): MutableMap<String, Pair<Long, Long>> {
        if (!updatesCacheFile.exists()) return mutableMapOf()
        return try {
            val obj = JSONObject(updatesCacheFile.readText())
            val map = mutableMapOf<String, Pair<Long, Long>>()
            val data = obj.optJSONObject("data") ?: JSONObject()
            data.keys().forEach { key ->
                val o = data.optJSONObject(key) ?: return@forEach
                map[key] = (o.optLong("epoch", 0L)) to (o.optLong("ts", 0L))
            }
            map
        } catch (_: Exception) { mutableMapOf() }
    }

    private fun writeUpdatesCache(data: Map<String, Pair<Long, Long>>) {
        try {
            val root = JSONObject()
            val d = JSONObject()
            data.forEach { (k,v) ->
                val o = JSONObject()
                o.put("epoch", v.first)
                o.put("ts", v.second)
                d.put(k, o)
            }
            root.put("data", d)
            updatesCacheFile.writeText(root.toString())
        } catch (_: Exception) {}
    }

    fun filterPodcasts(podcasts: List<Podcast>, filter: PodcastFilter): List<Podcast> {
        // First apply hard filters (genres + duration)
        val baseFiltered = podcasts.filter { podcast ->
            val genreMatch = if (filter.genres.isEmpty()) true
            else podcast.genres.any { it in filter.genres }
            val durationMatch = podcast.typicalDurationMins in filter.minDuration..filter.maxDuration
            genreMatch && durationMatch
        }

        // If there's no search query, return the base filtered list (ensure index built)
        val q = filter.searchQuery.trim()
        if (q.isEmpty()) {
            indexPodcasts(baseFiltered)
            return baseFiltered
        }

        // Ensure we have indexed data for fast checks
        indexPodcasts(baseFiltered)
        val qLower = q.lowercase(Locale.getDefault())

        // Prioritise podcasts whose TITLE matches the query, then podcast DESCRIPTION,
        // then EPISODE titles, then EPISODE descriptions.
        val titleMatches = mutableListOf<Podcast>()
        val descMatches = mutableListOf<Podcast>()
        val epTitleMatches = mutableListOf<Podcast>()
        val epDescMatches = mutableListOf<Podcast>()

        for (p in baseFiltered) {
            if (podcastMatches(p, qLower)) {
                // determine whether it matched title or description first
                val pair = podcastSearchIndex[p.id]
                if (pair != null) {
                    if (pair.first.contains(qLower)) titleMatches.add(p)
                    else descMatches.add(p)
                    continue
                } else {
                    // fallback to original contains checks
                    if (p.title.contains(q, ignoreCase = true)) {
                        titleMatches.add(p)
                        continue
                    }
                    if (p.description.contains(q, ignoreCase = true)) {
                        descMatches.add(p)
                        continue
                    }
                }
            }

            // Check episode metadata cache for matches. If not cached yet, skip episode matching so we don't block.
            val episodes = episodesCache[p.id]
            val episodeIdx = episodesIndex[p.id]
            if (!episodeIdx.isNullOrEmpty()) {
                if (episodeIdx.any { (titleLower, _) -> containsPhraseOrAllTokens(titleLower, qLower) }) {
                    epTitleMatches.add(p)
                    continue
                }
                if (episodeIdx.any { (_, descLower) -> containsPhraseOrAllTokens(descLower, qLower) }) {
                    epDescMatches.add(p)
                    continue
                }
            } else if (!episodes.isNullOrEmpty()) {
                // Fallback: older cached episodes without precomputed index
                if (episodes.any { containsPhraseOrAllTokens(it.title.lowercase(Locale.getDefault()), qLower) }) {
                    epTitleMatches.add(p)
                    continue
                }
                if (episodes.any { containsPhraseOrAllTokens((it.description ?: "").lowercase(Locale.getDefault()), qLower) }) {
                    epDescMatches.add(p)
                    continue
                }
            }
        }

        // Keep the relative order within each bucket same as the source order
        return titleMatches + descMatches + epTitleMatches + epDescMatches
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
