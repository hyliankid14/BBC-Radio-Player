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
            val savedHeader = findViewById<View>(R.id.saved_episodes_header_container)
            val savedExpandIcon = findViewById<ImageView>(R.id.saved_episodes_expand_icon)
            val savedDivider = findViewById<View>(R.id.saved_episodes_divider)
            val savedRecycler = findViewById<RecyclerView>(R.id.saved_episodes_recycler)

            if (savedEntries.isNotEmpty()) {
                savedContainer.visibility = View.VISIBLE
                val onSurface = androidx.core.content.ContextCompat.getColor(this, R.color.md_theme_onSurface)
                savedHeader.isClickable = true
                savedHeader.isFocusable = true
                savedExpandIcon?.setColorFilter(onSurface)
                savedHeader.elevation = 8f

                savedRecycler.layoutManager = LinearLayoutManager(this)
                savedRecycler.isNestedScrollingEnabled = false
                var savedExpanded = false
                savedRecycler.visibility = View.GONE
                savedDivider.visibility = View.GONE
                savedExpandIcon.visibility = View.VISIBLE

                val savedAdapter = SavedEpisodesAdapter(this, savedEntries, onPlayEpisode = { episode, podcastTitle, podcastImage ->
                    val intent = android.content.Intent(this, RadioService::class.java).apply {
                        action = RadioService.ACTION_PLAY_PODCAST_EPISODE
                        putExtra(RadioService.EXTRA_EPISODE, episode)
                        putExtra(RadioService.EXTRA_PODCAST_ID, episode.podcastId)
                        // Provide title and image so the service and mini-player can show correct info immediately
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
                    if (updated.isEmpty()) savedContainer.visibility = View.GONE
                })

                savedRecycler.adapter = savedAdapter

                savedHeader.setOnClickListener {
                    savedExpanded = !savedExpanded
                    val defaultPadding = (8 * resources.displayMetrics.density).toInt()
                    if (savedExpanded) {
                        savedRecycler.visibility = View.VISIBLE
                        savedDivider.visibility = View.VISIBLE
                        savedExpandIcon.setImageResource(R.drawable.ic_expand_less)
                        // Ensure list content sits below the header to avoid overlap with header's rounded corner
                        savedRecycler.setPadding(
                            savedRecycler.paddingLeft,
                            savedHeader.height,
                            savedRecycler.paddingRight,
                            savedRecycler.paddingBottom
                        )
                        try {
                            val parentScroll = findViewById<androidx.core.widget.NestedScrollView>(R.id.podcasts_scroll)
                            parentScroll?.post { parentScroll.smoothScrollTo(0, savedContainer.top) }
                        } catch (_: Exception) {}
                    } else {
                        savedRecycler.visibility = View.GONE
                        savedDivider.visibility = View.GONE
                        savedExpandIcon.setImageResource(R.drawable.ic_expand_more)
                        // Reset padding when collapsed
                        savedRecycler.setPadding(
                            savedRecycler.paddingLeft,
                            defaultPadding,
                            savedRecycler.paddingRight,
                            savedRecycler.paddingBottom
                        )
                    }
                }
            } else {
                savedContainer.visibility = View.GONE
            }
        } catch (e: Exception) {
            // Swallow — UI refresh should never crash the app
            android.util.Log.w("MainActivity", "refreshSavedEpisodesSection failed: ${e.message}")
        }
    }

    private fun showAllStations() {
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
        val favoritesPodcastsHeaderContainer = findViewById<View>(R.id.favorites_podcasts_header_container)
        val favoritesPodcastsExpandIcon = findViewById<ImageView>(R.id.favorites_podcasts_expand_icon)
        val favoritesPodcastsRecycler = findViewById<RecyclerView>(R.id.favorites_podcasts_recycler)
        
        // Move podcasts container to top of parent
        val parent = favoritesPodcastsContainer.parent as? android.view.ViewGroup
        parent?.let {
            it.removeView(favoritesPodcastsContainer)
            it.addView(favoritesPodcastsContainer, 0)

            // Ensure the saved-episodes card is immediately after the subscribed podcasts card
            try {
                val savedContainer = findViewById<View>(R.id.saved_episodes_container)
                if (savedContainer != null) {
                    // Remove and re-insert at index 1 (after the favorites card we just moved)
                    it.removeView(savedContainer)
                    it.addView(savedContainer, 1)
                }
            } catch (_: Exception) { /* no-op */ }
        }
        
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

        // Load subscribed podcasts into Favorites section
        val subscribedIds = PodcastSubscriptions.getSubscribedIds(this)
        // Ensure the subscribed podcasts header is visible when the Favorites view is open
        favoritesPodcastsContainer.visibility = View.VISIBLE
        if (subscribedIds.isNotEmpty()) {
            favoritesPodcastsRecycler.layoutManager = LinearLayoutManager(this)

            // Use theme surface and text colors so the header matches the current theme
            val onSurface = androidx.core.content.ContextCompat.getColor(this, R.color.md_theme_onSurface)
            // Ensure header is clickable and focusable so the expand/collapse can be toggled
            favoritesPodcastsHeaderContainer.isClickable = true
            favoritesPodcastsHeaderContainer.isFocusable = true
            favoritesPodcastsExpandIcon?.setColorFilter(onSurface)

            val divider = findViewById<View>(R.id.favorites_podcasts_divider)

            // Start collapsed by default and restore header expand/collapse behaviour
            var isExpanded = false
            favoritesPodcastsRecycler.visibility = View.GONE
            divider.visibility = View.GONE
            favoritesPodcastsExpandIcon.visibility = View.VISIBLE

            // Header tap toggles expand/collapse (original behaviour)
            favoritesPodcastsHeaderContainer.setOnClickListener {
                isExpanded = !isExpanded
                val defaultPadding = (8 * resources.displayMetrics.density).toInt()
                if (isExpanded) {
                    favoritesPodcastsRecycler.visibility = View.VISIBLE
                    divider.visibility = View.VISIBLE
                    favoritesPodcastsExpandIcon.setImageResource(R.drawable.ic_expand_less)
                    // Ensure content sits below the header to avoid overlap
                    favoritesPodcastsRecycler.setPadding(
                        favoritesPodcastsRecycler.paddingLeft,
                        favoritesPodcastsHeaderContainer.height,
                        favoritesPodcastsRecycler.paddingRight,
                        favoritesPodcastsRecycler.paddingBottom
                    )
                    // Scroll the card into view so the header remains visible after expansion
                    try {
                        val parentScroll = findViewById<androidx.core.widget.NestedScrollView>(R.id.podcasts_scroll)
                        parentScroll?.post { parentScroll.smoothScrollTo(0, favoritesPodcastsContainer.top) }
                    } catch (_: Exception) {}
                } else {
                    favoritesPodcastsRecycler.visibility = View.GONE
                    divider.visibility = View.GONE
                    favoritesPodcastsExpandIcon.setImageResource(R.drawable.ic_expand_more)
                    // Reset to default padding to keep layout tight when collapsed
                    favoritesPodcastsRecycler.setPadding(
                        favoritesPodcastsRecycler.paddingLeft,
                        defaultPadding,
                        favoritesPodcastsRecycler.paddingRight,
                        favoritesPodcastsRecycler.paddingBottom
                    )
                }
            }
            // Refresh Saved Episodes UI so it appears under Subscribed Podcasts when the Favorites view opens
            refreshSavedEpisodesSection()

            // Make sure the inner RecyclerView doesn't intercept header clicks by disabling nested scrolling
            favoritesPodcastsRecycler.isNestedScrollingEnabled = false
            // Ensure clicking the header is always reachable by giving it a higher elevation
            favoritesPodcastsHeaderContainer.elevation = 8f

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
                }
            }.start()
        } else {
            favoritesPodcastsContainer.visibility = View.GONE
        }

        // Load saved episodes and display underneath Subscribed Podcasts in the Favorites section
        refreshSavedEpisodesSection()
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

    override fun onStart() {
        super.onStart()
        try {
            registerReceiver(playedStatusReceiver, android.content.IntentFilter(PlayedEpisodesPreference.ACTION_PLAYED_STATUS_CHANGED))
        } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(playedStatusReceiver)
        } catch (_: Exception) {}
    }

    private fun showSettings() {
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
    }

    private fun showPodcasts() {
        currentMode = "podcasts"
        fragmentContainer.visibility = View.VISIBLE
        staticContentContainer.visibility = View.GONE
        // Hide the global action bar so the Podcasts fragment can present its own search app bar at the top
        supportActionBar?.hide()

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

    private fun setupFilterButtons() {
        // Find the TabLayout inside the included layout and handle missing cases safely
        val tabs = findViewById<com.google.android.material.tabs.TabLayout?>(R.id.filter_tabs)
        if (tabs == null) {
            android.util.Log.w("MainActivity", "TabLayout with id 'filter_tabs' not found; filter buttons disabled, but swipe navigation will still be enabled")
            // Enable swipe navigation even if tabs are missing so user can still switch categories
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
                if (stationsContent.width <= 0) {
                    android.util.Log.d("MainActivity", "stationsContent not laid out yet; updating immediately")
                    showCategoryStations(category)
                } else {
                    animateListTransition(direction) {
                        showCategoryStations(category)
                    }
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

    private fun animateListTransition(direction: Int, onFadeOutComplete: () -> Unit) {
        val screenWidth = stationsContent.width.toFloat().takeIf { it > 0f } ?: stationsList.width.toFloat()
        val exitTranslation = if (direction > 0) -screenWidth else screenWidth
        val enterTranslation = if (direction > 0) screenWidth else -screenWidth

        // Use a hardware layer and disable nested scrolling during the animation to avoid blurring/jitter
        stationsContent.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        stationsList.isNestedScrollingEnabled = false

        stationsContent.animate()
            .translationX(exitTranslation)
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                onFadeOutComplete()
                stationsContent.translationX = enterTranslation
                stationsContent.alpha = 0f
                stationsContent.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .withEndAction {
                        // Restore normal rendering after animation completes
                        stationsContent.setLayerType(View.LAYER_TYPE_NONE, null)
                        stationsList.isNestedScrollingEnabled = true
                    }
                    .start()
            }
            .start()
    }
    
    private fun showCategoryStations(category: StationCategory) {
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
            private var dragging = false
            private var velocityTracker: android.view.VelocityTracker? = null
            private var lastTranslation = 0f

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.x
                        downY = e.y
                        dragging = false
                        lastTranslation = 0f
                        velocityTracker?.recycle()
                        velocityTracker = android.view.VelocityTracker.obtain()
                        velocityTracker?.addMovement(e)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.x - downX
                        val dy = e.y - downY
                        val maxIndex = if (::tabLayout.isInitialized) tabLayout.tabCount - 1 else 2

                        if (!dragging) {
                            val horizontalEnough = Math.abs(dx) > Math.abs(dy) * 1.5f && Math.abs(dx) > touchSlop
                            if (horizontalEnough) {
                                // Do not start a horizontal drag if it would move past the first or last tab
                                if ((dx > 0 && currentTabIndex == 0) || (dx < 0 && currentTabIndex == maxIndex)) {
                                    // Let the RecyclerView (or parent) handle the gesture; do not intercept
                                    return false
                                }

                                // Start dragging: take over touch events and prepare for smooth animation
                                dragging = true
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
                        velocityTracker?.recycle()
                        velocityTracker = null
                        dragging = false
                        rv.parent?.requestDisallowInterceptTouchEvent(false)
                        rv.isNestedScrollingEnabled = true
                        stationsContent.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                velocityTracker?.addMovement(e)
                when (e.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging) return
                        val dx = e.x - downX
                        val maxTrans = stationsContent.width.toFloat().takeIf { it > 0f } ?: rv.width.toFloat()
                        // Round to integer pixels to avoid sub-pixel text blurring
                        val trans = Math.round(dx.coerceIn(-maxTrans, maxTrans)).toFloat()
                        // Only apply translation when it actually changes to reduce rendering churn
                        if (trans != lastTranslation) {
                            stationsContent.translationX = trans
                            lastTranslation = trans
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!dragging) return
                        velocityTracker?.computeCurrentVelocity(1000)
                        val vx = velocityTracker?.xVelocity ?: 0f
                        val dxTotal = e.x - downX
                        val threshold = (stationsContent.width.takeIf { it > 0 } ?: rv.width) * 0.25f
                        val maxIndex = if (::tabLayout.isInitialized) tabLayout.tabCount - 1 else 2
                        val target = if (dxTotal < 0) currentTabIndex + 1 else currentTabIndex - 1
                        val shouldNavigate = Math.abs(dxTotal) > threshold || Math.abs(vx) > Math.max(minFlingVelocity, 1000)

                        // Restore parent handling after the gesture is finished
                        rv.parent?.requestDisallowInterceptTouchEvent(false)
                        rv.isNestedScrollingEnabled = true

                        if (shouldNavigate && target in 0..maxIndex) {
                            // Animate off-screen in the swipe direction for a smooth feel, then navigate
                            val off = if (dxTotal < 0) -stationsContent.width.toFloat() else stationsContent.width.toFloat()
                            stationsContent.animate().translationX(off).setDuration(180).withEndAction {
                                if (dxTotal < 0) {
                                    navigateToTab(currentTabIndex + 1)
                                } else {
                                    navigateToTab(currentTabIndex - 1)
                                }
                                // Ensure translation reset after navigation (animateListTransition will animate new content)
                                stationsContent.translationX = 0f
                                stationsContent.setLayerType(View.LAYER_TYPE_NONE, null)
                                lastTranslation = 0f
                            }.start()
                        } else {
                            // animate back into place
                            stationsView.animate().translationX(0f).setDuration(200).withEndAction {
                                stationsView.setLayerType(View.LAYER_TYPE_NONE, null)
                                lastTranslation = 0f
                            }.start()
                        }
                        velocityTracker?.recycle()
                        velocityTracker = null
                        dragging = false
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                // No-op
            }
        })
    }

    private fun navigateToTab(index: Int) {
        if (!::tabLayout.isInitialized) return
        val maxIndex = tabLayout.tabCount - 1
        val target = index.coerceIn(0, maxIndex)
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
            val names = listOf("favorites_prefs", "podcast_subscriptions", "saved_episodes_prefs", "played_episodes_prefs", "playback_prefs", "scrolling_prefs", "theme_prefs")
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
            true
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to import preferences", e)
            false
        }
    }
}
