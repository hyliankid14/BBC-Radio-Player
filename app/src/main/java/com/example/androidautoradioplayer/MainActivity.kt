package com.example.androidautoradioplayer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.ListView

class MainActivity : AppCompatActivity() {
    private lateinit var stationsList: ListView
    private lateinit var btnFavorites: Button
    private lateinit var btnList: Button
    private lateinit var btnSettings: Button
    private lateinit var btnStop: Button
    
    private var currentMode = "list" // "favorites" or "list"

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before creating the view
        val theme = ThemePreference.getTheme(this)
        ThemeManager.applyTheme(theme)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stationsList = findViewById(R.id.stations_list)
        btnStop = findViewById(R.id.btn_stop)
        btnFavorites = findViewById(R.id.btn_favorites)
        btnList = findViewById(R.id.btn_list)
        btnSettings = findViewById(R.id.btn_settings)
        
        btnStop.setOnClickListener { stopPlayback() }
        btnFavorites.setOnClickListener { showFavorites() }
        btnList.setOnClickListener { showAllStations() }
        btnSettings.setOnClickListener { openSettings() }
        
        // Show list by default
        showAllStations()
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
        
        // Launch playback screen
        val playbackIntent = Intent(this, PlaybackActivity::class.java).apply {
            putExtra("station_id", id)
        }
        startActivity(playbackIntent)
    }

    override fun onResume() {
        super.onResume()
        // Restore button states when returning from settings
        updateButtonStates()
    }

    private fun stopPlayback() {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_STOP
        }
        startService(intent)
    }
    
    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}
