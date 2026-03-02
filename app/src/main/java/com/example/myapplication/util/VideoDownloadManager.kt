package com.example.myapplication.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoDownloadManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "VideoDownloadManager"
        private const val HISTORY_VIDEOS_DIR = "history_videos"
        private const val BUFFER_SIZE = 64 * 1024 // 64KB
        private const val PROGRESS_CALLBACK_MIN_INTERVAL_MS = 250L
        private const val PROGRESS_CALLBACK_MIN_BYTES = 512L * 1024L // 512KB
        
        // 🔄 重试配置
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L  // 初始重试延迟2秒
        private const val CONNECT_TIMEOUT_MS = 30000  // 连接超时30秒
        private const val READ_TIMEOUT_MS = 60000  // 读取超时60秒（视频文件较大）
    }

    private fun getHistoryVideosDir(): File {
        val dir = File(context.filesDir, HISTORY_VIDEOS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun guessExtensionFromUrl(url: String): String {
        val filenameParam = Regex("[?&]filename=([^&]+)").find(url)?.groupValues?.getOrNull(1)
        val name = filenameParam ?: url.substringAfterLast('/')
        val ext = name.substringAfterLast('.', "").lowercase()
        return if (ext.isNotEmpty()) ext else "mp4"
    }

    /**
     * 🔄 带重试机制的视频下载
     * @param videoUrl 视频URL
     * @param promptId 执行ID
     * @param index 视频索引
     * @param onProgress 进度回调 (已下载字节, 总字节)
     * @return 本地文件路径，失败返回null
     */
    suspend fun downloadAndSaveVideo(
        videoUrl: String, 
        promptId: String, 
        index: Int,
        onProgress: ((Long, Long) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        
        // 🔄 最多重试 MAX_RETRY_ATTEMPTS 次
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                Log.d(TAG, "🎬 [尝试 ${attempt + 1}/$MAX_RETRY_ATTEMPTS] 开始下载视频: $videoUrl")
                
                val result = downloadVideoOnce(videoUrl, promptId, index, onProgress)
                if (result != null) {
                    Log.d(TAG, "✅ 视频下载成功: $result")
                    return@withContext result
                }
                
                // 如果返回null（非异常失败，如HTTP错误），记录但继续重试
                Log.w(TAG, "⚠️ [尝试 ${attempt + 1}/$MAX_RETRY_ATTEMPTS] 下载返回null，可能是HTTP错误")
                
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "❌ [尝试 ${attempt + 1}/$MAX_RETRY_ATTEMPTS] 下载异常: ${e.message}", e)
            }
            
            // 如果不是最后一次尝试，等待后重试（指数退避）
            if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                val delayTime = RETRY_DELAY_MS * (attempt + 1)
                Log.d(TAG, "⏱️ 将在 ${delayTime}ms 后重试...")
                delay(delayTime)
            }
        }
        
        // 所有重试都失败
        Log.e(TAG, "💀 视频下载最终失败，已重试 $MAX_RETRY_ATTEMPTS 次: $videoUrl", lastException)
        return@withContext null
    }
    
    /**
     * 🎯 单次视频下载（无重试）
     */
    private suspend fun downloadVideoOnce(
        videoUrl: String,
        promptId: String,
        index: Int,
        onProgress: ((Long, Long) -> Unit)?
    ): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var tempFile: File? = null
        
        try {
            val url = URL(videoUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doInput = true
            }
            
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "视频下载失败，HTTP $code: $videoUrl")
                return@withContext null
            }

            val ext = guessExtensionFromUrl(videoUrl)
            val timestamp = System.currentTimeMillis()
            val filename = "${promptId}_${index}_${timestamp}.${ext}"
            tempFile = File(getHistoryVideosDir(), "${filename}.tmp") // 先下载到临时文件
            val outFile = File(getHistoryVideosDir(), filename)

            // 获取文件总大小
            val totalBytes = connection.contentLength.toLong().coerceAtLeast(0L)
            var downloadedBytes = 0L
            var lastCallbackAt = 0L
            var bytesSinceLastCallback = 0L
            
            Log.d(TAG, "📥 视频总大小: ${totalBytes / 1024}KB")

            BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                BufferedOutputStream(FileOutputStream(tempFile), BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        bytesSinceLastCallback += read
                        
                        // 回调下载进度
                        val now = System.currentTimeMillis()
                        if (onProgress != null &&
                            (
                                downloadedBytes == totalBytes ||
                                    (now - lastCallbackAt) >= PROGRESS_CALLBACK_MIN_INTERVAL_MS ||
                                    bytesSinceLastCallback >= PROGRESS_CALLBACK_MIN_BYTES
                            )
                        ) {
                            lastCallbackAt = now
                            bytesSinceLastCallback = 0L
                            onProgress.invoke(downloadedBytes, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            // 下载完成，重命名临时文件
            if (tempFile.renameTo(outFile)) {
                Log.d(TAG, "视频保存成功: ${outFile.absolutePath}, 大小: ${outFile.length()/1024}KB")
                return@withContext outFile.absolutePath
            } else {
                Log.e(TAG, "重命名临时文件失败")
                try { tempFile.delete() } catch (_: Exception) {}
                return@withContext null
            }
            
        } catch (e: Exception) {
            // 清理临时文件
            tempFile?.delete()
            throw e
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun downloadAndSaveVideos(
        videoUrls: List<String>, 
        promptId: String,
        onProgress: ((Int, Long, Long) -> Unit)? = null  // index, downloaded, total
    ): List<String> = withContext(Dispatchers.IO) {
        val saved = mutableListOf<String>()
        videoUrls.forEachIndexed { index, url ->
            try {
                val path = downloadAndSaveVideo(url, promptId, index) { downloaded, total ->
                    onProgress?.invoke(index, downloaded, total)
                }
                if (path != null) saved.add(path)
            } catch (e: Exception) {
                Log.e(TAG, "批量下载第${index}个视频失败", e)
            }
        }
        Log.d(TAG, "批量视频下载完成: ${saved.size}/${videoUrls.size}")
        return@withContext saved
    }
    
    /**
     * 检查视频是否已下载（避免重复下载）
     */
    fun isVideoDownloaded(promptId: String, index: Int): String? {
        val dir = getHistoryVideosDir()
        val matchingFiles = dir.listFiles { file ->
            file.isFile &&
                file.name.startsWith("${promptId}_${index}_") &&
                !file.name.endsWith(".tmp") &&
                file.length() > 0L
        } ?: return null

        // 选最近修改的（更可能是最新完整下载）
        return matchingFiles
            .maxByOrNull { it.lastModified() }
            ?.absolutePath
    }
    
    /**
     * 删除指定路径的视频文件
     */
    suspend fun deleteVideoFiles(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { path ->
            try {
                val file = File(path)
                if (file.exists() && file.delete()) {
                    Log.d(TAG, "删除视频文件: $path")
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除视频文件失败: $path", e)
            }
        }
    }
    
    /**
     * 删除特定执行记录的所有视频
     */
    suspend fun deleteVideosForPrompt(promptId: String) = withContext(Dispatchers.IO) {
        try {
            val dir = getHistoryVideosDir()
            val files = dir.listFiles { file ->
                file.name.startsWith("${promptId}_")
            }
            
            files?.forEach { file ->
                if (file.delete()) {
                    Log.d(TAG, "删除视频: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除视频失败: $promptId", e)
        }
    }
    
    /**
     * 清理所有历史视频
     */
    suspend fun clearAllVideos() = withContext(Dispatchers.IO) {
        try {
            val dir = getHistoryVideosDir()
            dir.listFiles()?.forEach { file ->
                file.delete()
            }
            Log.d(TAG, "清理所有历史视频完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理历史视频失败", e)
        }
    }
    
    /**
     * 获取历史视频总大小（MB）
     */
    suspend fun getTotalVideosSizeMB(): Float = withContext(Dispatchers.IO) {
        try {
            val dir = getHistoryVideosDir()
            val totalBytes = dir.listFiles()?.sumOf { it.length() } ?: 0L
            return@withContext (totalBytes / 1024f / 1024f)
        } catch (e: Exception) {
            Log.e(TAG, "获取视频大小失败", e)
            return@withContext 0f
        }
    }
    
    /**
     * 获取历史视频数量
     */
    suspend fun getVideosCount(): Int = withContext(Dispatchers.IO) {
        try {
            val dir = getHistoryVideosDir()
            return@withContext dir.listFiles()?.size ?: 0
        } catch (e: Exception) {
            return@withContext 0
        }
    }
}


