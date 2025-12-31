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
    private val onStationClick: (String) -> Unit
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
        
        textView.text = station.title
        
        Glide.with(context)
            .load(station.logoUrl)
            .into(imageView)
        
        // Make both image and text clickable
        val clickListener = View.OnClickListener { onStationClick(station.id) }
        imageView.setOnClickListener(clickListener)
        textView.setOnClickListener(clickListener)
        view.setOnClickListener(clickListener)
        
        return view
    }
}
