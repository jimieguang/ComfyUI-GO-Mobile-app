package com.example.myapplication.domain.manager

import android.util.Log
import com.example.myapplication.data.model.ComfyUIResources
import com.example.myapplication.data.model.NodeInfo
import com.example.myapplication.data.repository.ComfyUIRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 参数选项管理器
 * 负责将 object_info 的参数类型映射到实际的选项列表
 */
@Singleton
class ParameterOptionsManager @Inject constructor(
    private val repository: ComfyUIRepository
) {
    
    companion object {
        private const val TAG = "ParameterOptionsManager"
        
        // 常见的参数类型映射
        private val PARAMETER_TYPE_MAPPING = mapOf(
            // 采样器相关
            "sampler_name" to "SAMPLER",
            "sampler" to "SAMPLER",
            // 调度器相关  
            "scheduler" to "SCHEDULER",
            "scheduler_name" to "SCHEDULER",
            // 模型相关
            "ckpt_name" to "CHECKPOINT", 
            "model_name" to "CHECKPOINT",
            "checkpoint" to "CHECKPOINT",
            // VAE相关
            "vae_name" to "VAE",
            "vae" to "VAE",
            // LoRA相关
            "lora_name" to "LORA",
            "lora" to "LORA",
            // Embedding相关
            "embedding_name" to "EMBEDDING",
            "embedding" to "EMBEDDING",
            // ControlNet相关
            "control_net_name" to "CONTROLNET",
            "controlnet" to "CONTROLNET",
            // 上采样模型
            "upscale_model" to "UPSCALE_MODEL",
            "model" to "UPSCALE_MODEL"
        )
    }
    
    // 缓存的资源信息
    private val _cachedResources = MutableStateFlow<ComfyUIResources?>(null)
    val cachedResources: Flow<ComfyUIResources?> = _cachedResources.asStateFlow()
    
    // 缓存的节点信息
    private var cachedObjectInfo: Map<String, NodeInfo>? = null
    
    /**
     * 刷新ComfyUI资源信息
     */
    suspend fun refreshResources(serverUrl: String): Result<ComfyUIResources> {
        return try {
            Log.d(TAG, "开始刷新ComfyUI资源信息")
            
            // 从object_info中提取可用的选项信息
            val objectInfoResult = repository.getObjectInfo(serverUrl)
            if (objectInfoResult.isFailure) {
                val error = objectInfoResult.exceptionOrNull()
                Log.w(TAG, "获取object_info失败，将使用默认选项: ${error?.message}")
                
                // 如果获取object_info失败，使用默认的资源配置
                val defaultResources = createDefaultResources()
                _cachedResources.value = defaultResources
                Log.d(TAG, "使用默认资源配置: ${defaultResources}")
                
                // 虽然获取失败，但我们提供了默认配置，所以返回成功
                return Result.success(defaultResources)
            }
            
            val objectInfo = objectInfoResult.getOrNull() ?: emptyMap()
            cachedObjectInfo = objectInfo
            
            Log.d(TAG, "成功获取到 ${objectInfo.size} 个节点类型的信息")
            
            // 从object_info中提取所有可能的选项
            val resources = extractResourcesFromObjectInfo(objectInfo)
            
            _cachedResources.value = resources
            Log.d(TAG, "资源信息刷新完成: ${getResourceStats()}")
            
            Result.success(resources)
        } catch (e: Exception) {
            Log.e(TAG, "刷新资源信息失败", e)
            
            // 即使出错，也提供默认资源
            val defaultResources = createDefaultResources()
            _cachedResources.value = defaultResources
            Log.d(TAG, "异常情况下使用默认资源配置")
            
            Result.success(defaultResources)
        }
    }
    
    /**
     * 从object_info中提取资源信息
     */
    private fun extractResourcesFromObjectInfo(objectInfo: Map<String, NodeInfo>): ComfyUIResources {
        val samplers = mutableSetOf<String>()
        val schedulers = mutableSetOf<String>()
        val checkpoints = mutableSetOf<String>()
        val vaes = mutableSetOf<String>()
        val loras = mutableSetOf<String>()
        val embeddings = mutableSetOf<String>()
        val controlnets = mutableSetOf<String>()
        val upscaleModels = mutableSetOf<String>()
        
        // 遍历所有节点类型，提取参数选项
        objectInfo.values.forEach { nodeInfo ->
            // 处理必需参数
            nodeInfo.input?.required?.forEach { (paramName, paramDef) ->
                extractOptionsFromParameter(
                    paramName, paramDef, samplers, schedulers, checkpoints, 
                    vaes, loras, embeddings, controlnets, upscaleModels
                )
            }
            
            // 处理可选参数
            nodeInfo.input?.optional?.forEach { (paramName, paramDef) ->
                extractOptionsFromParameter(
                    paramName, paramDef, samplers, schedulers, checkpoints, 
                    vaes, loras, embeddings, controlnets, upscaleModels
                )
            }
        }
        
        // 添加默认选项（以防object_info中没有）
        addDefaultOptions(samplers, schedulers)
        
        return ComfyUIResources(
            samplers = samplers.sorted(),
            schedulers = schedulers.sorted(),
            models = com.example.myapplication.data.model.ComfyUIModels(
                checkpoints = checkpoints.sorted(),
                vae = vaes.sorted(),
                loras = loras.sorted(),
                embeddings = embeddings.sorted(),
                controlnet = controlnets.sorted(),
                upscale_models = upscaleModels.sorted()
            )
        )
    }
    
    /**
     * 从参数定义中提取选项
     */
    private fun extractOptionsFromParameter(
        paramName: String,
        paramDef: JsonElement,
        samplers: MutableSet<String>,
        schedulers: MutableSet<String>, 
        checkpoints: MutableSet<String>,
        vaes: MutableSet<String>,
        loras: MutableSet<String>,
        embeddings: MutableSet<String>,
        controlnets: MutableSet<String>,
        upscaleModels: MutableSet<String>
    ) {
        try {
            // 参数定义通常是一个数组，第一个元素是类型或选项列表
            if (paramDef is JsonArray && paramDef.size > 0) {
                val firstElement = paramDef[0]
                
                // 如果第一个元素是数组，那么它包含选项列表
                if (firstElement is JsonArray) {
                    val options = firstElement.jsonArray.mapNotNull { element ->
                        if (element is JsonPrimitive && element.isString) {
                            element.jsonPrimitive.content
                        } else null
                    }
                    
                    // 根据参数名称判断选项类型
                    when {
                        paramName.contains("sampler", ignoreCase = true) -> samplers.addAll(options)
                        paramName.contains("scheduler", ignoreCase = true) -> schedulers.addAll(options)
                        paramName.contains("ckpt", ignoreCase = true) || 
                        paramName.contains("checkpoint", ignoreCase = true) -> checkpoints.addAll(options)
                        paramName.contains("vae", ignoreCase = true) -> vaes.addAll(options)
                        paramName.contains("lora", ignoreCase = true) -> loras.addAll(options)
                        paramName.contains("embedding", ignoreCase = true) -> embeddings.addAll(options)
                        paramName.contains("controlnet", ignoreCase = true) || 
                        paramName.contains("control_net", ignoreCase = true) -> controlnets.addAll(options)
                        paramName.contains("upscale", ignoreCase = true) || 
                        paramName.contains("model", ignoreCase = true) -> {
                            // 进一步区分是checkpoint还是upscale model
                            if (paramName.contains("upscale", ignoreCase = true)) {
                                upscaleModels.addAll(options)
                            } else {
                                checkpoints.addAll(options)
                            }
                        }
                    }
                    
                    Log.d(TAG, "从参数 $paramName 提取选项: ${options.take(3)}...")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "解析参数 $paramName 的选项时出错: ${e.message}")
        }
    }
    
    /**
     * 添加默认选项（以防object_info中没有足够的信息）
     */
    private fun addDefaultOptions(
        samplers: MutableSet<String>,
        schedulers: MutableSet<String>
    ) {
        // 添加常见的采样器
        if (samplers.isEmpty()) {
            samplers.addAll(listOf(
                "euler", "euler_ancestral", "heun", "dpm_2", "dpm_2_ancestral",
                "lms", "dpm_fast", "dpm_adaptive", "dpmpp_2s_ancestral", 
                "dpmpp_sde", "dpmpp_3m_sde", "ddim", "uni_pc", "uni_pc_bh2"
            ))
        }
        
        // 添加常见的调度器
        if (schedulers.isEmpty()) {
            schedulers.addAll(listOf(
                "normal", "karras", "exponential", "sgm_uniform", "simple", "ddim_uniform"
            ))
        }
    }
    
    /**
     * 根据参数名称获取选项列表
     */
    fun getOptionsForParameter(paramName: String, paramType: String? = null): List<String> {
        val resources = _cachedResources.value ?: return emptyList()
        
        // 首先根据参数名称模式匹配
        return when {
            paramName.contains("sampler", ignoreCase = true) -> 
                resources.samplers ?: emptyList()
                
            paramName.contains("scheduler", ignoreCase = true) -> 
                resources.schedulers ?: emptyList()
                
            paramName.contains("ckpt", ignoreCase = true) || 
            paramName.contains("checkpoint", ignoreCase = true) -> 
                resources.models?.checkpoints ?: emptyList()
                
            paramName.contains("vae", ignoreCase = true) -> 
                resources.models?.vae ?: emptyList()
                
            paramName.contains("lora", ignoreCase = true) -> 
                resources.models?.loras ?: emptyList()
                
            paramName.contains("embedding", ignoreCase = true) -> 
                resources.models?.embeddings ?: emptyList()
                
            paramName.contains("controlnet", ignoreCase = true) || 
            paramName.contains("control_net", ignoreCase = true) -> 
                resources.models?.controlnet ?: emptyList()
                
            paramName.contains("upscale", ignoreCase = true) -> 
                resources.models?.upscale_models ?: emptyList()
                
            else -> {
                // 如果参数名称模式匹配失败，尝试根据参数类型
                when (paramType?.uppercase()) {
                    "SAMPLER" -> resources.samplers ?: emptyList()
                    "SCHEDULER" -> resources.schedulers ?: emptyList()
                    "CHECKPOINT" -> resources.models?.checkpoints ?: emptyList()
                    "VAE" -> resources.models?.vae ?: emptyList()
                    "LORA" -> resources.models?.loras ?: emptyList()
                    "EMBEDDING" -> resources.models?.embeddings ?: emptyList()
                    "CONTROLNET" -> resources.models?.controlnet ?: emptyList()
                    "UPSCALE_MODEL" -> resources.models?.upscale_models ?: emptyList()
                    else -> emptyList()
                }
            }
        }
    }
    
    /**
     * 检查是否有缓存的资源信息
     */
    fun hasResources(): Boolean {
        return _cachedResources.value != null
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        _cachedResources.value = null
        cachedObjectInfo = null
        Log.d(TAG, "已清除资源缓存")
    }
    
    /**
     * 获取资源统计信息
     */
    fun getResourceStats(): Map<String, Int> {
        val resources = _cachedResources.value ?: return emptyMap()
        
        return mapOf(
            "samplers" to (resources.samplers?.size ?: 0),
            "schedulers" to (resources.schedulers?.size ?: 0),
            "checkpoints" to (resources.models?.checkpoints?.size ?: 0),
            "vae" to (resources.models?.vae?.size ?: 0),
            "loras" to (resources.models?.loras?.size ?: 0),
            "embeddings" to (resources.models?.embeddings?.size ?: 0),
            "controlnet" to (resources.models?.controlnet?.size ?: 0),
            "upscale_models" to (resources.models?.upscale_models?.size ?: 0)
        )
    }
    
    /**
     * 创建默认资源配置
     * 当无法从ComfyUI获取实际资源时使用
     */
    private fun createDefaultResources(): ComfyUIResources {
        Log.d(TAG, "创建默认资源配置")
        
        // 提供常见的采样器列表
        val defaultSamplers = listOf(
            "euler", "euler_ancestral", "heun", "dpm_2", "dpm_2_ancestral",
            "lms", "dpm_fast", "dpm_adaptive", "dpmpp_2s_ancestral", 
            "dpmpp_sde", "dpmpp_3m_sde", "ddim", "uni_pc", "uni_pc_bh2"
        )
        
        // 提供常见的调度器列表
        val defaultSchedulers = listOf(
            "normal", "karras", "exponential", "sgm_uniform", "simple", "ddim_uniform"
        )
        
        // 提供一些常见的模型名称示例
        val defaultCheckpoints = listOf(
            "model.safetensors",
            "checkpoint.ckpt",
            "base_model.safetensors"
        )
        
        // 提供一些常见的VAE
        val defaultVAE = listOf(
            "vae-ft-mse-840000-ema-pruned.safetensors",
            "sdxl_vae.safetensors"
        )
        
        return ComfyUIResources(
            samplers = defaultSamplers,
            schedulers = defaultSchedulers,
            models = com.example.myapplication.data.model.ComfyUIModels(
                checkpoints = defaultCheckpoints,
                vae = defaultVAE,
                loras = emptyList<String>(),
                embeddings = emptyList<String>(),
                controlnet = emptyList<String>(),
                upscale_models = emptyList<String>()
            )
        )
    }
}
