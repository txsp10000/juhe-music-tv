package com.miaomiao.music.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.miaomiao.music.R
import com.miaomiao.music.databinding.ActivityPlayerBinding
import com.miaomiao.music.model.FavoritesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

class PlayerActivity : FragmentActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private val scope = CoroutineScope(Dispatchers.Main)

    private var isFavorite = false
    private var currentPositionMs = 0

    // 歌词解析
    private data class LyricLine(val timeMs: Int, val text: String)
    private var lyricLines: List<LyricLine> = emptyList()
    private var currentLyricIndex = -1
    private var songDurationMs = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        registerPlayerCallbacks()
        setupFavoriteButton()
        setupPlaylistButton()
        setupPlayPauseButton()
        setupPrevNextButtons()
        setupSearchSameButton()
        setupSeekBar()
        syncAllUI()
        binding.seekBarContainer.requestFocus()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        lyricLines = emptyList()
        currentLyricIndex = -1
        syncAllUI()
    }

    override fun onResume() {
        super.onResume()
        registerPlayerCallbacks()
        syncAllUI()
    }

    private fun registerPlayerCallbacks() {
        PlayerManager.onStateChanged = { _, _ -> runOnUiThread { syncAllUI() } }
        PlayerManager.onProgress = { current, total ->
            runOnUiThread {
                currentPositionMs = current
                songDurationMs = total
                if (total > 0) {
                    binding.seekBar.max = total
                    binding.seekBar.progress = current
                    binding.tvTimeCurrent.text = formatTime(current)
                    binding.tvTimeTotal.text = formatTime(total)
                }
                syncLyricByTime(current)
            }
        }
        PlayerManager.onError = { msg ->
            runOnUiThread {
                showToast(msg)
                if (binding.tvLyricCurrent.text.isEmpty() || binding.tvLyricCurrent.text == "歌词加载中...") {
                    binding.tvLyricCurrent.text = msg
                }
            }
        }
        PlayerManager.onLyric = { lyric ->
            runOnUiThread { parseLyric(lyric) }
        }
    }

    /** 解析歌词，自动识别 LRC 或纯文本格式 */
    private fun parseLyric(raw: String, fromSongDurationMs: Long = 0L) {
        if (raw.isBlank() || raw == "纯音乐，请欣赏") {
            binding.tvLyricCurrent.text = "纯音乐，请欣赏"
            binding.tvLyricAbove2.text = ""
            binding.tvLyricAbove.text = ""
            binding.tvLyricBelow.text = ""
            binding.tvLyricBelow2.text = ""
            binding.tvLyricBelow3.text = ""
            lyricLines = emptyList()
            return
        }

        val lines = raw.lines().filter { it.isNotBlank() }
        val lrcPattern = Regex("""^\[(\d{2}):(\d{2})(?:\.(\d{1,3}))?\](.*)""")
        val hasLrc = lines.any { lrcPattern.matches(it) }

        if (hasLrc) {
            val parsed = mutableListOf<LyricLine>()
            for (line in lines) {
                val match = lrcPattern.find(line) ?: continue
                val min = match.groupValues[1].toIntOrNull() ?: 0
                val sec = match.groupValues[2].toIntOrNull() ?: 0
                val msStr = match.groupValues[3]
                val ms = if (msStr.isNotEmpty()) {
                    (msStr.toIntOrNull() ?: 0) * (if (msStr.length == 2) 10 else if (msStr.length == 3) 1 else 100)
                } else 0
                val timeMs = min * 60_000 + sec * 1_000 + ms
                val text = match.groupValues[4].trim()
                if (text.isNotBlank()) parsed.add(LyricLine(timeMs, text))
            }
            lyricLines = parsed.sortedBy { it.timeMs }
        } else {
            val totalLines = lines.size
            val baseDuration = if (songDurationMs > 0) songDurationMs
                else if (fromSongDurationMs > 0) (fromSongDurationMs * 1000).toInt()
                else 0
            val estimatedDuration = baseDuration.coerceAtLeast(totalLines * 3000)
            lyricLines = lines.mapIndexed { idx, text ->
                val timeMs = if (totalLines > 1) (estimatedDuration.toLong() * idx / totalLines).toInt() else 0
                LyricLine(timeMs, text.trim())
            }
        }

        currentLyricIndex = -1
        syncLyricByTime(currentPositionMs)
    }

    private fun syncLyricByTime(positionMs: Int) {
        if (lyricLines.isEmpty()) return
        var idx = lyricLines.indexOfLast { it.timeMs <= positionMs }
        if (idx < 0) idx = 0
        if (idx == currentLyricIndex) return
        currentLyricIndex = idx

        val current = lyricLines[idx].text
        val above1 = if (idx > 0) lyricLines[idx - 1].text else ""
        val above2 = if (idx > 1) lyricLines[idx - 2].text else ""
        val below1 = if (idx < lyricLines.size - 1) lyricLines[idx + 1].text else ""
        val below2 = if (idx < lyricLines.size - 2) lyricLines[idx + 2].text else ""
        val below3 = if (idx < lyricLines.size - 3) lyricLines[idx + 3].text else ""

        binding.tvLyricAbove2.text = above2
        binding.tvLyricAbove.text = above1
        binding.tvLyricCurrent.text = current
        binding.tvLyricBelow.text = below1
        binding.tvLyricBelow2.text = below2
        binding.tvLyricBelow3.text = below3
    }

    private fun syncAllUI() {
        val song = PlayerManager.currentSong ?: return
        binding.tvSongName.text = song.name
        binding.tvSongArtist.text = "${song.singer} · ${song.album}"
        binding.tvCurrentQuality.text = getRealQualityLabel()

        // 加载专辑封面
        val coverUrl = song.coverUrl
        if (coverUrl.isNotEmpty()) {
            binding.ivCover.visibility = android.view.View.VISIBLE
            binding.coverTextArea.visibility = android.view.View.GONE
            Glide.with(this)
                .load(coverUrl)
                .placeholder(R.drawable.bg_func_card)
                .into(binding.ivCover)
        } else {
            binding.ivCover.visibility = android.view.View.GONE
            binding.coverTextArea.visibility = android.view.View.VISIBLE
        }

        if (lyricLines.isEmpty()) {
            val songLyric = song.lyric
            if (songLyric.isNotBlank()) {
                parseLyric(songLyric)
            } else if (binding.tvLyricCurrent.text.isEmpty() || binding.tvLyricCurrent.text == "歌词加载中...") {
                binding.tvLyricCurrent.text = "歌词加载中..."
                binding.tvLyricAbove2.text = ""
                binding.tvLyricAbove.text = ""
                binding.tvLyricBelow.text = ""
                binding.tvLyricBelow2.text = ""
                binding.tvLyricBelow3.text = ""
            }
        }
        syncPlayPauseIcon()
        syncFavoriteIcon()
    }

    private fun syncPlayPauseIcon() {
        binding.btnPlayPause.setImageResource(
            if (PlayerManager.isPlaying) R.drawable.ic_pause_dark else R.drawable.ic_play_dark
        )
    }

    private fun setupFavoriteButton() {
        binding.btnFavorite.setOnClickListener {
            val song = PlayerManager.currentSong ?: return@setOnClickListener
            if (isFavorite) {
                FavoritesManager.remove(this, song)
                isFavorite = false
                showToast("已取消收藏")
            } else {
                FavoritesManager.save(this, song)
                isFavorite = true
                showToast("已加入收藏")
            }
            syncFavoriteIcon()
        }
    }

    private fun syncFavoriteIcon() {
        val song = PlayerManager.currentSong ?: return
        isFavorite = FavoritesManager.isFavorite(this, song)
        binding.ivFavoriteIcon.setImageResource(
            if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        )
    }

    private fun setupPlaylistButton() {
        binding.btnShowPlaylist.setOnClickListener {
            startActivity(Intent(this, PlaylistActivity::class.java))
        }
    }

    private fun setupPlayPauseButton() {
        binding.btnPlayPauseContainer.setOnClickListener {
            PlayerManager.togglePlayPause()
            syncPlayPauseIcon()
        }
    }

    private fun setupPrevNextButtons() {
        binding.btnPrev.setOnClickListener {
            PlayerManager.prev()
            lyricLines = emptyList()
            currentLyricIndex = -1
            syncAllUI()
        }
        binding.btnNext.setOnClickListener {
            PlayerManager.next()
            lyricLines = emptyList()
            currentLyricIndex = -1
            syncAllUI()
        }
    }

    private fun setupSearchSameButton() {
        binding.btnSearchSame.setOnClickListener {
            val song = PlayerManager.currentSong ?: return@setOnClickListener
            val view = layoutInflater.inflate(R.layout.dialog_search_same, null)
            val dialog = AlertDialog.Builder(this)
                .setTitle("搜索歌曲")
                .setView(view)
                .create()

            view.findViewById<android.widget.TextView>(R.id.tv_search_by_name).apply {
                text = "歌曲名: ${song.name}"
                setOnClickListener {
                    doSearchSame(song.name)
                    dialog.dismiss()
                }
            }
            view.findViewById<android.widget.TextView>(R.id.tv_search_by_artist).apply {
                text = "歌手: ${song.singer}"
                setOnClickListener {
                    doSearchSame(song.singer)
                    dialog.dismiss()
                }
            }

            dialog.show()
        }
    }

    private fun doSearchSame(keyword: String) {
        startActivity(Intent(this, SearchResultActivity::class.java).apply {
            putExtra("keyword", keyword)
        })
    }

    private fun setupSeekBar() {
        binding.seekBarContainer.setOnKeyListener { _, keyCode, event ->
            when {
                event.action == KeyEvent.ACTION_DOWN -> {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { PlayerManager.seekSingle(-5000); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { PlayerManager.seekSingle(5000); true }
                        else -> false
                    }
                }
                else -> false
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { PlayerManager.togglePlayPause(); return true }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) { PlayerManager.next(); syncAllUI(); return true }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { PlayerManager.prev(); syncAllUI(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        PlayerManager.onStateChanged = null
        PlayerManager.onProgress = null
        PlayerManager.onError = null
        PlayerManager.onLyric = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun getRealQualityLabel(): String {
        val ext = PlayerManager.currentFileExt
        return when {
            ext == "flac" -> "24bit 无损"
            ext == "m4a" -> "AAC 高品质"
            ext == "ogg" -> "OGG"
            ext == "wav" -> "WAV 无损"
            else -> "MP3 320k"
        }
    }

    private fun formatTime(ms: Int): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun showToast(msg: String) {
        val toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()
    }
}




