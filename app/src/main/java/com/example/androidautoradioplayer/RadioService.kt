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
import com.google.android.exoplayer2.MediaItem as ExoMediaItem
import com.google.android.exoplayer2.audio.AudioAttributes as ExoAudioAttributes
import android.util.Log

class RadioService : MediaBrowserServiceCompat() {
    private lateinit var mediaSession: MediaSessionCompat
    private var player: ExoPlayer? = null
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentStationTitle: String = ""
    private var currentStationId: String = ""

    companion object {
        const val ACTION_PLAY_STATION = "com.example.androidautoradioplayer.ACTION_PLAY_STATION"
        const val ACTION_PLAY = "com.example.androidautoradioplayer.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.androidautoradioplayer.ACTION_PAUSE"
        const val ACTION_STOP = "com.example.androidautoradioplayer.ACTION_STOP"
        const val ACTION_TOGGLE_FAVORITE = "com.example.androidautoradioplayer.ACTION_TOGGLE_FAVORITE"
        const val EXTRA_STATION_ID = "com.example.androidautoradioplayer.EXTRA_STATION_ID"
        private const val TAG = "RadioService"
        private const val CHANNEL_ID = "radio_playback"
        private const val NOTIFICATION_ID = 1
        private const val CUSTOM_ACTION_TOGGLE_FAVORITE = "TOGGLE_FAVORITE"
        
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
                if (action == CUSTOM_ACTION_TOGGLE_FAVORITE && currentStationId.isNotEmpty()) {
                    toggleFavoriteAndNotify(currentStationId)
                }
            }
        })
        
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
        val favoriteLabel = if (currentStationId.isNotEmpty() && FavoritesPreference.isFavorite(this, currentStationId)) {
            "Remove from Favorites"
        } else {
            "Add to Favorites"
        }
        
        val pbState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            .addCustomAction(
                CUSTOM_ACTION_TOGGLE_FAVORITE, 
                favoriteLabel,
                android.R.drawable.btn_star_big_off
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
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
            }
            MEDIA_ID_FAVORITES -> {
                val favorites = FavoritesPreference.getFavorites(this)
                favorites.forEach { station ->
                    items.add(createMediaItem(station))
                }
            }
            MEDIA_ID_ALL_STATIONS -> {
                val stations = StationRepository.getStations()
                stations.forEach { station ->
                    items.add(createMediaItem(station))
                }
            }
            else -> {
                Log.d(TAG, "Unknown parentId: $parentId")
            }
        }
        
        Log.d(TAG, "Sending ${items.size} items for parentId: $parentId")
        result.sendResult(items)
    }

    private fun createMediaItem(station: Station): MediaItem {
        return MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(station.id)
                .setTitle(station.title)
                .setSubtitle("BBC Radio")
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

        // Create stop action
        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_media_pause,
            "Stop",
            createPendingIntent(ACTION_STOP, "stop_action")
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentStationTitle.ifEmpty { "BBC Radio Player" })
            .setContentText("Live Stream")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSound(null)
            .setVibrate(null)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setStyle(MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        startForeground(NOTIFICATION_ID, notification)
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
        
        // Update global playback state
        PlaybackStateHelper.setCurrentStation(station)
        PlaybackStateHelper.setIsPlaying(true)
        
        // Release existing player to ensure clean state
        player?.release()
        player = null
        
        ensurePlayer()
        requestAudioFocus()
        startForegroundNotification()
        
        // Set metadata for Android Auto display with album art
        val metadata = android.support.v4.media.MediaMetadataCompat.Builder()
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID, station.id)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, station.title)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, "BBC Radio")
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM, "Live Stream")
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, station.logoUrl)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, station.logoUrl)
            .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, -1)
            .build()
        mediaSession.setMetadata(metadata)
        
        // Indicate buffering immediately to prevent UI from showing "Stopped"
        updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)

        player?.apply {
            playWhenReady = true
            setMediaItem(ExoMediaItem.fromUri(streamUri))
            prepare()
        }
    }

    private fun stopPlayback() {
        player?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        
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
                ACTION_TOGGLE_FAVORITE -> {
                    if (currentStationId.isNotEmpty()) {
                        toggleFavoriteAndNotify(currentStationId)
                    }
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
        super.onDestroy()
    }
}
