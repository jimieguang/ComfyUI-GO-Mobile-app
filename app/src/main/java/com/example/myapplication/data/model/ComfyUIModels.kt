package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * ComfyUI 相关数据模型
 */

/**
 * ComfyUI 服务器资源信息 (用于动态选项获取)
 */
@Serializable
data class ComfyUIResources(
    val models: ComfyUIModels? = null,
    val samplers: List<String>? = null,
    val schedulers: List<String>? = null,
    val upscale_models: List<String>? = null,
    val vae: List<String>? = null,
    val loras: List<String>? = null,
    val embeddings: List<String>? = null,
    val hypernetworks: List<String>? = null,
    val controlnet: List<String>? = null
)

/**
 * ComfyUI 模型分类
 */
@Serializable
data class ComfyUIModels(
    val checkpoints: List<String>? = null,
    val configs: List<String>? = null,
    val vae: List<String>? = null,
    val loras: List<String>? = null,
    val upscale_models: List<String>? = null,
    val embeddings: List<String>? = null,
    val hypernetworks: List<String>? = null,
    val controlnet: List<String>? = null,
    val clip: List<String>? = null,
    val unet: List<String>? = null,
    val gligen: List<String>? = null,
    val style_models: List<String>? = null
)

/**
 * 节点信息
 */
@Serializable
data class NodeInfo(
    val input: NodeInput? = null,
    // 有些节点返回字符串、有些返回数组，甚至嵌套数组，使用JsonElement以兼容
    val output: JsonElement? = null,
    val output_is_list: JsonElement? = null,
    // 有些自定义节点此处返回的是字符串而非数组，使用JsonElement以兼容两者
    val output_name: JsonElement? = null,
    val name: String? = null,
    val display_name: String? = null,
    val description: String? = null,
    val category: String? = null,
    val python_module: String? = null
)

@Serializable
data class NodeInput(
    val required: Map<String, JsonElement>? = null,
    val optional: Map<String, JsonElement>? = null
)

/**
 * 提示请求
 */
@Serializable
data class PromptRequest(
    val prompt: JsonObject,
    val client_id: String
)

/**
 * 提示响应
 */
@Serializable
data class PromptResponse(
    val prompt_id: String,
    val number: Int? = null,
    val node_errors: Map<String, JsonElement>? = null
)

/**
 * 上传响应
 */
@Serializable
data class UploadResponse(
    val name: String,
    val subfolder: String? = null,
    val type: String? = null
)

/**
 * WebSocket 消息
 */
@Serializable
data class WebSocketMessage(
    val type: String,
    val data: JsonElement? = null
)

/**
 * 执行进度
 */
@Serializable
data class ExecutionProgress(
    val node: String? = null,
    val value: Int = 0,
    val max: Int = 0,
    val step: Int? = null,
    val total_steps: Int? = null
)

/**
 * 生成的图像信息
 */
@Serializable
data class GeneratedImage(
    val filename: String,
    val subfolder: String = "",
    val type: String = "output",
    val url: String = ""
)

/**
 * 生成的视频信息
 */
@Serializable
data class GeneratedVideo(
    val filename: String,
    val subfolder: String = "",
    val type: String = "output",
    val url: String = "",
    val duration: Long? = null,  // 视频时长(毫秒)
    val format: String? = null   // 视频格式(mp4/webm等)
)

/**
 * 连接状态
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * 参数类型
 */
enum class ParameterType {
    STRING,
    TEXT,
    INTEGER,
    FLOAT,
    BOOLEAN,
    ENUM,
    SEED,
    IMAGE,
    VIDEO,  // 新增：视频上传类型
    UNKNOWN
}

/**
 * UI类型
 */
enum class UIType {
    TEXT,
    NUMBER,
    SELECT,
    CHECKBOX,
    UPLOAD_IMAGE,
    SEED
}

/**
 * 参数配置
 */
sealed class ParameterConfig {
    abstract val name: String
    abstract val displayName: String
    
    data class StringConfig(
        override val name: String,
        override val displayName: String,
        val defaultValue: String = "",
        val maxLength: Int = 500
    ) : ParameterConfig()
    
    data class TextConfig(
        override val name: String,
        override val displayName: String,
        val defaultValue: String = "",
        val maxLength: Int = 2000
    ) : ParameterConfig()
    
    data class IntegerConfig(
        override val name: String,
        override val displayName: String,
        val min: Int = Int.MIN_VALUE,
        val max: Int = Int.MAX_VALUE,
        val step: Int = 1,
        val defaultValue: Int = 0
    ) : ParameterConfig()
    
    data class FloatConfig(
        override val name: String,
        override val displayName: String,
        val min: Float = Float.MIN_VALUE,
        val max: Float = Float.MAX_VALUE,
        val step: Float = 0.01f,
        val defaultValue: Float = 0f
    ) : ParameterConfig()
    
    data class BooleanConfig(
        override val name: String,
        override val displayName: String,
        val defaultValue: Boolean = false
    ) : ParameterConfig()
    
    data class EnumConfig(
        override val name: String,
        override val displayName: String,
        val options: List<String>,
        val defaultValue: String = ""
    ) : ParameterConfig()
    
    data class SeedConfig(
        override val name: String,
        override val displayName: String,
        val defaultValue: Long = -1L
    ) : ParameterConfig()
    
    data class ImageConfig(
        override val name: String,
        override val displayName: String
    ) : ParameterConfig()
    
    data class VideoConfig(
        override val name: String,
        override val displayName: String
    ) : ParameterConfig()
}
