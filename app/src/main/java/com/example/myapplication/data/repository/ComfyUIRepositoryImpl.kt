package com.example.myapplication.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.myapplication.data.api.ComfyApiService
import com.example.myapplication.data.model.*
import com.example.myapplication.data.websocket.ComfyWebSocketManager
import com.example.myapplication.util.Constants
import com.example.myapplication.util.generateClientId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.booleanOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ComfyUI 数据仓库实现
 * 负责管理与 ComfyUI 服务器的所有数据交互
 */
@Singleton
class ComfyUIRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val webSocketManager: ComfyWebSocketManager,
    private val retrofit: Retrofit
) : ComfyUIRepository {
    
    companion object {
        private const val TAG = "ComfyUIRepository"
        private val KEY_SERVER_URL = stringPreferencesKey(Constants.KEY_SERVER_URL)
        private val KEY_CLIENT_ID = stringPreferencesKey(Constants.KEY_CLIENT_ID)
        private val KEY_EXEC_HISTORY = stringSetPreferencesKey("exec_history")
        private val KEY_SAVED_WORKFLOWS = stringSetPreferencesKey("saved_workflows")
        private val KEY_LAST_IMPORT_NAME = stringPreferencesKey("last_import_name")
        private val KEY_LAST_IMPORT_SOURCE = stringPreferencesKey("last_import_source")
    }
    
    // 动态创建 API 服务实例
    private fun createApiService(serverUrl: String): ComfyApiService {
        return retrofit.newBuilder()
            .baseUrl(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
            .build()
            .create(ComfyApiService::class.java)
    }
    
    // 内存缓存的 object_info
    @Volatile
    private var cachedObjectInfo: Map<String, NodeInfo>? = null

    override suspend fun testConnection(serverUrl: String): Result<Unit> {
        return try {
            Log.d(TAG, "测试连接到服务器: $serverUrl")
            
            val apiService = createApiService(serverUrl)
            val response = apiService.getObjectInfo()
            
            if (response.isSuccessful) {
                Log.d(TAG, "连接测试成功")
                Result.success(Unit)
            } else {
                Log.e(TAG, "连接测试失败: ${response.code()} - ${response.message()}")
                Result.failure(Exception("连接失败: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "连接测试异常", e)
            Result.failure(e)
        }
    }

    override suspend fun freeResources(
        serverUrl: String,
        unloadModels: Boolean,
        freeMemory: Boolean
    ): Result<Unit> {
        return try {
            val apiService = createApiService(serverUrl)
            val response = apiService.free(
                FreeRequest(
                    unload_models = unloadModels,
                    free_memory = freeMemory
                )
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("释放资源失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getObjectInfo(serverUrl: String): Result<Map<String, NodeInfo>> {
        // 如果已有缓存，直接返回缓存
        cachedObjectInfo?.let { cached ->
            Log.d(TAG, "使用缓存的节点信息: ${cached.size} 个节点类型")
            return Result.success(cached)
        }

        return try {
            Log.d(TAG, "获取节点信息: $serverUrl")
            
            // 添加重试机制
            var lastException: Exception? = null
            val maxRetries = 3
            
            for (attempt in 1..maxRetries) {
                try {
                    Log.d(TAG, "获取节点信息 - 第 $attempt 次尝试")
                    
                    val apiService = createApiService(serverUrl)
                    val response = apiService.getObjectInfo()
                    
                    if (response.isSuccessful) {
                        val objectInfo = response.body()
                        if (objectInfo != null) {
                            Log.d(TAG, "获取到 ${objectInfo.size} 个节点类型")
                            cachedObjectInfo = objectInfo
                            return Result.success(objectInfo)
                        } else {
                            Log.e(TAG, "获取节点信息失败: 响应体为空")
                            return Result.failure(Exception("节点信息响应为空"))
                        }
                    } else {
                        Log.e(TAG, "获取节点信息失败: ${response.code()} - ${response.message()}")
                        if (attempt == maxRetries) {
                            return Result.failure(Exception("获取节点信息失败: ${response.message()}"))
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "获取节点信息第 $attempt 次尝试失败: ${e.message}")
                    
                    // 如果是网络相关错误，等待后重试
                    if (attempt < maxRetries && isRetryableException(e)) {
                        val delayMs = attempt * 2000L // 递增延迟: 2s, 4s, 6s
                        Log.d(TAG, "将在 ${delayMs}ms 后重试")
                        kotlinx.coroutines.delay(delayMs)
                    } else if (attempt == maxRetries) {
                        Log.e(TAG, "获取节点信息异常 - 已达最大重试次数", e)
                        return Result.failure(e)
                    }
                }
            }
            
            Result.failure(lastException ?: Exception("获取节点信息失败 - 未知错误"))
        } catch (e: Exception) {
            Log.e(TAG, "获取节点信息异常", e)
            Result.failure(e)
        }
    }

    override fun getCachedObjectInfo(): Map<String, NodeInfo>? {
        return cachedObjectInfo
    }

    override fun clearObjectInfoCache() {
        Log.d(TAG, "清空对象信息缓存")
        cachedObjectInfo = null
    }
    
    override suspend fun queuePrompt(
        serverUrl: String,
        workflow: JsonObject,
        clientId: String
    ): Result<PromptResponse> {
        return try {
            Log.d(TAG, "提交工作流执行请求")
            
            val apiService = createApiService(serverUrl)
            val request = PromptRequest(workflow, clientId)
            val response = apiService.queuePrompt(request)
            
            if (response.isSuccessful) {
                val promptResponse = response.body()
                if (promptResponse != null) {
                    Log.d(TAG, "工作流提交成功: ${promptResponse.prompt_id}")
                    Result.success(promptResponse)
                } else {
                    Log.e(TAG, "工作流提交失败: 响应体为空")
                    Result.failure(Exception("工作流提交响应为空"))
                }
            } else {
                Log.e(TAG, "工作流提交失败: ${response.code()} - ${response.message()}")
                Result.failure(Exception("工作流提交失败: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "工作流提交异常", e)
            Result.failure(e)
        }
    }
    
    override suspend fun interrupt(serverUrl: String): Result<Unit> {
        return try {
            val apiService = createApiService(serverUrl)
            val response = apiService.interrupt()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("中断失败: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "中断执行异常", e)
            Result.failure(e)
        }
    }
    
    override suspend fun uploadImage(
        serverUrl: String,
        imageData: ByteArray,
        filename: String
    ): Result<UploadResponse> {
        return try {
            Log.d(TAG, "上传图像: $filename (${imageData.size} bytes)")
            
            val apiService = createApiService(serverUrl)
            
            // 创建请求体
            val requestFile = imageData.toRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("image", filename, requestFile)
            
            val typeBody = "input".toRequestBody("text/plain".toMediaTypeOrNull())
            val overwriteBody = "true".toRequestBody("text/plain".toMediaTypeOrNull())
            val response = apiService.uploadImage(body, typeBody, overwriteBody)
            
            if (response.isSuccessful) {
                val uploadResponse = response.body()
                if (uploadResponse != null) {
                    Log.d(TAG, "图像上传成功: ${uploadResponse.name}")
                    Result.success(uploadResponse)
                } else {
                    Log.e(TAG, "图像上传失败: 响应体为空")
                    Result.failure(Exception("图像上传响应为空"))
                }
            } else {
                Log.e(TAG, "图像上传失败: ${response.code()} - ${response.message()}")
                Result.failure(Exception("图像上传失败: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "图像上传异常", e)
            Result.failure(e)
        }
    }
    
    override suspend fun uploadMask(
        serverUrl: String,
        maskData: ByteArray,
        filename: String,
        originalImageName: String
    ): Result<UploadResponse> {
        return try {
            Log.d(TAG, "上传遮罩: $filename (${maskData.size} bytes), 原图: $originalImageName")
            
            val apiService = createApiService(serverUrl)
            
            // 创建请求体
            val requestFile = maskData.toRequestBody("image/png".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("image", filename, requestFile)
            
            val typeBody = "input".toRequestBody("text/plain".toMediaTypeOrNull())
            val subfolderBody = "clipspace".toRequestBody("text/plain".toMediaTypeOrNull())
            
            // 创建original_ref JSON
            val originalRef = """{"filename":"$originalImageName","type":"input","subfolder":""}"""
            val originalRefBody = originalRef.toRequestBody("text/plain".toMediaTypeOrNull())
            
            val response = apiService.uploadMask(body, typeBody, subfolderBody, originalRefBody)
            
            if (response.isSuccessful) {
                val uploadResponse = response.body()
                if (uploadResponse != null) {
                    Log.d(TAG, "遮罩上传成功: ${uploadResponse.name}")
                    Result.success(uploadResponse)
                } else {
                    Log.e(TAG, "遮罩上传失败: 响应体为空")
                    Result.failure(Exception("Upload response body is null"))
                }
            } else {
                Log.e(TAG, "遮罩上传失败: ${response.code()} ${response.message()}")
                Result.failure(Exception("Upload failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "遮罩上传失败", e)
            Result.failure(e)
        }
    }
    
    override suspend fun uploadVideo(
        serverUrl: String,
        videoData: ByteArray,
        filename: String,
        subfolder: String,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<UploadResponse> {
        return try {
            Log.d(TAG, "上传视频: $filename (${videoData.size} bytes), subfolder: $subfolder")
            
            val apiService = createApiService(serverUrl)
            
            // 创建带进度的请求体（使用 /upload/image 端点，它支持任意文件）
            val requestFile = if (onProgress != null) {
                // 包装为带进度回调的 RequestBody
                object : okhttp3.RequestBody() {
                    override fun contentType() = "video/*".toMediaTypeOrNull()
                    override fun contentLength() = videoData.size.toLong()
                    
                    override fun writeTo(sink: okio.BufferedSink) {
                        val totalBytes = videoData.size.toLong()
                        var writtenBytes = 0L
                        val bufferSize = 8192 // 8KB 缓冲区
                        
                        var offset = 0
                        while (offset < videoData.size) {
                            val chunkSize = minOf(bufferSize, videoData.size - offset)
                            sink.write(videoData, offset, chunkSize)
                            offset += chunkSize
                            writtenBytes += chunkSize
                            
                            // 回调进度
                            onProgress(writtenBytes, totalBytes)
                        }
                    }
                }
            } else {
                videoData.toRequestBody("video/*".toMediaTypeOrNull())
            }
            
            val body = MultipartBody.Part.createFormData("image", filename, requestFile)
            val typeBody = "input".toRequestBody("text/plain".toMediaTypeOrNull())
            val overwriteBody = "1".toRequestBody("text/plain".toMediaTypeOrNull())
            
            // 添加子文件夹（如果不为空）
            val response = if (subfolder.isNotEmpty()) {
                val subfolderBody = subfolder.toRequestBody("text/plain".toMediaTypeOrNull())
                apiService.uploadImage(body, typeBody, overwriteBody, subfolderBody)
            } else {
                apiService.uploadImage(body, typeBody, overwriteBody)
            }
            
            if (response.isSuccessful) {
                val uploadResponse = response.body()
                if (uploadResponse != null) {
                    Log.d(TAG, "视频上传成功: ${uploadResponse.name}")
                    Result.success(uploadResponse)
                } else {
                    Log.e(TAG, "视频上传失败: 响应体为空")
                    Result.failure(Exception("视频上传响应为空"))
                }
            } else {
                Log.e(TAG, "视频上传失败: ${response.code()} - ${response.message()}")
                Result.failure(Exception("视频上传失败: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "视频上传异常", e)
            Result.failure(e)
        }
    }
    
    override suspend fun uploadVideoStream(
        context: android.content.Context,
        serverUrl: String,
        videoUri: android.net.Uri,
        filename: String,
        fileSize: Long,
        subfolder: String,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<UploadResponse> = uploadMediaStream(
        context = context,
        serverUrl = serverUrl,
        mediaUri = videoUri,
        filename = filename,
        fileSize = fileSize,
        subfolder = subfolder,
        contentType = "video/*",
        mediaType = "视频",
        onProgress = onProgress
    )
    
    override suspend fun uploadAudioStream(
        context: android.content.Context,
        serverUrl: String,
        audioUri: android.net.Uri,
        filename: String,
        fileSize: Long,
        subfolder: String,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<UploadResponse> = uploadMediaStream(
        context = context,
        serverUrl = serverUrl,
        mediaUri = audioUri,
        filename = filename,
        fileSize = fileSize,
        subfolder = subfolder,
        contentType = "audio/*",
        mediaType = "音频",
        onProgress = onProgress
    )
    
    /**
     * 通用流式媒体上传方法
     */
    private suspend fun uploadMediaStream(
        context: android.content.Context,
        serverUrl: String,
        mediaUri: android.net.Uri,
        filename: String,
        fileSize: Long,
        subfolder: String,
        contentType: String,
        mediaType: String,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<UploadResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "流式上传$mediaType: $filename ($fileSize bytes)")
            
            val apiService = createApiService(serverUrl)
            
            val requestFile = object : okhttp3.RequestBody() {
                override fun contentType() = contentType.toMediaTypeOrNull()
                override fun contentLength() = fileSize
                
                override fun writeTo(sink: okio.BufferedSink) {
                    context.contentResolver.openInputStream(mediaUri)?.use { inputStream ->
                        val buffer = ByteArray(8192)
                        var uploadedBytes = 0L
                        var bytesRead: Int
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            sink.write(buffer, 0, bytesRead)
                            uploadedBytes += bytesRead
                            onProgress?.invoke(uploadedBytes, fileSize)
                        }
                    } ?: throw Exception("无法打开${mediaType}文件")
                }
            }
            
            val body = MultipartBody.Part.createFormData("image", filename, requestFile)
            val typeBody = "input".toRequestBody("text/plain".toMediaTypeOrNull())
            val overwriteBody = "1".toRequestBody("text/plain".toMediaTypeOrNull())
            
            val response = if (subfolder.isNotEmpty()) {
                val subfolderBody = subfolder.toRequestBody("text/plain".toMediaTypeOrNull())
                apiService.uploadImage(body, typeBody, overwriteBody, subfolderBody)
            } else {
                apiService.uploadImage(body, typeBody, overwriteBody)
            }
            
            if (response.isSuccessful) {
                val uploadResponse = response.body()
                if (uploadResponse != null) {
                    Log.d(TAG, "${mediaType}上传成功: ${uploadResponse.name}")
                    Result.success(uploadResponse)
                } else {
                    Result.failure(Exception("${mediaType}上传响应为空"))
                }
            } else {
                Result.failure(Exception("${mediaType}上传失败: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "${mediaType}上传异常", e)
            Result.failure(e)
        }
    }
    
    override fun connectWebSocket(serverUrl: String, clientId: String): Flow<WebSocketMessage> {
        Log.d(TAG, "连接 WebSocket: $serverUrl, clientId: $clientId")
        return webSocketManager.connect(serverUrl, clientId)
    }
    
    override fun disconnectWebSocket() {
        // 全局持久连接：保留连接，不在业务流程中随意断开
        Log.d(TAG, "保留全局 WebSocket 连接，不执行断开")
    }
    
    override suspend fun saveServerUrl(url: String) {
        Log.d(TAG, "保存服务器URL: $url")
        dataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = url
        }
    }
    
    override suspend fun getServerUrl(): String {
        return dataStore.data.map { preferences ->
            preferences[KEY_SERVER_URL] ?: Constants.DEFAULT_SERVER_URL
        }.first()
    }
    
    override fun getServerUrlFlow(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[KEY_SERVER_URL] ?: Constants.DEFAULT_SERVER_URL
        }
    }
    
    override suspend fun disconnect() {
        Log.d(TAG, "断开所有连接")
        disconnectWebSocket()
    }
    
    override suspend fun getClientId(): String {
        return dataStore.data.map { preferences ->
            preferences[KEY_CLIENT_ID] ?: run {
                // 如果没有保存的客户端ID，生成一个新的
                val newClientId = generateClientId()
                // 异步保存新的客户端ID
                dataStore.edit { prefs ->
                    prefs[KEY_CLIENT_ID] = newClientId
                }
                newClientId
            }
        }.first()
    }

    override suspend fun getHistory(serverUrl: String, promptId: String): Result<JsonObject> {
        return try {
            val api = createApiService(serverUrl)
            val resp = api.getHistory(promptId)
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body != null) Result.success(body) else Result.failure(Exception("历史为空"))
            } else {
                Result.failure(Exception("历史查询失败: ${resp.code()} ${resp.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun appendExecutionRecord(record: ExecutionRecord) {
        dataStore.edit { prefs ->
            val json = kotlinx.serialization.json.Json.encodeToString(ExecutionRecord.serializer(), record)
            val old = prefs[KEY_EXEC_HISTORY] ?: emptySet()
            // 保留最近100条
            val updated = (listOf(json) + old).take(100).toSet()
            prefs[KEY_EXEC_HISTORY] = updated
        }
    }

    override fun getExecutionRecords(): Flow<List<ExecutionRecord>> {
        return dataStore.data.map { prefs ->
            val set = prefs[KEY_EXEC_HISTORY] ?: emptySet()
            val json = kotlinx.serialization.json.Json
            set.mapNotNull {
                try { json.decodeFromString(ExecutionRecord.serializer(), it) } catch (_: Exception) { null }
            }
        }
    }

    override suspend fun setLastImportedWorkflow(name: String?, source: String?) {
        dataStore.edit { prefs ->
            if (name != null) prefs[KEY_LAST_IMPORT_NAME] = name else prefs.remove(KEY_LAST_IMPORT_NAME)
            if (source != null) prefs[KEY_LAST_IMPORT_SOURCE] = source else prefs.remove(KEY_LAST_IMPORT_SOURCE)
        }
    }

    override suspend fun getLastImportedWorkflow(): Pair<String?, String?> {
        val prefs = dataStore.data.first()
        val name = prefs[KEY_LAST_IMPORT_NAME]
        val source = prefs[KEY_LAST_IMPORT_SOURCE]
        return name to source
    }

    override suspend fun deleteExecutionRecord(predicateJson: String) {
        dataStore.edit { prefs ->
            val set = prefs[KEY_EXEC_HISTORY] ?: emptySet()
            // 直接按完整JSON匹配删除（简单可靠）
            val updated = set.filterNot { it == predicateJson }.toSet()
            prefs[KEY_EXEC_HISTORY] = updated
        }
    }

    override suspend fun clearExecutionHistory() {
        dataStore.edit { prefs ->
            prefs[KEY_EXEC_HISTORY] = emptySet()
        }
    }
    
    override suspend fun updateVideoDownloadProgress(promptId: String, videoIndex: Int, progress: Float) {
        dataStore.edit { prefs ->
            val set = prefs[KEY_EXEC_HISTORY] ?: emptySet()
            val json = kotlinx.serialization.json.Json
            val updated = set.map { recordJson ->
                try {
                    val record = json.decodeFromString(ExecutionRecord.serializer(), recordJson)
                    if (record.promptId == promptId) {
                        // 更新进度
                        val newProgress = record.videoDownloadProgress.toMutableMap()
                        newProgress[videoIndex] = progress
                        val updatedRecord = record.copy(videoDownloadProgress = newProgress)
                        json.encodeToString(ExecutionRecord.serializer(), updatedRecord)
                    } else {
                        recordJson
                    }
                } catch (e: Exception) {
                    recordJson
                }
            }.toSet()
            prefs[KEY_EXEC_HISTORY] = updated
        }
    }
    
    override suspend fun updateVideoLocalPaths(promptId: String, localVideoPaths: List<String>) {
        dataStore.edit { prefs ->
            val set = prefs[KEY_EXEC_HISTORY] ?: emptySet()
            val json = kotlinx.serialization.json.Json
            val updated = set.map { recordJson ->
                try {
                    val record = json.decodeFromString(ExecutionRecord.serializer(), recordJson)
                    if (record.promptId == promptId) {
                        // 更新本地路径，清空进度
                        val updatedRecord = record.copy(
                            localVideoPaths = localVideoPaths,
                            videoDownloadProgress = emptyMap()
                        )
                        json.encodeToString(ExecutionRecord.serializer(), updatedRecord)
                    } else {
                        recordJson
                    }
                } catch (e: Exception) {
                    recordJson
                }
            }.toSet()
            prefs[KEY_EXEC_HISTORY] = updated
        }
    }
    
    override suspend fun markVideoDownloadFailed(promptId: String, videoIndex: Int) {
        dataStore.edit { prefs ->
            val set = prefs[KEY_EXEC_HISTORY] ?: emptySet()
            val json = kotlinx.serialization.json.Json
            val updated = set.map { recordJson ->
                try {
                    val record = json.decodeFromString(ExecutionRecord.serializer(), recordJson)
                    if (record.promptId == promptId) {
                        // 添加到失败集合，清空该视频的进度
                        val updatedRecord = record.copy(
                            videoDownloadFailed = record.videoDownloadFailed + videoIndex,
                            videoDownloadProgress = record.videoDownloadProgress - videoIndex
                        )
                        json.encodeToString(ExecutionRecord.serializer(), updatedRecord)
                    } else {
                        recordJson
                    }
                } catch (e: Exception) {
                    recordJson
                }
            }.toSet()
            prefs[KEY_EXEC_HISTORY] = updated
        }
    }

    override suspend fun saveWorkflow(item: SavedWorkflow) {
        dataStore.edit { prefs ->
            val json = kotlinx.serialization.json.Json.encodeToString(SavedWorkflow.serializer(), item)
            val old = prefs[KEY_SAVED_WORKFLOWS] ?: emptySet()
            // 去重同 id：先过滤再加入
            val filtered = old.filterNot {
                try {
                    val decoded = kotlinx.serialization.json.Json.decodeFromString(SavedWorkflow.serializer(), it)
                    decoded.id == item.id
                } catch (_: Exception) { false }
            }
            val updated = (listOf(json) + filtered).take(200).toSet()
            prefs[KEY_SAVED_WORKFLOWS] = updated
        }
    }

    override fun getSavedWorkflows(): Flow<List<SavedWorkflow>> {
        return dataStore.data.map { prefs ->
            val set = prefs[KEY_SAVED_WORKFLOWS] ?: emptySet()
            val json = kotlinx.serialization.json.Json
            set.mapNotNull {
                try { json.decodeFromString(SavedWorkflow.serializer(), it) } catch (_: Exception) { null }
            }.sortedByDescending { it.timestamp }
        }
    }

    override suspend fun deleteSavedWorkflow(id: String) {
        dataStore.edit { prefs ->
            val set = prefs[KEY_SAVED_WORKFLOWS] ?: emptySet()
            val json = kotlinx.serialization.json.Json
            val updated = set.filterNot {
                try { json.decodeFromString(SavedWorkflow.serializer(), it).id == id } catch (_: Exception) { false }
            }.toSet()
            prefs[KEY_SAVED_WORKFLOWS] = updated
        }
    }

    override suspend fun renameSavedWorkflow(id: String, newName: String) {
        dataStore.edit { prefs ->
            val set = prefs[KEY_SAVED_WORKFLOWS] ?: emptySet()
            val json = kotlinx.serialization.json.Json
            val mapped = set.mapNotNull {
                try {
                    val obj = json.decodeFromString(SavedWorkflow.serializer(), it)
                    if (obj.id == id) json.encodeToString(SavedWorkflow.serializer(), obj.copy(name = newName)) else it
                } catch (_: Exception) { it }
            }.toSet()
            prefs[KEY_SAVED_WORKFLOWS] = mapped
        }
    }
    
    /**
     * 获取图像URL
     */
    fun getImageUrl(
        serverUrl: String,
        filename: String,
        subfolder: String = "",
        type: String = "output"
    ): String {
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        return "${baseUrl}view?filename=$filename&subfolder=$subfolder&type=$type"
    }
    
    /**
     * 中断当前执行
     */
    suspend fun interruptExecution(serverUrl: String): Result<Unit> {
        return try {
            Log.d(TAG, "中断执行")
            val apiService = createApiService(serverUrl)
            val response = apiService.interrupt()
            
            if (response.isSuccessful) {
                Log.d(TAG, "执行中断成功")
                Result.success(Unit)
            } else {
                Log.e(TAG, "执行中断失败: ${response.code()} - ${response.message()}")
                Result.failure(Exception("执行中断失败: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行中断异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 清空执行队列
     */
    override suspend fun clearQueue(serverUrl: String): Result<Unit> {
        return try {
            Log.d(TAG, "清除待处理队列（官方标准API）")
            val apiService = createApiService(serverUrl)
            
            // 1. 获取当前队列信息
            val queueResponse = apiService.getQueueInfo()
            if (!queueResponse.isSuccessful) {
                return Result.failure(Exception("获取队列信息失败: ${queueResponse.message()}"))
            }
            
            val queueData = queueResponse.body()
            if (queueData == null) {
                return Result.failure(Exception("队列数据为空"))
            }
            
            // 2. 提取待处理任务的ID
            val pendingArray = queueData["queue_pending"]?.jsonArray
            if (pendingArray == null || pendingArray.isEmpty()) {
                Log.d(TAG, "没有待处理任务需要清除")
                return Result.success(Unit)
            }
            
            val pendingIds = mutableListOf<String>()
            val pendingQueueIds = mutableListOf<String>()
            for (item in pendingArray) {
                // 队列项格式: [number, prompt_id, {...}]
                val itemArray = item.jsonArray
                if (itemArray.size >= 2) {
                    // 第0位：队列项序号（或队列ID），第1位：prompt_id
                    val queueId = runCatching { itemArray[0].jsonPrimitive.content }.getOrNull()
                    val promptId = itemArray[1].jsonPrimitive.content
                    if (!queueId.isNullOrBlank()) pendingQueueIds.add(queueId)
                    pendingIds.add(promptId)
                }
            }
            
            if (pendingIds.isEmpty()) {
                Log.d(TAG, "没有有效的任务ID需要清除")
                return Result.success(Unit)
            }
            
            Log.d(TAG, "准备删除 ${pendingIds.size} 个待处理任务")
            
            // 3. 使用官方标准API优先以 prompt_id 删除这些任务
            val deleteRequest = com.example.myapplication.data.model.QueueDeleteRequest(delete = pendingIds)
            val deleteResponse = apiService.deleteQueueItems(deleteRequest)
            if (deleteResponse.isSuccessful) {
                Log.d(TAG, "成功清除 ${pendingIds.size} 个待处理任务")
                return Result.success(Unit)
            }

            // 4. 若以 prompt_id 删除失败，尝试用 queueId 删除（部分后端实现可能需要）
            if (pendingQueueIds.isNotEmpty()) {
                val deleteByQueueId = apiService.deleteQueueItems(
                    com.example.myapplication.data.model.QueueDeleteRequest(delete = pendingQueueIds)
                )
                if (deleteByQueueId.isSuccessful) {
                    Log.d(TAG, "以queueId方式清除了 ${pendingQueueIds.size} 个待处理任务")
                    return Result.success(Unit)
                }
            }

            // 5. 兼容旧版/非标准实现：尝试使用 {"clear":true} 清空队列
            Log.w(TAG, "delete API 失败，尝试兼容清空方式 clear=true: ${deleteResponse.code()} - ${deleteResponse.message()}")
            val legacyClearResp = apiService.clearQueue(
                com.example.myapplication.data.model.QueueClearRequest(clear = true)
            )
            if (legacyClearResp.isSuccessful) {
                Log.d(TAG, "兼容清空成功（clear=true）")
                return Result.success(Unit)
            }

            Log.e(TAG, "清除队列失败（delete(promptId)、delete(queueId) 与 clear 皆失败）: ${legacyClearResp.code()} - ${legacyClearResp.message()}")
            Result.failure(Exception("清除队列失败: ${legacyClearResp.message()}"))
        } catch (e: Exception) {
            Log.e(TAG, "清除队列异常", e)
            Result.failure(e)
        }
    }

    /**
     * 获取队列信息
     */
    override suspend fun getQueue(serverUrl: String): Result<kotlinx.serialization.json.JsonObject> {
        return try {
            val apiService = createApiService(serverUrl)
            val response = apiService.getQueueInfo()
            if (response.isSuccessful) {
                Result.success(response.body() ?: kotlinx.serialization.json.JsonObject(emptyMap()))
            } else {
                Result.failure(Exception("获取队列失败: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 判断异常是否可以重试
     */
    private fun isRetryableException(exception: Exception): Boolean {
        return when (exception) {
            is java.net.SocketTimeoutException -> true
            is java.net.ConnectException -> true
            is java.net.ProtocolException -> true
            is java.io.IOException -> true
            is retrofit2.HttpException -> {
                // HTTP 5xx 错误可以重试，4xx 错误通常不应该重试
                exception.code() >= 500
            }
            else -> false
        }
    }
    
    // ============== 系统资源监控 ==============
    
    override suspend fun getSystemStatsSimple(serverUrl: String): Result<SystemStatsSimple> {
        return try {
            val apiService = createApiService(serverUrl)
            val response = apiService.getSystemStatsSimple()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                Result.failure(Exception("获取系统状态失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getSystemStatsFull(serverUrl: String): Result<SystemStatsFull> {
        return try {
            val apiService = createApiService(serverUrl)
            val response = apiService.getSystemStatsFull()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                Result.failure(Exception("获取系统状态失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
