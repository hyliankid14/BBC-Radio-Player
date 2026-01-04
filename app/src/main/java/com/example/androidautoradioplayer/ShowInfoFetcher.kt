package com.example.androidautoradioplayer

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CurrentShow(
    val title: String,
    val startTime: String? = null,
    val endTime: String? = null
)

object ShowInfoFetcher {
    private const val TAG = "ShowInfoFetcher"
    
    suspend fun getCurrentShow(stationId: String): CurrentShow = withContext(Dispatchers.IO) {
        // BBC schedule APIs are not currently accessible
        // Future enhancement: implement scraping or alternative data source
        Log.d(TAG, "Show info requested for station: $stationId - returning default BBC Radio")
        CurrentShow("BBC Radio")
    }
}
