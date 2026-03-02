package com.example.myapplication.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

/**
 * 图片处理工具类
 */
object ImageUtils {
    
    /**
     * 从Uri读取图片数据为ByteArray
     */
    fun uriToByteArray(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArrayOutputStream()
                val data = ByteArray(1024)
                var nRead: Int
                while (inputStream.read(data, 0, data.size).also { nRead = it } != -1) {
                    buffer.write(data, 0, nRead)
                }
                val result = buffer.toByteArray()
                
                // 调试：检查读取的原始数据
                android.util.Log.d("ImageUtils", "uriToByteArray - 原始字节数组大小: ${result.size}")
                
                // 调试：检查解码后的图像尺寸
                val testBitmap = byteArrayToBitmap(result)
                if (testBitmap != null) {
                    android.util.Log.d("ImageUtils", "uriToByteArray - 解码后图像尺寸: ${testBitmap.width}x${testBitmap.height}")
                } else {
                    android.util.Log.e("ImageUtils", "uriToByteArray - 无法解码图像")
                }
                
                result
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 从Uri获取文件名
     */
    fun getFileName(context: Context, uri: Uri): String {
        var fileName = "image_${System.currentTimeMillis()}.jpg"
        
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
            e.printStackTrace()
        }
        
        return fileName
    }
    
    /**
     * 获取文件大小
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
            e.printStackTrace()
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
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    /**
     * 从Uri转换为Bitmap
     */
    fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 从ByteArray转换为Bitmap
     */
    fun byteArrayToBitmap(byteArray: ByteArray): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 将Bitmap转换为ByteArray
     */
    fun bitmapToByteArray(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG, quality: Int = 100): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(format, quality, stream)
        return stream.toByteArray()
    }
    
    /**
     * 缩放Bitmap到指定尺寸
     */
    fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // 计算缩放比例
        val scaleWidth = maxWidth.toFloat() / width
        val scaleHeight = maxHeight.toFloat() / height
        val scale = minOf(scaleWidth, scaleHeight)
        
        // 如果不需要缩放，直接返回原图
        if (scale >= 1.0f) return bitmap
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * 确保Bitmap使用ARGB_8888格式（支持Alpha通道）
     */
    fun ensureARGB8888(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }
    
    
    
    /**
     * 获取图像的透明像素数量
     */
    fun getTransparentPixelCount(bitmap: Bitmap): Int {
        if (!bitmap.hasAlpha()) return 0
        
        var count = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel shr 24) and 0xFF
                if (alpha == 0) count++
            }
        }
        return count
    }
}
