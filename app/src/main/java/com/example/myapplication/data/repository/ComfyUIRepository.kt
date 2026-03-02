package com.example.myapplication.data.repository

import com.example.myapplication.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * ComfyUI 数据仓库接口
 * 定义所有与 ComfyUI 相关的数据操作
 */
interface ComfyUIRepository {
    
    // 连接管理
    suspend fun testConnection(serverUrl: String): Result<Unit>
    suspend fun getObjectInfo(serverUrl: String): Result<Map<String, NodeInfo>>
    fun getCachedObjectInfo(): Map<String, NodeInfo>?
    fun clearObjectInfoCache()
    
    // 工作流执行
    suspend fun queuePrompt(
        serverUrl: String,
        workflow: JsonObject,
        clientId: String
    ): Result<PromptResponse>
    
    /**
     * 中断当前执行
     */
    suspend fun interrupt(serverUrl: String): Result<Unit>

    /**
     * 释放内存/卸载模型（ComfyUI: POST /free）
     * - unloadModels: 卸载模型
     * - freeMemory: 释放内存/缓存
     */
    suspend fun freeResources(
        serverUrl: String,
        unloadModels: Boolean = false,
        freeMemory: Boolean = false
    ): Result<Unit>
    
    suspend fun uploadImage(
        serverUrl: String,
        imageData: ByteArray,
        filename: String
    ): Result<UploadResponse>
    
    suspend fun uploadMask(
        serverUrl: String,
        maskData: ByteArray,
        filename: String,
        originalImageName: String
    ): Result<UploadResponse>
    
    /**
     * 上传视频文件（旧方法，保留向后兼容）
     * @param serverUrl 服务器地址
     * @param videoData 视频数据（字节数组）
     * @param filename 文件名
     * @param subfolder 子文件夹（可选，默认为空）
     * @param onProgress 上传进度回调 (已上传字节, 总字节)
     */
    suspend fun uploadVideo(
        serverUrl: String,
        videoData: ByteArray,
        filename: String,
        subfolder: String = "",
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<UploadResponse>
    
    /**
     * 流式上传视频文件（推荐，避免大文件OOM）
     * @param context Android Context
     * @param serverUrl 服务器地址
     * @param videoUri 视频文件 Uri
     * @param filename 文件名
     * @param fileSize 文件大小（字节）
     * @param subfolder 子文件夹（可选，默认为空）
     * @param onProgress 上传进度回调 (已上传字节, 总字节)
     */
    suspend fun uploadVideoStream(
        context: android.content.Context,
        serverUrl: String,
        videoUri: android.net.Uri,
        filename: String,
        fileSize: Long,
        subfolder: String = "",
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<UploadResponse>
    
    /**
     * 🎵 流式上传音频文件（推荐，避免大文件OOM）
     * @param context Android Context
     * @param serverUrl 服务器地址
     * @param audioUri 音频文件 Uri
     * @param filename 文件名
     * @param fileSize 文件大小（字节）
     * @param subfolder 子文件夹（可选，默认为空）
     * @param onProgress 上传进度回调 (已上传字节, 总字节)
     */
    suspend fun uploadAudioStream(
        context: android.content.Context,
        serverUrl: String,
        audioUri: android.net.Uri,
        filename: String,
        fileSize: Long,
        subfolder: String = "",
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<UploadResponse>
    
    // WebSocket 连接
    fun connectWebSocket(
        serverUrl: String,
        clientId: String
    ): Flow<WebSocketMessage>
    
    fun disconnectWebSocket()
    
    // 设置管理
    suspend fun saveServerUrl(url: String)
    suspend fun getServerUrl(): String
    suspend fun getClientId(): String
    
    // 额外方法
    fun getServerUrlFlow(): Flow<String>
    suspend fun disconnect()

    /**
     * 清空待处理队列（不影响当前正在执行的任务）
     */
    suspend fun clearQueue(serverUrl: String): Result<Unit>

    /**
     * 获取服务器队列原始结构（用于统计 n/N）
     */
    suspend fun getQueue(serverUrl: String): Result<kotlinx.serialization.json.JsonObject>

    // 历史查询
    suspend fun getHistory(serverUrl: String, promptId: String): Result<JsonObject>

    // 执行历史持久化
    suspend fun appendExecutionRecord(record: ExecutionRecord)
    fun getExecutionRecords(): Flow<List<ExecutionRecord>>
    suspend fun deleteExecutionRecord(predicateJson: String)
    suspend fun clearExecutionHistory()
    
    /**
     * 更新视频下载进度
     * @param promptId 执行ID
     * @param videoIndex 视频索引
     * @param progress 下载进度 (0.0-1.0)
     */
    suspend fun updateVideoDownloadProgress(promptId: String, videoIndex: Int, progress: Float)
    
    /**
     * 更新视频本地路径（下载完成）
     * @param promptId 执行ID
     * @param localVideoPaths 本地视频路径列表
     */
    suspend fun updateVideoLocalPaths(promptId: String, localVideoPaths: List<String>)
    
    /**
     * 🔄 标记视频下载失败
     * @param promptId 执行ID
     * @param videoIndex 视频索引
     */
    suspend fun markVideoDownloadFailed(promptId: String, videoIndex: Int)

    // 最近一次导入的工作流元信息（用于给执行历史打上名称/来源）
    suspend fun setLastImportedWorkflow(name: String?, source: String?)
    suspend fun getLastImportedWorkflow(): Pair<String?, String?>

    // 工作流存储
    suspend fun saveWorkflow(item: SavedWorkflow)
    fun getSavedWorkflows(): Flow<List<SavedWorkflow>>
    suspend fun deleteSavedWorkflow(id: String)
    suspend fun renameSavedWorkflow(id: String, newName: String)
    
    // 系统资源监控
    /**
     * 获取系统资源统计（简化版，适合频繁轮询）
     * 需要安装 comfyui_system_monitor 插件
     */
    suspend fun getSystemStatsSimple(serverUrl: String): Result<SystemStatsSimple>
    
    /**
     * 获取系统资源统计（完整版）
     * 需要安装 comfyui_system_monitor 插件
     */
    suspend fun getSystemStatsFull(serverUrl: String): Result<SystemStatsFull>
}
