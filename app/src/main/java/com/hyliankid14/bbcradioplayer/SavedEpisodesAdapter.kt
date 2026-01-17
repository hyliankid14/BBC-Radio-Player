package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class SavedEpisodesAdapter(
    private val context: Context,
    private var entries: List<SavedEpisodes.Entry>,
    private val onPlayEpisode: (Episode) -> Unit,
    private val onOpenEpisode: (Episode, String) -> Unit,
    private val onRemoveSaved: (String) -> Unit
) : RecyclerView.Adapter<SavedEpisodesAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.episode_title)
        val desc: TextView = view.findViewById(R.id.episode_description)
        val podcastTitle: TextView? = view.findViewById(R.id.episode_podcast)
        val date: TextView = view.findViewById(R.id.episode_date)
        val duration: TextView? = view.findViewById(R.id.episode_duration)
        val play: View? = view.findViewById(R.id.episode_play_icon)
        val image: ImageView? = view.findViewById(R.id.episode_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(context).inflate(R.layout.item_episode, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val e = entries[position]
        holder.title.text = e.title
        holder.desc.text = e.description
        holder.podcastTitle?.text = e.podcastTitle
        holder.date.text = e.pubDate
        holder.duration?.text = "${e.durationMins} min"
        // episode image may be in imageUrl; item_episode may not expose image view - ignore if null
        holder.image?.let { iv ->
            if (e.imageUrl.isNotEmpty()) Glide.with(context).load(e.imageUrl).into(iv) else iv.setImageResource(R.drawable.ic_podcast_placeholder)
        }

        val episode = Episode(
            id = e.id,
            title = e.title,
            description = e.description,
            audioUrl = "",
            imageUrl = e.imageUrl,
            pubDate = e.pubDate,
            durationMins = e.durationMins,
            podcastId = e.podcastId
        )

        holder.play?.setOnClickListener { onPlayEpisode(episode) }
        holder.itemView.setOnClickListener { onOpenEpisode(episode, e.podcastTitle) }
        holder.itemView.setOnLongClickListener {
            // Remove saved episode on long press
            onRemoveSaved(e.id)
            true
        }
    }

    override fun getItemCount(): Int = entries.size

    fun updateEntries(newEntries: List<SavedEpisodes.Entry>) {
        entries = newEntries
        notifyDataSetChanged()
    }
}
