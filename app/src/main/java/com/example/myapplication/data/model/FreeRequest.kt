package com.example.myapplication.data.model

import kotlinx.serialization.Serializable

/**
 * ComfyUI /free 请求体
 * 文档：https://docs.comfy.org/zh-CN/development/comfyui-server/comms_routes
 *
 * - unload_models: 卸载模型（更彻底，下一次执行会更慢但更省显存）
 * - free_memory: 释放内存/缓存（相对温和）
 */
@Serializable
data class FreeRequest(
    val unload_models: Boolean = false,
    val free_memory: Boolean = false
)




