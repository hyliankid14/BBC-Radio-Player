package com.hyliankid14.bbcradioplayer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.text.method.ScrollingMovementMethod
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.button.MaterialButton
import android.content.res.ColorStateList
import com.hyliankid14.bbcradioplayer.PodcastSubscriptions

class NowPlayingActivity : AppCompatActivity() {
    private lateinit var stationArtwork: ImageView
    private lateinit var showName: TextView
    private lateinit var episodeTitle: TextView
    private lateinit var artistTrack: TextView
    private lateinit var stopButton: MaterialButton
    private lateinit var previousButton: MaterialButton
    private lateinit var playPauseButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private lateinit var favoriteButton: MaterialButton
    private lateinit var seekBar: SeekBar
    private lateinit var progressGroup: android.view.View
    private lateinit var elapsedView: TextView
    private lateinit var remainingView: TextView
    
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
        episodeTitle = findViewById(R.id.now_playing_episode_title)
        artistTrack = findViewById(R.id.now_playing_artist_track)
        stopButton = findViewById(R.id.now_playing_stop)
        previousButton = findViewById(R.id.now_playing_previous)
        playPauseButton = findViewById(R.id.now_playing_play_pause)
        nextButton = findViewById(R.id.now_playing_next)
        favoriteButton = findViewById(R.id.now_playing_favorite)
        progressGroup = findViewById(R.id.podcast_progress_group)
        seekBar = findViewById(R.id.playback_seekbar)
        elapsedView = findViewById(R.id.playback_elapsed)
        remainingView = findViewById(R.id.playback_remaining)

        // Setup control button listeners
        stopButton.setOnClickListener { stopPlayback() }
        previousButton.setOnClickListener { skipToPrevious() }
        playPauseButton.setOnClickListener { togglePlayPause() }
        nextButton.setOnClickListener { skipToNext() }
        favoriteButton.setOnClickListener { toggleFavorite() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val show = PlaybackStateHelper.getCurrentShow()
                    val duration = show.segmentDurationMs ?: return
                    if (duration <= 0) return
                    val newPos = (duration * (progress / seekBar!!.max.toDouble())).toLong()
                    sendSeekTo(newPos)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Register listener for show changes
        PlaybackStateHelper.onShowChange(showChangeListener)
        
        // Initial update
        // Allow long descriptions to be scrolled
        artistTrack.isVerticalScrollBarEnabled = true
        artistTrack.movementMethod = ScrollingMovementMethod()
        artistTrack.setOnTouchListener { v, _ ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
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
        val isPodcast = station?.id?.startsWith("podcast_") == true

        if (station != null) {
            // Update station name in action bar
            supportActionBar?.title = station.title
            
            if (isPodcast) {
                // Podcasts: action bar already shows podcast name; hide duplicate header
                showName.visibility = android.view.View.GONE

                val episodeHeading = show.episodeTitle?.takeIf { it.isNotEmpty() } ?: show.title
                if (!episodeHeading.isNullOrEmpty()) {
                    episodeTitle.text = episodeHeading
                    episodeTitle.visibility = android.view.View.VISIBLE
                } else {
                    episodeTitle.visibility = android.view.View.GONE
                }

                val description = show.description?.let {
                    androidx.core.text.HtmlCompat.fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
                }
                if (!description.isNullOrEmpty()) {
                    artistTrack.text = description
                    artistTrack.maxLines = 4
                    artistTrack.ellipsize = null
                    artistTrack.isVerticalScrollBarEnabled = true
                    artistTrack.movementMethod = ScrollingMovementMethod()
                    artistTrack.visibility = android.view.View.VISIBLE
                } else {
                    artistTrack.visibility = android.view.View.GONE
                }
            } else {
                // Radio: show name plus artist/track metadata
                showName.visibility = android.view.View.VISIBLE
                showName.text = show.title.ifEmpty { "BBC Radio" }
                
                if (!show.episodeTitle.isNullOrEmpty()) {
                    episodeTitle.text = show.episodeTitle
                    episodeTitle.visibility = android.view.View.VISIBLE
                } else {
                    episodeTitle.visibility = android.view.View.GONE
                }
                
                if (!show.secondary.isNullOrEmpty() || !show.tertiary.isNullOrEmpty()) {
                    val parts = mutableListOf<String>()
                    if (!show.secondary.isNullOrEmpty()) parts.add(show.secondary)
                    if (!show.tertiary.isNullOrEmpty()) parts.add(show.tertiary)
                    artistTrack.text = parts.joinToString(" - ")
                    artistTrack.visibility = android.view.View.VISIBLE
                } else {
                    artistTrack.visibility = android.view.View.GONE
                }
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
            
            updateProgressUi()

            // Update play/pause button
            playPauseButton.icon = ContextCompat.getDrawable(this, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            
            val podcastId = station.id.removePrefix("podcast_")
            val isFavorited = if (isPodcast) {
                PodcastSubscriptions.isSubscribed(this, podcastId)
            } else {
                FavoritesPreference.isFavorite(this, station.id)
            }
            favoriteButton.icon = ContextCompat.getDrawable(this, if (isFavorited) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            favoriteButton.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.md_theme_primary))
            favoriteButton.setBackgroundColor(if (isFavorited) ContextCompat.getColor(this, R.color.md_theme_primaryContainer) else android.graphics.Color.TRANSPARENT)
        } else {
            progressGroup.visibility = android.view.View.GONE
            seekBar.visibility = android.view.View.GONE
        }
    }

    private fun updateProgressUi() {
        val station = PlaybackStateHelper.getCurrentStation()
        val isPodcast = station?.id?.startsWith("podcast_") == true
        val showProgress = PlaybackStateHelper.getCurrentShow()
        val pos = showProgress.segmentStartMs ?: 0L
        val dur = showProgress.segmentDurationMs ?: 0L

        if (isPodcast && dur > 0) {
            progressGroup.visibility = android.view.View.VISIBLE
            seekBar.visibility = android.view.View.VISIBLE
            val ratio = (pos.toDouble() / dur.toDouble()).coerceIn(0.0, 1.0)
            seekBar.progress = (ratio * seekBar.max).toInt()
            elapsedView.text = formatTime(pos)
            remainingView.text = "-${formatTime((dur - pos).coerceAtLeast(0))}"
            seekBar.isEnabled = true
        } else {
            progressGroup.visibility = android.view.View.GONE
            seekBar.visibility = android.view.View.GONE
        }
    }

    private fun sendSeekTo(positionMs: Long) {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_SEEK_TO
            putExtra(RadioService.EXTRA_SEEK_POSITION, positionMs)
        }
        startService(intent)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
    
    private fun updateFromShow(show: CurrentShow) {
        if (isFinishing || isDestroyed) return
        val station = PlaybackStateHelper.getCurrentStation()
        val isPodcast = station?.id?.startsWith("podcast_") == true
        
        if (isPodcast) {
            showName.visibility = android.view.View.GONE

            val episodeHeading = show.episodeTitle?.takeIf { it.isNotEmpty() } ?: show.title
            if (!episodeHeading.isNullOrEmpty()) {
                episodeTitle.text = episodeHeading
                episodeTitle.visibility = android.view.View.VISIBLE
            } else {
                episodeTitle.visibility = android.view.View.GONE
            }

            val description = show.description?.let {
                androidx.core.text.HtmlCompat.fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
            }
            if (!description.isNullOrEmpty()) {
                artistTrack.text = description
                artistTrack.maxLines = 4
                artistTrack.ellipsize = null
                artistTrack.isVerticalScrollBarEnabled = true
                artistTrack.movementMethod = ScrollingMovementMethod()
                artistTrack.visibility = android.view.View.VISIBLE
            } else {
                artistTrack.visibility = android.view.View.GONE
            }
        } else {
            showName.visibility = android.view.View.VISIBLE
            // Update show name
            showName.text = show.title.ifEmpty { "BBC Radio" }
            
            // Update episode title if available
            if (!show.episodeTitle.isNullOrEmpty()) {
                episodeTitle.text = show.episodeTitle
                episodeTitle.visibility = android.view.View.VISIBLE
            } else {
                episodeTitle.visibility = android.view.View.GONE
            }
            
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

            updateProgressUi()
    }
    
    private fun isPlaceholderImage(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        
        // Check for 1x1 placeholder
        if (width == 1 && height == 1) return true
        
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

    private fun stopPlayback() {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_STOP
        }
        startService(intent)
        finish()
    }

    private fun skipToPrevious() {
        val station = PlaybackStateHelper.getCurrentStation()
        if (station?.id?.startsWith("podcast_") == true) {
            val intent = Intent(this, RadioService::class.java).apply {
                action = RadioService.ACTION_SEEK_DELTA
                putExtra(RadioService.EXTRA_SEEK_DELTA, -10_000L)
            }
            startService(intent)
        } else {
            val intent = Intent(this, RadioService::class.java).apply {
                action = RadioService.ACTION_SKIP_TO_PREVIOUS
            }
            startService(intent)
        }
    }

    private fun skipToNext() {
        val station = PlaybackStateHelper.getCurrentStation()
        if (station?.id?.startsWith("podcast_") == true) {
            val intent = Intent(this, RadioService::class.java).apply {
                action = RadioService.ACTION_SEEK_DELTA
                putExtra(RadioService.EXTRA_SEEK_DELTA, 30_000L)
            }
            startService(intent)
        } else {
            val intent = Intent(this, RadioService::class.java).apply {
                action = RadioService.ACTION_SKIP_TO_NEXT
            }
            startService(intent)
        }
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
            if (station.id.startsWith("podcast_")) {
                val podcastId = station.id.removePrefix("podcast_")
                PodcastSubscriptions.toggleSubscription(this, podcastId)
            } else {
                FavoritesPreference.toggleFavorite(this, station.id)
            }
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
