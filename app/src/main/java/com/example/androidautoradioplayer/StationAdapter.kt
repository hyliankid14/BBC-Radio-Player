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

class StationAdapter(
    private val context: Context,
    private val stations: List<Station>,
    private val onStationClick: (String) -> Unit,
    private val onFavoriteToggle: ((String) -> Unit)? = null
) : RecyclerView.Adapter<StationAdapter.StationViewHolder>() {
    
    class StationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.station_artwork)
        val textView: TextView = view.findViewById(R.id.station_title)
        val starView: ImageView = view.findViewById(R.id.station_favorite_star)
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
