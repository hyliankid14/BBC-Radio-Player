package com.hyliankid14.bbcradioplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

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
            val headerContainer: View = view.findViewById(R.id.podcast_detail_header)
            val imageView: ImageView = view.findViewById(R.id.podcast_detail_image)
            val titleView: TextView = view.findViewById(R.id.podcast_detail_title)
            val descriptionView: TextView = view.findViewById(R.id.podcast_detail_description)
            val showMoreView: TextView = view.findViewById(R.id.podcast_detail_show_more)
            val subscribeButton: Button = view.findViewById(R.id.subscribe_button)
            val episodesRecycler: RecyclerView = view.findViewById(R.id.episodes_recycler)
            val loadingIndicator: ProgressBar = view.findViewById(R.id.loading_progress)
            val emptyState: TextView = view.findViewById(R.id.empty_state_text)

            (activity as? AppCompatActivity)?.supportActionBar?.apply {
                title = "Podcasts"
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
                setHomeAsUpIndicator(R.drawable.ic_arrow_back)
            }

            titleView.text = podcast.title
            descriptionView.text = HtmlCompat.fromHtml(podcast.description, HtmlCompat.FROM_HTML_MODE_LEGACY)
            
            // Add "Show more" functionality for description (clamp to 3 lines while image height)
            var userExpanded = false
            descriptionView.post {
                if (descriptionView.lineCount > 3) {
                    showMoreView.visibility = View.VISIBLE
                }
            }

            val toggleHeader: () -> Unit = {
                // Animate layout changes so the subscribe button and episodes move smoothly
                android.transition.TransitionManager.beginDelayedTransition(headerContainer as android.view.ViewGroup)

                val expanding = descriptionView.maxLines == 3
                if (expanding) {
                    // Expand: allow description to grow beyond image height
                    val lp = descriptionView.layoutParams as android.widget.LinearLayout.LayoutParams
                    lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    lp.weight = 0f
                    descriptionView.layoutParams = lp

                    descriptionView.maxLines = Int.MAX_VALUE
                    descriptionView.gravity = android.view.Gravity.TOP
                    showMoreView.visibility = View.GONE
                    descriptionView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    headerContainer.requestLayout()
                    userExpanded = true
                } else {
                    // Collapse: constrain description to image height again
                    val lp = descriptionView.layoutParams as android.widget.LinearLayout.LayoutParams
                    lp.height = 0
                    lp.weight = 1f
                    descriptionView.layoutParams = lp

                    descriptionView.maxLines = 3
                    descriptionView.gravity = android.view.Gravity.BOTTOM
                    showMoreView.visibility = View.VISIBLE
                    descriptionView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_expand_more, 0)
                    headerContainer.requestLayout()
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
            val adapter = EpisodeAdapter(
                requireContext(),
                onPlayClick = { episode -> playEpisode(episode) },
                onOpenFull = { episode -> openEpisodePreview(episode) }
            )
            episodesRecycler.adapter = adapter

            // Make the RecyclerView participate in the parent NestedScrollView instead of scrolling independently
            episodesRecycler.isNestedScrollingEnabled = false

            // Show a FAB after the user scrolls a bit; tapping it scrolls back to the top
            val scrollView = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.podcast_detail_scroll)
            val fab = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.scroll_to_top_fab)
            val showThresholdPx = (160 * resources.displayMetrics.density).toInt()
            scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                if (scrollY > showThresholdPx) {
                    fab.show()
                } else {
                    fab.hide()
                }
            }
            fab.setOnClickListener {
                scrollView.smoothScrollTo(0, 0)
            }

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

    private fun openEpisodePreview(episode: Episode) {
        val intent = Intent(requireContext(), NowPlayingActivity::class.java).apply {
            putExtra("preview_episode", episode)
            currentPodcast?.let {
                putExtra("preview_podcast_title", it.title)
                putExtra("preview_podcast_image", it.imageUrl)
            }
        }
        startActivity(intent)
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

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowHomeEnabled(false)
            title = "Podcasts"
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                requireActivity().onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
