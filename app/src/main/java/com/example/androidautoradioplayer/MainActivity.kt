package com.example.androidautoradioplayer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.ListView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before creating the view
        val theme = ThemePreference.getTheme(this)
        ThemeManager.applyTheme(theme)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val stationsList: ListView = findViewById(R.id.stations_list)
        val btnStop: Button = findViewById(R.id.btn_stop)
        val btnSettings: android.widget.ImageButton = findViewById(R.id.btn_settings)
        
        val stations = StationRepository.getStations()
        
        val adapter = StationAdapter(this, stations) { stationId ->
            playStation(stationId)
        }
        
        stationsList.adapter = adapter
        btnStop.setOnClickListener { stopPlayback() }
        btnSettings.setOnClickListener { openSettings() }
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
