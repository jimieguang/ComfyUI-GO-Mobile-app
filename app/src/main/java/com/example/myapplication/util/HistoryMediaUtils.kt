package com.example.myapplication.util

import com.example.myapplication.data.model.ExecutionRecord
import com.example.myapplication.presentation.ui.model.MediaItem

/**
 * 历史记录媒体聚合工具：把多条执行记录的输出拼成一条可左右滑动的画廊列表。
 *
 * 背景：原先点开某条记录的图片，画廊只包含该记录自己的输出，
 * 任务只有一张图时就无法滑到相邻任务的结果。
 */
object HistoryMediaUtils {

    /** 视觉类媒体（图片 + 视频），跨记录翻页时只在这些之间切换 */
    fun visualMediaOf(rec: ExecutionRecord, imageUrls: List<String>): List<MediaItem> {
        val images = imageUrls.map { MediaItem.Image(it) }
        val videos = rec.localVideoPaths
            .filter { it.isNotBlank() }
            .map { path ->
                val lower = path.trim().lowercase()
                val isGifLike = lower.endsWith(".gif") || lower.endsWith(".webp") ||
                    lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                if (isGifLike) MediaItem.Image("file://$path") else MediaItem.Video(path)
            }
        return images + videos
    }

    /** 完整媒体（含音频），用于单条记录内部浏览 */
    fun fullMediaOf(rec: ExecutionRecord, imageUrls: List<String>): List<MediaItem> {
        val audio = rec.localAudioPaths
            .filter { it.isNotBlank() }
            .map { MediaItem.Audio("file://$it") }
            .ifEmpty { rec.audioUrls.map { MediaItem.Audio(it) } }
        return visualMediaOf(rec, imageUrls) + audio
    }

    data class GalleryTarget(val items: List<MediaItem>, val startIndex: Int)

    /**
     * 按显示顺序把多条记录的视觉媒体串成一条列表，并定位到被点击记录的第一项。
     * 若该记录没有视觉媒体（例如纯音频任务），退回它自己的完整媒体列表。
     */
    fun buildGalleryTarget(
        orderedRecords: List<ExecutionRecord>,
        outputs: Map<String, List<String>>,
        clicked: ExecutionRecord
    ): GalleryTarget {
        val flattened = ArrayList<MediaItem>()
        var startIndex = -1
        for (rec in orderedRecords) {
            val visuals = visualMediaOf(rec, outputs[rec.promptId].orEmpty())
            if (rec.promptId == clicked.promptId && visuals.isNotEmpty()) {
                startIndex = flattened.size
            }
            flattened.addAll(visuals)
        }
        if (startIndex >= 0 && flattened.isNotEmpty()) {
            return GalleryTarget(flattened, startIndex)
        }
        val own = fullMediaOf(clicked, outputs[clicked.promptId].orEmpty())
        return GalleryTarget(own, 0)
    }
}
