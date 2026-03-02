package com.example.myapplication.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.OperationHistory
import com.example.myapplication.data.model.TextOperation
import com.example.myapplication.data.repository.LLMRepository
import com.example.myapplication.util.LLMPreferences
import com.example.myapplication.util.PromptTemplates
import com.example.myapplication.data.model.TextOperationType
import com.example.myapplication.data.model.CustomRule
import com.example.myapplication.util.LLMProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * LLM助手ViewModel
 * 管理AI文本处理的状态和业务逻辑
 */
@HiltViewModel
class LLMAssistantViewModel @Inject constructor(
    private val repository: LLMRepository,
    private val preferences: LLMPreferences
) : ViewModel() {
    
    companion object {
        private const val TAG = "LLMAssistantVM"
    }
    
    /**
     * UI状态
     */
    data class UiState(
        val isLoading: Boolean = false,
        val currentOperation: TextOperation? = null,
        val originalText: String = "",
        val resultText: String = "",
        val errorMessage: String? = null,
        val showResultDialog: Boolean = false,
        val tokenUsage: String? = null,
        val history: List<OperationHistory> = emptyList(),
        // ✅ 结果归属：为空表示来自全屏编辑器；非空表示归属于某个参数ID
        val targetParamId: String? = null,
        // ✅ 最后一次操作记录（用于撤回）
        val lastOperation: OperationHistory? = null
    )
    
    // 内部可变状态
    private val _uiState = MutableStateFlow(UiState())
    
    // 对外只读状态
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // 当前选择的服务商
    private val _currentProvider = preferences.getProvider()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LLMProvider.ALIYUN)

    // 完整的LLM配置状态
    val llmConfig: StateFlow<LLMPreferences.LLMConfig> = preferences.getConfig()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LLMPreferences.LLMConfig(
                provider = LLMProvider.ALIYUN,
                apiKey = "",
                modelName = LLMPreferences.DEFAULT_ALIYUN_MODEL,
                temperature = LLMPreferences.DEFAULT_TEMPERATURE,
                maxTokens = LLMPreferences.DEFAULT_MAX_TOKENS,
                baseUrl = LLMPreferences.DEFAULT_ALIYUN_BASE_URL
            )
        )

    // API Key是否已配置（从llmConfig派生）
    val hasApiKey: StateFlow<Boolean> = llmConfig
        .map { it.apiKey.isNotBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    // 预设管理公开流
    val disabledPresetTypes: StateFlow<Set<TextOperationType>> = preferences.getDisabledPresetTypes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val customRules: StateFlow<List<CustomRule>> = preferences.getCustomRules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * 最终可用操作：内置(过滤禁用) + 自定义
     */
    val operations: StateFlow<List<TextOperation>> = combine(
        preferences.getDisabledPresetTypes(),
        preferences.getCustomRules()
    ) { disabled, customRules ->
        val builtins = PromptTemplates.getAllOperations().filterNot { op ->
            val isTranslate = op.type == TextOperationType.TRANSLATE_ZH_TO_EN || op.type == TextOperationType.TRANSLATE_EN_TO_ZH
            !isTranslate && disabled.contains(op.type)
        }
        val customs = customRules
            .filter { it.isEnabled }  // 只包含启用的自定义预设
            .map { rule ->
                TextOperation(
                    type = TextOperationType.CUSTOM,
                    displayName = rule.name,
                    icon = rule.icon,
                    systemPrompt = rule.systemPrompt,
                    userPromptTemplate = rule.userPromptTemplate,
                    description = "自定义"
                )
            }
        builtins + customs
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前正在执行的AI任务（用于切换工作流时取消，避免旧结果串到新工作流）
    private var currentOperationJob: Job? = null

    fun setDisabledPresetTypes(types: Set<TextOperationType>) {
        viewModelScope.launch { preferences.setDisabledPresetTypes(types) }
    }
    fun addCustomRule(rule: CustomRule) {
        viewModelScope.launch { preferences.addCustomRule(rule) }
    }
    fun deleteCustomRule(id: String) {
        viewModelScope.launch { preferences.deleteCustomRule(id) }
    }
    fun updateCustomRule(rule: CustomRule) {
        viewModelScope.launch { preferences.updateCustomRule(rule) }
    }
    fun toggleCustomRuleEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val currentRules = preferences.getCustomRules().first()
            val updatedRule = currentRules.find { it.id == id }?.copy(isEnabled = enabled)
            if (updatedRule != null) {
                preferences.updateCustomRule(updatedRule)
            }
        }
    }
    
    /**
     * 执行文本操作
     */
    fun executeOperation(operation: TextOperation, inputText: String, targetParamId: String? = null) {
        // 取消上一条任务（避免并发导致状态覆盖/串号）
        currentOperationJob?.cancel()
        currentOperationJob = viewModelScope.launch {
            try {
                Log.d(TAG, "执行操作: ${operation.displayName} using ${llmConfig.value.provider}")
                
                // 更新状态为加载中
                _uiState.update { state ->
                    state.copy(
                        isLoading = true,
                        currentOperation = operation,
                        originalText = inputText,
                        errorMessage = null,
                        resultText = "",
                        tokenUsage = null,
                        targetParamId = targetParamId
                    )
                }
                
                // 调用Repository处理文本
                val result = repository.processText(operation, inputText)
                
                result.fold(
                    onSuccess = { processResult ->
                        Log.d(TAG, "操作成功: ${operation.displayName}")
                        
                        // 添加到历史记录
                        val newHistory = OperationHistory(
                            operation = operation,
                            originalText = inputText,
                            resultText = processResult.outputText
                        )
                        
                        // 更新状态 - 直接替换模式（不显示结果对话框）
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                resultText = processResult.outputText,
                                showResultDialog = false,  // 不显示对话框
                                tokenUsage = processResult.usage?.totalTokens?.let { "使用 $it tokens" } ?: "",
                                history = listOf(newHistory) + state.history.take(9), // 保留最近10条
                                lastOperation = newHistory  // 保存最后一次操作用于撤回
                            )
                        }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "操作失败: ${operation.displayName}", exception)
                        
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                errorMessage = exception.message ?: "操作失败"
                            )
                        }
                    }
                )
                
            } catch (e: CancellationException) {
                // 任务被取消（例如切换工作流/重新触发操作），不提示错误
                Log.d(TAG, "AI任务已取消: ${operation.displayName}")
            } catch (e: Exception) {
                Log.e(TAG, "执行操作异常", e)
                
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "未知错误"
                    )
                }
            }
        }
    }

    /**
     * 切换/重新加载工作流时重置AI状态：
     * - 取消正在进行的AI任务
     * - 清空上一次结果/撤回记录/目标参数，避免旧结果回填到新工作流
     */
    fun resetForWorkflowReload() {
        currentOperationJob?.cancel()
        currentOperationJob = null
        _uiState.value = UiState()
    }
    
    /**
     * 撤回最后一次操作
     */
    fun undoLastOperation(): String? {
        val lastOp = _uiState.value.lastOperation
        if (lastOp != null) {
            _uiState.update { state ->
                state.copy(
                    lastOperation = null,  // 清除最后操作记录
                    resultText = lastOp.originalText  // 返回原始文本
                )
            }
            return lastOp.originalText
        }
        return null
    }
    
    /**
     * 清除撤回记录
     */
    fun clearUndoHistory() {
        _uiState.update { state ->
            state.copy(lastOperation = null)
        }
    }
    
    /**
     * 取消当前操作
     */
    fun cancelOperation() {
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                currentOperation = null,
                errorMessage = null
            )
        }
    }
    
    /**
     * 关闭结果对话框
     */
    fun dismissResultDialog() {
        _uiState.update { state ->
            state.copy(
                showResultDialog = false,
                targetParamId = null
            )
        }
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { state ->
            state.copy(errorMessage = null)
        }
    }
    
    /**
     * 撤销到上一次操作
     */
    fun undo(): String? {
        val history = _uiState.value.history
        return if (history.isNotEmpty()) {
            val lastHistory = history.first()
            _uiState.update { state ->
                state.copy(history = state.history.drop(1))
            }
            lastHistory.originalText
        } else {
            null
        }
    }
    
    /**
     * 测试API连接
     */
    fun testConnection(provider: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                val result = repository.testConnection(provider)
                
                result.fold(
                    onSuccess = { message ->
                        _uiState.update { it.copy(isLoading = false) }
                        onResult(true, message)
                    },
                    onFailure = { exception ->
                        _uiState.update { it.copy(isLoading = false) }
                        onResult(false, exception.message ?: "连接失败")
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                onResult(false, e.message ?: "连接异常")
            }
        }
    }
    
    /**
     * 保存服务商
     */
    fun saveProvider(provider: String) {
        viewModelScope.launch {
            preferences.saveProvider(provider)
        }
    }

    /**
     * 保存API Key（根据服务商）
     */
    fun saveApiKey(apiKey: String, provider: String) {
        viewModelScope.launch {
            preferences.saveApiKey(apiKey, provider)
        }
    }
    
    /**
     * 保存模型配置（根据服务商）
     */
    fun saveModelConfig(
        modelName: String,
        temperature: Float,
        maxTokens: Int,
        provider: String
    ) {
        viewModelScope.launch {
            preferences.saveModelConfig(modelName, temperature, maxTokens, provider)
        }
    }
    
    /**
     * 获取可用模型列表
     * @param provider 服务商
     * @param onResult 回调函数 (成功?, 模型列表或错误信息)
     */
    fun fetchAvailableModels(
        provider: String,
        onResult: (Boolean, List<Pair<String, String>>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "开始获取模型列表: $provider")
                
                val result = repository.getAvailableModels(provider)
                
                result.fold(
                    onSuccess = { models ->
                        Log.d(TAG, "成功获取 ${models.size} 个模型")
                        onResult(true, models)
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "获取模型列表失败", exception)
                        // 失败时返回空列表，UI将使用硬编码的默认列表
                        onResult(false, emptyList())
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "获取模型列表异常", e)
                onResult(false, emptyList())
            }
        }
    }
}

