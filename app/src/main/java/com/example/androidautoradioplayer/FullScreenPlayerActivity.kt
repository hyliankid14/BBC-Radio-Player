package com.example.androidautoradioplayer

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

class FullScreenPlayerActivity : AppCompatActivity() {

    private lateinit var artworkView: ImageView
    private lateinit var titleView: TextView
    private lateinit var showTitleView: TextView
    private lateinit var nowPlayingView: ScrollingTextView
    private lateinit var backButton: ImageButton
    private lateinit var playPauseButton: ImageButton
    private lateinit var previousButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var favoriteButton: ImageButton

    private var updateThread: Thread? = null
    private var lastArtworkUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme
        val theme = ThemePreference.getTheme(this)
        ThemeManager.applyTheme(theme)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_player)

        // Hide action bar for full screen experience
        supportActionBar?.hide()

        artworkView = findViewById(R.id.player_artwork)
        titleView = findViewById(R.id.player_station_title)
        showTitleView = findViewById(R.id.player_show_title)
        nowPlayingView = findViewById(R.id.player_now_playing)
        backButton = findViewById(R.id.player_back)
        playPauseButton = findViewById(R.id.player_play_pause)
        previousButton = findViewById(R.id.player_previous)
        nextButton = findViewById(R.id.player_next)
        stopButton = findViewById(R.id.player_stop)
        favoriteButton = findViewById(R.id.player_favorite)

        setupClickListeners()
        updateUI()
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }

        playPauseButton.setOnClickListener {
            val isPlaying = PlaybackStateHelper.getIsPlaying()
            PlaybackStateHelper.setIsPlaying(!isPlaying)
            
            val intent = Intent(this, RadioService::class.java).apply {
                action = if (isPlaying) RadioService.ACTION_PAUSE else RadioService.ACTION_PLAY
            }
            startService(intent)
            updateUI()
        }

        previousButton.setOnClickListener {
            val intent = Intent(this, RadioService::class.java).apply {
                action = RadioService.ACTION_SKIP_TO_PREVIOUS
            }
            startService(intent)
        }

        nextButton.setOnClickListener {
            val intent = Intent(this, RadioService::class.java).apply {
                action = RadioService.ACTION_SKIP_TO_NEXT
            }
            startService(intent)
        }

        stopButton.setOnClickListener {
            val intent = Intent(this, RadioService::class.java).apply {
                action = RadioService.ACTION_STOP
            }
            startService(intent)
            finish() // Close the player and return to list
        }
        
        favoriteButton.setOnClickListener {
            val station = PlaybackStateHelper.getCurrentStation()
            if (station != null) {
                FavoritesPreference.toggleFavorite(this, station.id)
                updateUI()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startUpdateThread()
    }

    override fun onPause() {
        super.onPause()
        stopUpdateThread()
    }

    private fun startUpdateThread() {
        stopUpdateThread()
        updateThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(500)
                    runOnUiThread { updateUI() }
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
        updateThread?.start()
    }

    private fun stopUpdateThread() {
        updateThread?.interrupt()
        updateThread = null
    }

    private fun updateUI() {
        val station = PlaybackStateHelper.getCurrentStation()
        val show = PlaybackStateHelper.getCurrentShow()
        val isPlaying = PlaybackStateHelper.getIsPlaying()

        if (station == null) {
            // If nothing is playing, close the player
            finish()
            return
        }

        titleView.text = station.title
        
        // Update Show Title (Programme Name)
        val currentShowTitle = show?.title ?: ""
        if (showTitleView.text.toString() != currentShowTitle) {
            showTitleView.text = currentShowTitle
        }
        
        // Update Now Playing Info - Use same logic as Mini Player
        // Display formatted show title (Artist - Track, or Show Name if no song info)
        val formattedTitle = show?.getFormattedTitle() ?: ""
        
        if (nowPlayingView.text.toString() != formattedTitle) {
            nowPlayingView.text = formattedTitle
            nowPlayingView.isSelected = true
            nowPlayingView.startScrolling()
        }
        
        // Always show the Now Playing view if we have any content
        val shouldBeVisible = formattedTitle.isNotEmpty()
        if (shouldBeVisible && nowPlayingView.visibility != android.view.View.VISIBLE) {
            nowPlayingView.visibility = android.view.View.VISIBLE
        } else if (!shouldBeVisible && nowPlayingView.visibility != android.view.View.GONE) {
            nowPlayingView.visibility = android.view.View.GONE
        }

        playPauseButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)

        // Update favorite icon
        val isFavorited = FavoritesPreference.isFavorite(this, station.id)
        if (isFavorited) {
            favoriteButton.setImageResource(R.drawable.ic_star_filled)
            favoriteButton.setColorFilter(android.graphics.Color.parseColor("#FFC107"))
        } else {
            favoriteButton.setImageResource(R.drawable.ic_star_outline)
            favoriteButton.clearColorFilter()
        }

        // Artwork logic
        val artworkUrl = if (!show?.imageUrl.isNullOrEmpty() && show?.imageUrl?.startsWith("http") == true) {
            show.imageUrl
        } else {
            station.logoUrl
        }

        if (artworkUrl != null && artworkUrl != lastArtworkUrl) {
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
                            Log.d("FullScreenPlayer", "Detected placeholder image, falling back to logo")
                            artworkView.post {
                                Glide.with(this@FullScreenPlayerActivity)
                                    .load(fallbackUrl)
                                    .into(artworkView)
                            }
                            return true
                        }
                        return false
                    }
                })
                .into(artworkView)
        }
    }
    
    // Copy of the placeholder detection logic
    private fun isPlaceholderImage(bitmap: android.graphics.Bitmap): Boolean {
        if (bitmap.width < 10 || bitmap.height < 10) return false
        
        val width = bitmap.width
        val height = bitmap.height
        
        // Sample 5 points
        val p1 = bitmap.getPixel(5, 5)
        val p2 = bitmap.getPixel(width - 5, 5)
        val p3 = bitmap.getPixel(5, height - 5)
        val p4 = bitmap.getPixel(width - 5, height - 5)
        val p5 = bitmap.getPixel(width / 2, height / 2)
        
        val pixels = listOf(p1, p2, p3, p4, p5)
        val first = pixels[0]
        
        for (p in pixels) {
            if (!areColorsSimilar(first, p)) return false
        }
        
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
        return diff < 30
    }
    
    private fun isGrey(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        
        val maxDiff = Math.max(Math.abs(r - g), Math.max(Math.abs(r - b), Math.abs(g - b)))
        return maxDiff < 20
    }
}
