package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * 阿里百炼API请求模型
 */
@Serializable
data class BaiLianRequest(
    val model: String,
    val input: BaiLianInput,
    val parameters: BaiLianParameters? = null
)

@Serializable
data class BaiLianInput(
    val messages: List<BaiLianMessage>
)

@Serializable
data class BaiLianMessage(
    val role: String,  // "system" | "user" | "assistant"
    val content: String
)

@Serializable
data class BaiLianParameters(
    @SerialName("result_format")
    val resultFormat: String = "message",
    val temperature: Float? = 0.7f,
    @SerialName("max_tokens")
    val maxTokens: Int? = 2000,
    @SerialName("top_p")
    val topP: Float? = null,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("repetition_penalty")
    val repetitionPenalty: Float? = null,
    val seed: Int? = null,
    val stop: List<String>? = null,
    @SerialName("enable_search")
    val enableSearch: Boolean? = null
)

/**
 * 阿里百炼API响应模型
 */
@Serializable
data class BaiLianResponse(
    val output: BaiLianOutput? = null,
    val usage: BaiLianUsage? = null,
    @SerialName("request_id")
    val requestId: String? = null,
    // 错误响应字段
    val code: String? = null,
    val message: String? = null
)

@Serializable
data class BaiLianOutput(
    // 支持多种响应格式
    val choices: List<BaiLianChoice>? = null,
    val text: String? = null,  // 某些模型直接返回text
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class BaiLianChoice(
    @SerialName("finish_reason")
    val finishReason: String? = null,
    val message: BaiLianMessage? = null
)

@Serializable
data class BaiLianUsage(
    @SerialName("input_tokens")
    val inputTokens: Int? = null,
    @SerialName("output_tokens")
    val outputTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null
)

/**
 * 文本操作类型
 */
enum class TextOperationType {
    TRANSLATE_ZH_TO_EN,    // 中译英
    TRANSLATE_EN_TO_ZH,    // 英译中
    CUSTOM                 // 自定义
}

/**
 * 文本操作定义
 */
data class TextOperation(
    val type: TextOperationType,
    val displayName: String,
    val icon: String,
    val systemPrompt: String,
    val userPromptTemplate: String,
    val description: String = ""
)

/**
 * 操作历史记录（用于撤销）
 */
data class OperationHistory(
    val operation: TextOperation,
    val originalText: String,
    val resultText: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 自定义规则
 */
@Serializable
data class CustomRule(
    val id: String,
    val name: String,
    val icon: String,
    val systemPrompt: String,
    val userPromptTemplate: String,
    val createdAt: Long,
    val isEnabled: Boolean = true  // 是否启用
)

/**
 * OpenAI 兼容的API请求模型（用于硅基流动等）
 */
@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<BaiLianMessage>,
    val temperature: Float? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("top_p")
    val topP: Float? = null,
    val stream: Boolean = false
)

/**
 * OpenAI 兼容的API响应模型（用于硅基流动等）
 */
@Serializable
data class OpenAIResponse(
    val id: String? = null,
    val `object`: String? = null,
    val created: Long? = null,
    val model: String? = null,
    val choices: List<OpenAIChoice>? = null,
    val usage: OpenAIUsage? = null,
    // 错误响应
    val error: OpenAIError? = null
)

@Serializable
data class OpenAIChoice(
    val index: Int? = null,
    val message: BaiLianMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class OpenAIUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null
)

@Serializable
data class OpenAIError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

/**
 * 模型列表响应（OpenAI兼容格式）
 */
@Serializable
data class ModelsListResponse(
    val `object`: String? = null,  // "list"
    val data: List<ModelInfo>? = null
)

@Serializable
data class ModelInfo(
    val id: String,  // 模型ID，如 "Qwen/Qwen2-7B-Instruct"
    val `object`: String? = null,  // "model"
    val created: Long? = null,
    val owned_by: String? = null,
    // 额外的模型信息
    val name: String? = null,
    val description: String? = null
)

