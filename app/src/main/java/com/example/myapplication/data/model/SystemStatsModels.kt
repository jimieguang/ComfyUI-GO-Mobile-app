package com.example.myapplication.data.model

import kotlinx.serialization.Serializable

/**
 * ComfyUI /system_stats 响应模型（按官方 Prompt Server 实现对齐）
 * 参考：
 * - 代码：ComfyUI server.py @routes.get("/system_stats")
 * - 文档：https://docs.comfy.org/zh-CN/development/comfyui-server/comms_routes
 *
 * 注意：单位由服务端 comfy.model_management 决定（通常是 bytes）。
 */
@Serializable
data class SystemStatsResponse(
    val system: SystemInfo,
    val devices: List<DeviceInfo> = emptyList()
)

@Serializable
data class SystemInfo(
    val os: String? = null,
    val ram_total: Long? = null,
    val ram_free: Long? = null,
    val comfyui_version: String? = null,
    val required_frontend_version: String? = null,
    val installed_templates_version: String? = null,
    val required_templates_version: String? = null,
    val python_version: String? = null,
    val pytorch_version: String? = null,
    val embedded_python: Boolean? = null
)

@Serializable
data class DeviceInfo(
    val name: String? = null,
    val type: String? = null,
    val index: Int? = null,
    val vram_total: Long? = null,
    val vram_free: Long? = null,
    val torch_vram_total: Long? = null,
    val torch_vram_free: Long? = null
)


