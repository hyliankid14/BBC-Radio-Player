package com.example.androidautoradioplayer

import android.content.Context

object FavoritesPreference {
    private const val PREFS_NAME = "favorites_prefs"
    private const val KEY_FAVORITES = "favorite_stations"
    private const val KEY_FAVORITES_ORDER = "favorite_stations_order"

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

    fun saveFavoritesOrder(context: Context, orderedIds: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_FAVORITES_ORDER, orderedIds.toSet()).apply()
    }

    fun getFavoritesOrder(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_FAVORITES_ORDER, emptySet())?.toList() ?: emptyList()
    }

    fun getFavorites(context: Context): List<Station> {
        val favoriteIds = getFavoriteIds(context)
        val allStations = StationRepository.getStations().filter { favoriteIds.contains(it.id) }
        
        // Try to maintain saved order
        val savedOrder = getFavoritesOrder(context)
        if (savedOrder.isNotEmpty()) {
            return savedOrder.mapNotNull { id -> allStations.find { it.id == id } }
        }
        
        return allStations
    }
}
