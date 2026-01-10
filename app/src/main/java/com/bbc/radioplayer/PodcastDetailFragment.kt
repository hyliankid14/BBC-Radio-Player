package com.bbc.radioplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PodcastDetailFragment : Fragment() {
    private lateinit var repository: PodcastRepository
    private val fragmentScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentPodcast: Podcast? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_podcast_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = PodcastRepository(requireContext())

        currentPodcast = arguments?.getParcelable("podcast")
        currentPodcast?.let { podcast ->
            val imageView: ImageView = view.findViewById(R.id.podcast_detail_image)
            val titleView: TextView = view.findViewById(R.id.podcast_detail_title)
            val descriptionView: TextView = view.findViewById(R.id.podcast_detail_description)
            val subscribeButton: Button = view.findViewById(R.id.subscribe_button)
            val episodesRecycler: RecyclerView = view.findViewById(R.id.episodes_recycler)
            val loadingIndicator: ProgressBar = view.findViewById(R.id.loading_progress)
            val emptyState: TextView = view.findViewById(R.id.empty_state_text)

            titleView.text = podcast.title
            descriptionView.text = podcast.description

            if (podcast.imageUrl.isNotEmpty()) {
                Glide.with(this)
                    .load(podcast.imageUrl)
                    .into(imageView)
            }

            subscribeButton.setOnClickListener {
                // Placeholder for subscription functionality
            }

            episodesRecycler.layoutManager = LinearLayoutManager(requireContext())
            val adapter = EpisodeAdapter(requireContext()) { episode ->
                playEpisode(episode)
            }
            episodesRecycler.adapter = adapter

            loadingIndicator.visibility = View.VISIBLE
            fragmentScope.launch {
                val episodes = repository.fetchEpisodes(podcast)
                loadingIndicator.visibility = View.GONE

                if (episodes.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    episodesRecycler.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    episodesRecycler.visibility = View.VISIBLE
                    adapter.updateEpisodes(episodes)
                }
            }
        }
    }

    private fun playEpisode(episode: Episode) {
        val intent = Intent(requireContext(), RadioService::class.java).apply {
            action = RadioService.ACTION_PLAY_PODCAST_EPISODE
            putExtra(RadioService.EXTRA_EPISODE, episode)
            putExtra(RadioService.EXTRA_PODCAST_ID, episode.podcastId)
        }
        requireContext().startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        fragmentScope.coroutineContext[Job]?.cancel()
    }
}
