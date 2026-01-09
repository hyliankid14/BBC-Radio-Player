package com.hyliankid14.bbcradioplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

class PodcastDetailActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var podcastImage: ImageView
    private lateinit var podcastTitle: TextView
    private lateinit var podcastDescription: TextView
    private lateinit var durationFilterSpinner: Spinner
    private lateinit var episodesList: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    
    private lateinit var podcast: Podcast
    private lateinit var episodeAdapter: EpisodeAdapter
    private var allEpisodes: List<PodcastEpisode> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_podcast_detail)

        // Get podcast from intent
        val podcastId = intent.getStringExtra(EXTRA_PODCAST_ID) ?: run {
            finish()
            return
        }
        val podcastTitle = intent.getStringExtra(EXTRA_PODCAST_TITLE) ?: ""
        val podcastDescription = intent.getStringExtra(EXTRA_PODCAST_DESCRIPTION) ?: ""
        val podcastXmlUrl = intent.getStringExtra(EXTRA_PODCAST_XML_URL) ?: run {
            finish()
            return
        }
        val podcastHtmlUrl = intent.getStringExtra(EXTRA_PODCAST_HTML_URL) ?: ""
        val podcastImageUrl = intent.getStringExtra(EXTRA_PODCAST_IMAGE_URL)
        val podcastGenre = intent.getStringExtra(EXTRA_PODCAST_GENRE)

        podcast = Podcast(
            id = podcastId,
            title = podcastTitle,
            description = podcastDescription,
            xmlUrl = podcastXmlUrl,
            htmlUrl = podcastHtmlUrl,
            imageUrl = podcastImageUrl,
            genre = podcastGenre
        )

        initViews()
        setupToolbar()
        setupDurationFilter()
        loadEpisodes()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        podcastImage = findViewById(R.id.podcast_detail_image)
        podcastTitle = findViewById(R.id.podcast_detail_title)
        podcastDescription = findViewById(R.id.podcast_detail_description)
        durationFilterSpinner = findViewById(R.id.duration_filter_spinner)
        episodesList = findViewById(R.id.episodes_list)
        loadingProgress = findViewById(R.id.loading_progress)

        podcastTitle.text = podcast.title
        podcastDescription.text = podcast.description

        if (!podcast.imageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(podcast.imageUrl)
                .placeholder(android.R.color.darker_gray)
                .error(android.R.color.darker_gray)
                .into(podcastImage)
        }

        episodesList.layoutManager = LinearLayoutManager(this)
        episodeAdapter = EpisodeAdapter(this, emptyList()) { episode ->
            playEpisode(episode)
        }
        episodesList.adapter = episodeAdapter
    }

    private fun setupToolbar() {
        toolbar.title = podcast.title
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupDurationFilter() {
        val filterOptions = listOf("All Durations") + DurationCategory.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        durationFilterSpinner.adapter = adapter

        durationFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCategory = if (position == 0) {
                    null
                } else {
                    DurationCategory.values()[position - 1]
                }
                episodeAdapter.filterByDuration(selectedCategory)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun loadEpisodes() {
        loadingProgress.visibility = View.VISIBLE
        episodesList.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val episodes = PodcastRepository.fetchEpisodes(podcast)
                allEpisodes = episodes
                episodeAdapter.updateEpisodes(episodes)
                
                loadingProgress.visibility = View.GONE
                episodesList.visibility = View.VISIBLE
            } catch (e: Exception) {
                loadingProgress.visibility = View.GONE
                Toast.makeText(
                    this@PodcastDetailActivity,
                    "Failed to load episodes",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun playEpisode(episode: PodcastEpisode) {
        // For now, just show a toast. In a full implementation, this would integrate
        // with the existing RadioService or create a new podcast player service
        Toast.makeText(
            this,
            "Playing: ${episode.title}\n(Audio playback not yet implemented)",
            Toast.LENGTH_LONG
        ).show()
        
        // TODO: Integrate with RadioService or create PodcastPlayerService
        // to actually play the episode audio file
    }

    companion object {
        const val EXTRA_PODCAST_ID = "podcast_id"
        const val EXTRA_PODCAST_TITLE = "podcast_title"
        const val EXTRA_PODCAST_DESCRIPTION = "podcast_description"
        const val EXTRA_PODCAST_XML_URL = "podcast_xml_url"
        const val EXTRA_PODCAST_HTML_URL = "podcast_html_url"
        const val EXTRA_PODCAST_IMAGE_URL = "podcast_image_url"
        const val EXTRA_PODCAST_GENRE = "podcast_genre"
    }
}
