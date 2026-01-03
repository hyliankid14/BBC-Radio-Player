package com.example.androidautoradioplayer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.bumptech.glide.Glide

class MainActivity : AppCompatActivity() {
    private lateinit var stationsList: ListView
    private lateinit var btnFavorites: Button
    private lateinit var btnList: Button
    private lateinit var btnSettings: Button
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniPlayerTitle: TextView
    private lateinit var miniPlayerArtwork: ImageView
    private lateinit var miniPlayerPlayPause: Button
    private lateinit var miniPlayerStop: Button
    
    private var currentMode = "list" // "favorites" or "list"
    private var miniPlayerUpdateTimer: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before creating the view
        val theme = ThemePreference.getTheme(this)
        ThemeManager.applyTheme(theme)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stationsList = findViewById(R.id.stations_list)
        btnFavorites = findViewById(R.id.btn_favorites)
        btnList = findViewById(R.id.btn_list)
        btnSettings = findViewById(R.id.btn_settings)
        
        // Mini player views
        miniPlayer = findViewById(R.id.mini_player)
        miniPlayerTitle = findViewById(R.id.mini_player_title)
        miniPlayerArtwork = findViewById(R.id.mini_player_artwork)
        miniPlayerPlayPause = findViewById(R.id.mini_player_play_pause)
        miniPlayerStop = findViewById(R.id.mini_player_stop)
        
        miniPlayerPlayPause.setOnClickListener { togglePlayPause() }
        miniPlayerStop.setOnClickListener { stopPlayback() }
        
        btnFavorites.setOnClickListener { showFavorites() }
        btnList.setOnClickListener { showAllStations() }
        btnSettings.setOnClickListener { openSettings() }
        
        // Show list by default
        showAllStations()
        
        // Start polling for playback state updates
        startPlaybackStateUpdates()
    }

    private fun showAllStations() {
        currentMode = "list"
        updateButtonStates()
        val stations = StationRepository.getStations()
        val adapter = StationAdapter(this, stations, { stationId ->
            playStation(stationId)
        }, { _ ->
            // Refresh adapter when favorite is toggled
            showAllStations()
        })
        stationsList.adapter = adapter
    }

    private fun showFavorites() {
        currentMode = "favorites"
        updateButtonStates()
        val stations = FavoritesPreference.getFavorites(this)
        val adapter = StationAdapter(this, stations, { stationId ->
            playStation(stationId)
        }, { _ ->
            // Refresh adapter when favorite is toggled
            showFavorites()
        })
        stationsList.adapter = adapter
    }

    private fun updateButtonStates() {
        when (currentMode) {
            "favorites" -> {
                btnFavorites.alpha = 1.0f
                btnList.alpha = 0.6f
                btnSettings.alpha = 0.6f
            }
            "list" -> {
                btnFavorites.alpha = 0.6f
                btnList.alpha = 1.0f
                btnSettings.alpha = 0.6f
            }
            "settings" -> {
                btnFavorites.alpha = 0.6f
                btnList.alpha = 0.6f
                btnSettings.alpha = 1.0f
            }
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
        // Restore button states when returning from settings
        updateButtonStates()
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
        val intent = Intent(this, RadioService::class.java).apply {
            action = if (PlaybackStateHelper.getIsPlaying()) {
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
        
        if (station != null && isPlaying) {
            // Show mini player
            miniPlayer.visibility = android.view.View.VISIBLE
            miniPlayerTitle.text = station.title
            
            // Load artwork
            Glide.with(this)
                .load(station.logoUrl)
                .error(android.R.drawable.ic_media_play)
                .into(miniPlayerArtwork)
            
            // Update play/pause button
            miniPlayerPlayPause.text = if (isPlaying) "⏸" else "▶"
        } else {
            // Hide mini player
            miniPlayer.visibility = android.view.View.GONE
        }
    }
}

