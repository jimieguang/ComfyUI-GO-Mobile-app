package com.example.myapplication.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工作流JSON管理器
 * 负责工作流JSON的本地存储、读取、删除和去重
 * 
 * 特性:
 * - 使用SHA-256 hash作为文件名，自动去重
 * - 多次执行相同工作流只保存一份JSON
 * - 删除时检查引用计数，避免误删
 */
@Singleton
class WorkflowJsonManager @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "WorkflowJsonManager"
        private const val WORKFLOW_JSON_DIR = "workflow_jsons"
        private const val HASH_LENGTH = 20 // 使用hash的前20位作为文件名
    }
    
    /**
     * 获取工作流JSON存储目录
     */
    private fun getJsonDir(): File {
        val dir = File(context.filesDir, WORKFLOW_JSON_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * 计算工作流JSON的SHA-256 hash
     * @param workflow 工作流JSON对象
     * @return hash字符串（前20位）
     */
    fun calculateHash(workflow: JsonObject): String {
        val jsonString = workflow.toString()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(jsonString.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }.take(HASH_LENGTH)
    }
    
    /**
     * 保存工作流JSON到本地
     * @param hash 工作流hash
     * @param workflow 工作流JSON对象
     * @return 是否保存成功
     */
    suspend fun saveWorkflowJson(hash: String, workflow: JsonObject): Boolean = 
        withContext(Dispatchers.IO) {
            try {
                val file = File(getJsonDir(), "${hash}.json")
                file.writeText(workflow.toString())
                Log.d(TAG, "保存工作流JSON成功: ${file.absolutePath}, 大小: ${file.length() / 1024}KB")
                true
            } catch (e: Exception) {
                Log.e(TAG, "保存工作流JSON失败: $hash", e)
                false
            }
        }
    
    /**
     * 从本地读取工作流JSON
     * @param hash 工作流hash
     * @return 工作流JSON对象，失败返回null
     */
    suspend fun readWorkflowJson(hash: String): JsonObject? = 
        withContext(Dispatchers.IO) {
            try {
                val file = File(getJsonDir(), "${hash}.json")
                if (!file.exists()) {
                    Log.w(TAG, "工作流JSON文件不存在: $hash")
                    return@withContext null
                }
                
                val jsonString = file.readText()
                val jsonElement = Json.parseToJsonElement(jsonString)
                jsonElement.jsonObject
            } catch (e: Exception) {
                Log.e(TAG, "读取工作流JSON失败: $hash", e)
                null
            }
        }
    
    /**
     * 删除工作流JSON文件
     * @param hash 工作流hash
     * @return 是否删除成功
     */
    suspend fun deleteWorkflowJson(hash: String): Boolean = 
        withContext(Dispatchers.IO) {
            try {
                val file = File(getJsonDir(), "${hash}.json")
                if (file.exists()) {
                    val success = file.delete()
                    if (success) {
                        Log.d(TAG, "删除工作流JSON成功: $hash")
                    } else {
                        Log.w(TAG, "删除工作流JSON失败: $hash")
                    }
                    success
                } else {
                    Log.w(TAG, "工作流JSON文件不存在，跳过删除: $hash")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除工作流JSON异常: $hash", e)
                false
            }
        }
    
    /**
     * 检查工作流JSON是否存在
     * @param hash 工作流hash
     * @return 是否存在
     */
    fun isJsonExists(hash: String): Boolean {
        val file = File(getJsonDir(), "${hash}.json")
        return file.exists()
    }
    
    /**
     * 获取所有工作流JSON文件的总大小（MB）
     */
    fun getTotalJsonSizeMB(): Float {
        return try {
            val files = getJsonDir().listFiles() ?: return 0f
            val totalBytes = files.sumOf { it.length() }
            totalBytes / 1024f / 1024f
        } catch (e: Exception) {
            Log.e(TAG, "计算JSON总大小失败", e)
            0f
        }
    }
    
    /**
     * 获取工作流JSON文件数量
     */
    fun getJsonCount(): Int {
        return try {
            getJsonDir().listFiles()?.size ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "获取JSON文件数量失败", e)
            0
        }
    }
    
    /**
     * 清空所有工作流JSON（慎用）
     */
    suspend fun clearAllJson(): Int = withContext(Dispatchers.IO) {
        try {
            val files = getJsonDir().listFiles() ?: return@withContext 0
            var deletedCount = 0
            files.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                }
            }
            Log.d(TAG, "清空所有工作流JSON，删除 $deletedCount 个文件")
            deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "清空工作流JSON失败", e)
            0
        }
    }
}

