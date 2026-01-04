package com.example.androidautoradioplayer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CurrentShow(
    val title: String, // Show Name (Programme)
    val secondary: String? = null, // Artist (from Segment)
    val tertiary: String? = null, // Track (from Segment)
    val imageUrl: String? = null,
    val startTime: String? = null,
    val endTime: String? = null
) {
    // Format the full subtitle as "primary - secondary - tertiary"
    fun getFormattedTitle(): String {
        // Prioritize "Artist - Track" if available, otherwise use Show Name
        val parts = mutableListOf<String>()
        if (!secondary.isNullOrEmpty()) parts.add(secondary)
        if (!tertiary.isNullOrEmpty()) parts.add(tertiary)
        
        if (parts.isNotEmpty()) {
            return parts.joinToString(" - ")
        }
        return title
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
            
            // 1. Fetch Schedule (Show Name) from ESS
            val scheduleShow = fetchShowFromEss(serviceId)
            val showName = if (scheduleShow.title != "BBC Radio") scheduleShow.title else ""
            
            // 2. Fetch Segment (Now Playing) from RMS
            // Add timestamp to prevent caching
            val url = "https://rms.api.bbc.co.uk/v2/services/$serviceId/segments/latest?t=${System.currentTimeMillis()}"
            
            Log.d(TAG, "Fetching now playing info for $stationId ($serviceId) from $url")
            
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "AndroidAutoRadioPlayer/1.0")
            
            val responseCode = connection.responseCode
            
            var artist: String? = null
            var track: String? = null
            var imageUrl: String? = null
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                
                val segmentShow = parseShowFromRmsResponse(response)
                if (segmentShow != null) {
                    // RMS returns Artist in 'title' (primary) and Track in 'secondary'
                    artist = segmentShow.title
                    track = segmentShow.secondary
                    imageUrl = segmentShow.imageUrl
                }
            } else {
                connection.disconnect()
            }
            
            // Combine info
            // If we have a show name, use it as title. If not, fallback to "BBC Radio"
            val finalTitle = if (showName.isNotEmpty()) showName else "BBC Radio"
            
            // Use RMS image if available, otherwise ESS image
            val finalImageUrl = imageUrl ?: scheduleShow.imageUrl
            
            return@withContext CurrentShow(
                title = finalTitle,
                secondary = artist,
                tertiary = track,
                imageUrl = finalImageUrl,
                startTime = scheduleShow.startTime,
                endTime = scheduleShow.endTime
            )
            
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching show info: ${e.message}", e)
            // Fallback to just ESS if RMS fails completely
             try {
                val serviceId = serviceIdMap[stationId]
                if (serviceId != null) {
                    return@withContext fetchShowFromEss(serviceId)
                }
            } catch (e2: Exception) {
                Log.w(TAG, "ESS fallback also failed: ${e2.message}")
            }
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
            val jsonObject = org.json.JSONObject(json)
            val items = jsonObject.optJSONArray("items") ?: return null
            
            val now = System.currentTimeMillis()
            // Handle ISO 8601 with potential millis and Z
            // Examples: "2026-01-04T18:00:53.092Z", "2026-01-04T18:00:00.000Z"
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val publishedTime = item.optJSONObject("published_time") ?: continue
                
                val startStr = publishedTime.optString("start")
                val endStr = publishedTime.optString("end")
                
                if (startStr.isNotEmpty() && endStr.isNotEmpty()) {
                    try {
                        val start = sdf.parse(startStr)?.time ?: continue
                        val end = sdf.parse(endStr)?.time ?: continue
                        
                        if (now in start until end) {
                            val brand = item.optJSONObject("brand")
                            val episode = item.optJSONObject("episode")
                            
                            val brandTitle = brand?.optString("title")
                            val episodeTitle = episode?.optString("title")
                            
                            val title = if (!brandTitle.isNullOrEmpty()) brandTitle else episodeTitle ?: "BBC Radio"
                            val subtitle = if (!brandTitle.isNullOrEmpty() && !episodeTitle.isNullOrEmpty()) episodeTitle else null
                            
                            Log.d(TAG, "Found current ESS show: $title ($subtitle)")
                            return CurrentShow(title = title, secondary = subtitle)
                        }
                    } catch (e: java.text.ParseException) {
                        Log.w(TAG, "Date parse error: ${e.message}")
                        continue
                    }
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
