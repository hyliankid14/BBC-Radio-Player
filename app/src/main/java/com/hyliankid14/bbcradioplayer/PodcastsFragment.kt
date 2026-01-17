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
import androidx.lifecycle.ViewModelProvider
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
    private lateinit var viewModel: PodcastsViewModel
    private lateinit var repository: PodcastRepository
    // Keep both adapters and swap depending on whether a search query is active
    private lateinit var podcastAdapter: PodcastAdapter
    private var searchAdapter: SearchResultsAdapter? = null
    private var allPodcasts: List<Podcast> = emptyList()
    private var currentFilter = PodcastFilter()
    private var currentSort: String = "Most popular"
    private var cachedUpdates: Map<String, Long> = emptyMap()
    private var searchQuery = ""
    // Active search state is persisted in the ViewModel while the activity lives
    // Suppress the text watcher when programmatically updating the search EditText
    private var suppressSearchWatcher: Boolean = false
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
        // Initialize ViewModel scoped to the Activity so it survives fragment navigation
        viewModel = ViewModelProvider(requireActivity()).get(PodcastsViewModel::class.java)

        val recyclerView: RecyclerView = view.findViewById(R.id.podcasts_recycler)
        val searchEditText: com.google.android.material.textfield.MaterialAutoCompleteTextView = view.findViewById(R.id.search_podcast_edittext)
        // Setup search history backing and adapter
        val searchHistory = SearchHistory(requireContext())
        val historyAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, searchHistory.getRecent())
        searchEditText.setAdapter(historyAdapter)

        // Restore the active search into the edit text when the view is (re)created, without triggering the watcher
        suppressSearchWatcher = true
        val restored = viewModel.activeSearchQuery.value
        searchEditText.setText(restored ?: "")
        if (!restored.isNullOrEmpty()) searchEditText.setSelection(searchEditText.text.length)
        suppressSearchWatcher = false
        android.util.Log.d("PodcastsFragment", "onViewCreated: viewModel.activeSearchQuery='${restored}' searchEditText='${searchEditText.text}'")

        // Observe active search and update hint + edit text when it changes
        val activeHintView: TextView = view.findViewById(R.id.active_search_hint)
        viewModel.activeSearchQuery.observe(viewLifecycleOwner) { q ->
            if (!q.isNullOrBlank()) {
                activeHintView.text = "Active search: '$q' (Reset to clear)"
                activeHintView.visibility = View.VISIBLE
            } else {
                activeHintView.visibility = View.GONE
            }

            // Ensure edit text reflects current active search without triggering watcher
            val current = searchEditText.text?.toString() ?: ""
            if (current != (q ?: "")) {
                suppressSearchWatcher = true
                searchEditText.setText(q ?: "")
                if (!q.isNullOrEmpty()) searchEditText.setSelection(searchEditText.text.length)
                suppressSearchWatcher = false
            }
        }

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
                if (suppressSearchWatcher) return
                searchQuery = s?.toString() ?: ""
                // If the user cleared the search box, clear the active persisted search and update immediately
                if (searchQuery.isBlank()) {
                    android.util.Log.d("PodcastsFragment", "afterTextChanged: search box cleared, clearing active search (was='${viewModel.activeSearchQuery.value}')")
                    viewModel.clearActiveSearch()
                    searchJob?.cancel()
                    filterDebounceJob?.cancel()
                    // Apply filters immediately so results disappear when the box is cleared
                    fragmentScope.launch {
                        if (!isAdded) return@launch
                        applyFilters(loadingIndicator, emptyState, recyclerView)
                    }
                    return
                }

                // When user types a non-empty query, set it as the active search that will persist
                // Clear any previously cached results so we rebuild for the new query
                viewModel.clearCachedSearch()
                viewModel.setActiveSearch(searchQuery)

                // Debounce the application of filters to avoid running heavy searches on every keystroke
                filterDebounceJob?.cancel()
                filterDebounceJob = fragmentScope.launch {
                    kotlinx.coroutines.delay(300) // 300ms debounce
                    // If fragment is gone, abort
                    if (!isAdded) return@launch
                    applyFilters(loadingIndicator, emptyState, recyclerView)

                    // Add to search history (deduplicated inside helper) and refresh adapter
                    try {
                        searchHistory.add(searchQuery)
                        withContext(Dispatchers.Main) {
                            historyAdapter.clear()
                            historyAdapter.addAll(searchHistory.getRecent())
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("PodcastsFragment", "Failed to update search history: ${e.message}")
                    }
                }
            }
        })

        // Handle IME action (search) to apply filters immediately and hide keyboard
        searchEditText.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                // Treat IME search as committing the current query as the active search
                if (searchQuery.isNotBlank()) {
                    viewModel.setActiveSearch(searchQuery)
                    try {
                        searchHistory.add(searchQuery)
                        historyAdapter.clear()
                        historyAdapter.addAll(searchHistory.getRecent())
                    } catch (e: Exception) {
                        android.util.Log.w("PodcastsFragment", "Failed to persist search history: ${e.message}")
                    }
                }
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

        // Show a dropdown of recent searches when the field is focused or typed into
        searchEditText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                historyAdapter.clear()
                historyAdapter.addAll(searchHistory.getRecent())
                searchEditText.showDropDown()
            }
        }
        // When the user selects a history item, populate search and apply immediately
        searchEditText.setOnItemClickListener { parent, _, position, _ ->
            val sel = parent?.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
            suppressSearchWatcher = true
            searchEditText.setText(sel)
            searchEditText.setSelection(sel.length)
            suppressSearchWatcher = false
            viewModel.setActiveSearch(sel)
            try {
                searchHistory.add(sel)
                historyAdapter.clear()
                historyAdapter.addAll(searchHistory.getRecent())
            } catch (e: Exception) {
                android.util.Log.w("PodcastsFragment", "Failed to update search history on selection: ${e.message}")
            }
            applyFilters(loadingIndicator, emptyState, recyclerView)
        }

        // Ensure the global action bar is shown when navigating into a podcast detail
        val originalOnPodcastClick = podcastAdapter

        // Subscribed podcasts are shown in Favorites section, not here

        // Duration filter removed

        resetButton.setOnClickListener {
            // Clear both the typed query and the active persisted search; clear cached search results
            searchJob?.cancel()
            searchQuery = ""
            viewModel.clearActiveSearch()
            viewModel.clearCachedSearch()
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
                    // Changing filters invalidates cached search results
                    viewModel.clearCachedSearch()
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
                    // Changing sort invalidates cached search results
                    viewModel.clearCachedSearch()
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
                        val prefetchCount = Math.min(20, allPodcasts.size)
                        withContext(Dispatchers.IO) { repository.prefetchEpisodesForPodcasts(allPodcasts.take(prefetchCount), limit = prefetchCount) }
                        android.util.Log.d("PodcastsFragment", "Prefetched episode metadata for top $prefetchCount podcasts")
                    } catch (e: Exception) {
                        android.util.Log.w("PodcastsFragment", "Episode prefetch failed: ${e.message}")
                    }
                }

                // Background indexing (Room FTS) disabled (waiting for Gradle/Kapt fix). In the meantime we rely on limited prefetches for search.

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
            // Use persisted active search from ViewModel when set to persist results until Reset is pressed
            val active = viewModel.activeSearchQuery.value ?: searchQuery
            val effectiveFilter = currentFilter.copy(searchQuery = active)
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
            val q = (viewModel.activeSearchQuery.value ?: searchQuery).trim()

            // If we have a cached result for this query, reuse it immediately to avoid rebuilding
            val cached = viewModel.getCachedSearch()
            if (cached != null && cached.query == q) {
                android.util.Log.d("PodcastsFragment", "applyFilters: using cached search results for query='$q'")
                searchAdapter = SearchResultsAdapter(requireContext(), cached.titleMatches, cached.descMatches, cached.episodeMatches,
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
                if (cached.titleMatches.isEmpty() && cached.descMatches.isEmpty() && cached.episodeMatches.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }

                // Ensure any previous episode search is cancelled and spinner hidden
                searchJob?.cancel()
                view?.findViewById<ProgressBar>(R.id.loading_progress)?.visibility = View.GONE
                return@launch
            }

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
                // Cache these quick results so returning to the list is instant
                viewModel.setCachedSearch(PodcastsViewModel.SearchCache(q, titleMatches.toList(), descMatches.toList(), episodeMatches.toList()))

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
            // Cache the initial adapter state (no episodes yet) so returning to the list is fast
            viewModel.setCachedSearch(PodcastsViewModel.SearchCache(q, titleMatches.toList(), descMatches.toList(), episodeMatches.toList()))

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
                    val candidateLimit = if (q.trim().contains(" ")) 100 else 30
                    val candidatesToCheck = remainingCandidates.take(candidateLimit)
                    android.util.Log.d("PodcastsFragment", "Episode search: checking ${candidatesToCheck.size} candidates (limit=$candidateLimit) for query '$q' (gen=$generation)")

                    // Global FTS-based episode search: use the on-disk index to quickly find matching
                    // episodes across all podcasts, then fetch the episode objects for display.
                    try {
                        val index = com.hyliankid14.bbcradioplayer.db.IndexStore.getInstance(requireContext())
                        val ftsResults = try {
                            index.searchEpisodes(q, 200)
                        } catch (e: Exception) {
                            android.util.Log.w("PodcastsFragment", "FTS global search failed: ${e.message}")
                            emptyList<com.hyliankid14.bbcradioplayer.db.EpisodeFts>()
                        }

                        if (ftsResults.isNotEmpty()) {
                            android.util.Log.d("PodcastsFragment", "FTS global returned ${ftsResults.size} hits for query '$q'")
                            // Group FTS hits by podcastId for efficient per-podcast resolution
                            val grouped = ftsResults.groupBy { it.podcastId }
                            var globalMatches = 0
                            for ((podId, eps) in grouped) {
                                if (generation != searchGeneration) break
                                if (globalMatches >= 50) break
                                // Only consider candidates that are within the remainingCandidates set
                                val p = remainingCandidates.find { it.id == podId } ?: continue
                                val episodes = repository.fetchEpisodesIfNeeded(p)
                                // Prefer title matches over description matches when mapping FTS hits
                                val titleMatched = mutableListOf<com.hyliankid14.bbcradioplayer.Episode>()
                                val descMatched = mutableListOf<com.hyliankid14.bbcradioplayer.Episode>()
                                for (ef in eps) {
                                    val found = episodes.find { it.id == ef.episodeId }
                                    if (repository.textMatchesNormalized(ef.title, q)) {
                                        if (found != null) titleMatched.add(found) else titleMatched.add(
                                            com.hyliankid14.bbcradioplayer.Episode(
                                                id = ef.episodeId,
                                                title = ef.title,
                                                description = ef.description,
                                                audioUrl = "",
                                                imageUrl = p.imageUrl,
                                                pubDate = "",
                                                durationMins = 0,
                                                podcastId = p.id
                                            )
                                        )
                                    } else if (repository.textMatchesNormalized(ef.description, q)) {
                                        if (found != null) descMatched.add(found) else descMatched.add(
                                            com.hyliankid14.bbcradioplayer.Episode(
                                                id = ef.episodeId,
                                                title = ef.title,
                                                description = ef.description,
                                                audioUrl = "",
                                                imageUrl = p.imageUrl,
                                                pubDate = "",
                                                durationMins = 0,
                                                podcastId = p.id
                                            )
                                        )
                                    }
                                }

                                val matched = (titleMatched + descMatched).take(3)

                                if (matched.isNotEmpty()) {
                                    val added = matched.map { it to p }
                                    episodeMatches.addAll(added)
                                    globalMatches += added.size
                                    recyclerView.post {
                                        // Sort episodes so title matches appear first across all podcasts
                                        val sorted = episodeMatches.sortedWith(compareByDescending<Pair<com.hyliankid14.bbcradioplayer.Episode, com.hyliankid14.bbcradioplayer.Podcast>> { pair ->
                                            when {
                                                repository.textMatchesNormalized(pair.first.title, q) -> 2
                                                repository.textMatchesNormalized(pair.first.description ?: "", q) -> 1
                                                else -> 0
                                            }
                                        })
                                        (recyclerView.adapter as? SearchResultsAdapter)?.updateEpisodeMatches(sorted)
                                        if (sorted.isNotEmpty() || titleMatches.isNotEmpty() || descMatches.isNotEmpty()) {
                                            emptyState.visibility = View.GONE
                                            recyclerView.visibility = View.VISIBLE
                                        } else {
                                            emptyState.visibility = View.VISIBLE
                                            recyclerView.visibility = View.GONE
                                        }
                                        // Update in-memory cache so returning to list shows results immediately
                                        viewModel.setCachedSearch(PodcastsViewModel.SearchCache(q, titleMatches.toList(), descMatches.toList(), sorted.toList()))
                                    }
                                    android.util.Log.d("PodcastsFragment", "FTS global matched ${added.size} episodes in podcast='${p.title}' for query='$q'")
                                }
                            }

                            // Avoid re-checking podcasts already matched by the global FTS search
                            remainingCandidates.removeAll { pm -> episodeMatches.any { it.second.id == pm.id } }

                            // If we've already reached the display limit, skip further per-podcast scanning
                            if (episodeMatches.size >= 50) {
                                view?.findViewById<ProgressBar>(R.id.loading_progress)?.visibility = View.GONE
                                return@launch
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("PodcastsFragment", "FTS pre-search unavailable: ${e.message}")
                    }

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
                                // Otherwise fetch episodes (repository will index them) then search using the same indexed helper
                                val eps = repository.fetchEpisodesIfNeeded(p)
                                val postHits = repository.searchCachedEpisodes(p.id, qLower, 3)
                                if (postHits.isNotEmpty()) return@async p to postHits

                                // As a last resort, attempt a looser token-AND search on the raw episode strings
                                val tokens = qLower.split(Regex("\\s+")).filter { it.isNotEmpty() }
                                if (tokens.isNotEmpty() && eps.isNotEmpty()) {
                                    val hits = eps.filter { ep ->
                                        val t = ep.title.lowercase(Locale.getDefault())
                                        val d = (ep.description ?: "").lowercase(Locale.getDefault())
                                        tokens.all { tok -> t.contains(tok) || d.contains(tok) }
                                    }.take(3)
                                    if (hits.isNotEmpty()) android.util.Log.d("PodcastsFragment", "token-AND fallback matched ${hits.size} items in podcast='${p.title}' for query='$q'")
                                    return@async p to hits
                                }

                                p to emptyList<Episode>()
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
                                // Update adapter's episode list incrementally — sort so title matches appear first
                                recyclerView.post {
                                    val sorted = episodeMatches.sortedWith(compareByDescending<Pair<com.hyliankid14.bbcradioplayer.Episode, com.hyliankid14.bbcradioplayer.Podcast>> { pair ->
                                        when {
                                            repository.textMatchesNormalized(pair.first.title, q) -> 2
                                            repository.textMatchesNormalized(pair.first.description ?: "", q) -> 1
                                            else -> 0
                                        }
                                    })
                                    (recyclerView.adapter as? SearchResultsAdapter)?.updateEpisodeMatches(sorted)
                                    if (sorted.isNotEmpty() || titleMatches.isNotEmpty() || descMatches.isNotEmpty()) {
                                        emptyState.visibility = View.GONE
                                        recyclerView.visibility = View.VISIBLE
                                    } else {
                                        emptyState.visibility = View.VISIBLE
                                        recyclerView.visibility = View.GONE
                                    }                                    // Update in-memory cache so returning to list shows results immediately
                                    viewModel.setCachedSearch(PodcastsViewModel.SearchCache(q, titleMatches.toList(), descMatches.toList(), sorted.toList()))                                }
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
        android.util.Log.d("PodcastsFragment", "onResume: activeSearchQuery='${viewModel.activeSearchQuery.value}' searchQuery='${searchQuery}' allPodcasts.size=${allPodcasts.size}")
        if (allPodcasts.isNotEmpty()) {
            // If we're already showing the search results adapter and an active search exists, keep them
            val active = viewModel.activeSearchQuery.value
            val rv = view?.findViewById<RecyclerView>(R.id.podcasts_recycler)
            if (rv != null) {
                if (active.isNullOrBlank()) {
                    // No active persisted search — if we're already showing the main podcasts list, skip reapplying filters
                    if (searchQuery.isBlank() && rv.adapter == podcastAdapter) {
                        android.util.Log.d("PodcastsFragment", "onResume: no active search and already showing podcasts, skipping rebuild")
                        return
                    }
                } else {
                    // An active search is persisted; if we're already showing the search results adapter, keep it to avoid re-running expensive searches
                    if (rv.adapter == searchAdapter) {
                        android.util.Log.d("PodcastsFragment", "onResume: keeping existing search results (active='${active}'), skipping rebuild")
                        return
                    }
                }
            }

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
