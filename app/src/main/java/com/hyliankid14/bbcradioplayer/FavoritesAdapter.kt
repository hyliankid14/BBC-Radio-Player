package com.hyliankid14.bbcradioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageView
import android.widget.TextView
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import kotlinx.coroutines.*

class FavoritesAdapter(
    private val context: Context,
    private val stations: MutableList<Station>,
    private val onStationClick: (String) -> Unit,
    private val onFavoriteToggle: ((String) -> Unit)? = null,
    private val onOrderChanged: () -> Unit = {}
) : RecyclerView.Adapter<FavoritesAdapter.StationViewHolder>() {
    
    private val adapterScope = CoroutineScope(Dispatchers.Main + Job())
    private val showCache = mutableMapOf<String, Pair<String, Long>>()
    private val fetchingIds = mutableSetOf<String>()
    private val CACHE_DURATION_MS = 120_000L // 2 minutes

    // Hook provided by the host to start a drag operation (ItemTouchHelper.startDrag)
    var onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null

    class StationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.station_artwork)
        val textView: TextView = view.findViewById(R.id.station_title)
        val subtitleView: TextView = view.findViewById(R.id.station_subtitle)
        val starView: ImageView = view.findViewById(R.id.station_favorite_star)
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
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
        
        // Show drag handle in favorites mode
        holder.dragHandle.visibility = View.VISIBLE
        
        // Handle subtitle (Show Name)
        val cachedEntry = showCache[station.id]
        val isCacheValid = cachedEntry != null && (System.currentTimeMillis() - cachedEntry.second < CACHE_DURATION_MS)
        
        if (isCacheValid) {
            holder.subtitleView.text = cachedEntry!!.first
            holder.subtitleView.visibility = View.VISIBLE
        } else {
            if (cachedEntry != null) {
                holder.subtitleView.text = cachedEntry.first
                holder.subtitleView.visibility = View.VISIBLE
            } else {
                holder.subtitleView.visibility = View.GONE
            }
            
            if (!fetchingIds.contains(station.id)) {
                fetchingIds.add(station.id)
                adapterScope.launch {
                    try {
                        val show = ShowInfoFetcher.getScheduleCurrentShow(station.id)
                        val showTitle = show.title
                        if (showTitle != "BBC Radio") {
                            showCache[station.id] = Pair(showTitle, System.currentTimeMillis())
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
        
        // Safer tap handling: gate clicks by movement slop; keep scrolling responsive
        val tapSlop = (12 * context.resources.displayMetrics.density).toInt()
        fun attachTapGuard(view: View, click: () -> Unit) {
            var downX = 0f
            var downY = 0f
            var movedBeyondSlop = false
            view.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        movedBeyondSlop = false
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = Math.abs(event.x - downX)
                        val dy = Math.abs(event.y - downY)
                        if (dx > tapSlop || dy > tapSlop) movedBeyondSlop = true
                    }
                }
                false
            }
            view.setOnClickListener {
                if (!movedBeyondSlop) click()
            }
        }
        attachTapGuard(holder.itemView) { onStationClick(station.id) }
        attachTapGuard(holder.imageView) { onStationClick(station.id) }
        attachTapGuard(holder.textView) { onStationClick(station.id) }

        // Long-pressing the station title should start a drag (bringing the item to foreground)
        // Use a GestureDetector to detect the long-press and allow the same pointer to continue dragging
        val gestureDetector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: android.view.MotionEvent) {
                // Give tactile feedback and prevent parent from intercepting while dragging
                holder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                (holder.itemView.parent as? ViewParent)?.requestDisallowInterceptTouchEvent(true)
                onStartDrag?.invoke(holder)
            }

            override fun onDown(e: android.view.MotionEvent): Boolean = true
        })

        holder.textView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            // Don't consume - allow RecyclerView to receive subsequent MOVE events to drive the drag
            false
        }

        // Touching the drag handle should also start the drag immediately on press. Return false
        // so the pointer remains active and MOVE events get delivered for a smooth drag.
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                onStartDrag?.invoke(holder)
            }
            false
        }
    }
    
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                val temp = stations[i]
                stations[i] = stations[i + 1]
                stations[i + 1] = temp
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                val temp = stations[i]
                stations[i] = stations[i - 1]
                stations[i - 1] = temp
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        onOrderChanged()
    }
    
    fun clearShowCache() {
        showCache.clear()
        fetchingIds.clear()
    }
}
