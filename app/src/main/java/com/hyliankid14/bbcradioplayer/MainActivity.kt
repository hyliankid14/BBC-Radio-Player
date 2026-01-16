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
import com.google.android.material.tabs.TabLayout
import com.hyliankid14.bbcradioplayer.PodcastSubscriptions

class MainActivity : AppCompatActivity() {
    private lateinit var stationsList: RecyclerView
    private lateinit var settingsContainer: View
    private lateinit var fragmentContainer: View
    private lateinit var staticContentContainer: View
    private lateinit var stationsView: View
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
    private lateinit var filterButtonsContainer: View
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
        
        filterButtonsContainer = findViewById(R.id.filter_buttons_include)
        
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
                        updateMiniPlayer()
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

    private fun showAllStations() {
        currentMode = "list"
        fragmentContainer.visibility = View.GONE
        staticContentContainer.visibility = View.VISIBLE
        stationsView.visibility = View.VISIBLE
        stationsList.visibility = View.VISIBLE
        filterButtonsContainer.visibility = View.VISIBLE
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
    }

    private fun showFavorites() {
        currentMode = "favorites"
        fragmentContainer.visibility = View.GONE
        staticContentContainer.visibility = View.VISIBLE
        stationsView.visibility = View.VISIBLE
        stationsList.visibility = View.VISIBLE
        filterButtonsContainer.visibility = View.GONE
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
        
        // Setup ItemTouchHelper for drag-and-drop
        val itemTouchHelperCallback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }
            
            override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                if (adapter is FavoritesAdapter) {
                    adapter.moveItem(source.bindingAdapterPosition, target.bindingAdapterPosition)
                    return true
                }
                return false
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            
            override fun isLongPressDragEnabled(): Boolean = true
        }
        
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(stationsList)

        // Load subscribed podcasts into Favorites section
        val subscribedIds = PodcastSubscriptions.getSubscribedIds(this)
        if (subscribedIds.isNotEmpty()) {
            favoritesPodcastsContainer.visibility = View.VISIBLE
            favoritesPodcastsRecycler.layoutManager = LinearLayoutManager(this)

            // Use theme surface and text colors so the header matches the current theme
            val onSurface = androidx.core.content.ContextCompat.getColor(this, R.color.md_theme_onSurface)
            // Ensure header is clickable and stays on top so the expand/collapse can always be toggled
            favoritesPodcastsHeaderContainer.isClickable = true
            favoritesPodcastsHeaderContainer.isFocusable = true
            favoritesPodcastsHeaderContainer.bringToFront()
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
                if (isExpanded) {
                    favoritesPodcastsRecycler.visibility = View.VISIBLE
                    divider.visibility = View.VISIBLE
                    favoritesPodcastsExpandIcon.setImageResource(R.drawable.ic_expand_less)
                } else {
                    favoritesPodcastsRecycler.visibility = View.GONE
                    divider.visibility = View.GONE
                    favoritesPodcastsExpandIcon.setImageResource(R.drawable.ic_expand_more)
                }
            }

            // Make sure the inner RecyclerView doesn't intercept header clicks by disabling nested scrolling
            favoritesPodcastsRecycler.isNestedScrollingEnabled = false

            val repo = PodcastRepository(this)
            Thread {
                val all = try { kotlinx.coroutines.runBlocking { repo.fetchPodcasts(false) } } catch (e: Exception) { emptyList<Podcast>() }
                val subs = all.filter { subscribedIds.contains(it.id) }
                runOnUiThread {
                        val podcastAdapter = PodcastAdapter(this, onPodcastClick = { podcast ->
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
                }
            }.start()
        } else {
            favoritesPodcastsContainer.visibility = View.GONE
        }
    }

    private fun showSettings() {
        currentMode = "settings"
        fragmentContainer.visibility = View.GONE
        staticContentContainer.visibility = View.VISIBLE
        stationsView.visibility = View.GONE
        stationsList.visibility = View.GONE
        filterButtonsContainer.visibility = View.GONE
        settingsContainer.visibility = View.VISIBLE
        // Ensure action bar reflects the section and clear any podcast-specific up affordance
        supportActionBar?.apply {
            show()
            title = "Settings"
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowHomeEnabled(false)
        }
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
        tabLayout = findViewById(R.id.filter_buttons_include)
        
        tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                val newIndex = tab.position
                val direction = if (newIndex > currentTabIndex) 1 else -1
                currentTabIndex = newIndex
                
                val category = when (newIndex) {
                    0 -> StationCategory.NATIONAL
                    1 -> StationCategory.REGIONS
                    2 -> StationCategory.LOCAL
                    else -> StationCategory.NATIONAL
                }
                
                animateListTransition(direction) {
                    showCategoryStations(category)
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
        val screenWidth = stationsList.width.toFloat()
        val exitTranslation = if (direction > 0) -screenWidth else screenWidth
        val enterTranslation = if (direction > 0) screenWidth else -screenWidth
        
        stationsList.animate()
            .translationX(exitTranslation)
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                onFadeOutComplete()
                stationsList.translationX = enterTranslation
                stationsList.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(200)
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
        val swipeThresholdDistance = (32 * resources.displayMetrics.density).toInt()
        stationsList.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            var downX = 0f
            var downY = 0f
            var swipeHandled = false

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.x
                        downY = e.y
                        swipeHandled = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!swipeHandled) {
                            val dx = e.x - downX
                            val dy = e.y - downY
                            val horizontalEnough = Math.abs(dx) > Math.abs(dy) * 1.5f
                            val distanceOk = Math.abs(dx) > swipeThresholdDistance
                            if (horizontalEnough && distanceOk) {
                                if (dx < 0) {
                                    navigateToTab(currentTabIndex + 1)
                                } else {
                                    navigateToTab(currentTabIndex - 1)
                                }
                                swipeHandled = true
                                // Do not intercept; allow vertical scroll to continue
                                return false
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        swipeHandled = false
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                // No-op; we don't need to consume events
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
            
            // Update favorite button state - swap drawable based on favorite status
            val isPodcast = station.id.startsWith("podcast_")
            val isFavorited = if (isPodcast) {
                PodcastSubscriptions.isSubscribed(this, station.id.removePrefix("podcast_"))
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
                PodcastSubscriptions.toggleSubscription(this, station.id.removePrefix("podcast_"))
                if (currentMode == "favorites") {
                    showFavorites()
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
            val names = listOf("favorites_prefs", "podcast_subscriptions", "played_episodes_prefs", "playback_prefs", "scrolling_prefs", "theme_prefs")
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
