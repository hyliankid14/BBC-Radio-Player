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
import java.util.Locale

class PodcastsFragment : Fragment() {
    private lateinit var repository: PodcastRepository
    private lateinit var adapter: PodcastAdapter
    private var allPodcasts: List<Podcast> = emptyList()
    private var currentFilter = PodcastFilter()
    private var currentSort: String = "Most recent"
    private var cachedUpdates: Map<String, Long> = emptyMap()
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
        val sortSpinner: Spinner = view.findViewById(R.id.sort_spinner)
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

        // Previously we hid filters on scroll which caused flicker. Let the filters scroll with content inside the NestedScrollView.
        // If desired we show a FAB (in podcasts fragment we rely on the system back-to-top behaviour or user scrolling).

        loadPodcasts(loadingIndicator, emptyState, recyclerView, genreSpinner, sortSpinner)
    }

    private fun loadPodcasts(
        loadingIndicator: ProgressBar,
        emptyState: TextView,
        recyclerView: RecyclerView,
        genreSpinner: Spinner,
        sortSpinner: Spinner
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

                // Setup sort spinner
                val sortOptions = listOf("Most popular", "Most recent", "Alphabetical (A-Z)")
                val sortAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
                sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                sortSpinner.adapter = sortAdapter
                sortSpinner.setSelection(sortOptions.indexOf(currentSort).coerceAtLeast(0))
                sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        currentSort = sortOptions[position]
                        applyFilters(loadingIndicator, emptyState, recyclerView)
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }

                // Sort by most recent update when starting
                val updates = withContext(Dispatchers.IO) { repository.fetchLatestUpdates(allPodcasts) }
                cachedUpdates = updates
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
        // Apply sorting
        val sortedList = when (currentSort) {
            "Most popular" -> {
                // Sort by popular rank (1-20), then by most recent for the rest
                unsubscribed.sortedWith(
                    compareBy<Podcast> { podcast ->
                        val rank = getPopularRank(podcast)
                        rank
                    }.thenByDescending { podcast ->
                        // For podcasts not in top 20, sort by most recent
                        if (getPopularRank(podcast) > 20) cachedUpdates[podcast.id] ?: 0L else 0L
                    }
                )
            }
            "Most recent" -> {
                unsubscribed.sortedByDescending { cachedUpdates[it.id] ?: 0L }
            }
            "Alphabetical (A-Z)" -> {
                unsubscribed.sortedBy { it.title }
            }
            else -> unsubscribed
        }

        adapter.updatePodcasts(sortedList)

        if (unsubscribed.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }

        // Ensure any loading spinner is hidden when filters finish applying
        view?.findViewById<ProgressBar>(R.id.loading_progress)?.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        fragmentScope.coroutineContext[Job]?.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (allPodcasts.isNotEmpty()) {
            view?.findViewById<ProgressBar>(R.id.loading_progress)?.let { loading ->
                view?.findViewById<TextView>(R.id.empty_state_text)?.let { empty ->
                    view?.findViewById<RecyclerView>(R.id.podcasts_recycler)?.let { rv ->
                        applyFilters(loading, empty, rv)
                    }
                }
            }
        }
    }

    private fun getPopularRank(podcast: Podcast): Int {
        val ranking = mapOf(
            "Global News Podcast" to 1,
            "6 Minute English" to 2,
            "The Documentary Podcast" to 3,
            "Newscast" to 4,
            "In Our Time" to 5,
            "Newshour" to 6,
            "Desert Island Discs" to 7,
            "Learning English Conversations" to 8,
            "The Archers" to 9,
            "You're Dead to Me" to 10,
            "Football Daily" to 11,
            "Americast" to 12,
            "Elis James and John Robins" to 13,
            "The Infinite Monkey Cage" to 14,
            "Learning Easy English" to 15,
            "Test Match Special" to 16,
            "Friday Night Comedy from BBC Radio 4" to 17,
            "Rugby Union Weekly" to 18,
            "World Business Report" to 19,
            "Woman's Hour" to 20,
        )

        // Exact match (case-insensitive) for top 20 podcasts
        val title = podcast.title
        for ((key, rank) in ranking) {
            if (title.equals(key, ignoreCase = true)) {
                return rank
            }
        }
        
        // Return rank > 20 for podcasts not in the top 20
        return 21
    }
}
