package com.example.myapplication.presentation.ui.components

import android.graphics.Bitmap
import android.net.Uri

/**
 * 媒体操作回调封装
 * 用于简化 WorkflowParameterCard 等组件的参数传递
 */
data class MediaCallbacks(
    // 图片相关
    val onStageImage: (paramId: String, bytes: ByteArray, filename: String) -> Unit = { _, _, _ -> },
    val getStagedImageBytes: (paramId: String) -> ByteArray? = { null },
    val onEditMask: (paramId: String) -> Unit = { _ -> },
    
    // 视频相关
    val onStageVideo: (paramId: String, uri: Uri, filename: String, fileSize: Long, duration: Long) -> Unit = { _, _, _, _, _ -> },
    val onRemoveVideo: (paramId: String) -> Unit = { _ -> },
    val getVideoUri: (paramId: String) -> Uri? = { null },
    val videoThumbnails: Map<String, Bitmap> = emptyMap(),
    
    // 音频相关
    val onStageAudio: (paramId: String, uri: Uri, filename: String, fileSize: Long, duration: Long) -> Unit = { _, _, _, _, _ -> },
    val onRemoveAudio: (paramId: String) -> Unit = { _ -> },
    val getAudioUri: (paramId: String) -> Uri? = { null }
) {
    companion object {
        val Empty = MediaCallbacks()
    }
}

