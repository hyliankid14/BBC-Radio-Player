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
    private var episodesAdapter: EpisodeAdapter? = null
    private val playedStatusReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            // Refresh the episodes list when played status changes
            requireActivity().runOnUiThread {
                episodesAdapter?.notifyDataSetChanged()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu, inflater: android.view.MenuInflater) {
        inflater.inflate(R.menu.podcast_detail_menu, menu)

        // Initialize the action icon state to match subscription
        val item = menu.findItem(R.id.action_subscribe)
        val actionView = item?.actionView
        val iconView = actionView?.findViewById<android.widget.ImageView>(R.id.action_subscribe_icon)
        val subscribed = currentPodcast?.let { PodcastSubscriptions.isSubscribed(requireContext(), it.id) } ?: false
        iconView?.setImageResource(if (subscribed) R.drawable.ic_star_filled else R.drawable.ic_check)
        // Ensure tapping the action view invokes the menu item handler
        actionView?.setOnClickListener { v ->
            onOptionsItemSelected(item)
        }
        super.onCreateOptionsMenu(menu, inflater)
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
                show()
                title = podcast.title
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
                setHomeAsUpIndicator(R.drawable.ic_arrow_back)
            }
            // Also set the activity title as a fallback so the action bar shows correctly
            requireActivity().title = podcast.title

            // Podcast title is shown in the action bar; hide the inline title to avoid duplication
            val fullDescriptionHtml = podcast.description ?: ""
            descriptionView.text = HtmlCompat.fromHtml(fullDescriptionHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
            titleView.visibility = View.GONE
            
            // Ensure the description fills the vertical space next to the artwork (defined in layout via weight)
            descriptionView.gravity = android.view.Gravity.TOP

            // Make the header tappable to show the full description (and show explicit affordance)
            val showDialog = {
                val dialog = EpisodeDescriptionDialogFragment.newInstance(fullDescriptionHtml, podcast.title, podcast.imageUrl)
                dialog.show(parentFragmentManager, "episode_description")
            }

            // Set image and description to open the full description
            if (fullDescriptionHtml.isNotEmpty()) {
                imageView.setOnClickListener { showDialog() }
                descriptionView.setOnClickListener { showDialog() }

                // Detect whether the description text is truncated within its allocated space by comparing
                // the last visible character index against the total text length. If not all characters are
                // visible, show the 'Show more' affordance.
                showMoreView.visibility = View.GONE
                descriptionView.post {
                    val layout = descriptionView.layout
                    var truncated = false
                    if (layout != null && layout.lineCount > 0) {
                        val lastLine = layout.lineCount - 1
                        val lastChar = layout.getLineEnd(lastLine)
                        val totalChars = descriptionView.text?.length ?: 0
                        truncated = lastChar < totalChars
                    }
                    showMoreView.visibility = if (truncated) View.VISIBLE else View.GONE
                    if (truncated) showMoreView.setOnClickListener { showDialog() }
                    else showMoreView.setOnClickListener(null)
                }
            } else {
                showMoreView.visibility = View.GONE
                descriptionView.setOnClickListener(null)
                imageView.setOnClickListener(null)
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
                // Also update the action bar icon if present
                try {
                    val menu = (activity as? AppCompatActivity)?.supportActionBar
                    // find the toolbar and update the custom action view icon
                    val toolbar = (activity as? AppCompatActivity)?.findViewById<com.google.android.material.appbar.MaterialToolbar?>(R.id.top_app_bar)
                    val actionView = toolbar?.menu?.findItem(R.id.action_subscribe)?.actionView
                    val iconView = actionView?.findViewById<android.widget.ImageView>(R.id.action_subscribe_icon)
                    iconView?.setImageResource(if (nowSubscribed) R.drawable.ic_star_filled else R.drawable.ic_check)
                } catch (_: Exception) {}
            }

            episodesRecycler.layoutManager = LinearLayoutManager(requireContext())
            episodesAdapter = EpisodeAdapter(
                requireContext(),
                onPlayClick = { episode -> playEpisode(episode) },
                onOpenFull = { episode -> openEpisodePreview(episode) }
            )
            episodesRecycler.adapter = episodesAdapter

            // Listen for played-status changes so the list updates when items are marked/unmarked
            // Use RECEIVER_NOT_EXPORTED to satisfy Android's requirement for non-system broadcasts
            requireContext().registerReceiver(playedStatusReceiver, android.content.IntentFilter(PlayedEpisodesPreference.ACTION_PLAYED_STATUS_CHANGED), android.content.Context.RECEIVER_NOT_EXPORTED)

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
                                episodesAdapter?.updateEpisodes(page)
                                // Ensure action bar shows podcast title once episodes are visible
                                (activity as? AppCompatActivity)?.supportActionBar?.title = podcast.title
                            } else episodesAdapter?.addEpisodes(page)
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
        // Do NOT open the full-screen NowPlayingActivity here -- keep playback in the mini player.
        // The mini player will update via PlaybackStateHelper when the service updates playback state.
    }

    override fun onDestroy() {
        super.onDestroy()
        fragmentScope.coroutineContext[Job]?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            requireContext().unregisterReceiver(playedStatusReceiver)
        } catch (e: Exception) {
            // ignore
        }
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            // Reset action bar state and hide it so the Podcasts fragment can manage its own top bar
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowHomeEnabled(false)
            title = "Podcasts"
            hide()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                requireActivity().onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_subscribe -> {
                // Toggle subscription and show snackbar feedback
                val pod = currentPodcast ?: return true
                PodcastSubscriptions.toggleSubscription(requireContext(), pod.id)
                val nowSubscribed = PodcastSubscriptions.isSubscribed(requireContext(), pod.id)

                // Update inline subscribe button
                val view = requireView()
                val subscribeButton: Button? = view.findViewById(R.id.subscribe_button)
                subscribeButton?.let { btn ->
                    val bg = if (nowSubscribed) androidx.core.content.ContextCompat.getColor(requireContext(), R.color.subscribe_button_bg_subscribed)
                             else androidx.core.content.ContextCompat.getColor(requireContext(), R.color.subscribe_button_bg)
                    val textColor = if (nowSubscribed) androidx.core.content.ContextCompat.getColor(requireContext(), R.color.subscribe_button_text_subscribed)
                                    else androidx.core.content.ContextCompat.getColor(requireContext(), R.color.subscribe_button_text)
                    btn.backgroundTintList = android.content.res.ColorStateList.valueOf(bg)
                    btn.setTextColor(textColor)
                    btn.text = if (nowSubscribed) "Subscribed" else "Subscribe"
                }

                // Update action icon
                try {
                    val toolbar = (activity as? AppCompatActivity)?.findViewById<com.google.android.material.appbar.MaterialToolbar?>(R.id.top_app_bar)
                    val actionView = toolbar?.menu?.findItem(R.id.action_subscribe)?.actionView
                    val iconView = actionView?.findViewById<android.widget.ImageView>(R.id.action_subscribe_icon)
                    iconView?.setImageResource(if (nowSubscribed) R.drawable.ic_star_filled else R.drawable.ic_check)
                } catch (_: Exception) {}

                // Show snackbar message
                val msg = if (nowSubscribed) "Subscribed to ${pod.title}" else "Unsubscribed from ${pod.title}"
                com.google.android.material.snackbar.Snackbar.make(requireView(), msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
