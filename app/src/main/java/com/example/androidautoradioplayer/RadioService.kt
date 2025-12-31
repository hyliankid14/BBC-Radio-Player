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

    companion object {
        const val ACTION_PLAY_STATION = "com.example.androidautoradioplayer.ACTION_PLAY_STATION"
        const val ACTION_STOP = "com.example.androidautoradioplayer.ACTION_STOP"
        const val EXTRA_STATION_ID = "com.example.androidautoradioplayer.EXTRA_STATION_ID"
        private const val TAG = "RadioService"
        private const val CHANNEL_ID = "radio_playback"
        private const val NOTIFICATION_ID = 1
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
            }

            override fun onPause() {
                Log.d(TAG, "onPause called")
                player?.pause()
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            }

            override fun onStop() {
                Log.d(TAG, "onStop called")
                stopPlayback()
            }

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                Log.d(TAG, "onPlayFromMediaId called with mediaId: $mediaId")
                mediaId?.let { playStation(it) }
            }
        })
        
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
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
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updatePlaybackState(state: Int) {
        val pbState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_PAUSE
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
        
        if (parentId != "root") {
            Log.d(TAG, "Non-root parent, sending empty")
            result.sendResult(emptyList())
            return
        }
        
        val items = mutableListOf<MediaItem>()
        
        items.add(MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId("radio2")
                .setTitle("BBC Radio 2")
                .setSubtitle("BBC Radio")
                .build(),
            MediaItem.FLAG_PLAYABLE
        ))
        
        items.add(MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId("radio4")
                .setTitle("BBC Radio 4")
                .setSubtitle("BBC Radio")
                .build(),
            MediaItem.FLAG_PLAYABLE
        ))
        
        items.add(MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId("radio6")
                .setTitle("BBC Radio 6 Music")
                .setSubtitle("BBC Radio")
                .build(),
            MediaItem.FLAG_PLAYABLE
        ))
        
        Log.d(TAG, "Sending ${items.size} items")
        result.sendResult(items)
        Log.d(TAG, "onLoadChildren complete")
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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BBC Radio Player")
            .setContentText("Playing radio")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun playStation(stationId: String) {
        val station = StationRepository.getStations().firstOrNull { it.id == stationId }
        if (station == null) {
            Log.w(TAG, "Unknown station: $stationId")
            return
        }
        Log.d(TAG, "Playing station: ${station.title} - ${station.uri}")
        
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
            setMediaItem(ExoMediaItem.fromUri(station.uri))
            prepare()
        }
    }

    private fun stopPlayback() {
        player?.stop()
        stopForeground(true)
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
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
                ACTION_STOP -> {
                    stopPlayback()
                }
            }
        }
        return START_STICKY
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
