package com.hyliankid14.bbcradioplayer

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

abstract class BaseStationWidgetProvider : AppWidgetProvider() {
    abstract val layoutResId: Int

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdateHelper.updateWidgets(context, appWidgetManager, appWidgetIds, layoutResId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> WidgetPreference.deleteWidget(context, id) }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateHelper.updateAllWidgets(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdateHelper.updateAllWidgets(context)
    }
}

class StationWidgetSmallProvider : BaseStationWidgetProvider() {
    override val layoutResId: Int = R.layout.widget_station_small
}

class StationWidgetMediumProvider : BaseStationWidgetProvider() {
    override val layoutResId: Int = R.layout.widget_station_medium
}

class StationWidgetMediumSquareProvider : BaseStationWidgetProvider() {
    override val layoutResId: Int = R.layout.widget_station_medium_square
}

class StationWidgetLargeProvider : BaseStationWidgetProvider() {
    override val layoutResId: Int = R.layout.widget_station_large
}

class StationWidgetXLargeProvider : BaseStationWidgetProvider() {
    override val layoutResId: Int = R.layout.widget_station_xlarge
}
