package com.hyliankid14.bbcradioplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import android.net.Uri
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.RadioGroup
import android.view.View
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.core.graphics.ColorUtils
import android.view.WindowManager
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.tabs.TabLayout
import com.google.android.material.button.MaterialButtonToggleGroup
import com.hyliankid14.bbcradioplayer.PodcastSubscriptions

class MainActivity : AppCompatActivity() {
    private lateinit var stationsList: RecyclerView
    private lateinit var settingsContainer: View
    private lateinit var fragmentContainer: View
    private lateinit var staticContentContainer: View
    private lateinit var stationsView: View
    private lateinit var stationsContent: View
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniPlayerTitle: TextView
    private lateinit var miniPlayerSubtitle: ScrollingTextView
    private lateinit var miniPlayerProgress: android.widget.ProgressBar
    private lateinit var miniPlayerArtwork: ImageView
    private lateinit var miniPlayerPrevious: ImageButton
    private lateinit var miniPlayerPlayPause: ImageButton
    private lateinit var miniPlayerNext: ImageButton
    private lateinit var miniPlayerStop: ImageButton
    private lateinit var miniPlayerFavorite: ImageButton
    private lateinit var createDocumentLauncher: ActivityResultLauncher<String>
    private lateinit var openDocumentLauncher: ActivityResultLauncher<Array<String>>
    // May be absent in some builds; make nullable and handle safely
    private var filterButtonsContainer: View? = null
    private lateinit var tabLayout: TabLayout
    private var categorizedAdapter: CategorizedStationAdapter? = null
    private var currentTabIndex = 0
    private var savedItemAnimator: androidx.recyclerview.widget.RecyclerView.ItemAnimator? = null
    private var selectionFromSwipe = false
    
    private var currentMode = "list" // "favorites", "list", or "settings"
    private var miniPlayerUpdateTimer: Thread? = null
    private var lastArtworkUrl: String? = null
    private val showChangeListener: (CurrentShow) -> Unit = { show ->
        runOnUiThread { updateMiniPlayerFromShow(show) }
    }
    private val backStackListener = FragmentManager.OnBackStackChangedListener {
        updateActionBarTitle()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before creating the view
        val theme = ThemePreference.getTheme(this)
        ThemeManager.applyTheme(theme)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportFragmentManager.addOnBackStackChangedListener(backStackListener)

        // Use Material Top App Bar instead of a classic action bar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.top_app_bar)
        try {
            setSupportActionBar(toolbar)
        } catch (e: IllegalStateException) {
            android.util.Log.w("MainActivity", "Could not set support action bar: ${e.message}")
        }

        stationsList = findViewById(R.id.stations_list)
        stationsList.layoutManager = LinearLayoutManager(this)
        
        fragmentContainer = findViewById(R.id.fragment_container)
        staticContentContainer = findViewById(R.id.static_content_container)
        stationsView = findViewById(R.id.stations_view)
        stationsContent = findViewById(R.id.stations_content)
        
        // Try multiple ids because some build/tooling combinations either generate the include id
        // or only the ids from the included layout itself. Fall back to a hidden placeholder if none found.
        val fbRuntimeId = resources.getIdentifier("filter_buttons", "id", packageName).takeIf { it != 0 }
        val fbRuntimeView = fbRuntimeId?.let { findViewById<View?>(it) }
        filterButtonsContainer = findViewById<View?>(R.id.filter_buttons_include)
            ?: findViewById<View?>(R.id.filter_tabs)
            ?: fbRuntimeView
            ?: run {
                android.util.Log.w("MainActivity", "Filter buttons view not found; continuing without it")
                // Create an invisible placeholder so callers can safely invoke visibility changes
                View(this).apply { visibility = View.GONE }
            }
        
        settingsContainer = findViewById(R.id.settings_container)
        
        bottomNavigation = findViewById(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_favorites -> {
                    showFavorites()
                    true
                }
                R.id.navigation_list -> {
                    showAllStations()
                    true
                }
                R.id.navigation_podcasts -> {
                    showPodcasts()
                    true
                }
                R.id.navigation_settings -> {
                    showSettings()
                    true
                }
                else -> false
            }
        }
        
        // Register Activity Result Launchers for Export / Import
        createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            if (uri != null) {
                Thread {
                    runOnUiThread { Toast.makeText(this, "Export started...", Toast.LENGTH_SHORT).show() }
                    val success = exportPreferencesToUri(uri)
                    runOnUiThread { Toast.makeText(this, if (success) "Export successful" else "Export failed", Toast.LENGTH_LONG).show() }
                }.start()
            }
        }

        openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) { }
                Thread {
                    runOnUiThread { Toast.makeText(this, "Import started...", Toast.LENGTH_SHORT).show() }
                    val success = importPreferencesFromUri(uri)
                    runOnUiThread {
                        Toast.makeText(this, if (success) "Import successful" else "Import failed", Toast.LENGTH_LONG).show()
                        ThemeManager.applyTheme(ThemePreference.getTheme(this))
                        refreshCurrentView()
                        refreshSavedEpisodesSection()
                        updateMiniPlayer()
                        // Ensure settings UI reflects the newly imported preferences immediately
                        setupSettings()
                        // Recreate the activity so any remaining listeners and UI are fully refreshed
                        recreate()
                    }
                }.start()
            }
        }

        // Setup settings controls
        setupSettings()
        
        // Mini player views
        miniPlayer = findViewById(R.id.mini_player)
        miniPlayerTitle = findViewById(R.id.mini_player_title)
        miniPlayerSubtitle = findViewById(R.id.mini_player_subtitle)
        miniPlayerProgress = findViewById(R.id.mini_player_progress)
        miniPlayerArtwork = findViewById(R.id.mini_player_artwork)
        miniPlayerPrevious = findViewById(R.id.mini_player_previous)
        miniPlayerPlayPause = findViewById(R.id.mini_player_play_pause)
        miniPlayerNext = findViewById(R.id.mini_player_next)
        miniPlayerStop = findViewById(R.id.mini_player_stop)
        miniPlayerFavorite = findViewById(R.id.mini_player_favorite)
        val miniPlayerTextContainer = findViewById<View>(R.id.mini_player_text_container)
        
        miniPlayerPrevious.setOnClickListener { skipToPrevious() }
        miniPlayerPlayPause.setOnClickListener { togglePlayPause() }
        miniPlayerNext.setOnClickListener { skipToNext() }
        miniPlayerStop.setOnClickListener { stopPlayback() }
        miniPlayerFavorite.setOnClickListener { toggleMiniPlayerFavorite() }
        miniPlayerArtwork.setOnClickListener { openNowPlaying() }
        miniPlayerTextContainer.setOnClickListener { openNowPlaying() }

        
        // Ensure mini player state is in sync immediately (avoids flicker on theme change)
        updateMiniPlayer()
        // Prevent navigation bar from resizing/moving when the keyboard appears
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        
        // Register listener for show changes
        PlaybackStateHelper.onShowChange(showChangeListener)
        
        // Restore previous section when recreating (e.g., theme change), otherwise default to list
        val restoredNavSelection = savedInstanceState?.getInt("selectedNavId")
        if (restoredNavSelection != null) {
            bottomNavigation.selectedItemId = restoredNavSelection
        } else {
            bottomNavigation.selectedItemId = R.id.navigation_list
        }

        updateActionBarTitle()
        
        // Start polling for playback state updates
        startPlaybackStateUpdates()

        // Handle any incoming intents that request opening a specific podcast or mode
        handleOpenPodcastIntent(intent)
        handleOpenModeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenPodcastIntent(intent)
        handleOpenModeIntent(intent)
    }

    private fun handleOpenModeIntent(intent: Intent?) {
        val mode = intent?.getStringExtra("open_mode") ?: return
        when (mode) {
            "favorites" -> showFavorites()
            "list" -> showAllStations()
            else -> {
                // Unknown mode - ignore
            }
        }
    }
    
    private fun refreshCurrentView() {
        // Always hide history when refreshing view (unless Favorites will explicitly show it)
        hideHistoryViews()
        // Clear show cache in the current adapter and refresh the view
        when (currentMode) {
            "list" -> {
                // Clear cache in categorized adapter if it exists
                categorizedAdapter?.clearShowCache()
                categorizedAdapter?.notifyDataSetChanged()
            }
            "favorites" -> {
                // Recreate favorites view to clear its cache
                showFavorites()
            }
        }
    }

    // Refresh the Saved Episodes UI card and adapter (called after import or when saved episodes change)
    private fun refreshSavedEpisodesSection() {
        try {
            val savedContainer = findViewById<View>(R.id.saved_episodes_container)
            if (currentMode != "favorites") {
                // Only show saved episodes when the Favorites view is active — avoids overlap in other views
                savedContainer.visibility = View.GONE
                return
            }

            val savedEntries = SavedEpisodes.getSavedEntries(this)
            val savedRecycler = findViewById<RecyclerView>(R.id.saved_episodes_recycler)

            // Prepare saved episodes recycler and adapter; visibility is controlled by the "Saved" tab
            if (savedEntries.isNotEmpty()) {
                savedRecycler.layoutManager = LinearLayoutManager(this)
                savedRecycler.isNestedScrollingEnabled = false
                val savedAdapter = SavedEpisodesAdapter(this, savedEntries, onPlayEpisode = { episode, podcastTitle, podcastImage ->
                    val intent = android.content.Intent(this, RadioService::class.java).apply {
                        action = RadioService.ACTION_PLAY_PODCAST_EPISODE
                        putExtra(RadioService.EXTRA_EPISODE, episode)
                        putExtra(RadioService.EXTRA_PODCAST_ID, episode.podcastId)
                        putExtra(RadioService.EXTRA_PODCAST_TITLE, podcastTitle)
                        putExtra(RadioService.EXTRA_PODCAST_IMAGE, episode.imageUrl.takeIf { it.isNotEmpty() } ?: podcastImage)
                    }
                    startService(intent)
                }, onOpenEpisode = { episode, podcastTitle, podcastImage ->
                    val intent = android.content.Intent(this, NowPlayingActivity::class.java).apply {
                        putExtra("preview_episode", episode)
                        putExtra("preview_use_play_ui", true)
                        putExtra("preview_podcast_title", podcastTitle)
                        putExtra("preview_podcast_image", episode.imageUrl.takeIf { it.isNotEmpty() } ?: podcastImage)
                    }
                    startActivity(intent)
                }, onRemoveSaved = { id ->
                    SavedEpisodes.remove(this, id)
                    val updated = SavedEpisodes.getSavedEntries(this)
                    savedRecycler.adapter?.let { (it as? SavedEpisodesAdapter)?.updateEntries(updated) }
                })

                savedRecycler.adapter = savedAdapter

                // Show the saved episodes immediately if the Favorites view is active AND the Saved tab is selected.
                val toggle = try { findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.favorites_toggle_group) } catch (_: Exception) { null }
                val savedTabActive = (currentMode == "favorites" && (toggle?.checkedButtonId == R.id.fav_tab_saved))
                if (savedTabActive) {
                    savedRecycler.visibility = View.VISIBLE
                    savedContainer.visibility = View.VISIBLE
                } else {
                    // Keep the container hidden by default; the Saved tab will reveal it when selected
                    savedRecycler.visibility = View.GONE
                    savedContainer.visibility = View.GONE
                }
            } else {
                savedRecycler.adapter = null
                savedContainer.visibility = View.GONE
            }
        } catch (e: Exception) {
            // Swallow — UI refresh should never crash the app
            android.util.Log.w("MainActivity", "refreshSavedEpisodesSection failed: ${e.message}")
        }
    }

    // Refresh the History UI card and adapter (most-recent-first)
    private fun refreshHistorySection() {
        try {
            val historyContainer = findViewById<View>(R.id.favorites_history_container)
            if (currentMode != "favorites") {
                historyContainer.visibility = View.GONE
                return
            }

            val historyEntries = PlayedHistoryPreference.getHistory(this)
            val historyRecycler = findViewById<RecyclerView>(R.id.favorites_history_recycler)

            // Only reveal the history RecyclerView when the Favorites *History* sub-tab is active.
            val toggle = try { findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.favorites_toggle_group) } catch (_: Exception) { null }
            val historyTabActive = (toggle?.checkedButtonId == R.id.fav_tab_history)

            if (historyEntries.isNotEmpty()) {
                historyRecycler.layoutManager = LinearLayoutManager(this)
                historyRecycler.isNestedScrollingEnabled = false
                val adapter = PlayedHistoryAdapter(this, historyEntries, onPlayEpisode = { episode, podcastTitle, podcastImage ->
                    val intent = android.content.Intent(this, RadioService::class.java).apply {
                        action = RadioService.ACTION_PLAY_PODCAST_EPISODE
                        putExtra(RadioService.EXTRA_EPISODE, episode)
                        putExtra(RadioService.EXTRA_PODCAST_ID, episode.podcastId)
                        putExtra(RadioService.EXTRA_PODCAST_TITLE, podcastTitle)
                        putExtra(RadioService.EXTRA_PODCAST_IMAGE, podcastImage)
                    }
                    startService(intent)
                }, onOpenEpisode = { episode, podcastTitle, podcastImage ->
                    val intent = android.content.Intent(this, NowPlayingActivity::class.java).apply {
                        putExtra("preview_episode", episode)
                        putExtra("preview_use_play_ui", true)
                        putExtra("preview_podcast_title", podcastTitle)
                        putExtra("preview_podcast_image", podcastImage)
                    }
                    startActivity(intent)
                })

                // Always keep the adapter up-to-date, but only make the views visible when the History sub-tab is selected.
                historyRecycler.adapter = adapter
                if (historyTabActive) {
                    historyRecycler.visibility = View.VISIBLE
                    historyContainer.visibility = View.VISIBLE
                    try { historyRecycler.scrollToPosition(0) } catch (_: Exception) { }
                } else {
                    // Keep the UI hidden when another Favorites sub-tab is active
                    historyRecycler.visibility = View.GONE
                    historyContainer.visibility = View.GONE
                }
            } else {
                historyRecycler.adapter = null
                historyContainer.visibility = View.GONE
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "refreshHistorySection failed: ${e.message}")
        }
    }

    // Hide history views (centralized) --------------------------------------------------------
    private fun hideHistoryViews() {
        try {
            val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.favorites_history_recycler)
            val container = findViewById<View>(R.id.favorites_history_container)
            // Clear adapter defensively to prevent stale items remaining attached after view reparenting
            try { rv?.adapter = null } catch (_: Exception) { }
            rv?.visibility = View.GONE
            container?.visibility = View.GONE
        } catch (_: Exception) { }
    }

    private fun showAllStations() {
        // Ensure history UI is hidden when leaving Favorites
        hideHistoryViews()
        currentMode = "list"
        fragmentContainer.visibility = View.GONE
        staticContentContainer.visibility = View.VISIBLE
        stationsView.visibility = View.VISIBLE
        stationsList.visibility = View.VISIBLE
        filterButtonsContainer?.visibility = View.VISIBLE
        settingsContainer.visibility = View.GONE
        // Ensure action bar reflects the section and clear any podcast-specific up affordance
        supportActionBar?.apply {
            show()
            title = "All Stations"
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowHomeEnabled(false)
        }
        
        // Hide subscribed podcasts section (only show in Favorites)
        val favoritesPodcastsContainer = findViewById<View>(R.id.favorites_podcasts_container)
        favoritesPodcastsContainer.visibility = View.GONE
        // Hide the favourites toggle group when showing all stations
        try {
            findViewById<MaterialButtonToggleGroup>(R.id.favorites_toggle_group).visibility = View.GONE
        } catch (_: Exception) { }
        
        // Default to National category
        showCategoryStations(StationCategory.NATIONAL)
        setupFilterButtons()
        // Ensure saved episodes UI is hidden when switching to All Stations
        refreshSavedEpisodesSection()
        
        // Hide filter buttons if not available
        filterButtonsContainer?.visibility = View.VISIBLE
    }

    private fun showFavorites() {
        currentMode = "favorites"
        fragmentContainer.visibility = View.GONE
        staticContentContainer.visibility = View.VISIBLE
        stationsView.visibility = View.VISIBLE
        stationsList.visibility = View.VISIBLE
        filterButtonsContainer?.visibility = View.GONE
        settingsContainer.visibility = View.GONE
        // Ensure action bar reflects the section and clear any podcast-specific up affordance
        supportActionBar?.apply {
            show()
            title = "Favourites"
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowHomeEnabled(false)
        }
        
        val favoritesPodcastsContainer = findViewById<View>(R.id.favorites_podcasts_container)
        val favoritesPodcastsRecycler = findViewById<RecyclerView>(R.id.favorites_podcasts_recycler)
        val savedContainer = findViewById<View>(R.id.saved_episodes_container)
        val savedRecycler = findViewById<RecyclerView>(R.id.saved_episodes_recycler)
        val historyContainer = findViewById<View>(R.id.favorites_history_container)
        val favoritesToggle = findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.favorites_toggle_group)
        // Ensure the favourites toggle group is visible when in Favorites
        try { favoritesToggle.visibility = View.VISIBLE } catch (_: Exception) { }

        // Key for persisting the last-selected Favorites sub-tab (declare once)
        val LAST_FAV_TAB_KEY = "last_fav_tab_id"

        // Ensure the favorites-related containers are near the top of the parent column so they appear
        // above other content when the Favorites view is selected.
        val parent = favoritesPodcastsContainer.parent as? android.view.ViewGroup
        parent?.let {
            try {
                it.removeView(favoritesPodcastsContainer)
                it.addView(favoritesPodcastsContainer, 1)
            } catch (_: Exception) { /* best-effort */ }
            try {
                it.removeView(savedContainer)
                it.addView(savedContainer, 2)
            } catch (_: Exception) { /* best-effort */ }
            try {
                it.removeView(historyContainer)
                it.addView(historyContainer, 3)
            } catch (_: Exception) { /* best-effort */ }
        }

        // Ensure only the last-accessed favorites group is visible immediately (avoid flicker / defaulting)
        try {
            val prefs = getPreferences(android.content.Context.MODE_PRIVATE)
            val candidateIds = listOf(R.id.fav_tab_stations, R.id.fav_tab_subscribed, R.id.fav_tab_saved, R.id.fav_tab_history)
            var initialLastChecked = prefs.getInt(LAST_FAV_TAB_KEY, R.id.fav_tab_stations)
            if (!candidateIds.contains(initialLastChecked)) initialLastChecked = R.id.fav_tab_stations

            stationsList.visibility = if (initialLastChecked == R.id.fav_tab_stations) View.VISIBLE else View.GONE
            favoritesPodcastsContainer.visibility = if (initialLastChecked == R.id.fav_tab_subscribed) View.VISIBLE else View.GONE
            savedContainer.visibility = if (initialLastChecked == R.id.fav_tab_saved) View.VISIBLE else View.GONE
            historyContainer.visibility = if (initialLastChecked == R.id.fav_tab_history) View.VISIBLE else View.GONE
        } catch (_: Exception) { }

        val stations = FavoritesPreference.getFavorites(this).toMutableList()
        val adapter = FavoritesAdapter(this, stations, { stationId ->
            playStation(stationId)
        }, { _ ->
            // Do nothing to prevent list jump
        }, {
            // Save the new order when changed
            FavoritesPreference.saveFavoritesOrder(this, stations.map { it.id })
        })
        stationsList.adapter = adapter

        // Wire favorites tab group to show/hide the four sub-views — implementation moved to class-level `showFavoritesTab` to allow reuse from other lifecycle methods.

        // Restore last-selected favorites tab (fall back to Stations)
        val prefs = getPreferences(android.content.Context.MODE_PRIVATE)
        val candidateIds = listOf(R.id.fav_tab_stations, R.id.fav_tab_subscribed, R.id.fav_tab_saved, R.id.fav_tab_history)
        var lastChecked = prefs.getInt(LAST_FAV_TAB_KEY, R.id.fav_tab_stations)
        if (!candidateIds.contains(lastChecked)) lastChecked = R.id.fav_tab_stations
        try {
            favoritesToggle.check(lastChecked)
        } catch (_: Exception) { /* ignore */ }
        // Ensure UI and section match restored selection
        updateFavoritesToggleVisuals(lastChecked)
        when (lastChecked) {
            R.id.fav_tab_stations -> showFavoritesTab("stations")
            R.id.fav_tab_subscribed -> showFavoritesTab("subscribed")
            R.id.fav_tab_saved -> showFavoritesTab("saved")
            R.id.fav_tab_history -> showFavoritesTab("history")
        }



        // Initial visuals (restore last selection)
        updateFavoritesToggleVisuals(lastChecked)

        favoritesToggle.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            // Persist user's last selection so returning to Favorites restores it
            try { prefs.edit().putInt(LAST_FAV_TAB_KEY, checkedId).apply() } catch (_: Exception) { }
            updateFavoritesToggleVisuals(checkedId)
            when (checkedId) {
                R.id.fav_tab_stations -> showFavoritesTab("stations")
                R.id.fav_tab_subscribed -> showFavoritesTab("subscribed")
                R.id.fav_tab_saved -> showFavoritesTab("saved")
                R.id.fav_tab_history -> showFavoritesTab("history")
            }
        }
        
        // Setup ItemTouchHelper for drag-and-drop. We disable the default long-press start and instead
        // start drags explicitly when the user long-presses the station name or touches the drag handle.
        val itemTouchHelperCallback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }
            
            override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                adapter.moveItem(source.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            // We will call startDrag manually from the adapter's long-press so disable the default long-press behavior
            override fun isLongPressDragEnabled(): Boolean = false

            // Visual feedback when an item is selected for dragging
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    val v = viewHolder.itemView
                    v.bringToFront()
                    v.animate().scaleX(1.02f).scaleY(1.02f).alpha(0.98f).setDuration(120).start()
                    v.elevation = (16 * resources.displayMetrics.density)
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val v = viewHolder.itemView
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start()
                v.elevation = 0f
            }
        }
        
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(stationsList)

        // Wire the adapter so it can start drags when the user long-presses the title or touches the handle
        adapter.onStartDrag = { vh, screenX, screenY ->
            // Start the ItemTouchHelper drag
            itemTouchHelper.startDrag(vh)
            // Synthesize a small ACTION_DOWN on the RecyclerView at the long-press location so the
            // active pointer is attached to the recycler for subsequent MOVE events (smooth drag)
            try {
                val rv = stationsList
                val rvLoc = IntArray(2).also { rv.getLocationOnScreen(it) }
                val x = (screenX - rvLoc[0]).toFloat()
                val y = (screenY - rvLoc[1]).toFloat()
                val now = android.os.SystemClock.uptimeMillis()
                val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
                rv.dispatchTouchEvent(down)
                down.recycle()
            } catch (_: Exception) { }
        }

        // Load subscribed podcasts into Favorites section — do not force visibility unless the Subscribed tab was last-selected
        val subscribedIds = PodcastSubscriptions.getSubscribedIds(this)
        val prefsLocal = getPreferences(android.content.Context.MODE_PRIVATE)
        val lastFav = prefsLocal.getInt(LAST_FAV_TAB_KEY, R.id.fav_tab_stations)

        if (subscribedIds.isNotEmpty()) {
            favoritesPodcastsRecycler.layoutManager = LinearLayoutManager(this)
            // Start hidden; the toggle/tab will reveal this when appropriate
            favoritesPodcastsRecycler.visibility = View.GONE
            favoritesPodcastsRecycler.isNestedScrollingEnabled = false

            // Refresh Saved Episodes data (visibility itself handled by the Saved tab)
            refreshSavedEpisodesSection()

            val repo = PodcastRepository(this)
            Thread {
                val all = try { kotlinx.coroutines.runBlocking { repo.fetchPodcasts(false) } } catch (e: Exception) { emptyList<Podcast>() }
                var subs = all.filter { subscribedIds.contains(it.id) }
                // Fetch cached latest update epochs and sort subscribed podcasts by newest update first
                val updates = try { kotlinx.coroutines.runBlocking { repo.fetchLatestUpdates(subs) } } catch (e: Exception) { emptyMap<String, Long>() }
                subs = subs.sortedByDescending { updates[it.id] ?: 0L }
                // Determine which subscriptions have unseen episodes (latest update > last played epoch)
                val newSet = subs.filter { p ->
                    val latest = updates[p.id] ?: 0L
                    val lastPlayed = PlayedEpisodesPreference.getLastPlayedEpoch(this, p.id)
                    latest > lastPlayed
                }.map { it.id }.toSet()
                runOnUiThread {
                    val podcastAdapter = PodcastAdapter(this, onPodcastClick = { podcast ->
                        // Show app bar so podcast title and back button are visible
                        supportActionBar?.show()
                        // Navigate to podcast detail
                        fragmentContainer.visibility = View.VISIBLE
                        staticContentContainer.visibility = View.GONE
                        val detailFragment = PodcastDetailFragment().apply {
                            arguments = android.os.Bundle().apply { putParcelable("podcast", podcast) }
                        }
                        supportFragmentManager.beginTransaction().apply {
                            replace(R.id.fragment_container, detailFragment)
                            addToBackStack(null)
                            commit()
                        }
                    }, highlightSubscribed = true, showSubscribedIcon = false)

                    favoritesPodcastsRecycler.adapter = podcastAdapter
                    podcastAdapter.updatePodcasts(subs)
                    podcastAdapter.updateNewEpisodes(newSet)

                    // Reveal recycler only if the Subscribed tab is actually selected right now
                    val toggle = try { findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.favorites_toggle_group) } catch (_: Exception) { null }
                    val subscribedTabActive = (currentMode == "favorites" && (toggle?.checkedButtonId == R.id.fav_tab_subscribed || lastFav == R.id.fav_tab_subscribed))
                    if (subscribedTabActive) {
                        favoritesPodcastsRecycler.visibility = View.VISIBLE
                        favoritesPodcastsContainer.visibility = View.VISIBLE
                    } else {
                        // keep hidden until the user explicitly selects the Subscribed tab
                        favoritesPodcastsRecycler.visibility = View.GONE
                    }
                }
            }.start()
        } else {
            // No subscriptions — ensure the container remains hidden
            favoritesPodcastsRecycler.adapter = null
            favoritesPodcastsContainer.visibility = View.GONE
        }

        // Load saved episodes and display underneath Subscribed Podcasts in the Favorites section
        refreshSavedEpisodesSection()
        refreshHistorySection()
    }

    // BroadcastReceiver to respond to played-status changes and update the "new episodes" indicators
    private val playedStatusReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            try {
                // Recompute which subscribed podcasts have newer episodes and update adapter
                val subscribedIds = PodcastSubscriptions.getSubscribedIds(this@MainActivity)
                if (subscribedIds.isEmpty()) return
                Thread {
                    val repo = PodcastRepository(this@MainActivity)
                    val all = try { kotlinx.coroutines.runBlocking { repo.fetchPodcasts(false) } } catch (e: Exception) { emptyList<Podcast>() }
                    val subs = all.filter { subscribedIds.contains(it.id) }
                    val updates = try { kotlinx.coroutines.runBlocking { repo.fetchLatestUpdates(subs) } } catch (e: Exception) { emptyMap<String, Long>() }
                    val newSet = subs.filter { p ->
                        val latest = updates[p.id] ?: 0L
                        val lastPlayed = PlayedEpisodesPreference.getLastPlayedEpoch(this@MainActivity, p.id)
                        latest > lastPlayed
                    }.map { it.id }.toSet()
                    runOnUiThread {
                        val rv = try { findViewById<RecyclerView>(R.id.favorites_podcasts_recycler) } catch (_: Exception) { null }
                        val adapter = rv?.adapter
                        if (adapter is PodcastAdapter) {
                            adapter.updateNewEpisodes(newSet)
                        }
                    }
                }.start()
            } catch (_: Exception) {}
        }
    }

    // BroadcastReceiver to refresh History UI when the played-history store changes
    private val historyChangedReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            try {
                runOnUiThread { refreshHistorySection() }
            } catch (_: Exception) { }
        }
    }

    /**
     * Show the requested Favorites sub-tab. Extracted to class level so it can be called from
     * lifecycle methods (onResume) and other places outside the original local scope.
     */
    private fun showFavoritesTab(tab: String) {
        when (tab) {
            "stations" -> {
                stationsList.visibility = View.VISIBLE
                findViewById<View>(R.id.favorites_podcasts_container).visibility = View.GONE
                findViewById<View>(R.id.saved_episodes_container).visibility = View.GONE
                findViewById<View>(R.id.favorites_history_container).visibility = View.GONE
                try { findViewById<RecyclerView>(R.id.favorites_history_recycler).visibility = View.GONE } catch (_: Exception) { }
            }
            "subscribed" -> {
                stationsList.visibility = View.GONE
                findViewById<View>(R.id.favorites_podcasts_container).visibility = View.VISIBLE
                findViewById<View>(R.id.saved_episodes_container).visibility = View.GONE
                findViewById<View>(R.id.favorites_history_container).visibility = View.GONE
                try { findViewById<RecyclerView>(R.id.saved_episodes_recycler).visibility = View.GONE } catch (_: Exception) { }

                // Refresh subscribed podcasts list asynchronously
                Thread {
                    try {
                        val ids = PodcastSubscriptions.getSubscribedIds(this@MainActivity)
                        if (ids.isEmpty()) {
                            runOnUiThread { findViewById<RecyclerView>(R.id.favorites_podcasts_recycler).adapter = null }
                            return@Thread
                        }

                        val repo = PodcastRepository(this@MainActivity)
                        val all = try { kotlinx.coroutines.runBlocking { repo.fetchPodcasts(false) } } catch (e: Exception) { emptyList<Podcast>() }
                        val podcasts = all.filter { ids.contains(it.id) }
                        val updates = try { kotlinx.coroutines.runBlocking { repo.fetchLatestUpdates(podcasts) } } catch (e: Exception) { emptyMap<String, Long>() }
                        val sorted = podcasts.sortedByDescending { updates[it.id] ?: 0L }
                        val itemsPodcasts = sorted.map { p ->
                            val subtitle = if ((updates[p.id] ?: 0L) > PlayedEpisodesPreference.getLastPlayedEpoch(this@MainActivity, p.id)) "New" else ""
                            MediaItem(
                                MediaDescriptionCompat.Builder()
                                    .setMediaId("podcast_${'$'}{p.id}")
                                    .setTitle(p.title)
                                    .setSubtitle(subtitle)
                                    .setIconUri(android.net.Uri.parse(p.imageUrl))
                                    .build(),
                                MediaItem.FLAG_BROWSABLE
                            )
                        }
                        runOnUiThread {
                            val rv = try { findViewById<RecyclerView>(R.id.favorites_podcasts_recycler) } catch (_: Exception) { null }
                            rv?.layoutManager = LinearLayoutManager(this@MainActivity)
                            val podcastAdapter = PodcastAdapter(this@MainActivity, onPodcastClick = { podcast ->
                                supportActionBar?.show()
                                fragmentContainer.visibility = View.VISIBLE
                                staticContentContainer.visibility = View.GONE
                                val detailFragment = PodcastDetailFragment().apply { arguments = android.os.Bundle().apply { putParcelable("podcast", podcast) } }
                                supportFragmentManager.beginTransaction().apply {
                                    replace(R.id.fragment_container, detailFragment)
                                    addToBackStack(null)
                                    commit()
                                }
                            }, highlightSubscribed = true, showSubscribedIcon = false)
                            rv?.adapter = podcastAdapter
                            podcastAdapter.updatePodcasts(sorted)
                            podcastAdapter.updateNewEpisodes(sorted.map { it.id }.toSet())
                            rv?.visibility = View.VISIBLE
                        }
                    } catch (_: Exception) { }
                }.start()
            }
            "saved" -> {
                stationsList.visibility = View.GONE
                findViewById<View>(R.id.favorites_podcasts_container).visibility = View.GONE
                findViewById<View>(R.id.saved_episodes_container).visibility = View.VISIBLE
                try { findViewById<RecyclerView>(R.id.saved_episodes_recycler).visibility = View.VISIBLE } catch (_: Exception) { }
                findViewById<View>(R.id.favorites_history_container).visibility = View.GONE
                try { findViewById<RecyclerView>(R.id.favorites_history_recycler).visibility = View.GONE } catch (_: Exception) { }
            }
            "history" -> {
                stationsList.visibility = View.GONE
                findViewById<View>(R.id.favorites_podcasts_container).visibility = View.GONE
                findViewById<View>(R.id.saved_episodes_container).visibility = View.GONE
                findViewById<View>(R.id.favorites_history_container).visibility = View.VISIBLE
                try { findViewById<RecyclerView>(R.id.favorites_history_recycler).visibility = View.VISIBLE } catch (_: Exception) { }
                // Always refresh the history contents when the tab becomes active
                try { refreshHistorySection(); findViewById<RecyclerView>(R.id.favorites_history_recycler).scrollToPosition(0) } catch (_: Exception) { }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            registerReceiver(playedStatusReceiver, android.content.IntentFilter(PlayedEpisodesPreference.ACTION_PLAYED_STATUS_CHANGED))
        } catch (_: Exception) {}
        try {
            registerReceiver(historyChangedReceiver, android.content.IntentFilter(PlayedHistoryPreference.ACTION_HISTORY_CHANGED))
        } catch (_: Exception) {}
    }



    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(playedStatusReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(historyChangedReceiver)
        } catch (_: Exception) {}
    }

    private fun showSettings() {
        // Ensure history UI is hidden when navigating away from Favorites
        hideHistoryViews()
        currentMode = "settings"
        fragmentContainer.visibility = View.GONE
        staticContentContainer.visibility = View.VISIBLE
        stationsView.visibility = View.GONE
        stationsList.visibility = View.GONE
        filterButtonsContainer?.visibility = View.GONE
        settingsContainer.visibility = View.VISIBLE
        // Ensure action bar reflects the section and clear any podcast-specific up affordance
        supportActionBar?.apply {
            show()
            title = "Settings"
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowHomeEnabled(false)
        }
        // Refresh the settings UI so controls reflect current preferences
        setupSettings()
        // Hide favourites toggle group in settings
        try { findViewById<MaterialButtonToggleGroup>(R.id.favorites_toggle_group).visibility = View.GONE } catch (_: Exception) { }
    }

    private fun showPodcasts() {
        // Ensure history UI is hidden when navigating away from Favorites
        hideHistoryViews()
        currentMode = "podcasts"
        fragmentContainer.visibility = View.VISIBLE
        staticContentContainer.visibility = View.GONE
        // Hide the global action bar so the Podcasts fragment can present its own search app bar at the top
        supportActionBar?.hide()
        // Hide favourites toggle group when viewing podcasts
        try { findViewById<MaterialButtonToggleGroup>(R.id.favorites_toggle_group).visibility = View.GONE } catch (_: Exception) { }

        // Create and show podcasts fragment
        val podcastsFragment = PodcastsFragment()
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fragment_container, podcastsFragment)
            commit()
        }
    }

    private fun handleOpenPodcastIntent(intent: Intent?) {
        val podcastId = intent?.getStringExtra("open_podcast_id") ?: return
        // Ensure podcasts UI is shown
        showPodcasts()
        // Fetch podcasts and open the matching podcast detail when available
        val repo = PodcastRepository(this)
        Thread {
            val all = try { kotlinx.coroutines.runBlocking { repo.fetchPodcasts(false) } } catch (e: Exception) { emptyList<Podcast>() }
            val match = all.find { it.id == podcastId }
            if (match != null) {
                runOnUiThread {
                    // Show app bar so podcast title and back button are visible
                    supportActionBar?.show()

                    fragmentContainer.visibility = View.VISIBLE
                    staticContentContainer.visibility = View.GONE
                    val detailFragment = PodcastDetailFragment().apply {
                        arguments = android.os.Bundle().apply { putParcelable("podcast", match) }
                    }
                    supportFragmentManager.beginTransaction().apply {
                        replace(R.id.fragment_container, detailFragment)
                        addToBackStack(null)
                        commit()
                    }
                }
            } else {
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Podcast not found", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun updateActionBarTitle() {
        val title = when (currentMode) {
            "favorites" -> "Favourites"
            "settings" -> "Settings"
            "podcasts" -> "Podcasts"
            else -> "All Stations"
        }
        supportActionBar?.title = title
    }

    // Update visuals for the favorites button group (tablet shows labels; phone icon-only)
    private fun updateFavoritesToggleVisuals(selectedId: Int) {
        val ids = listOf(R.id.fav_tab_stations, R.id.fav_tab_subscribed, R.id.fav_tab_saved, R.id.fav_tab_history)
        val labels = mapOf(
            R.id.fav_tab_stations to "Stations",
            R.id.fav_tab_subscribed to "Subscribed",
            R.id.fav_tab_saved to "Saved",
            R.id.fav_tab_history to "History"
        )
        val isTablet = try { resources.getBoolean(R.bool.is_tablet) } catch (_: Exception) { false }

        // Colors from theme for selected/unselected states
        fun getThemeColorByName(attrName: String): Int {
            return try {
                val resId = resources.getIdentifier(attrName, "attr", packageName)
                if (resId == 0) return android.graphics.Color.BLACK
                val tv = android.util.TypedValue()
                theme.resolveAttribute(resId, tv, true)
                tv.data
            } catch (_: Exception) { android.graphics.Color.BLACK }
        }

        val colorPrimaryContainer = try { getThemeColorByName("colorPrimaryContainer") } catch (_: Exception) { getThemeColorByName("colorPrimary") }
        val colorOnPrimaryContainer = try { getThemeColorByName("colorOnPrimaryContainer") } catch (_: Exception) { getThemeColorByName("colorOnPrimary") }
        val colorSurface = try { getThemeColorByName("colorSurface") } catch (_: Exception) { android.graphics.Color.WHITE }
        val colorOnSurface = try { getThemeColorByName("colorOnSurface") } catch (_: Exception) { android.graphics.Color.BLACK }

        for (id in ids) {
            try {
                val btn = findViewById<com.google.android.material.button.MaterialButton>(id)
                val lp = btn.layoutParams as? android.widget.LinearLayout.LayoutParams

                val selected = (id == selectedId)

                // Apply base layout changes (tablet: expanded selected with label; phone: icon-only but centered)
                if (!isTablet) {
                    // Icon-only on phones: center the icon horizontally/vertically and ensure equal weight
                    btn.text = ""
                    lp?.width = 0
                    lp?.weight = 1f
                    try {
                        // Remove extra icon padding so the drawable sits exactly centered when there's no text
                        btn.iconPadding = 0
                        // Remove asymmetric content padding coming from the style so icon can be centered
                        btn.setPaddingRelative(0, btn.paddingTop, 0, btn.paddingBottom)
                        // Allow the button to shrink below the default min width so equal-weight centering works
                        btn.minWidth = 0
                        // Center content inside the button (icon will be centered when text is empty)
                        btn.gravity = android.view.Gravity.CENTER
                        // Use text-relative gravity so the icon aligns as if text were present (helps centering)
                        btn.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
                    } catch (_: Exception) { }
                } else {
                    if (selected) {
                        btn.text = labels[id]
                        lp?.width = 0
                        lp?.weight = 1f
                        try { btn.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START } catch (_: Exception) { }
                    } else {
                        btn.text = ""
                        lp?.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                        lp?.weight = 0f
                        try { btn.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_START } catch (_: Exception) { }
                    }
                }

                // Apply Material 3 expressive colors/tints
                try {
                    if (selected) {
                        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(colorPrimaryContainer)
                        btn.iconTint = android.content.res.ColorStateList.valueOf(colorOnPrimaryContainer)
                        btn.setTextColor(colorOnPrimaryContainer)
                    } else {
                        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(colorSurface)
                        btn.iconTint = android.content.res.ColorStateList.valueOf(colorOnSurface)
                        btn.setTextColor(colorOnSurface)
                    }
                } catch (_: Exception) { }

                // Subtle animations and elevation
                try {
                    if (selected) {
                        btn.animate().scaleX(1.02f).scaleY(1.02f).setDuration(200).start()
                        val d = resources.displayMetrics.density
                        androidx.core.view.ViewCompat.setElevation(btn, 6f * d)
                    } else {
                        btn.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
                        androidx.core.view.ViewCompat.setElevation(btn, 0f)
                    }
                } catch (_: Exception) { }

                btn.contentDescription = labels[id]
                btn.layoutParams = lp
            } catch (_: Exception) { }
        }
    }

    private var filterButtonsSetupTried = false

    private fun setupFilterButtons() {
        // Robustly find a TabLayout: try id lookup first, then search inside the stations view
        fun findTabLayoutRecursive(v: View): com.google.android.material.tabs.TabLayout? {
            if (v is com.google.android.material.tabs.TabLayout) return v
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    val child = v.getChildAt(i)
                    val found = findTabLayoutRecursive(child)
                    if (found != null) return found
                }
            }
            return null
        }

        var tabs = findViewById<com.google.android.material.tabs.TabLayout?>(R.id.filter_tabs)
        if (tabs == null) {
            tabs = findTabLayoutRecursive(stationsView)
        }
        if (tabs == null) {
            tabs = findTabLayoutRecursive(staticContentContainer)
        }
        // If the include wrapper exists but the TabLayout id wasn't found, search inside it
        val fbContainer = filterButtonsContainer
        if (tabs == null && fbContainer is android.view.ViewGroup) {
            tabs = findTabLayoutRecursive(fbContainer)
        }

        if (tabs == null) {
            if (!filterButtonsSetupTried) {
                // Perhaps layout isn't laid out yet — try again after a layout pass
                filterButtonsSetupTried = true
                android.util.Log.d("MainActivity", "TabLayout not found yet; will retry after layout")
                stationsView.post {
                    setupFilterButtons()
                }
                return
            }

            android.util.Log.w("MainActivity", "TabLayout not found after retry; filter buttons disabled, but swipe navigation will still be enabled")
            // Ensure swipe navigation is enabled even if the tabs are missing
            setupSwipeNavigation()
            return
        }

        tabLayout = tabs

        tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                val newIndex = tab.position
                val direction = if (newIndex > currentTabIndex) 1 else -1
                android.util.Log.d("MainActivity", "Tab selected: $newIndex (current=$currentTabIndex), direction=$direction")
                currentTabIndex = newIndex

                val category = when (newIndex) {
                    0 -> StationCategory.NATIONAL
                    1 -> StationCategory.REGIONS
                    2 -> StationCategory.LOCAL
                    else -> StationCategory.NATIONAL
                }

                // If the stations content isn't laid out (width == 0), fall back to immediate update
                val wasSwipeSelection = selectionFromSwipe
                if (wasSwipeSelection) selectionFromSwipe = false
                val slowTransition = !wasSwipeSelection

                if (stationsContent.width <= 0) {
                    android.util.Log.d("MainActivity", "stationsContent not laid out yet; updating immediately")
                    showCategoryStations(category)
                } else {
                    animateListTransition(direction, {
                        showCategoryStations(category)
                    }, slowTransition)
                }
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })

        // Set National as default selected and sync index
        tabLayout.getTabAt(0)?.select()
        currentTabIndex = 0
        showCategoryStations(StationCategory.NATIONAL)

        // Enable swipe navigation on the stations list
        setupSwipeNavigation()
    }

    private fun animateListTransition(direction: Int, onFadeOutComplete: () -> Unit, slow: Boolean = false) {
        val screenWidth = stationsContent.width.toFloat().takeIf { it > 0f } ?: stationsList.width.toFloat()
        val exitTranslation = if (direction > 0) -screenWidth else screenWidth
        // Make incoming content start very close (15% off-screen) so it appears almost immediately
        val enterTranslation = if (direction > 0) screenWidth * 0.15f else -screenWidth * 0.15f
        val exitDuration = if (slow) 200L else 100L
        val enterDuration = if (slow) 200L else 100L
        // If we don't have a valid size, fall back to the simple animation
        if (stationsContent.width <= 0 || stationsContent.height <= 0) {
            stationsContent.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            stationsList.isNestedScrollingEnabled = false
            // Hide actual RecyclerView and disable its animator to avoid a content flash
            stationsList.visibility = View.INVISIBLE
            try {
                savedItemAnimator = stationsList.itemAnimator
                stationsList.itemAnimator = null
            } catch (_: Exception) {}

            stationsContent.animate().translationX(exitTranslation).alpha(0f).setDuration(200).withEndAction {
                try {
                    onFadeOutComplete()
                } catch (_: Exception) {}
                stationsContent.translationX = enterTranslation
                stationsContent.alpha = 0f
                stationsContent.animate().translationX(0f).alpha(1f).setDuration(200).withEndAction {
                    stationsContent.setLayerType(View.LAYER_TYPE_NONE, null)
                    stationsList.isNestedScrollingEnabled = true
                    // Reveal RecyclerView and restore animator
                    stationsList.visibility = View.VISIBLE
                    try {
                        stationsList.itemAnimator = savedItemAnimator
                        savedItemAnimator = null
                    } catch (_: Exception) {}
                }.start()
            }.start()
            return
        }

        // Create a snapshot overlay (or fallback view) of the outgoing content so we can swap the RecyclerView's adapter
        val parent = staticContentContainer as? android.view.ViewGroup
        val bitmap = try {
            Bitmap.createBitmap(stationsContent.width, stationsContent.height, Bitmap.Config.ARGB_8888).also { b ->
                val c = android.graphics.Canvas(b)
                stationsContent.draw(c)
            }
        } catch (_: Exception) {
            null
        }

        // Prepare overlay view (image if possible, otherwise a solid surface copy)
        val overlayView = if (bitmap != null) {
            ImageView(this).apply {
                setImageBitmap(bitmap)
                translationX = stationsContent.x
                translationY = stationsContent.y
                alpha = 1f
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                layoutParams = android.view.ViewGroup.LayoutParams(stationsContent.width, stationsContent.height)
            }
        } else {
            View(this).apply {
                background = stationsContent.background ?: android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
                translationX = stationsContent.x
                translationY = stationsContent.y
                alpha = 1f
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                layoutParams = android.view.ViewGroup.LayoutParams(stationsContent.width, stationsContent.height)
            }
        }

        // Add overlay above the content so we can animate it away while preparing the new list
        try {
            val insertIndex = parent?.indexOfChild(stationsContent)?.plus(1) ?: -1
            if (insertIndex >= 0) parent?.addView(overlayView, insertIndex) else parent?.addView(overlayView)
        } catch (_: Exception) {}

        // Use hardware layer and disable nested scrolling during the animation to avoid blurring/jitter
        stationsContent.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        stationsList.isNestedScrollingEnabled = false

        // Hide the actual RecyclerView content while overlay animates out
        stationsList.visibility = View.INVISIBLE

        // Disable item animator while swapping content
        try {
            savedItemAnimator = stationsList.itemAnimator
            stationsList.itemAnimator = null
        } catch (_: Exception) {}

        // Animate the overlay out and overlap the incoming content animation for a snappier feel
        // Start the incoming animation immediately so the new list is visible sooner

        // Start overlay exit animation immediately but keep it mostly transparent so incoming content shows through
        overlayView.alpha = 1f
        overlayView.animate()
            .translationX(exitTranslation)
            .setDuration(exitDuration)
            .start()

        // Immediately swap content so the incoming list can begin animating while the old one exits
        try {
            onFadeOutComplete()
        } catch (_: Exception) {}

        // Ensure the RecyclerView content is present underneath the overlay and prepare incoming animation
        stationsList.visibility = View.VISIBLE
        stationsContent.translationX = enterTranslation
        stationsContent.alpha = 0f

        // Start incoming animation without delay so it overlaps the overlay's exit
        stationsList.post {
            stationsContent.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(enterDuration)
                .withEndAction {
                    // Restore normal rendering after animation completes
                    stationsContent.setLayerType(View.LAYER_TYPE_NONE, null)
                    stationsList.isNestedScrollingEnabled = true
                    // Remove overlay and recycle bitmap
                    try {
                        parent?.removeView(overlayView)
                    } catch (_: Exception) {}
                    (overlayView as? ImageView)?.drawable?.let { d ->
                        (d as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle()
                    }
                    // Restore animator
                    try {
                        stationsList.itemAnimator = savedItemAnimator
                        savedItemAnimator = null
                    } catch (_: Exception) {}
                }
                .start()
        }
    }
    
    private fun showCategoryStations(category: StationCategory) {
        android.util.Log.d("MainActivity", "showCategoryStations: $category")
        val stations = StationRepository.getStationsByCategory(category)
        categorizedAdapter = CategorizedStationAdapter(this, stations, { stationId ->
            playStation(stationId)
        }, { _ ->
            // Do nothing to prevent list jump
        })
        stationsList.adapter = categorizedAdapter
        stationsList.scrollToPosition(0)
    }

    private fun setupSwipeNavigation() {
        // Use touch slop and velocity to start a drag and make the list follow the finger
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        val minFlingVelocity = android.view.ViewConfiguration.get(this).scaledMinimumFlingVelocity

        stationsList.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var activePointerId = MotionEvent.INVALID_POINTER_ID
            private var dragging = false
            private var velocityTracker: android.view.VelocityTracker? = null
            private var lastTranslation = 0f

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // Start tracking this pointer explicitly
                        activePointerId = e.getPointerId(0)
                        downX = e.getX(0)
                        downY = e.getY(0)
                        dragging = false
                        lastTranslation = 0f
                        velocityTracker?.recycle()
                        velocityTracker = android.view.VelocityTracker.obtain()
                        velocityTracker?.addMovement(e)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val idx = try { e.findPointerIndex(activePointerId) } catch (_: Exception) { 0 }
                        val px = if (idx >= 0) e.getX(idx) else e.x
                        val py = if (idx >= 0) e.getY(idx) else e.y
                        val dx = px - downX
                        val dy = py - downY
                        val maxIndex = if (::tabLayout.isInitialized) tabLayout.tabCount - 1 else 2

                        if (!dragging) {
                            val horizontalEnough = Math.abs(dx) > Math.abs(dy) * 1.5f && Math.abs(dx) > touchSlop
                            if (horizontalEnough) {
                                // Do not start a horizontal drag if it would move past the first or last tab
                                if ((dx > 0 && currentTabIndex == 0) || (dx < 0 && currentTabIndex == maxIndex)) {
                                    return false
                                }

                                // Start dragging: take over touch events and prepare for smooth animation
                                dragging = true
                                lastTranslation = stationsContent.translationX
                                // Disable RecyclerView item animations to avoid layout jitter during drag
                                try {
                                    savedItemAnimator = stationsList.itemAnimator
                                    stationsList.itemAnimator = null
                                } catch (_: Exception) {}
                                rv.stopScroll() // stop any ongoing fling to avoid jitter
                                rv.parent?.requestDisallowInterceptTouchEvent(true)
                                rv.isNestedScrollingEnabled = false
                                stationsContent.animate().cancel()
                                stationsContent.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                return true
                            }
                        } else {
                            // already dragging; intercept
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Clean up even if we never started dragging
                        velocityTracker?.recycle()
                        velocityTracker = null
                        dragging = false
                        rv.parent?.requestDisallowInterceptTouchEvent(false)
                        rv.isNestedScrollingEnabled = true
                        stationsContent.setLayerType(View.LAYER_TYPE_NONE, null)
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                velocityTracker?.addMovement(e)
                when (e.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging) return
                        val idx = try { e.findPointerIndex(activePointerId) } catch (_: Exception) { 0 }
                        val px = if (idx >= 0) e.getX(idx) else e.x
                        val dx = px - downX
                        val maxTrans = stationsContent.width.toFloat().takeIf { it > 0f } ?: rv.width.toFloat()
                        // Use float translation for smooth tracking; avoid aggressive rounding which can cause jitter
                        val trans = (dx).coerceIn(-maxTrans, maxTrans)
                        // Apply slight exponential smoothing to reduce micro-jitter while still following finger
                        val smoothed = lastTranslation + (trans - lastTranslation) * 0.35f
                        if (Math.abs(smoothed - lastTranslation) > 0.25f) {
                            stationsContent.translationX = smoothed
                            lastTranslation = smoothed
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!dragging) {
                            activePointerId = MotionEvent.INVALID_POINTER_ID
                            return
                        }
                        velocityTracker?.computeCurrentVelocity(1000)
                        val vx = try { velocityTracker?.getXVelocity(activePointerId) ?: 0f } catch (_: Exception) { velocityTracker?.xVelocity ?: 0f }
                        val idx = try { e.findPointerIndex(activePointerId) } catch (_: Exception) { -1 }
                        val px = if (idx >= 0) e.getX(idx) else e.x
                        val dxTotal = px - downX
                        val threshold = (stationsContent.width.takeIf { it > 0 } ?: rv.width) * 0.25f
                        val maxIndex = if (::tabLayout.isInitialized) tabLayout.tabCount - 1 else 2
                        val target = if (dxTotal < 0) currentTabIndex + 1 else currentTabIndex - 1
                        val shouldNavigate = Math.abs(dxTotal) > threshold || Math.abs(vx) > Math.max(minFlingVelocity, 1000)

                        // Restore parent handling after the gesture is finished
                        rv.parent?.requestDisallowInterceptTouchEvent(false)
                        rv.isNestedScrollingEnabled = true
                        // Restore RecyclerView item animator
                        try {
                            stationsList.itemAnimator = savedItemAnimator
                            savedItemAnimator = null
                        } catch (_: Exception) {}

                        if (shouldNavigate && target in 0..maxIndex) {
                            // Animate off-screen in the swipe direction for a smooth feel, then navigate
                            val off = if (dxTotal < 0) -stationsContent.width.toFloat() else stationsContent.width.toFloat()
                            stationsContent.animate().translationX(off).setDuration(180).withEndAction {
                                if (dxTotal < 0) {
                                    selectionFromSwipe = true
                                    navigateToTab(currentTabIndex + 1)
                                } else {
                                    selectionFromSwipe = true
                                    navigateToTab(currentTabIndex - 1)
                                }
                                // Ensure translation reset after navigation (animateListTransition will animate new content)
                                stationsContent.translationX = 0f
                                stationsContent.setLayerType(View.LAYER_TYPE_NONE, null)
                                lastTranslation = 0f
                            }.start()
                        } else {
                            // animate back into place (apply to stationsContent, which is what we translate during the gesture)
                            stationsContent.animate().translationX(0f).setDuration(200).withEndAction {
                                stationsContent.setLayerType(View.LAYER_TYPE_NONE, null)
                                lastTranslation = 0f
                                // Restore animator if not already restored
                                try {
                                    stationsList.itemAnimator = savedItemAnimator
                                    savedItemAnimator = null
                                } catch (_: Exception) {}
                            }.start()
                        }
                        velocityTracker?.recycle()
                        velocityTracker = null
                        dragging = false
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                // No-op
            }
        })
    }

    private fun navigateToTab(index: Int) {
        android.util.Log.d("MainActivity", "navigateToTab: requested=$index current=$currentTabIndex")
        if (!::tabLayout.isInitialized) return
        val maxIndex = tabLayout.tabCount - 1
        val target = index.coerceIn(0, maxIndex)
        android.util.Log.d("MainActivity", "navigateToTab: target=$target")
        if (target != currentTabIndex) {
            tabLayout.getTabAt(target)?.select()
        }
    }

    private fun setupSettings() {
        val themeGroup: RadioGroup = findViewById(R.id.theme_radio_group)
        val qualityGroup: RadioGroup = findViewById(R.id.quality_radio_group)
        val autoQualityCheckbox: android.widget.CheckBox = findViewById(R.id.auto_quality_checkbox)
        val scrollingModeGroup: RadioGroup = findViewById(R.id.scrolling_mode_radio_group)
        val autoResumeAndroidAutoCheckbox: android.widget.CheckBox = findViewById(R.id.auto_resume_android_auto_checkbox)
        
        // Set current theme selection
        val currentTheme = ThemePreference.getTheme(this)
        when (currentTheme) {
            ThemePreference.THEME_LIGHT -> themeGroup.check(R.id.radio_light)
            ThemePreference.THEME_DARK -> themeGroup.check(R.id.radio_dark)
            ThemePreference.THEME_SYSTEM -> themeGroup.check(R.id.radio_system)
        }
        
        // Set current auto-detect quality selection
        val autoDetectQuality = ThemePreference.getAutoDetectQuality(this)
        autoQualityCheckbox.isChecked = autoDetectQuality
        qualityGroup.alpha = if (autoDetectQuality) 0.5f else 1.0f
        qualityGroup.isEnabled = !autoDetectQuality
        
        // Set manual quality selection (only used if auto-detect is disabled)
        val highQuality = if (autoDetectQuality) {
            NetworkQualityDetector.shouldUseHighQuality(this)
        } else {
            ThemePreference.getHighQuality(this)
        }
        if (highQuality) {
            qualityGroup.check(R.id.radio_high_quality)
        } else {
            qualityGroup.check(R.id.radio_low_quality)
        }
        
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedTheme = when (checkedId) {
                R.id.radio_light -> ThemePreference.THEME_LIGHT
                R.id.radio_dark -> ThemePreference.THEME_DARK
                R.id.radio_system -> ThemePreference.THEME_SYSTEM
                else -> ThemePreference.THEME_SYSTEM
            }
            
            ThemePreference.setTheme(this, selectedTheme)
            ThemeManager.applyTheme(selectedTheme)
        }
        
        autoQualityCheckbox.setOnCheckedChangeListener { _, isChecked ->
            ThemePreference.setAutoDetectQuality(this, isChecked)
            qualityGroup.alpha = if (isChecked) 0.5f else 1.0f
            qualityGroup.isEnabled = !isChecked
            
            // If currently playing, reload stream with new quality settings
            val currentStation = PlaybackStateHelper.getCurrentStation()
            if (currentStation != null && PlaybackStateHelper.getIsPlaying()) {
                val intent = Intent(this, RadioService::class.java).apply {
                    action = RadioService.ACTION_PLAY_STATION
                    putExtra(RadioService.EXTRA_STATION_ID, currentStation.id)
                }
                startService(intent)
            }
        }
        
        qualityGroup.setOnCheckedChangeListener { _, checkedId ->
            if (!autoQualityCheckbox.isChecked) {
                val isHighQuality = checkedId == R.id.radio_high_quality
                ThemePreference.setHighQuality(this, isHighQuality)
                // If currently playing, reload stream with the new quality
                val currentStation = PlaybackStateHelper.getCurrentStation()
                if (currentStation != null && PlaybackStateHelper.getIsPlaying()) {
                    val intent = Intent(this, RadioService::class.java).apply {
                        action = RadioService.ACTION_PLAY_STATION
                        putExtra(RadioService.EXTRA_STATION_ID, currentStation.id)
                    }
                    startService(intent)
                }
            }
        }
        
        // Set current scrolling mode selection
        val currentScrollMode = ScrollingPreference.getScrollMode(this)
        when (currentScrollMode) {
            ScrollingPreference.MODE_ALL_STATIONS -> scrollingModeGroup.check(R.id.radio_scroll_all_stations)
            ScrollingPreference.MODE_FAVORITES -> scrollingModeGroup.check(R.id.radio_scroll_favorites)
        }
        
        scrollingModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedMode = when (checkedId) {
                R.id.radio_scroll_all_stations -> ScrollingPreference.MODE_ALL_STATIONS
                R.id.radio_scroll_favorites -> ScrollingPreference.MODE_FAVORITES
                else -> ScrollingPreference.MODE_ALL_STATIONS
            }
            ScrollingPreference.setScrollMode(this, selectedMode)
        }

        autoResumeAndroidAutoCheckbox.isChecked = PlaybackPreference.isAutoResumeAndroidAutoEnabled(this)
        autoResumeAndroidAutoCheckbox.setOnCheckedChangeListener { _, isChecked ->
            PlaybackPreference.setAutoResumeAndroidAuto(this, isChecked)
        }

        // Export / Import buttons
        val exportBtn: Button = findViewById(R.id.export_prefs_button)
        val importBtn: Button = findViewById(R.id.import_prefs_button)

        exportBtn.setOnClickListener {
            // Suggest filename
            createDocumentLauncher.launch("bbcradio_prefs.json")
        }

        importBtn.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/json"))
        }

        // Podcast indexing controls (manual trigger + progress)
        val indexNowBtn: Button = findViewById(R.id.index_now_button)
        val indexStatus: TextView = findViewById(R.id.index_status_text)
        val indexLastRebuilt: TextView = findViewById(R.id.index_last_rebuilt)
        val indexEpisodesProgress: android.widget.ProgressBar = findViewById(R.id.index_episodes_progress)

        fun updateLastRebuilt(ts: Long?) {
            indexLastRebuilt.text = if (ts != null) {
                val fmt = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                "Last rebuilt: ${fmt.format(java.util.Date(ts))}"
            } else {
                "Last rebuilt: —"
            }
        }

        // Initialize display from persisted value
        updateLastRebuilt(com.hyliankid14.bbcradioplayer.db.IndexStore.getInstance(this).getLastReindexTime())

        indexNowBtn.setOnClickListener {
            try {
                indexStatus.text = "Starting index..."
                indexEpisodesProgress.isIndeterminate = true
                indexEpisodesProgress.progress = 0
                lifecycleScope.launch {
                    com.hyliankid14.bbcradioplayer.workers.IndexWorker.reindexAll(this@MainActivity) { status, percent, isEpisodePhase ->
                        runOnUiThread {
                            indexStatus.text = status

                            // Only use the episode-specific progress bar (under the status text)
                            if (isEpisodePhase) {
                                indexEpisodesProgress.visibility = android.view.View.VISIBLE
                                if (percent < 0) {
                                    indexEpisodesProgress.isIndeterminate = true
                                } else {
                                    indexEpisodesProgress.isIndeterminate = false
                                    indexEpisodesProgress.max = 100
                                    indexEpisodesProgress.progress = percent.coerceIn(0, 100)
                                }
                            } else {
                                indexEpisodesProgress.visibility = android.view.View.GONE
                                indexEpisodesProgress.isIndeterminate = false
                                indexEpisodesProgress.progress = 0
                            }

                            // When index completes, update the last rebuilt timestamp immediately
                            if (percent == 100 || status.startsWith("Index complete")) {
                                val now = System.currentTimeMillis()
                                updateLastRebuilt(now)
                            }
                        }
                    }
                    indexStatus.text = "Index finished"
                    // Ensure episode bar hidden once done
                    indexEpisodesProgress.visibility = android.view.View.GONE
                    // Also refresh persisted value (in case it was updated by the worker)
                    updateLastRebuilt(com.hyliankid14.bbcradioplayer.db.IndexStore.getInstance(this@MainActivity).getLastReindexTime())
                }
            } catch (e: Exception) {
                indexStatus.text = "Failed to schedule indexing: ${e.message}"
                android.util.Log.w("MainActivity", "Failed to start indexing: ${e.message}")
            }
        }

        // Periodic background indexing may be scheduled by the app in future; manual trigger available above.
        android.util.Log.d("MainActivity", "Indexing controls wired up")
    }

    private fun playStation(id: String) {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_PLAY_STATION
            putExtra(RadioService.EXTRA_STATION_ID, id)
        }
        startService(intent)
    }

    override fun onResume() {
        super.onResume()
        // Restore view when returning from settings
        PlaybackStateHelper.onShowChange(showChangeListener)
        startPlaybackStateUpdates()
        
        // Clear show cache and refresh the current view to prevent stale show names
        refreshCurrentView()

        // Ensure the action bar reflects the current section when returning from other activities
        updateActionBarTitle()
        if (currentMode != "podcasts") {
            // Explicitly clear any Up/home affordance left by a detail fragment so
            // the 'Favourites' / 'All Stations' titles display correctly
            supportActionBar?.apply {
                show()
                setDisplayHomeAsUpEnabled(false)
                setDisplayShowHomeEnabled(false)
            }
        }

        // If returning to Favorites ensure the last-selected sub-tab is restored and its
        // content (Saved / History) is refreshed so it doesn't remain hidden after navigation.
        try {
            if (currentMode == "favorites") {
                val prefs = getPreferences(android.content.Context.MODE_PRIVATE)
                val lastChecked = prefs.getInt("last_fav_tab_id", R.id.fav_tab_stations)
                val toggle = try { findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.favorites_toggle_group) } catch (_: Exception) { null }
                try { toggle?.check(lastChecked) } catch (_: Exception) { }
                updateFavoritesToggleVisuals(lastChecked)
                when (lastChecked) {
                    R.id.fav_tab_stations -> showFavoritesTab("stations")
                    R.id.fav_tab_subscribed -> showFavoritesTab("subscribed")
                    R.id.fav_tab_saved -> showFavoritesTab("saved")
                    R.id.fav_tab_history -> showFavoritesTab("history")
                }
                refreshSavedEpisodesSection()
                refreshHistorySection()
            }
        } catch (_: Exception) { }
    }
    
    override fun onPause() {
        super.onPause()
        PlaybackStateHelper.removeShowChangeListener(showChangeListener)
        stopPlaybackStateUpdates()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("currentMode", currentMode)
        outState.putInt("selectedNavId", bottomNavigation.selectedItemId)
    }

    private fun openNowPlaying() {
        val intent = Intent(this, NowPlayingActivity::class.java).apply {
            // Tell NowPlaying where we are so it can return to the correct view on back
            putExtra("origin_mode", currentMode)
        }
        startActivity(intent)
    }

    private fun stopPlayback() {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_STOP
        }
        startService(intent)
        updateMiniPlayer()
    }
    
    private fun skipToPrevious() {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_SKIP_TO_PREVIOUS
        }
        startService(intent)
    }

    private fun skipToNext() {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_SKIP_TO_NEXT
        }
        startService(intent)
    }

    private fun togglePlayPause() {
        // Toggle the state immediately for UI feedback
        val isCurrentlyPlaying = PlaybackStateHelper.getIsPlaying()
        PlaybackStateHelper.setIsPlaying(!isCurrentlyPlaying)
        
        val intent = Intent(this, RadioService::class.java).apply {
            action = if (isCurrentlyPlaying) {
                RadioService.ACTION_PAUSE
            } else {
                RadioService.ACTION_PLAY
            }
        }
        startService(intent)
        updateMiniPlayer()
    }
    
    private fun startPlaybackStateUpdates() {
        stopPlaybackStateUpdates()
        miniPlayerUpdateTimer = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(500) // Update every 500ms
                    runOnUiThread { updateMiniPlayer() }
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
        miniPlayerUpdateTimer?.start()
    }
    
    private fun stopPlaybackStateUpdates() {
        miniPlayerUpdateTimer?.interrupt()
        miniPlayerUpdateTimer = null
    }
    
    private fun updateMiniPlayer() {
        val station = PlaybackStateHelper.getCurrentStation()
        val isPlaying = PlaybackStateHelper.getIsPlaying()
        val show = PlaybackStateHelper.getCurrentShow()
        
        if (station != null) {
            // Show mini player
            miniPlayer.visibility = android.view.View.VISIBLE
            miniPlayerTitle.text = station.title
            
            // Display formatted show title (primary - secondary - tertiary)
            val newTitle = show.getFormattedTitle()
            if (miniPlayerSubtitle.text.toString() != newTitle) {
                miniPlayerSubtitle.text = newTitle
                miniPlayerSubtitle.isSelected = true // Trigger marquee/scroll
                miniPlayerSubtitle.startScrolling()
            }
            
            // Load artwork: Use image_url from API if available and valid, otherwise station logo
            val artworkUrl = if (!show.imageUrl.isNullOrEmpty() && show.imageUrl.startsWith("http")) {
                show.imageUrl
            } else {
                station.logoUrl
            }
            
            // Only reload if URL changed to prevent flashing
            if (artworkUrl != lastArtworkUrl) {
                lastArtworkUrl = artworkUrl
                val fallbackUrl = station.logoUrl
                
                Glide.with(this)
                    .load(artworkUrl)
                    .placeholder(android.R.color.transparent)
                    .error(Glide.with(this).load(fallbackUrl))
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            return false
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            if (resource is BitmapDrawable && isPlaceholderImage(resource.bitmap)) {
                                Log.d("MainActivity", "Detected placeholder image, falling back to logo")
                                miniPlayerArtwork.post {
                                    Glide.with(this@MainActivity)
                                        .load(fallbackUrl)
                                        .into(miniPlayerArtwork)
                                }
                                return true
                            }
                            return false
                        }
                    })
                    .into(miniPlayerArtwork)
                Log.d("MainActivity", "Loading artwork from: $artworkUrl")
            }
            
            // Update play/pause button - always show the correct state
            miniPlayerPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            
            // Update favorite button state - for podcasts, show saved-episode state if an episode is playing; otherwise show podcast subscription
            val isPodcast = station.id.startsWith("podcast_")
            val isFavorited = if (isPodcast) {
                val currentEpisodeId = PlaybackStateHelper.getCurrentEpisodeId()
                if (!currentEpisodeId.isNullOrEmpty()) {
                    SavedEpisodes.isSaved(this, currentEpisodeId)
                } else {
                    PodcastSubscriptions.isSubscribed(this, station.id.removePrefix("podcast_"))
                }
            } else {
                FavoritesPreference.isFavorite(this, station.id)
            }
            if (isFavorited) {
                miniPlayerFavorite.setImageResource(R.drawable.ic_star_filled)
                miniPlayerFavorite.setColorFilter(ContextCompat.getColor(this, R.color.favorite_star_color))
            } else {
                miniPlayerFavorite.setImageResource(R.drawable.ic_star_outline)
                miniPlayerFavorite.clearColorFilter()
            }
        } else {
            // Hide mini player
            miniPlayer.visibility = android.view.View.GONE
        }
    }
    
    private fun updateMiniPlayerFromShow(show: CurrentShow) {
        if (isFinishing || isDestroyed) {
            Log.w("MainActivity", "Ignoring show update because activity is finishing/destroyed")
            return
        }
        if (!miniPlayerArtwork.isAttachedToWindow) {
            Log.w("MainActivity", "Ignoring show update because mini player view is detached")
            return
        }

        // Update subtitle with formatted show title
        val newTitle = show.getFormattedTitle()
        if (miniPlayerSubtitle.text.toString() != newTitle) {
            miniPlayerSubtitle.text = newTitle
            miniPlayerSubtitle.isSelected = true
            miniPlayerSubtitle.startScrolling()
        }
        
        // Load new artwork - use image_url if available and valid, otherwise station logo
        val artworkUrl = if (!show.imageUrl.isNullOrEmpty() && show.imageUrl.startsWith("http")) {
            show.imageUrl
        } else {
            PlaybackStateHelper.getCurrentStation()?.logoUrl
        }
        
        // Only reload if URL changed
        if (artworkUrl != null && artworkUrl != lastArtworkUrl) {
            lastArtworkUrl = artworkUrl
            val fallbackUrl = PlaybackStateHelper.getCurrentStation()?.logoUrl
            
            Glide.with(this)
                .load(artworkUrl)
                .placeholder(android.R.color.transparent)
                .error(Glide.with(this).load(fallbackUrl))
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                        return false
                    }

                    override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                        if (resource is BitmapDrawable && isPlaceholderImage(resource.bitmap)) {
                            Log.d("MainActivity", "Detected placeholder image, falling back to logo")
                            miniPlayerArtwork.post {
                                Glide.with(this@MainActivity)
                                    .load(fallbackUrl)
                                    .into(miniPlayerArtwork)
                            }
                            return true
                        }
                        return false
                    }
                })
                .into(miniPlayerArtwork)
            Log.d("MainActivity", "Loading artwork from: $artworkUrl")
        }

        // Show episode progress when available (podcast playback)
        val pos = show.segmentStartMs ?: -1L
        val dur = show.segmentDurationMs ?: -1L
        if (dur > 0 && pos >= 0) {
            val ratio = (pos.toDouble() / dur.toDouble()).coerceIn(0.0, 1.0)
            val percent = (ratio * 100).toInt()
            miniPlayerProgress.progress = percent
            miniPlayerProgress.visibility = android.view.View.VISIBLE
        } else {
            miniPlayerProgress.visibility = android.view.View.GONE
        }
    }
    
    private fun toggleMiniPlayerFavorite() {
        val station = PlaybackStateHelper.getCurrentStation()
        if (station != null) {
            if (station.id.startsWith("podcast_")) {
                // If an episode is currently playing, save/unsave the episode. Otherwise toggle podcast subscription.
                val currentEpisodeId = PlaybackStateHelper.getCurrentEpisodeId()
                if (!currentEpisodeId.isNullOrEmpty()) {
                    val episode = com.hyliankid14.bbcradioplayer.Episode(
                        id = currentEpisodeId,
                        title = PlaybackStateHelper.getCurrentShow().episodeTitle ?: "Saved episode",
                        description = PlaybackStateHelper.getCurrentShow().description ?: "",
                        audioUrl = "",
                        imageUrl = PlaybackStateHelper.getCurrentShow().imageUrl ?: "",
                        pubDate = "",
                        durationMins = 0,
                        podcastId = station.id.removePrefix("podcast_")
                    )
                    val podcastTitle = PlaybackStateHelper.getCurrentStation()?.title ?: ""
                    val nowSaved = SavedEpisodes.toggleSaved(this, episode, podcastTitle)
                    if (currentMode == "favorites") showFavorites()
                    updateMiniPlayer()
                    val msg = if (nowSaved) "Saved episode: ${episode.title}" else "Removed saved episode: ${episode.title}"
                    com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content), msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).setAnchorView(miniPlayer).show()
                } else {
                    PodcastSubscriptions.toggleSubscription(this, station.id.removePrefix("podcast_"))
                    if (currentMode == "favorites") {
                        showFavorites()
                    }
                }
            } else {
                FavoritesPreference.toggleFavorite(this, station.id)
                
                // Refresh the current view to update the station's favorite status
                when (currentMode) {
                    "list" -> showAllStations()
                    "favorites" -> showFavorites()
                }
            }
            updateMiniPlayer()
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                scrollToNextStation()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                scrollToPreviousStation()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
    
    private fun scrollToNextStation() {
        val scrollMode = ScrollingPreference.getScrollMode(this)
        val stations = if (scrollMode == ScrollingPreference.MODE_FAVORITES) {
            val favorites = FavoritesPreference.getFavorites(this)
            if (favorites.isEmpty()) {
                Log.w("MainActivity", "No favorites available for scrolling")
                return
            }
            favorites
        } else {
            StationRepository.getStations()
        }
        
        val currentStation = PlaybackStateHelper.getCurrentStation()
        val currentIndex = stations.indexOfFirst { it.id == currentStation?.id }
        
        val nextIndex = if (currentIndex == -1) {
            0
        } else {
            (currentIndex + 1) % stations.size
        }
        
        playStation(stations[nextIndex].id)
    }
    
    private fun scrollToPreviousStation() {
        val scrollMode = ScrollingPreference.getScrollMode(this)
        val stations = if (scrollMode == ScrollingPreference.MODE_FAVORITES) {
            val favorites = FavoritesPreference.getFavorites(this)
            if (favorites.isEmpty()) {
                Log.w("MainActivity", "No favorites available for scrolling")
                return
            }
            favorites
        } else {
            StationRepository.getStations()
        }
        
        val currentStation = PlaybackStateHelper.getCurrentStation()
        val currentIndex = stations.indexOfFirst { it.id == currentStation?.id }
        
        val prevIndex = if (currentIndex == -1) {
            stations.size - 1
        } else {
            (currentIndex - 1 + stations.size) % stations.size
        }
        
        playStation(stations[prevIndex].id)
    }

    private fun isPlaceholderImage(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width < 10 || height < 10) return false
        
        // Sample 5 points: corners and center
        val p1 = bitmap.getPixel(5, 5)
        val p2 = bitmap.getPixel(width - 5, 5)
        val p3 = bitmap.getPixel(5, height - 5)
        val p4 = bitmap.getPixel(width - 5, height - 5)
        val p5 = bitmap.getPixel(width / 2, height / 2)
        
        val pixels = listOf(p1, p2, p3, p4, p5)
        val first = pixels[0]
        
        // Check if all sampled pixels are similar to the first one
        for (p in pixels) {
            if (!areColorsSimilar(first, p)) return false
        }
        
        // Check if the color is grey-ish (R ~= G ~= B)
        return isGrey(first)
    }

    override fun onDestroy() {
        super.onDestroy()
        PlaybackStateHelper.removeShowChangeListener(showChangeListener)
        supportFragmentManager.removeOnBackStackChangedListener(backStackListener)
    }
    
    private fun areColorsSimilar(c1: Int, c2: Int): Boolean {
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF
        
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF
        
        val diff = Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2)
        return diff < 30 // Tolerance
    }
    
    private fun isGrey(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        
        // Grey means R, G, and B are close to each other
        val maxDiff = Math.max(Math.abs(r - g), Math.max(Math.abs(r - b), Math.abs(g - b)))
        return maxDiff < 20
    }

    // Export preferences to the given Uri as JSON. Returns true on success.
    private fun exportPreferencesToUri(uri: Uri): Boolean {
        return try {
            val names = listOf("favorites_prefs", "podcast_subscriptions", "saved_episodes_prefs", "played_episodes_prefs", "played_history_prefs", "playback_prefs", "scrolling_prefs", "theme_prefs")
            val root = JSONObject()
            for (name in names) {
                val prefs = getSharedPreferences(name, MODE_PRIVATE)
                val obj = JSONObject()
                for ((k, v) in prefs.all) {
                    when (v) {
                        is Set<*> -> {
                            val arr = JSONArray()
                            v.forEach { arr.put(it.toString()) }
                            obj.put(k, arr)
                        }
                        is Boolean -> obj.put(k, v)
                        is Number -> obj.put(k, v)
                        else -> obj.put(k, v?.toString())
                    }
                }
                // Ensure known defaults are present even if they were never explicitly stored
                if (name == "scrolling_prefs") {
                    if (!obj.has("scroll_mode")) obj.put("scroll_mode", ScrollingPreference.getScrollMode(this))
                }
                if (name == "playback_prefs") {
                    if (!obj.has("auto_resume_android_auto")) obj.put("auto_resume_android_auto", PlaybackPreference.isAutoResumeAndroidAutoEnabled(this))
                }
                root.put(name, obj)
            }

            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray())
            }
            true
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to export preferences", e)
            false
        }
    }

    // Import preferences from the given Uri (JSON). Returns true on success.
    private fun importPreferencesFromUri(uri: Uri): Boolean {
        return try {
            val text = contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: return false
            val root = JSONObject(text)
            val keys = root.keys()
            while (keys.hasNext()) {
                val prefsName = keys.next()
                val prefsObj = root.getJSONObject(prefsName)
                val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
                val edit = prefs.edit()
                edit.clear()
                val kIt = prefsObj.keys()
                while (kIt.hasNext()) {
                    val key = kIt.next()
                    val value = prefsObj.get(key)
                    when (value) {
                        is JSONArray -> {
                            val set = mutableSetOf<String>()
                            for (i in 0 until value.length()) set.add(value.getString(i))
                            edit.putStringSet(key, set)
                        }
                        is Boolean -> edit.putBoolean(key, value)
                        is Int -> edit.putInt(key, value)
                        is Long -> edit.putLong(key, value)
                        is Double -> {
                            // JSONObject represents numbers as Double. Decide whether to store as int/long/float
                            val d = value
                            if (d % 1.0 == 0.0) {
                                val l = d.toLong()
                                if (l <= Int.MAX_VALUE && l >= Int.MIN_VALUE) edit.putInt(key, l.toInt()) else edit.putLong(key, l)
                            } else {
                                edit.putFloat(key, d.toFloat())
                            }
                        }
                        else -> {
                            val s = if (value == JSONObject.NULL) null else value.toString()
                            edit.putString(key, s)
                        }
                    }
                }
                edit.apply()
            }

            // Ensure critical preferences are set via their helpers so any logic they perform runs
            try {
                if (root.has("scrolling_prefs")) {
                    val sp = root.getJSONObject("scrolling_prefs")
                    if (sp.has("scroll_mode")) {
                        val mode = sp.optString("scroll_mode", ScrollingPreference.MODE_ALL_STATIONS)
                        ScrollingPreference.setScrollMode(this, mode)
                    }
                }
            } catch (e: Exception) { /* Ignore */ }

            try {
                if (root.has("playback_prefs")) {
                    val pp = root.getJSONObject("playback_prefs")
                    if (pp.has("auto_resume_android_auto")) {
                        val enabled = pp.optBoolean("auto_resume_android_auto", false)
                        PlaybackPreference.setAutoResumeAndroidAuto(this, enabled)
                    }
                }
            } catch (e: Exception) { /* Ignore */ }

            // Notify listeners that played-status/progress may have changed so UI updates
            try {
                val intent = android.content.Intent(PlayedEpisodesPreference.ACTION_PLAYED_STATUS_CHANGED)
                sendBroadcast(intent)
            } catch (e: Exception) { }
            try {
                val intent = android.content.Intent(PlayedHistoryPreference.ACTION_HISTORY_CHANGED)
                sendBroadcast(intent)
            } catch (e: Exception) { }
            true
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to import preferences", e)
            false
        }
    }

}
