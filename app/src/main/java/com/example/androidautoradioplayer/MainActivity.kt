package com.example.androidautoradioplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
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
import android.view.GestureDetector
import android.view.MotionEvent
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {
    private lateinit var stationsList: RecyclerView
    private lateinit var settingsContainer: View
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniPlayerTitle: TextView
    private lateinit var miniPlayerSubtitle: com.example.androidautoradioplayer.ScrollingTextView
    private lateinit var miniPlayerArtwork: ImageView
    private lateinit var miniPlayerPrevious: ImageButton
    private lateinit var miniPlayerPlayPause: ImageButton
    private lateinit var miniPlayerNext: ImageButton
    private lateinit var miniPlayerStop: ImageButton
    private lateinit var miniPlayerFavorite: ImageButton
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before creating the view
        val theme = ThemePreference.getTheme(this)
        ThemeManager.applyTheme(theme)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set action bar color to match status bar (purple)
        supportActionBar?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(
                android.graphics.Color.parseColor("#6200EE")
            )
        )

        stationsList = findViewById(R.id.stations_list)
        stationsList.layoutManager = LinearLayoutManager(this)
        
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
                R.id.navigation_settings -> {
                    showSettings()
                    true
                }
                else -> false
            }
        }
        
        // Setup settings controls
        setupSettings()
        
        // Mini player views
        miniPlayer = findViewById(R.id.mini_player)
        miniPlayerTitle = findViewById(R.id.mini_player_title)
        miniPlayerSubtitle = findViewById(R.id.mini_player_subtitle)
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
        
        // Register listener for show changes
        PlaybackStateHelper.onShowChange(showChangeListener)
        
        // Restore previous section when recreating (e.g., theme change), otherwise default to list
        val restoredNavSelection = savedInstanceState?.getInt("selectedNavId")
        if (restoredNavSelection != null) {
            bottomNavigation.selectedItemId = restoredNavSelection
        } else {
            bottomNavigation.selectedItemId = R.id.navigation_list
        }
        
        // Start polling for playback state updates
        startPlaybackStateUpdates()
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
        stationsList.visibility = View.VISIBLE
        filterButtonsContainer.visibility = View.VISIBLE
        settingsContainer.visibility = View.GONE
        
        // Default to National category
        showCategoryStations(StationCategory.NATIONAL)
        setupFilterButtons()
    }

    private fun showFavorites() {
        currentMode = "favorites"
        stationsList.visibility = View.VISIBLE
        filterButtonsContainer.visibility = View.GONE
        settingsContainer.visibility = View.GONE
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
    }

    private fun showSettings() {
        currentMode = "settings"
        stationsList.visibility = View.GONE
        filterButtonsContainer.visibility = View.GONE
        settingsContainer.visibility = View.VISIBLE
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
        val swipeThresholdDistance = 100
        var downX = 0f
        var downY = 0f
        var swipeHandled = false

        stationsList.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    swipeHandled = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!swipeHandled) {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        val isHorizontal = Math.abs(dx) > Math.abs(dy)
                        val distanceOk = Math.abs(dx) > swipeThresholdDistance
                        if (isHorizontal && distanceOk) {
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            if (dx < 0) {
                                navigateToTab(currentTabIndex + 1)
                            } else {
                                navigateToTab(currentTabIndex - 1)
                            }
                            swipeHandled = true
                            return@setOnTouchListener true
                        }
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    swipeHandled = false
                    false
                }
                else -> false
            }
        }
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
        val intent = Intent(this, NowPlayingActivity::class.java)
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
            val isFavorited = FavoritesPreference.isFavorite(this, station.id)
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
    }
    
    private fun toggleMiniPlayerFavorite() {
        val station = PlaybackStateHelper.getCurrentStation()
        if (station != null) {
            FavoritesPreference.toggleFavorite(this, station.id)
            updateMiniPlayer()
            
            // Refresh the current view to update the station's favorite status
            when (currentMode) {
                "list" -> showAllStations()
                "favorites" -> showFavorites()
            }
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
}

