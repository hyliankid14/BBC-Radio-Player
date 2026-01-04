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
    
    private val serviceIdMap by lazy {
        StationRepository.getStations().associate { it.id to it.serviceId }
    }
    
    suspend fun getCurrentShow(stationId: String): CurrentShow = withContext(Dispatchers.IO) {
        try {
            val serviceId = serviceIdMap[stationId] ?: return@withContext CurrentShow("BBC Radio")
            // Add timestamp to prevent caching
            val url = "https://rms.api.bbc.co.uk/v2/services/$serviceId/segments/latest?t=${System.currentTimeMillis()}"
            
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
    
    suspend fun getScheduleCurrentShow(stationId: String): CurrentShow = withContext(Dispatchers.IO) {
        val serviceId = serviceIdMap[stationId] ?: return@withContext CurrentShow("BBC Radio")
        return@withContext fetchShowFromEss(serviceId)
    }
    
    private suspend fun fetchShowFromEss(serviceId: String): CurrentShow {
        return try {
            // Add timestamp to prevent caching
            val url = "https://ess.api.bbci.co.uk/schedules?serviceId=$serviceId&t=${System.currentTimeMillis()}"
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
                
                // Only use URL if it looks valid and isn't a known placeholder pattern
                // p01tqv8z is the standard BBC "blocks" placeholder
                if (unescapedUrl.isNotEmpty() && 
                    unescapedUrl.startsWith("http") && 
                    !unescapedUrl.contains("default", ignoreCase = true) &&
                    !unescapedUrl.contains("p01tqv8z", ignoreCase = true)) {
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
            // We need to find the item that is currently on air
            // The JSON structure is {"items": [{"published_time": {"start": "...", "end": "..."}, "brand": {"title": "..."}, "episode": {"title": "..."}}, ...]}
            
            val now = java.time.Instant.now()
            val itemsArrayStart = json.indexOf("\"items\":[")
            if (itemsArrayStart == -1) return null
            
            // Simple manual parsing to avoid heavy JSON library if not present
            // Split by "published_time" to find segments
            val segments = json.substring(itemsArrayStart).split("{\"id\":")
            
            for (segment in segments) {
                if (!segment.contains("published_time")) continue
                
                try {
                    // Extract start and end times
                    val startMatch = "\"start\":\"([^\"]+)\"".toRegex().find(segment)
                    val endMatch = "\"end\":\"([^\"]+)\"".toRegex().find(segment)
                    
                    if (startMatch != null && endMatch != null) {
                        val startTimeStr = startMatch.groupValues[1]
                        val endTimeStr = endMatch.groupValues[1]
                        
                        val start = java.time.Instant.parse(startTimeStr)
                        val end = java.time.Instant.parse(endTimeStr)
                        
                        if (now.isAfter(start) && now.isBefore(end)) {
                            // This is the current show
                            val brandMatch = "\"brand\":\\{\"title\":\"([^\"]+)\"".toRegex().find(segment)
                            val episodeMatch = "\"episode\":\\{.*\"title\":\"([^\"]+)\"".toRegex().find(segment)
                            
                            val brandTitle = brandMatch?.groupValues?.get(1)
                            val episodeTitle = episodeMatch?.groupValues?.get(1)
                            
                            // Prefer Brand Title (Show Name), fallback to Episode Title
                            val title = brandTitle ?: episodeTitle ?: "BBC Radio"
                            val subtitle = if (brandTitle != null && episodeTitle != null) episodeTitle else null
                            
                            Log.d(TAG, "Found current ESS show: $title ($subtitle)")
                            return CurrentShow(title = title, secondary = subtitle)
                        }
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            
            Log.w(TAG, "No current show found in ESS schedule")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing ESS response: ${e.message}")
            return null
        }
    }
}
