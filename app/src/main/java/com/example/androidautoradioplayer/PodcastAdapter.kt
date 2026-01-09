package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PodcastAdapter(
    private val context: Context,
    private var podcasts: List<Podcast>,
    private val onPodcastClick: (Podcast) -> Unit
) : RecyclerView.Adapter<PodcastAdapter.PodcastViewHolder>() {

    private var filteredPodcasts: List<Podcast> = podcasts

    class PodcastViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val podcastImage: ImageView = view.findViewById(R.id.podcast_image)
        val podcastTitle: TextView = view.findViewById(R.id.podcast_title)
        val podcastGenre: TextView = view.findViewById(R.id.podcast_genre)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PodcastViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.podcast_list_item, parent, false)
        return PodcastViewHolder(view)
    }

    override fun onBindViewHolder(holder: PodcastViewHolder, position: Int) {
        val podcast = filteredPodcasts[position]
        
        holder.podcastTitle.text = podcast.title
        holder.podcastGenre.text = podcast.genre ?: "Uncategorized"
        
        // Load podcast image if available
        if (!podcast.imageUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(podcast.imageUrl)
                .placeholder(android.R.color.darker_gray)
                .error(android.R.color.darker_gray)
                .into(holder.podcastImage)
        } else {
            holder.podcastImage.setImageResource(android.R.color.darker_gray)
        }
        
        holder.itemView.setOnClickListener {
            onPodcastClick(podcast)
        }
    }

    override fun getItemCount(): Int = filteredPodcasts.size

    fun updatePodcasts(newPodcasts: List<Podcast>) {
        podcasts = newPodcasts
        filteredPodcasts = newPodcasts
        notifyDataSetChanged()
    }

    fun filterByGenre(genre: String?) {
        filteredPodcasts = if (genre == null || genre == "All") {
            podcasts
        } else {
            podcasts.filter { it.genre == genre }
        }
        notifyDataSetChanged()
    }
}
