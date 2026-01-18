package com.hyliankid14.bbcradioplayer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ViewModel to hold transient UI state for PodcastsFragment, e.g., the active persisted search query.
 */
class PodcastsViewModel : ViewModel() {
    private val _activeSearchQuery = MutableLiveData<String?>(null)
    val activeSearchQuery: LiveData<String?> = _activeSearchQuery

    // Simple in-memory cache to hold the last search results for a query so we can reuse them
    data class SearchCache(
        val query: String,
        val titleMatches: List<Podcast>,
        val descMatches: List<Podcast>,
        val episodeMatches: List<Pair<Episode, Podcast>>
    )

    @Volatile
    private var cachedSearch: SearchCache? = null

    fun setActiveSearch(query: String?) {
        // Use synchronous set so callers on the main (UI) thread can read the updated
        // value immediately (avoids races when applyFilters reads the LiveData right away).
        _activeSearchQuery.value = query
    }

    fun clearActiveSearch() {
        _activeSearchQuery.value = null
    }

    fun getCachedSearch(): SearchCache? = cachedSearch
    fun setCachedSearch(cache: SearchCache?) { cachedSearch = cache }
    fun clearCachedSearch() { cachedSearch = null }
}