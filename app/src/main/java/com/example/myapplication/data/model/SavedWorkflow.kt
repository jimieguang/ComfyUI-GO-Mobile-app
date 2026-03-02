package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 已保存的工作流
 */
@Serializable
data class SavedWorkflow(
    val id: String,
    val name: String,
    val json: JsonObject,
    val timestamp: Long,
    val source: String? = null,
    val quickParams: Map<String, List<String>>? = null  // 快速参数配置（节点ID -> 参数ID列表）
)


