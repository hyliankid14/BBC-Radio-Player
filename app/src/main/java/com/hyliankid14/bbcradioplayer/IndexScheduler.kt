package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.Calendar

/**
 * Scheduler for periodic podcast indexing using WorkManager.
 * This ensures indexing can run in the background even when the app is closed.
 */
object IndexScheduler {
    private const val TAG = "IndexScheduler"
    private const val TARGET_HOUR_LOCAL = 5
    private const val TARGET_MINUTE_LOCAL = 0
    
    fun scheduleIndexing(context: Context) {
        val days = IndexPreference.getIntervalDays(context)
        if (days <= 0) {
            cancel(context)
            IndexPreference.setLastScheduledDays(context, 0)
            return
        }

        // Always use UPDATE policy to ensure constraints are applied correctly.
        // This is important when internal scheduling logic changes (e.g., constraint modifications).
        // WorkManager is smart about not re-running work that hasn't expired, so this is safe.
        val policy = ExistingPeriodicWorkPolicy.UPDATE

        // Use WorkManager for reliable background scheduling
        com.hyliankid14.bbcradioplayer.workers.BackgroundIndexWorker.schedulePeriodicIndexing(
            context,
            days,
            policy
        )

        maybeEnqueueCatchUpIndexing(context, days)

        IndexPreference.setLastScheduledDays(context, days)
    }

    private fun maybeEnqueueCatchUpIndexing(context: Context, intervalDays: Int) {
        val lastReindex = try {
            com.hyliankid14.bbcradioplayer.db.IndexStore.getInstance(context).getLastReindexTime()
        } catch (_: Exception) {
            null
        }

        // First-time users or users without a known timestamp do not need catch-up logic.
        val lastRunTime = lastReindex ?: return

        val nowMs = System.currentTimeMillis()
        val mostRecentTargetWindow = computeMostRecentTargetWindow(nowMs)

        // If daily indexing missed the 05:00 window, run as soon as the app starts.
        if (intervalDays == 1 && nowMs >= mostRecentTargetWindow && lastRunTime < mostRecentTargetWindow) {
            com.hyliankid14.bbcradioplayer.workers.BackgroundIndexWorker.enqueueIndexing(
                context,
                fullReindex = false
            )
            Log.d(TAG, "Enqueued catch-up indexing for missed 05:00 run")
            return
        }

        val intervalMillis = intervalDays * 24L * 60L * 60L * 1000L
        val elapsed = nowMs - lastRunTime
        if (elapsed >= intervalMillis) {
            com.hyliankid14.bbcradioplayer.workers.BackgroundIndexWorker.enqueueIndexing(
                context,
                fullReindex = false
            )
            Log.d(TAG, "Enqueued catch-up indexing (overdue by ${elapsed - intervalMillis} ms)")
        }
    }

    private fun computeMostRecentTargetWindow(nowMs: Long): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMs }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, TARGET_HOUR_LOCAL)
            set(Calendar.MINUTE, TARGET_MINUTE_LOCAL)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (after(now)) {
                add(Calendar.DAY_OF_YEAR, -1)
            }
        }
        return target.timeInMillis
    }

    fun cancel(context: Context) {
        // Cancel both periodic and one-time work
        com.hyliankid14.bbcradioplayer.workers.BackgroundIndexWorker.cancelAll(context)
    }
}

