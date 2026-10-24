package com.example.myapplication.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.ComfyUIRepository
import com.example.myapplication.domain.parser.WorkflowParser
import com.example.myapplication.domain.execution.WorkflowExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import com.example.myapplication.presentation.ui.components.PendingImageData
import com.example.myapplication.presentation.ui.components.ImageParameterValue
import com.example.myapplication.presentation.ui.components.DrawingPath
import com.example.myapplication.presentation.ui.components.generateMaskedImageFast
import com.example.myapplication.presentation.ui.components.toSerializable
import com.example.myapplication.presentation.ui.components.toDrawingPath
import com.example.myapplication.presentation.ui.components.toSize
import com.example.myapplication.util.ImageUtils

/**
 * 工作流解析ViewModel
 * 管理工作流解析状态和参数编辑
 */
@HiltViewModel
class WorkflowParserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workflowParser: WorkflowParser,
    private val repository: ComfyUIRepository,
    private val workflowExecutor: WorkflowExecutor,
    private val notificationService: com.example.myapplication.service.WorkflowProgressNotificationService
) : ViewModel() {
    
    /**
     * 节点实例数据
     */
    data class NodeInstance(
        val nodeId: String,
        val classType: String,
        val title: String,
        val parameters: List<WorkflowParser.EditableParam>
    )

    /**
     * UI状态数据类
     */
    data class WorkflowParserUiState(
        val isParsing: Boolean = false,
        val hasParsedWorkflow: Boolean = false,
        val parseResult: WorkflowParser.ParseResult? = null,
        val parameterValues: Map<String, JsonElement> = emptyMap(),
        val nodeInstances: List<NodeInstance> = emptyList(), // 改为按节点实例分组
        val expandedGroups: Set<String> = emptySet(),
        val errorMessage: String = "",
        val showError: Boolean = false,
        val currentWorkflowHash: String = "", // 用于检测工作流是否变更
        val executionStatus: WorkflowExecutor.ExecutionStatus = WorkflowExecutor.ExecutionStatus(),
        val seedModes: Map<String, SeedMode> = emptyMap(),
        val queueCurrentIndex: Int = 0,
        val queueTotal: Int = 0,
        val quickParamsByNode: Map<String, Set<String>> = emptyMap(), // nodeId -> set of paramIds
        val maskEditingParamId: String? = null, // 当前正在编辑遮罩的参数ID
        val maskEditingOriginalBytes: ByteArray? = null, // 正在编辑的原始图像数据
        val maskEditingExistingPaths: List<DrawingPath> = emptyList(), // 已有的遮罩路径
        val showMaskEditor: Boolean = false, // 是否显示遮罩编辑器
        val shouldPreserveQuickParams: Boolean = false, // ✅ 标记是否应该保留快速参数（从历史/存储加载时）
        // 从历史记录左滑恢复时，待注入的参数值（解析完成后一次性应用）
        val pendingParameterValues: Map<String, kotlinx.serialization.json.JsonElement>? = null,
        // 🎥 视频上传进度
        val isUploadingVideo: Boolean = false,
        val videoUploadProgress: Float = 0f,
        val videoUploadFileName: String = "",
        // 🎥 视频缩略图缓存（直接放入状态中，自动触发重组）
        val videoThumbnails: Map<String, android.graphics.Bitmap> = emptyMap(),
        // 批次数量（持久化状态，避免导航丢失）
        val batchCount: Int = 1
    )
    
    // 内部可变状态
    private val _uiState = MutableStateFlow(WorkflowParserUiState())
    
    // 对外暴露的只读状态
    val uiState: StateFlow<WorkflowParserUiState> = _uiState.asStateFlow()
    
    // 保存原始工作流用于执行
    private var originalWorkflow: JsonObject? = null
    enum class SeedMode { FIXED, INCREMENT, DECREMENT, RANDOM }

    fun setSeedMode(paramId: String, mode: SeedMode) {
        _uiState.update { state ->
            state.copy(seedModes = state.seedModes + (paramId to mode))
        }
    }

    fun getSeedMode(paramId: String): SeedMode {
        return _uiState.value.seedModes[paramId] ?: SeedMode.FIXED
    }
    
    /**
     * 开始编辑遮罩
     */
    fun startMaskEditing(paramId: String) {
        android.util.Log.d("WorkflowParserVM", "开始编辑遮罩 - 参数ID: $paramId")
        android.util.Log.d("WorkflowParserVM", "当前暂存数据: ${pendingImageUploads.keys}")
        
        val pendingData = pendingImageUploads[paramId]
        val originalBytes = pendingData?.originalBytes
        val existingPaths = pendingData?.maskPaths ?: emptyList()
        
        android.util.Log.d("WorkflowParserVM", "找到暂存数据: ${pendingData != null}")
        android.util.Log.d("WorkflowParserVM", "原图字节数组大小: ${originalBytes?.size ?: 0}")
        android.util.Log.d("WorkflowParserVM", "已有路径数量: ${existingPaths.size}")
        
        if (originalBytes != null) {
            android.util.Log.d("WorkflowParserVM", "启动遮罩编辑器")
            _uiState.update { state ->
                state.copy(
                    maskEditingParamId = paramId,
                    maskEditingOriginalBytes = originalBytes,
                    maskEditingExistingPaths = existingPaths,
                    showMaskEditor = true
                )
            }
        } else {
            android.util.Log.e("WorkflowParserVM", "无法启动遮罩编辑器 - 未找到原图数据")
            
            // 尝试从参数值中恢复数据
            val currentValue = _uiState.value.parameterValues[paramId]
            android.util.Log.d("WorkflowParserVM", "当前参数值: $currentValue")
            
            if (currentValue != null) {
                try {
                    val valueStr = (currentValue as? JsonPrimitive)?.contentOrNull
                    if (valueStr != null && !valueStr.startsWith("staged::")) {
                        // 可能是新格式的ImageParameterValue，尝试恢复
                        val imageParam = kotlinx.serialization.json.Json.decodeFromString<ImageParameterValue>(valueStr)
                        android.util.Log.d("WorkflowParserVM", "尝试从参数值恢复数据: 路径数量=${imageParam.maskPaths.size}")
                        
                        // 从UI预览缓存中获取原图
                        val cachedBytes = imagePreviews[paramId]
                        if (cachedBytes != null) {
                            android.util.Log.d("WorkflowParserVM", "从预览缓存恢复原图数据")
                            val restoredPaths = imageParam.maskPaths.map { it.toDrawingPath() }
                            
                            // 恢复暂存数据
                            pendingImageUploads[paramId] = PendingImageData(
                                originalBytes = cachedBytes,
                                filename = "restored_image_${System.currentTimeMillis()}.png",
                                maskPaths = restoredPaths
                            )
                            
                            // 启动遮罩编辑器
                            _uiState.update { state ->
                                state.copy(
                                    maskEditingParamId = paramId,
                                    maskEditingOriginalBytes = cachedBytes,
                                    maskEditingExistingPaths = restoredPaths,
                                    showMaskEditor = true
                                )
                            }
                            android.util.Log.d("WorkflowParserVM", "成功恢复数据并启动遮罩编辑器")
                        } else {
                            android.util.Log.e("WorkflowParserVM", "预览缓存中也没有原图数据")
                            
                            // 🔥 最后的尝试：检查是否是旧格式的staged标记
                            if (valueStr.startsWith("staged::")) {
                                android.util.Log.d("WorkflowParserVM", "检测到旧格式的staged标记，无法恢复")
                                // 可以考虑提示用户重新上传图片
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WorkflowParserVM", "解析参数值失败: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 取消遮罩编辑
     */
    fun cancelMaskEditing() {
        _uiState.update { state ->
            state.copy(
                maskEditingParamId = null,
                maskEditingOriginalBytes = null,
                maskEditingExistingPaths = emptyList(),
                showMaskEditor = false
            )
        }
    }
    
    /**
     * 完成遮罩编辑并保存路径信息
     */
    fun completeMaskEditing(allPaths: List<DrawingPath>, canvasSize: androidx.compose.ui.geometry.Size) {
        val paramId = _uiState.value.maskEditingParamId
        if (paramId != null) {
            val timestamp = System.currentTimeMillis()
            val originalData = pendingImageUploads[paramId]
            
            if (originalData != null) {
                // 更新暂存数据：保持原图，更新遮罩路径
                pendingImageUploads[paramId] = originalData.copy(
                    maskPaths = allPaths
                )
                
                // 更新参数值：保存包含路径信息的完整数据
                val imageKey = "img_${timestamp}_${paramId}"
                val paramValue = ImageParameterValue(
                    originalImageKey = imageKey,
                    maskPaths = allPaths.map { it.toSerializable() },
                    canvasSize = canvasSize.toSerializable(),
                    lastModified = timestamp
                )
                
                updateParameterValue(paramId, JsonPrimitive(kotlinx.serialization.json.Json.encodeToString(ImageParameterValue.serializer(), paramValue)))
                
                // 更新UI预览缓存（显示原图）- 确保缓存持久化
                imagePreviews[paramId] = originalData.originalBytes
                android.util.Log.d("WorkflowParserVM", "更新预览缓存: $paramId, 大小: ${originalData.originalBytes.size}")
            }
            
            // 关闭遮罩编辑器
            cancelMaskEditing()
        }
    }
    


    // 待上传图片的暂存区（按参数ID存储）
    private val pendingImageUploads: MutableMap<String, PendingImageData> = ConcurrentHashMap()
    // 仅用于UI预览的图片缓存（执行后仍保留，用于返回时继续显示预览）
    private val imagePreviews: MutableMap<String, ByteArray> = ConcurrentHashMap()
    
    // 🎥 待上传视频的暂存区（按参数ID存储）
    data class PendingVideoData(
        val videoUri: Uri,  // 改为 Uri，避免一次性加载到内存
        val filename: String,
        val fileSize: Long,
        val duration: Long = 0
    )
    private val pendingVideoUploads: MutableMap<String, PendingVideoData> = ConcurrentHashMap()
    
    // 🎵 待上传音频的暂存区（按参数ID存储）
    data class PendingAudioData(
        val audioUri: Uri,  // 音频 Uri
        val filename: String,
        val fileSize: Long,
        val duration: Long = 0
    )
    private val pendingAudioUploads: MutableMap<String, PendingAudioData> = ConcurrentHashMap()

    init {
        // 启动通知监听
        notificationService.startMonitoring()
        
        // 监听执行状态变化
        viewModelScope.launch {
            workflowExecutor.executionStatus.collect { executionStatus ->
                _uiState.update { it.copy(executionStatus = executionStatus) }
            }
        }
        // 轮询队列，用于显示 n/N（仅当有连接时）
        viewModelScope.launch {
            while (true) {
                try {
                    val serverUrl = repository.getServerUrl()
                    if (serverUrl.isNotEmpty()) {
                        updateQueueProgress()
                    }
                } catch (_: Exception) { }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private suspend fun updateQueueProgress() {
        val serverUrl = repository.getServerUrl()
        val clientId = repository.getClientId()
        val result = repository.getQueue(serverUrl)
        if (result.isSuccess) {
            val json = result.getOrNull()
            if (json != null) {
                val running = (json["queue_running"] as? kotlinx.serialization.json.JsonArray)?.toList() ?: emptyList()
                val pending = (json["queue_pending"] as? kotlinx.serialization.json.JsonArray)?.toList() ?: emptyList()

                // 统计队列数量（始终更新，包括0）
                val myRunningCount = running.size // 无法区分client，保守估计
                val myPendingCount = pending.size
                val total = myRunningCount + myPendingCount
                
                // 计算当前执行的序号
                val currentIndex = if (total > 1) {
                    (total - myPendingCount).coerceAtLeast(1)
                } else {
                    0
                }
                
                // 始终更新队列信息（包括队列为空的情况）
                _uiState.update { it.copy(queueCurrentIndex = currentIndex, queueTotal = total) }
                
                // 更新通知服务的队列信息
                notificationService.updateQueueInfo(currentIndex, total)
            }
        }
    }
    
    /**
     * 解析工作流（带重复检测）
     * @param workflow 工作流JSON对象
     * @param serverUrl 服务器URL（可选）
     */
    fun parseWorkflow(workflow: JsonObject, serverUrl: String? = null) {
        viewModelScope.launch {
            android.util.Log.d("WorkflowParserVM", "========== parseWorkflow 被调用 ==========")
            
            // 计算工作流哈希值用于检测变更
            val workflowHash = workflow.toString().hashCode().toString()
            val currentState = _uiState.value
            val isNewWorkflow = currentState.currentWorkflowHash != workflowHash
            
            android.util.Log.d("WorkflowParserVM", "工作流哈希: 当前=${currentState.currentWorkflowHash}, 新=${workflowHash}")
            android.util.Log.d("WorkflowParserVM", "isNewWorkflow=$isNewWorkflow, shouldPreserveQuickParams=${currentState.shouldPreserveQuickParams}")
            android.util.Log.d("WorkflowParserVM", "当前快速参数: ${currentState.quickParamsByNode}")
            
            // 如果工作流没有变更且已有解析结果，跳过解析
            if (currentState.currentWorkflowHash == workflowHash && currentState.hasParsedWorkflow) {
                android.util.Log.d("WorkflowParserVM", "✅ 工作流相同且已解析，跳过解析")
                // ✅ 不重置标记，让标记保持到真正需要清空时
                android.util.Log.d("WorkflowParserVM", "========== parseWorkflow 跳过 ==========")
                return@launch
            }
            
            // ✅ 更换工作流时的处理
            if (isNewWorkflow) {
                android.util.Log.d("WorkflowParserVM", "检测到新工作流")
                
                // 清空图片预览与待上传缓存
                pendingImageUploads.clear()
                imagePreviews.clear()
                
                // ✅ 关键逻辑：只有在"真正导入新工作流"且"没有标记保留"时才清空快速参数
                if (currentState.shouldPreserveQuickParams) {
                    // 从历史/存储加载：保留快速参数
                    android.util.Log.d("WorkflowParserVM", "✅ 从历史/存储加载，保留快速参数")
                    // ✅ 加载完成后重置标记（避免影响后续操作）
                    _uiState.update { it.copy(shouldPreserveQuickParams = false) }
                } else if (currentState.quickParamsByNode.isNotEmpty()) {
                    // 导入新工作流：清空旧的快速参数
                    android.util.Log.d("WorkflowParserVM", "❌ 导入新工作流，清空旧的快速参数")
                    // ✅ 清空快速参数，同时重置标记
                    _uiState.update { it.copy(quickParamsByNode = emptyMap(), shouldPreserveQuickParams = false) }
                }
            } else {
                android.util.Log.d("WorkflowParserVM", "同一工作流，保持快速参数不变")
            }

            _uiState.update { it.copy(isParsing = true, showError = false, currentWorkflowHash = workflowHash) }
            
            // 存储原始工作流
            originalWorkflow = workflow
            
            try {
                // 仅使用缓存的 objectInfo（不在解析阶段发起网络请求）
                val objectInfo = repository.getCachedObjectInfo()
                
                // 解析工作流
                val parseResult = workflowParser.parse(workflow, objectInfo)
                
                if (parseResult.success) {
                    // 按节点实例分组参数，并获取节点标题
                    val nodeInstances = parseResult.editableParams
                        .groupBy { it.nodeId }
                        .map { (nodeId, params) ->
                            val classType = params.first().classType
                            // 优先使用工作流中的自定义标题，否则使用中文节点名称
                            val nodeTitle = getNodeTitle(workflow, nodeId) 
                                ?: getChineseNodeName(classType)
                            NodeInstance(
                                nodeId = nodeId,
                                classType = classType,
                                title = nodeTitle,
                                parameters = params
                            )
                        }
                        .sortedBy { it.nodeId.toIntOrNull() ?: Int.MAX_VALUE } // 按节点ID排序
                    
                    // 初始化参数值（自动将种子改为10位数字）
                    val parameterValues = parseResult.editableParams.associate { param ->
                        // 🎲 种子参数：自动改为10位数字
                        if (param.uiConfig.isSeed || param.name.lowercase().contains("seed")) {
                            android.util.Log.d("WorkflowParserVM", "📥 处理种子参数 ${param.id}:")
                            android.util.Log.d("WorkflowParserVM", "   原始值: ${param.value}")
                            
                            // 提取当前种子值
                            val currentSeed = if (param.value is JsonPrimitive) {
                                try {
                                    val content = param.value.content
                                    content.toLongOrNull()
                                } catch (e: Exception) {
                                    null
                                }
                            } else {
                                null
                            }
                            
                            // 检查是否为10位数（1000000000 到 9999999999）
                            val is10Digits = currentSeed != null && currentSeed in 1000000000L..9999999999L
                            
                            val finalSeed = if (is10Digits) {
                                // 已经是10位数，保持不变
                                android.util.Log.d("WorkflowParserVM", "   ✅ 已是10位数，保持: $currentSeed")
                                currentSeed!!
                            } else {
                                // 不是10位数，生成新的10位随机数
                                val newSeed = kotlin.random.Random.nextLong(1000000000L, 10000000000L)
                                android.util.Log.d("WorkflowParserVM", "   🔄 原值($currentSeed)不是10位数，生成新种子: $newSeed")
                                newSeed
                            }
                            
                            param.id to JsonPrimitive(finalSeed)
                        } else {
                            param.id to param.value
                        }
                    }
                    
                    // 默认展开第一个组
                    val expandedGroups = if (nodeInstances.isNotEmpty()) {
                        setOf(nodeInstances.first().nodeId)
                    } else emptySet()
                    
                    // 初始化种子模式：默认固定
                    val defaultSeedModes = parseResult.editableParams
                        .filter { it.uiConfig.isSeed || it.name.lowercase().contains("seed") }
                        .associate { it.id to SeedMode.FIXED }

                    _uiState.update { currentState ->
                        // 从历史记录恢复时，把待注入的参数值合并进来（仅覆盖工作流中真实存在的参数）
                        val pending = currentState.pendingParameterValues
                        val mergedValues = if (pending.isNullOrEmpty()) {
                            parameterValues
                        } else {
                            val valid = pending.filterKeys { parameterValues.containsKey(it) }
                            android.util.Log.d(
                                "WorkflowParserVM",
                                "恢复历史参数: 待注入 ${pending.size} 个, 有效 ${valid.size} 个"
                            )
                            parameterValues + valid
                        }

                        currentState.copy(
                            isParsing = false,
                            hasParsedWorkflow = true,
                            parseResult = parseResult,
                            parameterValues = mergedValues,
                            nodeInstances = nodeInstances,
                            expandedGroups = expandedGroups,
                            showError = false,
                            errorMessage = "",
                            seedModes = defaultSeedModes,
                            pendingParameterValues = null
                            // ✅ 不在这里重置标记，让标记在整个加载流程中保持
                        )
                    }
                } else {
                    _uiState.update { currentState ->
                        currentState.copy(
                            isParsing = false,
                            showError = true,
                            errorMessage = parseResult.errorMessage
                        )
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isParsing = false,
                        showError = true,
                        errorMessage = "解析异常: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 更新参数值
     */
    fun updateParameterValue(paramId: String, value: JsonElement) {
        _uiState.update { currentState ->
            currentState.copy(
                parameterValues = currentState.parameterValues + (paramId to value)
            )
        }
    }
    
    /**
     * 切换组展开状态
     */
    fun toggleGroupExpanded(groupName: String) {
        _uiState.update { currentState ->
            val expandedGroups = currentState.expandedGroups.toMutableSet()
            if (expandedGroups.contains(groupName)) {
                expandedGroups.remove(groupName)
            } else {
                expandedGroups.add(groupName)
            }
            currentState.copy(expandedGroups = expandedGroups)
        }
    }
    
    /**
     * 展开所有组
     */
    fun expandAllGroups() {
        _uiState.update { currentState ->
            currentState.copy(
                expandedGroups = currentState.nodeInstances.map { it.nodeId }.toSet()
            )
        }
    }
    
    /**
     * 收起所有组
     */
    fun collapseAllGroups() {
        _uiState.update { it.copy(expandedGroups = emptySet()) }
    }
    
    /**
     * 获取更新后的工作流
     */
    fun getUpdatedWorkflow(): JsonObject? {
        val currentState = _uiState.value
        val originalWorkflow = currentState.parseResult?.workflow ?: return null
        val parameterValues = currentState.parameterValues
        
        return updateWorkflowWithParameters(originalWorkflow, parameterValues)
    }
    
    /**
     * 使用新参数值更新工作流
     */
    private fun updateWorkflowWithParameters(
        workflow: JsonObject,
        parameterValues: Map<String, JsonElement>
    ): JsonObject {
        val updatedWorkflow = workflow.toMutableMap()
        
        // 遍历参数值更新工作流
        for ((paramId, newValue) in parameterValues) {
            val parts = paramId.split("_", limit = 2)
            if (parts.size != 2) continue
            
            val nodeId = parts[0]
            val paramName = parts[1]
            
            val nodeElement = updatedWorkflow[nodeId]
            if (nodeElement is JsonObject) {
                val nodeObj = nodeElement.toMutableMap()
                val inputs = nodeObj["inputs"]
                
                if (inputs is JsonObject) {
                    val inputsObj = inputs.toMutableMap()
                    inputsObj[paramName] = newValue
                    nodeObj["inputs"] = JsonObject(inputsObj)
                }
                
                updatedWorkflow[nodeId] = JsonObject(nodeObj)
            }
        }
        
        return JsonObject(updatedWorkflow)
    }
    
    /**
     * 重置参数值到默认值
     */
    fun resetParameterValues() {
        val currentState = _uiState.value
        val parseResult = currentState.parseResult ?: return
        
        val defaultValues = parseResult.editableParams.associate { param ->
            param.id to param.value
        }
        
        _uiState.update { it.copy(parameterValues = defaultValues) }
    }
    
    /**
     * 清除解析结果（保留展开状态以维持用户体验）
     */
    /**
     * 从工作流JSON中获取节点标题
     */
    private fun getNodeTitle(workflow: JsonObject, nodeId: String): String? {
        return try {
            val nodeData = workflow[nodeId] as? JsonObject
            
            // 优先获取 _meta.title 字段（用户自定义的中文名称）
            val meta = nodeData?.get("_meta") as? JsonObject
            val title = (meta?.get("title") as? JsonPrimitive)?.contentOrNull
            if (!title.isNullOrBlank()) {
                return title
            }
            
            // 其次尝试 inputs.text 字段（对于文本类节点）
            val inputs = nodeData?.get("inputs") as? JsonObject
            val text = (inputs?.get("text") as? JsonPrimitive)?.contentOrNull
            if (!text.isNullOrBlank() && text.length <= 20) { // 限制长度，避免把长文本当作标题
                return text
            }
            
            return null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将英文节点类型转换为中文名称
     */
    private fun getChineseNodeName(classType: String): String {
        return when (classType) {
            // 加载器类
            "CheckpointLoaderSimple" -> "检查点加载器"
            "CheckpointLoader" -> "检查点加载器（高级）"
            "LoraLoader" -> "LoRA加载器"
            "VAELoader" -> "VAE加载器"
            "CLIPLoader" -> "CLIP加载器"
            "UNETLoader" -> "UNET加载器"
            "ControlNetLoader" -> "ControlNet加载器"
            "ControlNetLoaderAdvanced" -> "ControlNet加载器（高级）"
            "UpscaleModelLoader" -> "放大模型加载器"
            
            // 文本编码
            "CLIPTextEncode" -> "CLIP文本编码"
            "CLIPTextEncodeSDXL" -> "CLIP文本编码（SDXL）"
            "CLIPTextEncodeSDXLRefiner" -> "CLIP文本编码（SDXL精修）"
            
            // 采样器
            "KSampler" -> "K采样器"
            "KSamplerAdvanced" -> "K采样器（高级）"
            "SamplerCustom" -> "自定义采样器"
            "SamplerDPMPP_2M" -> "DPM++2M采样器"
            "SamplerDPMPP_SDE" -> "DPM++SDE采样器"
            "SamplerEuler" -> "Euler采样器"
            "SamplerEulerAncestral" -> "EulerA采样器"
            "SamplerLMS" -> "LMS采样器"
            
            // VAE处理
            "VAEDecode" -> "VAE解码"
            "VAEEncode" -> "VAE编码"
            "VAEEncodeForInpaint" -> "VAE编码（修复）"
            
            // 图像处理
            "ImageScale" -> "图像缩放"
            "ImageUpscaleWithModel" -> "模型放大图像"
            "ImageInvert" -> "图像反色"
            "ImageBlur" -> "图像模糊"
            "ImageSharpen" -> "图像锐化"
            "ImageResize" -> "图像调整大小"
            "LoadImage" -> "加载图像"
            "SaveImage" -> "保存图像"
            "PreviewImage" -> "预览图像"
            
            // ControlNet
            "ControlNetApply" -> "应用ControlNet"
            "ControlNetApplyAdvanced" -> "应用ControlNet（高级）"
            "CannyEdgePreprocessor" -> "Canny边缘预处理"
            "OpenposePreprocessor" -> "Openpose预处理"
            "DepthMapPreprocessor" -> "深度图预处理"
            
            // 条件处理
            "ConditioningAverage" -> "条件平均"
            "ConditioningCombine" -> "条件组合"
            "ConditioningSetArea" -> "条件设置区域"
            "ConditioningSetMask" -> "条件设置遮罩"
            
            // 遮罩处理
            "MaskComposite" -> "遮罩合成"
            "MaskToImage" -> "遮罩转图像"
            "ImageToMask" -> "图像转遮罩"
            
            // 批处理
            "BatchManager" -> "批处理管理器"
            "ImageBatch" -> "图像批处理"
            
            // 数学运算
            "MathExpression" -> "数学表达式"
            "FloatConstant" -> "浮点常量"
            "IntConstant" -> "整数常量"
            
            // 潜在空间处理
            "LatentUpscale" -> "潜在空间放大"
            "LatentUpscaleBy" -> "潜在空间按比例放大"
            "LatentRotate" -> "潜在空间旋转"
            "LatentFlip" -> "潜在空间翻转"
            "LatentCrop" -> "潜在空间裁剪"
            "EmptyLatentImage" -> "空白潜在图像"
            
            // 高级功能
            "FreeU" -> "FreeU"
            "HyperTile" -> "HyperTile"
            "PatchModelAddDownscale" -> "模型补丁下采样"
            "ModelMergeSimple" -> "简单模型合并"
            "ModelMergeBlocks" -> "分块模型合并"
            
            // 其他常用节点
            "Note" -> "注释"
            "Reroute" -> "重新路由"
            "PrimitiveNode" -> "原始节点"
            "PreviewTextNode" -> "文本预览节点"
            
            // 如果没有匹配的中文名称，返回格式化的英文名称
            else -> {
                // 将驼峰命名转换为更友好的格式
                classType.replace(Regex("([A-Z])"), " $1").trim()
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.lowercase().replaceFirstChar { it.uppercase() }
                    }
            }
        }
    }

    fun clearParseResult() {
        _uiState.update { currentState ->
            currentState.copy(
                isParsing = false,
                hasParsedWorkflow = false,
                parseResult = null,
                parameterValues = emptyMap(),
                nodeInstances = emptyList(),
                currentWorkflowHash = "",
                showError = false,
                errorMessage = ""
                // 注意：保留 expandedGroups 状态，不重置
            )
        }
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { it.copy(showError = false, errorMessage = "") }
    }
    
    /**
     * 获取当前参数值
     */
    fun getCurrentParameterValue(paramId: String): JsonElement? {
        return _uiState.value.parameterValues[paramId]
    }
    
    /**
     * 检查是否有未保存的更改
     */
    fun hasUnsavedChanges(): Boolean {
        val currentState = _uiState.value
        val parseResult = currentState.parseResult ?: return false
        
        // 比较当前值与原始值
        for (param in parseResult.editableParams) {
            val currentValue = currentState.parameterValues[param.id]
            if (currentValue != param.value) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * 上传图片到ComfyUI服务器
     */
    suspend fun uploadImage(
        imageData: ByteArray,
        filename: String
    ): Result<String> {
        // 不立即上传，先暂存，执行时统一上传（对齐Web逻辑）
        return try {
            // 找到当前正处于交互的参数ID：通过最近一次值更新无法获知，这里改为由调用方决定。
            // 由于当前ImageUploadInput无法传入paramId，这里采用“由屏幕逻辑负责调用updateParameterValue后再调用uploadImage”的约定：
            // 直接返回一个占位名，执行时根据占位名+参数ID进行解析和上传。
            // 为了能定位具体参数，这里只返回占位前缀，具体映射在updateParameterValue中完成。
            Result.success("staged::${System.currentTimeMillis()}::$filename").also {
                // 暂不在此处写入pendingImageUploads，改由updateParameterValue承接（见下方重载）
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 重载：提供在参数更新时附带暂存数据的能力
    fun stageImageForParam(paramId: String, bytes: ByteArray, filename: String) {
        val timestamp = System.currentTimeMillis()
        val imageKey = "img_${timestamp}_${filename.hashCode()}"
        
        // 存储为新的数据结构：原图+空遮罩路径
        pendingImageUploads[paramId] = PendingImageData(
            originalBytes = bytes,
            filename = filename,
            maskPaths = emptyList()
        )
        
        // 更新参数值为包含原图信息的结构，Canvas尺寸将在首次编辑时设置
        val paramValue = ImageParameterValue(
            originalImageKey = imageKey,
            maskPaths = emptyList(),
            canvasSize = null, // 🔥 首次上传时不设置Canvas尺寸，将在编辑或执行时动态计算
            lastModified = timestamp
        )
        
        updateParameterValue(paramId, JsonPrimitive(kotlinx.serialization.json.Json.encodeToString(ImageParameterValue.serializer(), paramValue)))
        
        // UI预览缓存（显示原图）
        imagePreviews[paramId] = bytes
    }
    
    // 提供读取与清理暂存图片的接口，便于参数页返回后仍可显示预览
    fun getStagedImageBytes(paramId: String): ByteArray? {
        return imagePreviews[paramId]
    }
    
    // 🎥 暂存视频数据（用于视频上传参数）
    fun stageVideoForParam(paramId: String, uri: Uri, filename: String, fileSize: Long, duration: Long = 0) {
        android.util.Log.d("WorkflowParserVM", "🎥 开始暂存视频: paramId=$paramId, filename=$filename, size=$fileSize")
        
        pendingVideoUploads[paramId] = PendingVideoData(
            videoUri = uri,
            filename = filename,
            fileSize = fileSize,
            duration = duration
        )
        
        // 更新参数值为文件名
        updateParameterValue(paramId, JsonPrimitive(filename))
        
        // 🖼️ 异步生成视频缩略图
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val thumbnail = com.example.myapplication.util.VideoUtils.getVideoThumbnail(context, uri)
                if (thumbnail != null) {
                    android.util.Log.d("WorkflowParserVM", "✅ 视频缩略图已生成: ${thumbnail.width}x${thumbnail.height}")
                    
                    // 🔄 安全地更新状态：将缩略图放入 UiState
                    _uiState.update { currentState ->
                        currentState.copy(
                            videoThumbnails = currentState.videoThumbnails + (paramId to thumbnail)
                        )
                    }
                } else {
                    android.util.Log.w("WorkflowParserVM", "⚠️ 无法生成视频缩略图")
                }
            } catch (e: Exception) {
                android.util.Log.e("WorkflowParserVM", "❌ 生成视频缩略图失败: ${e.message}", e)
            }
        }
        
        android.util.Log.d("WorkflowParserVM", "✅ 视频已暂存: 当前暂存数=${pendingVideoUploads.size}")
    }
    
    // 🎥 获取暂存的视频数据
    fun getPendingVideoData(paramId: String): PendingVideoData? {
        return pendingVideoUploads[paramId]
    }
    
    // 🎥 获取视频缩略图
    fun getVideoThumbnail(paramId: String): android.graphics.Bitmap? {
        return _uiState.value.videoThumbnails[paramId]
    }
    
    // 🎥 获取视频 Uri（用于播放）
    fun getVideoUri(paramId: String): Uri? {
        return pendingVideoUploads[paramId]?.videoUri
    }
    
    // 🎥 移除视频（清理暂存数据和缩略图）
    fun removeVideo(paramId: String) {
        android.util.Log.d("WorkflowParserVM", "🗑️ 移除视频: paramId=$paramId")
        
        // 清理暂存数据
        pendingVideoUploads.remove(paramId)
        
        // 清理缩略图
        _uiState.update { currentState ->
            currentState.copy(
                videoThumbnails = currentState.videoThumbnails - paramId
            )
        }
        
        // 清空参数值
        updateParameterValue(paramId, JsonPrimitive(""))
    }
    
    // 🎵 暂存音频数据（用于音频上传参数）
    fun stageAudioForParam(paramId: String, uri: Uri, filename: String, fileSize: Long, duration: Long = 0) {
        android.util.Log.d("WorkflowParserVM", "🎵 开始暂存音频: paramId=$paramId, filename=$filename, size=$fileSize")
        
        pendingAudioUploads[paramId] = PendingAudioData(
            audioUri = uri,
            filename = filename,
            fileSize = fileSize,
            duration = duration
        )
        
        // 更新参数值为文件名
        updateParameterValue(paramId, JsonPrimitive(filename))
        
        android.util.Log.d("WorkflowParserVM", "✅ 音频已暂存: 当前暂存数=${pendingAudioUploads.size}")
    }
    
    // 🎵 获取暂存的音频数据
    fun getPendingAudioData(paramId: String): PendingAudioData? {
        return pendingAudioUploads[paramId]
    }
    
    // 🎵 获取音频 Uri（用于播放）
    fun getAudioUri(paramId: String): Uri? {
        return pendingAudioUploads[paramId]?.audioUri
    }
    
    // 🎵 移除音频（清理暂存数据）
    fun removeAudio(paramId: String) {
        android.util.Log.d("WorkflowParserVM", "🗑️ 移除音频: paramId=$paramId")
        
        // 清理暂存数据
        pendingAudioUploads.remove(paramId)
        
        // 清空参数值
        updateParameterValue(paramId, JsonPrimitive(""))
    }

    /**
     * 根据原图和遮罩路径生成带Alpha通道的合成图片
     */
    private suspend fun generateMaskedImageFromPaths(
        pendingData: PendingImageData
    ): ByteArray = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val originalBitmap = ImageUtils.byteArrayToBitmap(pendingData.originalBytes)
            ?: throw Exception("无法解码原始图片")
        
        // 🔥 简化逻辑：直接使用已保存的Canvas尺寸，如果没有就用原图尺寸
        val canvasSize = try {
            // 找到对应的参数ID
            val currentParamId = _uiState.value.parameterValues.entries.find { (paramId, value) ->
                val pending = pendingImageUploads[paramId]
                pending != null && pending.filename == pendingData.filename
            }?.key
            
            if (currentParamId != null) {
                val currentValue = _uiState.value.parameterValues[currentParamId]
                val valueStr = (currentValue as? JsonPrimitive)?.contentOrNull
                if (valueStr != null && !valueStr.startsWith("staged::")) {
                    try {
                        val imageParam = kotlinx.serialization.json.Json.decodeFromString<ImageParameterValue>(valueStr)
                        if (imageParam.canvasSize != null) {
                            val savedSize = imageParam.canvasSize.toSize()
                            android.util.Log.d("WorkflowParserVM", "✅ 使用已保存的Canvas尺寸: ${savedSize.width}x${savedSize.height}")
                            savedSize
                        } else {
                            // 没有保存的Canvas尺寸，使用原图尺寸
                            val originalSize = androidx.compose.ui.geometry.Size(originalBitmap.width.toFloat(), originalBitmap.height.toFloat())
                            android.util.Log.d("WorkflowParserVM", "⚠️ 使用原图尺寸作为Canvas: ${originalSize.width}x${originalSize.height}")
                            originalSize
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("WorkflowParserVM", "解析Canvas尺寸失败，使用原图尺寸: ${e.message}")
                        androidx.compose.ui.geometry.Size(originalBitmap.width.toFloat(), originalBitmap.height.toFloat())
                    }
                } else {
                    android.util.Log.w("WorkflowParserVM", "参数值无效，使用原图尺寸")
                    androidx.compose.ui.geometry.Size(originalBitmap.width.toFloat(), originalBitmap.height.toFloat())
                }
            } else {
                android.util.Log.w("WorkflowParserVM", "找不到参数ID，使用原图尺寸")
                androidx.compose.ui.geometry.Size(originalBitmap.width.toFloat(), originalBitmap.height.toFloat())
            }
        } catch (e: Exception) {
            android.util.Log.e("WorkflowParserVM", "获取Canvas尺寸失败，使用原图尺寸: ${e.message}")
            androidx.compose.ui.geometry.Size(originalBitmap.width.toFloat(), originalBitmap.height.toFloat())
        }
        
        android.util.Log.d("WorkflowParserVM", "合成遮罩图片 - 原图尺寸: ${originalBitmap.width}x${originalBitmap.height}, Canvas尺寸: ${canvasSize.width}x${canvasSize.height}")
        
        // 使用ImageMaskEditor中的合成函数
        val maskedBitmap = generateMaskedImageFast(
            originalBitmap, 
            pendingData.maskPaths, 
            canvasSize
        )
        
        ImageUtils.bitmapToByteArray(maskedBitmap)
    }
    
    // 已移除清除图片按钮，保留预览以便返回继续显示

    /**
     * 快速参数选择：切换选中/取消某个参数
     */
    fun toggleQuickParam(paramId: String) {
        val parts = paramId.split("_", limit = 2)
        if (parts.size != 2) return
        val nodeId = parts[0]
        _uiState.update { state ->
            val current = state.quickParamsByNode[nodeId] ?: emptySet()
            val next = if (current.contains(paramId)) current - paramId else current + paramId
            state.copy(quickParamsByNode = state.quickParamsByNode + (nodeId to next))
        }
    }

    fun isQuickParamSelected(paramId: String): Boolean {
        val parts = paramId.split("_", limit = 2)
        if (parts.size != 2) return false
        val nodeId = parts[0]
        return _uiState.value.quickParamsByNode[nodeId]?.contains(paramId) == true
    }

    fun clearQuickParams() {
        _uiState.update { it.copy(quickParamsByNode = emptyMap()) }
    }

    fun clearQuickParamsOfNode(nodeId: String) {
        _uiState.update { state ->
            if (!state.quickParamsByNode.containsKey(nodeId)) state
            else state.copy(quickParamsByNode = state.quickParamsByNode - nodeId)
        }
    }
    
    /**
     * 获取当前快速参数配置（用于保存）
     * @return 序列化的快速参数映射（节点ID -> 参数ID列表）
     */
    fun getCurrentQuickParams(): Map<String, List<String>>? {
        val quickParams = _uiState.value.quickParamsByNode
        if (quickParams.isEmpty()) return null
        return quickParams.mapValues { it.value.toList() }
    }
    
    /**
     * 恢复快速参数配置（用于加载工作流时）
     * @param quickParams 序列化的快速参数映射
     */
    fun restoreQuickParams(quickParams: Map<String, List<String>>?) {
        android.util.Log.d("WorkflowParserVM", "========== restoreQuickParams 被调用 ==========")
        android.util.Log.d("WorkflowParserVM", "传入的 quickParams: $quickParams")
        
        // ✅ 原子更新：一次性清空旧的并恢复新的，避免竞态条件
        if (quickParams == null || quickParams.isEmpty()) {
            _uiState.update { state ->
                state.copy(
                    quickParamsByNode = emptyMap(),
                    shouldPreserveQuickParams = false
                )
            }
            android.util.Log.d("WorkflowParserVM", "没有快速参数配置，已清空")
        } else {
            val restoredMap = quickParams.mapValues { it.value.toSet() }
            android.util.Log.d("WorkflowParserVM", "转换后的 restoredMap: $restoredMap")
            _uiState.update { state ->
                val newState = state.copy(
                    quickParamsByNode = restoredMap,
                    shouldPreserveQuickParams = true  // ✅ 标记应该保留快速参数
                )
                android.util.Log.d("WorkflowParserVM", "更新后的状态 - quickParamsByNode: ${newState.quickParamsByNode}, shouldPreserveQuickParams: ${newState.shouldPreserveQuickParams}")
                newState
            }
            android.util.Log.d("WorkflowParserVM", "✅ 已恢复 ${restoredMap.size} 个节点的快速参数，并标记保留")
        }
        android.util.Log.d("WorkflowParserVM", "========== restoreQuickParams 完成 ==========")
    }

    /**
     * 恢复执行时的参数值（用于从历史记录左滑复用工作流）。
     *
     * 必须在导入工作流之前调用：解析完成后会一次性合并进 parameterValues。
     * 传 null 表示不恢复任何历史参数。
     */
    fun restoreParameterValues(values: Map<String, kotlinx.serialization.json.JsonElement>?) {
        android.util.Log.d(
            "WorkflowParserVM",
            "restoreParameterValues: ${values?.size ?: 0} 项待恢复"
        )
        _uiState.update { state ->
            state.copy(pendingParameterValues = values?.takeIf { it.isNotEmpty() })
        }
    }
    
    /**
     * 执行工作流
     */
    fun executeWorkflow() {
        viewModelScope.launch {
            executeOnce()
        }
    }

    fun executeWorkflowBatch(times: Int) {
        viewModelScope.launch {
            val count = times.coerceAtLeast(1)
            repeat(count) {
                val result = executeOnce()
                if (result.isFailure) return@launch
            }
        }
    }

    private suspend fun executeOnce(): Result<Unit> {
        val currentState = _uiState.value
        val workflow = originalWorkflow

        if (workflow == null) {
            _uiState.update { 
                it.copy(
                    errorMessage = "没有可执行的工作流",
                    showError = true
                ) 
            }
            return Result.failure(IllegalStateException("No workflow"))
        }

        return try {
            // 1) 在执行前，统一处理上传型参数（如 LoadImage.image）
            val parse = currentState.parseResult
            val paramIdToParam = parse?.editableParams?.associateBy { it.id } ?: emptyMap()
            val resolvedParams = currentState.parameterValues.toMutableMap()
            val updatedUiParams = currentState.parameterValues.toMutableMap()

            // 0) ComfyUI标准种子逻辑：执行前先根据模式变换种子，然后用新种子执行
            android.util.Log.d("WorkflowParserVM", "========== 开始种子变换 ==========")
            
            for ((paramId, param) in paramIdToParam) {
                val isSeed = param.uiConfig.isSeed || param.name.lowercase().contains("seed")
                if (!isSeed) continue
                
                val mode = getSeedMode(paramId)
                
                // 从UI状态中获取用户输入的最新值
                val userInputValue = currentState.parameterValues[paramId]
                android.util.Log.d("WorkflowParserVM", "🔍 种子参数 $paramId 详细信息:")
                android.util.Log.d("WorkflowParserVM", "   UI状态中的值: $userInputValue")
                android.util.Log.d("WorkflowParserVM", "   resolvedParams中的值: ${resolvedParams[paramId]}")
                
                val curEl = resolvedParams[paramId]
                val cur = (curEl as? JsonPrimitive)?.let { p ->
                    val txt = p.contentOrNull
                    txt?.toLongOrNull()
                }
                
                android.util.Log.d("WorkflowParserVM", "🎲 准备变换:")
                android.util.Log.d("WorkflowParserVM", "   📝 解析后的Long值: $cur")
                android.util.Log.d("WorkflowParserVM", "   🎯 选择的模式: $mode")
                
                // 根据模式计算新的种子值（基于输入框的值进行变换）
                val newSeed: Long = when (mode) {
                    SeedMode.RANDOM -> {
                        // 随机模式：生成10位随机数
                        val randomSeed = kotlin.random.Random.nextLong(1000000000L, 10000000000L)
                        android.util.Log.d("WorkflowParserVM", "   🎲 随机模式：生成新种子 $randomSeed")
                        randomSeed
                    }
                    SeedMode.FIXED -> {
                        // 固定模式：保持当前值不变
                        val fixedSeed = cur ?: kotlin.random.Random.nextLong(1000000000L, 10000000000L)
                        android.util.Log.d("WorkflowParserVM", "   📌 固定模式：保持种子 $fixedSeed")
                        fixedSeed
                    }
                    SeedMode.INCREMENT -> {
                        // 递增模式：当前值+1（无限制，基于用户输入的任何值）
                        val base = cur ?: 0L
                        val incremented = base + 1L
                        android.util.Log.d("WorkflowParserVM", "   ➕ 递增模式：$base + 1 = $incremented")
                        incremented
                    }
                    SeedMode.DECREMENT -> {
                        // 递减模式：当前值-1（无限制，基于用户输入的任何值）
                        val base = cur ?: 0L
                        val decremented = if (base > 0) base - 1L else 0L
                        android.util.Log.d("WorkflowParserVM", "   ➖ 递减模式：$base - 1 = $decremented")
                        decremented
                    }
                }
                
                // 用新种子执行（提交给服务器）
                // 🔥 确保使用Long类型的JsonPrimitive，避免转成Double
                val seedPrimitive = JsonPrimitive(newSeed)
                resolvedParams[paramId] = seedPrimitive
                // 立即更新UI显示的种子值
                updatedUiParams[paramId] = seedPrimitive
                
                android.util.Log.d("WorkflowParserVM", "")
                android.util.Log.d("WorkflowParserVM", "✅ 种子变换完成:")
                android.util.Log.d("WorkflowParserVM", "   📥 变换前（输入框值）: $cur")
                android.util.Log.d("WorkflowParserVM", "   📤 变换后（新种子）: $newSeed")
                android.util.Log.d("WorkflowParserVM", "   🎯 使用模式: $mode")
                android.util.Log.d("WorkflowParserVM", "   💾 已更新UI和执行参数")
                android.util.Log.d("WorkflowParserVM", "")
            }
            
            // 立即更新UI，让用户看到变换后的种子值
            _uiState.update { it.copy(parameterValues = updatedUiParams) }

            for ((paramId, param) in paramIdToParam) {
                val uiType = param.uiConfig.type
                if (uiType == WorkflowParser.UiType.UPLOAD_IMAGE) {
                    val currentVal = resolvedParams[paramId]
                    val stagedMarker = (currentVal as? JsonPrimitive)?.contentOrNull
                    if (stagedMarker != null) {
                        // 检查是否是新的ImageParameterValue格式
                        val pending = pendingImageUploads[paramId]
                        if (pending != null) {
                            val serverUrl = repository.getServerUrl()
                            if (serverUrl.isEmpty()) throw Exception("服务器地址未配置")
                            
                            // 🔥 关键：根据是否有遮罩路径决定上传内容
                            val finalImageBytes = if (pending.maskPaths.isNotEmpty()) {
                                // 有遮罩路径：后台合成Alpha通道图片
                                android.util.Log.d("WorkflowParserVM", "参数 $paramId 有 ${pending.maskPaths.size} 个遮罩路径，开始合成Alpha通道图片")
                                generateMaskedImageFromPaths(pending)
                            } else {
                                // 无遮罩：直接使用原图
                                android.util.Log.d("WorkflowParserVM", "参数 $paramId 无遮罩路径，直接使用原图")
                                pending.originalBytes
                            }
                            
                            val upload = repository.uploadImage(serverUrl, finalImageBytes, pending.filename)
                            if (upload.isSuccess) {
                                val serverName = upload.getOrThrow().name
                                android.util.Log.d("WorkflowParserVM", "已上传参数 $paramId -> $serverName")
                                // 🔥 关键修复：只更新工作流执行用的参数，不覆盖UI参数值
                                resolvedParams[paramId] = JsonPrimitive(serverName)
                                // ❌ 不要覆盖UI参数值，保持ImageParameterValue结构用于后续执行
                                // updatedUiParams[paramId] = JsonPrimitive(serverName)
                                android.util.Log.d("WorkflowParserVM", "🔄 保持UI参数值为ImageParameterValue结构，不覆盖为服务器文件名")
                            } else {
                                throw upload.exceptionOrNull() ?: Exception("图片上传失败")
                            }
                        } else {
                            // 兼容旧格式的staged标记
                            if (stagedMarker.startsWith("staged::")) {
                                throw Exception("图片未暂存：$paramId")
                            }
                        }
                    }
                }
                
                // 🎥 处理视频上传参数
                if (uiType == WorkflowParser.UiType.UPLOAD_VIDEO) {
                    val pending = pendingVideoUploads[paramId]
                    if (pending != null) {
                        val serverUrl = repository.getServerUrl()
                        if (serverUrl.isEmpty()) throw Exception("服务器地址未配置")
                        
                        android.util.Log.d("WorkflowParserVM", "🎥 开始上传视频: paramId=$paramId, filename=${pending.filename}, size=${pending.fileSize} bytes")
                        android.util.Log.d("WorkflowParserVM", "📍 调用 uploadVideoStream（流式上传，边读边传）")
                        
                        // 更新 UI 状态：开始上传
                        _uiState.update {
                            it.copy(
                                isUploadingVideo = true,
                                videoUploadProgress = 0f,
                                videoUploadFileName = pending.filename
                            )
                        }
                        
                        // 流式上传视频到服务器（边读边传，只有一个进度）
                        val upload = repository.uploadVideoStream(
                            context = context,
                            serverUrl = serverUrl,
                            videoUri = pending.videoUri,
                            filename = pending.filename,
                            fileSize = pending.fileSize,
                            subfolder = "",
                            onProgress = { uploaded, total ->
                                val progress = if (total > 0) (uploaded * 100 / total) else 0
                                val progressFloat = if (total > 0) (uploaded.toFloat() / total.toFloat()) else 0f
                                
                                // 更新 UI 进度
                                _uiState.update {
                                    it.copy(videoUploadProgress = progressFloat)
                                }
                                
                                android.util.Log.d("WorkflowParserVM", "🎥 上传进度: $progress% ($uploaded/$total)")
                            }
                        )
                        
                        // 上传完成，重置状态
                        _uiState.update {
                            it.copy(
                                isUploadingVideo = false,
                                videoUploadProgress = 0f,
                                videoUploadFileName = ""
                            )
                        }
                        
                        if (upload.isSuccess) {
                            val serverName = upload.getOrThrow().name
                            android.util.Log.d("WorkflowParserVM", "✅ 视频上传成功: $serverName")
                            // 更新工作流执行用的参数为服务器返回的文件名
                            resolvedParams[paramId] = JsonPrimitive(serverName)
                        } else {
                            throw upload.exceptionOrNull() ?: Exception("视频上传失败")
                        }
                    }
                }
                
                // 🎵 处理音频上传参数
                if (uiType == WorkflowParser.UiType.UPLOAD_AUDIO) {
                    val pending = pendingAudioUploads[paramId]
                    if (pending != null) {
                        val serverUrl = repository.getServerUrl()
                        if (serverUrl.isEmpty()) throw Exception("服务器地址未配置")
                        
                        android.util.Log.d("WorkflowParserVM", "🎵 开始上传音频: paramId=$paramId, filename=${pending.filename}, size=${pending.fileSize} bytes")
                        
                        // 流式上传音频到服务器
                        val upload = repository.uploadAudioStream(
                            context = context,
                            serverUrl = serverUrl,
                            audioUri = pending.audioUri,
                            filename = pending.filename,
                            fileSize = pending.fileSize,
                            subfolder = "",
                            onProgress = { uploaded, total ->
                                val progress = if (total > 0) (uploaded * 100 / total) else 0
                                android.util.Log.d("WorkflowParserVM", "🎵 上传进度: $progress% ($uploaded/$total)")
                            }
                        )
                        
                        if (upload.isSuccess) {
                            val serverName = upload.getOrThrow().name
                            android.util.Log.d("WorkflowParserVM", "✅ 音频上传成功: $serverName")
                            // 更新工作流执行用的参数为服务器返回的文件名
                            resolvedParams[paramId] = JsonPrimitive(serverName)
                        } else {
                            throw upload.exceptionOrNull() ?: Exception("音频上传失败")
                        }
                    }
                }
            }

            // 额外兜底
            resolvedParams.forEach { (pid, v) ->
                val s = (v as? JsonPrimitive)?.contentOrNull
                if (s != null && s.startsWith("staged::")) {
                    throw Exception("检测到未替换的图片占位：$pid")
                }
            }

            // 🔥 不要清空pendingImageUploads，保留数据以支持连续编辑
            // pendingImageUploads.clear() // 移除这行，保持数据用于后续编辑

            // ✅ ComfyUI标准批处理：异步执行，不阻塞批量提交
            // 在后台启动执行监听（包括WebSocket监听、进度更新、历史记录保存）
            viewModelScope.launch {
                val execResult = workflowExecutor.executeWorkflow(
                    workflow = workflow,
                    parameterValues = resolvedParams,
                    quickParams = getCurrentQuickParams()  // ✅ 传入当前快速参数配置
                )
                
                if (execResult.isFailure) {
                    val error = execResult.exceptionOrNull()?.message ?: "执行失败"
                    android.util.Log.e("WorkflowParserVM", "❌ 任务执行失败: $error")
                    // 不更新UI错误，因为可能是批量执行中的一个
                }
            }
            
            // 更新UI显示最新的种子值（立即返回，不等待执行完成）
            _uiState.update { it.copy(parameterValues = updatedUiParams) }
            android.util.Log.d("WorkflowParserVM", "✅ 任务已提交到队列（异步执行）")
            
            Result.success(Unit)
        } catch (e: Exception) {
            // 重置视频上传状态（如果有）
            _uiState.update { 
                it.copy(
                    errorMessage = "执行异常: ${e.message}",
                    showError = true,
                    isUploadingVideo = false,
                    videoUploadProgress = 0f,
                    videoUploadFileName = ""
                ) 
            }
            Result.failure(e)
        }
    }
    
    /**
     * 取消执行
     */
    fun cancelExecution() {
        viewModelScope.launch {
            workflowExecutor.cancelExecution()
        }
    }

    /**
     * 清空待处理队列（不影响当前执行项）
     */
    fun clearPendingQueue() {
        viewModelScope.launch {
            val serverUrl = repository.getServerUrl()
            repository.clearQueue(serverUrl)
        }
    }
    
    /**
     * ViewModel 销毁时清理资源
     */
    override fun onCleared() {
        super.onCleared()
        // 停止通知监听
        notificationService.stopMonitoring()
    }
    
    /**
     * 更新批次数量
     */
    fun updateBatchCount(count: Int) {
        _uiState.update { it.copy(batchCount = count.coerceIn(1, 9999)) }
    }

    /**
     * 重置执行状态
     */
    fun resetExecution() {
        workflowExecutor.resetExecution()
    }
    
}


