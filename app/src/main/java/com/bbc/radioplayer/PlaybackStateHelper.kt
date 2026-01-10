package com.bbc.radioplayer

/**
 * Helper to track playback state across the app
 */
object PlaybackStateHelper {
    private var currentStation: Station? = null
    private var isPlaying: Boolean = false
    private var currentShow: CurrentShow = CurrentShow("BBC Radio")
    private val showChangeListeners = mutableListOf<(CurrentShow) -> Unit>()
    
    fun setCurrentStation(station: Station?) {
        android.util.Log.d("PlaybackStateHelper", "setCurrentStation called: ${station?.title}")
        currentStation = station
    }
    
    fun getCurrentStation(): Station? = currentStation
    
    fun setIsPlaying(playing: Boolean) {
        isPlaying = playing
    }
    
    fun getIsPlaying(): Boolean = isPlaying
    
    fun setCurrentShow(show: CurrentShow) {
        android.util.Log.d("PlaybackStateHelper", "setCurrentShow called: title='${show.title}', listeners count=${showChangeListeners.size}")
        currentShow = show
        notifyShowChangeListeners()
    }
    
    fun getCurrentShow(): CurrentShow = currentShow
    
    fun getCurrentShowTitle(): String = currentShow.getFormattedTitle()
    
    fun onShowChange(listener: (CurrentShow) -> Unit) {
        showChangeListeners.add(listener)
        android.util.Log.d("PlaybackStateHelper", "Listener registered, total listeners now: ${showChangeListeners.size}")
    }
    
    fun removeShowChangeListener(listener: (CurrentShow) -> Unit) {
        showChangeListeners.remove(listener)
        android.util.Log.d("PlaybackStateHelper", "Listener removed, total listeners now: ${showChangeListeners.size}")
    }
    
    private fun notifyShowChangeListeners() {
        android.util.Log.d("PlaybackStateHelper", "notifyShowChangeListeners: notifying ${showChangeListeners.size} listeners")
        showChangeListeners.forEach { it(currentShow) }
    }
    
    fun isPlayingAny(): Boolean = currentStation != null && isPlaying
}
