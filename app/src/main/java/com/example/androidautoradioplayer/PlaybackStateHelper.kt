package com.example.androidautoradioplayer

/**
 * Helper to track playback state across the app
 */
object PlaybackStateHelper {
    private var currentStation: Station? = null
    private var isPlaying: Boolean = false
    
    fun setCurrentStation(station: Station?) {
        currentStation = station
    }
    
    fun getCurrentStation(): Station? = currentStation
    
    fun setIsPlaying(playing: Boolean) {
        isPlaying = playing
    }
    
    fun getIsPlaying(): Boolean = isPlaying
    
    fun isPlayingAny(): Boolean = currentStation != null && isPlaying
}
