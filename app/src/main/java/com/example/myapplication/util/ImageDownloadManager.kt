package com.example.myapplication.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图片下载和本地存储管理器
 * 负责从ComfyUI服务器下载图片并保存到应用私有目录
 */
@Singleton
class ImageDownloadManager @Inject constructor(
    private val context: Context,
    private val imageLoader: ImageLoader
) {
    
    companion object {
        private const val TAG = "ImageDownloadManager"
        private const val HISTORY_IMAGES_DIR = "history_images"
        private const val MAX_IMAGE_SIZE_MB = 10 // 单张图片最大10MB
        private const val COMPRESSION_QUALITY = 90 // JPEG压缩质量
    }
    
    /**
     * 获取历史图片存储目录
     */
    private fun getHistoryImagesDir(): File {
        val dir = File(context.filesDir, HISTORY_IMAGES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * 下载并保存单张图片
     * @param imageUrl 图片URL
     * @param promptId 执行ID
     * @param index 图片索引
     * @return 本地文件路径，失败返回null
     */
    suspend fun downloadAndSaveImage(
        imageUrl: String,
        promptId: String,
        index: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始下载图片: $imageUrl")
            
            // 使用Coil加载图片
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false) // 不使用硬件加速，以便转换为Bitmap
                .build()
            
            val result = imageLoader.execute(request)
            
            if (result is SuccessResult) {
                val bitmap = result.drawable.toBitmap()
                
                // 检查图片尺寸
                val estimatedSizeMB = (bitmap.byteCount / 1024 / 1024).toFloat()
                if (estimatedSizeMB > MAX_IMAGE_SIZE_MB) {
                    Log.w(TAG, "图片过大: ${estimatedSizeMB}MB，跳过保存")
                    return@withContext null
                }
                
                // 生成文件名：promptId_index_timestamp.jpg
                val timestamp = System.currentTimeMillis()
                val filename = "${promptId}_${index}_${timestamp}.jpg"
                val file = File(getHistoryImagesDir(), filename)
                
                // 保存为JPEG格式（压缩）
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, out)
                }
                
                Log.d(TAG, "图片保存成功: ${file.absolutePath}, 大小: ${file.length() / 1024}KB")
                return@withContext file.absolutePath
            } else {
                Log.e(TAG, "图片加载失败: $imageUrl")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载保存图片失败: $imageUrl", e)
            return@withContext null
        }
    }
    
    /**
     * 批量下载并保存图片
     * @param imageUrls 图片URL列表
     * @param promptId 执行ID
     * @return 本地文件路径列表
     */
    suspend fun downloadAndSaveImages(
        imageUrls: List<String>,
        promptId: String
    ): List<String> = withContext(Dispatchers.IO) {
        val savedPaths = mutableListOf<String>()
        
        imageUrls.forEachIndexed { index, url ->
            try {
                val path = downloadAndSaveImage(url, promptId, index)
                if (path != null) {
                    savedPaths.add(path)
                }
            } catch (e: Exception) {
                Log.e(TAG, "批量下载第${index}张图片失败", e)
            }
        }
        
        Log.d(TAG, "批量下载完成: ${savedPaths.size}/${imageUrls.size}")
        return@withContext savedPaths
    }
    
    /**
     * 删除特定执行记录的所有图片
     */
    suspend fun deleteImagesForPrompt(promptId: String) = withContext(Dispatchers.IO) {
        try {
            val dir = getHistoryImagesDir()
            val files = dir.listFiles { file ->
                file.name.startsWith("${promptId}_")
            }
            
            files?.forEach { file ->
                if (file.delete()) {
                    Log.d(TAG, "删除图片: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除图片失败: $promptId", e)
        }
    }
    
    /**
     * 删除指定路径的图片文件
     */
    suspend fun deleteImageFiles(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { path ->
            try {
                val file = File(path)
                if (file.exists() && file.delete()) {
                    Log.d(TAG, "删除图片文件: $path")
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除图片文件失败: $path", e)
            }
        }
    }
    
    /**
     * 清理所有历史图片
     */
    suspend fun clearAllImages() = withContext(Dispatchers.IO) {
        try {
            val dir = getHistoryImagesDir()
            dir.listFiles()?.forEach { file ->
                file.delete()
            }
            Log.d(TAG, "清理所有历史图片完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理历史图片失败", e)
        }
    }
    
    /**
     * 获取历史图片总大小（MB）
     */
    suspend fun getTotalImagesSizeMB(): Float = withContext(Dispatchers.IO) {
        try {
            val dir = getHistoryImagesDir()
            val totalBytes = dir.listFiles()?.sumOf { it.length() } ?: 0L
            return@withContext (totalBytes / 1024f / 1024f)
        } catch (e: Exception) {
            Log.e(TAG, "获取图片大小失败", e)
            return@withContext 0f
        }
    }
    
    /**
     * 获取历史图片数量
     */
    suspend fun getImagesCount(): Int = withContext(Dispatchers.IO) {
        try {
            val dir = getHistoryImagesDir()
            return@withContext dir.listFiles()?.size ?: 0
        } catch (e: Exception) {
            return@withContext 0
        }
    }
    
    /**
     * 清理旧的图片（超过指定天数）
     */
    suspend fun cleanupOldImages(daysToKeep: Int = 30) = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
            val dir = getHistoryImagesDir()
            var deletedCount = 0
            
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }
            
            Log.d(TAG, "清理${daysToKeep}天前的图片，删除了${deletedCount}个文件")
        } catch (e: Exception) {
            Log.e(TAG, "清理旧图片失败", e)
        }
    }
    
    /**
     * 检查本地图片是否存在
     */
    fun isImageExists(path: String): Boolean {
        return try {
            File(path).exists()
        } catch (e: Exception) {
            false
        }
    }
}

