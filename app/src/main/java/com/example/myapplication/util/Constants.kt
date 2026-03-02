package com.example.myapplication.util

/**
 * 应用程序常量
 */
object Constants {
    
    // 默认服务器配置
    const val DEFAULT_SERVER_URL = "http://127.0.0.1:8188"
    const val DEFAULT_CLIENT_ID_PREFIX = "comfyui_go_android_"
    
    // DataStore Keys
    const val KEY_SERVER_URL = "server_url"
    const val KEY_CLIENT_ID = "client_id"
    
    // WebSocket 消息类型
    object WebSocketMessageType {
        const val STATUS = "status"
        const val PROGRESS = "progress"
        const val EXECUTING = "executing"
        const val EXECUTED = "executed"
        const val EXECUTION_START = "execution_start"
        const val EXECUTION_ERROR = "execution_error"
        const val EXECUTION_CACHED = "execution_cached"
        // 官方消息类型（文档）：https://docs.comfy.org/zh-CN/development/comfyui-server/comms_messages
        const val EXECUTION_SUCCESS = "execution_success"
        const val EXECUTION_INTERRUPTED = "execution_interrupted"
    }
    
    // API 端点
    object ApiEndpoints {
        const val OBJECT_INFO = "/object_info"
        const val PROMPT = "/prompt"
        const val UPLOAD_IMAGE = "/upload/image"
        const val VIEW_IMAGE = "/view"
        const val HISTORY = "/history"
    }
    
    // 文件类型
    object FileTypes {
        const val JSON = "application/json"
        const val IMAGE_PNG = "image/png"
        const val IMAGE_JPEG = "image/jpeg"
        const val IMAGE_WEBP = "image/webp"
    }
    
    // 数据库
    object Database {
        const val NAME = "comfyui_go_database"
        const val VERSION = 1
    }
}
