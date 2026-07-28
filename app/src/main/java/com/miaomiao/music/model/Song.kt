package com.miaomiao.music.model

import com.google.gson.annotations.SerializedName

/**
 * 歌曲数据模型 - 适配 GD Studio 网易云音乐 API
 */
data class Song(
    val id: String = "",
    val name: String = "",
    val artist: List<String> = emptyList(),
    val album: String = "",
    @SerializedName("pic_id") val picId: String = "",
    @SerializedName("url_id") val urlId: String = "",
    @SerializedName("lyric_id") val lyricId: String = "",
    @SerializedName("source") val source: String = "netease"
) {
    /** 歌手名，多人用 / 分隔 */
    val singer: String get() = artist.joinToString(" / ")

    /** 播放URL，异步获取后填入 */
    @Transient var playUrl: String = ""

    /** 歌词文本，异步获取后填入 */
    @Transient var lyric: String = ""

    /** 专辑封面URL，异步获取后填入 */
    @Transient var coverUrl: String = ""
}
