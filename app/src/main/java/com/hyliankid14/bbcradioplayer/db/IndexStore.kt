package com.hyliankid14.bbcradioplayer.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import com.hyliankid14.bbcradioplayer.Episode
import com.hyliankid14.bbcradioplayer.Podcast
import java.util.*

/**
 * Lightweight SQLite FTS-backed index for podcasts and episodes.
 * Implemented without Room to avoid KAPT/annotation toolchain dependencies.
 */
class IndexStore private constructor(private val context: Context) {
    private val helper = IndexDatabaseHelper(context.applicationContext)

    companion object {
        @Volatile
        private var INSTANCE: IndexStore? = null

        fun getInstance(context: Context): IndexStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IndexStore(context).also { INSTANCE = it }
            }
        }

        private fun normalizeQueryForFts(query: String): String {
            // Normalize: strip punctuation, collapse whitespace, lowercase.
            val q = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .replace(Regex("[^\\p{L}0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .lowercase(Locale.getDefault())

            if (q.isEmpty()) return q
            val tokens = q.split(Regex("\\s+"))
            // Use prefix matching and AND semantics to approximate containsPhraseOrAllTokens
            return tokens.joinToString(" AND ") { "${it}*" }
        }
    }

    fun replaceAllPodcasts(podcasts: List<Podcast>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM podcast_fts;")
            val stmt: SQLiteStatement = db.compileStatement("INSERT INTO podcast_fts(podcastId, title, description) VALUES (?, ?, ?);")
            for (p in podcasts) {
                stmt.clearBindings()
                stmt.bindString(1, p.id)
                stmt.bindString(2, p.title ?: "")
                stmt.bindString(3, p.description ?: "")
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun replaceAllEpisodes(episodes: List<Episode>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM episode_fts;")
            val stmt: SQLiteStatement = db.compileStatement("INSERT INTO episode_fts(episodeId, podcastId, title, description) VALUES (?, ?, ?, ?);")
            for (e in episodes) {
                stmt.clearBindings()
                stmt.bindString(1, e.id)
                stmt.bindString(2, e.podcastId)
                stmt.bindString(3, e.title ?: "")
                stmt.bindString(4, e.description ?: "")
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun searchPodcasts(query: String, limit: Int = 50): List<PodcastFts> {
        if (query.isBlank()) return emptyList()
        val db = helper.readableDatabase
        val match = normalizeQueryForFts(query)
        if (match.isBlank()) return emptyList()
        val cursor = db.rawQuery("SELECT podcastId, title, description FROM podcast_fts WHERE podcast_fts MATCH ? LIMIT ?", arrayOf(match, limit.toString()))
        val results = mutableListOf<PodcastFts>()
        cursor.use {
            while (it.moveToNext()) {
                val pid = it.getString(0)
                val title = it.getString(1) ?: ""
                val desc = it.getString(2) ?: ""
                results.add(PodcastFts(pid, title, desc))
            }
        }
        return results
    }

    fun searchEpisodes(query: String, limit: Int = 100): List<EpisodeFts> {
        if (query.isBlank()) return emptyList()
        val db = helper.readableDatabase
        val match = normalizeQueryForFts(query)
        if (match.isBlank()) return emptyList()
        val cursor = db.rawQuery("SELECT episodeId, podcastId, title, description FROM episode_fts WHERE episode_fts MATCH ? LIMIT ?", arrayOf(match, limit.toString()))
        val results = mutableListOf<EpisodeFts>()
        cursor.use {
            while (it.moveToNext()) {
                val eid = it.getString(0)
                val pid = it.getString(1)
                val title = it.getString(2) ?: ""
                val desc = it.getString(3) ?: ""
                results.add(EpisodeFts(eid, pid, title, desc))
            }
        }
        return results
    }
}
