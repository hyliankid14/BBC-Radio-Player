package com.example.androidautoradioplayer

import android.content.Context

object FavoritesPreference {
    private const val PREFS_NAME = "favorites_prefs"
    private const val KEY_FAVORITES = "favorite_stations"

    fun isFavorite(context: Context, stationId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val favorites = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        return favorites.contains(stationId)
    }

    fun toggleFavorite(context: Context, stationId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val favorites = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toMutableSet() ?: mutableSetOf()
        
        if (favorites.contains(stationId)) {
            favorites.remove(stationId)
        } else {
            favorites.add(stationId)
        }
        
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
    }

    fun getFavoriteIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun getFavorites(context: Context): List<Station> {
        val favoriteIds = getFavoriteIds(context)
        return StationRepository.getStations().filter { favoriteIds.contains(it.id) }
    }
}
