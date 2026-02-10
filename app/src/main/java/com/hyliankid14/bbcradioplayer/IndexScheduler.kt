package com.hyliankid14.bbcradioplayer

import android.content.Context

/**
 * Scheduler for periodic podcast indexing using WorkManager.
 * This ensures indexing can run in the background even when the app is closed.
 */
object IndexScheduler {
    
    fun scheduleIndexing(context: Context) {
        val days = IndexPreference.getIntervalDays(context)
        if (days <= 0) {
            cancel(context)
            return
        }

        // Use WorkManager for reliable background scheduling
        com.hyliankid14.bbcradioplayer.workers.BackgroundIndexWorker.schedulePeriodicIndexing(
            context,
            days
        )
    }

    fun cancel(context: Context) {
        com.hyliankid14.bbcradioplayer.workers.BackgroundIndexWorker.cancelPeriodicIndexing(context)
    }
}

