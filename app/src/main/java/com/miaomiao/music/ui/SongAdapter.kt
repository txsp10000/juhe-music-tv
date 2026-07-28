package com.miaomiao.music.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.miaomiao.music.R
import com.miaomiao.music.model.Song

class SongAdapter(
    private val songs: MutableList<Song>,
    private val onPlay: (Int) -> Unit,
    private val showFavButton: Boolean = true,
    private var showDeleteButton: Boolean = false,
    private val showSource: Boolean = false,
    private val onDelete: ((Int) -> Unit)? = null,
    private val onLongPress: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<SongAdapter.VH>() {

    private var playingIndex = -1
    var onFavoriteToggle: ((Song) -> Unit)? = null
    private val favoriteStates = mutableMapOf<Int, Boolean>()

    fun setShowDeleteButton(show: Boolean) {
        showDeleteButton = show
        notifyDataSetChanged()
    }

    fun setPlaying(idx: Int) {
        val old = playingIndex
        playingIndex = idx
        if (old >= 0 && old < songs.size) notifyItemChanged(old)
        if (idx >= 0 && idx < songs.size) notifyItemChanged(idx)
    }

    fun getPlayingIndex(): Int = playingIndex

    fun setFavoriteStates(states: Map<Int, Boolean>) {
        favoriteStates.clear()
        favoriteStates.putAll(states)
        notifyDataSetChanged()
    }

    fun isFavoriteAt(position: Int): Boolean = favoriteStates[position] ?: false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val song = songs[position]
        holder.tvIndex.text = "${position + 1}"
        holder.tvName.text = song.name
        holder.tvArtist.text = song.singer

        if (showSource) {
            holder.tvSource.visibility = View.VISIBLE
            holder.tvSource.text = "网易云"
            holder.tvSource.setTextColor(0xFFE91E63.toInt())
        } else {
            holder.tvSource.visibility = View.GONE
        }

        holder.tvBitrate.visibility = View.GONE

        val ctx = holder.itemView.context
        val textColor = if (position == playingIndex) {
            ContextCompat.getColor(ctx, R.color.accent)
        } else {
            ContextCompat.getColor(ctx, R.color.text_primary)
        }
        holder.tvIndex.setTextColor(textColor)
        holder.tvName.setTextColor(textColor)

        holder.btnFav.visibility = if (showFavButton) View.VISIBLE else View.GONE
        val isFav = favoriteStates[position] ?: false
        holder.btnFav.text = if (isFav) "♥" else "♡"
        holder.btnFav.setTextColor(if (isFav) 0xFFFF4444.toInt() else 0xFFFFFFFF.toInt())
        holder.btnFav.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) onFavoriteToggle?.invoke(songs[pos])
        }

        holder.btnDelete.visibility = if (showDeleteButton) View.VISIBLE else View.GONE
        holder.btnDelete.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) onDelete?.invoke(pos)
        }

        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) onPlay(pos)
        }
        holder.itemView.setOnLongClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION && onLongPress != null) {
                onLongPress.invoke(pos)
                true
            } else false
        }
    }

    override fun getItemCount(): Int = songs.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvIndex: TextView = v.findViewById(R.id.tv_index)
        val tvName: TextView = v.findViewById(R.id.tv_name)
        val tvArtist: TextView = v.findViewById(R.id.tv_artist)
        val tvBitrate: TextView = v.findViewById(R.id.tv_bitrate)
        val tvSource: TextView = v.findViewById(R.id.tv_source)
        val btnFav: TextView = v.findViewById(R.id.btn_fav)
        val btnDelete: TextView = v.findViewById(R.id.btn_delete)
    }
}
