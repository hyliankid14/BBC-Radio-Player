package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.util.Locale
import androidx.core.text.HtmlCompat

/**
 * Adapter that displays grouped search results with section headers. Supports three sections:
 * - Podcast Name matches
 * - Podcast Description matches
 * - Episode matches (shows episode title and parent podcast title)
 */
class SearchResultsAdapter(
    private val context: Context,
    private val titleMatches: List<Podcast>,
    private val descMatches: List<Podcast>,
    private val episodeMatches: List<Pair<Episode, Podcast>>,
    private val onPodcastClick: (Podcast) -> Unit,
    private val onPlayEpisode: (Episode) -> Unit,
    private val onOpenEpisode: (Episode, Podcast) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Item {
        data class Section(val title: String) : Item()
        data class PodcastItem(val podcast: Podcast) : Item()
        data class EpisodeItem(val episode: Episode, val podcast: Podcast) : Item()
    }

    private val items: List<Item>

    init {
        val list = mutableListOf<Item>()
        if (titleMatches.isNotEmpty()) {
            list.add(Item.Section("Podcast Name"))
            titleMatches.forEach { list.add(Item.PodcastItem(it)) }
        }
        if (descMatches.isNotEmpty()) {
            list.add(Item.Section("Podcast Description"))
            descMatches.forEach { list.add(Item.PodcastItem(it)) }
        }
        if (episodeMatches.isNotEmpty()) {
            list.add(Item.Section("Episode"))
            episodeMatches.forEach { (ep, p) -> list.add(Item.EpisodeItem(ep, p)) }
        }
        items = list
    }

    companion object {
        private const val TYPE_SECTION = 0
        private const val TYPE_PODCAST = 1
        private const val TYPE_EPISODE = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is Item.Section -> TYPE_SECTION
            is Item.PodcastItem -> TYPE_PODCAST
            is Item.EpisodeItem -> TYPE_EPISODE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return when (viewType) {
            TYPE_SECTION -> {
                val v = inflater.inflate(R.layout.item_section_header, parent, false)
                SectionViewHolder(v)
            }
            TYPE_PODCAST -> {
                val v = inflater.inflate(R.layout.item_podcast, parent, false)
                PodcastViewHolder(v)
            }
            TYPE_EPISODE -> {
                val v = inflater.inflate(R.layout.item_episode, parent, false)
                EpisodeViewHolder(v)
            }
            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val it = items[position]) {
            is Item.Section -> (holder as SectionViewHolder).bind(it.title)
            is Item.PodcastItem -> (holder as PodcastViewHolder).bind(it.podcast)
            is Item.EpisodeItem -> (holder as EpisodeViewHolder).bind(it.episode, it.podcast)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class SectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.section_title)
        fun bind(text: String) {
            title.text = text
        }
    }

    inner class PodcastViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.podcast_image)
        private val titleView: TextView = itemView.findViewById(R.id.podcast_title)
        private val descriptionView: TextView = itemView.findViewById(R.id.podcast_description)

        fun bind(podcast: Podcast) {
            titleView.text = podcast.title
            descriptionView.text = podcast.description.take(100)

            if (podcast.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context).load(podcast.imageUrl).into(imageView)
            }

            itemView.setOnClickListener { onPodcastClick(podcast) }
            titleView.setOnClickListener { onPodcastClick(podcast) }
            descriptionView.setOnClickListener { onPodcastClick(podcast) }
        }
    }

    inner class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.episode_title)
        private val descriptionView: TextView = itemView.findViewById(R.id.episode_description)
        private val dateView: TextView = itemView.findViewById(R.id.episode_date)
        private val playButton: View? = itemView.findViewById(R.id.episode_play_icon)
        private val durationView: TextView? = itemView.findViewById(R.id.episode_duration)

        fun bind(episode: Episode, podcast: Podcast) {
            titleView.text = episode.title
            // Show podcast title as small subtitle appended to description for context
            val desc = sanitize(episode.description)
            val combined = if (desc.isNotEmpty()) "$desc\n\n${podcast.title}" else podcast.title
            descriptionView.text = combined
            dateView.text = formatDate(episode.pubDate)
            durationView?.text = "${episode.durationMins} min"

            playButton?.setOnClickListener { onPlayEpisode(episode) }

            // Open preview (full activity) when title or description tapped
            titleView.setOnClickListener { onOpenEpisode(episode, podcast) }
            descriptionView.setOnClickListener { onOpenEpisode(episode, podcast) }
        }

        private fun sanitize(raw: String): String {
            val sp = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY)
            return sp.toString().trim()
        }

        private fun formatDate(raw: String): String {
            return raw.substringBefore(":").trim()
        }
    }
}
