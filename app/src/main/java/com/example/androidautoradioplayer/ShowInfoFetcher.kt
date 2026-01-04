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
    
    // Mapping of station IDs to BBC Programmes schedule page IDs
    private val stationIdMap = mapOf(
        "radio1" to "p00fzl86",
        "1xtra" to "p00fzl64",
        "radio2" to "p00fzl8v",
        "radio3" to "p00fzl8t",
        "radio4" to "p00fzl7j",
        "radio4extra" to "p00fzl7l",
        "radio5live" to "p00fzl7g",
        "radio6" to "p00fzl65",
        "asiannetwork" to "p00fzl68",
        "radiobristol" to "p00fzl75",
        "radiodevon" to "p00fzl7d",
        "radioleeds" to "p00fzl7w",
        "radiolon" to "p00fzl6f",
        "radionorthampton" to "p00fzl84",
        "radionottingham" to "p00fzl85",
        "radiosolent" to "p00fzl8l",
        "radiotees" to "p00fzl93",
        "radioscotland" to "p00fzl8d",
        "radiowales" to "p00fzl8y",
        "radiocymru" to "p00fzl7b",
        "radioulster" to "p00fzl8w",
        "radiofoyle" to "p00fzl7m"
    )
    
    suspend fun getCurrentShow(stationId: String): CurrentShow = withContext(Dispatchers.IO) {
        try {
            val bbcId = stationIdMap[stationId] ?: return@withContext CurrentShow("BBC Radio")
            
            // Get today's date for the schedule URL
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
            val dateStr = dateFormat.format(calendar.time)
            val url = "https://www.bbc.co.uk/schedules/$bbcId/$dateStr"
            
            Log.d(TAG, "Fetching schedule for station $stationId from $url")
            
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "AndroidAutoRadioPlayer/1.0")
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")
            
            if (responseCode != 200) {
                Log.w(TAG, "Failed to fetch schedule: HTTP $responseCode")
                return@withContext CurrentShow("BBC Radio")
            }
            
            val html = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            Log.d(TAG, "HTML response length: ${html.length} bytes")
            
            // Parse HTML to find current show
            val showTitle = parseCurrentShowFromHtml(html)
            Log.d(TAG, "Parsed show title: $showTitle")
            
            CurrentShow(showTitle ?: "BBC Radio")
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching show info: ${e.message}", e)
            CurrentShow("BBC Radio")
        }
    }
    
    private fun parseCurrentShowFromHtml(html: String): String? {
        try {
            // Get current time
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val currentTimeInMinutes = hour * 60 + minute
            
            // Find the ON AIR section which marks the currently playing show
            val onAirRegex = "ON AIR[\\s\\S]*?#### \\[\\d+ [A-Za-z]+ (\\d+):\\d+: (.+?),".toRegex()
            val match = onAirRegex.find(html)
            
            if (match != null) {
                val showHour = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
                val showTitle = match.groupValues.getOrNull(2)?.trim()
                
                Log.d(TAG, "Found ON AIR show at $showHour:00 - $showTitle")
                return showTitle
            }
            
            // Alternative: find show by looking for most recent hour before current time
            val hourPattern = "#### \\[(\\d{1,2}) [A-Za-z]+ \\d+:00: (.+?),".toRegex()
            val matches = hourPattern.findAll(html)
            
            var closestShow: String? = null
            var closestTime = -1
            
            for (m in matches) {
                val showHour = m.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
                val showTitle = m.groupValues.getOrNull(2)?.trim() ?: continue
                
                if (showHour <= hour && showHour > closestTime) {
                    closestTime = showHour
                    closestShow = showTitle
                }
            }
            
            if (closestShow != null) {
                Log.d(TAG, "Found closest show: $closestShow at $closestTime:00")
                return closestShow
            }
            
            Log.w(TAG, "Could not parse current show from HTML")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing HTML: ${e.message}")
            return null
        }
    }
