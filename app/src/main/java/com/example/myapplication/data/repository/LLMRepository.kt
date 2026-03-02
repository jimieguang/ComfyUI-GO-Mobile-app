package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.api.BaiLianApiService
import com.example.myapplication.data.model.*
import com.example.myapplication.util.LLMPreferences
import com.example.myapplication.util.LLMProvider
import kotlinx.coroutines.flow.first
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM数据仓库
 * 负责与阿里百炼API交互
 */
@Singleton
class LLMRepository @Inject constructor(
    private val apiService: BaiLianApiService,
    private val preferences: LLMPreferences
) {
    
    companion object {
        private const val TAG = "LLMRepository"
    }
    
    /**
     * 处理文本操作
     * @param operation 操作类型
     * @param inputText 输入文本
     * @return 处理后的文本
     */
    suspend fun processText(
        operation: TextOperation,
        inputText: String
    ): Result<ProcessResult> {
        return try {
            Log.d(TAG, "开始处理文本操作: ${operation.displayName}")
            Log.d(TAG, "输入文本长度: ${inputText.length}")
            
            // 1. 获取配置
            val config = preferences.getConfig().first()
            
            // 2. 检查API Key
            if (config.apiKey.isBlank()) {
                Log.e(TAG, "API Key未配置")
                return Result.failure(Exception("请先在设置中配置API Key"))
            }
            
            // 3. 构建用户提示词（填充模板）
            val userPrompt = operation.userPromptTemplate.replace("{text}", inputText)
            
            // 4. 根据服务商构建不同格式的请求并调用相应的API
            val messages = listOf(
                BaiLianMessage(role = "system", content = operation.systemPrompt),
                BaiLianMessage(role = "user", content = userPrompt)
            )
            
            // 根据服务商处理不同的请求和响应
            when (config.provider) {
                LLMProvider.ALIYUN -> {
                    val url = "${config.baseUrl}api/v1/services/aigc/text-generation/generation"
                    val request = BaiLianRequest(
                        model = config.modelName,
                        input = BaiLianInput(messages = messages),
                        parameters = BaiLianParameters(
                            resultFormat = "message",
                            temperature = config.temperature,
                            maxTokens = config.maxTokens
                        )
                    )
                    Log.d(TAG, "发送请求到 [阿里云百炼], URL: $url, 模型: ${config.modelName}")
                    
                    val response = apiService.generateTextAliyun(url = url, request = request)
                    return handleAliyunResponse(response)
                }
                LLMProvider.SILICON_FLOW -> {
                    val url = "${config.baseUrl}/chat/completions"
                    val request = OpenAIRequest(
                        model = config.modelName,
                        messages = messages,
                        temperature = config.temperature,
                        maxTokens = config.maxTokens
                    )
                    Log.d(TAG, "发送请求到 [硅基流动], URL: $url, 模型: ${config.modelName}")
                    
                    val response = apiService.generateTextOpenAI(url = url, request = request)
                    return handleOpenAIResponse(response)
                }
                else -> return Result.failure(Exception("不支持的服务商: ${config.provider}"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "文本处理异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 测试API连接
     */
    suspend fun testConnection(provider: String): Result<String> {
        return try {
            val config = preferences.getConfig().first()
            
            if (config.apiKey.isBlank()) {
                return Result.failure(Exception("请先为 ${provider.uppercase()} 配置API Key"))
            }
            
            val messages = listOf(
                BaiLianMessage(role = "user", content = "Hi")
            )
            
            when (config.provider) {
                LLMProvider.ALIYUN -> {
                    val url = "${config.baseUrl}api/v1/services/aigc/text-generation/generation"
                    val request = BaiLianRequest(
                        model = config.modelName,
                        input = BaiLianInput(messages = messages),
                        parameters = BaiLianParameters(
                            resultFormat = "message",
                            maxTokens = 10
                        )
                    )
                    val response = apiService.generateTextAliyun(url = url, request = request)
                    
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        if (body.code == null && body.output != null) {
                            return Result.success("连接成功！")
                        } else {
                            return Result.failure(Exception("API错误: ${body.message}"))
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        return Result.failure(Exception(getErrorMessage(response.code(), errorBody)))
                    }
                }
                LLMProvider.SILICON_FLOW -> {
                    val url = "${config.baseUrl}/chat/completions"
                    val request = OpenAIRequest(
                        model = config.modelName,
                        messages = messages,
                        maxTokens = 10
                    )
                    val response = apiService.generateTextOpenAI(url = url, request = request)
                    
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        if (body.error == null && body.choices != null) {
                            return Result.success("连接成功！")
                        } else {
                            return Result.failure(Exception("API错误: ${body.error?.message}"))
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        return Result.failure(Exception(getErrorMessage(response.code(), errorBody)))
                    }
                }
                else -> return Result.failure(Exception("不支持的服务商: ${config.provider}"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "测试连接失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 根据HTTP状态码获取友好错误信息
     */
    private fun getErrorMessage(code: Int, errorBody: String?): String {
        return when (code) {
            400 -> "请求参数错误"
            401 -> "API Key无效或已过期，请检查配置"
            403 -> "访问被拒绝，请检查API权限"
            429 -> "请求过于频繁，请稍后再试"
            500, 502, 503, 504 -> "服务器错误，请稍后再试"
            else -> {
                // 尝试从错误体中提取信息
                if (errorBody != null && errorBody.contains("message")) {
                    try {
                        // 简单提取message字段
                        val messageStart = errorBody.indexOf("\"message\"") + 11
                        val messageEnd = errorBody.indexOf("\"", messageStart)
                        if (messageStart > 10 && messageEnd > messageStart) {
                            errorBody.substring(messageStart, messageEnd)
                        } else {
                            "API调用失败 (代码: $code)"
                        }
                    } catch (e: Exception) {
                        "API调用失败 (代码: $code)"
                    }
                } else {
                    "API调用失败 (代码: $code)"
                }
            }
        }
    }
    
    /**
     * 处理阿里云百炼的响应
     */
    private fun handleAliyunResponse(response: Response<BaiLianResponse>): Result<ProcessResult> {
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                // 检查错误响应
                if (body.code != null && body.message != null) {
                    Log.e(TAG, "API返回错误: ${body.code} - ${body.message}")
                    return Result.failure(Exception("API错误: ${body.message}"))
                }
                
                // 提取内容
                val content = when {
                    body.output?.choices?.isNotEmpty() == true -> {
                        body.output.choices.firstOrNull()?.message?.content
                    }
                    body.output?.text != null -> {
                        body.output.text
                    }
                    else -> null
                }
                
                if (content != null && content.isNotBlank()) {
                    Log.d(TAG, "API调用成功，返回文本长度: ${content.length}")
                    return Result.success(
                        ProcessResult(
                            outputText = content.trim(),
                            usage = body.usage,
                            requestId = body.requestId ?: ""
                        )
                    )
                } else {
                    Log.e(TAG, "API返回内容为空")
                    return Result.failure(Exception("API返回内容为空"))
                }
            } else {
                return Result.failure(Exception("响应体为空"))
            }
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e(TAG, "API调用失败: ${response.code()}, 错误: $errorBody")
            return Result.failure(Exception(getErrorMessage(response.code(), errorBody)))
        }
    }
    
    /**
     * 处理 OpenAI 兼容格式的响应（硅基流动等）
     */
    private fun handleOpenAIResponse(response: Response<OpenAIResponse>): Result<ProcessResult> {
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                // 检查错误响应
                if (body.error != null) {
                    Log.e(TAG, "API返回错误: ${body.error.message}")
                    return Result.failure(Exception("API错误: ${body.error.message}"))
                }
                
                // 提取内容
                val content = body.choices?.firstOrNull()?.message?.content
                
                if (content != null && content.isNotBlank()) {
                    Log.d(TAG, "API调用成功，返回文本长度: ${content.length}")
                    
                    // 转换 OpenAI 格式的 usage 到 BaiLianUsage
                    val usage = body.usage?.let {
                        BaiLianUsage(
                            inputTokens = it.promptTokens,
                            outputTokens = it.completionTokens,
                            totalTokens = it.totalTokens
                        )
                    }
                    
                    return Result.success(
                        ProcessResult(
                            outputText = content.trim(),
                            usage = usage,
                            requestId = body.id ?: ""
                        )
                    )
                } else {
                    Log.e(TAG, "API返回内容为空")
                    return Result.failure(Exception("API返回内容为空"))
                }
            } else {
                return Result.failure(Exception("响应体为空"))
            }
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e(TAG, "API调用失败: ${response.code()}, 错误: $errorBody")
            return Result.failure(Exception(getErrorMessage(response.code(), errorBody)))
        }
    }

    /**
     * 获取可用模型列表
     * @param provider 服务商（aliyun 或 silicon_flow）
     * @return 模型列表
     */
    suspend fun getAvailableModels(provider: String): Result<List<Pair<String, String>>> {
        return try {
            Log.d(TAG, "获取模型列表，服务商: $provider")
            
            val config = preferences.getConfig().first()
            
            // 阿里云百炼暂不支持动态获取模型列表，返回硬编码列表
            if (provider == LLMProvider.ALIYUN) {
                return Result.success(listOf(
                    "qwen-turbo" to "通义千问-Turbo (推荐)",
                    "qwen-plus" to "通义千问-Plus",
                    "qwen-max" to "通义千问-Max"
                ))
            }
            
            // 硅基流动：调用 /v1/models 接口
            if (provider == LLMProvider.SILICON_FLOW) {
                // 如果没有API Key，返回硬编码的默认列表
                if (config.apiKey.isBlank()) {
                    Log.d(TAG, "API Key未配置，返回默认模型列表")
                    return Result.success(listOf(
                        "Qwen/Qwen2-7B-Instruct" to "通义千问Qwen2-7B",
                        "deepseek-ai/DeepSeek-V2-Chat" to "DeepSeek-V2 (推荐)",
                        "meta-llama/Llama-3-8b-chat-hf" to "Llama3-8B"
                    ))
                }
                
                // 使用 sub_type=chat 参数只获取对话模型
                val url = "${config.baseUrl}/models?sub_type=chat"
                Log.d(TAG, "请求对话模型列表: $url")
                
                val response = apiService.getModels(url)
                
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val models = body.data?.map { model ->
                        // 从模型ID生成显示名称
                        val displayName = model.name ?: model.id.split("/").lastOrNull() ?: model.id
                        model.id to displayName
                    } ?: emptyList()
                    
                    Log.d(TAG, "成功获取 ${models.size} 个模型")
                    
                    // 如果API返回的模型列表为空，返回默认列表
                    if (models.isEmpty()) {
                        Log.d(TAG, "API返回空列表，使用默认模型列表")
                        return Result.success(listOf(
                            "Qwen/Qwen2-7B-Instruct" to "通义千问Qwen2-7B",
                            "deepseek-ai/DeepSeek-V2-Chat" to "DeepSeek-V2 (推荐)",
                            "meta-llama/Llama-3-8b-chat-hf" to "Llama3-8B"
                        ))
                    }
                    
                    return Result.success(models)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "获取模型列表失败: ${response.code()}, 错误: $errorBody")
                    
                    // API调用失败，返回默认列表
                    Log.d(TAG, "使用默认模型列表作为备选")
                    return Result.success(listOf(
                        "Qwen/Qwen2-7B-Instruct" to "通义千问Qwen2-7B",
                        "deepseek-ai/DeepSeek-V2-Chat" to "DeepSeek-V2 (推荐)",
                        "meta-llama/Llama-3-8b-chat-hf" to "Llama3-8B"
                    ))
                }
            }
            
            // 未知服务商
            Result.failure(Exception("不支持的服务商: $provider"))
            
        } catch (e: Exception) {
            Log.e(TAG, "获取模型列表异常", e)
            
            // 异常情况下返回默认列表
            when (provider) {
                LLMProvider.ALIYUN -> Result.success(listOf(
                    "qwen-turbo" to "通义千问-Turbo (推荐)",
                    "qwen-plus" to "通义千问-Plus",
                    "qwen-max" to "通义千问-Max"
                ))
                LLMProvider.SILICON_FLOW -> Result.success(listOf(
                    "Qwen/Qwen2-7B-Instruct" to "通义千问Qwen2-7B",
                    "deepseek-ai/DeepSeek-V2-Chat" to "DeepSeek-V2 (推荐)",
                    "meta-llama/Llama-3-8b-chat-hf" to "Llama3-8B"
                ))
                else -> Result.failure(e)
            }
        }
    }

    /**
     * 处理结果
     */
    data class ProcessResult(
        val outputText: String,
        val usage: BaiLianUsage?,
        val requestId: String
    )
}

