package com.example.androidautoradioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageView
import android.widget.TextView
import android.graphics.Color
import com.bumptech.glide.Glide
import kotlinx.coroutines.*

class CategorizedStationAdapter(
    private val context: Context,
    private val categorizedStations: Map<StationCategory, List<Station>>,
    private val onStationClick: (String) -> Unit,
    private val onFavoriteToggle: ((String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    private val adapterScope = CoroutineScope(Dispatchers.Main + Job())
    private val showCache = mutableMapOf<String, Pair<String, Long>>()
    private val fetchingIds = mutableSetOf<String>()
    private val CACHE_DURATION_MS = 120_000L // 2 minutes
    
    private val items = mutableListOf<AdapterItem>()
    
    sealed class AdapterItem {
        data class CategoryHeader(val category: StationCategory) : AdapterItem()
        data class StationItem(val station: Station) : AdapterItem()
    }
    
    private val VIEW_TYPE_HEADER = 0
    private val VIEW_TYPE_STATION = 1
    
    class StationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.station_artwork)
        val textView: TextView = view.findViewById(R.id.station_title)
        val subtitleView: TextView = view.findViewById(R.id.station_subtitle)
        val starView: ImageView = view.findViewById(R.id.station_favorite_star)
    }
    
    class CategoryHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryTitle: TextView = view.findViewById(R.id.category_title)
    }
    
    init {
        // Build the items list with categories and stations
        val categoryOrder = listOf(StationCategory.NATIONAL, StationCategory.REGIONS, StationCategory.LOCAL)
        for (category in categoryOrder) {
            categorizedStations[category]?.let { stationList ->
                if (stationList.isNotEmpty()) {
                    items.add(AdapterItem.CategoryHeader(category))
                    items.addAll(stationList.map { AdapterItem.StationItem(it) })
                }
            }
        }
    }
    
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterScope.cancel()
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AdapterItem.CategoryHeader -> VIEW_TYPE_HEADER
            is AdapterItem.StationItem -> VIEW_TYPE_STATION
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(
                    R.layout.station_category_header,
                    parent,
                    false
                )
                CategoryHeaderViewHolder(view)
            }
            VIEW_TYPE_STATION -> {
                val view = LayoutInflater.from(parent.context).inflate(
                    R.layout.station_list_item,
                    parent,
                    false
                )
                StationViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }
    
    override fun getItemCount(): Int = items.size
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is AdapterItem.CategoryHeader -> {
                val headerHolder = holder as CategoryHeaderViewHolder
                headerHolder.categoryTitle.text = item.category.displayName
            }
            is AdapterItem.StationItem -> {
                val stationHolder = holder as StationViewHolder
                val station = item.station
                
                stationHolder.textView.text = station.title
                
                // Handle subtitle (Show Name)
                val cachedEntry = showCache[station.id]
                val isCacheValid = cachedEntry != null && (System.currentTimeMillis() - cachedEntry.second < CACHE_DURATION_MS)
                
                if (isCacheValid) {
                    stationHolder.subtitleView.text = cachedEntry.first
                    stationHolder.subtitleView.visibility = View.VISIBLE
                } else {
                    stationHolder.subtitleView.visibility = View.GONE
                    
                    if (!fetchingIds.contains(station.id)) {
                        fetchingIds.add(station.id)
                        adapterScope.launch {
                            try {
                                val show = ShowInfoFetcher.getScheduleCurrentShow(station.id)
                                if (show.title.isNotEmpty()) {
                                    showCache[station.id] = Pair(show.title, System.currentTimeMillis())
                                    stationHolder.subtitleView.text = show.title
                                    stationHolder.subtitleView.visibility = View.VISIBLE
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("CategorizedStationAdapter", "Failed to fetch show info for ${station.id}", e)
                            } finally {
                                fetchingIds.remove(station.id)
                            }
                        }
                    }
                }
                
                // Load image
                Glide.with(context)
                    .load(station.logoUrl)
                    .into(stationHolder.imageView)
                
                // Handle favorite star
                stationHolder.starView.setImageResource(R.drawable.ic_star)
                val isFavorite = FavoritesPreference.isFavorite(context, station.id)
                if (isFavorite) {
                    stationHolder.starView.setColorFilter(Color.parseColor("#FFC107"))
                } else {
                    stationHolder.starView.clearColorFilter()
                }
                
                stationHolder.starView.setOnClickListener {
                    FavoritesPreference.toggleFavorite(context, station.id)
                    val nowFavorite = FavoritesPreference.isFavorite(context, station.id)
                    if (nowFavorite) {
                        stationHolder.starView.setColorFilter(Color.parseColor("#FFC107"))
                    } else {
                        stationHolder.starView.clearColorFilter()
                    }
                    onFavoriteToggle?.invoke(station.id)
                }
                
                // Make image and text clickable to play
                val clickListener = View.OnClickListener { onStationClick(station.id) }
                stationHolder.imageView.setOnClickListener(clickListener)
                stationHolder.textView.setOnClickListener(clickListener)
                stationHolder.itemView.setOnClickListener(clickListener)
            }
        }
    }
    
    fun getPositionForCategory(category: StationCategory): Int {
        for (i in items.indices) {
            if (items[i] is AdapterItem.CategoryHeader && 
                (items[i] as AdapterItem.CategoryHeader).category == category) {
                return i
            }
        }
        return -1
    }
}
