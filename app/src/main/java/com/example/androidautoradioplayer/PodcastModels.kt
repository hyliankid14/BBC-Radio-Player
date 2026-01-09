package com.hyliankid14.bbcradioplayer

data class Podcast(
    val id: String,
    val title: String,
    val description: String,
    val xmlUrl: String,
    val htmlUrl: String,
    val imageUrl: String? = null,
    val genre: String? = null
)

data class PodcastEpisode(
    val id: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val duration: Int, // Duration in seconds
    val pubDate: String,
    val imageUrl: String? = null
) {
    fun getDurationMinutes(): Int = duration / 60
    
    fun getFormattedDuration(): String {
        val hours = duration / 3600
        val minutes = (duration % 3600) / 60
        val seconds = duration % 60
        
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%d:%02d", minutes, seconds)
        }
    }
    
    fun getDurationCategory(): DurationCategory {
        val minutes = getDurationMinutes()
        return when {
            minutes <= 15 -> DurationCategory.SHORT
            minutes <= 45 -> DurationCategory.MEDIUM
            else -> DurationCategory.LONG
        }
    }
}

enum class DurationCategory(val displayName: String) {
    SHORT("Short (0-15 min)"),
    MEDIUM("Medium (15-45 min)"),
    LONG("Long (45+ min)")
}
