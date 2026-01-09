package com.hyliankid14.bbcradioplayer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

object PodcastRepository {
    private const val TAG = "PodcastRepository"
    private const val OPML_URL = "https://www.bbc.co.uk/radio/opml/bbc_podcast_opml.xml"
    private val client = OkHttpClient()
    private var cachedPodcasts: List<Podcast>? = null
    
    suspend fun fetchPodcasts(): List<Podcast> = withContext(Dispatchers.IO) {
        cachedPodcasts?.let { return@withContext it }
        
        try {
            val request = Request.Builder()
                .url(OPML_URL)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch OPML: ${response.code}")
                return@withContext emptyList()
            }
            
            val xml = response.body?.string() ?: return@withContext emptyList()
            val podcasts = parseOPML(xml)
            cachedPodcasts = podcasts
            podcasts
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching podcasts", e)
            emptyList()
        }
    }
    
    private fun parseOPML(xml: String): List<Podcast> {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xml.toByteArray()))
            
            val podcasts = mutableListOf<Podcast>()
            val outlines = doc.getElementsByTagName("outline")
            
            for (i in 0 until outlines.length) {
                val node = outlines.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    val type = element.getAttribute("type")
                    
                    // Only parse RSS feeds (actual podcasts), not categories
                    if (type == "rss") {
                        val text = element.getAttribute("text")
                        val xmlUrl = element.getAttribute("xmlUrl")
                        val htmlUrl = element.getAttribute("htmlUrl")
                        val description = element.getAttribute("description")
                        
                        // Extract genre from text or description if available
                        val genre = extractGenre(text, description)
                        
                        if (text.isNotEmpty() && xmlUrl.isNotEmpty()) {
                            podcasts.add(
                                Podcast(
                                    id = xmlUrl.hashCode().toString(),
                                    title = text,
                                    description = description,
                                    xmlUrl = xmlUrl,
                                    htmlUrl = htmlUrl,
                                    genre = genre
                                )
                            )
                        }
                    }
                }
            }
            
            Log.d(TAG, "Parsed ${podcasts.size} podcasts from OPML")
            return podcasts
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OPML", e)
            return emptyList()
        }
    }
    
    private fun extractGenre(text: String, description: String): String? {
        // Common BBC podcast categories
        val categories = listOf(
            "News", "Sport", "Comedy", "Drama", "Documentary", "Music",
            "Science", "History", "Arts", "Culture", "Politics", "Business",
            "Technology", "Health", "Education", "Entertainment", "Kids"
        )
        
        val combinedText = "$text $description".lowercase()
        for (category in categories) {
            if (combinedText.contains(category.lowercase())) {
                return category
            }
        }
        return "Other"
    }
    
    suspend fun fetchEpisodes(podcast: Podcast): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(podcast.xmlUrl)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch RSS for ${podcast.title}: ${response.code}")
                return@withContext emptyList()
            }
            
            val xml = response.body?.string() ?: return@withContext emptyList()
            parseRSS(xml, podcast)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching episodes for ${podcast.title}", e)
            emptyList()
        }
    }
    
    private fun parseRSS(xml: String, podcast: Podcast): List<PodcastEpisode> {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xml.toByteArray()))
            
            val episodes = mutableListOf<PodcastEpisode>()
            val items = doc.getElementsByTagName("item")
            
            // Extract channel image URL if available
            var channelImageUrl: String? = null
            val channelNodes = doc.getElementsByTagName("channel")
            if (channelNodes.length > 0) {
                val channel = channelNodes.item(0) as Element
                val imageNodes = channel.getElementsByTagName("image")
                if (imageNodes.length > 0) {
                    val imageNode = imageNodes.item(0) as Element
                    val urlNodes = imageNode.getElementsByTagName("url")
                    if (urlNodes.length > 0) {
                        channelImageUrl = urlNodes.item(0).textContent
                    }
                }
                
                // Try iTunes image tag
                if (channelImageUrl == null) {
                    val itunesImageNodes = channel.getElementsByTagName("itunes:image")
                    if (itunesImageNodes.length > 0) {
                        val itunesImage = itunesImageNodes.item(0) as Element
                        channelImageUrl = itunesImage.getAttribute("href")
                    }
                }
            }
            
            for (i in 0 until items.length) {
                val item = items.item(i) as Element
                
                val title = getTagValue("title", item) ?: continue
                val description = getTagValue("description", item) ?: ""
                val pubDate = getTagValue("pubDate", item) ?: ""
                val guid = getTagValue("guid", item) ?: title.hashCode().toString()
                
                // Get enclosure (audio file)
                val enclosureNodes = item.getElementsByTagName("enclosure")
                val audioUrl = if (enclosureNodes.length > 0) {
                    (enclosureNodes.item(0) as Element).getAttribute("url")
                } else null
                
                if (audioUrl.isNullOrEmpty()) continue
                
                // Get duration (iTunes tag)
                val durationStr = getTagValue("itunes:duration", item)
                val duration = parseDuration(durationStr)
                
                // Get episode image (iTunes tag or use channel image)
                var episodeImageUrl = channelImageUrl
                val itunesImageNodes = item.getElementsByTagName("itunes:image")
                if (itunesImageNodes.length > 0) {
                    val href = (itunesImageNodes.item(0) as Element).getAttribute("href")
                    if (href.isNotEmpty()) {
                        episodeImageUrl = href
                    }
                }
                
                episodes.add(
                    PodcastEpisode(
                        id = guid,
                        title = title,
                        description = description,
                        audioUrl = audioUrl,
                        duration = duration,
                        pubDate = pubDate,
                        imageUrl = episodeImageUrl
                    )
                )
            }
            
            Log.d(TAG, "Parsed ${episodes.size} episodes for ${podcast.title}")
            return episodes
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing RSS for ${podcast.title}", e)
            return emptyList()
        }
    }
    
    private fun getTagValue(tag: String, element: Element): String? {
        val nodes = element.getElementsByTagName(tag)
        return if (nodes.length > 0) {
            nodes.item(0).textContent
        } else null
    }
    
    private fun parseDuration(durationStr: String?): Int {
        if (durationStr.isNullOrEmpty()) return 0
        
        return try {
            // Duration can be in HH:MM:SS, MM:SS, or just seconds
            val parts = durationStr.split(":")
            when (parts.size) {
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                1 -> durationStr.toInt()
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }
    
    fun getUniqueGenres(podcasts: List<Podcast>): List<String> {
        return podcasts.mapNotNull { it.genre }.distinct().sorted()
    }
    
    fun clearCache() {
        cachedPodcasts = null
    }
}
