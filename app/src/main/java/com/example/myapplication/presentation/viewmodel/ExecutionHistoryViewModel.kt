package com.example.myapplication.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.ExecutionRecord
import com.example.myapplication.data.repository.ComfyUIRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray

@HiltViewModel
class ExecutionHistoryViewModel @Inject constructor(
    private val repository: ComfyUIRepository,
    private val imageDownloadManager: com.example.myapplication.util.ImageDownloadManager,
    private val videoDownloadManager: com.example.myapplication.util.VideoDownloadManager,
    private val workflowJsonManager: com.example.myapplication.util.WorkflowJsonManager
) : ViewModel() {

    val records: StateFlow<List<ExecutionRecord>> = repository
        .getExecutionRecords()
        .map { list -> list.sortedByDescending { it.timestamp } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _workflowNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val workflowNames = _workflowNames.asStateFlow()

    private val _recordOutputs = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val recordOutputs = _recordOutputs.asStateFlow()

    // 最近一次从历史加载的记录ID（用于返回历史页时显示“已加载”）
    private val _lastLoadedId = MutableStateFlow<String?>(null)
    val lastLoadedId = _lastLoadedId.asStateFlow()

    fun setLastLoadedId(id: String?) { _lastLoadedId.value = id }

    // 预取的历史工作流JSON（promptId -> JsonObject）
    private val _workflowJson = MutableStateFlow<Map<String, JsonObject>>(emptyMap())
    val workflowJson = _workflowJson.asStateFlow()

    fun deleteRecordByJson(rawJson: String) {
        viewModelScope.launch {
            // 1. 反序列化获取记录信息，以便删除本地文件
            try {
                val record = kotlinx.serialization.json.Json.decodeFromString(
                    ExecutionRecord.serializer(),
                    rawJson
                )
                
                // 2. 删除本地图片
                if (record.localImagePaths.isNotEmpty()) {
                    imageDownloadManager.deleteImageFiles(record.localImagePaths)
                    android.util.Log.d("ExecutionHistoryVM", "删除图片: ${record.localImagePaths.size}张")
                }
                
                // 3. 删除本地视频
                if (record.localVideoPaths.isNotEmpty()) {
                    val paths = record.localVideoPaths.filter { it.isNotBlank() }
                    if (paths.isNotEmpty()) {
                        videoDownloadManager.deleteVideoFiles(paths)
                    }
                    android.util.Log.d("ExecutionHistoryVM", "删除视频: ${paths.size}个")
                }
                
                // 4. 检查并删除工作流JSON（如无其他引用）
                val hash = record.workflowJsonHash
                if (hash != null) {
                    val allRecords = records.value
                    val otherRecordsUsingThisJson = allRecords.count { 
                        it.promptId != record.promptId && it.workflowJsonHash == hash 
                    }
                    
                    if (otherRecordsUsingThisJson == 0) {
                        workflowJsonManager.deleteWorkflowJson(hash)
                        android.util.Log.d("ExecutionHistoryVM", "删除工作流JSON: $hash")
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("ExecutionHistoryVM", "解析记录失败，可能无法删除本地文件", e)
            }
            
            // 5. 删除数据库记录
            repository.deleteExecutionRecord(rawJson)
        }
    }

    @Deprecated("使用 clearAllHistory() 代替，该方法会同时清理本地文件", ReplaceWith("clearAllHistory()"))
    fun clearAll() {
        clearAllHistory()  // 调用完整的清理方法，包括本地文件
    }

    /**
     * 从本地读取历史工作流JSON
     * @param promptId 执行ID
     * @return 工作流JSON对象
     */
    suspend fun fetchWorkflowFromHistory(promptId: String): Result<JsonObject> {
        return try {
            // 1. 从记录中获取workflowJsonHash
            val record = records.value.find { it.promptId == promptId }
            val hash = record?.workflowJsonHash
            
            if (hash == null) {
                return Result.failure(Exception("记录中没有工作流JSON引用"))
            }
            
            // 2. 从本地文件读取
            val workflow = workflowJsonManager.readWorkflowJson(hash)
            if (workflow != null) {
                Result.success(workflow)
            } else {
                Result.failure(Exception("工作流JSON文件不存在或已损坏"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun ensureWorkflowNameLoaded(promptId: String) {
        if (_workflowNames.value.containsKey(promptId)) return
        viewModelScope.launch {
            val result = fetchWorkflowFromHistory(promptId)
            val name = if (result.isSuccess) {
                val wf = result.getOrThrow()
                // 优先取 SaveImage 的 filename_prefix
                var derived: String? = null
                try {
                    for ((key, value) in wf) {
                        if (key.toIntOrNull() == null) continue
                        val node = value.jsonObject
                        val classType = node["class_type"]?.jsonPrimitive?.content
                        if (classType == "SaveImage") {
                            val inputs = node["inputs"]?.jsonObject
                            val prefix = inputs?.get("filename_prefix")?.jsonPrimitive?.content
                            if (!prefix.isNullOrBlank()) { derived = prefix; break }
                        }
                    }
                } catch (_: Exception) { }
                derived ?: ("工作流 " + promptId.take(8))
            } else {
                "工作流 " + promptId.take(8)
            }
            _workflowNames.value = _workflowNames.value + (promptId to name)
        }
    }

    /**
     * 加载执行记录的输出图片（仅从本地读取）
     */
    fun ensureOutputsLoaded(promptId: String) {
        if (_recordOutputs.value.containsKey(promptId)) return
        viewModelScope.launch {
            runCatching {
                // 从执行记录中获取本地图片路径
                val allRecords = records.value
                val record = allRecords.find { it.promptId == promptId }
                
                if (record != null && record.localImagePaths.isNotEmpty()) {
                    // 验证本地图片是否存在
                    val validPaths = record.localImagePaths.filter { path: String ->
                        imageDownloadManager.isImageExists(path)
                    }
                    
                    if (validPaths.isNotEmpty()) {
                        // 使用 file:// URI 协议
                        val fileUris = validPaths.map { path: String -> "file://$path" }
                        _recordOutputs.update { it + (promptId to fileUris) }
                    } else {
                        // 本地图片文件已丢失
                        android.util.Log.w("ExecutionHistoryVM", "本地图片文件不存在: $promptId")
                        _recordOutputs.update { it + (promptId to emptyList()) }
                    }
                } else {
                    // 记录中没有图片路径
                    _recordOutputs.update { it + (promptId to emptyList()) }
                }
            }.onFailure {
                // 忽略失败，保持无图状态
                _recordOutputs.update { it + (promptId to emptyList()) }
            }
        }
    }

    fun ensureWorkflowJsonLoaded(promptId: String) {
        if (_workflowJson.value.containsKey(promptId)) return
        viewModelScope.launch {
            val result = fetchWorkflowFromHistory(promptId)
            if (result.isSuccess) {
                val wf = result.getOrThrow()
                _workflowJson.value = _workflowJson.value + (promptId to wf)
            }
        }
    }

    fun getCachedWorkflow(promptId: String): JsonObject? = _workflowJson.value[promptId]
    
    fun clearAllHistory() {
        viewModelScope.launch {
            // 1. 删除所有本地图片
            imageDownloadManager.clearAllImages()
            
            // 2. 删除所有本地视频
            videoDownloadManager.clearAllVideos()
            
            // 3. 删除所有工作流JSON
            workflowJsonManager.clearAllJson()
            
            // 4. 清空执行记录
            repository.clearExecutionHistory()
            
            // 5. 清空缓存
            _workflowNames.value = emptyMap()
            _recordOutputs.value = emptyMap()
            _workflowJson.value = emptyMap()
        }
    }
    
    /**
     * 删除单条执行记录、图片、视频及工作流JSON（如无其他引用）
     */
    fun deleteRecord(record: ExecutionRecord) {
        viewModelScope.launch {
            val promptId = record.promptId
            val hash = record.workflowJsonHash
            
            // 1. 删除本地图片（总是删除）
            if (record.localImagePaths.isNotEmpty()) {
                imageDownloadManager.deleteImageFiles(record.localImagePaths)
                android.util.Log.d("ExecutionHistoryVM", "删除图片: ${record.localImagePaths.size}张")
            }
            
            // 2. 删除本地视频（总是删除）
            if (record.localVideoPaths.isNotEmpty()) {
                val paths = record.localVideoPaths.filter { it.isNotBlank() }
                if (paths.isNotEmpty()) {
                    videoDownloadManager.deleteVideoFiles(paths)
                }
                android.util.Log.d("ExecutionHistoryVM", "删除视频: ${paths.size}个")
            }
            
            // 3. 检查工作流JSON的引用计数
            if (hash != null) {
                val allRecords = records.value
                val otherRecordsUsingThisJson = allRecords.count { 
                    it.promptId != promptId && it.workflowJsonHash == hash 
                }
                
                if (otherRecordsUsingThisJson == 0) {
                    // 没有其他记录引用，删除JSON文件
                    workflowJsonManager.deleteWorkflowJson(hash)
                    android.util.Log.d("ExecutionHistoryVM", "删除工作流JSON: $hash (无其他引用)")
                } else {
                    // 还有其他记录引用，保留JSON
                    android.util.Log.d("ExecutionHistoryVM", "保留工作流JSON: $hash (还有${otherRecordsUsingThisJson}条记录引用)")
                }
            }
            
            // 4. 删除记录
            repository.deleteExecutionRecord(promptId)
            
            // 5. 清理缓存
            _workflowNames.update { it - promptId }
            _recordOutputs.update { it - promptId }
            _workflowJson.update { it - promptId }
        }
    }
    
    /**
     * 获取所有历史图片的总大小
     */
    suspend fun getTotalImagesSizeMB(): Float {
        return imageDownloadManager.getTotalImagesSizeMB()
    }
    
    /**
     * 清理旧图片（超过指定天数）
     */
    fun cleanupOldImages(daysToKeep: Int = 30) {
        viewModelScope.launch {
            imageDownloadManager.cleanupOldImages(daysToKeep)
        }
    }
}


