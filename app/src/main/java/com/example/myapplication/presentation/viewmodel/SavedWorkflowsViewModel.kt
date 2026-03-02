package com.example.myapplication.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.SavedWorkflow
import com.example.myapplication.data.repository.ComfyUIRepository
import com.example.myapplication.domain.manager.WorkflowImportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.net.Uri
import java.util.UUID
import kotlinx.serialization.json.JsonObject

@HiltViewModel
class SavedWorkflowsViewModel @Inject constructor(
    private val repository: ComfyUIRepository,
    private val workflowImportManager: WorkflowImportManager
) : ViewModel() {

    val items: StateFlow<List<SavedWorkflow>> = repository.getSavedWorkflows()
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 最近一次加载的工作流ID（用于返回存储页时显示"已加载"标记）
    private val _lastLoadedId = MutableStateFlow<String?>(null)
    val lastLoadedId: StateFlow<String?> = _lastLoadedId
    
    // 剪贴板命名对话框相关
    private val _showClipboardNameDialog = MutableStateFlow(false)
    val showClipboardNameDialog: StateFlow<Boolean> = _showClipboardNameDialog
    
    private val _pendingClipboardResult = MutableStateFlow<WorkflowImportManager.ImportResult?>(null)
    val pendingClipboardResult: StateFlow<WorkflowImportManager.ImportResult?> = _pendingClipboardResult

    fun setLastLoadedId(id: String?) {
        _lastLoadedId.value = id
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteSavedWorkflow(id) }
    }

    fun rename(id: String, newName: String) {
        viewModelScope.launch { repository.renameSavedWorkflow(id, newName) }
    }

    fun save(item: SavedWorkflow) {
        viewModelScope.launch { repository.saveWorkflow(item) }
    }

    fun importFromFileAndSave(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = workflowImportManager.importFromFile(uri)
                if (result.success && result.workflow != null) {
                    val name = result.workflowName ?: deriveName(result.workflow)
                    val source = when (result.source) {
                        WorkflowImportManager.ImportSource.FILE -> "FILE"
                        else -> "UNKNOWN"
                    }
                    val item = SavedWorkflow(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        json = result.workflow,
                        timestamp = System.currentTimeMillis(),
                        source = source
                    )
                    repository.saveWorkflow(item)
                }
            } catch (_: Exception) { }
        }
    }

    fun importFromClipboardAndSave() {
        viewModelScope.launch {
            try {
                val result = workflowImportManager.importFromClipboard()
                
                // 如果成功识别到工作流，显示命名对话框
                if (result.success && result.workflow != null) {
                    _pendingClipboardResult.value = result
                    _showClipboardNameDialog.value = true
                }
                // 失败的情况下不做任何处理（可以在UI层显示toast）
            } catch (_: Exception) { }
        }
    }
    
    /**
     * 确认剪贴板工作流名称并保存到存储
     */
    fun confirmClipboardWorkflowNameAndSave(name: String) {
        val pendingResult = _pendingClipboardResult.value
        if (pendingResult != null && pendingResult.success && pendingResult.workflow != null) {
            viewModelScope.launch {
                val finalName = name.trim().ifBlank { "未命名工作流" }
                val item = SavedWorkflow(
                    id = UUID.randomUUID().toString(),
                    name = finalName,
                    json = pendingResult.workflow,
                    timestamp = System.currentTimeMillis(),
                    source = "CLIPBOARD"
                )
                repository.saveWorkflow(item)
                
                // 关闭对话框并清空pending数据
                _showClipboardNameDialog.value = false
                _pendingClipboardResult.value = null
            }
        }
    }
    
    /**
     * 取消剪贴板工作流导入
     */
    fun cancelClipboardWorkflowImport() {
        // 关闭对话框并丢弃待处理的工作流
        _showClipboardNameDialog.value = false
        _pendingClipboardResult.value = null
    }

    private fun deriveName(workflow: JsonObject): String {
        return "工作流" + System.currentTimeMillis().toString().takeLast(6)
    }
}


