package com.hyliankid14.bbcradioplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
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
            val toolbar: com.google.android.material.appbar.MaterialToolbar = view.findViewById(R.id.podcast_detail_toolbar)
            val imageView: ImageView = view.findViewById(R.id.podcast_detail_image)
            val titleView: TextView = view.findViewById(R.id.podcast_detail_title)
            val descriptionView: TextView = view.findViewById(R.id.podcast_detail_description)
            val showMoreView: TextView = view.findViewById(R.id.podcast_detail_show_more)
            val subscribeButton: Button = view.findViewById(R.id.subscribe_button)
            val episodesRecycler: RecyclerView = view.findViewById(R.id.episodes_recycler)
            val loadingIndicator: ProgressBar = view.findViewById(R.id.loading_progress)
            val emptyState: TextView = view.findViewById(R.id.empty_state_text)

            (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)
            toolbar.title = podcast.title
            toolbar.setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }

            titleView.text = podcast.title
            descriptionView.text = HtmlCompat.fromHtml(podcast.description, HtmlCompat.FROM_HTML_MODE_LEGACY)
            
            // Add "Show more" functionality for description
            var userExpanded = false
            descriptionView.post {
                if (descriptionView.lineCount > 5) {
                    showMoreView.visibility = View.VISIBLE
                }
            }

            val toggleHeader: () -> Unit = {
                val expanding = descriptionView.maxLines == 5
                if (expanding) {
                    descriptionView.maxLines = Int.MAX_VALUE
                    showMoreView.visibility = View.GONE
                    descriptionView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    userExpanded = true
                } else {
                    descriptionView.maxLines = 5
                    showMoreView.visibility = View.VISIBLE
                    descriptionView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_expand_more, 0)
                    userExpanded = false
                }
            }

            descriptionView.setOnClickListener { toggleHeader() }
            showMoreView.setOnClickListener { toggleHeader() }

            if (podcast.imageUrl.isNotEmpty()) {
                Glide.with(this)
                    .load(podcast.imageUrl)
                    .into(imageView)
            }

            // Initialize subscribe button state
            val isSubscribed = PodcastSubscriptions.isSubscribed(requireContext(), podcast.id)
            subscribeButton.text = if (isSubscribed) "Subscribed" else "Subscribe"
            subscribeButton.setOnClickListener {
                PodcastSubscriptions.toggleSubscription(requireContext(), podcast.id)
                val nowSubscribed = PodcastSubscriptions.isSubscribed(requireContext(), podcast.id)
                subscribeButton.text = if (nowSubscribed) "Subscribed" else "Subscribe"
            }

            episodesRecycler.layoutManager = LinearLayoutManager(requireContext())
            val adapter = EpisodeAdapter(requireContext()) { episode ->
                playEpisode(episode)
            }
            episodesRecycler.adapter = adapter

            episodesRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                    val isLongDescription = descriptionView.lineCount > 5
                    if (firstVisible > 0 && isLongDescription) {
                        descriptionView.maxLines = 5
                        showMoreView.visibility = View.VISIBLE
                        descriptionView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_expand_more, 0)
                    } else if (firstVisible == 0 && userExpanded && isLongDescription) {
                        descriptionView.maxLines = Int.MAX_VALUE
                        showMoreView.visibility = View.GONE
                        descriptionView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    }
                }
            })

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
            currentPodcast?.let {
                putExtra(RadioService.EXTRA_PODCAST_TITLE, it.title)
                putExtra(RadioService.EXTRA_PODCAST_IMAGE, it.imageUrl)
            }
        }
        requireContext().startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        fragmentScope.coroutineContext[Job]?.cancel()
    }
}
