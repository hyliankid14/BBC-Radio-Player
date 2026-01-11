package com.hyliankid14.bbcradioplayer

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.URL
import java.util.Locale

object OPMLParser {
    private const val TAG = "OPMLParser"
    private const val OUTLINE = "outline"
    private const val OPML = "opml"

    fun parseOPML(inputStream: InputStream): List<Podcast> {
        val podcasts = mutableListOf<Podcast>()
        val seenIds = mutableSetOf<String>()
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, null)
            var eventType = parser.eventType

            var parsedCount = 0
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == OUTLINE) {
                    val podcast = parsePodcastOutline(parser)
                    if (podcast != null && seenIds.add(podcast.id)) {
                        podcasts.add(podcast)
                        parsedCount++
                    }
                }
                eventType = parser.next()
            }
            Log.d(TAG, "Parsed $parsedCount unique podcasts from OPML")
            if (podcasts.isEmpty()) {
                Log.w(TAG, "Parsed OPML but found zero podcasts; check feed structure or filters")
            }
            podcasts
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OPML", e)
            emptyList()
        }
    }

    fun fetchAndParseOPML(url: String): List<Podcast> {
        return try {
            var redirectUrl = url
            var redirects = 0
            while (redirects < 5) {
                val currentUrl = URL(redirectUrl)
                val connection = (currentUrl.openConnection() as java.net.HttpURLConnection).apply {
                    instanceFollowRedirects = false // handle manually to keep headers
                    connectTimeout = 15000
                    readTimeout = 15000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "BBC Radio Player/1.0 (Android)")
                    setRequestProperty("Accept", "application/xml,text/xml,application/rss+xml,*/*")
                    setRequestProperty("Accept-Encoding", "gzip")
                    doInput = true
                }

                val code = connection.responseCode
                if (code == java.net.HttpURLConnection.HTTP_MOVED_PERM ||
                    code == java.net.HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == java.net.HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308
                ) {
                    val location = connection.getHeaderField("Location") ?: break
                    redirectUrl = URL(currentUrl, location).toString()
                    // Some BBC endpoints redirect to http; prefer https when available
                    if (redirectUrl.startsWith("http://podcasts.files.bbci.co.uk")) {
                        redirectUrl = redirectUrl.replaceFirst("http://", "https://")
                    }
                    redirects++
                    continue
                }

                if (code != java.net.HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "HTTP $code while fetching OPML")
                    return emptyList()
                }

                val stream = if ("gzip".equals(connection.getHeaderField("Content-Encoding"), true)) {
                    java.util.zip.GZIPInputStream(connection.inputStream)
                } else {
                    connection.inputStream
                }

                stream.use { return parseOPML(it) }
            }

            Log.e(TAG, "Too many redirects while fetching OPML")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching OPML from $url", e)
            emptyList()
        }
    }

    private fun parsePodcastOutline(parser: XmlPullParser): Podcast? {
        val type = parser.getAttributeValue(null, "type") ?: ""
        val text = parser.getAttributeValue(null, "text") ?: ""
        val description = parser.getAttributeValue(null, "description") ?: ""
        val xmlUrl = parser.getAttributeValue(null, "xmlUrl") ?: ""
        val htmlUrl = parser.getAttributeValue(null, "htmlUrl") ?: ""
        val imageUrl = parser.getAttributeValue(null, "imageHref") ?: ""
        val keyName = parser.getAttributeValue(null, "keyname") ?: text
        val durationStr = parser.getAttributeValue(null, "typicalDurationMins") ?: "0"
        val genresStr = parser.getAttributeValue(null, "bbcgenres") ?: ""

        val duration = durationStr.toIntOrNull() ?: 0
        val genres = genresStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val isRssLike = type.isEmpty() || type.lowercase(Locale.US) == "rss"
        return if (xmlUrl.isNotEmpty() && isRssLike) {
            Podcast(
                id = keyName.hashCode().toString(),
                title = text,
                description = description,
                rssUrl = xmlUrl,
                htmlUrl = htmlUrl,
                imageUrl = imageUrl,
                genres = genres,
                typicalDurationMins = duration
            )
        } else {
            null
        }
    }
}

object RSSParser {
    private const val TAG = "RSSParser"
    private const val ITEM = "item"
    private const val TITLE = "title"
    private const val DESCRIPTION = "description"
    private const val ENCLOSURE = "enclosure"
    private const val PUB_DATE = "pubDate"
    private const val DURATION = "duration"

    fun parseRSS(inputStream: InputStream, podcastId: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, null)
            var eventType = parser.eventType
            var currentTitle = ""
            var currentDescription = ""
            var currentAudioUrl = ""
            var currentPubDate = ""
            var currentDuration = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            ITEM -> {
                                currentTitle = ""
                                currentDescription = ""
                                currentAudioUrl = ""
                                currentPubDate = ""
                                currentDuration = 0
                            }
                            TITLE -> {
                                if (parser.next() == XmlPullParser.TEXT) {
                                    currentTitle = parser.text
                                }
                            }
                            DESCRIPTION -> {
                                if (parser.next() == XmlPullParser.TEXT) {
                                    currentDescription = parser.text.take(200)
                                }
                            }
                            ENCLOSURE -> {
                                currentAudioUrl = parser.getAttributeValue(null, "url") ?: ""
                            }
                            PUB_DATE -> {
                                if (parser.next() == XmlPullParser.TEXT) {
                                    currentPubDate = parser.text
                                }
                            }
                            DURATION -> {
                                if (parser.next() == XmlPullParser.TEXT) {
                                    currentDuration = parseDuration(parser.text)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == ITEM && currentAudioUrl.isNotEmpty()) {
                            val episode = Episode(
                                id = currentAudioUrl.hashCode().toString(),
                                title = currentTitle,
                                description = currentDescription,
                                audioUrl = currentAudioUrl,
                                imageUrl = "",
                                pubDate = currentPubDate,
                                durationMins = currentDuration,
                                podcastId = podcastId
                            )
                            episodes.add(episode)
                        }
                    }
                }
                eventType = parser.next()
            }
            episodes
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing RSS", e)
            emptyList()
        }
    }

    fun fetchAndParseRSS(url: String, podcastId: String): List<Episode> {
        return try {
            val connection = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "BBC Radio Player/1.0 (Android)")
            }
            
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.w(TAG, "HTTP $responseCode while fetching RSS from $url")
                connection.disconnect()
                return emptyList()
            }
            
            connection.inputStream.use { parseRSS(it, podcastId) }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching RSS from $url", e)
            emptyList()
        }
    }

    fun fetchLatestPubDateEpoch(url: String): Long? {
        return try {
            val connection = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "BBC Radio Player/1.0 (Android)")
            }
            val code = connection.responseCode
            if (code != java.net.HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return null
            }
            val epoch = connection.inputStream.use { parseLatestPubDate(it) }
            connection.disconnect()
            epoch
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching latest pubDate from $url", e)
            null
        }
    }

    private fun parseLatestPubDate(inputStream: InputStream): Long? {
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, null)
            var eventType = parser.eventType
            var inItem = false
            var pubDate: String? = null
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            ITEM -> inItem = true
                            PUB_DATE -> if (inItem && parser.next() == XmlPullParser.TEXT) {
                                pubDate = parser.text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == ITEM) {
                            // We've reached end of first item; return
                            return pubDate?.let { parseRfc2822Date(it) }
                        }
                    }
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing latest pubDate", e)
            null
        }
    }

    private fun parseRfc2822Date(s: String): Long? {
        return try {
            val fmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US)
            fmt.parse(s)?.time
        } catch (_: Exception) { null }
    }

    fun parseDuration(durationStr: String): Int {
        return try {
            when {
                durationStr.toIntOrNull() != null -> durationStr.toInt() / 60
                durationStr.contains(":") -> {
                    val parts = durationStr.split(":")
                    when (parts.size) {
                        2 -> parts[0].toInt() // MM:SS -> minutes
                        3 -> parts[0].toInt() * 60 + parts[1].toInt() // HH:MM:SS
                        else -> 0
                    }
                }
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }
}
