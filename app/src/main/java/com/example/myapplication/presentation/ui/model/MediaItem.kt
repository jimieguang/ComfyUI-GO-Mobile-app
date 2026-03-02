package com.example.myapplication.presentation.ui.model

/**
 * 媒体类型密封类（支持图片和视频统一管理）
 */
sealed class MediaItem {
    abstract val url: String
    data class Image(override val url: String) : MediaItem()
    data class Video(override val url: String) : MediaItem()
    data class Audio(override val url: String) : MediaItem()
}

