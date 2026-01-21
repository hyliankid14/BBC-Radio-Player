package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.text.HtmlCompat

class PlayedHistoryAdapter(
    private val context: Context,
    private var entries: List<PlayedHistoryPreference.Entry>,
    private val onPlayEpisode: (Episode, String, String) -> Unit,
    private val onOpenEpisode: (Episode, String, String) -> Unit
) : RecyclerView.Adapter<PlayedHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.episode_title)
        val desc: TextView = view.findViewById(R.id.episode_description)
        val podcastTitle: TextView? = view.findViewById(R.id.episode_podcast)
        val date: TextView = view.findViewById(R.id.episode_date)
        val duration: TextView? = view.findViewById(R.id.episode_duration)
        val play: View? = view.findViewById(R.id.episode_play_icon)
    }

    private fun sanitize(raw: String): String {
        return HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
    }

    private fun formatDate(raw: String): String {
        if (raw.isBlank()) return ""
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH",
            "EEE, dd MMM yyyy",
            "dd MMM yyyy HH",
            "dd MMM yyyy"
        )
        val parsed: java.util.Date? = patterns.firstNotNullOfOrNull { pattern ->
            try {
                java.text.SimpleDateFormat(pattern, java.util.Locale.US).parse(raw)
            } catch (e: java.text.ParseException) {
                null
            }
        }
        val cleaned = raw.trim().replace(Regex("\\s+(GMT|UTC|UT)", RegexOption.IGNORE_CASE), "").replace(Regex(",\\s+"), ", ")
        val fallback = cleaned.replace(Regex("\\s+\\d{1,2}:\\d{2}(:\\d{2})?"), "").replace(Regex("\\s+\\d{1,2}$"), "").trim()
        return parsed?.let { java.text.SimpleDateFormat("EEE, dd MMM yyyy", java.util.Locale.US).format(it) } ?: fallback
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(context).inflate(R.layout.item_episode, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val e = entries[position]
        holder.title.text = e.title
        holder.desc.text = sanitize(e.description)
        if (e.podcastTitle.isBlank()) {
            holder.podcastTitle?.visibility = View.GONE
        } else {
            holder.podcastTitle?.visibility = View.VISIBLE
            holder.podcastTitle?.text = e.podcastTitle
        }
        holder.date.text = formatDate(e.pubDate)
        holder.duration?.text = "${e.durationMins} min"

        val episode = Episode(
            id = e.id,
            title = e.title,
            description = e.description,
            audioUrl = e.audioUrl,
            imageUrl = e.imageUrl,
            pubDate = e.pubDate,
            durationMins = e.durationMins,
            podcastId = e.podcastId
        )

        holder.play?.setOnClickListener { onPlayEpisode(episode, e.podcastTitle, e.imageUrl) }
        holder.itemView.setOnClickListener { onOpenEpisode(episode, e.podcastTitle, e.imageUrl) }
    }

    override fun getItemCount(): Int = entries.size

    fun updateEntries(newEntries: List<PlayedHistoryPreference.Entry>) {
        entries = newEntries
        notifyDataSetChanged()
    }
}
