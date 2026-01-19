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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import android.view.KeyEvent

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
    // Job for ongoing search; cancel when a new query arrives
    private var searchJob: kotlinx.coroutines.Job? = null
    // Use viewLifecycleOwner.lifecycleScope for UI coroutines (auto-cancelled when the view is destroyed) 

    // Normalize queries for robust cache lookups (trim + locale-aware lowercase)
    private fun normalizeQuery(q: String?): String = q?.trim()?.lowercase(Locale.getDefault()) ?: ""

    // Small delay to let IMEs commit composition before we read the EditText text
    private val IME_COMMIT_DELAY_MS = 50L

    // Snapshot of what's currently displayed to avoid redundant adapter/visibility swaps
    private data class DisplaySnapshot(val queryNorm: String, val filterHash: Int, val isSearchAdapter: Boolean)
    private var lastDisplaySnapshot: DisplaySnapshot? = null
    private var lastActiveQueryNorm: String = ""

    private fun currentFilterHash(): Int = (currentFilter.hashCode() * 31) xor currentSort.hashCode()

    private fun showResultsSafely(
        recyclerView: RecyclerView,
        adapter: RecyclerView.Adapter<*>?,
        isSearchAdapter: Boolean,
        hasContent: Boolean,
        emptyState: TextView
    ) {
        val queryNorm = normalizeQuery(viewModel.activeSearchQuery.value ?: searchQuery)
        val snap = DisplaySnapshot(queryNorm, currentFilterHash(), isSearchAdapter)
        // Capture current adapter to avoid races with mutable properties
        val currentAdapter = recyclerView.adapter
        // If the same snapshot is already displayed and adapter instance matches, skip any UI work
        if (lastDisplaySnapshot == snap && currentAdapter == adapter) return
        lastDisplaySnapshot = snap
        lastActiveQueryNorm = queryNorm

        // Apply adapter and visibility in a single, atomic UI update to avoid flicker
        recyclerView.adapter = adapter
        if (hasContent) {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        } else {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    // Leading quick-search helper: run the unified search immediately for snappy UX
    private fun scheduleQuickSearch(emptyState: TextView, recyclerView: RecyclerView) {
        // Use the same single-path search so there's only one codepath to maintain
        viewLifecycleOwner.lifecycleScope.launch { if (isAdded) applyFilters(emptyState, recyclerView) }
    }

    // Keep a tiny suspend wrapper for compatibility with any callers — it delegates to applyFilters
    private suspend fun applyFiltersQuick(emptyState: TextView, recyclerView: RecyclerView) {
        withContext(Dispatchers.Main) { applyFilters(emptyState, recyclerView) }
    }

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

            // Avoid invalidating the display snapshot when the same query is re-applied programmatically
            val newNorm = normalizeQuery(q)
            if (newNorm != lastActiveQueryNorm) {
                lastDisplaySnapshot = null
                lastActiveQueryNorm = newNorm
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
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (!isAdded) return@launch
                        applyFilters(emptyState, recyclerView)
                    }
                    return
                }

                // When user types a non-empty query, set it as the active search that will persist
                // Clear any previously cached results so we rebuild for the new query
                viewModel.clearCachedSearch()
                viewModel.setActiveSearch(searchQuery)

                // Leading-edge quick update: show title/description matches immediately for snappy UX
                // while the debounced full search (episodes + FTS) is scheduled.
                if (filterDebounceJob == null || filterDebounceJob?.isActive == false) {
                    // Schedule the suspendable quick search from a coroutine (cancellable)
                    scheduleQuickSearch(emptyState, recyclerView)
                }

                // Debounce the application of filters to avoid running heavy searches on every keystroke
                filterDebounceJob?.cancel()
                filterDebounceJob = viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(150) // shorter debounce for snappier typing
                    // If fragment is gone, abort
                    if (!isAdded) return@launch
                    applyFilters(emptyState, recyclerView)

                    // Add to search history (deduplicated and prefix-guarded inside helper) and refresh adapter
                    try {
                        // Avoid adding very short queries from debounce (reduces noise)
                        val MIN_HISTORY_LENGTH = 3
                        if (searchQuery.length >= MIN_HISTORY_LENGTH) {
                            searchHistory.add(searchQuery)
                        }
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

        // Handle IME action (search/enter) to apply filters immediately and hide keyboard.
        // Some keyboards send IME_ACTION_DONE or a raw ENTER key event, so handle those too.
        searchEditText.setOnEditorActionListener { v, actionId, event ->
            val isSearchKey = (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH)
                    || (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
                    || (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO)
                    || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)

            if (!isSearchKey) return@setOnEditorActionListener false

            // Cancel any pending debounce and apply filters immediately
            filterDebounceJob?.cancel()

            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
            v.clearFocus()

            // Read the current text directly from the view to avoid races (IME or composition may not have updated local state)
            val current = (v.text?.toString() ?: "").trim()
            // Keep local searchQuery in sync so other code paths rely on the latest value
            searchQuery = current

            // Commit current query as active search and add to history
            if (current.isNotBlank()) {
                viewModel.setActiveSearch(current)
                try {
                    searchHistory.add(current)
                    historyAdapter.clear()
                    historyAdapter.addAll(searchHistory.getRecent())
                } catch (e: Exception) {
                    android.util.Log.w("PodcastsFragment", "Failed to persist search history: ${e.message}")
                }
            }

            // Post the apply so the IME/view has time to commit composition state — prevents "stops after submit" bugs
            v.postDelayed({ if (isAdded) applyFilters(emptyState, recyclerView) }, IME_COMMIT_DELAY_MS)
            true
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        podcastAdapter = PodcastAdapter(requireContext(), onPodcastClick = { podcast -> onPodcastClicked(podcast) })
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
            // Keep local state in sync and commit immediately so applyFilters uses the selected text
            searchQuery = sel
            viewModel.setActiveSearch(sel)
            try {
                searchHistory.add(sel)
                historyAdapter.clear()
                historyAdapter.addAll(searchHistory.getRecent())
            } catch (e: Exception) {
                android.util.Log.w("PodcastsFragment", "Failed to update search history on selection: ${e.message}")
            }
            // Post with a tiny delay so AutoCompleteTextView finishes its internal state changes
            // (some implementations update internal state asynchronously) before running search.
            searchEditText.postDelayed({ if (isAdded) applyFilters(emptyState, recyclerView) }, IME_COMMIT_DELAY_MS)
        }

        // Ensure the global action bar is shown when navigating into a podcast detail

        // Subscribed podcasts are shown in Favorites section, not here

        // Duration filter removed

        resetButton.setOnClickListener {
            // Clear both the typed query and the active persisted search; clear cached search results
            searchJob?.cancel()
            searchQuery = ""
            viewModel.clearActiveSearch()
            viewModel.clearCachedSearch()
            // Also forget the last displayed snapshot so UI won't attempt to re-use it
            lastDisplaySnapshot = null
            lastActiveQueryNorm = ""
            currentFilter = PodcastFilter()
            // Set exposed dropdowns back to 'All Genres' / default label
            genreSpinner.setText("All Genres", false)
            sortSpinner.setText("Most popular", false)
            applyFilters(emptyState, recyclerView)
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
        viewLifecycleOwner.lifecycleScope.launch {
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
                    val selected = parent?.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
                    currentFilter = if (selected == "All Genres") {
                        currentFilter.copy(genres = emptySet())
                    } else {
                        currentFilter.copy(genres = setOf(selected))
                    }
                    // Re-apply filters against any existing cached search (do NOT clear cache here).
                    applyFilters(emptyState, recyclerView)
                }
                // ensure the list is shown by applying filters after spinner is configured
                applyFilters(emptyState, recyclerView)

                // Setup sort dropdown
                val sortOptions = listOf("Most popular", "Most recent", "Alphabetical (A-Z)")
                val sortAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item_large, sortOptions)
                sortAdapter.setDropDownViewResource(R.layout.dropdown_item_large)
                sortSpinner.setAdapter(sortAdapter)
                sortSpinner.setText(currentSort, false)
                sortSpinner.setOnItemClickListener { parent, _, position, _ ->
                    val selected = parent?.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
                    currentSort = selected
                    // Re-apply sort against any existing cached search (do NOT clear cache here).
                    applyFilters(emptyState, recyclerView)
                }

                // Sort by most recent update when starting
                val updates = withContext(Dispatchers.IO) { repository.fetchLatestUpdates(allPodcasts) }
                cachedUpdates = updates
                val sorted = if (updates.isNotEmpty()) {
                    allPodcasts.sortedByDescending { updates[it.id] ?: 0L }
                } else allPodcasts
                allPodcasts = sorted
                applyFilters(emptyState, recyclerView)

                // Start a background prefetch of episode metadata for the top podcasts only
                // (prefetching all podcasts was too expensive and caused slowdown).
                viewLifecycleOwner.lifecycleScope.launch {
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
        emptyState: TextView,
        recyclerView: RecyclerView
    ) {
        // Simplified path: delegate to a single, cancellable search implementation and skip legacy logic.
        // (Legacy incremental/FTS implementation remains below but is intentionally bypassed
        //  for now to keep the behavior minimal and deterministic.)
        searchJob?.cancel()
        simplifiedApplyFilters(emptyState, recyclerView)
        return
        // Legacy/complex path removed — simplified search (called above) is used instead.
        // Removed the old `viewLifecycleOwner.lifecycleScope.launch { ... }` block to avoid
        // duplicate/unused coroutine scopes and to eliminate parser/brace-mismatch risks.
        

        // Ensure any loading spinner is hidden when filters finish applying
        view?.findViewById<ProgressBar>(R.id.loading_progress)?.visibility = View.GONE
    }

    private fun loadNextPage() {
        if (isLoadingPage) return
        val start = (currentPage + 1) * pageSize
        if (start >= filteredList.size) return
        isLoadingPage = true

        viewLifecycleOwner.lifecycleScope.launch {
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

    override fun onDestroyView() {
        super.onDestroyView()
        filterDebounceJob?.cancel()
        searchJob?.cancel()
        // Avoid leaking adapter/view references
        view?.findViewById<RecyclerView>(R.id.podcasts_recycler)?.adapter = null
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

            // Fast-path: if we have a cached search that matches the active query, restore it
            // immediately to avoid re-running expensive searches when returning from other screens
            val activeNorm = normalizeQuery(viewModel.activeSearchQuery.value)
            val cached = viewModel.getCachedSearch()
            if (activeNorm.isNotEmpty() && cached != null && normalizeQuery(cached.query) == activeNorm) {
                view?.findViewById<RecyclerView>(R.id.podcasts_recycler)?.let { rv ->
                    // Re-filter cached results for current UI filters and restore adapter
                    val cachedPodcasts = (cached.titleMatches + cached.descMatches + cached.episodeMatches.map { it.second }).distinct()
                    val filteredCachedPodcasts = repository.filterPodcasts(cachedPodcasts, currentFilter)
                    val filteredTitle = cached.titleMatches.filter { filteredCachedPodcasts.contains(it) }
                    val filteredDesc = cached.descMatches.filter { filteredCachedPodcasts.contains(it) }
                    val filteredEpisodes = cached.episodeMatches.filter { filteredCachedPodcasts.contains(it.second) }

                    searchAdapter = createSearchAdapter(filteredTitle, filteredDesc, filteredEpisodes)

                    rv.adapter = searchAdapter
                    view?.findViewById<TextView>(R.id.empty_state_text)?.let { empty ->
                        if (filteredTitle.isEmpty() && filteredDesc.isEmpty() && filteredEpisodes.isEmpty()) {
                            empty.visibility = View.VISIBLE
                            rv.visibility = View.GONE
                        } else {
                            empty.visibility = View.GONE
                            rv.visibility = View.VISIBLE
                        }
                    }
                }

                android.util.Log.d("PodcastsFragment", "onResume: restored cached search for='${viewModel.activeSearchQuery.value}' without rebuild")

                // If the cached search is present but incomplete (no episodeMatches yet),
                // resume the background episode search so results continue populating while
                // the user is viewing the player or returns to this fragment.
                if ((cached.episodeMatches.isEmpty()) && normalizeQuery(cached.query).length >= 3 && searchJob?.isActive != true) {
                    // Launch without changing the current UI state (showResultsSafely prevents flicker).
                    viewLifecycleOwner.lifecycleScope.launch {
                        // Small delay to let the restored UI settle
                        kotlinx.coroutines.delay(20)
                        if (!isAdded) return@launch
                        view?.findViewById<TextView>(R.id.empty_state_text)?.let { empty ->
                            view?.findViewById<RecyclerView>(R.id.podcasts_recycler)?.let { rv ->
                                applyFilters(empty, rv)
                            }
                        }
                    }
                }

                return
            }

            view?.findViewById<ProgressBar>(R.id.loading_progress)?.let { _ ->
                view?.findViewById<TextView>(R.id.empty_state_text)?.let { empty ->
                    view?.findViewById<RecyclerView>(R.id.podcasts_recycler)?.let { rv ->
                        applyFilters(empty, rv)
                    }
                }
            }
        }
    }

    // Centralized UI action helpers + adapter factory to reduce duplication and make callbacks testable
    private fun onPodcastClicked(podcast: Podcast) {
        android.util.Log.d("PodcastsFragment", "onPodcastClick triggered for: ${'$'}{podcast.title}")
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.show()
        val detailFragment = PodcastDetailFragment().apply { arguments = Bundle().apply { putParcelable("podcast", podcast) } }
        parentFragmentManager.beginTransaction().apply { replace(R.id.fragment_container, detailFragment); addToBackStack(null); commit() }
    }

    private fun playEpisode(episode: Episode) {
        val intent = android.content.Intent(requireContext(), RadioService::class.java).apply {
            action = RadioService.ACTION_PLAY_PODCAST_EPISODE
            putExtra(RadioService.EXTRA_EPISODE, episode)
            putExtra(RadioService.EXTRA_PODCAST_ID, episode.podcastId)
        }
        requireContext().startService(intent)
    }

    private fun openEpisodePreview(episode: Episode, podcast: Podcast) {
        val intent = android.content.Intent(requireContext(), NowPlayingActivity::class.java).apply {
            putExtra("preview_episode", episode)
            putExtra("preview_use_play_ui", true)
            putExtra("preview_podcast_title", podcast.title)
            putExtra("preview_podcast_image", podcast.imageUrl)
        }
        startActivity(intent)
    }

    private fun createSearchAdapter(
        titles: List<Podcast>,
        descs: List<Podcast>,
        episodes: List<Pair<Episode, Podcast>>
    ): SearchResultsAdapter {
        return SearchResultsAdapter(requireContext(), titles, descs, episodes,
            onPodcastClick = { onPodcastClicked(it) },
            onPlayEpisode = { playEpisode(it) },
            onOpenEpisode = { ep, pod -> openEpisodePreview(ep, pod) }
        )
    }

    // Simplified, single-path search implementation. Keeps behavior minimal and reliable:
    // - matches podcast title and description
    // - searches episodes from the in-memory cache only (no network/FTS during typing)
    // - updates the adapter once per query (no incremental swapping)
    private fun simplifiedApplyFilters(emptyState: TextView, recyclerView: RecyclerView) {
        // Ensure only one search runs at a time
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded) return@launch

            val q = (viewModel.activeSearchQuery.value ?: searchQuery).trim()
            searchQuery = q

            // Empty -> show main list (preserve sorting/pagination)
            if (q.isEmpty()) {
                val effectiveFilter = currentFilter.copy(searchQuery = "")
                val filtered = withContext(Dispatchers.Default) { repository.filterPodcasts(allPodcasts, effectiveFilter) }

                val sortedList = when (currentSort) {
                    "Most popular" -> filtered.sortedWith(
                        compareBy<Podcast> { getPopularRank(it) }
                            .thenByDescending { if (getPopularRank(it) > 20) cachedUpdates[it.id] ?: 0L else 0L }
                    )
                    "Most recent" -> filtered.sortedByDescending { cachedUpdates[it.id] ?: 0L }
                    "Alphabetical (A-Z)" -> filtered.sortedBy { it.title }
                    else -> filtered
                }

                filteredList = sortedList
                currentPage = 0
                isLoadingPage = false
                val initialPage = if (filteredList.size <= pageSize) filteredList else filteredList.take(pageSize)
                podcastAdapter.updatePodcasts(initialPage)

                showResultsSafely(recyclerView, podcastAdapter, isSearchAdapter = false, hasContent = filteredList.isNotEmpty(), emptyState)
                view?.findViewById<ProgressBar>(R.id.loading_progress)?.visibility = View.GONE
                return@launch
            }

            val qLower = q.lowercase(Locale.getDefault())

            val titleMatches = withContext(Dispatchers.Default) {
                allPodcasts.filter { repository.podcastMatchKind(it, qLower) == "title" }
            }
            val descMatches = withContext(Dispatchers.Default) {
                allPodcasts.filter { repository.podcastMatchKind(it, qLower) == "description" }
            }

            val episodeMatches = withContext(Dispatchers.Default) {
                val eps = mutableListOf<Pair<Episode, Podcast>>()
                if (q.length >= 3) {
                    val perPodcastLimit = 3
                    for (p in allPodcasts) {
                        val hits = repository.searchCachedEpisodes(p.id, qLower, perPodcastLimit)
                        for (ep in hits) {
                            eps.add(ep to p)
                            if (eps.size >= 50) break
                        }
                        if (eps.size >= 50) break
                    }
                }
                eps
            }

            searchAdapter = createSearchAdapter(titleMatches, descMatches, episodeMatches)
            viewModel.setCachedSearch(PodcastsViewModel.SearchCache(q, titleMatches.toList(), descMatches.toList(), episodeMatches.toList(), isComplete = true))

            showResultsSafely(recyclerView, searchAdapter, isSearchAdapter = true, hasContent = titleMatches.isNotEmpty() || descMatches.isNotEmpty() || episodeMatches.isNotEmpty(), emptyState)
            view?.findViewById<ProgressBar>(R.id.loading_progress)?.visibility = View.GONE
        }
    }

    private fun getPopularRank(podcast: Podcast): Int {
        for ((key, rank) in POPULAR_RANKING) {
            if (podcast.title.equals(key, ignoreCase = true)) return rank
        }
        return 21
    }

    companion object {
        private val POPULAR_RANKING = mapOf(
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
            "Woman's Hour" to 20
        )
    }
}
