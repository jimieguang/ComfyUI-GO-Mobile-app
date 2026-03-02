package com.example.myapplication.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.ConnectionState
import com.example.myapplication.data.repository.ComfyUIRepository
import com.example.myapplication.domain.usecase.ConnectToServerUseCase
import com.example.myapplication.domain.usecase.FreeResourcesUseCase
import com.example.myapplication.util.isValidUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.myapplication.data.model.SystemStatsSimple
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 服务器连接管理ViewModel
 * 负责管理ComfyUI服务器连接状态和配置
 */
@HiltViewModel
class ServerConnectionViewModel @Inject constructor(
    private val connectToServerUseCase: ConnectToServerUseCase,
    private val freeResourcesUseCase: FreeResourcesUseCase,
    private val repository: ComfyUIRepository
) : ViewModel() {
    
    // UI状态数据类
    data class ServerConnectionUiState(
        val serverUrl: String = "",
        val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
        val errorMessage: String = "",
        val statusMessage: String = "",
        val isConnecting: Boolean = false,
        val showUrlError: Boolean = false,
        val urlErrorMessage: String = "",
        // 系统资源监控
        val systemStats: SystemStatsSimple? = null,
        val isSystemStatsAvailable: Boolean = false,
        val isSystemStatsLoading: Boolean = false
    )
    
    // 内部可变状态
    private val _uiState = MutableStateFlow(ServerConnectionUiState())
    
    // 对外暴露的只读状态
    val uiState: StateFlow<ServerConnectionUiState> = _uiState.asStateFlow()
    
    // 系统资源监控轮询 Job
    private var systemStatsPollingJob: Job? = null
    private val SYSTEM_STATS_POLLING_INTERVAL_MS = 100L  // 0.1秒轮询一次（绝对实时）
    
    init {
        // 启动时加载保存的服务器URL
        loadSavedServerUrl()
    }
    
    /**
     * 加载保存的服务器URL
     */
    private fun loadSavedServerUrl() {
        viewModelScope.launch {
            repository.getServerUrlFlow().collect { savedUrl ->
                _uiState.update { currentState ->
                    currentState.copy(serverUrl = savedUrl)
                }
            }
        }
    }
    
    /**
     * 更新服务器URL
     */
    fun updateServerUrl(url: String) {
        _uiState.update { currentState ->
            currentState.copy(
                serverUrl = url,
                showUrlError = false,
                urlErrorMessage = "",
                errorMessage = "",
                statusMessage = ""
            )
        }
    }
    
    /**
     * 验证URL格式
     */
    private fun validateUrl(url: String): Boolean {
        if (url.isBlank()) {
            _uiState.update { currentState ->
                currentState.copy(
                    showUrlError = true,
                    urlErrorMessage = "请输入服务器URL"
                )
            }
            return false
        }
        
        if (!url.isValidUrl()) {
            _uiState.update { currentState ->
                currentState.copy(
                    showUrlError = true,
                    urlErrorMessage = "URL格式不正确，请使用 http://ip:port 格式"
                )
            }
            return false
        }
        
        return true
    }
    
    /**
     * 连接到服务器
     */
    fun connectToServer() {
        val currentUrl = _uiState.value.serverUrl.trim()
        
        // 验证URL格式
        if (!validateUrl(currentUrl)) {
            return
        }
        
        viewModelScope.launch {
            connectToServerUseCase(currentUrl)
                .catch { exception ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            connectionState = ConnectionState.ERROR,
                            errorMessage = "连接失败: ${exception.message}",
                            isConnecting = false
                        )
                    }
                }
                .collect { result ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            connectionState = result.state,
                            isConnecting = result.state == ConnectionState.CONNECTING,
                            errorMessage = if (result.state == ConnectionState.CONNECTED) "" else currentState.errorMessage
                        )
                    }
                    // 连接成功时自动开始系统资源监控
                    if (result.state == ConnectionState.CONNECTED) {
                        startSystemStatsPolling()
                    }
                }
        }
    }
    
    /**
     * 断开服务器连接
     */
    fun disconnectFromServer() {
        viewModelScope.launch {
            try {
                // 停止系统资源监控
                stopSystemStatsPolling()
                
                repository.disconnect()
                _uiState.update { currentState ->
                    currentState.copy(
                        connectionState = ConnectionState.DISCONNECTED,
                        errorMessage = "",
                        isConnecting = false,
                        systemStats = null,
                        isSystemStatsAvailable = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        errorMessage = "断开连接失败: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { currentState ->
            currentState.copy(
                errorMessage = "",
                statusMessage = "",
                showUrlError = false,
                urlErrorMessage = ""
            )
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = "") }
    }

    /**
     * 释放内存/缓存（ComfyUI: POST /free { free_memory: true }）
     */
    fun freeMemory() {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = "", statusMessage = "清理内存中…") }
            val r = freeResourcesUseCase(unloadModels = false, freeMemory = true)
            _uiState.update { currentState ->
                if (r.isSuccess) {
                    currentState.copy(statusMessage = "已清理内存", errorMessage = "")
                } else {
                    currentState.copy(
                        statusMessage = "",
                        errorMessage = "释放内存失败: ${r.exceptionOrNull()?.message ?: "未知错误"}"
                    )
                }
            }
        }
    }

    /**
     * 卸载模型（ComfyUI: POST /free { unload_models: true }）
     */
    fun unloadModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = "", statusMessage = "清理显存中…") }
            val r = freeResourcesUseCase(unloadModels = true, freeMemory = false)
            _uiState.update { currentState ->
                if (r.isSuccess) {
                    currentState.copy(statusMessage = "已清理显存", errorMessage = "")
                } else {
                    currentState.copy(
                        statusMessage = "",
                        errorMessage = "卸载模型失败: ${r.exceptionOrNull()?.message ?: "未知错误"}"
                    )
                }
            }
        }
    }
    
    /**
     * 测试连接（不保存状态）
     */
    fun testConnection() {
        val currentUrl = _uiState.value.serverUrl.trim()
        
        if (!validateUrl(currentUrl)) {
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, errorMessage = "") }
            
            try {
                val result = repository.testConnection(currentUrl)
                result.fold(
                    onSuccess = {
                        _uiState.update { currentState ->
                            currentState.copy(
                                isConnecting = false,
                                errorMessage = ""
                            )
                        }
                    },
                    onFailure = { exception ->
                        _uiState.update { currentState ->
                            currentState.copy(
                                isConnecting = false,
                                errorMessage = "连接测试失败: ${exception.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isConnecting = false,
                        errorMessage = "连接测试失败: ${e.message}"
                    )
                }
            }
        }
    }
    
    // ============== 系统资源监控 ==============
    
    /**
     * 开始轮询系统资源
     */
    fun startSystemStatsPolling() {
        // 如果已经在轮询，不重复启动
        if (systemStatsPollingJob?.isActive == true) return
        
        systemStatsPollingJob = viewModelScope.launch {
            _uiState.update { it.copy(isSystemStatsLoading = true) }
            
            while (isActive) {
                val serverUrl = _uiState.value.serverUrl.trim()
                if (serverUrl.isNotBlank() && _uiState.value.connectionState == ConnectionState.CONNECTED) {
                    try {
                        val result = repository.getSystemStatsSimple(serverUrl)
                        result.fold(
                            onSuccess = { stats ->
                                _uiState.update { currentState ->
                                    currentState.copy(
                                        systemStats = stats,
                                        isSystemStatsAvailable = true,
                                        isSystemStatsLoading = false
                                    )
                                }
                            },
                            onFailure = {
                                // 插件未安装或请求失败，标记为不可用
                                _uiState.update { currentState ->
                                    currentState.copy(
                                        systemStats = null,
                                        isSystemStatsAvailable = false,
                                        isSystemStatsLoading = false
                                    )
                                }
                            }
                        )
                    } catch (e: Exception) {
                        _uiState.update { currentState ->
                            currentState.copy(
                                systemStats = null,
                                isSystemStatsAvailable = false,
                                isSystemStatsLoading = false
                            )
                        }
                    }
                }
                delay(SYSTEM_STATS_POLLING_INTERVAL_MS)
            }
        }
    }
    
    /**
     * 停止轮询系统资源
     */
    fun stopSystemStatsPolling() {
        systemStatsPollingJob?.cancel()
        systemStatsPollingJob = null
        _uiState.update { it.copy(systemStats = null, isSystemStatsLoading = false) }
    }
    
    /**
     * 手动刷新一次系统资源
     */
    fun refreshSystemStats() {
        viewModelScope.launch {
            val serverUrl = _uiState.value.serverUrl.trim()
            if (serverUrl.isBlank()) return@launch
            
            _uiState.update { it.copy(isSystemStatsLoading = true) }
            
            try {
                val result = repository.getSystemStatsSimple(serverUrl)
                result.fold(
                    onSuccess = { stats ->
                        _uiState.update { currentState ->
                            currentState.copy(
                                systemStats = stats,
                                isSystemStatsAvailable = true,
                                isSystemStatsLoading = false
                            )
                        }
                    },
                    onFailure = {
                        _uiState.update { currentState ->
                            currentState.copy(
                                systemStats = null,
                                isSystemStatsAvailable = false,
                                isSystemStatsLoading = false
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        systemStats = null,
                        isSystemStatsAvailable = false,
                        isSystemStatsLoading = false
                    )
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopSystemStatsPolling()
    }
}
