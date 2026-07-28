package com.miaomiao.music.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miaomiao.music.R
import com.miaomiao.music.model.SearchHistoryItem

class HistoryAdapter(
    private val items: List<SearchHistoryItem>,
    private val onClick: (String) -> Unit,
    private val onLongPress: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvKeyword.text = item.keyword
        holder.tvPlatform.visibility = View.GONE

        // 隐藏删除按钮
        holder.btnDelete.visibility = View.GONE

        holder.itemView.setOnClickListener {
            onClick(item.keyword)
        }

        holder.itemView.setOnLongClickListener {
            onLongPress?.invoke(position)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvKeyword: TextView = v.findViewById(R.id.tv_keyword)
        val tvPlatform: TextView = v.findViewById(R.id.tv_platform)
        val btnDelete: TextView = v.findViewById(R.id.btn_delete)
    }
}
