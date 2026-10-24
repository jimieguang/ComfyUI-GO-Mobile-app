package com.example.myapplication.data.api

import com.example.myapplication.data.model.OpenAIRequest
import com.example.myapplication.data.model.OpenAIResponse
import com.example.myapplication.data.model.ModelsListResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * LLM API服务接口 (通用)
 * 支持多种API格式（阿里云百炼、OpenAI等）
 */
interface BaiLianApiService {
    
    /**
     * OpenAI兼容格式的文本生成接口（用于硅基流动等）
     * @param url 完整的请求URL
     * @param request OpenAI格式的请求体
     */
    @POST
    suspend fun generateTextOpenAI(
        @Url url: String,
        @Body request: OpenAIRequest
    ): Response<OpenAIResponse>
    
    /**
     * 获取模型列表（OpenAI兼容格式）
     * @param url 完整的请求URL（如 https://api.siliconflow.cn/v1/models）
     */
    @GET
    suspend fun getModels(
        @Url url: String
    ): Response<ModelsListResponse>
}

