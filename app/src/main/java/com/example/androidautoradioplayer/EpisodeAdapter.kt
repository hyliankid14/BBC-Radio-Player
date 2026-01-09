package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*

class EpisodeAdapter(
    private val context: Context,
    private var episodes: List<PodcastEpisode>,
    private val onEpisodePlay: (PodcastEpisode) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    private var filteredEpisodes: List<PodcastEpisode> = episodes

    class EpisodeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val episodeImage: ImageView = view.findViewById(R.id.episode_image)
        val episodeTitle: TextView = view.findViewById(R.id.episode_title)
        val episodeDuration: TextView = view.findViewById(R.id.episode_duration)
        val episodeDate: TextView = view.findViewById(R.id.episode_date)
        val episodePlayButton: ImageButton = view.findViewById(R.id.episode_play_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.episode_list_item, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val episode = filteredEpisodes[position]
        
        holder.episodeTitle.text = episode.title
        holder.episodeDuration.text = episode.getFormattedDuration()
        holder.episodeDate.text = formatDate(episode.pubDate)
        
        // Load episode image if available
        if (!episode.imageUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(episode.imageUrl)
                .placeholder(android.R.color.darker_gray)
                .error(android.R.color.darker_gray)
                .into(holder.episodeImage)
        } else {
            holder.episodeImage.setImageResource(android.R.color.darker_gray)
        }
        
        holder.episodePlayButton.setOnClickListener {
            onEpisodePlay(episode)
        }
        
        holder.itemView.setOnClickListener {
            onEpisodePlay(episode)
        }
    }

    override fun getItemCount(): Int = filteredEpisodes.size

    fun updateEpisodes(newEpisodes: List<PodcastEpisode>) {
        episodes = newEpisodes
        filteredEpisodes = newEpisodes
        notifyDataSetChanged()
    }

    fun filterByDuration(category: DurationCategory?) {
        filteredEpisodes = if (category == null) {
            episodes
        } else {
            episodes.filter { it.getDurationCategory() == category }
        }
        notifyDataSetChanged()
    }

    private fun formatDate(dateStr: String): String {
        return try {
            // Parse RFC 822 date format used in RSS
            val inputFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
            val date = inputFormat.parse(dateStr)
            
            // Format to a more readable format
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            // If parsing fails, just return the original string or a trimmed version
            dateStr.take(20)
        }
    }
}
