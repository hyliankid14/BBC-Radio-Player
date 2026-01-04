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

class StationAdapter(
    private val context: Context,
    private val stations: List<Station>,
    private val onStationClick: (String) -> Unit,
    private val onFavoriteToggle: ((String) -> Unit)? = null
) : RecyclerView.Adapter<StationAdapter.StationViewHolder>() {
    
    private val adapterScope = CoroutineScope(Dispatchers.Main + Job())
    private val showCache = mutableMapOf<String, String>()
    private val fetchingIds = mutableSetOf<String>()

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
        val cachedShow = showCache[station.id]
        if (cachedShow != null) {
            holder.subtitleView.text = cachedShow
            holder.subtitleView.visibility = View.VISIBLE
        } else {
            holder.subtitleView.visibility = View.GONE
            if (!fetchingIds.contains(station.id)) {
                fetchingIds.add(station.id)
                adapterScope.launch {
                    try {
                        val show = withContext(Dispatchers.IO) {
                            ShowInfoFetcher.getCurrentShow(station.id)
                        }
                        val showTitle = show.getFormattedTitle()
                        // Only show if it's not the generic default
                        if (showTitle != "BBC Radio") {
                            showCache[station.id] = showTitle
                            // Update the item if it's still visible/bound to the same position
                            val currentPos = holder.bindingAdapterPosition
                            if (currentPos != RecyclerView.NO_POSITION && currentPos < stations.size && stations[currentPos].id == station.id) {
                                notifyItemChanged(currentPos)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore errors
                    } finally {
                        fetchingIds.remove(station.id)
                    }
                }
            }
        }
        
        Glide.with(context)
            .load(station.logoUrl)
            .into(holder.imageView)
        
        // Update star icon
        val isFavorite = FavoritesPreference.isFavorite(context, station.id)
        holder.starView.setImageResource(
            if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
        if (isFavorite) {
            holder.starView.setColorFilter(Color.parseColor("#FFC107"))
        } else {
            holder.starView.clearColorFilter()
        }
        
        holder.starView.setOnClickListener {
            FavoritesPreference.toggleFavorite(context, station.id)
            holder.starView.setImageResource(
                if (FavoritesPreference.isFavorite(context, station.id))
                    R.drawable.ic_star_filled
                else
                    R.drawable.ic_star_outline
            )
            val nowFavorite = FavoritesPreference.isFavorite(context, station.id)
            if (nowFavorite) {
                holder.starView.setColorFilter(Color.parseColor("#FFC107"))
            } else {
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
