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

    fun setActiveSearch(query: String?) {
        _activeSearchQuery.postValue(query)
    }

    fun clearActiveSearch() {
        _activeSearchQuery.postValue(null)
    }
}