package com.hyliankid14.bbcradioplayer.db

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS entities for fast phrase and token searches.
 * We normalize text on insertion so that searches are robust to punctuation/diacritics.
 */
@Fts4
@Entity(tableName = "podcast_fts")
data class PodcastFts(
    val podcastId: String,
    val title: String,
    val description: String
)

@Fts4
@Entity(tableName = "episode_fts")
data class EpisodeFts(
    val episodeId: String,
    val podcastId: String,
    val title: String,
    val description: String
)
