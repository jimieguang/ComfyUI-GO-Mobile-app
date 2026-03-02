package com.example.myapplication.data.model

import kotlinx.serialization.Serializable

/**
 * 执行历史记录
 */
@Serializable
data class ExecutionRecord(
    val promptId: String,
    val timestamp: Long,
    val success: Boolean,
    val durationMs: Long,
    val workflowName: String? = null,
    val importSource: String? = null,
    val errorType: String? = null,
    val errorMessage: String? = null,
    val localImagePaths: List<String> = emptyList(),  // 本地保存的图片路径
    val localVideoPaths: List<String> = emptyList(),  // 本地保存的视频路径（下载完成）
    val localAudioPaths: List<String> = emptyList(),  // 🎵 本地保存的音频路径（下载完成）
    val videoUrls: List<String> = emptyList(),  // 视频URL（用于显示下载进度，下载完成后可忽略）
    val audioUrls: List<String> = emptyList(),  // 🎵 音频URL（用于显示下载进度，下载完成后可忽略）
    val videoDownloadProgress: Map<Int, Float> = emptyMap(),  // 视频下载进度 (index -> progress 0-1)
    val audioDownloadProgress: Map<Int, Float> = emptyMap(),  // 🎵 音频下载进度 (index -> progress 0-1)
    val videoDownloadFailed: Set<Int> = emptySet(),  // 🔄 下载失败的视频索引集合
    val audioDownloadFailed: Set<Int> = emptySet(),  // 🎵 下载失败的音频索引集合
    val workflowJsonHash: String? = null,  // 工作流JSON的hash（用于本地文件引用和去重）
    val quickParams: Map<String, List<String>>? = null,  // 快速参数配置（节点ID -> 参数ID列表）
    val textOutputs: Map<String, List<String>> = emptyMap(),  // 📝 文本输出（节点ID -> 文本列表）
    // 移除 imageUrls 字段，不再依赖远程URL
    @Deprecated("不再使用远程URL，已改为本地存储", level = DeprecationLevel.HIDDEN)
    val imageUrls: List<String> = emptyList()  // 向后兼容旧数据
)


