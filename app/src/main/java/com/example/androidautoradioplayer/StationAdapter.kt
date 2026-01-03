package com.example.androidautoradioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide

class StationAdapter(
    context: Context,
    private val stations: List<Station>,
    private val onStationClick: (String) -> Unit,
    private val onFavoriteToggle: ((String) -> Unit)? = null
) : ArrayAdapter<Station>(context, 0, stations) {
    
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.station_list_item,
            parent,
            false
        )
        
        val station = getItem(position) ?: return view
        
        val imageView: ImageView = view.findViewById(R.id.station_artwork)
        val textView: TextView = view.findViewById(R.id.station_title)
        val starView: ImageView = view.findViewById(R.id.station_favorite_star)
        
        textView.text = station.title
        
        Glide.with(context)
            .load(station.logoUrl)
            .into(imageView)
        
        // Update star icon
        val isFavorite = FavoritesPreference.isFavorite(context, station.id)
        starView.setImageResource(if (isFavorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
        
        starView.setOnClickListener {
            FavoritesPreference.toggleFavorite(context, station.id)
            starView.setImageResource(
                if (FavoritesPreference.isFavorite(context, station.id))
                    android.R.drawable.btn_star_big_on
                else
                    android.R.drawable.btn_star_big_off
            )
            onFavoriteToggle?.invoke(station.id)
        }
        
        // Make image and text clickable to play
        val clickListener = View.OnClickListener { onStationClick(station.id) }
        imageView.setOnClickListener(clickListener)
        textView.setOnClickListener(clickListener)
        
        return view
    }
}
