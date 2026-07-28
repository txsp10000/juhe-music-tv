package com.miaomiao.music.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.miaomiao.music.R
import com.miaomiao.music.api.MusicApi
import com.miaomiao.music.databinding.ActivitySearchResultBinding
import com.miaomiao.music.model.FavoritesManager
import com.miaomiao.music.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SearchResultActivity : FragmentActivity() {
    private lateinit var binding: ActivitySearchResultBinding
    private val scope = CoroutineScope(Dispatchers.Main)
    private var searchJob: Job? = null

    private val songList = mutableListOf<Song>()
    private lateinit var songAdapter: SongAdapter

    private var keyword = ""
    private var searchCount = 20
    private var searchSource = "netease"
    private var currentPage = 1
    private var hasMore = true
    private var isLoadingMore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        keyword = intent.getStringExtra("keyword") ?: ""
        searchCount = intent.getIntExtra("count", 20)
        searchSource = intent.getStringExtra("source") ?: "netease"
        setupSongList()
        doSearch()
    }

    private fun doSearch() {
        currentPage = 1
        hasMore = true
        searchAndLoad(append = false)
    }

    private fun loadNextPage() {
        if (!hasMore || isLoadingMore) return
        isLoadingMore = true
        currentPage++
        // 显示全屏遮罩
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.loadingOverlay.requestFocus()
        searchAndLoad(append = true)
    }

    private fun searchAndLoad(append: Boolean) {
        searchJob?.cancel()

        if (!append) {
            songList.clear()
            binding.tvTitle.text = "搜索中..."
            binding.tvCount.text = ""
            binding.tvLoading.visibility = View.VISIBLE
            binding.rvSongs.visibility = View.GONE
            binding.tvEmpty.visibility = View.GONE
        }

        searchJob = scope.launch {
            val result = try {
                MusicApi.searchRaw(keyword, searchCount, searchSource, currentPage)
            } catch (e: Exception) {
                MusicApi.SearchResult(emptyList(), "")
            }

            // 隐藏全屏遮罩
            binding.loadingOverlay.visibility = View.GONE
            isLoadingMore = false

            if (result.songs.isNotEmpty()) {
                val oldSize = songList.size
                if (!append) {
                    songList.clear()
                }
                songList.addAll(result.songs)
                hasMore = result.songs.size >= searchCount

                // 后台加载封面（使用 pic_id）
                launch(Dispatchers.IO) {
                    val covers = MusicApi.getCovers(songList.map { it.picId.ifEmpty { it.id } })
                    songList.forEach { it.coverUrl = covers[it.picId.ifEmpty { it.id }] ?: "" }
                    runOnUiThread { songAdapter.notifyItemRangeChanged(0, songList.size) }
                }

                refreshFavoriteStates()
                songAdapter.notifyDataSetChanged()
                binding.tvTitle.text = "搜索结果"
                binding.tvCount.text = "${songList.size} 首"
                binding.tvLoading.visibility = View.GONE
                binding.rvSongs.visibility = View.VISIBLE
                if (!append) {
                    binding.rvSongs.post { binding.rvSongs.getChildAt(0)?.requestFocus() }
                } else {
                    // 加载完成后聚焦第一条新数据
                    binding.rvSongs.post {
                        val lm = binding.rvSongs.layoutManager as? LinearLayoutManager ?: return@post
                        lm.scrollToPosition(oldSize)
                        binding.rvSongs.post {
                            val firstNew = lm.findViewByPosition(oldSize)
                            firstNew?.requestFocus()
                        }
                    }
                }
            } else {
                hasMore = false
                if (!append) {
                    binding.tvTitle.text = "搜索失败"
                    binding.tvCount.text = ""
                    binding.tvLoading.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "重试30次后仍无结果\nAPI返回:\n${result.rawBody.ifEmpty { "空列表" }.take(200)}"
                }
            }
        }
    }

    private fun refreshFavoriteStates() {
        val states = mutableMapOf<Int, Boolean>()
        for (i in songList.indices) {
            states[i] = FavoritesManager.isFavorite(this, songList[i])
        }
        songAdapter.setFavoriteStates(states)
    }

    private fun setupSongList() {
        songAdapter = SongAdapter(
            songs = songList,
            onPlay = { idx ->
                PlayerManager.play(songList.toList(), idx)
                startActivity(Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            },
            showFavButton = false,
            showSource = false
        )
        binding.rvSongs.layoutManager = LinearLayoutManager(this)
        binding.rvSongs.adapter = songAdapter

        // 滚动到最后一条时自动加载下一页
        binding.rvSongs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!hasMore || isLoadingMore) return
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = lm.findLastVisibleItemPosition()
                if (lastVisible >= 0 && lastVisible >= songList.size - 1) {
                    loadNextPage()
                }
            }
        })
    }

    private fun showToast(msg: String) {
        val toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT)
        toast.setGravity(android.view.Gravity.CENTER, 0, 0)
        toast.show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        if (isLoadingMore) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
