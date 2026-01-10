package com.hyliankid14.bbcradioplayer

import android.content.Context

object PodcastSubscriptions {
    private const val PREFS_NAME = "podcast_subscriptions"
    private const val KEY_SUBSCRIBED_IDS = "subscribed_ids"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSubscribedIds(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_SUBSCRIBED_IDS, emptySet()) ?: emptySet()
    }

    fun isSubscribed(context: Context, podcastId: String): Boolean {
        return getSubscribedIds(context).contains(podcastId)
    }

    fun toggleSubscription(context: Context, podcastId: String) {
        val current = getSubscribedIds(context).toMutableSet()
        if (current.contains(podcastId)) current.remove(podcastId) else current.add(podcastId)
        prefs(context).edit().putStringSet(KEY_SUBSCRIBED_IDS, current).apply()
    }
}
