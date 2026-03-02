package com.example.myapplication.domain.usecase

import android.util.Log
import com.example.myapplication.data.model.ConnectionState
import com.example.myapplication.data.network.NetworkStateManager
import com.example.myapplication.data.repository.ComfyUIRepository
import com.example.myapplication.util.isValidUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * 连接到 ComfyUI 服务器的用例
 */
class ConnectToServerUseCase @Inject constructor(
    private val repository: ComfyUIRepository,
    private val networkStateManager: NetworkStateManager
) {
    
    companion object {
        private const val TAG = "ConnectToServerUseCase"
        private const val CONNECTION_TIMEOUT_MS = 10000L
    }
    
    /**
     * 连接结果数据类
     */
    data class ConnectionResult(
        val state: ConnectionState,
        val error: String? = null,
        val nodeCount: Int = 0
    )
    
    operator fun invoke(serverUrl: String): Flow<ConnectionResult> = flow {
        Log.d(TAG, "开始连接到服务器: $serverUrl")
        
        emit(ConnectionResult(ConnectionState.CONNECTING))
        
        try {
            // 1. 验证URL格式
            if (!serverUrl.isValidUrl()) {
                Log.e(TAG, "无效的服务器URL: $serverUrl")
                emit(ConnectionResult(
                    state = ConnectionState.ERROR,
                    error = "无效的服务器URL格式"
                ))
                return@flow
            }
            
            // 2. 检查网络连接
            if (!networkStateManager.isNetworkConnected()) {
                Log.e(TAG, "设备无网络连接")
                emit(ConnectionResult(
                    state = ConnectionState.ERROR,
                    error = "设备无网络连接，请检查网络设置"
                ))
                return@flow
            }
            
            // 3. 保存服务器URL并清空缓存（防止服务器变更后旧缓存残留）
            repository.saveServerUrl(serverUrl)
            repository.clearObjectInfoCache()
            
            // 4. 测试基础连接
            val connectionResult = repository.testConnection(serverUrl)
            
            if (connectionResult.isFailure) {
                val error = connectionResult.exceptionOrNull()?.message ?: "连接失败"
                Log.e(TAG, "连接测试失败: $error")
                emit(ConnectionResult(
                    state = ConnectionState.ERROR,
                    error = "无法连接到服务器: $error"
                ))
                return@flow
            }
            
            Log.d(TAG, "基础连接测试成功")
            
            // 5. 获取节点信息（只获取一次并缓存）
            var nodeCount = 0
            try {
                val objectInfoResult = repository.getObjectInfo(serverUrl)
                if (objectInfoResult.isSuccess) {
                    nodeCount = objectInfoResult.getOrNull()?.size ?: 0
                    Log.d(TAG, "获取到 $nodeCount 个节点类型")
                }
            } catch (e: Exception) {
                Log.w(TAG, "获取节点信息失败，但连接仍然有效: ${e.message}")
            }
            
            // 6. 连接成功（不做全局选项刷新，按网页逻辑在解析/渲染时就地使用各参数的options）
            Log.d(TAG, "服务器连接成功")
            emit(ConnectionResult(
                state = ConnectionState.CONNECTED,
                nodeCount = nodeCount
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "连接过程中发生异常", e)
            emit(ConnectionResult(
                state = ConnectionState.ERROR,
                error = "连接异常: ${e.message}"
            ))
        }
    }
    
    /**
     * 测试WebSocket连接
     */
    suspend fun testWebSocketConnection(serverUrl: String): Boolean {
        return try {
            Log.d(TAG, "测试WebSocket连接")
            val clientId = repository.getClientId()
            
            // 这里可以实现WebSocket连接测试逻辑
            // 暂时返回true，实际项目中应该实现真正的测试
            true
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket连接测试失败", e)
            false
        }
    }
    
    /**
     * 断开连接
     */
    suspend fun disconnect() {
        Log.d(TAG, "断开服务器连接")
        repository.disconnectWebSocket()
    }
}
