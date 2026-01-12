package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
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
    private val onOpenPlayer: (() -> Unit)? = null,
    private val highlightSubscribed: Boolean = false
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

    inner class PodcastViewHolder(
        itemView: View,
        private val onPodcastClick: (Podcast) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private lateinit var currentPodcast: Podcast
        private val imageView: ImageView = itemView.findViewById(R.id.podcast_image)
        private val titleView: TextView = itemView.findViewById(R.id.podcast_title)
        private val descriptionView: TextView = itemView.findViewById(R.id.podcast_description)
        private val genresView: TextView = itemView.findViewById(R.id.podcast_genres)

        init {
            // Use adapter position to safely resolve the podcast at the time of the click
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos < this@PodcastAdapter.podcasts.size) {
                    val podcast = this@PodcastAdapter.podcasts[pos]
                    android.util.Log.d("PodcastAdapter", "Tapped podcast row: ${podcast.title}")
                    onPodcastClick(podcast)
                }
            }
            titleView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos < this@PodcastAdapter.podcasts.size) {
                    val podcast = this@PodcastAdapter.podcasts[pos]
                    android.util.Log.d("PodcastAdapter", "Tapped podcast title: ${podcast.title}")
                    onPodcastClick(podcast)
                }
            }
            descriptionView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos < this@PodcastAdapter.podcasts.size) {
                    val podcast = this@PodcastAdapter.podcasts[pos]
                    android.util.Log.d("PodcastAdapter", "Tapped podcast description: ${podcast.title}")
                    onPodcastClick(podcast)
                }
            }
            imageView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos < this@PodcastAdapter.podcasts.size) {
                    val podcast = this@PodcastAdapter.podcasts[pos]
                    android.util.Log.d("PodcastAdapter", "Tapped podcast image: ${podcast.title}")
                    onPodcastClick(podcast)
                }
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

            // Show a filled star for subscribed podcasts in the main list
            val subscribedIcon: ImageView? = itemView.findViewById(R.id.podcast_subscribed_icon)
            if (PodcastSubscriptions.isSubscribed(itemView.context, podcast.id)) {
                subscribedIcon?.setImageResource(R.drawable.ic_star_filled)
                subscribedIcon?.visibility = View.VISIBLE
            } else {
                subscribedIcon?.visibility = View.GONE
            }

            // Highlight subscribed podcasts when used in the Favorites list using fixed lavender color
            if ((itemView.context as? android.app.Activity) != null && (adapterPosition >= 0)) {
                if (highlightSubscribed && PodcastSubscriptions.isSubscribed(itemView.context, podcast.id)) {
                    val bg = androidx.core.content.ContextCompat.getColor(itemView.context, R.color.subscribed_podcasts_bg)
                    val on = androidx.core.content.ContextCompat.getColor(itemView.context, R.color.subscribed_podcasts_text)
                    itemView.setBackgroundColor(bg)
                    // Use the same darker text for title, description and genres to increase contrast
                    titleView.setTextColor(on)
                    descriptionView.setTextColor(on)
                    genresView.setTextColor(on)
                } else {
                    itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    titleView.setTextColor(androidx.core.content.ContextCompat.getColor(itemView.context, R.color.md_theme_onSurface))
                    descriptionView.setTextColor(androidx.core.content.ContextCompat.getColor(itemView.context, R.color.md_theme_onSurfaceVariant))
                    genresView.setTextColor(androidx.core.content.ContextCompat.getColor(itemView.context, R.color.md_theme_onSurfaceVariant))
                }
            }
        }
    }
}

class EpisodeAdapter(
    private val context: Context,
    private var episodes: List<Episode> = emptyList(),
    private val onPlayClick: (Episode) -> Unit,
    private val onOpenFull: (Episode) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    fun updateEpisodes(newEpisodes: List<Episode>) {
        episodes = newEpisodes
        notifyDataSetChanged()
    }

    fun addEpisodes(newEpisodes: List<Episode>) {
        episodes = episodes + newEpisodes
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_episode, parent, false)
        return EpisodeViewHolder(view, onPlayClick, onOpenFull)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(episodes[position])
    }

    override fun getItemCount() = episodes.size

    class EpisodeViewHolder(
        itemView: View,
        private val onPlayClick: (Episode) -> Unit,
        private val onOpenFull: (Episode) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private lateinit var currentEpisode: Episode
        private val titleView: TextView = itemView.findViewById(R.id.episode_title)
        private val descriptionView: TextView = itemView.findViewById(R.id.episode_description)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.episode_progress_bar)
        private val showMoreView: TextView = itemView.findViewById(R.id.episode_show_more)
        private val dateView: TextView = itemView.findViewById(R.id.episode_date)
        private val durationView: TextView = itemView.findViewById(R.id.episode_duration)
        private val playButton: MaterialButton = itemView.findViewById(R.id.episode_play_icon)
        private val playedIcon: ImageView? = itemView.findViewById(R.id.episode_played_icon)
        private var isExpanded = false
        private val collapsedLines = 2

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
                onPlayClick(currentEpisode)
            }
            // Play when the play button is tapped
            playButton.setOnClickListener(playAction)

            // Do not open preview when the row itself is tapped — only specific subviews are actionable
            itemView.setOnClickListener(null)

            // Make the title and description open the full-screen player in preview mode (no autoplay)
            titleView.isClickable = true
            titleView.isFocusable = true
            titleView.setOnClickListener { onOpenFull(currentEpisode) }

            descriptionView.isClickable = true
            descriptionView.isFocusable = true
            descriptionView.setOnClickListener { onOpenFull(currentEpisode) }
        }

        fun bind(episode: Episode) {
            currentEpisode = episode
            titleView.text = episode.title
            isExpanded = false
            descriptionView.maxLines = collapsedLines
            // Hide the inline toggle for episode items — show-more is handled in the full-screen player.
            showMoreView.visibility = View.GONE

            // Show description text sanitized
            val fullDesc = sanitizeDescription(episode.description)
            descriptionView.text = fullDesc

            // Show saved playback progress if available and episode has duration
            val progressMs = PlayedEpisodesPreference.getProgress(itemView.context, episode.id)
            val durMs = (episode.durationMins.takeIf { it > 0 } ?: 0) * 60_000L
            val isPlayed = PlayedEpisodesPreference.isPlayed(itemView.context, episode.id)

            // Set progress bar visibility and value (only when not completed)
            if (!isPlayed && durMs > 0 && progressMs > 0L) {
                val ratio = (progressMs.toDouble() / durMs.toDouble()).coerceIn(0.0, 1.0)
                val percent = Math.round(ratio * 100).toInt()
                progressBar.progress = percent
                progressBar.visibility = View.VISIBLE
            } else {
                progressBar.visibility = View.GONE
            }

            // Show indicator: check for completed, hollow circle for in-progress, otherwise hidden
            val isPlayed = PlayedEpisodesPreference.isPlayed(itemView.context, episode.id)
            if (isPlayed) {
                playedIcon?.setImageResource(R.drawable.ic_check)
                playedIcon?.visibility = View.VISIBLE
            } else if (durMs > 0 && progressMs > 0L) {
                // Consider in-progress when progress > 0 and < 95%
                val ratio = progressMs.toDouble() / durMs.toDouble()
                if (ratio < 0.95) {
                    playedIcon?.setImageResource(R.drawable.ic_circle_outline)
                    playedIcon?.visibility = View.VISIBLE
                } else {
                    playedIcon?.visibility = View.GONE
                }
            } else {
                playedIcon?.visibility = View.GONE
            }
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

