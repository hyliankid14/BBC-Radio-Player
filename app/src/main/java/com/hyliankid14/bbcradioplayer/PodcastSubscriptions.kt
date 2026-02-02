package com.hyliankid14.bbcradioplayer

import android.content.Context

object PodcastSubscriptions {
    private const val PREFS_NAME = "podcast_subscriptions"
    private const val KEY_SUBSCRIBED_IDS = "subscribed_ids"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSubscribedIds(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_SUBSCRIBED_IDS, emptySet()) ?: emptySet()
    }

    fun isSubscribed(context: Context, podcastId: String): Boolean {
        return getSubscribedIds(context).contains(podcastId)
    }

    fun toggleSubscription(context: Context, podcastId: String) {
        val current = getSubscribedIds(context).toMutableSet()
        if (current.contains(podcastId)) {
            current.remove(podcastId)
            // Also remove notification preference when unsubscribing
            setNotificationsEnabled(context, podcastId, false)
        } else {
            current.add(podcastId)
            // Enable notifications by default when subscribing
            setNotificationsEnabled(context, podcastId, true)
        }
        prefs(context).edit().putStringSet(KEY_SUBSCRIBED_IDS, current).apply()
    }

    fun isNotificationsEnabled(context: Context, podcastId: String): Boolean {
        // Only return true if the podcast is subscribed AND notifications are enabled
        if (!isSubscribed(context, podcastId)) return false
        val enabledIds = prefs(context).getStringSet(KEY_NOTIFICATIONS_ENABLED, emptySet()) ?: emptySet()
        return enabledIds.contains(podcastId)
    }

    fun setNotificationsEnabled(context: Context, podcastId: String, enabled: Boolean) {
        val current = prefs(context).getStringSet(KEY_NOTIFICATIONS_ENABLED, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (enabled) {
            current.add(podcastId)
        } else {
            current.remove(podcastId)
        }
        prefs(context).edit().putStringSet(KEY_NOTIFICATIONS_ENABLED, current).apply()
    }

    fun toggleNotifications(context: Context, podcastId: String) {
        val current = isNotificationsEnabled(context, podcastId)
        setNotificationsEnabled(context, podcastId, !current)
    }
}
