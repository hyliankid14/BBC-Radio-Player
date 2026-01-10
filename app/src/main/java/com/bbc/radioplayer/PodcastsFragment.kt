package com.hyliankid14.bbcradioplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PodcastsFragment : Fragment() {
    private lateinit var repository: PodcastRepository
    private lateinit var adapter: PodcastAdapter
    private var allPodcasts: List<Podcast> = emptyList()
    private var currentFilter = PodcastFilter()
    private val fragmentScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_podcasts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = PodcastRepository(requireContext())

        val recyclerView: RecyclerView = view.findViewById(R.id.podcasts_recycler)
        val genreSpinner: Spinner = view.findViewById(R.id.genre_filter_spinner)
        val durationSeekBar: SeekBar = view.findViewById(R.id.duration_range_slider)
        val resetButton: android.widget.Button = view.findViewById(R.id.reset_filters_button)
        val loadingIndicator: ProgressBar = view.findViewById(R.id.loading_progress)
        val emptyState: TextView = view.findViewById(R.id.empty_state_text)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = PodcastAdapter(requireContext()) { podcast ->
            val detail = PodcastDetailFragment().apply {
                arguments = Bundle().apply { putParcelable("podcast", podcast) }
            }
            parentFragmentManager.beginTransaction()
                .replace(R.id.content_container, detail)
                .addToBackStack(null)
                .commit()
        }
        recyclerView.adapter = adapter

        durationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentFilter = currentFilter.copy(maxDuration = progress)
                applyFilters(loadingIndicator, emptyState, recyclerView)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        resetButton.setOnClickListener {
            currentFilter = PodcastFilter()
            genreSpinner.setSelection(0)
            durationSeekBar.progress = 100
            applyFilters(loadingIndicator, emptyState, recyclerView)
        }

        loadPodcasts(loadingIndicator, emptyState, recyclerView, genreSpinner, durationSeekBar)
    }

    private fun loadPodcasts(
        loadingIndicator: ProgressBar,
        emptyState: TextView,
        recyclerView: RecyclerView,
        genreSpinner: Spinner,
        durationSeekBar: SeekBar
    ) {
        loadingIndicator.visibility = View.VISIBLE
        fragmentScope.launch {
            allPodcasts = repository.fetchPodcasts()

            val genres = listOf("All Genres") + repository.getUniqueGenres(allPodcasts)
            val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, genres)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            genreSpinner.adapter = spinnerAdapter

            genreSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    currentFilter = if (position == 0) {
                        currentFilter.copy(genres = emptySet())
                    } else {
                        currentFilter.copy(genres = setOf(genres[position]))
                    }
                    applyFilters(loadingIndicator, emptyState, recyclerView)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            applyFilters(loadingIndicator, emptyState, recyclerView)
            loadingIndicator.visibility = View.GONE
        }
    }

    private fun applyFilters(
        loadingIndicator: ProgressBar,
        emptyState: TextView,
        recyclerView: RecyclerView
    ) {
        val filtered = repository.filterPodcasts(allPodcasts, currentFilter)
        adapter.updatePodcasts(filtered)

        if (filtered.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fragmentScope.coroutineContext[Job]?.cancel()
    }
}
