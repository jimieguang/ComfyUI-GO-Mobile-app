package com.example.myapplication.domain.parser

import android.util.Log
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ComfyUI工作流解析器
 * 基于Web版逻辑实现，解析工作流JSON并提取可编辑参数
 */
@Singleton
class WorkflowParser @Inject constructor() {
    
    companion object {
        private const val TAG = "WorkflowParser"
        
        // 参数默认范围常量
        private const val SEED_MIN = 0.0
        private const val SEED_MAX = 9999999999.0
        private const val SEED_STEP = 1.0
        
        private const val STEP_MIN = 1.0
        private const val STEP_MAX = 150.0
        private const val STEP_STEP = 1.0
        
        private const val DIMENSION_MIN = 64.0
        private const val DIMENSION_MAX = 2048.0
        private const val DIMENSION_STEP = 64.0
        
        private const val CFG_MIN = 1.0
        private const val CFG_MAX = 30.0
        private const val CFG_STEP = 0.1
        
        private const val DEFAULT_MIN = 0.0
        private const val DEFAULT_MAX = 100.0
        private const val DEFAULT_STEP_INT = 1.0
        private const val DEFAULT_STEP_FLOAT = 0.1
        
        // 输出节点类型定义 - 与Web版保持一致
        private val OUTPUT_KEYWORDS = listOf("Save", "Preview", "Output", "Write", "Show", "Display")
        private val OUTPUT_EXACT_TYPES = setOf(
            // 标准输出节点
            "PreviewImage", "SaveImage", "WriteImage", "ImageToOutput",
            // 动画输出节点
            "SaveAnimatedWEBP", "SaveAnimatedPNG", "SaveAnimatedGIF",
            // 视频相关节点
            "VHS_VideoCombine", "VHS_SaveVideo", "VHS_PreviewVideo",
            "VHS_WritePNG", "VHS_WriteJPG", "VHS_WriteWEBP", "VHS_WriteMP4", "VHS_WriteGIF",
            // 各扩展的输出节点
            "ImpactSaveImage", "ImpactSaveAnimatedWEBP", "ImpactSaveAnimatedPNG",
            "PreviewBridge", "SaveImageExtended", "ADE_SaveImage",
            "SaveImageWithMetadata", "ShowImage", "SaveImageWCustomName", "SaveAnimatedWCustomName"
        )
        
        // 视频类型相关关键字（用于智能识别视频上传参数）
        private val VIDEO_TYPE_KEYWORDS = setOf(
            "VIDEO", "VIDEO_PATH", "VIDEOINFO", "VHS_VIDEO", "video_upload"
        )
        
        // 视频参数名称模式（辅助信号）
        private val VIDEO_PARAM_NAMES = setOf(
            "video", "input_video", "init_video", "videos", "video_file"
            // 注意：不包含 "file" 因为太通用，会与图像上传冲突
        )
        
        // 音频类型相关关键字（用于智能识别音频上传参数）
        private val AUDIO_TYPE_KEYWORDS = setOf(
            "AUDIO", "AUDIO_PATH", "AUDIOINFO", "audio_upload"
        )
        
        // 音频参数名称模式（辅助信号）
        private val AUDIO_PARAM_NAMES = setOf(
            "audio", "input_audio", "sound", "music", "audio_file"
        )
        
        /**
         * 智能判断参数是否为视频上传类型
         * @param classType 节点类型
         * @param paramName 参数名称
         * @param paramDef 参数定义（来自 object_info）
         * @param isApiNode 是否为 API 节点
         */
        fun isVideoUploadParameter(
            classType: String,
            paramName: String,
            paramDef: JsonElement,
            isApiNode: Boolean = false
        ): Boolean {
            // 强信号1: API 节点中出现 video_upload
            if (isApiNode && paramDef.toString().contains("video_upload", ignoreCase = true)) {
                return true
            }
            
            // 强信号2: 已知的视频加载节点
            if (classType == "LoadVideo" && (paramName == "video" || paramName == "file")) {
                return true
            }
            
            // 强信号3: 参数定义中的类型包含视频相关关键字
            if (paramDef is JsonArray && paramDef.size > 0) {
                val firstElement = paramDef[0]
                
                // 检查类型字符串
                if (firstElement is JsonPrimitive && firstElement.isString) {
                    val typeStr = firstElement.content.uppercase()
                    if (VIDEO_TYPE_KEYWORDS.any { typeStr.contains(it) }) {
                        return true
                    }
                }
                
                // 检查元数据中的 UI 标记
                if (paramDef.size > 1) {
                    val metadata = paramDef[1]
                    if (metadata is JsonObject) {
                        // 检查 video_upload 标记
                        if (metadata["video_upload"]?.jsonPrimitive?.boolean == true) {
                            return true
                        }
                        
                        // 检查 ui 数组
                        val ui = metadata["ui"]
                        if (ui is JsonArray && ui.toString().contains("video_upload", ignoreCase = true)) {
                            return true
                        }
                    }
                }
                
                // 检查选项数组中的文件扩展名
                if (firstElement is JsonArray) {
                    val options = firstElement.map { it.jsonPrimitive.content.lowercase() }
                    val videoExtensions = setOf("mp4", "webm", "mov", "avi", "mkv", "gif")
                    if (options.any { opt -> videoExtensions.any { ext -> opt.endsWith(".$ext") } }) {
                        return true
                    }
                }
            }
            
            // 辅助信号: 参数名匹配（需配合其他信号）
            if (VIDEO_PARAM_NAMES.contains(paramName.lowercase())) {
                // 如果参数名像视频且不是已知的图片/音频节点，则判定为视频
                // 排除所有图像相关节点
                val imageNodeKeywords = listOf("LoadImage", "SaveImage", "Image", "Img")
                val isImageNode = imageNodeKeywords.any { classType.contains(it, ignoreCase = true) }
                
                // 🎵 排除所有音频相关节点
                val audioNodeKeywords = listOf("LoadAudio", "SaveAudio", "Audio", "AudioGen", "StableAudio")
                val isAudioNode = audioNodeKeywords.any { classType.contains(it, ignoreCase = true) }
                
                // 🎵 排除音频参数名
                val audioParamNames = listOf("audio", "sound", "music")
                val isAudioParam = audioParamNames.contains(paramName.lowercase())
                
                return !isImageNode && !isAudioNode && !isAudioParam
            }
            
            return false
        }
        
        /**
         * 智能判断参数是否为音频上传类型
         * @param classType 节点类型
         * @param paramName 参数名称
         * @param paramDef 参数定义（来自 object_info）
         * @param isApiNode 是否为 API 节点
         */
        fun isAudioUploadParameter(
            classType: String,
            paramName: String,
            paramDef: JsonElement,
            isApiNode: Boolean = false
        ): Boolean {
            // 强信号1: API 节点中出现 audio_upload
            if (isApiNode && paramDef.toString().contains("audio_upload", ignoreCase = true)) {
                return true
            }
            
            // 强信号2: 已知的音频加载节点
            if ((classType == "LoadAudio" || classType.contains("Audio", ignoreCase = true)) 
                && (paramName == "audio" || paramName == "file" || paramName == "sound" || paramName == "music")) {
                return true
            }
            
            // 强信号3: 参数定义中的类型包含音频相关关键字
            if (paramDef is JsonArray && paramDef.size > 0) {
                val firstElement = paramDef[0]
                
                // 检查类型字符串
                if (firstElement is JsonPrimitive && firstElement.isString) {
                    val typeStr = firstElement.content.uppercase()
                    if (AUDIO_TYPE_KEYWORDS.any { typeStr.contains(it) }) {
                        return true
                    }
                }
                
                // 检查元数据中的 UI 标记
                if (paramDef.size > 1) {
                    val metadata = paramDef[1]
                    if (metadata is JsonObject) {
                        // 检查 audio_upload 标记
                        if (metadata["audio_upload"]?.jsonPrimitive?.boolean == true) {
                            return true
                        }
                        
                        // 检查 ui 数组
                        val ui = metadata["ui"]
                        if (ui is JsonArray && ui.toString().contains("audio_upload", ignoreCase = true)) {
                            return true
                        }
                    }
                }
                
                // 检查选项数组中的文件扩展名
                if (firstElement is JsonArray) {
                    val options = firstElement.map { it.jsonPrimitive.content.lowercase() }
                    val audioExtensions = setOf("mp3", "wav", "ogg", "flac", "m4a", "aac")
                    if (options.any { opt -> audioExtensions.any { ext -> opt.endsWith(".$ext") } }) {
                        return true
                    }
                }
            }
            
            // 辅助信号: 参数名匹配（需配合其他信号）
            if (AUDIO_PARAM_NAMES.contains(paramName.lowercase())) {
                // 如果参数名像音频且不是已知的图片/视频节点，则判定为音频
                val imageVideoKeywords = listOf("LoadImage", "SaveImage", "Image", "Img", "LoadVideo", "SaveVideo", "Video")
                val isImageVideoNode = imageVideoKeywords.any { classType.contains(it, ignoreCase = true) }
                return !isImageVideoNode
            }
            
            return false
        }
    }
    
    private var workflow: JsonObject? = null
    private var objectInfo: Map<String, com.example.myapplication.data.model.NodeInfo>? = null
    
    /**
     * 解析结果数据类
     */
    data class ParseResult(
        val success: Boolean,
        val workflow: JsonObject? = null,
        val allNodes: List<NodeInfo> = emptyList(),
        val inputNodes: List<NodeInfo> = emptyList(),
        val outputNodes: List<NodeInfo> = emptyList(),
        val editableParams: List<EditableParam> = emptyList(),
        val errorMessage: String = ""
    )
    
    /**
     * 节点信息数据类
     */
    data class NodeInfo(
        val id: String,
        val type: String,
        val isInputNode: Boolean,
        val isOutputNode: Boolean,
        val title: String,
        val customTitle: String? = null
    )
    
    /**
     * 可编辑参数数据类
     */
    data class EditableParam(
        val id: String,
        val nodeId: String,
        val classType: String,
        val name: String,
        val displayName: String,
        val value: JsonElement,
        val type: ParamType,
        val uiConfig: UiConfig
    )
    
    /**
     * 参数类型枚举
     */
    enum class ParamType {
        STRING, NUMBER, BOOLEAN, ARRAY
    }
    
    /**
     * UI配置数据类
     */
    data class UiConfig(
        val type: UiType,
        val min: Double? = null,
        val max: Double? = null,
        val step: Double? = null,
        val options: List<String> = emptyList(),
        val isSeed: Boolean = false,
        val isMultiline: Boolean = false,
        val isDynamic: Boolean = false, // 标记是否使用动态选项
        val supportsMask: Boolean = false // 标记是否支持遮罩编辑
    )
    
    /**
     * UI类型枚举
     */
    enum class UiType {
        TEXT, NUMBER, CHECKBOX, SELECT, UPLOAD_IMAGE, UPLOAD_VIDEO, UPLOAD_AUDIO, MULTILINE_TEXT
    }
    
    /**
     * 解析工作流
     */
    suspend fun parse(
        workflowJson: JsonObject,
        objectInfo: Map<String, com.example.myapplication.data.model.NodeInfo>? = null
    ): ParseResult {
        try {
            Log.d(TAG, "开始解析工作流，节点数量: ${workflowJson.size}")
            
            this.workflow = workflowJson
            this.objectInfo = objectInfo
            
            // 识别所有节点
            val allNodes = identifyAllNodes()
            
            // 分类节点
            val inputNodes = allNodes.filter { it.isInputNode }
            val outputNodes = allNodes.filter { it.isOutputNode }
            
            Log.d(TAG, "节点分析完成: 总计 ${allNodes.size}, 输入 ${inputNodes.size}, 输出 ${outputNodes.size}")
            
            // 识别可编辑参数
            val editableParams = if (objectInfo != null) {
                identifyEditableParamsWithObjectInfo()
            } else {
                identifyBasicEditableParams()
            }
            
            Log.d(TAG, "参数分析完成: ${editableParams.size} 个可编辑参数")
            
            return ParseResult(
                success = true,
                workflow = workflowJson,
                allNodes = allNodes,
                inputNodes = inputNodes,
                outputNodes = outputNodes,
                editableParams = editableParams
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "工作流解析失败", e)
            return ParseResult(
                success = false,
                errorMessage = "解析失败: ${e.message}"
            )
        }
    }
    
    /**
     * 识别工作流中的所有节点
     */
    private fun identifyAllNodes(): List<NodeInfo> {
        val nodes = mutableListOf<NodeInfo>()
        val workflowObj = workflow ?: return nodes
        
        for ((nodeId, nodeElement) in workflowObj) {
            // 跳过非节点数据
            if (nodeElement !is JsonObject) continue
            
            val nodeObj = nodeElement.jsonObject
            val classType = nodeObj["class_type"]?.jsonPrimitive?.content ?: continue
            
            // 检查是否是输出节点
            val isOutputNode = isOutputNodeType(classType)
            
            // 检查是否是输入节点（没有输入连接的节点）
            val isInputNode = checkIsInputNode(nodeObj)
            
            // 获取自定义标题
            val customTitle = nodeObj["_meta"]?.jsonObject?.get("title")?.jsonPrimitive?.content
            
            val nodeInfo = NodeInfo(
                id = nodeId,
                type = classType,
                isInputNode = isInputNode,
                isOutputNode = isOutputNode,
                title = customTitle ?: classType,
                customTitle = customTitle
            )
            
            nodes.add(nodeInfo)
        }
        
        return nodes
    }
    
    /**
     * 检查节点类型是否为输出节点
     */
    private fun isOutputNodeType(classType: String): Boolean {
        // 1. 精确匹配
        if (OUTPUT_EXACT_TYPES.contains(classType)) {
            return true
        }
        
        // 2. 关键词模糊匹配
        return OUTPUT_KEYWORDS.any { keyword ->
            classType.contains(keyword, ignoreCase = true)
        }
    }
    
    /**
     * 检查是否为输入节点（没有输入连接）
     */
    private fun checkIsInputNode(nodeObj: JsonObject): Boolean {
        val inputs = nodeObj["inputs"]?.jsonObject ?: return true
        
        // 如果有任何输入是数组形式（连接），则不是纯输入节点
        for ((_, paramValue) in inputs) {
            if (paramValue is JsonArray) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * 使用objectInfo识别可编辑参数
     */
    private fun identifyEditableParamsWithObjectInfo(): List<EditableParam> {
        val params = mutableListOf<EditableParam>()
        val workflowObj = workflow ?: return params
        val objInfo = objectInfo ?: return identifyBasicEditableParams()
        
        for ((nodeId, nodeElement) in workflowObj) {
            if (nodeElement !is JsonObject) continue
            
            val nodeObj = nodeElement.jsonObject
            val classType = nodeObj["class_type"]?.jsonPrimitive?.content ?: continue
            val inputs = nodeObj["inputs"]?.jsonObject ?: continue
            
            // 获取节点类型信息
            val nodeTypeInfo = objInfo[classType] ?: continue
            val requiredInputs = nodeTypeInfo.input?.required ?: continue
            
            for ((paramName, paramValue) in inputs) {
                // 跳过连接类型的参数
                if (paramValue is JsonArray) continue
                
                // 获取参数定义
                val paramDef = requiredInputs[paramName] ?: continue
                
                val param = createParamFromObjectInfo(
                    nodeId, classType, paramName, paramValue, paramDef
                )
                
                if (param != null) {
                    params.add(param)
                }
            }
        }
        
        return params
    }
    
    /**
     * 基于objectInfo创建参数配置
     */
    private fun createParamFromObjectInfo(
        nodeId: String,
        classType: String,
        paramName: String,
        paramValue: JsonElement,
        paramDef: JsonElement
    ): EditableParam? {
        try {
            val paramId = "${nodeId}_${paramName}"
            val displayName = paramName.replace("_", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            
            // 🔍 诊断日志
            Log.d(TAG, "📝 解析参数: classType=$classType, paramName=$paramName")
            Log.d(TAG, "📝 paramValue=$paramValue")
            Log.d(TAG, "📝 paramDef=$paramDef")
            
            // 🎥 最高优先级：LoadVideo 节点的文件参数
            if (classType == "LoadVideo" && (paramName == "file" || paramName == "video")) {
                Log.d(TAG, "🎥 检测到 LoadVideo 的文件参数，强制设置为视频上传")
                return EditableParam(
                    id = paramId,
                    nodeId = nodeId,
                    classType = classType,
                    name = paramName,
                    displayName = displayName,
                    value = paramValue,
                    type = ParamType.STRING,
                    uiConfig = UiConfig(type = UiType.UPLOAD_VIDEO)
                )
            }
            
            // 解析参数定义
            if (paramDef is JsonArray && paramDef.size > 0) {
                val firstElement = paramDef[0]
                
                // 处理枚举类型（按网页逻辑：仅使用该参数自身在 object_info 中给出的 options）
                if (firstElement is JsonArray) {
                    // ✅ 优先检查：图像相关节点（避免被视频参数误判）
                    if (classType == "LoadImage" && paramName == "image") {
                        return EditableParam(
                            id = paramId,
                            nodeId = nodeId,
                            classType = classType,
                            name = paramName,
                            displayName = displayName,
                            value = paramValue,
                            type = ParamType.STRING,
                            uiConfig = UiConfig(
                                type = UiType.UPLOAD_IMAGE,
                                supportsMask = true // LoadImage节点支持遮罩编辑
                            )
                        )
                    }
                    // 检查其他图像上传节点（类名包含 Image 或 Img）
                    else if ((classType.contains("Image", ignoreCase = true) || classType.contains("Img", ignoreCase = true)) &&
                             (paramName == "image" || paramName == "file")) {
                        return EditableParam(
                            id = paramId,
                            nodeId = nodeId,
                            classType = classType,
                            name = paramName,
                            displayName = displayName,
                            value = paramValue,
                            type = ParamType.STRING,
                            uiConfig = UiConfig(
                                type = UiType.UPLOAD_IMAGE
                            )
                        )
                    }
                    // 检查：LoadVideo 节点的文件参数
                    else if ((classType == "LoadVideo" || classType.contains("Video", ignoreCase = true)) && 
                        (paramName == "file" || paramName == "video" || paramName == "input_video")) {
                        return EditableParam(
                            id = paramId,
                            nodeId = nodeId,
                            classType = classType,
                            name = paramName,
                            displayName = displayName,
                            value = paramValue,
                            type = ParamType.STRING,
                            uiConfig = UiConfig(
                                type = UiType.UPLOAD_VIDEO
                            )
                        )
                    }
                    // 🎵 智能识别音频上传参数（优先于视频，避免误判）
                    else if (isAudioUploadParameter(classType, paramName, paramDef, false)) {
                        return EditableParam(
                            id = paramId,
                            nodeId = nodeId,
                            classType = classType,
                            name = paramName,
                            displayName = displayName,
                            value = paramValue,
                            type = ParamType.STRING,
                            uiConfig = UiConfig(
                                type = UiType.UPLOAD_AUDIO
                            )
                        )
                    }
                    // 智能识别其他视频上传参数
                    else if (isVideoUploadParameter(classType, paramName, paramDef, false)) {
                        return EditableParam(
                            id = paramId,
                            nodeId = nodeId,
                            classType = classType,
                            name = paramName,
                            displayName = displayName,
                            value = paramValue,
                            type = ParamType.STRING,
                            uiConfig = UiConfig(
                                type = UiType.UPLOAD_VIDEO
                            )
                        )
                    }
                    // 普通选择列表
                    else {
                        // 普通选择列表
                        val staticOptions = firstElement.map { it.jsonPrimitive.content }
                        return EditableParam(
                            id = paramId,
                            nodeId = nodeId,
                            classType = classType,
                            name = paramName,
                            displayName = displayName,
                            value = paramValue,
                            type = ParamType.STRING,
                            uiConfig = UiConfig(
                                type = UiType.SELECT,
                                options = staticOptions,
                                isDynamic = false
                            )
                        )
                    }
                }
                
                // 处理基本类型
                when (firstElement.jsonPrimitive.content) {
                    "INT" -> {
                        val min = if (paramDef.size > 1) {
                            paramDef[1].jsonObject["min"]?.jsonPrimitive?.double
                        } else null
                        val max = if (paramDef.size > 1) {
                            paramDef[1].jsonObject["max"]?.jsonPrimitive?.double
                        } else null
                        val step = if (paramDef.size > 1) {
                            paramDef[1].jsonObject["step"]?.jsonPrimitive?.double ?: 1.0
                        } else 1.0
                        
                        return EditableParam(
                            id = paramId,
                            nodeId = nodeId,
                            classType = classType,
                            name = paramName,
                            displayName = displayName,
                            value = paramValue,
                            type = ParamType.NUMBER,
                            uiConfig = UiConfig(
                                type = UiType.NUMBER,
                                min = min,
                                max = max,
                                step = step,
                                isSeed = paramName.lowercase().contains("seed")
                            )
                        )
                    }
                    
                    "FLOAT" -> {
                        val min = if (paramDef.size > 1) {
                            paramDef[1].jsonObject["min"]?.jsonPrimitive?.double
                        } else null
                        val max = if (paramDef.size > 1) {
                            paramDef[1].jsonObject["max"]?.jsonPrimitive?.double
                        } else null
                        val step = if (paramDef.size > 1) {
                            paramDef[1].jsonObject["step"]?.jsonPrimitive?.double ?: 0.1
                        } else 0.1
                        
                        return EditableParam(
                            id = paramId,
                            nodeId = nodeId,
                            classType = classType,
                            name = paramName,
                            displayName = displayName,
                            value = paramValue,
                            type = ParamType.NUMBER,
                            uiConfig = UiConfig(
                                type = UiType.NUMBER,
                                min = min,
                                max = max,
                                step = step
                            )
                        )
                    }
                    
                    "STRING" -> {
                        val isMultiline = if (paramDef.size > 1) {
                            paramDef[1].jsonObject["multiline"]?.jsonPrimitive?.boolean == true
                        } else false
                        
                        return EditableParam(
                            id = paramId,
                            nodeId = nodeId,
                            classType = classType,
                            name = paramName,
                            displayName = displayName,
                            value = paramValue,
                            type = ParamType.STRING,
                            uiConfig = UiConfig(
                                type = if (isMultiline) UiType.MULTILINE_TEXT else UiType.TEXT,
                                isMultiline = isMultiline
                            )
                        )
                    }
                    
                    "BOOLEAN" -> {
                        return EditableParam(
                            id = paramId,
                            nodeId = nodeId,
                            classType = classType,
                            name = paramName,
                            displayName = displayName,
                            value = paramValue,
                            type = ParamType.BOOLEAN,
                            uiConfig = UiConfig(type = UiType.CHECKBOX)
                        )
                    }
                    
                    "IMAGE" -> {
                        return EditableParam(
                            id = paramId,
                            nodeId = nodeId,
                            classType = classType,
                            name = paramName,
                            displayName = displayName,
                            value = paramValue,
                            type = ParamType.STRING,
                            uiConfig = UiConfig(type = UiType.UPLOAD_IMAGE)
                        )
                    }
                }
            }
            
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "创建参数配置失败: $paramName", e)
            return null
        }
    }
    
    /**
     * 基础参数识别（不依赖objectInfo）
     */
    private fun identifyBasicEditableParams(): List<EditableParam> {
        val params = mutableListOf<EditableParam>()
        val workflowObj = workflow ?: return params
        
        for ((nodeId, nodeElement) in workflowObj) {
            if (nodeElement !is JsonObject) continue
            
            val nodeObj = nodeElement.jsonObject
            val classType = nodeObj["class_type"]?.jsonPrimitive?.content ?: continue
            val inputs = nodeObj["inputs"]?.jsonObject ?: continue
            
            for ((paramName, paramValue) in inputs) {
                // 跳过连接类型的参数
                if (paramValue is JsonArray) continue
                
                val param = createBasicParamConfig(nodeId, classType, paramName, paramValue)
                if (param != null) {
                    params.add(param)
                }
            }
        }
        
        return params
    }
    
    /**
     * 创建基础参数配置
     */
    private fun createBasicParamConfig(
        nodeId: String,
        classType: String,
        paramName: String,
        paramValue: JsonElement
    ): EditableParam? {
        try {
            val paramId = "${nodeId}_${paramName}"
            val displayName = paramName.replace("_", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            
            // 根据参数值类型和名称推断UI配置
            val (paramType, uiConfig) = when {
                paramValue is JsonPrimitive && paramValue.isString -> {
                    val stringValue = paramValue.content
                    when {
                        paramName.lowercase().contains("image") -> 
                            ParamType.STRING to UiConfig(type = UiType.UPLOAD_IMAGE)
                        paramName.lowercase().contains("sampler") -> {
                            val samplerOptions = listOf(
                                "euler", "euler_ancestral", "heun", "dpm_2", 
                                "dpm_2_ancestral", "lms", "dpm_fast", "dpm_adaptive", 
                                "dpmpp_2s_ancestral", "dpmpp_sde", "dpmpp_3m_sde", 
                                "ddim", "uni_pc", "uni_pc_bh2"
                            )
                            ParamType.STRING to UiConfig(
                                type = UiType.SELECT,
                                options = if (samplerOptions.contains(stringValue)) samplerOptions 
                                         else listOf(stringValue) + samplerOptions
                            )
                        }
                        stringValue.length > 50 -> 
                            ParamType.STRING to UiConfig(type = UiType.MULTILINE_TEXT, isMultiline = true)
                        else -> 
                            ParamType.STRING to UiConfig(type = UiType.TEXT)
                    }
                }
                
                paramValue is JsonPrimitive && paramValue.booleanOrNull != null -> {
                    ParamType.BOOLEAN to UiConfig(type = UiType.CHECKBOX)
                }
                
                paramValue is JsonPrimitive && (paramValue.intOrNull != null || paramValue.doubleOrNull != null) -> {
                    val numValue = paramValue.doubleOrNull ?: paramValue.intOrNull?.toDouble() ?: 0.0
                    
                    val (min, max, step, isSeed) = when {
                        paramName.lowercase().contains("seed") -> 
                            Tuple4(SEED_MIN, SEED_MAX, SEED_STEP, true)
                        paramName.lowercase().contains("step") -> 
                            Tuple4(STEP_MIN, STEP_MAX, STEP_STEP, false)
                        paramName.lowercase().contains("width") || paramName.lowercase().contains("height") -> 
                            Tuple4(DIMENSION_MIN, DIMENSION_MAX, DIMENSION_STEP, false)
                        paramName.lowercase().contains("cfg") || paramName.lowercase().contains("scale") -> 
                            Tuple4(CFG_MIN, CFG_MAX, CFG_STEP, false)
                        else -> 
                            Tuple4(DEFAULT_MIN, DEFAULT_MAX, if (numValue % 1.0 == 0.0) DEFAULT_STEP_INT else DEFAULT_STEP_FLOAT, false)
                    }
                    
                    ParamType.NUMBER to UiConfig(
                        type = UiType.NUMBER,
                        min = min,
                        max = max,
                        step = step,
                        isSeed = isSeed
                    )
                }
                
                else -> ParamType.STRING to UiConfig(type = UiType.TEXT)
            }
            
            return EditableParam(
                id = paramId,
                nodeId = nodeId,
                classType = classType,
                name = paramName,
                displayName = displayName,
                value = paramValue,
                type = paramType,
                uiConfig = uiConfig
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "创建基础参数配置失败: $paramName", e)
            return null
        }
    }
    
    /**
     * 辅助数据类 - 四元组
     */
    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

