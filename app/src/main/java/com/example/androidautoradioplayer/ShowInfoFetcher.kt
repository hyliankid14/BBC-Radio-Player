package com.example.androidautoradioplayer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CurrentShow(
    val title: String,
    val startTime: String? = null,
    val endTime: String? = null
)

object ShowInfoFetcher {
    private const val TAG = "ShowInfoFetcher"
    
    // Mapping of station IDs to BBC RMS service IDs
    private val serviceIdMap = mapOf(
        "radio1" to "bbc_radio_one",
        "1xtra" to "bbc_1xtra",
        "radio2" to "bbc_radio_two",
        "radio3" to "bbc_radio_three",
        "radio4" to "bbc_radio_fourfm",
        "radio4extra" to "bbc_radio_four_extra",
        "radio5live" to "bbc_radio_five_live",
        "radio6" to "bbc_6music",
        "asiannetwork" to "bbc_asian_network",
        "radiobristol" to "bbc_radio_bristol",
        "radiodevon" to "bbc_radio_devon",
        "radioleeds" to "bbc_radio_leeds",
        "radiolon" to "bbc_london",
        "radionorthampton" to "bbc_radio_northampton",
        "radionottingham" to "bbc_radio_nottingham",
        "radiosolent" to "bbc_radio_solent",
        "radiotees" to "bbc_radio_tees",
        "radioscotland" to "bbc_radio_scotland_fm",
        "radiowales" to "bbc_radio_wales_fm",
        "radiocymru" to "bbc_radio_cymru",
        "radioulster" to "bbc_radio_ulster",
        "radiofoyle" to "bbc_radio_foyle"
    )
    
    suspend fun getCurrentShow(stationId: String): CurrentShow = withContext(Dispatchers.IO) {
        try {
            val serviceId = serviceIdMap[stationId] ?: return@withContext CurrentShow("BBC Radio")
            val url = "https://rms.api.bbc.co.uk/v2/services/$serviceId/segments/latest"
            
            Log.d(TAG, "Fetching now playing info for $stationId ($serviceId) from $url")
            
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "AndroidAutoRadioPlayer/1.0")
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")
            
            if (responseCode != 200) {
                Log.w(TAG, "RMS API returned HTTP $responseCode, trying ESS API as fallback")
                connection.disconnect()
                
                // Try ESS API for schedule info
                return@withContext fetchShowFromEss(serviceId)
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            Log.d(TAG, "RMS Response length: ${response.length} bytes")
            
            val showTitle = parseShowFromRmsResponse(response)
            Log.d(TAG, "Parsed show title: $showTitle")
            
            CurrentShow(showTitle ?: "BBC Radio")
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching show info: ${e.message}", e)
            CurrentShow("BBC Radio")
        }
    }
    
    private suspend fun fetchShowFromEss(serviceId: String): CurrentShow {
        return try {
            val url = "https://ess.api.bbci.co.uk/schedules?serviceId=$serviceId"
            Log.d(TAG, "Fetching from ESS API: $url")
            
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "AndroidAutoRadioPlayer/1.0")
            
            val responseCode = connection.responseCode
            Log.d(TAG, "ESS Response code: $responseCode")
            
            if (responseCode != 200) {
                connection.disconnect()
                return CurrentShow("BBC Radio")
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            val showTitle = parseShowFromEssResponse(response)
            CurrentShow(showTitle ?: "BBC Radio")
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching from ESS API: ${e.message}")
            CurrentShow("BBC Radio")
        }
    }
    
    private fun parseShowFromRmsResponse(json: String): String? {
        try {
            // Look for titles.primary field which contains the current song/show name
            // Example: "primary": "Texas Hold 'Em"
            val primaryRegex = "\"primary\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val primaryMatch = primaryRegex.find(json)
            
            if (primaryMatch != null) {
                val primary = primaryMatch.groupValues.getOrNull(1)?.trim()
                Log.d(TAG, "Found primary title: $primary")
                return primary
            }
            
            // Also check for secondary (artist) if primary not found
            val secondaryRegex = "\"secondary\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val secondaryMatch = secondaryRegex.find(json)
            if (secondaryMatch != null) {
                val secondary = secondaryMatch.groupValues.getOrNull(1)?.trim()
                Log.d(TAG, "Found secondary title: $secondary")
                return secondary
            }
            
            Log.w(TAG, "No title found in RMS response")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing RMS response: ${e.message}")
            return null
        }
    }
    
    private fun parseShowFromEssResponse(json: String): String? {
        try {
            // Look for program title in ESS response
            // This is typically in the schedule for the current time
            val titleRegex = "\"title\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val match = titleRegex.find(json)
            
            if (match != null) {
                val title = match.groupValues.getOrNull(1)?.trim()
                Log.d(TAG, "Found ESS title: $title")
                return title
            }
            
            Log.w(TAG, "No title found in ESS response")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing ESS response: ${e.message}")
            return null
        }
    }
}
