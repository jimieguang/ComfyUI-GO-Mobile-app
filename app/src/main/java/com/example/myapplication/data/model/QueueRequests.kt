package com.example.myapplication.data.model

import kotlinx.serialization.Serializable

/**
 * 请求体：清空队列（兼容服务端：{"clear": true}）
 */
@Serializable
data class QueueClearRequest(
    val clear: Boolean
)

/**
 * 请求体：删除指定队列项（官方标准：{"delete": ["id1", "id2"]}）
 */
@Serializable
data class QueueDeleteRequest(
    val delete: List<String>
)


