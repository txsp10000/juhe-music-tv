package com.miaomiao.music.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.miaomiao.music.databinding.ActivityPlaylistBinding
import com.miaomiao.music.model.Song

class PlaylistActivity : FragmentActivity() {
    private lateinit var binding: ActivityPlaylistBinding
    private val songs = mutableListOf<Song>()
    private lateinit var adapter: SongAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SongAdapter(
            songs = songs,
            onPlay = { idx ->
                PlayerManager.playAt(idx)
                finish()
            },
            showFavButton = false,
            showDeleteButton = false,
            onLongPress = { idx ->
                PlayerManager.removeAt(idx)
                syncList()
            }
        )
        binding.rvPlaylist.layoutManager = LinearLayoutManager(this)
        binding.rvPlaylist.adapter = adapter

        syncList()
        binding.rvPlaylist.post {
            val idx = PlayerManager.currentIndex
            if (idx >= 0) {
                val lm = binding.rvPlaylist.layoutManager as LinearLayoutManager
                lm.scrollToPositionWithOffset(idx, binding.rvPlaylist.height / 3)
                binding.rvPlaylist.post {
                    val view = binding.rvPlaylist.findViewHolderForAdapterPosition(idx)?.itemView
                    view?.requestFocus()
                }
            }
        }
    }

    private fun syncList() {
        songs.clear()
        songs.addAll(PlayerManager.playlist)
        adapter.setPlaying(PlayerManager.currentIndex)
        adapter.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        syncList()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
