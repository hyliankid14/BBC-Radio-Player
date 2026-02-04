package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Utility for sharing podcasts and episodes with proper fallback support.
 * 
 * Sharing strategy:
 * 1. Generate deep links for app users (app://podcast/{id} or app://episode/{id})
 * 2. Generate web fallback URLs (https://bbcradioplayer.app/p/{id})
 * 3. Use Android's share sheet with rich text and metadata
 */
object ShareUtil {

    // GitHub Pages URL for web player
    private const val WEB_BASE_URL = "https://hyliankid14.github.io/BBC-Radio-Player"
    private const val APP_SCHEME = "app"
    
    /**
     * Share a podcast with others.
     * Non-app users will be directed to the web player.
     */
    fun sharePodcast(context: Context, podcast: Podcast) {
        val encodedTitle = Uri.encode(podcast.title)
        val encodedDesc = Uri.encode(podcast.description.take(200))
        val encodedImage = Uri.encode(podcast.imageUrl)
        val webUrl = "$WEB_BASE_URL/#/p/${podcast.id}?title=$encodedTitle&desc=$encodedDesc&img=$encodedImage"
        val deepLink = "$APP_SCHEME://podcast/${podcast.id}"
        
        val shareTitle = podcast.title
        val shareMessage = buildString {
            append("Check out \"${podcast.title}\"")
            if (podcast.description.isNotEmpty()) {
                append(" - ${podcast.description.take(100)}")
                if (podcast.description.length > 100) append("...")
            }
            append("\n\n")
            append(webUrl)
            append("\n\nIf you have the BBC Radio Player app installed, you can open it directly.")
        }
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, shareTitle)
            putExtra(Intent.EXTRA_TEXT, shareMessage)
            type = "text/plain"
        }
        
        context.startActivity(Intent.createChooser(shareIntent, "Share podcast"))
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
        val webUrl = "$WEB_BASE_URL/#/e/${episode.id}?title=$encodedTitle&desc=$encodedDesc&img=$encodedImage&podcast=$encodedPodcast&audio=$encodedAudio&date=${episode.pubDate}&duration=${episode.durationMins}"
        val deepLink = "$APP_SCHEME://episode/${episode.id}"
        
        val shareTitle = episode.title
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
            append(webUrl)
            append("\n\nIf you have the BBC Radio Player app installed, you can open it directly.")
        }
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, shareTitle)
            putExtra(Intent.EXTRA_TEXT, shareMessage)
            type = "text/plain"
        }
        
        context.startActivity(Intent.createChooser(shareIntent, "Share episode"))
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
    
    enum class ShareContentType {
        PODCAST,
        EPISODE
    }
}
