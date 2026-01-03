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
        if (currentMode == "favorites") {
            btnFavorites.isEnabled = false
            btnList.isEnabled = true
        } else {
            btnFavorites.isEnabled = true
            btnList.isEnabled = false
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
