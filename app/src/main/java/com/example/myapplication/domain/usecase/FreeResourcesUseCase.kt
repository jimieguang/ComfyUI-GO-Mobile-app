package com.example.myapplication.domain.usecase

import com.example.myapplication.data.repository.ComfyUIRepository
import javax.inject.Inject

/**
 * 释放内存/卸载模型（ComfyUI: POST /free）
 *
 * 说明：
 * - 本 UseCase 不引入 UI，只提供一个可复用入口，供后续按钮/策略调用。
 * - serverUrl 从 Repository 配置读取，避免 UI 层关心 URL 细节。
 *
 * 文档：https://docs.comfy.org/zh-CN/development/comfyui-server/comms_routes
 */
class FreeResourcesUseCase @Inject constructor(
    private val repository: ComfyUIRepository
) {
    suspend operator fun invoke(
        unloadModels: Boolean = false,
        freeMemory: Boolean = false
    ): Result<Unit> {
        val serverUrl = repository.getServerUrl()
        if (serverUrl.isBlank()) return Result.failure(IllegalStateException("服务器地址未配置"))
        if (!unloadModels && !freeMemory) return Result.success(Unit) // 空操作：不发请求
        return repository.freeResources(
            serverUrl = serverUrl,
            unloadModels = unloadModels,
            freeMemory = freeMemory
        )
    }
}




