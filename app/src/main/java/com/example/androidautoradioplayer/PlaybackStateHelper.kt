package com.example.androidautoradioplayer

/**
 * Helper to track playback state across the app
 */
object PlaybackStateHelper {
    private var currentStation: Station? = null
    private var isPlaying: Boolean = false
    private var currentShow: CurrentShow = CurrentShow("BBC Radio")
    private val showChangeListeners = mutableListOf<(CurrentShow) -> Unit>()
    
    fun setCurrentStation(station: Station?) {
        currentStation = station
    }
    
    fun getCurrentStation(): Station? = currentStation
    
    fun setIsPlaying(playing: Boolean) {
        isPlaying = playing
    }
    
    fun getIsPlaying(): Boolean = isPlaying
    
    fun setCurrentShow(show: CurrentShow) {
        currentShow = show
        notifyShowChangeListeners()
    }
    
    fun getCurrentShow(): CurrentShow = currentShow
    
    fun getCurrentShowTitle(): String = currentShow.getFormattedTitle()
    
    fun onShowChange(listener: (CurrentShow) -> Unit) {
        showChangeListeners.add(listener)
    }
    
    fun removeShowChangeListener(listener: (CurrentShow) -> Unit) {
        showChangeListeners.remove(listener)
    }
    
    private fun notifyShowChangeListeners() {
        showChangeListeners.forEach { it(currentShow) }
    }
    
    fun isPlayingAny(): Boolean = currentStation != null && isPlaying
}
