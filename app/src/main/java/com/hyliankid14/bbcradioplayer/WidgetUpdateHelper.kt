package com.hyliankid14.bbcradioplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import java.util.concurrent.Executors

object WidgetUpdateHelper {
    private val worker = Executors.newSingleThreadExecutor()

    private val providers = listOf(
        ProviderSpec(StationWidgetSmallProvider::class.java, R.layout.widget_station_small)
    )

    fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        layoutResId: Int
    ) {
        val appContext = context.applicationContext
        appWidgetIds.forEach { id ->
            worker.execute { updateSingleWidget(appContext, appWidgetManager, id, layoutResId) }
        }
    }

    fun updateAllWidgets(context: Context) {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)

        providers.forEach { provider ->
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(appContext, provider.providerClass))
            ids.forEach { id ->
                worker.execute { updateSingleWidget(appContext, appWidgetManager, id, provider.layoutResId) }
            }
        }
    }

    private fun updateSingleWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        layoutResId: Int
    ) {
        val selectedStationId = WidgetPreference.getStationForWidget(context, appWidgetId)
            ?: PlaybackPreference.getLastStationId(context)
            ?: StationRepository.getStations().firstOrNull()?.id
            ?: return

        val station = StationRepository.getStationById(selectedStationId) ?: return
        val currentStation = PlaybackStateHelper.getCurrentStation()
        val isCurrentStation = currentStation?.id == station.id
        val isPlaying = PlaybackStateHelper.getIsPlaying() && isCurrentStation
        val currentShow = if (isCurrentStation) PlaybackStateHelper.getCurrentShow() else null

        val views = RemoteViews(context.packageName, layoutResId)

        views.setTextViewText(R.id.widget_station_name, station.title)
        views.setTextViewText(R.id.widget_now_playing, formatNowPlaying(context, currentShow, isCurrentStation))
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )

        views.setOnClickPendingIntent(
            R.id.widget_root,
            playStationIntent(context, appWidgetId, station.id)
        )
        views.setOnClickPendingIntent(
            R.id.widget_artwork,
            playStationIntent(context, appWidgetId + 1_000_000, station.id)
        )
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            if (isPlaying) pauseIntent(context, appWidgetId) else playIntent(context, appWidgetId, station.id, isCurrentStation)
        )
        views.setOnClickPendingIntent(
            R.id.widget_stop,
            stopIntent(context, appWidgetId)
        )

        appWidgetManager.updateAppWidget(appWidgetId, views)

        val artworkUrl = if (isCurrentStation && !currentShow?.imageUrl.isNullOrEmpty()) {
            currentShow?.imageUrl
        } else {
            station.logoUrl
        }

        if (!artworkUrl.isNullOrEmpty()) {
            try {
                val bitmap = Glide.with(context)
                    .asBitmap()
                    .load(artworkUrl)
                    .submit(320, 320)
                    .get()
                views.setImageViewBitmap(R.id.widget_artwork, bitmap)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (_: Exception) {
                views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_music_note)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun formatNowPlaying(context: Context, show: CurrentShow?, isCurrentStation: Boolean): String {
        if (!isCurrentStation || show == null) {
            return context.getString(R.string.widget_tap_to_play)
        }

        val hasSong = !show.secondary.isNullOrEmpty() || !show.tertiary.isNullOrEmpty()
        if (hasSong) {
            val artist = show.secondary.orEmpty()
            val track = show.tertiary.orEmpty()
            return if (artist.isNotBlank() && track.isNotBlank()) "$artist - $track" else show.getFormattedTitle()
        }

        val episode = show.episodeTitle.orEmpty().trim()
        if (episode.isNotEmpty()) {
            return episode
        }

        val title = show.title.trim()
        if (title.isNotEmpty() && !title.equals("BBC Radio", ignoreCase = true)) {
            return title
        }

        return context.getString(R.string.widget_live)
    }

    private fun playIntent(context: Context, requestCode: Int, stationId: String, isCurrentStation: Boolean): PendingIntent {
        val action = if (isCurrentStation) RadioService.ACTION_PLAY else RadioService.ACTION_PLAY_STATION
        val intent = Intent(context, RadioService::class.java).apply {
            this.action = action
            if (action == RadioService.ACTION_PLAY_STATION) {
                putExtra(RadioService.EXTRA_STATION_ID, stationId)
            }
        }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun playStationIntent(context: Context, requestCode: Int, stationId: String): PendingIntent {
        val intent = Intent(context, RadioService::class.java).apply {
            action = RadioService.ACTION_PLAY_STATION
            putExtra(RadioService.EXTRA_STATION_ID, stationId)
        }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pauseIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, RadioService::class.java).apply {
            action = RadioService.ACTION_PAUSE
        }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, RadioService::class.java).apply {
            action = RadioService.ACTION_STOP
        }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private data class ProviderSpec(
        val providerClass: Class<out BaseStationWidgetProvider>,
        val layoutResId: Int
    )
}
