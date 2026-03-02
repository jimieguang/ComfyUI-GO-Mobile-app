package com.example.myapplication.data.websocket

import android.util.Log
import com.example.myapplication.data.model.WebSocketMessage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.*
import okio.ByteString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.example.myapplication.data.model.PreviewFrame

/**
 * ComfyUI WebSocket 连接管理器
 * 负责建立、维护和管理与 ComfyUI 服务器的 WebSocket 连接
 */
@Singleton
class ComfyWebSocketManager @Inject constructor(
    private val json: Json
) {
    
    companion object {
        private const val TAG = "ComfyWebSocketManager"
        private const val NORMAL_CLOSURE_STATUS = 1000
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val RECONNECT_DELAY_MS = 3000L
        private const val PING_INTERVAL_SECONDS = 15L
        private const val VERBOSE_WS_LOG = false
    }
    
    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    private var shouldReconnect = true
    private val reconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var keepAliveJob: kotlinx.coroutines.Job? = null
    
    // 全局共享的消息流，所有订阅者都可以接收到消息
    private val _messageFlow = MutableSharedFlow<WebSocketMessage>(
        replay = 0,
        extraBufferCapacity = 100
    )
    private val messageFlow: SharedFlow<WebSocketMessage> = _messageFlow.asSharedFlow()

    /**
     * 二进制预览帧流（ComfyUI preview image）
     * - replay=1：UI/订阅者可直接拿到最近一帧
     */
    private val _previewFlow = MutableSharedFlow<PreviewFrame>(
        replay = 1,
        extraBufferCapacity = 2
    )
    private val previewFlow: SharedFlow<PreviewFrame> = _previewFlow.asSharedFlow()
    
    // 当前连接的 URL 和 clientId
    private var currentServerUrl: String? = null
    private var currentClientId: String? = null
    
    /**
     * 连接到 WebSocket 服务器（如果已连接到相同地址，则复用现有连接）
     */
    fun connect(serverUrl: String, clientId: String): Flow<WebSocketMessage> {
        // 如果已经连接到相同的服务器，直接返回共享流
        if (isConnected && currentServerUrl == serverUrl && currentClientId == clientId) {
            Log.d(TAG, "复用现有 WebSocket 连接")
            return messageFlow
        }
        
        // 需要建立新连接
        Log.d(TAG, "建立新的 WebSocket 连接: $serverUrl")
        currentServerUrl = serverUrl
        currentClientId = clientId
        
        // 启动连接（在后台协程中）
        reconnectScope.launch {
            ensureConnection(serverUrl, clientId)
        }
        
        return messageFlow
    }

    /**
     * 订阅 ComfyUI 二进制预览帧（preview image）
     */
    fun previewFrames(): Flow<PreviewFrame> = previewFlow
    
    /**
     * 确保 WebSocket 连接已建立
     */
    private suspend fun ensureConnection(serverUrl: String, clientId: String) {
        // 如果已连接，不重复连接
        if (isConnected && webSocket != null) {
            Log.d(TAG, "WebSocket 已连接，无需重新连接")
            return
        }
        
        try {
            // 每次新的连接都重置重连标志
            shouldReconnect = true
            reconnectAttempts = 0
            
            // 关闭旧的 WebSocket 连接（如果存在）
            webSocket?.let { oldWs ->
                Log.d(TAG, "关闭旧的 WebSocket 连接以建立新连接")
                stopAppLevelKeepAlive()
                try {
                    oldWs.close(NORMAL_CLOSURE_STATUS, "建立新连接")
                } catch (e: Exception) {
                    Log.w(TAG, "关闭旧连接失败", e)
                }
                webSocket = null
            }
            
            // 构建 WebSocket URL
            val url = URL(serverUrl)
            val protocol = if (url.protocol == "https") "wss" else "ws"
            val hostPort = if (url.port == -1) url.host else "${url.host}:${url.port}"
            val wsUrl = "$protocol://$hostPort/ws?clientId=$clientId"
            
            Log.d(TAG, "连接 WebSocket: $wsUrl")
            
            // 创建 OkHttp 客户端（WebSocket 推荐：readTimeout(0) + 重试 + ping）
            client = OkHttpClient.Builder()
                .connectTimeout(45, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(45, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build()
            
            // 创建 WebSocket 请求
            val request = Request.Builder()
                .url(wsUrl)
                .build()
            
            // 创建 WebSocket 监听器
            val webSocketListener = object : WebSocketListener() {
                
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket 连接已建立")
                    isConnected = true
                    reconnectAttempts = 0
                    
                    // 发送状态请求
                    sendMessage(mapOf("type" to "status"))
                    
                    // 启动心跳
                    startHeartbeat(webSocket)
                    startAppLevelKeepAlive()
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (VERBOSE_WS_LOG) {
                        Log.d(TAG, "收到 WebSocket 消息(长度=${text.length})")
                    } else {
                        // 降低日志与解析负载：不打印整段JSON
                        Log.d(TAG, "收到 WebSocket 消息")
                    }
                    // 将解析移至后台线程，避免阻塞OkHttp回调线程
                    reconnectScope.launch(Dispatchers.Default) {
                        try {
                            val message = json.decodeFromString<WebSocketMessage>(text)
                            _messageFlow.emit(message)
                        } catch (e: Exception) {
                            Log.e(TAG, "解析 WebSocket 消息失败", e)
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    // ComfyUI 会通过二进制帧发送预览图（采样过程预览）
                    // 格式（服务端 server.py）:
                    // - 前 4 字节：event(int, big-endian)
                    // - event=1 PREVIEW_IMAGE：payload 前 4 字节 type_num(1=jpeg,2=png)，剩余为 image bytes
                    // - event=4 PREVIEW_IMAGE_WITH_METADATA：payload 前 4 字节 metadata_len，随后 metadata_json，再随后 image bytes
                    reconnectScope.launch(Dispatchers.Default) {
                        try {
                            val data = bytes.toByteArray()
                            if (data.size < 4) return@launch
                            val event = readIntBE(data, 0)
                            when (event) {
                                1 -> { // PREVIEW_IMAGE
                                    if (data.size < 8) return@launch
                                    val typeNum = readIntBE(data, 4)
                                    val mime = when (typeNum) {
                                        1 -> "image/jpeg"
                                        2 -> "image/png"
                                        else -> "image/*"
                                    }
                                    val imgBytes = data.copyOfRange(8, data.size)
                                    if (imgBytes.isNotEmpty()) {
                                        _previewFlow.emit(PreviewFrame(imgBytes, mime))
                                    }
                                }
                                4 -> { // PREVIEW_IMAGE_WITH_METADATA
                                    if (data.size < 8) return@launch
                                    val metaLen = readIntBE(data, 4).coerceAtLeast(0)
                                    val metaStart = 8
                                    val metaEnd = metaStart + metaLen
                                    if (metaEnd > data.size) return@launch
                                    val metaJsonBytes = data.copyOfRange(metaStart, metaEnd)
                                    val imgBytes = data.copyOfRange(metaEnd, data.size)
                                    val mime = runCatching {
                                        val metaStr = String(metaJsonBytes, Charsets.UTF_8)
                                        val meta = json.parseToJsonElement(metaStr).jsonObject
                                        meta["image_type"]?.jsonPrimitive?.contentOrNull
                                    }.getOrNull() ?: "image/*"
                                    if (imgBytes.isNotEmpty()) {
                                        _previewFlow.emit(PreviewFrame(imgBytes, mime))
                                    }
                                }
                                else -> {
                                    // 其他二进制事件类型（例如 TEXT/其他预览类型）暂不处理
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析 WebSocket 二进制预览帧失败", e)
                        }
                    }
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket 连接正在关闭: $code - $reason")
                    webSocket.close(NORMAL_CLOSURE_STATUS, null)
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket 连接已关闭: $code - $reason")
                    isConnected = false
                    stopAppLevelKeepAlive()
                    
                    // 如果需要重连且未达到最大重连次数
                    if (shouldReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS && currentServerUrl != null && currentClientId != null) {
                        scheduleReconnect(currentServerUrl!!, currentClientId!!)
                    }
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket 连接失败", t)
                    isConnected = false
                    stopAppLevelKeepAlive()
                    
                    // 尝试重连
                    if (shouldReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS && currentServerUrl != null && currentClientId != null) {
                        scheduleReconnect(currentServerUrl!!, currentClientId!!)
                    }
                    
                    // 发送错误消息到共享流
                    reconnectScope.launch {
                        _messageFlow.emit(WebSocketMessage("error", kotlinx.serialization.json.JsonNull))
                    }
                }
            }
            
            // 建立新连接
            webSocket = client?.newWebSocket(request, webSocketListener)
            
        } catch (e: Exception) {
            Log.e(TAG, "创建 WebSocket 连接失败", e)
            reconnectScope.launch {
                _messageFlow.emit(WebSocketMessage("error", null))
            }
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(NORMAL_CLOSURE_STATUS, "手动断开连接")
        webSocket = null
        client = null
        isConnected = false
        reconnectAttempts = 0
        stopAppLevelKeepAlive()
    }
    
    /**
     * 发送消息
     */
    fun sendMessage(message: Map<String, Any>) {
        if (isConnected && webSocket != null) {
            try {
                // 简单的JSON构建，WebSocket消息通常较简单
                val jsonMessage = buildString {
                    append("{")
                    message.entries.forEachIndexed { index, (key, value) ->
                        if (index > 0) append(",")
                        append("\"$key\":")
                        when (value) {
                            is String -> append("\"$value\"")
                            is Number -> append(value)
                            is Boolean -> append(value)
                            else -> append("\"$value\"")
                        }
                    }
                    append("}")
                }
                webSocket?.send(jsonMessage)
                Log.d(TAG, "发送 WebSocket 消息: $jsonMessage")
            } catch (e: Exception) {
                Log.e(TAG, "发送 WebSocket 消息失败", e)
            }
        } else {
            Log.w(TAG, "WebSocket 未连接，无法发送消息")
        }
    }
    
    /**
     * 检查连接状态
     */
    fun isConnected(): Boolean = isConnected
    
    /**
     * 安排重连
     */
    private fun scheduleReconnect(serverUrl: String, clientId: String) {
        if (!shouldReconnect) return
        
        reconnectAttempts++
        val base = RECONNECT_DELAY_MS
        val exp = reconnectAttempts.coerceAtMost(10)
        val delay = (base * exp).coerceAtMost(30000L) // 最大30秒
        
        Log.d(TAG, "将在 ${delay}ms 后尝试第 $reconnectAttempts 次重连")
        
        reconnectScope.launch {
            try {
                delay(delay)
                if (shouldReconnect && !isConnected) {
                    ensureConnection(serverUrl, clientId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "重连调度失败", e)
            }
        }
    }
    
    
    /**
     * 启动心跳检测 (OkHttp自动处理)
     */
    private fun startHeartbeat(webSocket: WebSocket) {
        Log.d(TAG, "WebSocket心跳已启用")
    }

    private fun startAppLevelKeepAlive() {
        stopAppLevelKeepAlive()
        keepAliveJob = reconnectScope.launch {
            while (isConnected) {
                try {
                    sendMessage(mapOf("type" to "status"))
                } catch (_: Exception) {
                }
                delay(PING_INTERVAL_SECONDS * 1000L)
            }
        }
    }

    private fun stopAppLevelKeepAlive() {
        try {
            keepAliveJob?.cancel()
        } catch (_: Exception) {}
        keepAliveJob = null
    }
    
    /**
     * 获取连接统计信息
     */
    fun getConnectionStats(): Map<String, Any> {
        return mapOf(
            "isConnected" to isConnected,
            "reconnectAttempts" to reconnectAttempts,
            "shouldReconnect" to shouldReconnect
        )
    }

    private fun readIntBE(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }
}
