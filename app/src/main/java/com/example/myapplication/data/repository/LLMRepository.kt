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
            
            // 统一走 OpenAI 兼容协议（百炼原生协议已移除）
            val url = buildOpenAiUrl(config.baseUrl)
            val request = OpenAIRequest(
                model = config.modelName,
                messages = messages,
                temperature = config.temperature,
                maxTokens = config.maxTokens
            )
            Log.d(TAG, "发送请求到 [OpenAI 兼容], URL: $url, 模型: ${config.modelName}")

            val response = apiService.generateTextOpenAI(url = url, request = request)
            return handleOpenAIResponse(response)
            
        } catch (e: Exception) {
            Log.e(TAG, "文本处理异常", e)
            Result.failure(e)
        }
    }

    /**
     * 规范化 Base URL：去掉尾部斜杠
     */
    private fun normalizeBase(baseUrl: String): String = baseUrl.trim().trimEnd('/')


    /**
     * OpenAI 兼容完整地址：已含 /chat/completions 的直接用，否则补 /chat/completions
     */
    private fun buildOpenAiUrl(baseUrl: String): String {
        val base = normalizeBase(baseUrl)
        return when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }

    /**
     * OpenAI 兼容的模型列表地址
     */
    private fun buildModelsUrl(baseUrl: String): String {
        val base = normalizeBase(baseUrl)
        return when {
            base.endsWith("/chat/completions") -> base.removeSuffix("/chat/completions") + "/models"
            base.endsWith("/v1") -> "$base/models"
            else -> "$base/v1/models"
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
            
            val url = buildOpenAiUrl(config.baseUrl)
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
            val config = preferences.getConfig().first()

            if (config.apiKey.isBlank()) {
                Log.d(TAG, "API Key 未配置，跳过模型列表拉取")
                return Result.success(emptyList())
            }

            val url = buildModelsUrl(config.baseUrl)
            Log.d(TAG, "请求模型列表: $url")

            val response = apiService.getModels(url)

            if (response.isSuccessful && response.body() != null) {
                val models = response.body()!!.data?.map { model ->
                    val displayName = model.name ?: model.id.split("/").lastOrNull() ?: model.id
                    model.id to displayName
                } ?: emptyList()
                Log.d(TAG, "成功获取 ${models.size} 个模型")
                Result.success(models)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "获取模型列表失败: ${response.code()}, 错误: $errorBody")
                Result.failure(Exception(getErrorMessage(response.code(), errorBody)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取模型列表异常", e)
            Result.failure(e)
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

