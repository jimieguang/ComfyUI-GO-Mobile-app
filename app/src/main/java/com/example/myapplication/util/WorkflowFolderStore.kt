package com.example.myapplication.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工作流文件夹（SAF 目录授权）
 *
 * 用户授权一次目录后持久保存 tree Uri，
 * 之后"选择文件"直接在应用内列出该目录的 .json 文件，
 * 不再每次都从系统文件选择器的根目录找起。
 */
@Singleton
class WorkflowFolderStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        private const val TAG = "WorkflowFolderStore"
        private val KEY_TREE_URI = stringPreferencesKey("workflow_folder_tree_uri")
        private val KEY_FOLDER_NAME = stringPreferencesKey("workflow_folder_name")
    }

    /**
     * 目录内的一个工作流文件
     */
    data class FolderFile(
        val name: String,
        val uri: Uri
    )

    /**
     * 保存用户选择的目录，并申请持久读权限
     */
    suspend fun saveFolder(treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "获取持久读权限失败（可能已授权过）", e)
        }
        val name = resolveFolderName(treeUri)
        dataStore.edit { prefs ->
            prefs[KEY_TREE_URI] = treeUri.toString()
            prefs[KEY_FOLDER_NAME] = name
        }
        Log.d(TAG, "已保存工作流目录: $name ($treeUri)")
    }

    suspend fun clearFolder() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_TREE_URI)
            prefs.remove(KEY_FOLDER_NAME)
        }
    }

    /** 当前目录的 tree Uri（未设置时为 null） */
    fun getFolderUri(): Flow<Uri?> {
        return dataStore.data.map { prefs ->
            prefs[KEY_TREE_URI]?.let { runCatching { Uri.parse(it) }.getOrNull() }
        }
    }

    /** 当前目录显示名 */
    fun getFolderName(): Flow<String?> {
        return dataStore.data.map { it[KEY_FOLDER_NAME] }
    }

    /**
     * 列出目录下的 .json 文件（按名称排序）
     */
    suspend fun listJsonFiles(treeUri: Uri): List<FolderFile> = withContext(Dispatchers.IO) {
        val result = mutableListOf<FolderFile>()
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val displayName = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    if (!displayName.lowercase().endsWith(".json")) continue
                    result += FolderFile(
                        name = displayName,
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "列出目录文件失败", e)
        }
        result.sortedBy { it.name.lowercase() }
    }

    /**
     * 读取文件内容
     */
    suspend fun readFileText(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取文件失败: $uri", e)
            null
        }
    }

    /**
     * 目录权限是否仍然有效
     */
    suspend fun hasPersistedPermission(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }
    }

    private suspend fun resolveFolderName(treeUri: Uri): String {
        return try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: treeUri.lastPathSegment ?: "已选文件夹"
        } catch (e: Exception) {
            treeUri.lastPathSegment ?: "已选文件夹"
        }
    }
}
