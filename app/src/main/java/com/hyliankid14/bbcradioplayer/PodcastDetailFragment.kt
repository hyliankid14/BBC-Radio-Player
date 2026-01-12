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
                title = podcast.title
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
                setHomeAsUpIndicator(R.drawable.ic_arrow_back)
            }
            // Also set the activity title as a fallback so the action bar shows correctly
            requireActivity().title = podcast.title

            // Podcast title is shown in the action bar; hide the inline title to avoid duplication
            val fullDescriptionText = HtmlCompat.fromHtml(podcast.description, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
            descriptionView.text = HtmlCompat.fromHtml(podcast.description, HtmlCompat.FROM_HTML_MODE_LEGACY)
            titleView.visibility = View.GONE
            
            // Show the description filling the available space next to the artwork.
            val initialLp = descriptionView.layoutParams as? android.widget.LinearLayout.LayoutParams
            initialLp?.let {
                it.height = 0
                it.weight = 1f
                descriptionView.layoutParams = it
                descriptionView.maxLines = Int.MAX_VALUE
                descriptionView.gravity = android.view.Gravity.TOP
                descriptionView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }

            // Make the header tappable to show the full description (and show explicit affordance)
            if (fullDescriptionText.isNotEmpty()) {
                showMoreView.visibility = View.VISIBLE
                val showDialog = {
                    val dialog = EpisodeDescriptionDialogFragment.newInstance(fullDescriptionText, "Podcast Description")
                    dialog.show(parentFragmentManager, "episode_description")
                }
                showMoreView.setOnClickListener { showDialog() }
                descriptionView.setOnClickListener { showDialog() }
            } else {
                showMoreView.visibility = View.GONE
                descriptionView.setOnClickListener(null)
            }

            if (podcast.imageUrl.isNotEmpty()) {
                Glide.with(this)
                    .load(podcast.imageUrl)
                    .into(imageView)
            }

            // Initialize subscribe button state and colors (use high-contrast text colors)
            fun updateSubscribeButton(subscribed: Boolean) {
                subscribeButton.text = if (subscribed) "Subscribed" else "Subscribe"
                val bg = if (subscribed) androidx.core.content.ContextCompat.getColor(requireContext(), R.color.subscribe_button_bg_subscribed)
                         else androidx.core.content.ContextCompat.getColor(requireContext(), R.color.subscribe_button_bg)
                val textColor = if (subscribed) androidx.core.content.ContextCompat.getColor(requireContext(), R.color.subscribe_button_text_subscribed)
                                else androidx.core.content.ContextCompat.getColor(requireContext(), R.color.subscribe_button_text)
                subscribeButton.backgroundTintList = android.content.res.ColorStateList.valueOf(bg)
                subscribeButton.setTextColor(textColor)
            }

            val isSubscribed = PodcastSubscriptions.isSubscribed(requireContext(), podcast.id)
            updateSubscribeButton(isSubscribed)
            subscribeButton.setOnClickListener {
                PodcastSubscriptions.toggleSubscription(requireContext(), podcast.id)
                val nowSubscribed = PodcastSubscriptions.isSubscribed(requireContext(), podcast.id)
                updateSubscribeButton(nowSubscribed)
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
            val fab = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.scroll_to_top_fab)
            // Show FAB sooner — lower threshold to make it visible when user scrolls down a little
            val showThresholdPx = (48 * resources.displayMetrics.density).toInt()
            fab.setOnClickListener {
                val scrollView = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.podcast_detail_scroll)
                scrollView.smoothScrollTo(0, 0)
            }

            // Implement lazy-loading (paged) fetch for episodes
            val pageSize = 20
            var currentOffset = 0
            var isLoadingPage = false
            var reachedEnd = false

            fun loadNextPage() {
                if (isLoadingPage || reachedEnd) return
                isLoadingPage = true
                loadingIndicator.visibility = View.VISIBLE
                fragmentScope.launch {
                    val page = repository.fetchEpisodesPaged(podcast, currentOffset, pageSize)
                    loadingIndicator.visibility = View.GONE

                    if (page.isEmpty()) {
                        if (currentOffset == 0) {
                            emptyState.visibility = View.VISIBLE
                            episodesRecycler.visibility = View.GONE
                        }
                        reachedEnd = true
                    } else {
                        emptyState.visibility = View.GONE
                        episodesRecycler.visibility = View.VISIBLE
                            if (currentOffset == 0) {
                                adapter.updateEpisodes(page)
                                // Ensure action bar shows podcast title once episodes are visible
                                (activity as? AppCompatActivity)?.supportActionBar?.title = podcast.title
                            } else adapter.addEpisodes(page)
                        currentOffset += page.size
                    }
                    isLoadingPage = false
                }
            }

            // Trigger initial page load
            loadNextPage()

            // Load more and handle FAB show/hide when the parent NestedScrollView nears the bottom
            val parentScroll = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.podcast_detail_scroll)
            parentScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                val child = parentScroll.getChildAt(0)
                if (child != null) {
                    val diff = child.measuredHeight - (parentScroll.height + scrollY)
                    // Load next page when within ~600px of bottom
                    if (diff <= 600 && !isLoadingPage && !reachedEnd) {
                        loadNextPage()
                    }
                }
                // FAB visibility
                if (scrollY > showThresholdPx) fab.show() else fab.hide()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        currentPodcast?.let { podcast ->
            (activity as? AppCompatActivity)?.supportActionBar?.title = podcast.title
        }
    }

    private fun openEpisodePreview(episode: Episode) {
        val intent = Intent(requireContext(), NowPlayingActivity::class.java).apply {
            putExtra("preview_episode", episode)
            // Ask NowPlaying to present the same play-style UI (show play button, same artwork/title)
            putExtra("preview_use_play_ui", true)
            currentPodcast?.let {
                putExtra("preview_podcast_title", it.title)
                putExtra("preview_podcast_image", it.imageUrl)
                putExtra("initial_podcast_title", it.title)
                putExtra("initial_podcast_image", it.imageUrl)
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
        // Open the now-playing screen so play behavior matches tapping the row
        val npIntent = Intent(requireContext(), NowPlayingActivity::class.java).apply {
            // Provide initial artwork/title so the NowPlaying screen can show artwork immediately
            currentPodcast?.let {
                putExtra("initial_podcast_title", it.title)
                putExtra("initial_podcast_image", it.imageUrl)
            }
        }
        startActivity(npIntent)
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
