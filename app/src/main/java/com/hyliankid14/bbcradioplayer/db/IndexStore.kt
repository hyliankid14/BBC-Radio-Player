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
                stmt.bindString(2, p.title)
                stmt.bindString(3, p.description)
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun truncateForIndex(s: String?, maxLen: Int): String {
        if (s.isNullOrBlank()) return ""
        // strip basic HTML and collapse whitespace to reduce index size
        val cleaned = s.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (cleaned.length <= maxLen) cleaned else cleaned.substring(0, maxLen)
    }

    /**
     * Append a batch of episodes into the FTS table. This is safe to call repeatedly to build the
     * index in small transactions (keeps memory and journal size bounded).
     */
    fun appendEpisodesBatch(episodes: List<Episode>, maxFieldLength: Int = 4096): Int {
        if (episodes.isEmpty()) return 0
        val db = helper.writableDatabase
        var inserted = 0
        db.beginTransaction()
        try {
            val stmt: SQLiteStatement = db.compileStatement("INSERT INTO episode_fts(episodeId, podcastId, title, description) VALUES (?, ?, ?, ?);")
            for (e in episodes) {
                stmt.clearBindings()
                stmt.bindString(1, e.id)
                stmt.bindString(2, e.podcastId)
                // keep original title column short and safe
                stmt.bindString(3, truncateForIndex(e.title, 512))

                // produce a trimmed, de-HTML'd searchable blob (bounded length)
                val cleanedTitle = truncateForIndex(e.title.replace(Regex("[\\p{Punct}\\s]+"), " ").lowercase(Locale.getDefault()), 256)
                val audioName = truncateForIndex(e.audioUrl.substringAfterLast('/').substringBefore('?').replace(Regex("[\\W_]+"), " ").lowercase(Locale.getDefault()), 128)
                val pub = truncateForIndex(e.pubDate, 64)
                val descPart = truncateForIndex(e.description, maxFieldLength)
                val searchBlob = listOf(descPart, cleanedTitle, pub, audioName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")

                stmt.bindString(4, searchBlob)
                stmt.executeInsert()
                inserted++
            }
            db.setTransactionSuccessful()
        } finally {
            try { db.yieldIfContendedSafely() } catch (_: Throwable) { /* best-effort */ }
            db.endTransaction()
        }
        return inserted
    }

    /**
     * Backwards-compatible replaceAllEpisodes that performs the work in bounded-size batches to
     * avoid OOM/journal blowups on large libraries. Trims very long fields before inserting.
     */
    fun replaceAllEpisodes(episodes: List<Episode>, onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }) {
        val total = episodes.size
        if (total == 0) return

        // Heuristic batch size: larger batches are faster but use more memory/journal.
        val batchSize = when {
            total <= 500 -> 100
            total <= 5_000 -> 500
            else -> 1500
        }

        // Do a single table wipe up-front, then append in batches.
        val db = helper.writableDatabase
        db.execSQL("DELETE FROM episode_fts;")

        var processed = 0
        var idx = 0
        while (idx < total) {
            val end = (idx + batchSize).coerceAtMost(total)
            val batch = episodes.subList(idx, end)
            try {
                appendEpisodesBatch(batch)
            } catch (oom: OutOfMemoryError) {
                Log.w("IndexStore", "OOM during index batch (size=${batch.size}), falling back to smaller batches")
                // Try much smaller batches as a fallback
                val small = batch.chunked(50)
                for (sb in small) appendEpisodesBatch(sb)
            } catch (e: Exception) {
                Log.w("IndexStore", "Failed to append episode batch: ${e.message}")
            }
            processed += batch.size
            idx = end
            try { onProgress(processed, total) } catch (_: Exception) {}
            try { db.yieldIfContendedSafely() } catch (_: Throwable) { /* best-effort */ }
        }
    }

    @Synchronized
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
        // bigram phrase variants: "t1 t2" OR "t2 t3" ... (helps match adjacent-word queries)
        val bigramList = tokens.windowed(2).map { '"' + it.joinToString(" ") + '"' }
        val bigramClause = if (bigramList.isNotEmpty()) bigramList.joinToString(" OR ") else ""
        val tokenAnd = tokens.joinToString(" AND ") { "${it}*" }

        val variants = mutableListOf<String>()
        // Exact phrase across all fields
        variants.add("($phrase)")
        // Phrase in title or description specifically
        variants.add("(title:$phrase) OR (description:$phrase)")
        // NEAR proximity (looser but adjacency-preserving)
        variants.add("($near)")
        // Bigram adjacency fallback (helps where only partial phrase exists)
        if (bigramClause.isNotBlank()) variants.add("($bigramClause)")
        // Prefix-AND fallback
        variants.add("($tokenAnd)")
        return variants
    }

    @Synchronized
    fun searchEpisodes(query: String, limit: Int = 100): List<EpisodeFts> {
        if (query.isBlank()) return emptyList()
        val db = helper.readableDatabase

        // Try prioritized MATCH variants and return first non-empty result set
        // Limit to first 2 variants to avoid slow searches
        val variants = buildFtsVariants(query).take(2)
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

        // Simplified fallback: only try LIKE for single-token queries to avoid slow multi-token searches
        try {
            val qnorm = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .replace(Regex("[^\\p{L}0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .lowercase(Locale.getDefault())
            val tokens = qnorm.split(Regex("\\s+")).filter { it.isNotEmpty() }
            
            // Only do LIKE fallback for single tokens to keep it fast
            if (tokens.size == 1) {
                val t = "%${tokens[0]}%"
                val sql = "SELECT episodeId, podcastId, title, description FROM episode_fts WHERE LOWER(title) LIKE ? LIMIT ?"
                Log.d("IndexStore", "FTS single-token fallback SQL token='${tokens[0]}'")
                val cursor = db.rawQuery(sql, arrayOf(t, limit.toString()))
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
                Log.d("IndexStore", "FTS single-token fallback returned ${fbResults.size} hits")
                if (fbResults.isNotEmpty()) return fbResults
            }
        } catch (e: Exception) {
            Log.w("IndexStore", "FTS fallback failed: ${e.message}")
        }

        return emptyList()
    }

    /**
     * Find an indexed episode by its canonical id. This is a fast on-disk lookup used as a
     * fallback for features like Android Auto auto-resume where we need to map an episode id
     * to its parent podcast without fetching every podcast remotely.
     */
    fun findEpisodeById(episodeId: String): EpisodeFts? {
        if (episodeId.isBlank()) return null
        val db = helper.readableDatabase
        try {
            val cursor = db.rawQuery(
                "SELECT episodeId, podcastId, title, description FROM episode_fts WHERE episodeId = ? LIMIT 1",
                arrayOf(episodeId)
            )
            cursor.use {
                if (it.moveToFirst()) {
                    val eid = it.getString(0)
                    val pid = it.getString(1)
                    val title = it.getString(2) ?: ""
                    val desc = it.getString(3) ?: ""
                    return EpisodeFts(eid, pid, title, desc)
                }
            }
        } catch (e: Exception) {
            Log.w("IndexStore", "findEpisodeById failed for $episodeId: ${e.message}")
        }
        return null
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

    // Check whether a podcast is present in the podcast FTS table
    fun hasPodcast(podcastId: String): Boolean {
        if (podcastId.isBlank()) return false
        val db = helper.readableDatabase
        try {
            val cursor = db.rawQuery("SELECT podcastId FROM podcast_fts WHERE podcastId = ? LIMIT 1", arrayOf(podcastId))
            cursor.use { return it.count > 0 }
        } catch (e: Exception) {
            Log.w("IndexStore", "hasPodcast failed for $podcastId: ${e.message}")
        }
        return false
    }

    // Check if any episodes have been indexed
    fun hasAnyEpisodes(): Boolean {
        val db = helper.readableDatabase
        try {
            val cursor = db.rawQuery("SELECT COUNT(*) FROM episode_fts LIMIT 1", emptyArray())
            cursor.use {
                if (it.moveToFirst()) {
                    return it.getInt(0) > 0
                }
            }
        } catch (e: Exception) {
            Log.w("IndexStore", "hasAnyEpisodes failed: ${e.message}")
        }
        return false
    }

    // Upsert a podcast row into the podcast_fts table
    fun upsertPodcast(p: Podcast) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            try { db.execSQL("DELETE FROM podcast_fts WHERE podcastId = ?", arrayOf(p.id)) } catch (_: Exception) {}
            val stmt = db.compileStatement("INSERT INTO podcast_fts(podcastId, title, description) VALUES (?, ?, ?)")
            stmt.clearBindings()
            stmt.bindString(1, p.id)
            stmt.bindString(2, p.title)
            stmt.bindString(3, p.description)
            stmt.executeInsert()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // Return all episode ids currently indexed for the given podcast
    fun getEpisodeIdsForPodcast(podcastId: String): Set<String> {
        val db = helper.readableDatabase
        val set = mutableSetOf<String>()
        try {
            val cursor = db.rawQuery("SELECT episodeId FROM episode_fts WHERE podcastId = ?", arrayOf(podcastId))
            cursor.use {
                while (it.moveToNext()) {
                    set.add(it.getString(0))
                }
            }
        } catch (e: Exception) {
            Log.w("IndexStore", "getEpisodeIdsForPodcast failed for $podcastId: ${e.message}")
        }
        return set
    }
}
