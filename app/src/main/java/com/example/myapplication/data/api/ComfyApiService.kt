package com.example.myapplication.data.api

import com.example.myapplication.data.model.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * ComfyUI API 服务接口
 * 定义所有与 ComfyUI 服务器交互的 HTTP 接口
 */
interface ComfyApiService {
    
    /**
     * 获取所有节点类型信息
     */
    @GET("object_info")
    suspend fun getObjectInfo(): Response<Map<String, NodeInfo>>
    
    /**
     * 提交工作流执行请求
     */
    @POST("prompt")
    suspend fun queuePrompt(@Body request: PromptRequest): Response<PromptResponse>
    
    /**
     * 上传图像文件
     */
    @Multipart
    @POST("upload/image")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part,
        @Part("type") type: okhttp3.RequestBody,
        @Part("overwrite") overwrite: okhttp3.RequestBody
    ): Response<UploadResponse>
    
    /**
     * 上传图像文件（带子文件夹）
     */
    @Multipart
    @POST("upload/image")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part,
        @Part("type") type: okhttp3.RequestBody,
        @Part("overwrite") overwrite: okhttp3.RequestBody,
        @Part("subfolder") subfolder: okhttp3.RequestBody
    ): Response<UploadResponse>
    
    /**
     * 上传遮罩文件
     */
    @Multipart
    @POST("upload/mask")
    suspend fun uploadMask(
        @Part image: MultipartBody.Part,
        @Part("type") type: okhttp3.RequestBody,
        @Part("subfolder") subfolder: okhttp3.RequestBody,
        @Part("original_ref") originalRef: okhttp3.RequestBody
    ): Response<UploadResponse>
    
    /**
     * 获取图像文件
     */
    @GET("view")
    suspend fun getImage(
        @Query("filename") filename: String,
        @Query("subfolder") subfolder: String = "",
        @Query("type") type: String = "output"
    ): Response<ResponseBody>
    
    /**
     * 获取执行历史
     */
    @GET("history/{prompt_id}")
    suspend fun getHistory(@Path("prompt_id") promptId: String): Response<kotlinx.serialization.json.JsonObject>
    
    /**
     * 获取队列信息
     */
    @GET("queue")
    suspend fun getQueueInfo(): Response<kotlinx.serialization.json.JsonObject>
    
    /**
     * 中断执行
     */
    @POST("interrupt")
    suspend fun interrupt(): Response<Unit>

    /**
     * 释放内存/卸载模型
     * ComfyUI: POST /free
     * 文档：https://docs.comfy.org/zh-CN/development/comfyui-server/comms_routes
     */
    @POST("free")
    suspend fun free(@Body request: com.example.myapplication.data.model.FreeRequest): Response<Unit>
    
    /**
     * 清空队列（旧版，保留兼容）
     */
    @POST("queue")
    suspend fun clearQueue(@Body request: com.example.myapplication.data.model.QueueClearRequest): Response<Unit>
    
    /**
     * 删除指定队列任务（官方标准API）
     */
    @POST("queue")
    suspend fun deleteQueueItems(@Body request: com.example.myapplication.data.model.QueueDeleteRequest): Response<Unit>
    
    /**
     * 获取ComfyUI资源信息 (模型、采样器等)
     * 这个端点可能因ComfyUI版本而异，需要根据实际情况调整
     */
    @GET("embeddings")
    suspend fun getEmbeddings(): Response<List<String>>
    
    /**
     * 获取模型扩展信息
     * 某些ComfyUI版本可能有这个端点
     */
    @GET("extensions")
    suspend fun getExtensions(): Response<List<String>>
    
    /**
     * 获取系统资源统计（简化版）
     * 需要安装 comfyui_system_monitor 插件
     */
    @GET("api/system_stats/simple")
    suspend fun getSystemStatsSimple(): Response<com.example.myapplication.data.model.SystemStatsSimple>
    
    /**
     * 获取系统资源统计（完整版）
     * 需要安装 comfyui_system_monitor 插件
     */
    @GET("api/system_stats")
    suspend fun getSystemStatsFull(): Response<com.example.myapplication.data.model.SystemStatsFull>
}
