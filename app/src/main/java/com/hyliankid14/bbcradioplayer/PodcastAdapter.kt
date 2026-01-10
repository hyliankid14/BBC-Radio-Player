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
    private var podcasts: List<Podcast> = emptyList(),
    private val onPodcastClick: (Podcast) -> Unit
) : RecyclerView.Adapter<PodcastAdapter.PodcastViewHolder>() {

    fun updatePodcasts(newPodcasts: List<Podcast>) {
        podcasts = newPodcasts
        notifyDataSetChanged()
    }

    fun addPodcasts(newPodcasts: List<Podcast>) {
        podcasts = podcasts + newPodcasts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PodcastViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_podcast, parent, false)
        return PodcastViewHolder(view, onPodcastClick)
    }

    override fun onBindViewHolder(holder: PodcastViewHolder, position: Int) {
        holder.bind(podcasts[position])
    }

    override fun getItemCount() = podcasts.size

    class PodcastViewHolder(
        itemView: View,
        private val onPodcastClick: (Podcast) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private lateinit var currentPodcast: Podcast
        private val imageView: ImageView = itemView.findViewById(R.id.podcast_image)
        private val titleView: TextView = itemView.findViewById(R.id.podcast_title)
        private val descriptionView: TextView = itemView.findViewById(R.id.podcast_description)
        private val genresView: TextView = itemView.findViewById(R.id.podcast_genres)
        private val durationView: TextView = itemView.findViewById(R.id.podcast_duration)

        init {
            itemView.setOnClickListener { onPodcastClick(currentPodcast) }
        }

        fun bind(podcast: Podcast) {
            currentPodcast = podcast
            titleView.text = podcast.title
            descriptionView.text = podcast.description.take(100)
            genresView.text = podcast.genres.take(2).joinToString(", ")
            durationView.text = "${podcast.typicalDurationMins} min"

            if (podcast.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(podcast.imageUrl)
                    .into(imageView)
            }
        }
    }
}

class EpisodeAdapter(
    private val context: Context,
    private var episodes: List<Episode> = emptyList(),
    private val onEpisodeClick: (Episode) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    fun updateEpisodes(newEpisodes: List<Episode>) {
        episodes = newEpisodes
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_episode, parent, false)
        return EpisodeViewHolder(view, onEpisodeClick)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(episodes[position])
    }

    override fun getItemCount() = episodes.size

    class EpisodeViewHolder(
        itemView: View,
        private val onEpisodeClick: (Episode) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private lateinit var currentEpisode: Episode
        private val titleView: TextView = itemView.findViewById(R.id.episode_title)
        private val descriptionView: TextView = itemView.findViewById(R.id.episode_description)
        private val dateView: TextView = itemView.findViewById(R.id.episode_date)
        private val durationView: TextView = itemView.findViewById(R.id.episode_duration)

        init {
            itemView.setOnClickListener { onEpisodeClick(currentEpisode) }
        }

        fun bind(episode: Episode) {
            currentEpisode = episode
            titleView.text = episode.title
            descriptionView.text = episode.description.take(80)
            dateView.text = episode.pubDate
            durationView.text = "${episode.durationMins} min"
        }
    }
}
