package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.net.URL
import java.net.URLEncoder

/**
 * Utility for sharing podcasts and episodes with proper fallback support.
 * 
 * Sharing strategy:
 * 1. Generate deep links for app users (app://podcast/{id} or app://episode/{id})
 * 2. Generate web fallback URLs with short codes
 * 3. Use Android's share sheet with rich text and metadata
 */
object ShareUtil {

    // GitHub Pages URL for web player
    private const val WEB_BASE_URL = "https://hyliankid14.github.io/BBC-Radio-Player"
    private const val APP_SCHEME = "app"
    private const val SHORT_URL_API = "https://is.gd/create.php"
    
    /**
     * Share a podcast with others.
     * Non-app users will be directed to the web player.
     */
    fun sharePodcast(context: Context, podcast: Podcast) {
        val encodedTitle = Uri.encode(podcast.title)
        val encodedDesc = Uri.encode(podcast.description.take(200))
        val encodedImage = Uri.encode(podcast.imageUrl)
        val encodedRss = Uri.encode(podcast.rssUrl)
        val webUrl = "$WEB_BASE_URL/#/p/${podcast.id}?title=$encodedTitle&desc=$encodedDesc&img=$encodedImage&rss=$encodedRss"
        
        val shareTitle = podcast.title
        val handler = Handler(Looper.getMainLooper())
        
        // Shorten URL on background thread
        Thread {
            try {
                val shortUrl = shortenUrl(webUrl)
                val shareMessage = buildString {
                    append("Check out \"${podcast.title}\"")
                    if (podcast.description.isNotEmpty()) {
                        append(" - ${podcast.description.take(100)}")
                        if (podcast.description.length > 100) append("...")
                    }
                    append("\n\n")
                    append(shortUrl)
                    append("\n\nIf you have the BBC Radio Player app installed, you can open it directly.")
                }
                
                // Post back to main thread to start activity
                handler.post {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                        putExtra(Intent.EXTRA_TEXT, shareMessage)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share podcast"))
                }
            } catch (e: Exception) {
                android.util.Log.w("ShareUtil", "Failed to share podcast: ${e.message}")
            }
        }.start()
    }
    
    /**
     * Share a specific episode with others.
     * Non-app users will be directed to the web player showing this episode.
     */
    fun shareEpisode(context: Context, episode: Episode, podcastTitle: String) {
        val encodedTitle = Uri.encode(episode.title)
        val encodedDesc = Uri.encode(episode.description.take(200))
        val encodedImage = Uri.encode(episode.imageUrl)
        val encodedPodcast = Uri.encode(podcastTitle)
        val encodedAudio = Uri.encode(episode.audioUrl)
        val encodedDate = Uri.encode(episode.pubDate.toString())
        val encodedDuration = Uri.encode(episode.durationMins.toString())
        val webUrl = "$WEB_BASE_URL/#/e/${episode.id}?title=$encodedTitle&desc=$encodedDesc&img=$encodedImage&podcast=$encodedPodcast&audio=$encodedAudio&date=$encodedDate&duration=$encodedDuration"
        
        val shareTitle = episode.title
        val handler = Handler(Looper.getMainLooper())
        
        // Shorten URL on background thread
        Thread {
            try {
                val shortUrl = shortenUrl(webUrl)
                val shareMessage = buildString {
                    append("Listen to \"${episode.title}\"")
                    if (podcastTitle.isNotEmpty()) {
                        append(" from $podcastTitle")
                    }
                    if (episode.description.isNotEmpty()) {
                        append(" - ${episode.description.take(100)}")
                        if (episode.description.length > 100) append("...")
                    }
                    append("\n\n")
                    append(shortUrl)
                    append("\n\nIf you have the BBC Radio Player app installed, you can open it directly.")
                }
                
                // Post back to main thread to start activity
                handler.post {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                        putExtra(Intent.EXTRA_TEXT, shareMessage)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share episode"))
                }
            } catch (e: Exception) {
                android.util.Log.w("ShareUtil", "Failed to share episode: ${e.message}")
            }
        }.start()
    }
    
    /**
     * Generate a podcast share URL (for use in custom sharing scenarios)
     */
    fun getPodcastShareUrl(podcastId: String): String {
        return "$WEB_BASE_URL/#/p/$podcastId"
    }
    
    /**
     * Generate an episode share URL (for use in custom sharing scenarios)
     */
    fun getEpisodeShareUrl(episodeId: String): String {
        return "$WEB_BASE_URL/#/e/$episodeId"
    }
    
    /**
     * Handle incoming deep links from share URLs.
     * Call this from MainActivity's onCreate when processing Intent data.
     * 
     * Returns the content type and ID, or null if not a share link.
     */
    fun parseShareLink(intent: Intent): Pair<ShareContentType, String>? {
        val uri = intent.data ?: return null
        
        return when {
            uri.scheme == APP_SCHEME && uri.host == "podcast" -> {
                val podcastId = uri.pathSegments.getOrNull(0) ?: return null
                ShareContentType.PODCAST to podcastId
            }
            uri.scheme == APP_SCHEME && uri.host == "episode" -> {
                val episodeId = uri.pathSegments.getOrNull(0) ?: return null
                ShareContentType.EPISODE to episodeId
            }
            uri.scheme == "https" && uri.host == "hyliankid14.github.io" -> {
                // GitHub Pages URL format: /BBC-Radio-Player/p/{id} or /BBC-Radio-Player/e/{id}
                val segments = uri.pathSegments
                if (segments.getOrNull(0) == "BBC-Radio-Player") {
                    when (segments.getOrNull(1)) {
                        "p" -> {
                            val podcastId = segments.getOrNull(2) ?: return null
                            ShareContentType.PODCAST to podcastId
                        }
                        "e" -> {
                            val episodeId = segments.getOrNull(2) ?: return null
                            ShareContentType.EPISODE to episodeId
                        }
                        else -> null
                    }
                } else null
            }
            else -> null
        }
    }
    
    /**
     * Shorten a URL using is.gd service
     */
    private fun shortenUrl(longUrl: String): String {
        return try {
            android.util.Log.d("ShareUtil", "Shortening URL (length: ${longUrl.length}): ${longUrl.take(200)}...")
            val encodedUrl = URLEncoder.encode(longUrl, "UTF-8")
            val urlStr = "$SHORT_URL_API?format=json&url=$encodedUrl"
            val connection = (URL(urlStr).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "BBC Radio Player/1.0")
            }
            
            val responseCode = connection.responseCode
            val response = if (responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                // For errors, read from error stream
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            
            if (responseCode == 200 && response.contains("shorturl")) {
                // Parse JSON response for shorturl field
                // Example: { "shorturl": "https://is.gd/abc123" }
                val shortUrl = response.substringAfter("\"shorturl\"")
                    .substringAfter("\"")
                    .substringBefore("\"")
                    .replace("\\/", "/")
                    .trim()
                
                // Validate it's actually a URL
                if (shortUrl.startsWith("http://") || shortUrl.startsWith("https://")) {
                    android.util.Log.d("ShareUtil", "Successfully shortened to: $shortUrl")
                    return shortUrl
                }
            }
            
            // Log any errors for debugging
            if (response.contains("errorcode")) {
                val errorMsg = response.substringAfter("\"errormessage\"")
                    .substringAfter("\"")
                    .substringBefore("\"")
                android.util.Log.w("ShareUtil", "is.gd error: $errorMsg")
            } else {
                android.util.Log.w("ShareUtil", "is.gd returned status $responseCode: $response")
            }
            
            longUrl
        } catch (e: Exception) {
            android.util.Log.w("ShareUtil", "Failed to shorten URL: ${e.message}")
            longUrl
        }
    }
    
    enum class ShareContentType {
        PODCAST,
        EPISODE
    }
}
