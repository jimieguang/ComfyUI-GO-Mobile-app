package com.example.myapplication.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 保存媒体到相册的工具：
 * - 图片：支持远程URL / file:// / 本地路径（通过 Coil 加载）
 * - 视频：支持 file:// / 本地路径（要求文件已在本地；远程URL不在此处下载）
 */
object MediaSaveUtils {
    /**
     * 统一保存函数：自动判断图片/视频/音频
     */
    suspend fun saveMediaToGallery(context: Context, url: String): Boolean {
        val lower = url.lowercase()
        val isVideo = lower.endsWith(".mp4") || lower.endsWith(".mov") ||
            lower.endsWith(".avi") || lower.endsWith(".mkv") || lower.endsWith(".webm") ||
            lower.contains("/history_videos/") || lower.contains("history_videos")

        val audioExt = guessExtensionFromUrl(url)
        val isAudio = audioExt in setOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "wma", "opus", "aiff") ||
            lower.contains("/history_audios/") || lower.contains("history_audios")

        return when {
            isVideo -> saveVideoToGallery(context, url)
            isAudio -> saveAudioToGallery(context, url)
            else -> saveImageToGallery(context, url)
        }
    }

    /**
     * 保存视频到相册（要求本地文件存在）
     * - 支持传入 file:// 前缀
     */
    suspend fun saveVideoToGallery(context: Context, videoPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 去掉 file:// 前缀（如果有）
                val path = videoPath.removePrefix("file://")
                val videoFile = File(path)
                if (!videoFile.exists()) {
                    return@withContext false
                }

                val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val displayName = "ComfyUI_${time}.mp4"
                val mimeType = "video/mp4"
                val relativePath = Environment.DIRECTORY_MOVIES + "/ComfyUI"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                        put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }

                    val uri = context.contentResolver.insert(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        values
                    ) ?: return@withContext false

                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        FileInputStream(videoFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    } ?: return@withContext false

                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    return@withContext true
                } else {
                    val moviesDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "ComfyUI"
                    )
                    if (!moviesDir.exists()) {
                        moviesDir.mkdirs()
                    }

                    val destFile = File(moviesDir, displayName)
                    FileInputStream(videoFile).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DATA, destFile.absolutePath)
                        put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                    }
                    context.contentResolver.insert(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        values
                    )
                    return@withContext true
                }
            } catch (_: Exception) {
                return@withContext false
            }
        }
    }

    /**
     * 保存图片到相册（支持远程URL / file:// / 本地路径）
     */
    suspend fun saveImageToGallery(context: Context, url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                val drawable = (result as? SuccessResult)?.drawable ?: return@withContext false
                val bitmap = drawable.toBitmap()

                val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val displayName = "ComfyUI_${time}.jpg"
                val mimeType = "image/jpeg"
                val relativePath = Environment.DIRECTORY_PICTURES + "/comfyui"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext false
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                    } ?: return@withContext false
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    true
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "comfyui")
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, displayName)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * 保存音频到相册/音乐库
     * - 支持 file:// / 本地路径
     * - 支持 http(s) 远程URL（会先下载到临时文件再写入 MediaStore）
     */
    suspend fun saveAudioToGallery(context: Context, audioPathOrUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val localFile = when {
                    audioPathOrUrl.startsWith("file://", ignoreCase = true) -> File(audioPathOrUrl.removePrefix("file://"))
                    audioPathOrUrl.startsWith("http://", ignoreCase = true) || audioPathOrUrl.startsWith("https://", ignoreCase = true) -> {
                        downloadRemoteToTempFile(context, audioPathOrUrl)
                    }
                    else -> File(audioPathOrUrl)
                } ?: return@withContext false

                if (!localFile.exists() || localFile.length() <= 0L) return@withContext false

                val ext = localFile.extension.lowercase().ifBlank { guessExtensionFromUrl(audioPathOrUrl) }.ifBlank { "mp3" }
                val mimeType = audioMimeType(ext)

                val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val displayName = "ComfyUI_${time}.$ext"
                val relativePath = Environment.DIRECTORY_MUSIC + "/ComfyUI"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                        put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext false

                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(localFile).use { input ->
                            input.copyTo(out)
                        }
                    } ?: return@withContext false

                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    return@withContext true
                } else {
                    val musicDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                        "ComfyUI"
                    )
                    if (!musicDir.exists()) musicDir.mkdirs()
                    val destFile = File(musicDir, displayName)
                    FileInputStream(localFile).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DATA, destFile.absolutePath)
                        put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                    }
                    context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    return@withContext true
                }
            } catch (_: Exception) {
                return@withContext false
            }
        }
    }

    private fun guessExtensionFromUrl(url: String): String {
        val lower = url.lowercase()
        val filenameParam = Regex("[?&]filename=([^&]+)").find(lower)?.groupValues?.getOrNull(1)
        val name = filenameParam ?: lower.substringAfterLast('/')
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext
    }

    private fun audioMimeType(ext: String): String {
        return when (ext.lowercase()) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "opus" -> "audio/opus"
            "wma" -> "audio/x-ms-wma"
            "aiff" -> "audio/aiff"
            else -> "audio/*"
        }
    }

    private fun downloadRemoteToTempFile(context: Context, url: String): File? {
        var connection: HttpURLConnection? = null
        return try {
            val u = URL(url)
            connection = (u.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 60_000
                doInput = true
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val ext = guessExtensionFromUrl(url).ifBlank { "mp3" }
            val tmp = File(context.cacheDir, "audio_${System.currentTimeMillis()}.$ext")

            BufferedInputStream(connection.inputStream, 64 * 1024).use { input ->
                BufferedOutputStream(FileOutputStream(tmp), 64 * 1024).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            tmp
        } catch (_: Exception) {
            null
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }
}


