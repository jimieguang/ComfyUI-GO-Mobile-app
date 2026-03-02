package com.example.myapplication.data.model

/**
 * ComfyUI WebSocket 二进制预览帧（preview image）
 *
 * 来源：ComfyUI PromptServer 通过 WebSocket 发送的二进制消息。
 * 参考服务端实现：ComfyUI `server.py` + `protocol.py`
 */
data class PreviewFrame(
    /** 图片字节（PNG/JPEG 等编码后的原始 bytes，不包含 ComfyUI 的二进制包头） */
    val imageBytes: ByteArray,
    /** MIME 类型（例如 image/png, image/jpeg） */
    val mimeType: String,
    /** 接收时间戳 */
    val receivedAtMs: Long = System.currentTimeMillis()
)


