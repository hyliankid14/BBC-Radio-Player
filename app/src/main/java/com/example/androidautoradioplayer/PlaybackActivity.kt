package com.example.androidautoradioplayer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class PlaybackActivity : AppCompatActivity() {
    private lateinit var stationArtwork: ImageView
    private lateinit var stationTitle: TextView
    private lateinit var stationSubtitle: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var btnBack: ImageButton
    
    private var currentStationId: String = ""
    private var isPlaying: Boolean = false
    private val updateHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before creating the view
        val theme = ThemePreference.getTheme(this)
        ThemeManager.applyTheme(theme)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)

        currentStationId = intent.getStringExtra("station_id") ?: return

        stationArtwork = findViewById(R.id.station_artwork)
        stationTitle = findViewById(R.id.station_title)
        stationSubtitle = findViewById(R.id.station_subtitle)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnStop = findViewById(R.id.btn_stop)
        btnBack = findViewById(R.id.btn_back)

        val station = StationRepository.getStations().firstOrNull { it.id == currentStationId }
        if (station != null) {
            stationTitle.text = station.title
            stationSubtitle.text = "BBC Radio"
            
            // Load station artwork with Glide
            Glide.with(this)
                .load(station.logoUrl)
                .into(stationArtwork)
        }

        // Set up button listeners
        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnStop.setOnClickListener { stopPlayback() }
        btnBack.setOnClickListener { finish() }

        // Start playback
        playStation(currentStationId)
        updatePlaybackUI()
    }

    override fun onResume() {
        super.onResume()
        // Update UI periodically to show current playback state
        updateHandler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        updateHandler.removeCallbacks(updateRunnable)
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            updatePlaybackUI()
            updateHandler.postDelayed(this, 500)
        }
    }

    private fun updatePlaybackUI() {
        // Update play/pause button icon based on current playback state
        // This is a visual representation; actual state is managed by RadioService
        btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    private fun playStation(stationId: String) {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_PLAY_STATION
            putExtra(RadioService.EXTRA_STATION_ID, stationId)
        }
        startService(intent)
        isPlaying = true
        updatePlaybackUI()
    }

    private fun togglePlayPause() {
        val action = if (isPlaying) RadioService.ACTION_PAUSE else RadioService.ACTION_PLAY
        val intent = Intent(this, RadioService::class.java).apply {
            this.action = action
        }
        startService(intent)
        isPlaying = !isPlaying
        updatePlaybackUI()
    }

    private fun stopPlayback() {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_STOP
        }
        startService(intent)
        isPlaying = false
        updatePlaybackUI()
        finish()
    }
}
