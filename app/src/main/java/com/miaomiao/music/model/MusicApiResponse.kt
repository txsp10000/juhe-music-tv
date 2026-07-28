package com.miaomiao.music.model

/**
 * 搜索接口返回的响应 - 直接返回歌曲列表
 */
data class LyricResponse(
    val lyric: String = ""
)

data class PicResponse(
    val url: String = ""
)

data class UrlResponse(
    val url: String = "",
    val br: Int = 0
)
