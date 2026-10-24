package com.example.myapplication.domain.execution

import android.util.Log
import com.example.myapplication.data.repository.ComfyUIRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工作流执行器
 * 负责管理工作流的执行流程和状态
 */
@Singleton
class WorkflowExecutor @Inject constructor(
    private val repository: ComfyUIRepository,
    private val outputExtractor: OutputExtractor,
    private val recordManager: ExecutionRecordManager
) {
    
    companion object {
        private const val TAG = "WorkflowExecutor"
    }
    
    /**
     * 执行状态
     */
    enum class ExecutionState {
        IDLE,           // 空闲
        PREPARING,      // 准备中（处理参数、上传文件等）
        QUEUED,         // 已提交到队列
        EXECUTING,      // 执行中
        COMPLETED,      // 执行完成
        ERROR           // 执行错误
    }
    
    /**
     * 执行结果数据类
     */
    data class ExecutionResult(
        val success: Boolean,
        val promptId: String? = null,
        val outputImages: List<String> = emptyList(),
        val outputVideos: List<String> = emptyList(), // 🎥 视频输出
        val outputAudios: List<String> = emptyList(), // 🎵 音频输出
        val outputTexts: Map<String, List<String>> = emptyMap(), // 📝 文本输出（nodeId -> 文本列表）
        val errorMessage: String? = null,
        val executionTime: Long = 0L
    )
    
    /**
     * 错误类型枚举
     */
    enum class ErrorType {
        NETWORK_ERROR,      // 网络连接错误
        SERVER_ERROR,       // 服务器错误
        TIMEOUT_ERROR,      // 超时错误
        VALIDATION_ERROR,   // 参数验证错误
        WORKFLOW_ERROR,     // 工作流执行错误
        UNKNOWN_ERROR       // 未知错误
    }

    /**
     * 执行状态数据类
     */
    data class ExecutionStatus(
        val state: ExecutionState = ExecutionState.IDLE,
        val promptId: String? = null,
        val progress: Float = 0f,
        val currentNode: String? = null,
        val message: String = "",
        val result: ExecutionResult? = null,
        val errorType: ErrorType? = null,
        val errorDetails: String? = null,
        val canRetry: Boolean = false,
        val retryCount: Int = 0,
        /**
         * 来自 WebSocket `status` 消息的队列剩余量（官方字段：exec_info.queue_remaining）
         * 文档：https://docs.comfy.org/zh-CN/development/comfyui-server/comms_messages
         */
        val queueRemaining: Int? = null
    )
    
    // 执行状态流
    private val _executionStatus = MutableStateFlow(ExecutionStatus())
    val executionStatus: StateFlow<ExecutionStatus> = _executionStatus.asStateFlow()
    
    /**
     * 执行工作流
     */
    suspend fun executeWorkflow(
        workflow: JsonObject,
        parameterValues: Map<String, JsonElement>,
        quickParams: Map<String, List<String>>? = null
    ): Result<ExecutionResult> {
        return try {
            Log.d(TAG, "开始执行工作流")
            
            // 1. 构建节点ID到名称的映射（nodeId -> title 或 class_type）
            val nodeIdToNameMap = buildNodeNameMap(workflow)
            
            // 2. 更新状态为准备中
            _executionStatus.value = ExecutionStatus(
                state = ExecutionState.PREPARING,
                message = "准备执行工作流..."
            )
            
            // 2. 处理参数并创建执行工作流
            val executionWorkflow = prepareWorkflow(workflow, parameterValues)
            
            // 3. 获取客户端ID和服务器URL
            val clientId = repository.getClientId()
            val serverUrl = repository.getServerUrl()
            
            if (serverUrl.isEmpty()) {
                throw Exception("服务器地址未配置")
            }
            
            // 4. 更新状态为已提交
            _executionStatus.value = ExecutionStatus(
                state = ExecutionState.QUEUED,
                message = "提交到执行队列..."
            )
            
            // 5. 提交工作流执行
            val startTime = System.currentTimeMillis()
            // 增加超时控制，避免长时间无响应
            val result = repository.queuePrompt(serverUrl, executionWorkflow, clientId)
            
            if (result.isSuccess) {
                val promptResponse = result.getOrThrow()
                val promptId = promptResponse.prompt_id
                
                Log.d(TAG, "工作流提交成功，prompt_id: $promptId")
                
                // 6. 更新状态为执行中
                _executionStatus.value = ExecutionStatus(
                    state = ExecutionState.EXECUTING,
                    promptId = promptId,
                    message = "正在执行工作流..."
                )
                // 执行过程中来自 WebSocket executed 的图片收集（与最终 history 结果合并去重）
                val collectedImageUrls: MutableSet<String> = linkedSetOf()
                
                // 6.1 立即使用History检查是否已有输出（缓存复用场景）
                try {
                    val quickHistory = repository.getHistory(serverUrl, promptId)
                    if (quickHistory.isSuccess) {
                        val json = quickHistory.getOrThrow()
                        val entry = json[promptId]?.jsonObject
                        if (entry != null && outputExtractor.hasOutputs(entry)) {
                            val endNow = System.currentTimeMillis()
                            Log.d(TAG, "缓存命中快速路径：开始提取结果")
                            val extracted = outputExtractor.extractAllOutputs(serverUrl, entry)
                            Log.d(TAG, "缓存命中：图片=${extracted.imageUrls.size}, 视频=${extracted.videoUrls.size}, 音频=${extracted.audioUrls.size}, 文本节点=${extracted.textOutputs.size}")
                            
                            val executionResult = ExecutionResult(
                                success = true,
                                promptId = promptId,
                                outputImages = extracted.imageUrls,
                                outputVideos = extracted.videoUrls,
                                outputAudios = extracted.audioUrls,
                                outputTexts = extracted.textOutputs,
                                executionTime = endNow - startTime
                            )
                            _executionStatus.value = ExecutionStatus(
                                state = ExecutionState.COMPLETED,
                                promptId = promptId,
                                message = "命中缓存，已返回历史结果",
                                result = executionResult
                            )
                            // 持久化执行记录并下载图片和视频
                            try {
                                val (name, source) = repository.getLastImportedWorkflow()
                                recordManager.saveRecord(ExecutionRecordParams(
                                    promptId = promptId,
                                    workflow = workflow,
                                    timestamp = endNow,
                                    success = true,
                                    durationMs = executionResult.executionTime,
                                    imageUrls = executionResult.outputImages,
                                    videoUrls = executionResult.outputVideos,
                                    audioUrls = executionResult.outputAudios,
                                    textOutputs = executionResult.outputTexts,
                                    workflowName = name,
                                    importSource = source,
                                        quickParams = quickParams,
                            parameterValues = sanitizeParameterValues(parameterValues)
                                ))
                            } catch (e: Exception) {
                                Log.e(TAG, "缓存路径保存记录失败", e)
                            }
                            return Result.success(executionResult)
                        }
                    }
                } catch (_: Exception) {
                }
                
                // 7. 通过 WebSocket 监听实时进度，失败时启用 History API 轮询备用
                var cacheHit = false
                var wsCompleted = false
                var sawExecutingNode = false
                var wsFinished = false
                var wsConnectionFailed = false
                var wsCollected: Unit? = null
                
                // 标记是否已收到最终完成信号，用于收尾判定
                var wsCompletedBySignal = false
                // 进度采样与去重控制（降低高频大报文负载）
                val progressStateGateMs = 350L
                var lastProgressStateAt = 0L
                val lastProgressPerNode: MutableMap<String, Pair<Float, Float>> = mutableMapOf()
                try {
                    wsCollected = withTimeoutOrNull(10 * 60 * 1000L) { // 延长监听时间，降低长任务误判
                        try {
                            repository.connectWebSocket(serverUrl, clientId)
                                .collect { msg ->
                                    try {
                                    val type = msg.type.lowercase()
                                    val dataObj = msg.data?.jsonObject
                                    
                                    // 检测WebSocket连接错误（不中断收集，等待管理器自动重连）
                                    if (type == "error" && dataObj == null) {
                                        Log.w(TAG, "[$promptId] WebSocket连接中断，等待自动重连……")
                                        // 不抛取消，不立刻降级，保持收集以接收重连后的消息
                                        return@collect
                                    }
                                    
                                    // 过滤不同 promptId（若消息携带）
                                    // 注意：某些消息类型（如 status）不携带 promptId，需要放行
                                    val msgPromptId = dataObj?.get("prompt_id")?.jsonPrimitive?.contentOrNull
                                        ?: dataObj?.get("promptId")?.jsonPrimitive?.contentOrNull
                                    
                                    // 调试日志：显示消息类型和 promptId
                                    if (msgPromptId != null) {
                                        Log.d(TAG, "[$promptId] 收到消息: type=$type, msgPromptId=$msgPromptId, match=${msgPromptId == promptId}")
                                    } else {
                                        Log.d(TAG, "[$promptId] 收到消息: type=$type, msgPromptId=null, sawExecutingNode=$sawExecutingNode")
                                    }
                                    
                                    // 对于携带 promptId 的消息，只处理匹配的
                                    if (msgPromptId != null && msgPromptId != promptId) {
                                        // 不是本次执行，忽略
                                        Log.d(TAG, "[$promptId] 忽略其他任务的消息: type=$type, msgPromptId=$msgPromptId")
                                        return@collect
                                    }
                                    
                                    // 对于不携带 promptId 的完成消息（如 execution_success），需要通过其他方式判断
                                    // 如果当前任务还未看到任何 executing 节点，说明任务还未真正开始，忽略完成消息
                                    if ((type.startsWith("execution_") && type.contains("success", ignoreCase = true)) 
                                        || type.contains("done", ignoreCase = true) || type.contains("complete", ignoreCase = true)) {
                                        if (!sawExecutingNode && msgPromptId == null) {
                                            // 这可能是其他任务的完成消息，忽略
                                            Log.d(TAG, "[$promptId] 忽略非本任务的完成消息: type=$type")
                                            return@collect
                                        }
                                    }

                                     when (type) {
                                         "error" -> {
                                             val err = dataObj?.get("message")?.jsonPrimitive?.contentOrNull
                                                 ?: "执行出错"
                                             _executionStatus.value = _executionStatus.value.copy(
                                                 state = ExecutionState.ERROR,
                                                 message = err
                                             )
                                         }
                                        "execution_cached" -> {
                                            // 命中缓存，标记但继续监听（某些服务器实现仍会执行并发送进度）
                                            Log.d(TAG, "WebSocket: 收到execution_cached消息，标记缓存命中但继续监听")
                                            cacheHit = true
                                            sawExecutingNode = true  // 标记为已执行，避免被误判
                                            _executionStatus.value = _executionStatus.value.copy(
                                                state = ExecutionState.EXECUTING,
                                                message = "命中缓存，正在验证..."
                                            )
                                            // 不抛异常，继续监听真正的完成信号
                                        }
                                         "executing" -> {
                                             val nodeId = dataObj?.get("node")?.jsonPrimitive?.contentOrNull
                                             if (nodeId != null && nodeId != "null") {
                                                 // 节点开始执行
                                                 sawExecutingNode = true
                                                 // 使用映射获取节点名称（优先 title，否则 class_type）
                                                 val displayName = nodeIdToNameMap[nodeId] ?: nodeId
                                                 _executionStatus.value = _executionStatus.value.copy(
                                                     state = ExecutionState.EXECUTING,
                                                     currentNode = displayName,
                                                     message = "执行节点: $displayName"
                                                 )
                                             } else {
                                                 // 官方：executing.node == null 表示整个 prompt 执行完毕（需要 prompt_id 匹配）
                                                 // 文档：https://docs.comfy.org/zh-CN/development/comfyui-server/comms_messages
                                                 if (msgPromptId == promptId) {
                                                     wsCompleted = true
                                                     wsCompletedBySignal = true
                                                     _executionStatus.value = _executionStatus.value.copy(
                                                         state = ExecutionState.COMPLETED,
                                                         currentNode = null,
                                                         message = "执行完成",
                                                         progress = 1f
                                                     )
                                                     throw CancellationException("execution_completed_by_executing_null")
                                                 } else {
                                                     // 没有 prompt_id 的情况下，谨慎处理：只更新提示，不作为完成信号
                                                 _executionStatus.value = _executionStatus.value.copy(
                                                     currentNode = null,
                                                     message = "节点执行完成"
                                                 )
                                                 }
                                             }
                                         }
                                        "progress" -> {
                                            val value = dataObj?.get("value")?.jsonPrimitive?.floatOrNull
                                            val max = dataObj?.get("max")?.jsonPrimitive?.floatOrNull
                                            val nodeId = dataObj?.get("node")?.jsonPrimitive?.contentOrNull
                                            if (value != null && max != null && max > 0f) {
                                                val key = nodeId ?: "__global__"
                                                val last = lastProgressPerNode[key]
                                                // 去重：相同节点相同 value/max 不重复刷新
                                                if (last == null || last.first != value || last.second != max) {
                                                    lastProgressPerNode[key] = value to max
                                                    val p = (value / max).coerceIn(0f, 1f)
                                                    _executionStatus.value = _executionStatus.value.copy(
                                                        progress = p
                                                    )
                                                }
                                            }
                                        }
                                        "executed" -> {
                                            val nodeName = dataObj?.get("node")?.jsonPrimitive?.contentOrNull
                                            Log.d(TAG, "WebSocket: 收到executed消息，节点: $nodeName，尝试直接收集输出并通过History确认")
                                            // 直接从 executed.data.output.images 收集图片
                                            try {
                                                val outputObj = dataObj?.get("output")?.jsonObject
                                                val images = outputObj?.get("images")?.jsonArray
                                                val base = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
                                                if (images != null) {
                                                    images.forEach { imgEl ->
                                                        val obj = imgEl.jsonObject
                                                        val filename = obj["filename"]?.jsonPrimitive?.contentOrNull
                                                            ?: obj["name"]?.jsonPrimitive?.contentOrNull
                                                        if (filename != null) {
                                                            val subfolder = obj["subfolder"]?.jsonPrimitive?.contentOrNull ?: ""
                                                            val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "output"
                                                            val url = "${base}view?filename=" +
                                                                java.net.URLEncoder.encode(filename, "UTF-8") +
                                                                "&subfolder=" + java.net.URLEncoder.encode(subfolder, "UTF-8") +
                                                                "&type=" + java.net.URLEncoder.encode(type, "UTF-8")
                                                            collectedImageUrls.add(url)
                                                        }
                                                    }
                                                } else {
                                                    val imageObj = outputObj?.get("image")?.jsonObject
                                                    val filename = imageObj?.get("filename")?.jsonPrimitive?.contentOrNull
                                                        ?: imageObj?.get("name")?.jsonPrimitive?.contentOrNull
                                                    if (filename != null) {
                                                        val subfolder = imageObj?.get("subfolder")?.jsonPrimitive?.contentOrNull ?: ""
                                                        val type = imageObj?.get("type")?.jsonPrimitive?.contentOrNull ?: "output"
                                                        val url = "${base}view?filename=" +
                                                            java.net.URLEncoder.encode(filename, "UTF-8") +
                                                            "&subfolder=" + java.net.URLEncoder.encode(subfolder, "UTF-8") +
                                                            "&type=" + java.net.URLEncoder.encode(type, "UTF-8")
                                                        collectedImageUrls.add(url)
                                                    }
                                                }
                                            } catch (_: Exception) {}

                                            // 显示等待状态
                                            _executionStatus.value = _executionStatus.value.copy(
                                                message = "节点 $nodeName 执行完成，检查执行状态..."
                                            )
                                        }
                                        "execution_done", "execution_success" -> {
                                            // 整个工作流执行完毕
                                            Log.d(TAG, "WebSocket: 收到${type}消息，设置完成状态")
                                            wsCompleted = true
                                            wsCompletedBySignal = true
                                            _executionStatus.value = _executionStatus.value.copy(
                                                state = ExecutionState.COMPLETED,
                                                currentNode = null,
                                                message = "执行完成",
                                                progress = 1f
                                            )
                                            // 立即结束本次收集，转入最终结果获取
                                            throw CancellationException("execution_completed_by_signal")
                                        }
                                         "execution_start" -> {
                                             _executionStatus.value = _executionStatus.value.copy(
                                                 state = ExecutionState.EXECUTING,
                                                 message = "开始执行工作流..."
                                             )
                                         }
                                         "execution_interrupted" -> {
                                             // 官方中断消息：执行被 InterruptProcessingException 打断
                                             // 仍可能产生部分输出，后续用 history 拉取最终结果并决定最终状态
                                             wsCompleted = true
                                             wsCompletedBySignal = true
                                                 _executionStatus.value = _executionStatus.value.copy(
                                                 state = ExecutionState.ERROR,
                                                 currentNode = null,
                                                 message = "执行被中断",
                                                 errorType = ErrorType.WORKFLOW_ERROR,
                                                 errorDetails = "execution_interrupted"
                                             )
                                             throw CancellationException("execution_interrupted")
                                         }
                                         "status" -> {
                                             // 官方：status 消息里主要是 exec_info.queue_remaining（队列剩余任务数）
                                             // 文档：https://docs.comfy.org/zh-CN/development/comfyui-server/comms_messages
                                             val execInfoObj =
                                                 dataObj?.get("exec_info")?.jsonObject
                                                     ?: dataObj?.get("status")?.jsonObject?.get("exec_info")?.jsonObject

                                             val queueRemaining = execInfoObj
                                                 ?.get("queue_remaining")
                                                 ?.jsonPrimitive
                                                 ?.intOrNull

                                             if (queueRemaining != null) {
                                                 // 不要用 status 覆盖执行中的提示文案；只更新 queueRemaining，
                                                 // 仅在排队阶段用更贴近官方的文案提示。
                                                 val current = _executionStatus.value
                                                 _executionStatus.value = current.copy(
                                                     queueRemaining = queueRemaining,
                                                     message = if (current.state == ExecutionState.QUEUED)
                                                         "队列剩余: $queueRemaining"
                                                     else current.message
                                                 )
                                             }
                                         }
                                         // 其他完成消息类型（兼容不同后端）
                                         "execution_end", "complete", "done" -> {
                                             _executionStatus.value = _executionStatus.value.copy(
                                                 state = ExecutionState.COMPLETED,
                                                 currentNode = null,
                                                 message = "执行完成",
                                                 progress = 1f
                                             )
                                             throw CancellationException("execution_completed")
                                         }
                                       else -> {
                                            // 尝试兼容 progress_state 更新（采样，避免高频大包造成负载）
                                            if (type == "progress_state") {
                                                val nowTs = System.currentTimeMillis()
                                                if (nowTs - lastProgressStateAt >= progressStateGateMs) {
                                                    lastProgressStateAt = nowTs
                                                    val nodes = dataObj?.get("nodes")?.jsonObject
                                                    if (nodes != null) {
                                                        var runningNodes = 0
                                                        var currentRunningNode: String? = null
                                                        var finished = 0
                                                        nodes.forEach { (id, info) ->
                                                            val st = info.jsonObject["state"]?.jsonPrimitive?.contentOrNull
                                                            when (st) {
                                                                "running" -> { runningNodes++; currentRunningNode = id }
                                                                "finished" -> finished++
                                                            }
                                                        }
                                                        currentRunningNode?.let { nodeId ->
                                                            // 使用映射获取节点名称
                                                            val displayName = nodeIdToNameMap[nodeId] ?: nodeId
                                                            _executionStatus.value = _executionStatus.value.copy(
                                                                state = ExecutionState.EXECUTING,
                                                                currentNode = displayName,
                                                                message = "执行节点: $displayName"
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                Log.d(TAG, "WebSocket: 收到未处理的消息类型: $type, data: $dataObj")
                                            }
                                        }
                                     }
                                    } catch (ce: CancellationException) {
                                        throw ce
                                    } catch (_: Exception) {
                                    }
                                }
                        } finally {
                            // 不在这里主动断开，让全局连接保持，避免错过事件
                        }
                    }
                } catch (ce: CancellationException) {
                    // 正常中断，标记WS完成
                    wsFinished = true
                    when (ce.message) {
                        "websocket_connection_failed" -> {
                            wsConnectionFailed = true
                            Log.d(TAG, "WebSocket连接失败，将启用History API轮询")
                        }
                        "execution_completed_check_history" -> {
                            Log.d(TAG, "执行完成但需要通过History API确认结果")
                            // 继续执行，通过History API获取最终结果
                        }
                        "execution_completed_by_signal" -> {
                            Log.d(TAG, "收到完成信号，结束WS收集并获取最终结果")
                        }
                        else -> {
                            Log.d(TAG, "WebSocket正常完成: ${ce.message}")
                        }
                    }
                } catch (e: Exception) {
                    // WebSocket连接异常，启用备用轮询
                    Log.w(TAG, "WebSocket连接异常，启用History API轮询备用", e)
                    wsConnectionFailed = true
                    wsFinished = true
                }
                
                // WebSocket监听结果判定
                if (wsCollected == null && !wsCompletedBySignal) {
                    // 若已检测到连接失败，则不再误报“超时”
                    if (!wsConnectionFailed) {
                        Log.w(TAG, "WebSocket监听超时，可能连接不稳定")
                        wsConnectionFailed = true
                    }
                }
                
                // 若WebSocket连接失败或超时，启用History API轮询备用
                if (wsConnectionFailed) {
                    Log.d(TAG, "WebSocket不可用，启用History API轮询监控执行进度")
                    _executionStatus.value = _executionStatus.value.copy(
                        message = "WebSocket连接不稳定，使用轮询监控进度..."
                    )
                    
                    // 启动History API轮询来监控执行状态
                    val pollingResult = monitorExecutionWithPolling(serverUrl, promptId, workflow, startTime, quickParams)
                    return Result.success(pollingResult)
                }
                
                // 若WS已标记完成或已收到完成信号，通过History获取最终结果
                if (wsCompletedBySignal || wsFinished || _executionStatus.value.state == ExecutionState.COMPLETED) {
                    val endNow = System.currentTimeMillis()
                    // 调用统一的History结果获取方法
                    val finalResult = getFinalExecutionResult(serverUrl, promptId, endNow - startTime)
                    // 合并WS阶段收集的图片（若有）
                    val mergedImages = try {
                        val existing = finalResult.outputImages
                        if (existing.isNotEmpty()) existing else existing
                    } catch (_: Exception) { finalResult.outputImages }
                    val mergedResult = finalResult.copy(outputImages = mergedImages)
                    _executionStatus.value = _executionStatus.value.copy(
                        state = ExecutionState.COMPLETED,
                        result = mergedResult
                    )
                    
                    // 持久化执行记录并下载图片和视频
                    try {
                        val (name, source) = repository.getLastImportedWorkflow()
                        recordManager.saveRecord(ExecutionRecordParams(
                            promptId = promptId,
                            workflow = workflow,
                            timestamp = endNow,
                            success = mergedResult.success,
                            durationMs = mergedResult.executionTime,
                            imageUrls = mergedResult.outputImages,
                            videoUrls = mergedResult.outputVideos,
                            audioUrls = mergedResult.outputAudios,
                            textOutputs = mergedResult.outputTexts,
                            workflowName = name,
                            importSource = source,
                            quickParams = quickParams,
                            parameterValues = sanitizeParameterValues(parameterValues)
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG, "保存执行记录失败", e)
                    }
                    
                    return Result.success(mergedResult)
                }

                // 8. 快速History检查：如果没出现executing节点事件，但WS宣告完成或缓存命中，优先用History outputs确认
                val quickHistory = if (!sawExecutingNode && (wsCompleted || cacheHit)) {
                    confirmCompletionByHistory(serverUrl, promptId, quick = true)
                } else null

                // 9. 基于History API轮询确认结束（命中缓存或快速确认成功时跳过）
                val historyResult = when {
                    cacheHit -> HistoryCheckResult(completed = true, hadError = false, timedOut = false)
                    quickHistory != null -> quickHistory
                    else -> confirmCompletionByHistory(serverUrl, promptId)
                }
                val endTime = System.currentTimeMillis()
                // 统一通过History获取最终结果（模仿web版本逻辑）
                // 🔥 关键修复：即使有错误，也要尝试获取结果，因为可能有图片输出
                val finalResult = if (historyResult.completed && !historyResult.hadError) {
                    getFinalExecutionResult(serverUrl, promptId, endTime - startTime)
                } else if (historyResult.hadError) {
                    // 即使报告错误，也尝试获取结果（可能有部分输出）
                    val result = getFinalExecutionResult(serverUrl, promptId, endTime - startTime)
                    // 🎥 如果有图片或视频输出，认为执行成功
                    if (result.outputImages.isNotEmpty() || result.outputVideos.isNotEmpty()) {
                        Log.d(TAG, "虽然有节点报错，但检测到输出（图片=${result.outputImages.size}, 视频=${result.outputVideos.size}），标记为成功")
                        result.copy(success = true)
                    } else {
                        result.copy(success = false)
                    }
                } else {
                    ExecutionResult(
                        success = false,
                        promptId = promptId,
                        outputImages = emptyList(),
                        outputVideos = emptyList(), // 🎥
                        outputAudios = emptyList(), // 🎵
                        outputTexts = emptyMap(), // 📝
                        executionTime = endTime - startTime
                    )
                }
                _executionStatus.value = _executionStatus.value.copy(
                    state = when {
                        // 🔥 关键修复：如果有图片或视频输出，即使有错误也显示为已完成
                        finalResult.outputImages.isNotEmpty() || finalResult.outputVideos.isNotEmpty() -> ExecutionState.COMPLETED
                        historyResult.hadError -> ExecutionState.ERROR
                        historyResult.timedOut -> ExecutionState.ERROR
                        historyResult.completed -> ExecutionState.COMPLETED
                        else -> ExecutionState.ERROR
                    },
                    progress = when {
                        // 🔥 关键修复：完成或失败时，进度设为1f，避免通知显示"执行中 100%"
                        finalResult.outputImages.isNotEmpty() || finalResult.outputVideos.isNotEmpty() -> 1f
                        historyResult.completed -> 1f
                        else -> _executionStatus.value.progress
                    },
                    currentNode = null, // 清除当前节点信息
                    message = when {
                        // 🔥 关键修复：如果有图片或视频输出，即使有错误也显示为执行完成
                        finalResult.outputImages.isNotEmpty() || finalResult.outputVideos.isNotEmpty() -> "执行完成"
                        historyResult.hadError -> "执行失败"
                        historyResult.timedOut -> "执行确认超时"
                        historyResult.completed -> "执行完成"
                        else -> "执行失败"
                    },
                    errorType = when {
                        // 🔥 关键修复：如果有图片或视频输出，清除错误类型
                        finalResult.outputImages.isNotEmpty() || finalResult.outputVideos.isNotEmpty() -> null
                        historyResult.hadError -> ErrorType.WORKFLOW_ERROR
                        historyResult.timedOut -> ErrorType.TIMEOUT_ERROR
                        else -> null
                    },
                    canRetry = historyResult.timedOut && finalResult.outputImages.isEmpty() && finalResult.outputVideos.isEmpty(),
                    result = finalResult
                )
                // 持久化执行记录并下载图片和视频
                try {
                    val (name, source) = repository.getLastImportedWorkflow()
                    val hasOutput = finalResult.outputImages.isNotEmpty() || finalResult.outputVideos.isNotEmpty()
                    recordManager.saveRecord(ExecutionRecordParams(
                        promptId = promptId,
                        workflow = workflow,
                        timestamp = endTime,
                        success = finalResult.success,
                        durationMs = finalResult.executionTime,
                        imageUrls = finalResult.outputImages,
                        videoUrls = finalResult.outputVideos,
                        audioUrls = finalResult.outputAudios,
                        textOutputs = finalResult.outputTexts,
                        workflowName = name,
                        importSource = source,
                        errorType = if (hasOutput) null else _executionStatus.value.errorType?.name,
                        errorMessage = if (hasOutput) null 
                                      else if (historyResult.hadError) _executionStatus.value.errorDetails ?: _executionStatus.value.message 
                                      else null,
                        quickParams = quickParams,
                        parameterValues = sanitizeParameterValues(parameterValues)
                    ))
                } catch (e: Exception) {
                    Log.e(TAG, "保存执行记录失败", e)
                }
                Result.success(finalResult)
                
            } else {
                val error = result.exceptionOrNull()?.message ?: "提交失败"
                Log.e(TAG, "工作流提交失败: $error")
                
                val (errorType, canRetry) = classifyError(result.exceptionOrNull())
                _executionStatus.value = ExecutionStatus(
                    state = ExecutionState.ERROR,
                    message = "提交失败",
                    errorType = errorType,
                    errorDetails = error,
                    canRetry = canRetry
                )
                
                Result.failure(Exception(error))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "工作流执行异常", e)
            
            val (errorType, canRetry) = classifyError(e)
            _executionStatus.value = ExecutionStatus(
                state = ExecutionState.ERROR,
                message = "执行异常",
                errorType = errorType,
                errorDetails = e.message ?: "未知错误",
                canRetry = canRetry
            )
            
            Result.failure(e)
        }
    }
    
    /**
     * 准备执行工作流（应用用户参数）
     */
    /**
     * 过滤掉媒体类参数后再持久化。
     * 图片/视频/音频参数在提交前会被替换成服务端临时文件名，
     * 该名字在下次执行时通常已失效，回填只会导致必然失败的任务。
     */
    private fun sanitizeParameterValues(
        values: Map<String, JsonElement>
    ): Map<String, JsonElement> {
        val mediaKeywords = listOf(
            "image", "video", "audio", "mask", "upload", "file", "filename", "clip_name"
        )
        return values.filterKeys { paramId ->
            val name = paramId.substringAfter('_', paramId).lowercase()
            mediaKeywords.none { name.contains(it) }
        }
    }

    private fun prepareWorkflow(
        originalWorkflow: JsonObject,
        parameterValues: Map<String, JsonElement>
    ): JsonObject {
        Log.d(TAG, "准备工作流，应用 ${parameterValues.size} 个参数修改")
        
        // 创建工作流副本
        val workflowMap = originalWorkflow.toMutableMap()
        
        // 应用参数修改
        for ((paramId, newValue) in parameterValues) {
            try {
                // 参数ID格式: nodeId_paramName
                val parts = paramId.split("_", limit = 2)
                if (parts.size == 2) {
                    val nodeId = parts[0]
                    val paramName = parts[1]
                    
                    // 获取节点
                    val nodeElement = workflowMap[nodeId]
                    if (nodeElement is JsonObject) {
                        val nodeMap = nodeElement.toMutableMap()
                        val inputsElement = nodeMap["inputs"]
                        
                        if (inputsElement is JsonObject) {
                            val inputsMap = inputsElement.toMutableMap()
                            
                            // 处理种子参数的特殊逻辑
                            val finalValue = if (paramName.lowercase().contains("seed")) {
                                processSeedParameter(newValue)
                            } else {
                                newValue
                            }
                            
                            inputsMap[paramName] = finalValue
                            nodeMap["inputs"] = JsonObject(inputsMap)
                            workflowMap[nodeId] = JsonObject(nodeMap)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "应用参数失败: $paramId", e)
            }
        }
        
        return JsonObject(workflowMap)
    }
    
    /**
     * 处理种子参数的特殊逻辑
     */
    private fun processSeedParameter(value: JsonElement): JsonElement {
        // 🔥 确保种子值作为整数类型，避免精度丢失（不做范围限制）
        return when {
            value is JsonPrimitive && value.longOrNull != null -> {
                // 直接使用Long值，不限制范围
                JsonPrimitive(value.longOrNull!!)
            }
            value is JsonPrimitive && value.intOrNull != null -> {
                // 如果是Int类型，保持为Int
                JsonPrimitive(value.int)
            }
            value is JsonPrimitive && value.doubleOrNull != null -> {
                // 如果是Double类型，转换为Long
                val seedValue = value.double.toLong()
                Log.w(TAG, "种子值是Double类型，转换为Long: ${value.double} -> $seedValue")
                JsonPrimitive(seedValue)
            }
            // 其他情况直接使用原值
            else -> value
        }
    }

    /**
     * 获取最终执行结果（模仿web版本逻辑）
     * 通过History API获取完整结果并提取图片/视频URL
     */
    private suspend fun getFinalExecutionResult(serverUrl: String, promptId: String, executionTime: Long): ExecutionResult {
        return try {
            Log.d(TAG, "开始获取最终执行结果，promptId: $promptId")
            // 部分后端在 execution_success 之后写入 history 需要短暂时间，这里做短轮询等待 outputs 就绪
            val timeoutMs = 15_000L
            val intervalMs = 350L
            val start = System.currentTimeMillis()
            var lastJson: kotlinx.serialization.json.JsonObject? = null
            while (System.currentTimeMillis() - start < timeoutMs) {
                val historyResult = repository.getHistory(serverUrl, promptId)
                if (historyResult.isSuccess) {
                    val json = historyResult.getOrThrow()
                    lastJson = json
                    val entry = json[promptId]?.jsonObject
                    val outputs = entry?.get("outputs")?.jsonObject
                    if (outputs != null && outputs.isNotEmpty()) {
                        Log.d(TAG, "getFinalExecutionResult: outputs 不为空，开始提取结果")
                        val extracted = outputExtractor.extractAllOutputs(serverUrl, entry)
                        Log.d(TAG, "提取完成：图片=${extracted.imageUrls.size}, 视频=${extracted.videoUrls.size}, 音频=${extracted.audioUrls.size}, 文本=${extracted.textOutputs.size}")
                        
                        if (extracted.hasAnyOutput()) {
                            Log.d(TAG, "History 已就绪")
                            val result = ExecutionResult(
                                success = true,
                                promptId = promptId,
                                outputImages = extracted.imageUrls,
                                outputVideos = extracted.videoUrls,
                                outputAudios = extracted.audioUrls,
                                outputTexts = extracted.textOutputs,
                                executionTime = executionTime
                            )
                            return result
                        } else {
                            Log.w(TAG, "outputs 存在但没有提取到任何输出")
                        }
                    } else {
                        Log.w(TAG, "outputs 为空或 null")
                    }
                }
                kotlinx.coroutines.delay(intervalMs)
            }
            // 超时仍未拿到输出，降级为成功但无输出
            Log.w(TAG, "History在等待窗口内未产出输出，返回空结果")
            ExecutionResult(
                success = true,
                promptId = promptId,
                outputImages = emptyList(),
                outputVideos = emptyList(), // 🎥
                outputAudios = emptyList(), // 🎵
                outputTexts = emptyMap(), // 📝
                executionTime = executionTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取最终执行结果失败", e)
            ExecutionResult(
                success = false,
                promptId = promptId,
                outputImages = emptyList(),
                outputVideos = emptyList(), // 🎥
                outputAudios = emptyList(), // 🎵
                outputTexts = emptyMap(), // 📝
                errorMessage = e.message,
                executionTime = executionTime
            )
        }
    }

    /**
     * 通过History API确认执行是否结束
     * 规则：所有节点状态为 done 则成功；若有 error 则失败；存在 running/pending 继续等待
     */
    private data class HistoryCheckResult(val completed: Boolean, val hadError: Boolean, val timedOut: Boolean)

    private suspend fun confirmCompletionByHistory(serverUrl: String, promptId: String, quick: Boolean = false): HistoryCheckResult {
        val start = System.currentTimeMillis()
        val timeoutMs = if (quick) 3_000L else 60_000L // 快速确认3秒，常规60秒
        val intervalMs = if (quick) 300L else 800L
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                val r = repository.getHistory(serverUrl, promptId)
                if (r.isSuccess) {
                    val json = r.getOrThrow()
                    // 返回结构通常为 { "prompt_id": { "status": {...}, "nodes": { nodeId: { "status": "done|running|pending|error" } } } }
                    val entry = json[promptId]?.jsonObject
                    val nodes = entry?.get("nodes")?.jsonObject
                    // 如果有 outputs，直接认为完成（缓存命中场景）
                    val outputs = entry?.get("outputs")
                    if (outputs != null && outputs.jsonObject.isNotEmpty()) {
                        return HistoryCheckResult(completed = true, hadError = false, timedOut = false)
                    }
                    if (nodes != null) {
                        var anyRunning = false
                        var anyError = false
                        var allDone = true
                        for ((_, nodeVal) in nodes) {
                            val status = nodeVal.jsonObject["status"]?.jsonPrimitive?.contentOrNull ?: ""
                            when (status.lowercase()) {
                                "done" -> {}
                                "error" -> { anyError = true; allDone = false }
                                "running", "pending" -> { anyRunning = true; allDone = false }
                                else -> { allDone = false }
                            }
                        }
                        if (anyError) return HistoryCheckResult(completed = false, hadError = true, timedOut = false)
                        if (allDone && !anyRunning) return HistoryCheckResult(completed = true, hadError = false, timedOut = false)
                    }
                    // 某些版本没有nodes细节，fallback根据 overall status
                    val overallStatus = entry?.get("status")?.jsonObject
                    val completed = overallStatus?.get("completed")?.jsonPrimitive?.booleanOrNull
                    if (completed == true) return HistoryCheckResult(completed = true, hadError = false, timedOut = false)
                }
            } catch (_: Exception) {
            }
            kotlinx.coroutines.delay(intervalMs)
        }
        // 超时
        return HistoryCheckResult(completed = false, hadError = false, timedOut = true)
    }
    
    /**
     * 重置执行状态
     */
    fun resetExecution() {
        _executionStatus.value = ExecutionStatus()
    }
    
    /**
     * 错误分类函数
     * 返回错误类型和是否可重试
     */
    private fun classifyError(exception: Throwable?): Pair<ErrorType, Boolean> {
        return when (exception) {
            is java.net.SocketTimeoutException,
            is java.net.ConnectException,
            is java.net.UnknownHostException -> {
                ErrorType.NETWORK_ERROR to true
            }
            is kotlinx.coroutines.TimeoutCancellationException -> {
                ErrorType.TIMEOUT_ERROR to true
            }
            is retrofit2.HttpException -> {
                when (exception.code()) {
                    in 400..499 -> ErrorType.VALIDATION_ERROR to false // 客户端错误通常不可重试
                    in 500..599 -> ErrorType.SERVER_ERROR to true      // 服务器错误可重试
                    else -> ErrorType.UNKNOWN_ERROR to false
                }
            }
            else -> {
                val message = exception?.message?.lowercase() ?: ""
                when {
                    message.contains("timeout") -> ErrorType.TIMEOUT_ERROR to true
                    message.contains("network") || message.contains("connection") -> ErrorType.NETWORK_ERROR to true
                    message.contains("server") -> ErrorType.SERVER_ERROR to true
                    message.contains("workflow") || message.contains("node") -> ErrorType.WORKFLOW_ERROR to false
                    else -> ErrorType.UNKNOWN_ERROR to false
                }
            }
        }
    }
    
    /**
     * 取消执行
     */
    suspend fun cancelExecution(): Result<Unit> {
        return try {
            val serverUrl = repository.getServerUrl()
            if (serverUrl.isEmpty()) return Result.failure(Exception("服务器地址未配置"))
            val r = repository.interrupt(serverUrl)
            if (r.isSuccess) {
                _executionStatus.value = ExecutionStatus(
                    state = ExecutionState.IDLE,
                    message = "执行已取消"
                )
                Result.success(Unit)
            } else {
                Result.failure(r.exceptionOrNull() ?: Exception("中断失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 使用History API轮询监控执行状态（WebSocket不可用时的备用方案）
     */
    private suspend fun monitorExecutionWithPolling(
        serverUrl: String,
        promptId: String,
        workflow: JsonObject,
        startTime: Long,
        quickParams: Map<String, List<String>>? = null
    ): ExecutionResult {
        Log.d(TAG, "开始History API轮询监控，promptId: $promptId")
        
        var pollingCount = 0
        val maxPollingCount = 120 // 最多轮询2分钟（每秒一次）
        val pollingInterval = 1000L // 1秒轮询间隔
        
        while (pollingCount < maxPollingCount) {
            try {
                kotlinx.coroutines.delay(pollingInterval)
                pollingCount++
                
                // 更新状态显示轮询进度
                _executionStatus.value = _executionStatus.value.copy(
                    message = "轮询监控执行状态... (${pollingCount}/${maxPollingCount})"
                )
                
                val historyResult = repository.getHistory(serverUrl, promptId)
                if (historyResult.isSuccess) {
                    val json = historyResult.getOrThrow()
                    val entry = json[promptId]?.jsonObject
                    
                    if (entry != null) {
                        val status = entry["status"]?.jsonObject
                        val statusStr = status?.get("status_str")?.jsonPrimitive?.contentOrNull
                        val completed = status?.get("completed")?.jsonPrimitive?.booleanOrNull ?: false
                        
                        Log.d(TAG, "轮询检查：status_str=$statusStr, completed=$completed")
                        
                        when {
                            statusStr == "success" && completed -> {
                                Log.d(TAG, "轮询检测到执行成功完成")
                                val endTime = System.currentTimeMillis()
                                
                                val extracted = outputExtractor.extractAllOutputs(serverUrl, entry)
                                Log.d(TAG, "轮询提取完成：图片=${extracted.imageUrls.size}, 视频=${extracted.videoUrls.size}, 音频=${extracted.audioUrls.size}, 文本=${extracted.textOutputs.size}")
                                
                                val result = ExecutionResult(
                                    success = true,
                                    promptId = promptId,
                                    outputImages = extracted.imageUrls,
                                    outputVideos = extracted.videoUrls,
                                    outputAudios = extracted.audioUrls,
                                    outputTexts = extracted.textOutputs,
                                    executionTime = endTime - startTime
                                )
                                
                                _executionStatus.value = ExecutionStatus(
                                    state = ExecutionState.COMPLETED,
                                    promptId = promptId,
                                    message = "执行完成（轮询检测）",
                                    progress = 1f,
                                    result = result
                                )
                                
                                // 保存执行记录并下载图片和视频
                                try {
                                    val (name, source) = repository.getLastImportedWorkflow()
                                    recordManager.saveRecord(ExecutionRecordParams(
                                        promptId = promptId,
                                        workflow = workflow,
                                        timestamp = endTime,
                                        success = true,
                                        durationMs = result.executionTime,
                                        imageUrls = result.outputImages,
                                        videoUrls = result.outputVideos,
                                        audioUrls = result.outputAudios,
                                        textOutputs = result.outputTexts,
                                        workflowName = name,
                                        importSource = source,
                                            quickParams = quickParams,
                            parameterValues = sanitizeParameterValues(parameterValues)
                                    ))
                                } catch (e: Exception) {
                                    Log.e(TAG, "保存执行记录失败", e)
                                }
                                
                                return result
                            }
                            statusStr == "error" -> {
                                Log.w(TAG, "轮询检测到执行失败")
                                val endTime = System.currentTimeMillis()
                                val result = ExecutionResult(
                                    success = false,
                                    promptId = promptId,
                                    outputImages = emptyList(),
                                    outputVideos = emptyList(), // 🎥
                                    outputAudios = emptyList(), // 🎵
                                    outputTexts = emptyMap(), // 📝
                                    errorMessage = "执行失败",
                                    executionTime = endTime - startTime
                                )
                                
                                _executionStatus.value = ExecutionStatus(
                                    state = ExecutionState.ERROR,
                                    promptId = promptId,
                                    message = "执行失败（轮询检测）",
                                    result = result,
                                    errorType = ErrorType.WORKFLOW_ERROR,
                                    errorDetails = "执行失败"
                                )
                                
                                return result
                            }
                            else -> {
                                // 继续轮询，更新进度显示
                                val progressPercent = (pollingCount.toFloat() / maxPollingCount * 0.9f).coerceIn(0f, 0.9f)
                                _executionStatus.value = _executionStatus.value.copy(
                                    progress = progressPercent,
                                    message = "ComfyUI正在处理请求... (已等待${pollingCount}秒)"
                                )
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "轮询History API失败: ${historyResult.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "轮询过程中发生异常", e)
                // 继续轮询，不中断
            }
        }
        
        // 轮询超时
        Log.w(TAG, "轮询监控超时")
        val endTime = System.currentTimeMillis()
        val result = ExecutionResult(
            success = false,
            promptId = promptId,
            outputImages = emptyList(),
            errorMessage = "执行监控超时",
            executionTime = endTime - startTime
        )
        
        _executionStatus.value = ExecutionStatus(
            state = ExecutionState.ERROR,
            promptId = promptId,
            message = "执行监控超时",
            result = result,
            errorType = ErrorType.TIMEOUT_ERROR,
            errorDetails = "轮询监控超时"
        )
        
        return result
    }
    
    /**
     * 构建节点ID到名称的映射
     * 优先使用 title（如果存在），否则使用 class_type
     */
    private fun buildNodeNameMap(workflow: JsonObject): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            workflow.forEach { (nodeId, nodeValue) ->
                val nodeObj = nodeValue.jsonObject
                val title = nodeObj["_meta"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
                val classType = nodeObj["class_type"]?.jsonPrimitive?.contentOrNull
                
                // 优先使用 title，否则使用 class_type
                val displayName = title?.takeIf { it.isNotBlank() } ?: classType
                
                if (displayName != null) {
                    map[nodeId] = displayName
                    Log.d(TAG, "节点映射: $nodeId -> $displayName")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "构建节点名称映射失败", e)
        }
        return map
    }
}
