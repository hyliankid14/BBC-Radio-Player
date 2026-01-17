package com.hyliankid14.bbcradioplayer.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import com.hyliankid14.bbcradioplayer.Episode
import com.hyliankid14.bbcradioplayer.Podcast
import java.util.*
import android.util.Log

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
            if (tokens.size == 1) return "${tokens[0]}*"

            // Construct an exact quoted phrase match (best-case), a NEAR/3 proximity fallback,
            // and finally a prefix-AND fallback to maximize recall for phrase-like queries.
            val phrase = '"' + tokens.joinToString(" ") + '"'
            val near = tokens.joinToString(" NEAR/3 ") { it }
            val tokenAnd = tokens.joinToString(" AND ") { "${it}*" }
            // Parenthesize each clause to ensure correct operator precedence in MATCH expressions
            return "($phrase) OR ($near) OR ($tokenAnd)"
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

    fun replaceAllEpisodes(episodes: List<Episode>, onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }) {
        val db = helper.writableDatabase
        val total = episodes.size
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM episode_fts;")
            val stmt: SQLiteStatement = db.compileStatement("INSERT INTO episode_fts(episodeId, podcastId, title, description) VALUES (?, ?, ?, ?);")
            var processed = 0
            val reportInterval = if (total <= 100) 1 else (total / 100)
            for (e in episodes) {
                stmt.clearBindings()
                stmt.bindString(1, e.id)
                stmt.bindString(2, e.podcastId)
                stmt.bindString(3, e.title ?: "")
                stmt.bindString(4, e.description ?: "")
                stmt.executeInsert()
                processed++
                if (processed % reportInterval == 0 || processed == total) {
                    try { onProgress(processed, total) } catch (_: Exception) {}
                }
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
        Log.d("IndexStore", "FTS podcast search: matchExpr='$match' originalQuery='$query' limit=$limit")
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
        Log.d("IndexStore", "FTS podcast search returned ${results.size} hits for query='$query'")
        return results
    }

    private fun buildFtsVariants(query: String): List<String> {
        // Return a prioritized list of MATCH expressions to try for multi-token queries
        val q = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^\\p{L}0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.getDefault())

        val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        if (tokens.size == 1) {
            // For single-token queries try a few prioritized variants: prefix, exact token, and field-scoped exact
            val t = tokens[0]
            return listOf("${t}*", "($t)", "(title:$t) OR (description:$t)")
        }

        val phrase = '"' + tokens.joinToString(" ") + '"'
        val near = tokens.joinToString(" NEAR/3 ") { it }
        val tokenAnd = tokens.joinToString(" AND ") { "${it}*" }

        val variants = mutableListOf<String>()
        // Exact phrase across all fields
        variants.add("($phrase)")
        // Phrase in title or description specifically
        variants.add("(title:$phrase) OR (description:$phrase)")
        // NEAR proximity (looser but adjacency-preserving)
        variants.add("($near)")
        // Prefix-AND fallback
        variants.add("($tokenAnd)")
        return variants
    }

    fun searchEpisodes(query: String, limit: Int = 100): List<EpisodeFts> {
        if (query.isBlank()) return emptyList()
        val db = helper.readableDatabase

        // Try prioritized MATCH variants and return first non-empty result set
        val variants = buildFtsVariants(query)
        for (v in variants) {
            try {
                Log.d("IndexStore", "FTS episode try: variant='$v' originalQuery='$query' limit=$limit")
                val cursor = db.rawQuery("SELECT episodeId, podcastId, title, description FROM episode_fts WHERE episode_fts MATCH ? LIMIT ?", arrayOf(v, limit.toString()))
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
                Log.d("IndexStore", "FTS episode variant returned ${results.size} hits for variant='$v' query='$query'")
                if (results.isNotEmpty()) return results
            } catch (e: Exception) {
                Log.w("IndexStore", "FTS episode variant failed '$v': ${e.message}")
            }
        }

        // Fallback: if FTS returned nothing for a multi-token query, try a looser LIKE-based check
        try {
            // Normalize query to plain tokens
            val qnorm = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .replace(Regex("[^\\p{L}0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .lowercase(Locale.getDefault())
            val tokens = qnorm.split(Regex("\\s+")).filter { it.isNotEmpty() }
            // Single-token fallback: if FTS failed, try simple LIKE on title/description to catch cases
            // where tokenization or spacing prevents a MATCH result (e.g., 'barber shop' vs 'barbershop').
            if (tokens.size == 1) {
                val t = "%${tokens[0]}%"
                val sql = "SELECT episodeId, podcastId, title, description FROM episode_fts WHERE LOWER(title) LIKE ? OR LOWER(description) LIKE ? LIMIT ?"
                Log.d("IndexStore", "FTS single-token fallback SQL: $sql token='${tokens[0]}'")
                val cursor = db.rawQuery(sql, arrayOf(t, t, limit.toString()))
                val fbResults = mutableListOf<EpisodeFts>()
                cursor.use {
                    while (it.moveToNext()) {
                        val eid = it.getString(0)
                        val pid = it.getString(1)
                        val title = it.getString(2) ?: ""
                        val desc = it.getString(3) ?: ""
                        fbResults.add(EpisodeFts(eid, pid, title, desc))
                    }
                }
                Log.d("IndexStore", "FTS single-token fallback returned ${fbResults.size} hits for query='$query'")
                if (fbResults.isNotEmpty()) return fbResults
            }

            if (tokens.size >= 2) {
                val phraseParam = "%${tokens.joinToString(" ")}%"
                // Build token AND checks for title and description
                val titleAndParams = tokens.map { "%$it%" }
                val descAndParams = tokens.map { "%$it%" }

                // Construct SQL fallback: phrase search OR (title contains all tokens) OR (description contains all tokens)
                val phraseClause = "(LOWER(title) LIKE ? OR LOWER(description) LIKE ?)"
                val titleAndClause = titleAndParams.joinToString(" AND ") { "LOWER(title) LIKE ?" }
                val descAndClause = descAndParams.joinToString(" AND ") { "LOWER(description) LIKE ?" }

                val fallbackSql = StringBuilder("SELECT episodeId, podcastId, title, description FROM episode_fts WHERE ")
                val fbParams = mutableListOf<String>()
                fallbackSql.append(phraseClause)
                fbParams.add(phraseParam)
                fbParams.add(phraseParam)

                if (titleAndClause.isNotBlank()) {
                    fallbackSql.append(" OR (").append(titleAndClause).append(")")
                    fbParams.addAll(titleAndParams)
                }
                if (descAndClause.isNotBlank()) {
                    fallbackSql.append(" OR (").append(descAndClause).append(")")
                    fbParams.addAll(descAndParams)
                }

                fallbackSql.append(" LIMIT ?")
                val finalParams = (fbParams + listOf(limit.toString())).toTypedArray()
                Log.d("IndexStore", "FTS fallback SQL: ${fallbackSql}")
                val fbCursor = db.rawQuery(fallbackSql.toString(), finalParams)
                val fbResults = mutableListOf<EpisodeFts>()
                fbCursor.use {
                    while (it.moveToNext()) {
                        val eid = it.getString(0)
                        val pid = it.getString(1)
                        val title = it.getString(2) ?: ""
                        val desc = it.getString(3) ?: ""
                        fbResults.add(EpisodeFts(eid, pid, title, desc))
                    }
                }
                Log.d("IndexStore", "FTS fallback search returned ${fbResults.size} hits for query='$query'")
                if (fbResults.isNotEmpty()) return fbResults
            }
        } catch (e: Exception) {
            Log.w("IndexStore", "FTS fallback failed: ${e.message}")
        }

        return emptyList()
    }

    /**
     * Persist and retrieve last reindex time to help users see when the on-disk index was last rebuilt.
     */
    fun setLastReindexTime(timeMillis: Long) {
        val prefs = context.getSharedPreferences("index_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putLong("last_reindex_time", timeMillis).apply()
    }

    fun getLastReindexTime(): Long? {
        val prefs = context.getSharedPreferences("index_prefs", android.content.Context.MODE_PRIVATE)
        return if (prefs.contains("last_reindex_time")) prefs.getLong("last_reindex_time", 0L) else null
    }
}
