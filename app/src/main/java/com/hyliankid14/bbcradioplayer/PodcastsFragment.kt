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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class PodcastsFragment : Fragment() {
    private lateinit var repository: PodcastRepository
    // Keep both adapters and swap depending on whether a search query is active
    private lateinit var podcastAdapter: PodcastAdapter
    private var searchAdapter: SearchResultsAdapter? = null
    private var allPodcasts: List<Podcast> = emptyList()
    private var currentFilter = PodcastFilter()
    private var currentSort: String = "Most popular"
    private var cachedUpdates: Map<String, Long> = emptyMap()
    private var searchQuery = ""
    // Debounce job for search input changes
    private var filterDebounceJob: kotlinx.coroutines.Job? = null
    // Job for ongoing incremental episode search; cancel when a new query arrives
    private var searchJob: kotlinx.coroutines.Job? = null
    private var searchGeneration: Int = 0
    private val fragmentScope = CoroutineScope(Dispatchers.Main + Job())

    // Pagination / lazy-loading state
    private val pageSize = 20
    private var currentPage = 0
    private var isLoadingPage = false
    private var filteredList: List<Podcast> = emptyList()

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
        val filterButton: android.widget.ImageButton = view.findViewById(R.id.podcasts_filter_button)
        val genreSpinner: com.google.android.material.textfield.MaterialAutoCompleteTextView = view.findViewById(R.id.genre_filter_spinner)
        val sortSpinner: com.google.android.material.textfield.MaterialAutoCompleteTextView = view.findViewById(R.id.sort_spinner)
        val resetButton: android.widget.Button = view.findViewById(R.id.reset_filters_button)
        val loadingIndicator: ProgressBar = view.findViewById(R.id.loading_progress)
        val emptyState: TextView = view.findViewById(R.id.empty_state_text)
        val filtersContainer: View = view.findViewById(R.id.filters_container)

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                // Debounce the application of filters to avoid running heavy searches on every keystroke
                filterDebounceJob?.cancel()
                filterDebounceJob = fragmentScope.launch {
                    kotlinx.coroutines.delay(300) // 300ms debounce
                    // If fragment is gone, abort
                    if (!isAdded) return@launch
                    applyFilters(loadingIndicator, emptyState, recyclerView)
                }
            }
        })

        // Handle IME action (search) to apply filters immediately and hide keyboard
        searchEditText.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                applyFilters(loadingIndicator, emptyState, recyclerView)
                true
            } else {
                false
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        podcastAdapter = PodcastAdapter(requireContext(), onPodcastClick = { podcast ->
            android.util.Log.d("PodcastsFragment", "onPodcastClick triggered for: ${podcast.title}")
            // Show the global action bar so the podcast title and back button are visible
            (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.show()

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
        recyclerView.adapter = podcastAdapter

        // Ensure the global action bar is shown when navigating into a podcast detail
        val originalOnPodcastClick = podcastAdapter

        // Subscribed podcasts are shown in Favorites section, not here

        // Duration filter removed

        resetButton.setOnClickListener {
            searchQuery = ""
            searchEditText.text.clear()
            currentFilter = PodcastFilter()
            // Set exposed dropdowns back to 'All Genres' / default label
            genreSpinner.setText("All Genres", false)
            sortSpinner.setText("Most popular", false)
            applyFilters(loadingIndicator, emptyState, recyclerView)
        }

        // Toggle filters visibility from the search app bar filter button
        filterButton.setOnClickListener {
            filtersContainer.visibility = if (filtersContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Previously we hid filters on scroll which caused flicker. Let the filters scroll with content inside the NestedScrollView.
        // Show a FAB when the user scrolls and implement lazy loading when the user nears the end of the list.

        // Scroll handling for nested scroll / pagination
        val nestedScroll: androidx.core.widget.NestedScrollView = view.findViewById(R.id.podcasts_scroll)
        val fab: com.google.android.material.floatingactionbutton.FloatingActionButton? = view.findViewById(R.id.scroll_to_top_fab)

        // Prevent navbar from resizing when keyboard opens while in this fragment
        val previousSoftInputMode = requireActivity().window.attributes.softInputMode
        requireActivity().window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        nestedScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // Show/hide FAB after some scrolling so it's unobtrusive initially
            val dp200 = (200 * resources.displayMetrics.density).toInt()
            if (scrollY > dp200) fab?.visibility = View.VISIBLE else fab?.visibility = View.GONE

            // Trigger loading of next page when near the bottom
            val child = nestedScroll.getChildAt(0)
            if (child != null) {
                val diff = child.measuredHeight - (nestedScroll.height + nestedScroll.scrollY)
                if (diff <= 300 && !isLoadingPage) {
                    loadNextPage()
                }
            }
        }
        // Restore previous mode when view is destroyed
        viewLifecycleOwner.lifecycle.addObserver(object : androidx.lifecycle.LifecycleEventObserver {
            override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: androidx.lifecycle.Lifecycle.Event) {
                if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) {
                    requireActivity().window.setSoftInputMode(previousSoftInputMode)
                }
            }
        })

        fab?.setOnClickListener { nestedScroll.smoothScrollTo(0, 0) }

        loadPodcasts(loadingIndicator, emptyState, recyclerView, genreSpinner, sortSpinner)
    }

    private fun loadPodcasts(
        loadingIndicator: ProgressBar,
        emptyState: TextView,
        recyclerView: RecyclerView,
        genreSpinner: com.google.android.material.textfield.MaterialAutoCompleteTextView,
        sortSpinner: com.google.android.material.textfield.MaterialAutoCompleteTextView
    ) {
        loadingIndicator.visibility = View.VISIBLE
        emptyState.text = "No podcasts found"
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
                val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item_large, genres)
                spinnerAdapter.setDropDownViewResource(R.layout.dropdown_item_large)
                genreSpinner.setAdapter(spinnerAdapter)
                // default to showing all podcasts immediately
                genreSpinner.setText(genres[0], false)

                genreSpinner.setOnItemClickListener { parent, _, position, _ ->
                    val selected = parent?.getItemAtPosition(position) as String
                    currentFilter = if (selected == "All Genres") {
                        currentFilter.copy(genres = emptySet())
                    } else {
                        currentFilter.copy(genres = setOf(selected))
                    }
                    applyFilters(loadingIndicator, emptyState, recyclerView)
                }
                // ensure the list is shown by applying filters after spinner is configured
                applyFilters(loadingIndicator, emptyState, recyclerView)

                // Setup sort dropdown
                val sortOptions = listOf("Most popular", "Most recent", "Alphabetical (A-Z)")
                val sortAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item_large, sortOptions)
                sortAdapter.setDropDownViewResource(R.layout.dropdown_item_large)
                sortSpinner.setAdapter(sortAdapter)
                sortSpinner.setText(currentSort, false)
                sortSpinner.setOnItemClickListener { parent, _, position, _ ->
                    val selected = parent?.getItemAtPosition(position) as String
                    currentSort = selected
                    applyFilters(loadingIndicator, emptyState, recyclerView)
                }

                // Sort by most recent update when starting
                val updates = withContext(Dispatchers.IO) { repository.fetchLatestUpdates(allPodcasts) }
                cachedUpdates = updates
                val sorted = if (updates.isNotEmpty()) {
                    allPodcasts.sortedByDescending { updates[it.id] ?: 0L }
                } else allPodcasts
                allPodcasts = sorted
                applyFilters(loadingIndicator, emptyState, recyclerView)

                // Start a background prefetch of episode metadata for the top podcasts only
                // (prefetching all podcasts was too expensive and caused slowdown).
                fragmentScope.launch {
                    try {
                        withContext(Dispatchers.IO) { repository.prefetchEpisodesForPodcasts(allPodcasts.take(10), limit = 10) }
                        android.util.Log.d("PodcastsFragment", "Prefetched episode metadata for top ${Math.min(10, allPodcasts.size)} podcasts")
                    } catch (e: Exception) {
                        android.util.Log.w("PodcastsFragment", "Episode prefetch failed: ${e.message}")
                    }
                }

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
        // Offload the expensive filtering operation to Default dispatcher to keep UI responsive
        fragmentScope.launch {
            val effectiveFilter = currentFilter.copy(searchQuery = searchQuery)
            val filtered = withContext(Dispatchers.Default) { repository.filterPodcasts(allPodcasts, effectiveFilter) }

            // If there's no search query, use the normal podcast adapter with sorting & pagination
            if (searchQuery.isEmpty()) {
                // Do not exclude subscribed podcasts — show all podcasts in the main list while
                // still listing subscribed ones in the Favorites section above.
                val toShow = filtered
                // Apply sorting
                val sortedList = when (currentSort) {
                    "Most popular" -> {
                        // Sort by popular rank (1-20), then by most recent for the rest
                        toShow.sortedWith(
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
                        toShow.sortedByDescending { cachedUpdates[it.id] ?: 0L }
                    }
                    "Alphabetical (A-Z)" -> {
                        toShow.sortedBy { it.title }
                    }
                    else -> toShow
                }

                // Prepare pagination
                filteredList = sortedList
                currentPage = 0
                isLoadingPage = false
                val initialPage = if (filteredList.size <= pageSize) filteredList else filteredList.take(pageSize)
                podcastAdapter.updatePodcasts(initialPage)

                recyclerView.adapter = podcastAdapter

                if (filteredList.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }

                // Ensure any loading spinner is hidden when filters finish applying
                view?.findViewById<ProgressBar>(R.id.loading_progress)?.visibility = View.GONE
                return@launch
            }

            // For search queries, build grouped results (Podcast Name / Description / Episode)
            val q = searchQuery.trim()
            val titleMatches = mutableListOf<Podcast>()
            val descMatches = mutableListOf<Podcast>()
            val episodeMatches = mutableListOf<Pair<Episode, Podcast>>()

            // IMPORTANT: ensure we operate on the base set filtered only by genres/duration
            // (do NOT apply the searchQuery filter here since episode matches may exist for
            // podcasts that don't match the podcast title/description yet).
            val baseFiltered = repository.filterPodcasts(allPodcasts, currentFilter.copy(searchQuery = ""))

            // Partition results immediately for name/description matches (fast, local)
            val remainingCandidates = mutableListOf<Podcast>()
val qLower = q.lowercase(Locale.getDefault())
        for (p in baseFiltered) {
            val kind = repository.podcastMatchKind(p, qLower)
            when (kind) {
                "title" -> titleMatches.add(p)
                "description" -> descMatches.add(p)
                else -> remainingCandidates.add(p)
                }
            }

            // If query is short, avoid expensive episode searches. Show only title/description matches.
            if (q.length < 3) {
                // Create search adapter and show current matches (no episode results)
                searchAdapter = SearchResultsAdapter(
                    requireContext(),
                    titleMatches,
                    descMatches,
                    episodeMatches,
                    onPodcastClick = { podcast ->
                        val detailFragment = PodcastDetailFragment().apply { arguments = Bundle().apply { putParcelable("podcast", podcast) } }
                        parentFragmentManager.beginTransaction().apply { replace(R.id.fragment_container, detailFragment); addToBackStack(null); commit() }
                    },
                    onPlayEpisode = { episode ->
                        val intent = android.content.Intent(requireContext(), RadioService::class.java).apply {
                            action = RadioService.ACTION_PLAY_PODCAST_EPISODE
                            putExtra(RadioService.EXTRA_EPISODE, episode)
                            putExtra(RadioService.EXTRA_PODCAST_ID, episode.podcastId)
                        }
                        requireContext().startService(intent)
                    },
                    onOpenEpisode = { episode, podcast ->
                        val intent = android.content.Intent(requireContext(), NowPlayingActivity::class.java).apply {
                            putExtra("preview_episode", episode)
                            putExtra("preview_use_play_ui", true)
                            putExtra("preview_podcast_title", podcast.title)
                            putExtra("preview_podcast_image", podcast.imageUrl)
                        }
                        startActivity(intent)
                    }
                )

                recyclerView.adapter = searchAdapter
                if (titleMatches.isEmpty() && descMatches.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }

                // Cancel any previous episode search and return early
                searchJob?.cancel()
                view?.findViewById<ProgressBar>(R.id.loading_progress)?.visibility = View.GONE
                return@launch
            }

            // At this point query is >= 3 chars; build initial adapter with no episodes, and then search episodes
            searchAdapter = SearchResultsAdapter(
                requireContext(),
                titleMatches,
                descMatches,
                episodeMatches,
                onPodcastClick = { podcast ->
                    val detailFragment = PodcastDetailFragment().apply { arguments = Bundle().apply { putParcelable("podcast", podcast) } }
                    parentFragmentManager.beginTransaction().apply { replace(R.id.fragment_container, detailFragment); addToBackStack(null); commit() }
                },
                onPlayEpisode = { episode ->
                    val intent = android.content.Intent(requireContext(), RadioService::class.java).apply {
                        action = RadioService.ACTION_PLAY_PODCAST_EPISODE
                        putExtra(RadioService.EXTRA_EPISODE, episode)
                        putExtra(RadioService.EXTRA_PODCAST_ID, episode.podcastId)
                    }
                    requireContext().startService(intent)
                },
                onOpenEpisode = { episode, podcast ->
                    val intent = android.content.Intent(requireContext(), NowPlayingActivity::class.java).apply {
                        putExtra("preview_episode", episode)
                        putExtra("preview_use_play_ui", true)
                        putExtra("preview_podcast_title", podcast.title)
                        putExtra("preview_podcast_image", podcast.imageUrl)
                    }
                    startActivity(intent)
                }
            )

            recyclerView.adapter = searchAdapter
            if (titleMatches.isEmpty() && descMatches.isEmpty() && episodeMatches.isEmpty()) {
                emptyState.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyState.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }

            // Cancel previous search job and start a new incremental episode search (limited size)
            searchJob?.cancel()
            val generation = ++searchGeneration

            // Show a small spinner while episode search is in progress
            view?.findViewById<ProgressBar>(R.id.loading_progress)?.visibility = View.VISIBLE

            searchJob = fragmentScope.launch {
                try {
                    // Limit number of podcasts we fetch episodes for to avoid overloading network
                    val candidatesToCheck = remainingCandidates.take(30)
                    android.util.Log.d("PodcastsFragment", "Episode search: checking ${candidatesToCheck.size} candidates for query '$q' (gen=$generation)")

                    // Check in small parallel chunks (bounded concurrency) to reduce wall-clock time
                    val chunkSize = 4
                    val chunks = candidatesToCheck.chunked(chunkSize)
                    var totalMatches = 0
                    for (chunk in chunks) {
                        if (generation != searchGeneration) break
                        val deferreds = chunk.map { p ->
                            async(Dispatchers.IO) {
                                val qLower = q.lowercase(Locale.getDefault())
                                // Prefer cached indexed search when available
                                val cachedHits = repository.searchCachedEpisodes(p.id, qLower, 3)
                                if (cachedHits.isNotEmpty()) return@async p to cachedHits
                                // Otherwise fetch episodes (repository will index them)
                                val eps = repository.fetchEpisodesIfNeeded(p)
                                val hits = eps.filter { it.title.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true) }
                                p to hits
                            }
                        }
                        val results = deferreds.awaitAll()
                        if (generation != searchGeneration) break
                        for ((p, matched) in results) {
                            if (generation != searchGeneration) break
                            if (matched.isNotEmpty()) {
                                val added = matched.take(3).map { it to p } // limit per-podcast
                                episodeMatches.addAll(added)
                                totalMatches += added.size
                                // Update adapter's episode list incrementally
                                recyclerView.post { (recyclerView.adapter as? SearchResultsAdapter)?.updateEpisodeMatches(episodeMatches) }
                                android.util.Log.d("PodcastsFragment", "Found ${added.size} episode matches in podcast '${p.title}' for query '$q'")
                                if (totalMatches >= 50) break
                            }
                        }
                        if (totalMatches >= 50) break
                    }
                } catch (e: Exception) {
                    android.util.Log.w("PodcastsFragment", "Episode search job failed: ${e.message}")
                } finally {
                    // Hide spinner when finished
                    if (generation == searchGeneration) {
                        view?.findViewById<ProgressBar>(R.id.loading_progress)?.visibility = View.GONE
                    }
                }
            }
        }

        // Ensure any loading spinner is hidden when filters finish applying
        view?.findViewById<ProgressBar>(R.id.loading_progress)?.visibility = View.GONE
    }

    private fun loadNextPage() {
        if (isLoadingPage) return
        val start = (currentPage + 1) * pageSize
        if (start >= filteredList.size) return
        isLoadingPage = true

        fragmentScope.launch {
            try {
                val end = (start + pageSize).coerceAtMost(filteredList.size)
                val next = filteredList.subList(start, end)
                podcastAdapter.addPodcasts(next)
                currentPage += 1
            } catch (e: Exception) {
                android.util.Log.e("PodcastsFragment", "Error loading next page", e)
            } finally {
                isLoadingPage = false
            }
        }
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
