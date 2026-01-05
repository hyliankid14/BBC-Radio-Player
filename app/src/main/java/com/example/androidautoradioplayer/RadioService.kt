package com.example.androidautoradioplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.MediaItem as ExoMediaItem
import com.google.android.exoplayer2.audio.AudioAttributes as ExoAudioAttributes
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class RadioService : MediaBrowserServiceCompat() {
    private lateinit var mediaSession: MediaSessionCompat
    private var player: ExoPlayer? = null
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentStationTitle: String = ""
    private var currentStationId: String = ""
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var currentStationLogo: String = ""
    private var currentShowTitle: String = "BBC Radio"
    private var currentShowInfo: CurrentShow = CurrentShow("BBC Radio")
    private var lastSongSignature: String? = null
    private val showInfoPollIntervalMs = 30_000L // Poll RMS at BBC's sweet spot (30s)
    private var currentArtworkBitmap: android.graphics.Bitmap? = null
    private var currentArtworkUri: String? = null
    private var showInfoRefreshRunnable: Runnable? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    
    private val placeholderBitmap by lazy {
        android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
    }

    companion object {
        const val ACTION_PLAY_STATION = "com.example.androidautoradioplayer.ACTION_PLAY_STATION"
        const val ACTION_PLAY = "com.example.androidautoradioplayer.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.androidautoradioplayer.ACTION_PAUSE"
        const val ACTION_STOP = "com.example.androidautoradioplayer.ACTION_STOP"
        const val ACTION_SKIP_TO_NEXT = "com.example.androidautoradioplayer.ACTION_SKIP_TO_NEXT"
        const val ACTION_SKIP_TO_PREVIOUS = "com.example.androidautoradioplayer.ACTION_SKIP_TO_PREVIOUS"
        const val ACTION_TOGGLE_FAVORITE = "com.example.androidautoradioplayer.ACTION_TOGGLE_FAVORITE"
        const val EXTRA_STATION_ID = "com.example.androidautoradioplayer.EXTRA_STATION_ID"
        private const val TAG = "RadioService"
        private const val CHANNEL_ID = "radio_playback"
        private const val NOTIFICATION_ID = 1
        private const val CUSTOM_ACTION_TOGGLE_FAVORITE = "TOGGLE_FAVORITE"
        private const val CUSTOM_ACTION_SPACER = "SPACER"
        private const val CUSTOM_ACTION_STOP = "STOP"
        
        private const val MEDIA_ID_ROOT = "root"
        private const val MEDIA_ID_FAVORITES = "favorites"
        private const val MEDIA_ID_ALL_STATIONS = "all_stations"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate - Service starting")
        createNotificationChannel()
        
        // Create and configure media session FIRST
        mediaSession = MediaSessionCompat(this, "RadioService")
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                Log.d(TAG, "onPlay called")
                player?.play()
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                PlaybackStateHelper.setIsPlaying(true)
                scheduleShowInfoRefresh()
            }

            override fun onSkipToNext() {
                Log.d(TAG, "onSkipToNext")
                skipStation(1)
            }

            override fun onSkipToPrevious() {
                Log.d(TAG, "onSkipToPrevious")
                skipStation(-1)
            }

            override fun onPause() {
                Log.d(TAG, "onPause called")
                player?.pause()
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                PlaybackStateHelper.setIsPlaying(false)
            }

            override fun onStop() {
                Log.d(TAG, "onStop called")
                stopPlayback()
            }

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                Log.d(TAG, "onPlayFromMediaId called with mediaId: $mediaId")
                mediaId?.let { playStation(it) }
            }

            override fun onCustomAction(action: String?, extras: Bundle?) {
                Log.d(TAG, "onCustomAction called with action: $action")
                when (action) {
                    CUSTOM_ACTION_STOP -> stopPlayback()
                    CUSTOM_ACTION_TOGGLE_FAVORITE -> {
                        if (currentStationId.isNotEmpty()) {
                            toggleFavoriteAndNotify(currentStationId)
                        }
                    }
                }
            }
        })
        
        @Suppress("DEPRECATION")
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        mediaSession.isActive = true
        
        // Set session activity for Android Auto
        val sessionIntent = Intent(this, MainActivity::class.java)
        val sessionPendingIntent = PendingIntent.getActivity(
            this, 0, sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession.setSessionActivity(sessionPendingIntent)
        
        // Set session token IMMEDIATELY
        sessionToken = mediaSession.sessionToken
        Log.d(TAG, "Session token set: $sessionToken")
        
        // Set initial playback state
        updatePlaybackState(PlaybackStateCompat.STATE_NONE)
        
        Log.d(TAG, "onCreate complete - Service ready")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radio Playback",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.setSound(null, null)
            channel.enableVibration(false)
            channel.enableLights(false)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updatePlaybackState(state: Int) {
        val isFavorite = currentStationId.isNotEmpty() && FavoritesPreference.isFavorite(this, currentStationId)
        val favoriteLabel = if (isFavorite) {
            "Remove from Favorites"
        } else {
            "Add to Favorites"
        }
        
        val favoriteIcon = if (isFavorite) {
            R.drawable.ic_star_filled_yellow
        } else {
            R.drawable.ic_star_outline
        }
        
        val pbState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            // Use the left custom-action slot for Stop (some Android media UIs don't show ACTION_STOP)
            .addCustomAction(
                CUSTOM_ACTION_STOP,
                "Stop",
                R.drawable.ic_stop
            )
            .addCustomAction(
                CUSTOM_ACTION_TOGGLE_FAVORITE, 
                favoriteLabel,
                favoriteIcon
            )
            .build()
        mediaSession.setPlaybackState(pbState)
    }

    override fun onGetRoot(clientName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        Log.d(TAG, "onGetRoot called for client: $clientName, uid: $clientUid")
        
        val extras = Bundle().apply {
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1) // 1 = LIST
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1) // 1 = LIST
        }
        
        Log.d(TAG, "onGetRoot returning root with extras")
        return BrowserRoot("root", extras)
    }

    override fun onLoadChildren(parentId: String, result: Result<List<MediaItem>>) {
        Log.d(TAG, "onLoadChildren - parentId: $parentId")
        
        result.detach()
        serviceScope.launch {
            val items = mutableListOf<MediaItem>()

            when (parentId) {
                MEDIA_ID_ROOT -> {
                    // Add "Favorites" folder
                    items.add(MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setMediaId(MEDIA_ID_FAVORITES)
                            .setTitle("Favorites")
                            .build(),
                        MediaItem.FLAG_BROWSABLE
                    ))
                    
                    // Add "All Stations" folder
                    items.add(MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setMediaId(MEDIA_ID_ALL_STATIONS)
                            .setTitle("All Stations")
                            .build(),
                        MediaItem.FLAG_BROWSABLE
                    ))
                    result.sendResult(items)
                }
                MEDIA_ID_FAVORITES -> {
                    val favorites = FavoritesPreference.getFavorites(this@RadioService)
                    val itemsWithShowInfo = favorites.map { station ->
                        async(Dispatchers.IO) {
                            val show = ShowInfoFetcher.getScheduleCurrentShow(station.id)
                            createMediaItem(station, show.title)
                        }
                    }.awaitAll()
                    result.sendResult(itemsWithShowInfo)
                }
                MEDIA_ID_ALL_STATIONS -> {
                    val stations = StationRepository.getStations()
                    val itemsWithShowInfo = stations.map { station ->
                        async(Dispatchers.IO) {
                            val show = ShowInfoFetcher.getScheduleCurrentShow(station.id)
                            createMediaItem(station, show.title)
                        }
                    }.awaitAll()
                    result.sendResult(itemsWithShowInfo)
                }
                else -> {
                    Log.d(TAG, "Unknown parentId: $parentId")
                    result.sendResult(null)
                }
            }
        }
    }

    private fun createMediaItem(station: Station, subtitle: String = ""): MediaItem {
        // If subtitle is "BBC Radio", treat it as empty to avoid redundancy
        val displaySubtitle = if (subtitle == "BBC Radio") "" else subtitle
        return MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(station.id)
                .setTitle(station.title)
                .setSubtitle(displaySubtitle)
                .setIconUri(android.net.Uri.parse(station.logoUrl))
                .build(),
            MediaItem.FLAG_PLAYABLE
        )
    }

    private fun ensurePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(this).build().apply {
                // Configure audio attributes for music playback
                setAudioAttributes(
                    ExoAudioAttributes.Builder()
                        .setUsage(com.google.android.exoplayer2.C.USAGE_MEDIA)
                        .setContentType(com.google.android.exoplayer2.C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val state = when (playbackState) {
                            Player.STATE_READY -> if (playWhenReady) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
                            Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
                            Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
                            Player.STATE_IDLE -> if (playWhenReady) PlaybackStateCompat.STATE_BUFFERING else PlaybackStateCompat.STATE_STOPPED
                            else -> PlaybackStateCompat.STATE_NONE
                        }
                        updatePlaybackState(state)
                        
                        // Update helper for mini player
                        when (state) {
                            PlaybackStateCompat.STATE_PLAYING -> PlaybackStateHelper.setIsPlaying(true)
                            PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.STATE_STOPPED -> PlaybackStateHelper.setIsPlaying(false)
                            else -> {}
                        }
                    }
                    
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Playback error: ${error.message}", error)
                        // Auto-reconnect after a delay
                        handler.postDelayed({
                            if (currentStationId.isNotEmpty()) {
                                Log.d(TAG, "Attempting to reconnect to station: $currentStationId")
                                playStation(currentStationId)
                            }
                        }, 3000) // Wait 3 seconds before reconnecting
                    }
                })
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .build()
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun startForegroundNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create previous action
        val previousAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            "Previous",
            createPendingIntent(ACTION_SKIP_TO_PREVIOUS, "previous_action")
        )

        // Create next action
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "Next",
            createPendingIntent(ACTION_SKIP_TO_NEXT, "next_action")
        )

        // Create play/pause action
        val playPauseAction = if (player?.isPlaying == true) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                createPendingIntent(ACTION_PAUSE, "pause_action")
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                createPendingIntent(ACTION_PLAY, "play_action")
            )
        }

        // Create stop action using custom stop icon
        val stopAction = NotificationCompat.Action(
            R.drawable.ic_stop,
            "Stop",
            createPendingIntent(ACTION_STOP, "stop_action")
        )

        // Create favorite action
        val isFavorite = currentStationId.isNotEmpty() && FavoritesPreference.isFavorite(this, currentStationId)
        val favoriteAction = NotificationCompat.Action(
            if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
            if (isFavorite) "Remove from Favorites" else "Add to Favorites",
            createPendingIntent(ACTION_TOGGLE_FAVORITE, "favorite_action")
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentStationTitle.ifEmpty { "BBC Radio Player" })
            .setContentText(currentShowTitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSound(null)
            .setVibrate(null)
            .addAction(stopAction)
            .addAction(previousAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(favoriteAction)
            .setStyle(MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notification = notificationBuilder.build()
        startForeground(NOTIFICATION_ID, notification)

        // Load station logo asynchronously and update notification
        if (currentStationLogo.isNotEmpty()) {
            loadStationLogoAndUpdateNotification()
        }
    }

    private fun loadStationLogoAndUpdateNotification() {
        // Snapshot play state on the main thread; ExoPlayer is not thread-safe.
        val isPlayingSnapshot = PlaybackStateHelper.getIsPlaying()
        Thread {
            try {
                // Use image_url from API if available and valid, otherwise fall back to station logo
                var imageUrl: String = when {
                    !currentShowInfo.imageUrl.isNullOrEmpty() && currentShowInfo.imageUrl?.startsWith("http") == true -> currentShowInfo.imageUrl!!
                    else -> currentStationLogo
                }
                
                if (imageUrl.isEmpty()) {
                    Log.d(TAG, "No image URL available for notification")
                    return@Thread
                }
                
                Log.d(TAG, "Loading notification artwork from: $imageUrl")
                
                var bitmap: android.graphics.Bitmap? = null
                var finalUrl = imageUrl
                
                try {
                    bitmap = com.bumptech.glide.Glide.with(this)
                        .asBitmap()
                        .load(imageUrl)
                        .submit(256, 256) // Request 256x256 bitmap
                        .get() // Block until loaded
                        
                    // Check if the loaded bitmap is actually a placeholder (grey box)
                    if (bitmap != null && isPlaceholderImage(bitmap)) {
                        Log.d(TAG, "Detected placeholder image from $imageUrl, forcing fallback")
                        bitmap = null // Discard it
                        throw Exception("Detected placeholder image")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load artwork from $imageUrl: ${e.message}")
                    // Fallback to station logo if we weren't already using it
                    if (imageUrl != currentStationLogo && currentStationLogo.isNotEmpty()) {
                        Log.d(TAG, "Falling back to station logo: $currentStationLogo")
                        finalUrl = currentStationLogo
                        try {
                            bitmap = com.bumptech.glide.Glide.with(this)
                                .asBitmap()
                                .load(finalUrl)
                                .submit(256, 256)
                                .get()
                        } catch (e2: Exception) {
                            Log.w(TAG, "Failed to load fallback station logo: ${e2.message}")
                        }
                    }
                }

                if (bitmap != null) {
                    // Cache artwork so later metadata refreshes don't wipe it
                    currentArtworkBitmap = bitmap
                    currentArtworkUri = finalUrl

                    // Update notification with the artwork
                    // Recreate all actions to maintain functionality
                    val previousAction = NotificationCompat.Action(
                        android.R.drawable.ic_media_previous,
                        "Previous",
                        createPendingIntent(ACTION_SKIP_TO_PREVIOUS, "previous_action")
                    )
                    val nextAction = NotificationCompat.Action(
                        android.R.drawable.ic_media_next,
                        "Next",
                        createPendingIntent(ACTION_SKIP_TO_NEXT, "next_action")
                    )
                    val playPauseAction = if (isPlayingSnapshot) {
                        NotificationCompat.Action(
                            android.R.drawable.ic_media_pause,
                            "Pause",
                            createPendingIntent(ACTION_PAUSE, "pause_action")
                        )
                    } else {
                        NotificationCompat.Action(
                            android.R.drawable.ic_media_play,
                            "Play",
                            createPendingIntent(ACTION_PLAY, "play_action")
                        )
                    }
                    val stopAction = NotificationCompat.Action(
                        R.drawable.ic_stop,
                        "Stop",
                        createPendingIntent(ACTION_STOP, "stop_action")
                    )
                    val isFavorite = currentStationId.isNotEmpty() && FavoritesPreference.isFavorite(this, currentStationId)
                    val favoriteAction = NotificationCompat.Action(
                        if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
                        if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                        createPendingIntent(ACTION_TOGGLE_FAVORITE, "favorite_action")
                    )

                    // Create intent to launch app when notification is tapped
                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val updatedNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle(currentStationTitle.ifEmpty { "BBC Radio Player" })
                        .setContentText(currentShowTitle)
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .setLargeIcon(bitmap)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .setSound(null)
                        .setVibrate(null)
                        .addAction(stopAction)
                        .addAction(previousAction)
                        .addAction(playPauseAction)
                        .addAction(nextAction)
                        .addAction(favoriteAction)
                        .setStyle(MediaStyle()
                            .setMediaSession(mediaSession.sessionToken)
                            .setShowActionsInCompactView(0, 1, 2)
                        )
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .build()

                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                    Log.d(TAG, "Updated notification with artwork from: $finalUrl")
                    
                    // Update MediaSession metadata with the bitmap AND the correct URI
                    updateMediaMetadata(bitmap, finalUrl)
                } else {
                    // If bitmap load failed completely, still update metadata with the fallback URI
                    // This ensures AA at least has a valid URI to try, rather than the broken one or the placeholder
                    if (finalUrl.isNotEmpty()) {
                         Log.d(TAG, "Bitmap load failed, updating metadata with URI only: $finalUrl")
                         currentArtworkBitmap = null
                         currentArtworkUri = finalUrl
                         updateMediaMetadata(null, finalUrl)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load artwork for notification: ${e.message}")
            }
        }.start()
    }
    
    private fun isPlaceholderImage(bitmap: android.graphics.Bitmap): Boolean {
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

    private fun createPendingIntent(action: String, tag: String): PendingIntent {
        val intent = Intent(this, RadioService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun playStation(stationId: String) {
        val station = StationRepository.getStations().firstOrNull { it.id == stationId }
        if (station == null) {
            Log.w(TAG, "Unknown station: $stationId")
            return
        }
        
        // Get quality preference
        val highQuality = ThemePreference.getHighQuality(this)
        val streamUri = station.getUri(highQuality)
        
        Log.d(TAG, "Playing station: ${station.title} - $streamUri (HQ: $highQuality)")
        
        currentStationTitle = station.title
        currentStationId = station.id
        currentStationLogo = station.logoUrl
        currentShowInfo = CurrentShow("") // Reset to empty to avoid "BBC Radio" flash
        currentShowTitle = ""
        currentArtworkBitmap = null
        currentArtworkUri = currentStationLogo
        lastSongSignature = null // Reset last song signature for new station
        
        // Cancel existing show refresh
        showInfoRefreshRunnable?.let { handler.removeCallbacks(it) }
        
        // Fetch current show information
        fetchAndUpdateShowInfo(station.id)
        
        // Update global playback state - SET STATION FIRST before notifying listeners
        PlaybackStateHelper.setCurrentStation(station)
        PlaybackStateHelper.setCurrentShow(currentShowInfo) // Clear show info in helper to prevent flashing old metadata
        PlaybackStateHelper.setIsPlaying(true)
        
        // Release existing player to ensure clean state
        player?.release()
        player = null
        
        ensurePlayer()
        requestAudioFocus()
        
        // Set metadata immediately with station logo URI (lets the system show artwork ASAP)
        updateMediaMetadata(artworkBitmap = null, artworkUri = currentStationLogo)
        
        startForegroundNotification()
        
        // Indicate buffering immediately to prevent UI from showing "Stopped"
        updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)

        player?.apply {
            playWhenReady = true
            setMediaItem(ExoMediaItem.fromUri(streamUri))
            prepare()
        }
        
        // Schedule periodic show info refresh (every 30 seconds)
        scheduleShowInfoRefresh()
    }

    private fun fetchAndUpdateShowInfo(stationId: String) {
        Log.d(TAG, "fetchAndUpdateShowInfo called for station: $stationId")
        Thread {
            try {
                Log.d(TAG, "Fetching show info in background thread for station: $stationId")
                var show = runBlocking { ShowInfoFetcher.getCurrentShow(stationId) }
                
                // Check if we are still playing the requested station
                if (stationId != currentStationId) {
                    Log.d(TAG, "Station changed during fetch (requested: $stationId, current: $currentStationId), ignoring result")
                    return@Thread
                }

                Log.d(TAG, "ShowInfoFetcher returned: ${show.title}")

                // Track RMS server cache TTL for smarter polling
                Log.d(TAG, "RMS Cache-Control max-age: ${ShowInfoFetcher.lastRmsCacheMaxAgeMs}ms")
                
                // Only update song data when RMS explicitly returns it
                // If RMS returns empty secondary/tertiary, clear song data immediately
                val songSignature = listOf(show.secondary, show.tertiary)
                    .filter { !it.isNullOrEmpty() }
                    .joinToString("|")
                    .ifEmpty { null }

                var finalShow = show
                if (songSignature != null) {
                    if (songSignature != lastSongSignature) {
                        lastSongSignature = songSignature
                        Log.d(TAG, "New song detected: $songSignature")
                    }
                } else {
                    // RMS returned no song data - clear immediately
                    if (lastSongSignature != null) {
                        Log.d(TAG, "RMS stopped returning song data. Reverting to show name.")
                        finalShow = show.copy(secondary = null, tertiary = null)
                        lastSongSignature = null
                    }
                }
                
                // Update show info (clear song data when RMS returns none)
                currentShowInfo = finalShow
                val formattedTitle = finalShow.getFormattedTitle()
                // If the title is just the generic default, treat it as empty to avoid redundancy
                currentShowTitle = if (formattedTitle == "BBC Radio") "" else formattedTitle
                
                PlaybackStateHelper.setCurrentShow(finalShow)
                Log.d(TAG, "Set currentShowTitle to: $currentShowTitle, imageUrl: ${finalShow.imageUrl}")
                
                // Switch to main thread to update UI
                handler.post {
                    Log.d(TAG, "Updating UI with show title: $currentShowTitle")
                    // If we got a now-playing image URL, prefer it for subsequent metadata/notification updates.
                    val nowPlayingImageUrl = finalShow.imageUrl
                    if (!nowPlayingImageUrl.isNullOrEmpty() && nowPlayingImageUrl.startsWith("http")) {
                        currentArtworkUri = nowPlayingImageUrl
                    } else {
                        // If no valid image URL, clear the sticky artwork URI so we fall back to station logo
                        currentArtworkUri = null
                    }
                    updateMediaMetadata()
                    startForegroundNotification()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching show info: ${e.message}", e)
            }
        }.start()
    }
    
    private fun scheduleShowInfoRefresh() {
        // Cancel existing scheduled refresh
        showInfoRefreshRunnable?.let { handler.removeCallbacks(it) }
        
        // Create new refresh runnable - poll every 30 seconds (BBC's optimal cadence)
        showInfoRefreshRunnable = Runnable {
            if (currentStationId.isNotEmpty() && PlaybackStateHelper.getIsPlaying()) {
                fetchAndUpdateShowInfo(currentStationId)
                // Schedule next refresh
                handler.postDelayed(showInfoRefreshRunnable!!, showInfoPollIntervalMs)
            }
        }
        
        // Schedule first refresh
        handler.postDelayed(showInfoRefreshRunnable!!, showInfoPollIntervalMs)
    }

    private fun skipStation(step: Int) {
        val stations = getScrollableStations()
        if (stations.isEmpty()) return

        val currentIndex = stations.indexOfFirst { it.id == currentStationId }
        val targetIndex = if (currentIndex == -1) {
            0
        } else {
            (currentIndex + step + stations.size) % stations.size
        }

        playStation(stations[targetIndex].id)
    }

    private fun getScrollableStations(): List<Station> {
        val mode = ScrollingPreference.getScrollMode(this)
        val favorites = FavoritesPreference.getFavorites(this)
        return if (mode == ScrollingPreference.MODE_FAVORITES && favorites.isNotEmpty()) {
            favorites
        } else {
            StationRepository.getStations()
        }
    }
    
    private fun updateMediaMetadata(artworkBitmap: android.graphics.Bitmap? = null, artworkUri: String? = null) {
        // Keep artwork sticky across metadata refreshes. Some OEM media UIs only show art
        // if a bitmap is present in the MediaSession metadata.
        // Prefer the latest now-playing artwork URL (RMS) when available.
        val displayUri = artworkUri
            ?: currentShowInfo.imageUrl
            ?: currentArtworkUri
            ?: currentStationLogo

        val displayBitmap = artworkBitmap ?: currentArtworkBitmap

        val metadataBuilder = android.support.v4.media.MediaMetadataCompat.Builder()
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID, currentStationId)
            // Station Name as Title (Large)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, currentStationTitle)
            // Show Name as Artist (Small)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, currentShowTitle)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, currentStationTitle)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, currentShowTitle)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM, "Live Stream")
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, displayUri)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, displayUri)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART_URI, displayUri)
            .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, -1)
            
        if (displayBitmap != null) {
            metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, displayBitmap)
            metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, displayBitmap)
            metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART, displayBitmap)
        }

        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun stopPlayback() {
        player?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        
        // Cancel show refresh
        showInfoRefreshRunnable?.let { handler.removeCallbacks(it) }
        
        // Update global playback state
        PlaybackStateHelper.setCurrentStation(null)
        PlaybackStateHelper.setIsPlaying(false)
        
        Log.d(TAG, "Playback stopped")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand - action: ${intent?.action}")
        intent?.let {
            when (it.action) {
                ACTION_PLAY_STATION -> {
                    val id = it.getStringExtra(EXTRA_STATION_ID)
                    id?.let { playStation(it) }
                }
                ACTION_PLAY -> {
                    player?.play()
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    startForegroundNotification()
                }
                ACTION_PAUSE -> {
                    player?.pause()
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                    startForegroundNotification()
                }
                ACTION_STOP -> {
                    stopPlayback()
                }
                ACTION_SKIP_TO_NEXT -> {
                    skipStation(1)
                }
                ACTION_SKIP_TO_PREVIOUS -> {
                    skipStation(-1)
                }
                ACTION_TOGGLE_FAVORITE -> {
                    if (currentStationId.isNotEmpty()) {
                        toggleFavoriteAndNotify(currentStationId)
                    }
                    Unit
                }
                else -> {
                    Log.w(TAG, "Unknown action: ${it.action}")
                }
            }
        }
        return START_STICKY
    }
    
    private fun toggleFavoriteAndNotify(stationId: String) {
        Log.d(TAG, "toggleFavoriteAndNotify - stationId: $stationId")
        FavoritesPreference.toggleFavorite(this, stationId)
        
        // Notify the media browser clients that favorites have changed
        notifyChildrenChanged(MEDIA_ID_FAVORITES)
        
        // Also notify all stations list to update star icons immediately
        notifyChildrenChanged(MEDIA_ID_ALL_STATIONS)
        
        // Update playback state to reflect new favorite status
        updatePlaybackState(mediaSession.controller.playbackState?.state ?: PlaybackStateCompat.STATE_PLAYING)
    }
    
    override fun onBind(intent: Intent?): android.os.IBinder? {
        Log.d(TAG, "onBind - action: ${intent?.action}")
        return super.onBind(intent)
    }

    override fun onDestroy() {
        player?.release()
        mediaSession.release()
        serviceScope.cancel()
        super.onDestroy()
    }
}
