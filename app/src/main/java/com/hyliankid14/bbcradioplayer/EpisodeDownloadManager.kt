package com.hyliankid14.bbcradioplayer

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Manages episode downloads using Android's DownloadManager.
 */
object EpisodeDownloadManager {
    private const val TAG = "EpisodeDownloadManager"
    
    // Broadcast action sent when a download completes or fails
    const val ACTION_DOWNLOAD_COMPLETE = "com.hyliankid14.bbcradioplayer.DOWNLOAD_COMPLETE"
    const val EXTRA_EPISODE_ID = "episode_id"
    const val EXTRA_SUCCESS = "success"

    private const val PREFS_NAME = "download_manager_prefs"
    private const val KEY_PREFIX_DOWNLOAD_ID = "download_id_"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun handleSystemDownloadComplete(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (downloadId == -1L) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val allPrefs = prefs.all
        var episodeId: String? = null

        for ((key, value) in allPrefs) {
            if (key.startsWith(KEY_PREFIX_DOWNLOAD_ID) && value == downloadId) {
                episodeId = key.removePrefix(KEY_PREFIX_DOWNLOAD_ID)
                break
            }
        }

        if (episodeId == null) {
            Log.w(TAG, "Download completed but episode ID not found")
            return
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusIndex)

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val localUri = if (uriIndex >= 0) cursor.getString(uriIndex) else null
                val sizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val reportedSize = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L

                val pendingKey = "pending_$episodeId"
                val pendingData = prefs.getString(pendingKey, null)

                if (pendingData != null) {
                    try {
                        val jsonStr = String(android.util.Base64.decode(pendingData, android.util.Base64.DEFAULT))
                        val json = org.json.JSONObject(jsonStr)
                        val episode = jsonToEpisode(json.getJSONObject("episode"))
                        val podcastTitle = json.optString("podcastTitle", "")
                        val autoDownload = json.optBoolean("autoDownload", false)
                        val pendingPath = json.optString("localPath", "")

                        // Prefer a concrete file path when available; fallback to DownloadManager URI ref.
                        val localRef = when {
                            pendingPath.isNotBlank() && File(pendingPath).exists() -> pendingPath
                            !localUri.isNullOrBlank() -> localUri
                            pendingPath.isNotBlank() -> pendingPath
                            else -> ""
                        }

                        if (localRef.isBlank()) {
                            throw IllegalStateException("Unable to resolve downloaded file location")
                        }

                        val fileSize = when {
                            reportedSize > 0 -> reportedSize
                            localRef.startsWith("file://") -> File(Uri.parse(localRef).path ?: "").takeIf { it.exists() }?.length() ?: 0L
                            localRef.startsWith("/") -> File(localRef).takeIf { it.exists() }?.length() ?: 0L
                            else -> 0L
                        }

                        DownloadedEpisodes.addDownloaded(context, episode, localRef, fileSize, podcastTitle, autoDownload)

                        if (autoDownload) {
                            val limit = DownloadPreferences.getAutoDownloadLimit(context).coerceAtLeast(1)
                            pruneDownloadsForPodcastToLimit(context, episode.podcastId, limit)
                        }

                        prefs.edit().remove(pendingKey).apply()
                        prefs.edit().remove(KEY_PREFIX_DOWNLOAD_ID + episodeId).apply()

                        val broadcastIntent = Intent(ACTION_DOWNLOAD_COMPLETE).apply {
                            putExtra(EXTRA_EPISODE_ID, episodeId)
                            putExtra(EXTRA_SUCCESS, true)
                        }
                        context.sendBroadcast(broadcastIntent)

                        Log.d(TAG, "Episode downloaded successfully: ${episode.title} to $localRef")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process completed download", e)
                    }
                }
            } else {
                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                Log.e(TAG, "Download failed for episode $episodeId, reason: $reason")

                val pendingKey = "pending_$episodeId"
                prefs.edit().remove(pendingKey).apply()
                prefs.edit().remove(KEY_PREFIX_DOWNLOAD_ID + episodeId).apply()

                val broadcastIntent = Intent(ACTION_DOWNLOAD_COMPLETE).apply {
                    putExtra(EXTRA_EPISODE_ID, episodeId)
                    putExtra(EXTRA_SUCCESS, false)
                }
                context.sendBroadcast(broadcastIntent)
            }
        }
        cursor.close()
    }

    /**
     * Start downloading an episode.
     * Returns true if download started successfully, false otherwise.
     */
    fun downloadEpisode(context: Context, episode: Episode, podcastTitle: String?, isAutoDownload: Boolean = false): Boolean {
        if (episode.audioUrl.isBlank()) {
            Toast.makeText(context, "Episode audio URL not available", Toast.LENGTH_SHORT).show()
            return false
        }

        // Check if already downloaded
        if (DownloadedEpisodes.isDownloaded(context, episode)) {
            Toast.makeText(context, "Episode already downloaded", Toast.LENGTH_SHORT).show()
            return false
        }

        // Check WiFi requirement
        if (DownloadPreferences.isDownloadOnWifiOnly(context) && !isWifiConnected(context)) {
            Toast.makeText(context, "Download requires WiFi connection", Toast.LENGTH_SHORT).show()
            return false
        }

        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            // Create download directory if it doesn't exist
            val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "episodes")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            // Sanitize filename
            val filename = sanitizeFilename(episode.title) + "_" + episode.id + ".mp3"
            val destinationUri = Uri.fromFile(File(downloadDir, filename))

            val request = DownloadManager.Request(Uri.parse(episode.audioUrl)).apply {
                setTitle("${podcastTitle ?: "Podcast"}: ${episode.title}")
                setDescription("Downloading episode")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(destinationUri)
                
                // Add headers
                addRequestHeader("User-Agent", "BBC Radio Player/1.0 (Android)")
                
                // Allow downloads on metered networks if WiFi-only is disabled
                if (!DownloadPreferences.isDownloadOnWifiOnly(context)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setRequiresCharging(false)
                        setRequiresDeviceIdle(false)
                    }
                }
            }

            val downloadId = downloadManager.enqueue(request)
            
            // Store download ID mapped to episode ID
            prefs(context).edit().putLong(KEY_PREFIX_DOWNLOAD_ID + episode.id, downloadId).apply()
            
            // Store episode info for later retrieval
            val pendingKey = "pending_" + episode.id
            val pendingData = android.util.Base64.encodeToString(
                org.json.JSONObject().apply {
                    put("episode", episodeToJson(episode))
                    put("podcastTitle", podcastTitle ?: "")
                    put("localPath", destinationUri.path ?: "")
                    put("autoDownload", isAutoDownload)
                }.toString().toByteArray(),
                android.util.Base64.DEFAULT
            )
            prefs(context).edit().putString(pendingKey, pendingData).apply()

            Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start download", e)
            Toast.makeText(context, "Failed to start download: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    /**
     * Delete a downloaded episode file and remove from downloaded list.
     */
    fun deleteDownload(context: Context, episodeId: String, showToast: Boolean = true): Boolean {
        val entry = DownloadedEpisodes.removeDownloaded(context, episodeId)
        if (entry != null) {
            try {
                val localRef = entry.localFilePath
                if (localRef.startsWith("content://") || localRef.startsWith("file://")) {
                    try {
                        val uri = Uri.parse(localRef)
                        context.contentResolver.delete(uri, null, null)
                    } catch (_: Exception) { }
                } else {
                    val file = File(localRef)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                if (showToast) Toast.makeText(context, "Download deleted", Toast.LENGTH_SHORT).show()
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete download", e)
                if (showToast) Toast.makeText(context, "Failed to delete download", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    /**
     * Delete all downloaded episodes.
     */
    fun deleteAllDownloads(context: Context) {
        val entries = DownloadedEpisodes.getDownloadedEntries(context)
        var deletedCount = 0
        
        for (entry in entries) {
            try {
                val file = File(entry.localFilePath)
                if (file.exists() && file.delete()) {
                    deletedCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete file: ${entry.localFilePath}", e)
            }
        }
        
        DownloadedEpisodes.removeAll(context)
        Toast.makeText(context, "Deleted $deletedCount episode(s)", Toast.LENGTH_SHORT).show()
    }

    fun pruneDownloadsForPodcastToLimit(context: Context, podcastId: String, limit: Int) {
        if (podcastId.isBlank()) return
        val max = limit.coerceAtLeast(1)
        val entries = DownloadedEpisodes.getDownloadedEpisodesForPodcast(context, podcastId)
        val autoEntries = entries.filter { it.isAutoDownloaded }
        if (autoEntries.size <= max) return
        val sorted = autoEntries.sortedWith(
            compareByDescending<DownloadedEpisodes.Entry> { EpisodeDateParser.parsePubDateToEpoch(it.pubDate) }
                .thenByDescending { it.downloadedAtMs }
        )
        val keepIds = sorted.take(max).map { it.id }.toSet()
        for (entry in autoEntries) {
            if (!keepIds.contains(entry.id)) {
                deleteDownload(context, entry.id, showToast = false)
            }
        }
    }

    /**
     * Check if device is connected to WiFi.
     */
    @Suppress("DEPRECATION")
    private fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true && networkInfo.type == ConnectivityManager.TYPE_WIFI
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
    }

    private fun episodeToJson(episode: Episode): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("id", episode.id)
            put("title", episode.title)
            put("description", episode.description)
            put("audioUrl", episode.audioUrl)
            put("imageUrl", episode.imageUrl)
            put("pubDate", episode.pubDate)
            put("durationMins", episode.durationMins)
            put("podcastId", episode.podcastId)
        }
    }

    private fun jsonToEpisode(json: org.json.JSONObject): Episode {
        return Episode(
            id = json.getString("id"),
            title = json.optString("title", ""),
            description = json.optString("description", ""),
            audioUrl = json.optString("audioUrl", ""),
            imageUrl = json.optString("imageUrl", ""),
            pubDate = json.optString("pubDate", ""),
            durationMins = json.optInt("durationMins", 0),
            podcastId = json.optString("podcastId", "")
        )
    }

    /**
     * BroadcastReceiver to handle download completion.
     * Register this in your Activity/Service to track download completion.
     */
    class DownloadCompleteReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handleSystemDownloadComplete(context, intent)
        }
    }
}
