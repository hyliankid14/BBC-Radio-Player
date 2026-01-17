package com.hyliankid14.bbcradioplayer.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FtsDao {
    // Podcast searches
    @Query("SELECT podcastId, title, description FROM podcast_fts WHERE podcast_fts MATCH :match LIMIT :limit")
    suspend fun searchPodcasts(match: String, limit: Int = 50): List<PodcastFts>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcasts(entries: List<PodcastFts>)

    @Query("DELETE FROM podcast_fts")
    suspend fun clearPodcasts()

    // Episode searches
    @Query("SELECT episodeId, podcastId, title, description FROM episode_fts WHERE episode_fts MATCH :match LIMIT :limit")
    suspend fun searchEpisodes(match: String, limit: Int = 100): List<EpisodeFts>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(entries: List<EpisodeFts>)

    @Query("DELETE FROM episode_fts WHERE podcastId = :podcastId")
    suspend fun deleteEpisodesForPodcast(podcastId: String)

    @Query("DELETE FROM episode_fts")
    suspend fun clearEpisodes()
}
