package com.miaomiao.music.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.miaomiao.music.R
import com.miaomiao.music.databinding.ActivityFavoritesBinding
import com.miaomiao.music.model.FavoritesManager

class FavoritesActivity : FragmentActivity() {
    private lateinit var binding: ActivityFavoritesBinding
    private val songs = mutableListOf<com.miaomiao.music.model.Song>()
    private lateinit var adapter: SongAdapter
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SongAdapter(
            songs = songs,
            onPlay = { idx ->
                if (isEditMode) {
                    // 编辑模式：点击直接删除
                    val song = songs[idx]
                    FavoritesManager.remove(this@FavoritesActivity, song)
                    songs.removeAt(idx)
                    adapter.notifyItemRemoved(idx)
                    adapter.notifyItemRangeChanged(idx, songs.size)
                    updateTitle()
                    if (songs.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.rvFavorites.visibility = View.GONE
                        exitEditMode()
                    }
                } else {
                    PlayerManager.play(songs.toList(), idx)
                    startActivity(Intent(this@FavoritesActivity, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
            },
            showFavButton = false,
            showDeleteButton = false,
            onLongPress = { _ ->
                toggleEditMode()
            }
        )
        binding.rvFavorites.layoutManager = LinearLayoutManager(this)
        binding.rvFavorites.adapter = adapter

        loadFavorites()

        // 自动滚动到正在播放的歌曲并聚焦
        binding.rvFavorites.post {
            val idx = adapter.getPlayingIndex()
            if (idx >= 0) {
                val lm = binding.rvFavorites.layoutManager as LinearLayoutManager
                lm.scrollToPositionWithOffset(idx, binding.rvFavorites.height / 3)
                binding.rvFavorites.post {
                    val view = binding.rvFavorites.findViewHolderForAdapterPosition(idx)?.itemView
                    view?.requestFocus()
                }
            }
        }
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode

        if (isEditMode) {
            binding.tvTitle.text = "编辑模式 · ${songs.size}首 (点一下即可删除)"
            binding.tvTitle.setTextColor(ContextCompat.getColor(this, R.color.accent))
        } else {
            updateTitle()
            binding.tvTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    private fun exitEditMode() {
        if (isEditMode) {
            isEditMode = false
            updateTitle()
            binding.tvTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    override fun onResume() {
        super.onResume()
        syncPlayingState()
    }

    private fun syncPlayingState() {
        val currentSong = PlayerManager.currentSong
        val idx = if (currentSong != null) songs.indexOfFirst { it.id == currentSong.id } else -1
        adapter.setPlaying(idx)
    }

    private fun loadFavorites() {
        songs.clear()
        songs.addAll(FavoritesManager.getAll(this))
        updateTitle()
        if (songs.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvFavorites.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvFavorites.visibility = View.VISIBLE
        }
        val states = mutableMapOf<Int, Boolean>()
        for (i in songs.indices) states[i] = true
        adapter.setFavoriteStates(states)
        syncPlayingState()
    }

    private fun updateTitle() {
        val count = songs.size
        binding.tvTitle.text = "我的收藏${if (count > 0) " (${count}首)" else ""}"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isEditMode) {
                exitEditMode()
            } else {
                finish()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}


