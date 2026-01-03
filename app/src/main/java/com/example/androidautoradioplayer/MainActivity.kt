package com.example.androidautoradioplayer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var stationsList: RecyclerView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniPlayerTitle: TextView
    private lateinit var miniPlayerArtwork: ImageView
    private lateinit var miniPlayerPlayPause: ImageButton
    private lateinit var miniPlayerStop: ImageButton
    private lateinit var miniPlayerFavorite: ImageButton
    
    private var currentMode = "list" // "favorites" or "list"
    private var miniPlayerUpdateTimer: Thread? = null

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
                    openSettings()
                    true
                }
                else -> false
            }
        }
        
        // Mini player views
        miniPlayer = findViewById(R.id.mini_player)
        miniPlayerTitle = findViewById(R.id.mini_player_title)
        miniPlayerArtwork = findViewById(R.id.mini_player_artwork)
        miniPlayerPlayPause = findViewById(R.id.mini_player_play_pause)
        miniPlayerStop = findViewById(R.id.mini_player_stop)
        miniPlayerFavorite = findViewById(R.id.mini_player_favorite)
        
        miniPlayerPlayPause.setOnClickListener { togglePlayPause() }
        miniPlayerStop.setOnClickListener { stopPlayback() }
        miniPlayerFavorite.setOnClickListener { toggleMiniPlayerFavorite() }
        
        // Show list by default
        bottomNavigation.selectedItemId = R.id.navigation_list
        
        // Start polling for playback state updates
        startPlaybackStateUpdates()
    }

    private fun showAllStations() {
        currentMode = "list"
        val stations = StationRepository.getStations()
        val adapter = StationAdapter(this, stations, { stationId ->
            playStation(stationId)
        }, { _ ->
            // Do nothing to prevent list jump
        })
        stationsList.adapter = adapter
    }

    private fun showFavorites() {
        currentMode = "favorites"
        val stations = FavoritesPreference.getFavorites(this)
        val adapter = StationAdapter(this, stations, { stationId ->
            playStation(stationId)
        }, { _ ->
            // Do nothing to prevent list jump
        })
        stationsList.adapter = adapter
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
        startPlaybackStateUpdates()
    }
    
    override fun onPause() {
        super.onPause()
        stopPlaybackStateUpdates()
    }

    private fun stopPlayback() {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_STOP
        }
        startService(intent)
        updateMiniPlayer()
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
    
    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
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
        
        if (station != null) {
            // Show mini player
            miniPlayer.visibility = android.view.View.VISIBLE
            miniPlayerTitle.text = station.title
            
            // Load artwork
            Glide.with(this)
                .load(station.logoUrl)
                .error(android.R.drawable.ic_media_play)
                .into(miniPlayerArtwork)
            
            // Update play/pause button - always show the correct state
            miniPlayerPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            
            // Update favorite button state - swap drawable based on favorite status
            val isFavorited = FavoritesPreference.isFavorite(this, station.id)
            if (isFavorited) {
                miniPlayerFavorite.setImageResource(R.drawable.ic_star_filled)
                miniPlayerFavorite.clearColorFilter()
            } else {
                miniPlayerFavorite.setImageResource(R.drawable.ic_star_outline)
                miniPlayerFavorite.clearColorFilter()
            }
        } else {
            // Hide mini player
            miniPlayer.visibility = android.view.View.GONE
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
}

