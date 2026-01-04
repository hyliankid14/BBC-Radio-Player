package com.example.androidautoradioplayer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CurrentShow(
    val title: String,
    val secondary: String? = null,
    val tertiary: String? = null,
    val imageUrl: String? = null,
    val startTime: String? = null,
    val endTime: String? = null
) {
    // Format the full subtitle as "primary - secondary - tertiary"
    fun getFormattedTitle(): String {
        val parts = mutableListOf(title)
        if (!secondary.isNullOrEmpty()) parts.add(secondary)
        if (!tertiary.isNullOrEmpty()) parts.add(tertiary)
        return parts.joinToString(" - ")
    }
}

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
            
            val show = parseShowFromRmsResponse(response)
            Log.d(TAG, "Parsed show: $show")
            
            show ?: CurrentShow("BBC Radio")
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
            
            val show = parseShowFromEssResponse(response)
            show ?: CurrentShow("BBC Radio")
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching from ESS API: ${e.message}")
            CurrentShow("BBC Radio")
        }
    }
    
    private fun parseShowFromRmsResponse(json: String): CurrentShow? {
        try {
            // Extract all three titles and image URL
            val primaryRegex = "\"primary\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val secondaryRegex = "\"secondary\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val tertiaryRegex = "\"tertiary\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val imageUrlRegex = "\"image_url\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            
            val primary = primaryRegex.find(json)?.groupValues?.getOrNull(1)?.trim()
            val secondary = secondaryRegex.find(json)?.groupValues?.getOrNull(1)?.trim()
            val tertiary = tertiaryRegex.find(json)?.groupValues?.getOrNull(1)?.trim()
            val rawImageUrl = imageUrlRegex.find(json)?.groupValues?.getOrNull(1)?.trim()
            
            // Validate and unescape the URL if it contains escaped characters
            var imageUrl: String? = null
            if (!rawImageUrl.isNullOrEmpty()) {
                var unescapedUrl = rawImageUrl.replace("\\/", "/").replace("\\\\", "\\")
                
                // Replace BBC image recipe placeholder if present
                if (unescapedUrl.contains("{recipe}")) {
                    unescapedUrl = unescapedUrl.replace("{recipe}", "320x320")
                }
                
                // Only use URL if it looks valid
                if (unescapedUrl.isNotEmpty() && unescapedUrl.startsWith("http")) {
                    imageUrl = unescapedUrl
                }
            }
            
            if (primary != null) {
                Log.d(TAG, "Found RMS: primary=$primary, secondary=$secondary, tertiary=$tertiary, imageUrl=$imageUrl")
                return CurrentShow(
                    title = primary,
                    secondary = secondary,
                    tertiary = tertiary,
                    imageUrl = imageUrl
                )
            }
            
            Log.w(TAG, "No primary title found in RMS response")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing RMS response: ${e.message}")
            return null
        }
    }
    
    private fun parseShowFromEssResponse(json: String): CurrentShow? {
        try {
            // Look for program title in ESS response
            // This is typically in the schedule for the current time
            val titleRegex = "\"title\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val match = titleRegex.find(json)
            
            if (match != null) {
                val title = match.groupValues.getOrNull(1)?.trim()
                Log.d(TAG, "Found ESS title: $title")
                return CurrentShow(title = title ?: "BBC Radio")
            }
            
            Log.w(TAG, "No title found in ESS response")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing ESS response: ${e.message}")
            return null
        }
    }
}
