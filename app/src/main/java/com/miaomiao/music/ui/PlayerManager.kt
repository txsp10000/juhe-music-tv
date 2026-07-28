package com.miaomiao.music.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import com.miaomiao.music.api.MusicApi
import com.miaomiao.music.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object PlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    var playlist = mutableListOf<Song>()
    var currentIndex = -1
    var isPlaying = false
    var loopMode = 0

    private var consecutiveErrors = 0
    private var cacheDir: File? = null
    private var lyricCacheDir: File? = null
    private var coverCacheDir: File? = null
    private var initialized = false
    private var loadJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    var onStateChanged: ((Song?, Boolean) -> Unit)? = null
    var onProgress: ((Int, Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

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

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        cacheDir = File(context.filesDir, "audio_cache").also { it.mkdirs() }
        lyricCacheDir = File(context.filesDir, "lyric_cache").also { it.mkdirs() }
        coverCacheDir = File(context.filesDir, "cover_cache").also { it.mkdirs() }
        // 启动时清理所有缓存
        cleanAllCacheOnStartup()
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }

    fun play(songs: List<Song>, index: Int) {
        // 如果点击的是当前正在播放的歌曲，继续播放不重启
        val targetSong = songs.getOrNull(index)
        if (targetSong != null && targetSong.id == currentSong?.id && mediaPlayer != null) {
            return
        }
        playlist.clear()
        playlist.addAll(songs)
        currentIndex = index
        loadAndPlay()
    }

    fun playAt(index: Int) {
        if (index in playlist.indices) {
            // 如果点击的是当前正在播放的歌曲，继续播放不重启
            if (index == currentIndex && mediaPlayer != null) {
                return
            }
            currentIndex = index
            loadAndPlay()
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                isPlaying = false
            } else {
                mp.start()
                isPlaying = true
            }
            onStateChanged?.invoke(currentSong, isPlaying)
        }
    }

    fun next() {
        if (loopMode == 1) {
            loadAndPlay()
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
    }

    private fun onComplete() {
        consecutiveErrors = 0
        next()
    }

    private fun loadAndPlay() {
        stop()
        val song = playlist.getOrNull(currentIndex) ?: return
        onStateChanged?.invoke(song, false)

        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val lyricId = song.lyricId.ifEmpty { song.id }
                val picId = song.picId.ifEmpty { song.id }

                val urlDeferred = async(Dispatchers.IO) {
                    try { MusicApi.getPlayUrl(song.id) } catch (_: Exception) { "" }
                }
                val lyricDeferred = async(Dispatchers.IO) {
                    loadLyricWithCache(lyricId)
                }
                val coverDeferred = async(Dispatchers.IO) {
                    loadCoverWithCache(picId, song.source)
                }

                val url = urlDeferred.await()
                val finalUrl = url.ifEmpty { song.playUrl }

                if (finalUrl.isEmpty()) {
                    consecutiveErrors++
                    onError?.invoke("获取播放地址失败")
                    return@launch
                }

                val lyric = lyricDeferred.await()
                lastLyric = lyric
                if (lyric.isNotBlank()) {
                    onLyric?.invoke(lyric)
                } else {
                    onLyric?.invoke("纯音乐，请欣赏")
                }

                val cover = coverDeferred.await()
                if (cover.isNotEmpty()) {
                    song.coverUrl = cover
                }

                song.playUrl = finalUrl
                song.lyric = lyric

                currentFileExt = extractExt(finalUrl)
                val localPath = downloadCurrentOnly(song.id, finalUrl)
                if (localPath != null) {
                    startPlayer(Uri.fromFile(File(localPath)).toString())
                } else {
                    startPlayer(finalUrl)
                }
            } catch (_: CancellationException) {
                // 切歌时协程取消，静默忽略
            } catch (e: Exception) {
                consecutiveErrors++
                onError?.invoke("播放失败: ${e.message}")
            }
        }
    }

    private suspend fun downloadCurrentOnly(songId: String, url: String): String? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val dir = cacheDir ?: return@withContext null
                val ext = extractExt(url)
                val file = File(dir, "$songId.$ext")
                if (file.exists() && file.length() > 0) {
                    return@withContext file.absolutePath
                }


                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (file.length() > 0) file.absolutePath else null
                } else null
            } catch (_: Exception) { null }
        }
    }

    private fun cleanAllCacheOnStartup() {
        try {
            cacheDir?.listFiles()?.forEach { it.delete() }
            lyricCacheDir?.listFiles()?.forEach { it.delete() }
            coverCacheDir?.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    private fun cleanOtherAudioCache(dir: File, keepFileName: String) {
        try {
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name != keepFileName) {
                    f.delete()
                }
            }
        } catch (_: Exception) {}
    }

    private fun cleanOtherCache(dir: File, keepFileName: String) {
        try {
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name != keepFileName) {
                    f.delete()
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun loadLyricWithCache(lyricId: String): String {
        try {
            val dir = lyricCacheDir ?: return ""
            val file = File(dir, "$lyricId.lrc")
            if (file.exists() && file.length() > 0) {
                return file.readText()
            }
            val lyric = MusicApi.getLyric(lyricId)
            if (lyric.isNotBlank()) {
                file.writeText(lyric)
            }
            return lyric
        } catch (_: Exception) { return "" }
    }

    private suspend fun loadCoverWithCache(picId: String, source: String): String {
        try {
            val dir = coverCacheDir ?: return ""
            val file = File(dir, "${source}_$picId.jpg")
            if (file.exists() && file.length() > 0) {
                return file.absolutePath
            }
            val url = MusicApi.getCover(picId, source)
            if (url.isNotEmpty()) {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                return if (file.exists() && file.length() > 0) file.absolutePath else url
            }
            return ""
        } catch (_: Exception) { return "" }
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
                setDataSource(uri)
                setOnPreparedListener {
                    consecutiveErrors = 0
                    it.start()
                    this@PlayerManager.isPlaying = true
                    onStateChanged?.invoke(currentSong, true)
                    startProgress()
                }
                setOnCompletionListener {
                    consecutiveErrors = 0
                    onComplete()
                }
                setOnErrorListener { _, what, extra ->
                    consecutiveErrors++
                    onError?.invoke("播放出错 (what=$what, extra=$extra)")
                    if (consecutiveErrors < 3) next()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            consecutiveErrors++
            onError?.invoke("播放器初始化失败: ${e.message}")
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
                    onProgress?.invoke(pos, dur)
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
    }
}




