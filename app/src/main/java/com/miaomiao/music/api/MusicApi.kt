package com.miaomiao.music.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.miaomiao.music.model.LyricResponse
import com.miaomiao.music.model.PicResponse
import com.miaomiao.music.model.Song
import com.miaomiao.music.model.UrlResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object MusicApi {
    private const val BASE = "https://music-api.gdstudio.xyz/api.php"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: throw Exception("响应为空")
        if (resp.code >= 400) throw Exception("HTTP ${resp.code}")
        body
    }

    /** 通用重试：最多30次，间隔1秒 */
    private suspend fun <T> retry(block: suspend () -> T): T {
        var lastError: Exception? = null
        for (attempt in 1..30) {
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
            }
            if (attempt < 30) delay(1000)
        }
        throw lastError ?: Exception("重试30次后仍失败")
    }

    /** 搜索结果包装类 */
    data class SearchResult(val songs: List<Song>, val rawBody: String)

    /** 搜索歌曲（最多重试30次，空结果也重试） */
    suspend fun search(keyword: String, num: Int = 20, source: String = "netease", page: Int = 1): List<Song> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        var songs = emptyList<Song>()
        for (attempt in 1..30) {
            try {
                val url = "$BASE?types=search&source=$source&name=$encoded&count=$num&pages=$page"
                val body = httpGet(url)
                val type = object : TypeToken<List<Song>>() {}.type
                songs = gson.fromJson<List<Song>>(body, type) ?: emptyList()
                if (songs.isNotEmpty()) break
            } catch (_: Exception) { }
            if (attempt < 30) delay(1000)
        }
        songs.take(num)
    }

    /** 搜索歌曲（返回原始响应体，最多重试30次） */
    suspend fun searchRaw(keyword: String, num: Int = 20, source: String = "netease", page: Int = 1): SearchResult = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        var songs = emptyList<Song>()
        var body = ""
        for (attempt in 1..30) {
            try {
                val url = "$BASE?types=search&source=$source&name=$encoded&count=$num&pages=$page"
                body = httpGet(url)
                val type = object : TypeToken<List<Song>>() {}.type
                songs = gson.fromJson<List<Song>>(body, type) ?: emptyList()
                if (songs.isNotEmpty()) break
            } catch (_: Exception) { }
            if (attempt < 30) delay(1000)
        }
        SearchResult(songs.take(num), body)
    }

    /** 获取播放URL（999=24bit FLAC无损，最多重试30次） */
    suspend fun getPlayUrl(trackId: String): String = withContext(Dispatchers.IO) {
        retry {
            val url = "$BASE?types=url&source=netease&id=$trackId&br=999"
            val body = httpGet(url)
            val resp = gson.fromJson(body, UrlResponse::class.java)
            resp.url.ifEmpty { throw Exception("播放地址为空") }
        }
    }

    /** 获取歌词（最多重试30次，空结果也重试） */
    suspend fun getLyric(lyricId: String): String = withContext(Dispatchers.IO) {
        try {
            retry {
                val url = "$BASE?types=lyric&source=netease&id=$lyricId"
                val body = httpGet(url)
                val resp = gson.fromJson(body, LyricResponse::class.java)
                resp.lyric.ifBlank { throw Exception("歌词为空") }
            }
        } catch (_: Exception) { "" }
    }

    /** 获取专辑封面URL（最多重试30次，空结果也重试） */
    suspend fun getCover(picId: String, source: String = "netease"): String = withContext(Dispatchers.IO) {
        try {
            retry {
                val url = "$BASE?types=pic&source=$source&id=$picId&size=500"
                val body = httpGet(url)
                val resp = gson.fromJson(body, PicResponse::class.java)
                resp.url.ifBlank { throw Exception("封面为空") }
            }
        } catch (_: Exception) { "" }
    }

    /** 批量获取封面（并发请求） */
    suspend fun getCovers(picIds: List<String>, source: String = "netease"): Map<String, String> = coroutineScope {
        picIds.map { id ->
            async(Dispatchers.IO) {
                id to try { getCover(id, source) } catch (_: Exception) { "" }
            }
        }.awaitAll().toMap()
    }
}
