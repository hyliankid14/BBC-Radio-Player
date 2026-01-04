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
            // For BBC Radio 2 as an example, the show info endpoint
            val bbcId = stationIdMap[stationId] ?: return@withContext CurrentShow("BBC Radio")
            
            // Try the main BBC Programmes API endpoint
            val url = "https://www.bbc.co.uk/programmes/$bbcId.json"
            
            Log.d(TAG, "Fetching show info for station $stationId (bbcId: $bbcId) from $url")
            
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "AndroidAutoRadioPlayer/1.0")
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")
            
            if (responseCode != 200) {
                Log.w(TAG, "Failed to fetch show info: HTTP $responseCode, trying alternate endpoint")
                
                // Try alternate endpoint
                val altUrl = "https://www.bbc.co.uk/schedules/$bbcId.json"
                val altConnection = java.net.URL(altUrl).openConnection() as java.net.HttpURLConnection
                altConnection.connectTimeout = 5000
                altConnection.readTimeout = 5000
                altConnection.setRequestProperty("User-Agent", "AndroidAutoRadioPlayer/1.0")
                
                val altResponseCode = altConnection.responseCode
                Log.d(TAG, "Alternate endpoint response code: $altResponseCode")
                
                if (altResponseCode != 200) {
                    Log.w(TAG, "Alternate endpoint also failed: HTTP $altResponseCode")
                    return@withContext CurrentShow("BBC Radio")
                }
                
                val response = altConnection.inputStream.bufferedReader().readText()
                altConnection.disconnect()
                
                Log.d(TAG, "Response length: ${response.length} bytes")
                if (response.length < 500) {
                    Log.d(TAG, "Response: $response")
                }
                
                val showTitle = parseShowFromJson(response)
                Log.d(TAG, "Parsed show title: $showTitle")
                
                return@withContext CurrentShow(showTitle ?: "BBC Radio")
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            Log.d(TAG, "Response length: ${response.length} bytes")
            if (response.length < 500) {
                Log.d(TAG, "Response: $response")
            }
            
            // Parse JSON response
            val showTitle = parseShowFromJson(response)
            Log.d(TAG, "Parsed show title: $showTitle")
            
            CurrentShow(showTitle ?: "BBC Radio")
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching show info: ${e.message}", e)
            CurrentShow("BBC Radio")
        }
    }
    
    private fun parseShowFromJson(json: String): String? {
        try {
            // Find the first "title" field in the JSON which represents the current/upcoming show
            val titleRegex = "\"title\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val match = titleRegex.find(json)
            
            if (match == null) {
                Log.w(TAG, "No title match found in JSON")
                return null
            }
            
            val title = match.groupValues.getOrNull(1)
            Log.d(TAG, "Found title match: $title")
            
            return title?.let { t ->
                // Clean up HTML entities if present
                t.replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing show from JSON: ${e.message}")
            return null
        }
    }
}
