package com.example.myapplication.domain.manager

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工作流导入管理器
 * 负责处理各种方式的工作流导入：文件、剪贴板、URL等
 */
@Singleton
class WorkflowImportManager @Inject constructor(
    private val context: Context,
    private val json: Json
) {
    
    companion object {
        private const val TAG = "WorkflowImportManager"
    }
    
    /**
     * 导入结果数据类
     */
    data class ImportResult(
        val success: Boolean,
        val workflow: JsonObject? = null,
        val errorMessage: String = "",
        val source: ImportSource = ImportSource.UNKNOWN,
        val workflowName: String? = null
    )
    
    /**
     * 导入来源枚举
     */
    enum class ImportSource {
        FILE,           // 文件导入
        CLIPBOARD,      // 剪贴板导入
        TEXT_INPUT,     // 文本输入
        URL,           // URL导入
        UNKNOWN        // 未知来源
    }
    
    /**
     * 从文件URI导入工作流
     */
    suspend fun importFromFile(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始从文件导入工作流: $uri")
            
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult(
                    success = false,
                    errorMessage = "无法打开文件",
                    source = ImportSource.FILE
                )
            
            val content = inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.readText()
                }
            }
            
            if (content.isBlank()) {
                return@withContext ImportResult(
                    success = false,
                    errorMessage = "文件内容为空",
                    source = ImportSource.FILE
                )
            }
            
            Log.d(TAG, "文件读取成功，内容长度: ${content.length}")
            val base = parseWorkflowJson(content, ImportSource.FILE)
            val name = try { getFileDisplayName(uri) } catch (_: Exception) { null }
            base.copy(workflowName = name)
            
        } catch (e: Exception) {
            Log.e(TAG, "文件导入失败", e)
            ImportResult(
                success = false,
                errorMessage = "文件读取失败: ${e.message}",
                source = ImportSource.FILE
            )
        }
    }
    
    /**
     * 从剪贴板导入工作流
     */
    suspend fun importFromClipboard(): ImportResult = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "开始从剪贴板导入工作流")
            
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboardManager.primaryClip
            
            if (clipData == null || clipData.itemCount == 0) {
                return@withContext ImportResult(
                    success = false,
                    errorMessage = "剪贴板为空",
                    source = ImportSource.CLIPBOARD
                )
            }
            
            val clipText = clipData.getItemAt(0).text?.toString()
            if (clipText.isNullOrBlank()) {
                return@withContext ImportResult(
                    success = false,
                    errorMessage = "剪贴板内容为空",
                    source = ImportSource.CLIPBOARD
                )
            }
            
            Log.d(TAG, "剪贴板内容获取成功，长度: ${clipText.length}")
            
            withContext(Dispatchers.IO) {
                val base = parseWorkflowJson(clipText, ImportSource.CLIPBOARD)
                base.copy(workflowName = "从剪贴板导入")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "剪贴板导入失败", e)
            ImportResult(
                success = false,
                errorMessage = "剪贴板读取失败: ${e.message}",
                source = ImportSource.CLIPBOARD
            )
        }
    }
    
    /**
     * 从文本输入导入工作流
     */
    suspend fun importFromText(text: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始从文本导入工作流，长度: ${text.length}")
            
            if (text.isBlank()) {
                return@withContext ImportResult(
                    success = false,
                    errorMessage = "输入文本为空",
                    source = ImportSource.TEXT_INPUT
                )
            }
            
            parseWorkflowJson(text.trim(), ImportSource.TEXT_INPUT)
            
        } catch (e: Exception) {
            Log.e(TAG, "文本导入失败", e)
            ImportResult(
                success = false,
                errorMessage = "文本解析失败: ${e.message}",
                source = ImportSource.TEXT_INPUT
            )
        }
    }
    
    /**
     * 解析工作流JSON
     */
    private fun parseWorkflowJson(jsonText: String, source: ImportSource): ImportResult {
        try {
            Log.d(TAG, "开始解析JSON工作流")
            
            // 尝试解析JSON
            val jsonElement = json.parseToJsonElement(jsonText)
            
            // 验证是否为JSON对象
            if (jsonElement !is JsonObject) {
                return ImportResult(
                    success = false,
                    errorMessage = "JSON格式错误：必须是对象格式",
                    source = source
                )
            }
            
            // 验证是否为有效的ComfyUI工作流
            val validationResult = validateWorkflow(jsonElement)
            if (!validationResult.isValid) {
                return ImportResult(
                    success = false,
                    errorMessage = validationResult.errorMessage,
                    source = source
                )
            }
            
            Log.d(TAG, "工作流解析成功，节点数量: ${jsonElement.size}")
            
            return ImportResult(
                success = true,
                workflow = jsonElement,
                source = source
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "JSON解析失败", e)
            return ImportResult(
                success = false,
                errorMessage = "JSON解析失败: ${e.message}",
                source = source
            )
        }
    }

    private fun getFileDisplayName(uri: Uri): String? {
        return try {
            val cr = context.contentResolver
            cr.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    uri.lastPathSegment
                }
            }
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }
    
    /**
     * 验证工作流格式
     */
    private fun validateWorkflow(workflow: JsonObject): ValidationResult {
        try {
            // 检查是否为空
            if (workflow.isEmpty()) {
                return ValidationResult(false, "工作流不能为空")
            }
            
            // 检查是否包含节点
            var hasValidNodes = false
            var nodeCount = 0
            
            for ((key, value) in workflow) {
                // 跳过非数字键（可能是元数据）
                if (key.toIntOrNull() == null) continue
                
                nodeCount++
                
                // 检查节点是否为对象
                if (value !is JsonObject) {
                    continue
                }
                
                val nodeObj = value.jsonObject
                
                // 检查是否有class_type字段
                if (nodeObj.containsKey("class_type")) {
                    hasValidNodes = true
                }
            }
            
            if (nodeCount == 0) {
                return ValidationResult(false, "工作流中没有找到有效节点")
            }
            
            if (!hasValidNodes) {
                return ValidationResult(false, "工作流中没有找到有效的ComfyUI节点")
            }
            
            Log.d(TAG, "工作流验证通过，有效节点数: $nodeCount")
            return ValidationResult(true, "")
            
        } catch (e: Exception) {
            Log.e(TAG, "工作流验证失败", e)
            return ValidationResult(false, "工作流验证失败: ${e.message}")
        }
    }
    
    /**
     * 验证结果数据类
     */
    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )
    
    /**
     * 获取工作流基本信息
     */
    fun getWorkflowInfo(workflow: JsonObject): WorkflowInfo {
        var nodeCount = 0
        var outputNodes = 0
        val nodeTypes = mutableSetOf<String>()
        
        try {
            for ((key, value) in workflow) {
                // 跳过非数字键
                if (key.toIntOrNull() == null) continue
                
                if (value is JsonObject) {
                    nodeCount++
                    
                    val classType = value["class_type"]?.toString()?.removeSurrounding("\"")
                    if (classType != null) {
                        nodeTypes.add(classType)
                        
                        // 检查是否为输出节点
                        if (isOutputNode(classType)) {
                            outputNodes++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取工作流信息失败", e)
        }
        
        return WorkflowInfo(
            nodeCount = nodeCount,
            outputNodes = outputNodes,
            nodeTypes = nodeTypes.toList()
        )
    }
    
    /**
     * 判断是否为输出节点
     */
    private fun isOutputNode(classType: String): Boolean {
        val outputNodeTypes = setOf(
            "SaveImage", "PreviewImage", "VHS_VideoCombine",
            "ImageSave", "SaveAnimatedWEBP", "SaveAnimatedPNG"
        )
        return outputNodeTypes.contains(classType)
    }
    
    /**
     * 工作流信息数据类
     */
    data class WorkflowInfo(
        val nodeCount: Int,
        val outputNodes: Int,
        val nodeTypes: List<String>
    )
}
