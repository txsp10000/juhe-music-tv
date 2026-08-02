package com.miaomiao.music.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import com.miaomiao.music.api.MusicApi
import com.miaomiao.music.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object PlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var audioManager: AudioManager? = null
    private var appContext: Context? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    var playlist = mutableListOf<Song>()
    var currentIndex = -1
    var isPlaying = false
    var loopMode = 0

    private var consecutiveErrors = 0
    private var initialized = false
    private var loadJob: Job? = null
    private var playRequestId = 0
    private var resumeAfterAudioFocusLoss = false

    var onStateChanged: ((Song?, Boolean) -> Unit)? = null
    var onProgress: ((Int, Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val stateListeners = mutableSetOf<(Song?, Boolean) -> Unit>()
    private val progressListeners = mutableSetOf<(Int, Int) -> Unit>()
    private val errorListeners = mutableSetOf<(String) -> Unit>()
    private val lyricListeners = mutableSetOf<(String) -> Unit>()

    private var lastLyric: String? = null
    var onLyric: ((String) -> Unit)? = null
        set(value) {
            field = value
            lastLyric?.let { if (it.isNotBlank()) value?.invoke(it) }
        }

    val currentSong: Song?
        get() = if (currentIndex in playlist.indices) playlist[currentIndex] else null

    var currentFileExt: String = "mp3"
        private set

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterAudioFocusLoss = pauseForAudioFocusLoss() || resumeAfterAudioFocusLoss
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 语音助手可能以“可降低音量”方式申请焦点，仍应完整静音以免干扰识别。
                resumeAfterAudioFocusLoss = pauseForAudioFocusLoss() || resumeAfterAudioFocusLoss
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1f, 1f)
                resumePlaybackAfterInterruption(requestAudioFocus = false)
            }
        }
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        requestAudioFocus()
    }

    fun play(songs: List<Song>, index: Int) {
        if (index !in songs.indices) return
        val targetSong = songs.getOrNull(index)
        if (targetSong != null &&
            targetSong.id == currentSong?.id &&
            targetSong.source == currentSong?.source &&
            mediaPlayer != null
        ) {
            return
        }
        loadAndPlay(songs.toList(), index)
    }

    fun playAt(index: Int) {
        if (index in playlist.indices) {
            if (index == currentIndex && mediaPlayer != null) {
                return
            }
            loadAndPlay(playlist.toList(), index)
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                resumeAfterAudioFocusLoss = false
                mp.pause()
                isPlaying = false
            } else {
                mp.start()
                isPlaying = true
            }
            notifyStateChanged(currentSong, isPlaying)
        }
    }

    fun next() {
        if (loopMode == 1) {
            if (currentIndex in playlist.indices) {
                loadAndPlay(playlist.toList(), currentIndex)
            }
            return
        }
        if (currentIndex < playlist.size - 1) {
            playAt(currentIndex + 1)
        } else if (loopMode == 0 && playlist.isNotEmpty()) {
            playAt(0)
        }
    }

    fun prev() {
        if (currentIndex > 0) playAt(currentIndex - 1)
    }

    fun removeAt(index: Int) {
        if (index !in playlist.indices) return
        val removingCurrent = index == currentIndex
        playlist.removeAt(index)

        if (playlist.isEmpty()) {
            currentIndex = -1
            stop()
            notifyStateChanged(null, false)
            return
        }

        if (removingCurrent) {
            val nextIndex = index.coerceAtMost(playlist.lastIndex)
            currentIndex = nextIndex
            stop()
            loadAndPlay(playlist.toList(), nextIndex)
        } else if (index < currentIndex) {
            currentIndex--
            notifyStateChanged(currentSong, isPlaying)
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
    }

    fun seekSingle(deltaMs: Int) {
        mediaPlayer?.let { mp ->
            val current = mp.currentPosition
            val duration = mp.duration
            val target = (current + deltaMs).coerceIn(0, if (duration > 0) duration else Int.MAX_VALUE)
            mp.seekTo(target)
        }
    }

    fun release() {
        loadJob?.cancel()
        progressJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        resumeAfterAudioFocusLoss = false
        abandonAudioFocus()
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun addStateListener(listener: (Song?, Boolean) -> Unit) {
        stateListeners.add(listener)
        listener(currentSong, isPlaying)
    }

    fun removeStateListener(listener: (Song?, Boolean) -> Unit) {
        stateListeners.remove(listener)
    }

    fun addProgressListener(listener: (Int, Int) -> Unit) {
        progressListeners.add(listener)
    }

    fun removeProgressListener(listener: (Int, Int) -> Unit) {
        progressListeners.remove(listener)
    }

    fun addErrorListener(listener: (String) -> Unit) {
        errorListeners.add(listener)
    }

    fun removeErrorListener(listener: (String) -> Unit) {
        errorListeners.remove(listener)
    }

    fun addLyricListener(listener: (String) -> Unit) {
        lyricListeners.add(listener)
        lastLyric?.let { if (it.isNotBlank()) listener(it) }
    }

    fun removeLyricListener(listener: (String) -> Unit) {
        lyricListeners.remove(listener)
    }

    fun resumePlaybackAfterInterruption(requestAudioFocus: Boolean = true) {
        if (!resumeAfterAudioFocusLoss || mediaPlayer == null) return

        if (requestAudioFocus && requestAudioFocus() != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return

        try {
            mediaPlayer?.start()
            isPlaying = true
            resumeAfterAudioFocusLoss = false
            notifyStateChanged(currentSong, true)
        } catch (_: IllegalStateException) {
        }
    }

    private fun onComplete() {
        consecutiveErrors = 0
        next()
    }

    private fun loadAndPlay(targetPlaylist: List<Song>, targetIndex: Int) {
        val song = targetPlaylist.getOrNull(targetIndex) ?: return
        val requestId = ++playRequestId

        stop()

        playlist.clear()
        playlist.addAll(targetPlaylist)
        currentIndex = targetIndex
        notifyStateChanged(song, false)

        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                requestAudioFocus()
                val playId = song.urlId.ifEmpty { song.id }
                val lyricId = song.lyricId.ifEmpty { song.id }
                val picId = song.picId.ifEmpty { song.id }

                val urlDeferred = async(Dispatchers.IO) {
                    try { MusicApi.getPlayUrl(playId, song.source) } catch (_: Exception) { "" }
                }
                val lyricDeferred = async(Dispatchers.IO) {
                    try { MusicApi.getLyric(lyricId, song.source) } catch (_: Exception) { "" }
                }
                val coverDeferred = async(Dispatchers.IO) {
                    try { MusicApi.getCover(picId, song.source) } catch (_: Exception) { "" }
                }

                val url = urlDeferred.await()
                val finalUrl = url.ifEmpty { song.playUrl }
                if (requestId != playRequestId) return@launch

                if (finalUrl.isEmpty()) {
                    consecutiveErrors++
                    notifyError("获取播放地址失败")
                    if (consecutiveErrors < 3) next()
                    return@launch
                }

                val lyric = lyricDeferred.await()
                lastLyric = lyric
                if (lyric.isNotBlank()) {
                    notifyLyric(lyric)
                } else {
                    notifyLyric("纯音乐，请欣赏")
                }

                val cover = coverDeferred.await()
                if (cover.isNotEmpty()) {
                    song.coverUrl = cover
                }

                song.playUrl = finalUrl
                song.lyric = lyric

                currentFileExt = extractExt(finalUrl)
                if (requestId != playRequestId) return@launch

                startPlayer(finalUrl)
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                consecutiveErrors++
                notifyError("播放失败: ${e.message}")
            }
        }
    }

    private fun extractExt(url: String): String {
        val path = try { java.net.URI(url).path.lowercase() } catch (_: Exception) { "" }
        return when {
            path.endsWith(".flac") -> "flac"
            path.endsWith(".m4a") -> "m4a"
            path.endsWith(".aac") -> "aac"
            path.endsWith(".wav") -> "wav"
            path.endsWith(".ogg") -> "ogg"
            else -> "mp3"
        }
    }

    private fun startPlayer(uri: String) {
        mediaPlayer?.release()
        mediaPlayer = null
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36",
                    "Referer" to "https://music.163.com/"
                )
                val ctx = appContext
                if (ctx != null && uri.startsWith("http")) {
                    setDataSource(ctx, Uri.parse(uri), headers)
                } else {
                    setDataSource(uri)
                }
                setOnPreparedListener {
                    consecutiveErrors = 0
                    it.start()
                    this@PlayerManager.isPlaying = true
                    notifyStateChanged(currentSong, true)
                    startProgress()
                }
                setOnCompletionListener {
                    consecutiveErrors = 0
                    onComplete()
                }
                setOnErrorListener { _, what, extra ->
                    consecutiveErrors++
                    notifyError("播放出错 (what=$what, extra=$extra)")
                    if (consecutiveErrors < 3) next()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            consecutiveErrors++
            notifyError("播放器初始化失败: ${e.message}")
            if (consecutiveErrors < 3) next()
        }
    }

    private fun startProgress() {
        progressJob?.cancel()
        progressJob = scope.launch {
            delay(100)
            while (true) {
                val mp = mediaPlayer ?: break
                try {
                    val pos = mp.currentPosition
                    val dur = mp.duration.coerceAtLeast(0)
                    notifyProgress(pos, dur)
                } catch (_: Exception) { break }
                delay(200)
            }
        }
    }

    private fun stop() {
        progressJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        resumeAfterAudioFocusLoss = false
    }

    private fun pauseForAudioFocusLoss(): Boolean {
        val wasPlaying = isPlaying
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {}
        isPlaying = false
        if (wasPlaying) notifyStateChanged(currentSong, false)
        return wasPlaying
    }

    private fun requestAudioFocus(): Int {
        val am = audioManager ?: return AudioManager.AUDIOFOCUS_REQUEST_FAILED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            return am.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            return am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun notifyStateChanged(song: Song?, playing: Boolean) {
        onStateChanged?.invoke(song, playing)
        stateListeners.toList().forEach { it.invoke(song, playing) }
    }

    private fun notifyProgress(current: Int, total: Int) {
        onProgress?.invoke(current, total)
        progressListeners.toList().forEach { it.invoke(current, total) }
    }

    private fun notifyError(message: String) {
        onError?.invoke(message)
        errorListeners.toList().forEach { it.invoke(message) }
    }

    private fun notifyLyric(lyric: String) {
        onLyric?.invoke(lyric)
        lyricListeners.toList().forEach { it.invoke(lyric) }
    }
}