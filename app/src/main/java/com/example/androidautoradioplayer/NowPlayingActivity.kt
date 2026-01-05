package com.example.androidautoradioplayer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

class NowPlayingActivity : AppCompatActivity() {
    private lateinit var stationArtwork: ImageView
    private lateinit var showName: TextView
    private lateinit var artistTrack: TextView
    private lateinit var stopButton: ImageButton
    private lateinit var previousButton: ImageButton
    private lateinit var playPauseButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var favoriteButton: ImageButton
    
    private var updateTimer: Thread? = null
    private var lastArtworkUrl: String? = null
    private val showChangeListener: (CurrentShow) -> Unit = { show ->
        runOnUiThread { updateFromShow(show) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)

        // Setup action bar with back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(
                android.graphics.Color.parseColor("#6200EE")
            )
        )

        // Initialize views
        stationArtwork = findViewById(R.id.now_playing_artwork)
        showName = findViewById(R.id.now_playing_show_name)
        artistTrack = findViewById(R.id.now_playing_artist_track)
        stopButton = findViewById(R.id.now_playing_stop)
        previousButton = findViewById(R.id.now_playing_previous)
        playPauseButton = findViewById(R.id.now_playing_play_pause)
        nextButton = findViewById(R.id.now_playing_next)
        favoriteButton = findViewById(R.id.now_playing_favorite)

        // Setup control button listeners
        stopButton.setOnClickListener { stopPlayback() }
        previousButton.setOnClickListener { skipToPrevious() }
        playPauseButton.setOnClickListener { togglePlayPause() }
        nextButton.setOnClickListener { skipToNext() }
        favoriteButton.setOnClickListener { toggleFavorite() }

        // Register listener for show changes
        PlaybackStateHelper.onShowChange(showChangeListener)
        
        // Initial update
        updateUI()
        
        // Start polling for playback state updates
        startPlaybackStateUpdates()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        startPlaybackStateUpdates()
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        stopPlaybackStateUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        PlaybackStateHelper.removeShowChangeListener(showChangeListener)
        stopPlaybackStateUpdates()
    }

    private fun updateUI() {
        if (isFinishing || isDestroyed) return
        
        val station = PlaybackStateHelper.getCurrentStation()
        val isPlaying = PlaybackStateHelper.getIsPlaying()
        val show = PlaybackStateHelper.getCurrentShow()

        if (station != null) {
            // Update station name in action bar
            supportActionBar?.title = station.title
            
            // Update show name
            showName.text = show.title.ifEmpty { "BBC Radio" }
            
            // Update artist/track info if available
            if (!show.secondary.isNullOrEmpty() || !show.tertiary.isNullOrEmpty()) {
                val parts = mutableListOf<String>()
                if (!show.secondary.isNullOrEmpty()) parts.add(show.secondary)
                if (!show.tertiary.isNullOrEmpty()) parts.add(show.tertiary)
                artistTrack.text = parts.joinToString(" - ")
                artistTrack.visibility = android.view.View.VISIBLE
            } else {
                artistTrack.visibility = android.view.View.GONE
            }
            
            // Load artwork: Use image_url from API if available and valid, otherwise station logo
            val artworkUrl = if (!show.imageUrl.isNullOrEmpty() && show.imageUrl.startsWith("http")) {
                show.imageUrl
            } else {
                station.logoUrl
            }
            
            // Only reload if URL changed
            if (artworkUrl != null && artworkUrl != lastArtworkUrl && !isFinishing && !isDestroyed) {
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
                                stationArtwork.post {
                                    Glide.with(this@NowPlayingActivity)
                                        .load(fallbackUrl)
                                        .into(stationArtwork)
                                }
                                return true
                            }
                            return false
                        }
                    })
                    .into(stationArtwork)
            }
            
            // Update play/pause button
            playPauseButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            
            // Update favorite button state
            val isFavorited = FavoritesPreference.isFavorite(this, station.id)
            if (isFavorited) {
                favoriteButton.setImageResource(R.drawable.ic_star_filled)
            } else {
                favoriteButton.setImageResource(R.drawable.ic_star_outline)
            }
        }
    }
    
    private fun updateFromShow(show: CurrentShow) {
        if (isFinishing || isDestroyed) return
        
        // Update show name
        showName.text = show.title.ifEmpty { "BBC Radio" }
        
        // Update artist/track info if available
        if (!show.secondary.isNullOrEmpty() || !show.tertiary.isNullOrEmpty()) {
            val parts = mutableListOf<String>()
            if (!show.secondary.isNullOrEmpty()) parts.add(show.secondary)
            if (!show.tertiary.isNullOrEmpty()) parts.add(show.tertiary)
            artistTrack.text = parts.joinToString(" - ")
            artistTrack.visibility = android.view.View.VISIBLE
        } else {
            artistTrack.visibility = android.view.View.GONE
        }
        
        // Load new artwork - use image_url if available and valid, otherwise station logo
        val artworkUrl = if (!show.imageUrl.isNullOrEmpty() && show.imageUrl.startsWith("http")) {
            show.imageUrl
        } else {
            PlaybackStateHelper.getCurrentStation()?.logoUrl
        }
        
        // Only reload if URL changed
        if (artworkUrl != null && artworkUrl != lastArtworkUrl && !isFinishing && !isDestroyed) {
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
                            stationArtwork.post {
                                Glide.with(this@NowPlayingActivity)
                                    .load(fallbackUrl)
                                    .into(stationArtwork)
                            }
                            return true
                        }
                        return false
                    }
                })
                .into(stationArtwork)
        }
    }
    
    private fun isPlaceholderImage(bitmap: Bitmap): Boolean {
        // Check if bitmap is a 1x1 transparent placeholder
        return bitmap.width == 1 && bitmap.height == 1
    }

    private fun stopPlayback() {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_STOP
        }
        startService(intent)
        updateUI()
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
        updateUI()
    }

    private fun toggleFavorite() {
        val station = PlaybackStateHelper.getCurrentStation()
        if (station != null) {
            FavoritesPreference.toggleFavorite(this, station.id)
            updateUI()
        }
    }

    private fun startPlaybackStateUpdates() {
        stopPlaybackStateUpdates()
        updateTimer = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(500) // Update every 500ms
                    if (!isFinishing && !isDestroyed) {
                        runOnUiThread { updateUI() }
                    }
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
        updateTimer?.start()
    }

    private fun stopPlaybackStateUpdates() {
        updateTimer?.interrupt()
        updateTimer = null
    }
}
