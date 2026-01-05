package com.example.androidautoradioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageView
import android.widget.TextView
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import kotlinx.coroutines.*

class CategorizedStationAdapter(
    private val context: Context,
    private val stations: List<Station>,
    private val onStationClick: (String) -> Unit,
    private val onFavoriteToggle: ((String) -> Unit)? = null
) : RecyclerView.Adapter<CategorizedStationAdapter.StationViewHolder>() {
    
    private val adapterScope = CoroutineScope(Dispatchers.Main + Job())
    private val showCache = mutableMapOf<String, Pair<String, Long>>()
    private val fetchingIds = mutableSetOf<String>()
    private val CACHE_DURATION_MS = 120_000L // 2 minutes
    
    class StationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.station_artwork)
        val textView: TextView = view.findViewById(R.id.station_title)
        val subtitleView: TextView = view.findViewById(R.id.station_subtitle)
        val starView: ImageView = view.findViewById(R.id.station_favorite_star)
    }
    
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterScope.cancel()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.station_list_item,
            parent,
            false
        )
        return StationViewHolder(view)
    }
    
    override fun getItemCount(): Int = stations.size
    
    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = stations[position]
        
        holder.textView.text = station.title
        
        // Handle subtitle (Show Name)
        val cachedEntry = showCache[station.id]
        val isCacheValid = cachedEntry != null && (System.currentTimeMillis() - cachedEntry.second < CACHE_DURATION_MS)
        
        if (isCacheValid && cachedEntry != null) {
            holder.subtitleView.text = cachedEntry.first
            holder.subtitleView.visibility = View.VISIBLE
        } else {
            holder.subtitleView.visibility = View.GONE
            
            if (!fetchingIds.contains(station.id)) {
                fetchingIds.add(station.id)
                adapterScope.launch {
                    try {
                        val show = ShowInfoFetcher.getScheduleCurrentShow(station.id)
                        if (show.title.isNotEmpty()) {
                            showCache[station.id] = Pair(show.title, System.currentTimeMillis())
                            holder.subtitleView.text = show.title
                            holder.subtitleView.visibility = View.VISIBLE
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
            .load(ImageLoader.getGlideUrl(station.logoUrl))
            .into(holder.imageView)
        
        // Handle favorite star
        val isFavorite = FavoritesPreference.isFavorite(context, station.id)
        if (isFavorite) {
            holder.starView.setImageResource(R.drawable.ic_star_filled)
            holder.starView.setColorFilter(ContextCompat.getColor(context, R.color.favorite_star_color))
        } else {
            holder.starView.setImageResource(R.drawable.ic_star_outline)
            holder.starView.clearColorFilter()
        }
        
        holder.starView.setOnClickListener {
            FavoritesPreference.toggleFavorite(context, station.id)
            val nowFavorite = FavoritesPreference.isFavorite(context, station.id)
            if (nowFavorite) {
                holder.starView.setImageResource(R.drawable.ic_star_filled)
                holder.starView.setColorFilter(ContextCompat.getColor(context, R.color.favorite_star_color))
            } else {
                holder.starView.setImageResource(R.drawable.ic_star_outline)
                holder.starView.clearColorFilter()
            }
            onFavoriteToggle?.invoke(station.id)
        }
        
        // Make image and text clickable to play
        val clickListener = View.OnClickListener { onStationClick(station.id) }
        holder.imageView.setOnClickListener(clickListener)
        holder.textView.setOnClickListener(clickListener)
        holder.itemView.setOnClickListener(clickListener)
    }
}
