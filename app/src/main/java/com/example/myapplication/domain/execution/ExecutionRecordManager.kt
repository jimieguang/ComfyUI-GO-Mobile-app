package com.example.myapplication.domain.execution

import android.util.Log
import com.example.myapplication.data.model.ExecutionRecord
import com.example.myapplication.data.repository.ComfyUIRepository
import com.example.myapplication.util.ImageDownloadManager
import com.example.myapplication.util.VideoDownloadManager
import com.example.myapplication.util.VideoUtils
import com.example.myapplication.util.WorkflowJsonManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 执行记录保存参数
 */
data class ExecutionRecordParams(
    val promptId: String,
    val workflow: JsonObject,
    val timestamp: Long,
    val success: Boolean,
    val durationMs: Long,
    val imageUrls: List<String>,
    val videoUrls: List<String> = emptyList(),
    val audioUrls: List<String> = emptyList(),
    val textOutputs: Map<String, List<String>> = emptyMap(),
    val workflowName: String?,
    val importSource: String?,
    val errorType: String? = null,
    val errorMessage: String? = null,
    val quickParams: Map<String, List<String>>? = null,
    val parameterValues: Map<String, kotlinx.serialization.json.JsonElement>? = null
)

/**
 * 执行记录管理器
 * 负责保存执行记录、下载图片和视频
 */
@Singleton
class ExecutionRecordManager @Inject constructor(
    private val repository: ComfyUIRepository,
    private val imageDownloadManager: ImageDownloadManager,
    private val videoDownloadManager: VideoDownloadManager,
    private val workflowJsonManager: WorkflowJsonManager
) {
    
    companion object {
        private const val TAG = "ExecutionRecordManager"
    }
    
    /**
     * 保存执行记录并下载媒体文件
     */
    suspend fun saveRecord(params: ExecutionRecordParams) {
        try {
            // 1. 计算工作流hash
            val workflowHash = workflowJsonManager.calculateHash(params.workflow)
            Log.d(TAG, "工作流hash: $workflowHash")
            
            // 2. 检查JSON文件是否已存在，不存在则保存
            if (!workflowJsonManager.isJsonExists(workflowHash)) {
                val saved = workflowJsonManager.saveWorkflowJson(workflowHash, params.workflow)
                if (saved) {
                    Log.d(TAG, "保存工作流JSON成功: $workflowHash")
                } else {
                    Log.w(TAG, "保存工作流JSON失败: $workflowHash")
                }
            }
            
            // 3. 下载并保存图片到本地
            val localImagePaths = if (params.success && params.imageUrls.isNotEmpty()) {
                Log.d(TAG, "开始下载${params.imageUrls.size}张图片")
                imageDownloadManager.downloadAndSaveImages(params.imageUrls, params.promptId)
            } else {
                emptyList()
            }
            
            // 4. 处理视频
            if (params.success && params.videoUrls.isNotEmpty()) {
                saveRecordWithVideos(params, workflowHash, localImagePaths)
            } else {
                // 无视频，直接保存记录
                saveRecordWithoutVideos(params, workflowHash, localImagePaths)
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存执行记录失败", e)
        }
    }
    
    private suspend fun saveRecordWithVideos(
        params: ExecutionRecordParams,
        workflowHash: String,
        localImagePaths: List<String>
    ) {
        Log.d(TAG, "发现${params.videoUrls.size}个视频，先保存URL用于显示缩略图")
        
        // 检查是否已下载
        val existingByIndex = MutableList(params.videoUrls.size) { "" }
        params.videoUrls.forEachIndexed { index, _ ->
            videoDownloadManager.isVideoDownloaded(params.promptId, index)?.let { path ->
                existingByIndex[index] = path
            }
        }
        val allDownloaded = existingByIndex.none { it.isBlank() }
        
        // 立即保存记录（带videoUrls，用于显示缩略图）
        val initialRecord = ExecutionRecord(
            promptId = params.promptId,
            timestamp = params.timestamp,
            success = params.success,
            durationMs = params.durationMs,
            workflowName = params.workflowName,
            importSource = params.importSource,
            errorType = params.errorType,
            errorMessage = params.errorMessage,
            localImagePaths = localImagePaths,
            // 用索引对齐的视频路径列表：即使只有部分已下载也能正确映射到对应视频
            localVideoPaths = if (allDownloaded) existingByIndex else existingByIndex,
            localAudioPaths = emptyList(),
            videoUrls = params.videoUrls,
            audioUrls = params.audioUrls,
            videoDownloadProgress = existingByIndex
                .mapIndexedNotNull { idx, p -> if (p.isNotBlank()) idx to 1f else null }
                .toMap(),
            audioDownloadProgress = emptyMap(),
            workflowJsonHash = workflowHash,
            quickParams = params.quickParams,
            textOutputs = params.textOutputs,
            parameterValues = params.parameterValues
        )
        
        repository.appendExecutionRecord(initialRecord)
        Log.d(TAG, "初始记录已保存，promptId: ${params.promptId}")
        
        // 异步下载视频
        if (!allDownloaded) {
            startVideoDownload(params)
        }
    }
    
    private fun startVideoDownload(params: ExecutionRecordParams) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "开始异步下载${params.videoUrls.size}个视频")
                // 保持索引对齐，避免 UI 用 index 取路径时错位
                val localVideoPaths = MutableList(params.videoUrls.size) { "" }

                // 先填充已存在的本地路径（避免重复下载）
                params.videoUrls.forEachIndexed { index, _ ->
                    videoDownloadManager.isVideoDownloaded(params.promptId, index)?.let { path ->
                        localVideoPaths[index] = path
                    }
                }
                
                params.videoUrls.forEachIndexed { index, url ->
                    try {
                        // 已下载则跳过，并确保进度是 1
                        val existing = localVideoPaths[index]
                        if (existing.isNotBlank()) {
                            repository.updateVideoDownloadProgress(params.promptId, index, 1f)
                            return@forEachIndexed
                        }

                        // 进度更新节流：避免每个 chunk 都写 DataStore
                        val progressCh = Channel<Float>(capacity = Channel.CONFLATED)
                        val progressJob = launch(Dispatchers.IO) {
                            var lastSent = -1f
                            for (p in progressCh) {
                                // 去重：相同进度不重复写入
                                if (p == lastSent) continue
                                lastSent = p
                                try {
                                    repository.updateVideoDownloadProgress(params.promptId, index, p)
                                } catch (e: Exception) {
                                    Log.e(TAG, "更新视频下载进度失败", e)
                                }
                            }
                        }

                        val path = videoDownloadManager.downloadAndSaveVideo(
                            videoUrl = url,
                            promptId = params.promptId,
                            index = index
                        ) { downloaded, total ->
                            val progress = if (total > 0) (downloaded.toFloat() / total.toFloat()) else 0f
                            progressCh.trySend(progress)
                        }

                        try {
                            progressCh.trySend(1f)
                        } catch (_: Exception) {}
                        try { progressCh.close() } catch (_: Exception) {}
                        try { progressJob.cancel() } catch (_: Exception) {}
                        
                        if (path != null) {
                            localVideoPaths[index] = path
                            val actualFormat = VideoUtils.detectActualFormat(path)
                            Log.d(TAG, "视频[$index]下载成功: $path, 格式: $actualFormat")
                            // 立即更新本地路径（索引对齐）
                            repository.updateVideoLocalPaths(params.promptId, localVideoPaths)
                        } else {
                            Log.e(TAG, "视频[$index]下载失败")
                            repository.markVideoDownloadFailed(params.promptId, index)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "视频[$index]下载异常", e)
                        repository.markVideoDownloadFailed(params.promptId, index)
                    }
                }
                
                val okCount = localVideoPaths.count { it.isNotBlank() }
                Log.d(TAG, "视频下载完成，成功: $okCount/${params.videoUrls.size}")
                
                repository.updateVideoLocalPaths(params.promptId, localVideoPaths)
            } catch (e: Exception) {
                Log.e(TAG, "视频异步下载失败", e)
            }
        }
    }
    
    private suspend fun saveRecordWithoutVideos(
        params: ExecutionRecordParams,
        workflowHash: String,
        localImagePaths: List<String>
    ) {
        val record = ExecutionRecord(
            promptId = params.promptId,
            timestamp = params.timestamp,
            success = params.success,
            durationMs = params.durationMs,
            workflowName = params.workflowName,
            importSource = params.importSource,
            errorType = params.errorType,
            errorMessage = params.errorMessage,
            localImagePaths = localImagePaths,
            localVideoPaths = emptyList(),
            localAudioPaths = emptyList(),
            audioUrls = params.audioUrls,
            workflowJsonHash = workflowHash,
            quickParams = params.quickParams,
            textOutputs = params.textOutputs,
            parameterValues = params.parameterValues
        )
        
        repository.appendExecutionRecord(record)
        Log.d(TAG, "执行记录已保存（无视频），promptId: ${params.promptId}")
    }
}

