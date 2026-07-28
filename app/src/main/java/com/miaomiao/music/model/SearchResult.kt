package com.miaomiao.music.model

data class SearchResult(
    val list: List<Song>,
    val total: Int,
    val page: Int
)
