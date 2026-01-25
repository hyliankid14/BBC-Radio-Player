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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import android.view.KeyEvent
import android.widget.Toast

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

    // Episode-search pagination state (index-backed)
    private val INITIAL_EPISODE_DISPLAY_LIMIT = 150        // how many episodes to try to show immediately
    private val EPISODE_PAGE_SIZE = 25                    // page size when scrolling
    private var pendingIndexEpisodeIds: MutableList<Pair<String, String>> = mutableListOf() // (episodeId, podcastId)
    private var resolvedEpisodeMatches: MutableList<Pair<Episode, Podcast>> = mutableListOf()
    private val podcastsBeingFetched: MutableSet<String> = mutableSetOf() // avoid duplicate fetches per-podcast
    // How many episode items are currently displayed by the adapter (used for search pagination)
    private var displayedEpisodeCount: Int = 0

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
        // TextInputLayout that wraps the EditText — used to control the end-icon visibility reliably
        val searchInputLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.search_podcast_text_input)

        // Setup search history backing and adapter
        val searchHistory = SearchHistory(requireContext())
        val historyAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, searchHistory.getRecent())
        searchEditText.setAdapter(historyAdapter)

        // Restore the active search into the edit text when the view is (re)created, without triggering the watcher
        suppressSearchWatcher = true
        val restored = viewModel.activeSearchQuery.value
        searchEditText.setText(restored ?: "")
        if (!restored.isNullOrEmpty()) searchEditText.setSelection(searchEditText.text.length)
        // Ensure the clear (end) icon reflects the restored text immediately (fixes OEMs that only show it after IME events)
        try {
            // Use a custom end-icon so some OEM/Material implementations don't hide it when the field loses focus.
            searchInputLayout.isEndIconVisible = !searchEditText.text.isNullOrEmpty()

            // Provide an explicit click handler that always clears the field and hides the IME.
            searchInputLayout.setEndIconOnClickListener {
                suppressSearchWatcher = true
                searchEditText.text?.clear()
                suppressSearchWatcher = false

                // Keep persisted state consistent
                viewModel.clearActiveSearch()
                try { searchInputLayout.isEndIconVisible = false } catch (_: Exception) { }

                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                searchEditText.clearFocus()
            }

            // Force visibility for non-empty text regardless of focus/IME state
            if (!searchEditText.text.isNullOrEmpty()) searchInputLayout.isEndIconVisible = true
        } catch (_: Exception) { }
        suppressSearchWatcher = false
        android.util.Log.d("PodcastsFragment", "onViewCreated: viewModel.activeSearchQuery='${restored}' searchEditText='${searchEditText.text}'")

        // Try to restore a persisted search cache (survives navigation/process-restores). Only restore
        // if the persisted query matches the currently active query so we don't override unrelated state.
        try {
            val persisted = SearchCacheStore.load(requireContext())
            if (persisted != null && normalizeQuery(persisted.query) == normalizeQuery(viewModel.activeSearchQuery.value) && viewModel.getCachedSearch() == null) {
                android.util.Log.d("PodcastsFragment", "Restoring persisted search cache for='${persisted.query}'")
                viewModel.setCachedSearch(persisted)
            }
        } catch (_: Exception) { /* best-effort */ }

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
                // Ensure end-icon visibility updates when we apply text programmatically
                try { searchInputLayout.isEndIconVisible = !q.isNullOrEmpty() } catch (_: Exception) { }
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
                // Keep the clear icon in sync immediately (fixes case where programmatic setText doesn't show it)
                try { searchInputLayout.isEndIconVisible = !s.isNullOrEmpty() } catch (_: Exception) { }
                searchQuery = s?.toString() ?: ""
                // If the user cleared the search box, clear the active persisted search and update immediately
                if (searchQuery.isBlank()) {
                    // Ensure end-icon is hidden when empty
                    try { searchInputLayout.isEndIconVisible = false } catch (_: Exception) { }
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
                // Clear both in-memory and persisted search cache
                clearCachedSearchPersisted()
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

            // Re-assert end-icon visibility after the IME is dismissed (persistent clear icon behavior)
            try { searchInputLayout.isEndIconVisible = current.isNotBlank() } catch (_: Exception) { }

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
            // Keep end-icon visibility consistent when focus changes
            try { searchInputLayout.isEndIconVisible = !searchEditText.text.isNullOrEmpty() } catch (_: Exception) { }
        }
        // When the user selects a history item, populate search and apply immediately
        searchEditText.setOnItemClickListener { parent, _, position, _ ->
            val sel = parent?.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
            suppressSearchWatcher = true
            searchEditText.setText(sel)
            searchEditText.setSelection(sel.length)
            // Ensure clear icon is visible for the selected text
            try { searchInputLayout.isEndIconVisible = true } catch (_: Exception) { }
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
            // Clear both in-memory and persisted search cache
            clearCachedSearchPersisted()
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
                        val prefetchCount = Math.min(100, allPodcasts.size)
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
        // If we're showing search results, paginate episodes from the pending index queue
        val rv = view?.findViewById<RecyclerView>(R.id.podcasts_recycler)
        if (rv?.adapter == searchAdapter) {
            if (displayedEpisodeCount >= resolvedEpisodeMatches.size && pendingIndexEpisodeIds.isEmpty()) return
            isLoadingPage = true

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val toAdd = mutableListOf<Pair<Episode, Podcast>>()

                    // First use any already-resolved episodes that aren't yet displayed
                    if (resolvedEpisodeMatches.size > displayedEpisodeCount) {
                        val take = resolvedEpisodeMatches.subList(displayedEpisodeCount, resolvedEpisodeMatches.size).take(EPISODE_PAGE_SIZE)
                        toAdd.addAll(take)
                        displayedEpisodeCount += take.size
                    }

                    // If we still need more, try to resolve ids from the pending index queue
                    if (toAdd.size < EPISODE_PAGE_SIZE && pendingIndexEpisodeIds.isNotEmpty()) {
                        val need = EPISODE_PAGE_SIZE - toAdd.size
                        val slice = pendingIndexEpisodeIds.take(need).toList()

                        // Group by podcast so we can fetch per-podcast only once if needed
                        val byPod = slice.groupBy { it.second }
                        val newlyResolved = mutableListOf<Pair<Episode, Podcast>>()

                        for ((podId, ids) in byPod) {
                            if (!kotlin.coroutines.coroutineContext.isActive) break
                            // Try cache first
                            val cached = repository.getEpisodesFromCache(podId) ?: emptyList()
                            val found = ids.mapNotNull { (eid, _) -> cached.firstOrNull { it.id == eid } }
                            val missing = ids.map { it.first }.filter { id -> found.none { it.id == id } }

                            if (found.isNotEmpty()) {
                                newlyResolved.addAll(found.map { it to (allPodcasts.firstOrNull { p -> p.id == podId } ?: Podcast(podId, "", "", "", "", "", emptyList(), 0)) })
                            }

                            if (missing.isNotEmpty()) {
                                // Attempt a short fetch for this podcast (only if not already being fetched)
                                if (!podcastsBeingFetched.contains(podId)) {
                                    podcastsBeingFetched.add(podId)
                                    val fetched = try { withTimeoutOrNull(1500L) { repository.fetchEpisodesIfNeeded(allPodcasts.first { it.id == podId }) } } catch (_: Exception) { null }
                                    podcastsBeingFetched.remove(podId)
                                    if (!fetched.isNullOrEmpty()) {
                                        newlyResolved.addAll(fetched.filter { it.id in missing }.map { it to allPodcasts.first { p -> p.id == podId } })
                                    }
                                }
                            }
                        }

                        // Append newly resolved matches in index order
                        for ((eid, pid) in slice) {
                            val match = newlyResolved.firstOrNull { it.first.id == eid && it.second.id == pid }
                            if (match != null) {
                                toAdd.add(match)
                                pendingIndexEpisodeIds.remove(eid to pid)
                                displayedEpisodeCount += 1
                            } else {
                                // keep in queue for background resolution
                            }
                            if (toAdd.size >= EPISODE_PAGE_SIZE) break
                        }
                    }

                    if (toAdd.isNotEmpty()) {
                        // Merge into resolved list and update adapter
                        resolvedEpisodeMatches.addAll(toAdd)
                        searchAdapter?.updateEpisodeMatches(resolvedEpisodeMatches)
                        // Persist expanded cache
                        val cached = viewModel.getCachedSearch()
                        persistCachedSearch(PodcastsViewModel.SearchCache(
                            cached?.query ?: searchQuery,
                            cached?.titleMatches ?: emptyList(),
                            cached?.descMatches ?: emptyList(),
                            resolvedEpisodeMatches.toList(),
                            isComplete = false
                        ))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PodcastsFragment", "Error loading next episode page", e)
                } finally {
                    isLoadingPage = false
                }
            }

            return
        }

        // Default: paginate podcasts list as before
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
                view?.findViewById<RecyclerView>(R.id.podcasts_recycler)?.let { cachedRv ->
                    // Re-filter cached results for current UI filters and restore adapter
                    val cachedPodcasts = (cached.titleMatches + cached.descMatches + cached.episodeMatches.map { it.second }).distinct()
                    val filteredCachedPodcasts = repository.filterPodcasts(cachedPodcasts, currentFilter)
                    val filteredTitle = cached.titleMatches.filter { filteredCachedPodcasts.contains(it) }
                    val filteredDesc = cached.descMatches.filter { filteredCachedPodcasts.contains(it) }
                    val filteredEpisodes = cached.episodeMatches.filter { filteredCachedPodcasts.contains(it.second) }

                    searchAdapter = createSearchAdapter(filteredTitle, filteredDesc, filteredEpisodes)
                    // track how many episode items the adapter is showing (used for incremental loads)
                    displayedEpisodeCount = filteredEpisodes.size

                    cachedRv.adapter = searchAdapter
                    view?.findViewById<TextView>(R.id.empty_state_text)?.let { empty ->
                        if (filteredTitle.isEmpty() && filteredDesc.isEmpty() && filteredEpisodes.isEmpty()) {
                            empty.visibility = View.VISIBLE
                            cachedRv.visibility = View.GONE
                        } else {
                            empty.visibility = View.GONE
                            cachedRv.visibility = View.VISIBLE
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
        // If we already have a playable URL, start immediately
        if (episode.audioUrl.isNotBlank()) {
            val intent = android.content.Intent(requireContext(), RadioService::class.java).apply {
                action = RadioService.ACTION_PLAY_PODCAST_EPISODE
                putExtra(RadioService.EXTRA_EPISODE, episode)
                putExtra(RadioService.EXTRA_PODCAST_ID, episode.podcastId)
            }
            requireContext().startService(intent)
            return
        }

        // Otherwise attempt a fast background resolution from the repository (bounded) and
        // only start playback if we obtain a playable audioUrl. This avoids a full navigation
        // to the podcast detail for index-only hits while respecting the no-spam network rule.
        viewLifecycleOwner.lifecycleScope.launch {
            val pod = allPodcasts.find { it.id == episode.podcastId }
            if (pod == null) {
                // Unknown podcast — open preview so user can navigate
                Toast.makeText(requireContext(), "Episode details unavailable", Toast.LENGTH_SHORT).show()
                openEpisodePreview(episode, Podcast(id = episode.podcastId, title = "", description = "", rssUrl = "", htmlUrl = "", imageUrl = "", genres = emptyList(), typicalDurationMins = 0))
                return@launch
            }

            // Try a quick fetch with timeout so we don't block the UI for long
            val resolved: Episode? = try {
                withTimeoutOrNull(3000L) {
                    val fetched = repository.fetchEpisodesIfNeeded(pod)
                    fetched.firstOrNull { it.id == episode.id }
                }
            } catch (e: Exception) {
                null
            }

            if (resolved?.audioUrl?.isNotBlank() == true) {
                val intent = android.content.Intent(requireContext(), RadioService::class.java).apply {
                    action = RadioService.ACTION_PLAY_PODCAST_EPISODE
                    putExtra(RadioService.EXTRA_EPISODE, resolved)
                    putExtra(RadioService.EXTRA_PODCAST_ID, resolved.podcastId)
                }
                requireContext().startService(intent)
                return@launch
            }

            // Fallback UX: let the user open the episode preview (which can trigger a full fetch)
            Toast.makeText(requireContext(), "Fetching episode details — open podcast to play", Toast.LENGTH_SHORT).show()
            openEpisodePreview(episode, pod)
        }
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

    // Persist the UI search cache both in-memory (ViewModel) and on-disk so it survives
    // navigation and short-lived process restarts. Clearing is best-effort.
    private fun persistCachedSearch(cache: PodcastsViewModel.SearchCache) {
        viewModel.setCachedSearch(cache)
        try { SearchCacheStore.save(requireContext(), cache) } catch (_: Exception) {}
    }

    private fun clearCachedSearchPersisted() {
        viewModel.clearCachedSearch()
        try { SearchCacheStore.clear(requireContext()) } catch (_: Exception) {}
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

            val loadingView = view?.findViewById<ProgressBar>(R.id.loading_progress)
            var spinnerShown = false
            // Delay showing the spinner slightly to avoid a flicker on very-fast searches
            val showSpinnerJob = launch {
                kotlinx.coroutines.delay(120)
                if (!isActive) return@launch
                loadingView?.visibility = View.VISIBLE
                spinnerShown = true
            }

            try {
                val q = (viewModel.activeSearchQuery.value ?: searchQuery).trim()
                searchQuery = q

                // Empty -> show main list (preserve sorting/pagination)
                if (q.isEmpty()) {
                    val effectiveFilter = currentFilter.copy(searchQuery = "")
                    val filtered = withContext(Dispatchers.Default) { repository.filterPodcasts(allPodcasts, effectiveFilter) }

                    val sortedList = when (currentSort) {
                        "Most popular" -> filtered.sortedWith(
                            compareBy<Podcast> { getPopularRank(it) }
                                .thenByDescending { if (getPopularRank(it) > 100) cachedUpdates[it.id] ?: 0L else 0L }
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
                    loadingView?.visibility = View.GONE
                    return@launch
                }

                val qLower = q.lowercase(Locale.getDefault())

                val titleMatches = withContext(Dispatchers.Default) {
                    val raw = allPodcasts.filter { repository.podcastMatchKind(it, qLower) == "title" }
                    repository.filterPodcasts(raw, currentFilter)
                }

                val descMatches = withContext(Dispatchers.Default) {
                    val raw = allPodcasts.filter { repository.podcastMatchKind(it, qLower) == "description" }
                    repository.filterPodcasts(raw, currentFilter)
                }

                val episodeMatches = withContext(Dispatchers.IO) {
                    // Prefer on-disk FTS index when available — it's fast and returns global
                    // episode hits across podcasts. Fall back to per-podcast cached search
                    // if the index is unavailable or returns nothing.
                    val eps = mutableListOf<Pair<Episode, Podcast>>()

                    if (q.length >= 3) {
                        try {
                            val index = com.hyliankid14.bbcradioplayer.db.IndexStore.getInstance(requireContext())
                            val ftsResults = try {
                                // request a much larger result set from the on-disk index so we can
                                // surface all available matches (subject to the index size)
                                index.searchEpisodes(q, 1000)
                            } catch (e: Exception) {
                                android.util.Log.w("PodcastsFragment", "FTS episode search failed: ${e.message}")
                                emptyList<com.hyliankid14.bbcradioplayer.db.EpisodeFts>()
                            }

                            if (ftsResults.isNotEmpty()) {
                                // Group hits by podcast and prefer title matches when ordering
                                val grouped = ftsResults.groupBy { it.podcastId }
                                var total = 0
                                for ((podId, hits) in grouped) {
                                    if (!coroutineContext.isActive) break
                                    val p = allPodcasts.find { it.id == podId } ?: continue

                                    // Try to resolve actual Episode objects from the in-memory cache
                                    val cached = repository.getEpisodesFromCache(podId)
                                    val perPodcastAdded = mutableListOf<Pair<Episode, Podcast>>()

                                    // Prefer title matches first
                                    val titleHits = hits.filter { repository.textMatchesNormalized(it.title, q) }
                                    val descHits = hits.filter { !repository.textMatchesNormalized(it.title, q) && repository.textMatchesNormalized(it.description, q) }

                                    fun resolveHit(ef: com.hyliankid14.bbcradioplayer.db.EpisodeFts): Episode {
                                        val found = cached?.find { it.id == ef.episodeId }
                                        return found ?: Episode(
                                            id = ef.episodeId,
                                            title = ef.title,
                                            description = ef.description ?: "",
                                            audioUrl = "",
                                            imageUrl = p.imageUrl,
                                            pubDate = "",
                                            durationMins = 0,
                                            podcastId = p.id
                                        )
                                    }

                                    for (ef in (titleHits + descHits)) {
                                        if (perPodcastAdded.size >= 200) break
                                        perPodcastAdded.add(resolveHit(ef) to p)
                                        total += 1
                                        if (total >= 1000) break
                                    }

                                    eps.addAll(perPodcastAdded)
                                    if (total >= 1000) break
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("PodcastsFragment", "IndexStore unavailable, falling back to cached-per-podcast search: ${e.message}")
                        }

                        // Fallback: if index returned nothing or wasn't available, search cached episodes per-podcast
                        if (eps.isEmpty()) {
                            val perPodcastLimit = 200
                            for (p in allPodcasts) {
                                if (!kotlin.coroutines.coroutineContext.isActive) break
                                val hits = repository.searchCachedEpisodes(p.id, qLower, perPodcastLimit)
                                for (ep in hits) {
                                    eps.add(ep to p)
                                    if (eps.size >= 50) break
                                }
                                if (eps.size >= 50) break
                            }
                        }
                    }

                    // Apply podcast-level filter and episode duration filter so the UI filters apply to episode hits
                    val epsFiltered = eps.filter { (ep, pod) ->
                        repository.filterPodcasts(listOf(pod), currentFilter).isNotEmpty()
                    }.filter { (ep, _) ->
                        ep.durationMins in currentFilter.minDuration..currentFilter.maxDuration
                    }

                    // Prioritise episodes that match the query in their TITLE, then description;
                    // use the selected `currentSort` as a tiebreaker (recency / popularity / alphabetical).
                    val patterns = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy", "dd MMM yyyy")
                    fun epochOf(ep: Episode): Long {
                        return patterns.firstNotNullOfOrNull { pattern ->
                            try { java.text.SimpleDateFormat(pattern, Locale.US).parse(ep.pubDate)?.time } catch (_: Exception) { null }
                        } ?: 0L
                    }

                    val matchRank: (Pair<Episode, Podcast>) -> Int = { pair ->
                        when {
                            repository.textMatchesNormalized(pair.first.title, q) -> 2
                            repository.textMatchesNormalized(pair.first.description, q) -> 1
                            else -> 0
                        }
                    }

                    val cmp = Comparator<Pair<Episode, Podcast>> { a, b ->
                        val ma = matchRank(a)
                        val mb = matchRank(b)
                        if (ma != mb) return@Comparator mb - ma // higher matchRank first

                        when (currentSort) {
                            "Most recent" -> epochOf(b.first).compareTo(epochOf(a.first))
                            "Alphabetical (A-Z)" -> {
                                val c = a.first.title.compareTo(b.first.title, ignoreCase = true)
                                if (c != 0) return@Comparator c
                                return@Comparator a.second.title.compareTo(b.second.title, ignoreCase = true)
                            }
                            else -> {
                                val c = getPopularRank(a.second).compareTo(getPopularRank(b.second))
                                if (c != 0) return@Comparator c
                                return@Comparator epochOf(b.first).compareTo(epochOf(a.first))
                            }
                        }
                    }

                    val sorted = epsFiltered.sortedWith(cmp)

                    // Eagerly enrich index-only hits (bounded & capped) so the user always sees
                    // a date, a non-zero duration and a playable audio URL for delivered results.
                    val resultList = sorted.toMutableList()

                    val incomplete = resultList.filter { (ep, _) -> ep.audioUrl.isBlank() || ep.pubDate.isBlank() || ep.durationMins <= 0 }
                    if (incomplete.isNotEmpty()) {
                        val podcastsToResolve = incomplete.map { it.second }.distinctBy { it.id }.take(6)

                        try {
                            val fetchedLists = coroutineScope {
                                val deferreds = podcastsToResolve.map { pod ->
                                    async(Dispatchers.IO) {
                                        try {
                                            withTimeoutOrNull(1200L) { repository.fetchEpisodesIfNeeded(pod) }
                                        } catch (e: Exception) {
                                            android.util.Log.w("PodcastsFragment", "Timed/enriched fetch failed for ${pod.id}: ${e.message}")
                                            null
                                        }
                                    }
                                }
                                deferreds.mapNotNull { runCatching { it.await() }.getOrNull() }
                            }

                            if (fetchedLists.isNotEmpty()) {
                                val merged = resultList.map { (ep, p) ->
                                    val improved = fetchedLists.firstOrNull { it.isNotEmpty() && it[0].podcastId == p.id }?.firstOrNull { it.id == ep.id }
                                    if (improved != null) improved to p else ep to p
                                }.toMutableList()

                                val filtered = merged.filter { (ep, _) -> ep.audioUrl.isNotBlank() && ep.pubDate.isNotBlank() && ep.durationMins > 0 }
                                if (filtered.isNotEmpty()) {
                                    resultList.clear()
                                    resultList.addAll(filtered)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("PodcastsFragment", "Synchronous enrichment failed: ${e.message}")
                        }

                        // Launch background resolution for any remaining incomplete hits so UI upgrades later
                        val stillIncomplete = resultList.filter { (ep, _) -> ep.audioUrl.isBlank() || ep.pubDate.isBlank() || ep.durationMins <= 0 }
                        if (stillIncomplete.isNotEmpty()) {
                            val podcastsBg = stillIncomplete.map { it.second }.distinctBy { it.id }.take(6)
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    for (pod in podcastsBg) {
                                        if (!isActive) break
                                        val fetched = try { repository.fetchEpisodesIfNeeded(pod) } catch (_: Exception) { null }
                                        if (fetched.isNullOrEmpty()) continue
                                        val updated = resultList.map { (ep, p) ->
                                            val improved = if (p.id == pod.id) fetched.find { it.id == ep.id } else null
                                            if (improved != null) improved to p else ep to p
                                        }
                                        withContext(Dispatchers.Main) {
                                            if (!isAdded) return@withContext
                                            val activeNorm = normalizeQuery(viewModel.activeSearchQuery.value ?: searchQuery)
                                            if (activeNorm == qLower) {
                                                searchAdapter?.updateEpisodeMatches(updated.filter { it.first.audioUrl.isNotEmpty() || it.first.durationMins > 0 })
                                                persistCachedSearch(PodcastsViewModel.SearchCache(q, titleMatches.toList(), descMatches.toList(), updated.toList(), isComplete = true))
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("PodcastsFragment", "Background episode resolution failed: ${e.message}")
                                }
                            }
                        }
                    }

                    // If enrichment removed all index-hits, fall back to cached-per-podcast search so we surface
                    // playable episodes rather than empty stubs.
                    if (resultList.isEmpty()) {
                        // If index couldn't produce playable hits, expand the cached-per-podcast search
                        // to a much larger per-podcast limit so more episodes are returned up-front.
                        val perPodcastLimit = 200
                        for (p in allPodcasts) {
                            if (!kotlin.coroutines.coroutineContext.isActive) break
                            val hits = repository.searchCachedEpisodes(p.id, qLower, perPodcastLimit)
                            for (ep in hits) {
                                if (ep.audioUrl.isNotBlank() && ep.pubDate.isNotBlank() && ep.durationMins > 0) {
                                    resultList.add(ep to p)
                                }
                                if (resultList.size >= 1000) break
                            }
                            if (resultList.size >= 1000) break
                        }
                    }

                    resultList
                }

                searchAdapter = createSearchAdapter(titleMatches, descMatches, episodeMatches)
                // record how many episode items are shown so pagination can append more later
                displayedEpisodeCount = episodeMatches.size
                persistCachedSearch(PodcastsViewModel.SearchCache(q, titleMatches.toList(), descMatches.toList(), episodeMatches.toList(), isComplete = true))

                showResultsSafely(recyclerView, searchAdapter, isSearchAdapter = true, hasContent = titleMatches.isNotEmpty() || descMatches.isNotEmpty() || episodeMatches.isNotEmpty(), emptyState)
                loadingView?.visibility = View.GONE
            } finally {
                showSpinnerJob.cancel()
                loadingView?.visibility = View.GONE
            }
        }
    }

    private fun getPopularRank(podcast: Podcast): Int {
        for ((key, rank) in POPULAR_RANKING) {
            if (podcast.title.equals(key, ignoreCase = true)) return rank
        }
        return 101
    }

    companion object {
        private val POPULAR_RANKING = mapOf(
            "Global News Podcast" to 1,
            "Football Daily" to 2,
            "Newshour" to 3,
            "Radio 1's All Day Breakfast with Greg James" to 4,
            "Test Match Special" to 5,
            "Best of Nolan" to 6,
            "Rugby Union Weekly" to 7,
            "Wake Up To Money" to 8,
            "Ten To The Top" to 9,
            "Witness History" to 10,
            "Focus on Africa" to 11,
            "BBC Music Introducing Mixtape" to 12,
            "F1: Chequered Flag" to 13,
            "BBC Introducing in Oxfordshire & Berkshire" to 14,
            "Business Daily" to 15,
            "Americast" to 16,
            "CrowdScience" to 17,
            "The Interview" to 18,
            "Six O'Clock News" to 19,
            "Science In Action" to 20,
            "Today in Parliament" to 21,
            "Talkback" to 22,
            "Access All: Disability News and Mental Health" to 23,
            "Fighting Talk" to 24,
            "World Business Report" to 25,
            "Business Matters" to 26,
            "Tailenders" to 27,
            "Moral Maze" to 28,
            "Any Questions? and Any Answers?" to 29,
            "Health Check" to 30,
            "Friday Night Comedy from BBC Radio 4" to 31,
            "BBC Inside Science" to 32,
            "People Fixing the World" to 33,
            "Add to Playlist" to 34,
            "In Touch" to 35,
            "Limelight" to 36,
            "Evil Genius with Russell Kane" to 37,
            "Africa Daily" to 38,
            "Broadcasting House" to 39,
            "From Our Own Correspondent" to 40,
            "Newscast" to 41,
            "Derby County" to 42,
            "Learning English Stories" to 43,
            "Tech Life" to 44,
            "World Football" to 45,
            "Private Passions" to 46,
            "Sunday Supplement" to 47,
            "Drama of the Week" to 48,
            "Sporting Witness" to 49,
            "File on 4 Investigates" to 50,
            "Nottingham Forest: Shut Up and Show More Football" to 51,
            "Soul Music" to 52,
            "Westminster Hour" to 53,
            "Inside Health" to 54,
            "5 Live's World Football Phone-in" to 55,
            "Over to You" to 56,
            "Political Thinking with Nick Robinson" to 57,
            "Sport's Strangest Crimes" to 58,
            "Inheritance Tracks" to 59,
            "The Archers" to 60,
            "Profile" to 61,
            "Sacked in the Morning" to 62,
            "The World Tonight" to 63,
            "Record Review Podcast" to 64,
            "Composer of the Week" to 65,
            "Short Cuts" to 66,
            "The History Hour" to 67,
            "The Archers Omnibus" to 68,
            "The Lazarus Heist" to 69,
            "Bad People" to 70,
            "Jill Scott's Coffee Club" to 71,
            "5 Live Boxing with Steve Bunce" to 72,
            "Unexpected Elements" to 73,
            "The Inquiry" to 74,
            "Not by the Playbook" to 75,
            "The Bottom Line" to 76,
            "Stumped" to 77,
            "Sliced Bread" to 78,
            "Sound of Cinema" to 79,
            "5 Live News Specials" to 80,
            "Comedy of the Week" to 81,
            "Curious Cases" to 82,
            "Breaking the News" to 83,
            "The Skewer" to 84,
            "5 Live Sport: All About..." to 85,
            "The Briefing Room" to 86,
            "The Early Music Show" to 87,
            "The Life Scientific" to 88,
            "5 Live Rugby League" to 89,
            "Learning English from the News" to 90,
            "The GAA Social" to 91,
            "Sportsworld" to 92,
            "Assume Nothing" to 93,
            "The LGBT Sport Podcast" to 94,
            "Fairy Meadow" to 95,
            "Kermode and Mayo's Film Review" to 96,
            "In Our Time: History" to 97,
            "Digital Planet" to 98,
            "Just One Thing - with Michael Mosley" to 99,
            "Scientifically..." to 100
        )
    }
}
