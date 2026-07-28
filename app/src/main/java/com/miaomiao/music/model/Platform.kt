package com.miaomiao.music.model

/** 音质标识，对应song中的不同播放URL */
object Quality {
    /** 通用标签 */
    val labels = mapOf(
        "high" to "高品质",
        "low" to "标准品质",
        "accompaniment" to "伴奏"
    )

    /** QQ音乐专用标签 */
    val qqLabels = mapOf(
        "high" to "高品质 (臻品)",
        "low" to "标准品质",
        "accompaniment" to "低品质"
    )

    val all = listOf("high", "low", "accompaniment")
}
