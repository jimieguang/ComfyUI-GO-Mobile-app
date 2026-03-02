package com.example.myapplication.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.manager.WorkflowImportManager
import com.example.myapplication.presentation.util.LoadedState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/**
 * 工作流导入ViewModel
 * 管理工作流导入相关的UI状态和业务逻辑
 */
@HiltViewModel
class WorkflowImportViewModel @Inject constructor(
    private val workflowImportManager: WorkflowImportManager,
    private val repository: com.example.myapplication.data.repository.ComfyUIRepository
) : ViewModel() {
    
    /**
     * UI状态数据类
     */
    data class WorkflowImportUiState(
        val isImporting: Boolean = false,
        val hasWorkflow: Boolean = false,
        val currentWorkflow: JsonObject? = null,
        val workflowInfo: WorkflowImportManager.WorkflowInfo? = null,
        val importSource: WorkflowImportManager.ImportSource = WorkflowImportManager.ImportSource.UNKNOWN,
        val workflowName: String? = null,
        // 用于覆盖展示的来源文案（例如：执行历史 / 工作流存储）
        val displaySource: String? = null,
        val errorMessage: String = "",
        val showError: Boolean = false,
        val jsonInputText: String = "",
        // 剪贴板命名对话框相关
        val showClipboardNameDialog: Boolean = false,
        val pendingClipboardResult: WorkflowImportManager.ImportResult? = null,
        // ✅ 直接导入（文件/剪贴板/URL）后，通知生成器界面清空一次快速参数
        val clearQuickParamsOnce: Boolean = false
    )
    
    // 内部可变状态
    private val _uiState = MutableStateFlow(WorkflowImportUiState())
    
    // 对外暴露的只读状态
    val uiState: StateFlow<WorkflowImportUiState> = _uiState.asStateFlow()
    
    /**
     * 消费一次性清空快速参数标记（由生成器界面调用）
     */
    fun consumeClearQuickParamsOnce() {
        _uiState.update { state ->
            if (state.clearQuickParamsOnce) state.copy(clearQuickParamsOnce = false) else state
        }
    }
    
    /**
     * 从文件导入工作流
     */
    fun importFromFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, showError = false) }
            
            try {
                val result = workflowImportManager.importFromFile(uri)
                handleImportResult(result)
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isImporting = false,
                        showError = true,
                        errorMessage = "文件导入异常: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 从剪贴板导入工作流
     */
    fun importFromClipboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, showError = false) }
            
            try {
                val result = workflowImportManager.importFromClipboard()
                
                // 如果成功识别到工作流，显示命名对话框
                if (result.success && result.workflow != null) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            isImporting = false,
                            showClipboardNameDialog = true,
                            pendingClipboardResult = result
                        )
                    }
                } else {
                    // 失败则直接显示错误
                    _uiState.update { currentState ->
                        currentState.copy(
                            isImporting = false,
                            showError = true,
                            errorMessage = result.errorMessage
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isImporting = false,
                        showError = true,
                        errorMessage = "剪贴板导入异常: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 确认剪贴板工作流名称并完成导入
     */
    fun confirmClipboardWorkflowName(name: String) {
        val pendingResult = _uiState.value.pendingClipboardResult
        if (pendingResult != null && pendingResult.success && pendingResult.workflow != null) {
            // 使用用户输入的名称替换默认名称
            val updatedResult = pendingResult.copy(workflowName = name.trim().ifBlank { "未命名工作流" })
            
            // 关闭对话框并处理导入结果
            _uiState.update { it.copy(showClipboardNameDialog = false, pendingClipboardResult = null) }
            
            // 处理导入结果
            handleImportResult(updatedResult)
        }
    }
    
    /**
     * 取消剪贴板工作流导入
     */
    fun cancelClipboardWorkflowImport() {
        // 关闭对话框并丢弃待处理的工作流
        _uiState.update { it.copy(showClipboardNameDialog = false, pendingClipboardResult = null) }
    }
    
    /**
     * 从文本输入导入工作流
     */
    fun importFromText(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, showError = false) }
            
            try {
                val result = workflowImportManager.importFromText(text)
                handleImportResult(result)
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isImporting = false,
                        showError = true,
                        errorMessage = "文本导入异常: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 处理导入结果
     */
    private fun handleImportResult(result: WorkflowImportManager.ImportResult) {
        if (result.success && result.workflow != null) {
            val workflowInfo = workflowImportManager.getWorkflowInfo(result.workflow)
            
            _uiState.update { currentState ->
                // 判断是否为直接导入（文件/剪贴板/URL），这类导入需要清除历史/存储的名称和来源
                // 注意：TEXT_INPUT 可能来自历史/存储，所以需要检查是否已有displaySource
                val isDirectImport = when (result.source) {
                    WorkflowImportManager.ImportSource.FILE,
                    WorkflowImportManager.ImportSource.CLIPBOARD,
                    WorkflowImportManager.ImportSource.URL -> true
                    WorkflowImportManager.ImportSource.TEXT_INPUT -> {
                        // 如果是TEXT_INPUT但已有displaySource，说明来自历史/存储，不是直接导入
                        currentState.displaySource == null
                    }
                    WorkflowImportManager.ImportSource.UNKNOWN -> false
                }
                
                currentState.copy(
                    isImporting = false,
                    hasWorkflow = true,
                    currentWorkflow = result.workflow,
                    workflowInfo = workflowInfo,
                    importSource = result.source,
                    // 直接导入：使用新的导入名称，清除历史/存储来源
                    // 历史/存储导入：保留上层设置的名称和来源
                    workflowName = if (isDirectImport) result.workflowName else (currentState.workflowName ?: result.workflowName),
                    displaySource = if (isDirectImport) null else currentState.displaySource,
                    clearQuickParamsOnce = isDirectImport,  // ✅ 直接导入触发一次清空
                    showError = false,
                    errorMessage = ""
                )
            }
            // ✅ 直接导入：清除历史/存储的“已导入”标记，避免卡片继续显示“已导入”
            if (when (result.source) {
                    WorkflowImportManager.ImportSource.FILE,
                    WorkflowImportManager.ImportSource.CLIPBOARD,
                    WorkflowImportManager.ImportSource.URL -> true
                    WorkflowImportManager.ImportSource.TEXT_INPUT -> _uiState.value.displaySource == null
                    WorkflowImportManager.ImportSource.UNKNOWN -> false
                }
            ) {
                LoadedState.clear()
            }
            // 记录最近一次导入名称/来源，供执行历史入库时使用
            viewModelScope.launch {
                // 若来自历史/存储（已由上层写入displaySource与workflowName），优先使用这些覆盖值
                val current = _uiState.value
                val effectiveName = current.workflowName ?: result.workflowName
                val effectiveSource = when (current.displaySource) {
                    "执行历史" -> "HISTORY"
                    "工作流存储" -> "STORAGE"
                    else -> when (result.source) {
                        WorkflowImportManager.ImportSource.FILE -> "FILE"
                        WorkflowImportManager.ImportSource.CLIPBOARD -> "CLIPBOARD"
                        WorkflowImportManager.ImportSource.TEXT_INPUT -> "TEXT_INPUT"
                        WorkflowImportManager.ImportSource.URL -> "URL"
                        WorkflowImportManager.ImportSource.UNKNOWN -> null
                    }
                }
                repository.setLastImportedWorkflow(effectiveName, effectiveSource)
            }
        } else {
            _uiState.update { currentState ->
                currentState.copy(
                    isImporting = false,
                    showError = true,
                    errorMessage = result.errorMessage
                )
            }
        }
    }
    
    /**
     * 更新JSON输入文本
     */
    fun updateJsonInputText(text: String) {
        _uiState.update { it.copy(jsonInputText = text) }
    }
    
    /**
     * 从当前输入文本导入
     */
    fun importFromCurrentText() {
        val currentText = _uiState.value.jsonInputText.trim()
        if (currentText.isNotEmpty()) {
            importFromText(currentText)
        } else {
            _uiState.update { currentState ->
                currentState.copy(
                    showError = true,
                    errorMessage = "请输入JSON工作流内容"
                )
            }
        }
    }
    
    /**
     * 清除当前工作流
     */
    fun clearWorkflow() {
        _uiState.update { 
            WorkflowImportUiState(jsonInputText = it.jsonInputText)
        }
        // 同步清理“已导入”标记
        LoadedState.clear()
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { it.copy(showError = false, errorMessage = "") }
    }
    
    /**
     * 从历史/其他来源应用工作流名称，并记录到仓库供执行历史入库时使用
     */
    fun applyWorkflowNameFromHistory(name: String?) {
        _uiState.update { it.copy(workflowName = name, displaySource = "执行历史") }
        viewModelScope.launch {
            repository.setLastImportedWorkflow(name, "HISTORY")
        }
    }

    /**
     * 从工作流存储应用名称与来源
     */
    fun applyWorkflowNameFromStorage(name: String?) {
        _uiState.update { it.copy(workflowName = name, displaySource = "工作流存储") }
        viewModelScope.launch {
            repository.setLastImportedWorkflow(name, "STORAGE")
        }
    }

    /**
     * 获取当前工作流
     */
    fun getCurrentWorkflow(): JsonObject? {
        return _uiState.value.currentWorkflow
    }
    
    /**
     * 检查是否有工作流
     */
    fun hasWorkflow(): Boolean {
        return _uiState.value.hasWorkflow && _uiState.value.currentWorkflow != null
    }
}
