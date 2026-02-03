package com.hyliankid14.bbcradioplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubscriptionRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val minutes = SubscriptionRefreshPreference.getIntervalMinutes(context)
        if (minutes <= 0) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val subscribedIds = PodcastSubscriptions.getSubscribedIds(context)
                if (subscribedIds.isEmpty()) return@launch

                val repo = PodcastRepository(context)
                val allPodcasts = try { repo.fetchPodcasts(forceRefresh = true) } catch (_: Exception) { emptyList() }
                val subscribed = allPodcasts.filter { subscribedIds.contains(it.id) }

                // For each subscribed podcast, fetch episodes and check for new ones
                for (podcast in subscribed) {
                    if (!PodcastSubscriptions.isNotificationsEnabled(context, podcast.id)) continue

                    val episodes = try { repo.fetchEpisodesIfNeeded(podcast) } catch (_: Exception) { emptyList() }
                    if (episodes.isEmpty()) continue

                    // Get the last known episode count from SharedPreferences
                    val prefs = context.getSharedPreferences("podcast_episode_counts", Context.MODE_PRIVATE)
                    val lastKnownCount = prefs.getInt(podcast.id, 0)

                    if (episodes.size > lastKnownCount) {
                        val newCount = episodes.size - lastKnownCount
                        PodcastEpisodeNotifier.notifyNewEpisodes(context, podcast, newCount)
                    }

                    // Update the episode count
                    prefs.edit().putInt(podcast.id, episodes.size).apply()
                }
            } catch (_: Exception) {
                // swallow - this is a best-effort background job
            }
        }
    }
}
