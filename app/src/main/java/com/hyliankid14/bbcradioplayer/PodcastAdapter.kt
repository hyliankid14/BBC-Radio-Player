package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.text.HtmlCompat

class PodcastAdapter(
    private val context: Context,
    private var podcasts: List<Podcast> = emptyList(),
    private val onPodcastClick: (Podcast) -> Unit,
    private val onOpenPlayer: (() -> Unit)? = null
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

        init {
            itemView.setOnClickListener { 
                android.util.Log.d("PodcastAdapter", "Tapped podcast row: ${currentPodcast.title}")
                onPodcastClick(currentPodcast) 
            }
            titleView.setOnClickListener { 
                android.util.Log.d("PodcastAdapter", "Tapped podcast title: ${currentPodcast.title}")
                onPodcastClick(currentPodcast) 
            }
            descriptionView.setOnClickListener { 
                android.util.Log.d("PodcastAdapter", "Tapped podcast description: ${currentPodcast.title}")
                onPodcastClick(currentPodcast) 
            }
            imageView.setOnClickListener { 
                android.util.Log.d("PodcastAdapter", "Tapped podcast image: ${currentPodcast.title}")
                onPodcastClick(currentPodcast) 
            }
        }

        fun bind(podcast: Podcast) {
            currentPodcast = podcast
            titleView.text = podcast.title
            descriptionView.text = podcast.description.take(100)
            genresView.text = podcast.genres.take(2).joinToString(", ")

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
        private val showMoreView: TextView = itemView.findViewById(R.id.episode_show_more)
        private val dateView: TextView = itemView.findViewById(R.id.episode_date)
        private val durationView: TextView = itemView.findViewById(R.id.episode_duration)
        private val playButton: MaterialButton = itemView.findViewById(R.id.episode_play_icon)
        private var isExpanded = false
        private val collapsedLines = 4

        init {
            val playAction: (View) -> Unit = {
                // Subtle scale animation to give tap feedback
                playButton.animate()
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(80)
                    .withEndAction {
                        playButton.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(80)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .start()
                    }
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
                onEpisodeClick(currentEpisode)
            }

            itemView.setOnClickListener(playAction)
            playButton.setOnClickListener(playAction)
            
            showMoreView.setOnClickListener {
                // Episode items do not show an inline "Show more" popup anymore.
                // Tapping an episode opens the player instead.
            }
        }

        fun bind(episode: Episode) {
            currentEpisode = episode
            titleView.text = episode.title
            isExpanded = false
            descriptionView.maxLines = collapsedLines
            // Hide the inline toggle for episode items — show-more is handled in the full-screen player.
            showMoreView.visibility = View.GONE
            
            // Show "Show more" if description is long enough to need it
            val fullDesc = sanitizeDescription(episode.description)
            descriptionView.text = fullDesc
            // Keep the toggle visible; content is clamped when collapsed
            
            // Remove timestamp from date - just show date portion
            dateView.text = formatEpisodeDate(episode.pubDate)
            durationView.text = "${episode.durationMins} min"
        }

        private fun sanitizeDescription(raw: String): String {
            val spanned = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY)
            return spanned.toString().trim()
        }

        private fun formatEpisodeDate(raw: String): String {
            val patterns = listOf(
                "EEE, dd MMM yyyy HH:mm:ss Z",
                "dd MMM yyyy HH:mm:ss Z",
                "EEE, dd MMM yyyy"
            )
            val parsed: Date? = patterns.firstNotNullOfOrNull { pattern ->
                try {
                    SimpleDateFormat(pattern, Locale.US).parse(raw)
                } catch (e: ParseException) {
                    null
                }
            }
            return parsed?.let {
                SimpleDateFormat("EEE, dd MMM yyyy", Locale.US).format(it)
            } ?: raw.substringBefore(":").trim()
        }
    }
}
