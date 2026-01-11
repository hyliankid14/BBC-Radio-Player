package com.hyliankid14.bbcradioplayer

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PodcastsFragment : Fragment() {
    private lateinit var repository: PodcastRepository
    private lateinit var adapter: PodcastAdapter
    private var allPodcasts: List<Podcast> = emptyList()
    private var currentFilter = PodcastFilter()
    private var searchQuery = ""
    private val fragmentScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_podcasts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = PodcastRepository(requireContext())

        val recyclerView: RecyclerView = view.findViewById(R.id.podcasts_recycler)
        val searchEditText: EditText = view.findViewById(R.id.search_podcast_edittext)
        val genreSpinner: Spinner = view.findViewById(R.id.genre_filter_spinner)
        val resetButton: android.widget.Button = view.findViewById(R.id.reset_filters_button)
        val loadingIndicator: ProgressBar = view.findViewById(R.id.loading_progress)
        val emptyState: TextView = view.findViewById(R.id.empty_state_text)
        val filtersContainer: View = view.findViewById(R.id.filters_container)

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                applyFilters(loadingIndicator, emptyState, recyclerView)
            }
        })

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = PodcastAdapter(requireContext(), onPodcastClick = { podcast ->
            android.util.Log.d("PodcastsFragment", "onPodcastClick triggered for: ${podcast.title}")
            val detailFragment = PodcastDetailFragment().apply {
                arguments = Bundle().apply {
                    putParcelable("podcast", podcast)
                }
            }
            parentFragmentManager.beginTransaction().apply {
                replace(R.id.fragment_container, detailFragment)
                addToBackStack(null)
                commit()
            }
        }, onOpenPlayer = {
            startActivity(android.content.Intent(requireContext(), NowPlayingActivity::class.java))
        })
        recyclerView.adapter = adapter

        // Subscribed podcasts are shown in Favorites section, not here

        // Duration filter removed

        resetButton.setOnClickListener {
            searchQuery = ""
            searchEditText.text.clear()
            currentFilter = PodcastFilter()
            genreSpinner.setSelection(0)
            applyFilters(loadingIndicator, emptyState, recyclerView)
        }

        // Hide filters on scroll
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    filtersContainer.visibility = View.GONE
                } else if (dy < 0) {
                    filtersContainer.visibility = View.VISIBLE
                }
            }
        })

        loadPodcasts(loadingIndicator, emptyState, recyclerView, genreSpinner)
    }

    private fun loadPodcasts(
        loadingIndicator: ProgressBar,
        emptyState: TextView,
        recyclerView: RecyclerView,
        genreSpinner: Spinner
    ) {
        loadingIndicator.visibility = View.VISIBLE
        emptyState.text = "Loading podcasts..."
        fragmentScope.launch {
            try {
                allPodcasts = repository.fetchPodcasts()
                android.util.Log.d("PodcastsFragment", "Loaded ${allPodcasts.size} podcasts")

                if (allPodcasts.isEmpty()) {
                    emptyState.text = "No podcasts found. Check your connection and try again."
                    emptyState.visibility = View.VISIBLE
                    loadingIndicator.visibility = View.GONE
                    return@launch
                }

                val genres = listOf("All Genres") + repository.getUniqueGenres(allPodcasts)
                val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, genres)
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                genreSpinner.adapter = spinnerAdapter

                genreSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        currentFilter = if (position == 0) {
                            currentFilter.copy(genres = emptySet())
                        } else {
                            currentFilter.copy(genres = setOf(genres[position]))
                        }
                        applyFilters(loadingIndicator, emptyState, recyclerView)
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }

                // Sort by most recent update when starting
                val updates = withContext(Dispatchers.IO) { repository.fetchLatestUpdates(allPodcasts) }
                val sorted = if (updates.isNotEmpty()) {
                    allPodcasts.sortedByDescending { updates[it.id] ?: 0L }
                } else allPodcasts
                allPodcasts = sorted
                applyFilters(loadingIndicator, emptyState, recyclerView)
                loadingIndicator.visibility = View.GONE
            } catch (e: Exception) {
                android.util.Log.e("PodcastsFragment", "Error loading podcasts", e)
                emptyState.text = "Error loading podcasts: ${e.message}"
                emptyState.visibility = View.VISIBLE
                loadingIndicator.visibility = View.GONE
            }
        }
    }

    private fun applyFilters(
        loadingIndicator: ProgressBar,
        emptyState: TextView,
        recyclerView: RecyclerView
    ) {
        val effectiveFilter = currentFilter.copy(searchQuery = searchQuery)
        val filtered = repository.filterPodcasts(allPodcasts, effectiveFilter)
        
        // Filter out subscribed podcasts (shown in Favorites instead)
        val subscribedIds = PodcastSubscriptions.getSubscribedIds(requireContext())
        val unsubscribed = filtered.filter { it.id !in subscribedIds }
        adapter.updatePodcasts(unsubscribed)

        if (unsubscribed.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fragmentScope.coroutineContext[Job]?.cancel()
    }
}
