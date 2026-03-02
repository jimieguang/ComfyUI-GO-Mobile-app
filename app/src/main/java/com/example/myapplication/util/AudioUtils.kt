package com.example.myapplication.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

/**
 * 音频处理工具类
 */
object AudioUtils {
    
    /**
     * 从Uri读取音频数据为ByteArray
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
            android.util.Log.e("AudioUtils", "读取音频失败", e)
            null
        }
    }
    
    /**
     * 从Uri获取音频文件名
     */
    fun getFileName(context: Context, uri: Uri): String {
        var fileName = "audio_${System.currentTimeMillis()}.mp3"
        
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
            android.util.Log.e("AudioUtils", "获取文件名失败", e)
        }
        
        return fileName
    }
    
    /**
     * 获取音频文件大小（字节）
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
            android.util.Log.e("AudioUtils", "获取文件大小失败", e)
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
     * 获取音频时长（毫秒）
     */
    fun getAudioDuration(context: Context, uri: Uri): Long? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            duration?.toLongOrNull()
        } catch (e: Exception) {
            android.util.Log.e("AudioUtils", "获取音频时长失败", e)
            null
        }
    }
    
    /**
     * 格式化音频时长显示
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
     * 获取音频格式（扩展名）
     */
    fun getAudioFormat(fileName: String): String? {
        return fileName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
    }
    
    /**
     * 判断文件是否为音频
     */
    fun isAudioFile(fileName: String): Boolean {
        val audioExtensions = setOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "wma", "opus", "aiff")
        val extension = getAudioFormat(fileName)?.lowercase()
        return extension in audioExtensions
    }
    
    /**
     * 判断文件大小是否过大（超过50MB提醒用户）
     */
    fun isLargeFile(bytes: Long): Boolean {
        return bytes > 50 * 1024 * 1024  // 50MB
    }
    
    /**
     * 获取音频采样率
     */
    fun getAudioSampleRate(context: Context, uri: Uri): Int? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            retriever.release()
            sampleRate?.toIntOrNull()
        } catch (e: Exception) {
            android.util.Log.e("AudioUtils", "获取采样率失败", e)
            null
        }
    }
    
    /**
     * 获取音频比特率
     */
    fun getAudioBitrate(context: Context, uri: Uri): Int? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            retriever.release()
            bitrate?.toIntOrNull()
        } catch (e: Exception) {
            android.util.Log.e("AudioUtils", "获取比特率失败", e)
            null
        }
    }
}

