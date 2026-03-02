package com.example.myapplication.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

/**
 * 视频处理工具类
 */
object VideoUtils {
    
    /**
     * 从Uri读取视频数据为ByteArray
     * 注意：大文件建议使用流式上传，避免内存溢出
     */
    fun uriToByteArray(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArrayOutputStream()
                val data = ByteArray(8192)  // 8KB 缓冲区
                var nRead: Int
                while (inputStream.read(data, 0, data.size).also { nRead = it } != -1) {
                    buffer.write(data, 0, nRead)
                }
                buffer.toByteArray()
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoUtils", "读取视频失败", e)
            null
        }
    }
    
    /**
     * 从Uri获取视频文件名
     */
    fun getFileName(context: Context, uri: Uri): String {
        var fileName = "video_${System.currentTimeMillis()}.mp4"
        
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            fileName = name
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoUtils", "获取文件名失败", e)
        }
        
        return fileName
    }
    
    /**
     * 获取视频文件大小（字节）
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) {
                        cursor.getLong(sizeIndex)
                    } else {
                        0L
                    }
                } else {
                    0L
                }
            } ?: 0L
        } catch (e: Exception) {
            android.util.Log.e("VideoUtils", "获取文件大小失败", e)
            0L
        }
    }
    
    /**
     * 格式化文件大小显示
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    /**
     * 获取视频时长（毫秒）
     */
    fun getVideoDuration(context: Context, uri: Uri): Long? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            duration?.toLongOrNull()
        } catch (e: Exception) {
            android.util.Log.e("VideoUtils", "获取视频时长失败", e)
            null
        }
    }
    
    /**
     * 格式化视频时长显示
     */
    fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
            minutes > 0 -> String.format("%d:%02d", minutes, seconds % 60)
            else -> String.format("0:%02d", seconds)
        }
    }
    
    /**
     * 获取视频格式（扩展名）
     */
    fun getVideoFormat(fileName: String): String? {
        return fileName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
    }
    
    /**
     * 判断文件是否为视频
     */
    fun isVideoFile(fileName: String): Boolean {
        val videoExtensions = setOf("mp4", "webm", "mov", "avi", "mkv", "flv", "wmv", "m4v", "gif")
        val extension = getVideoFormat(fileName)?.lowercase()
        return extension in videoExtensions
    }
    
    /**
     * 检测文件的真实格式（通过文件头魔数）
     * @return "MP4", "GIF", "WEBM", "UNKNOWN" 等
     */
    fun detectActualFormat(filePath: String): String {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists()) return "FILE_NOT_FOUND"
            
            val bytes = ByteArray(12)
            java.io.FileInputStream(file).use { input ->
                val read = input.read(bytes)
                if (read < 4) return "UNKNOWN"
            }
            
            // 检查文件头魔数
            when {
                // GIF: "GIF87a" or "GIF89a"
                bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && 
                bytes[2] == 0x46.toByte() -> "GIF"
                
                // MP4: ftyp
                bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() && 
                bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte() -> "MP4"
                
                // WEBM: 0x1A 0x45 0xDF 0xA3
                bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() && 
                bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte() -> "WEBM"
                
                else -> "UNKNOWN"
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoUtils", "检测文件格式失败", e)
            "ERROR"
        }
    }
    
    /**
     * 判断文件大小是否过大（超过100MB提醒用户）
     */
    fun isLargeFile(bytes: Long): Boolean {
        return bytes > 100 * 1024 * 1024  // 100MB
    }
    
    /**
     * 从视频URL获取缩略图（第一帧）
     * @param videoUrl 视频URL（支持网络URL或本地文件路径）
     * @return 视频第一帧的Bitmap，失败返回null
     */
    fun getVideoThumbnail(context: Context, videoUrl: String): android.graphics.Bitmap? {
        return try {
            // 🎨 GIF 文件不支持 MediaMetadataRetriever，直接返回 null
            // UI 会显示占位符图标，但视频仍然可以正常播放
            if (videoUrl.endsWith(".gif", ignoreCase = true)) {
                android.util.Log.w("VideoUtils", "GIF 格式不支持缩略图生成，将显示占位符")
                return null
            }
            
            val retriever = MediaMetadataRetriever()
            
            // 判断是网络URL还是本地文件
            if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                retriever.setDataSource(videoUrl, HashMap())
            } else {
                retriever.setDataSource(videoUrl)
            }
            
            // 获取第一帧（时间为0微秒）
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("VideoUtils", "获取视频缩略图失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 从视频URI获取缩略图（第一帧）
     */
    fun getVideoThumbnail(context: Context, uri: Uri): android.graphics.Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("VideoUtils", "获取视频缩略图失败: ${e.message}", e)
            null
        }
    }
}

