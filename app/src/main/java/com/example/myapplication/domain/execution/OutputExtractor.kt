package com.example.myapplication.domain.execution

import android.util.Log
import kotlinx.serialization.json.*
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工作流输出提取器
 * 负责从 ComfyUI History API 响应中提取各类输出（图片、视频、音频、文本）
 */
@Singleton
class OutputExtractor @Inject constructor() {
    
    companion object {
        private const val TAG = "OutputExtractor"
        
        // 视频文件扩展名
        private val VIDEO_EXTENSIONS = listOf("mp4", "avi", "mov", "webm", "mkv", "gif")
        
        // 音频文件扩展名
        private val AUDIO_EXTENSIONS = listOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "wma", "opus", "aiff")
        
        // 文本输出键名
        private val TEXT_KEYS = listOf(
            "text", "string", "value", "output", "result",
            "content", "message", "log", "response"
        )
    }
    
    /**
     * 提取结果数据类
     */
    data class ExtractedOutputs(
        val imageUrls: List<String> = emptyList(),
        val videoUrls: List<String> = emptyList(),
        val audioUrls: List<String> = emptyList(),
        val textOutputs: Map<String, List<String>> = emptyMap()
    ) {
        fun hasAnyOutput(): Boolean {
            return imageUrls.isNotEmpty() || videoUrls.isNotEmpty() || 
                   audioUrls.isNotEmpty() || textOutputs.isNotEmpty()
        }
    }
    
    /**
     * 从历史条目中提取所有输出
     */
    fun extractAllOutputs(serverUrl: String, entry: JsonObject): ExtractedOutputs {
        return ExtractedOutputs(
            imageUrls = extractImageUrls(serverUrl, entry),
            videoUrls = extractVideoUrls(serverUrl, entry),
            audioUrls = extractAudioUrls(serverUrl, entry),
            textOutputs = extractTexts(entry)
        )
    }
    
    /**
     * 判断历史条目是否包含输出
     */
    fun hasOutputs(entry: JsonObject): Boolean {
        val outputs = entry["outputs"]?.jsonObject ?: return false
        
        for ((nodeId, nodeOut) in outputs) {
            val nodeOutObj = nodeOut.jsonObject
            
            // 检查图片输出
            if (hasNonEmptyArray(nodeOutObj, "images")) return true
            
            // 检查视频输出
            if (hasNonEmptyArray(nodeOutObj, "videos")) return true
            
            // 检查音频输出
            if (hasNonEmptyArray(nodeOutObj, "audios")) return true
            
            // 检查 GIF 输出
            if (hasNonEmptyArray(nodeOutObj, "gifs")) return true
            
            // 检查其他可能的输出字段
            val otherKeys = listOf("audio", "sound", "output", "result")
            for (key in otherKeys) {
                val field = nodeOutObj[key]
                if (field is JsonArray && field.isNotEmpty()) return true
                if (field is JsonObject) return true
                if (field is JsonPrimitive && field.isString && field.content.isNotEmpty()) return true
            }
        }
        
        return false
    }
    
    /**
     * 从历史条目中提取图片 URL
     */
    fun extractImageUrls(serverUrl: String, entry: JsonObject): List<String> {
        val outputs = entry["outputs"]?.jsonObject ?: return emptyList()
        val urls = mutableListOf<String>()
        val base = normalizeBaseUrl(serverUrl)
        
        for ((_, nodeOut) in outputs) {
            val nodeOutObj = nodeOut.jsonObject
            
            // 处理 images 数组
            val imagesArray = nodeOutObj["images"]?.jsonArray
            if (imagesArray != null) {
                for (img in imagesArray) {
                    val url = buildViewUrl(base, img.jsonObject, VIDEO_EXTENSIONS)
                    if (url != null && !isVideoFile(img.jsonObject)) {
                        urls.add(url)
                    }
                }
            }
            
            // 兼容单张图像输出
            val imgObj = nodeOutObj["image"]?.jsonObject
            if (imgObj != null) {
                val url = buildViewUrl(base, imgObj, VIDEO_EXTENSIONS)
                if (url != null && !isVideoFile(imgObj)) {
                    urls.add(url)
                }
            }
        }
        
        return urls
    }
    
    /**
     * 从历史条目中提取视频 URL
     */
    fun extractVideoUrls(serverUrl: String, entry: JsonObject): List<String> {
        val outputs = entry["outputs"]?.jsonObject ?: return emptyList()
        val urls = mutableListOf<String>()
        val base = normalizeBaseUrl(serverUrl)
        
        for ((_, nodeOut) in outputs) {
            val nodeOutObj = nodeOut.jsonObject
            
            // 检查 images 数组中的视频文件
            val imagesArray = nodeOutObj["images"]?.jsonArray
            if (imagesArray != null) {
                for (img in imagesArray) {
                    if (isVideoFile(img.jsonObject)) {
                        buildViewUrl(base, img.jsonObject, emptyList())?.let { urls.add(it) }
                    }
                }
            }
            
            // 检查 videos 数组
            val videosArray = nodeOutObj["videos"]?.jsonArray
            if (videosArray != null) {
                for (video in videosArray) {
                    buildViewUrl(base, video.jsonObject, emptyList())?.let { urls.add(it) }
                }
            }
            
            // 检查 gifs 数组
            val gifsArray = nodeOutObj["gifs"]?.jsonArray
            if (gifsArray != null) {
                for (gif in gifsArray) {
                    buildViewUrl(base, gif.jsonObject, emptyList())?.let { urls.add(it) }
                }
            }
        }
        
        return urls
    }
    
    /**
     * 从历史条目中提取音频 URL
     */
    fun extractAudioUrls(serverUrl: String, entry: JsonObject): List<String> {
        val outputs = entry["outputs"]?.jsonObject ?: return emptyList()
        val urls = mutableListOf<String>()
        val base = normalizeBaseUrl(serverUrl)
        
        for ((_, nodeOut) in outputs) {
            val nodeOutObj = nodeOut.jsonObject
            
            // 检查 audios 数组
            val audiosArray = nodeOutObj["audios"]?.jsonArray
            if (audiosArray != null) {
                for (audio in audiosArray) {
                    buildViewUrl(base, audio.jsonObject, emptyList())?.let { urls.add(it) }
                }
            }
            
            // 检查 images 数组中的音频文件
            val imagesArray = nodeOutObj["images"]?.jsonArray
            if (imagesArray != null) {
                for (img in imagesArray) {
                    if (isAudioFile(img.jsonObject)) {
                        buildViewUrl(base, img.jsonObject, emptyList())?.let { urls.add(it) }
                    }
                }
            }
            
            // 检查其他可能的音频字段
            val audioKeys = listOf("audio", "sound", "output", "result", "file", "files")
            for (key in audioKeys) {
                extractAudioFromField(base, nodeOutObj[key])?.let { urls.addAll(it) }
            }
        }
        
        return urls
    }
    
    /**
     * 从历史条目中提取文本输出
     */
    fun extractTexts(entry: JsonObject): Map<String, List<String>> {
        val outputs = entry["outputs"]?.jsonObject ?: return emptyMap()
        val textsMap = mutableMapOf<String, List<String>>()
        
        for ((nodeId, nodeOut) in outputs) {
            val nodeOutObj = nodeOut.jsonObject
            val texts = mutableListOf<String>()
            
            // 检查主输出中的文本
            for (key in TEXT_KEYS) {
                extractTextFromElement(nodeOutObj[key])?.let { texts.addAll(it) }
            }
            
            // 检查 ui 对象中的文本
            val ui = nodeOutObj["ui"]?.jsonObject
            if (ui != null) {
                for (key in TEXT_KEYS) {
                    extractTextFromElement(ui[key])?.let { texts.addAll(it) }
                }
            }
            
            if (texts.isNotEmpty()) {
                textsMap[nodeId] = texts.distinct()
            }
        }
        
        return textsMap
    }
    
    // ==================== 私有辅助方法 ====================
    
    private fun normalizeBaseUrl(serverUrl: String): String {
        return if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
    }
    
    private fun hasNonEmptyArray(obj: JsonObject, key: String): Boolean {
        val arr = obj[key]?.jsonArray
        return arr != null && arr.isNotEmpty()
    }
    
    private fun buildViewUrl(base: String, fileObj: JsonObject, excludeExtensions: List<String>): String? {
        val filename = fileObj["filename"]?.jsonPrimitive?.contentOrNull
            ?: fileObj["name"]?.jsonPrimitive?.contentOrNull
            ?: return null
        
        // 如果需要排除某些扩展名
        if (excludeExtensions.isNotEmpty()) {
            val ext = filename.substringAfterLast('.', "").lowercase()
            if (ext in excludeExtensions) return null
        }
        
        val subfolder = fileObj["subfolder"]?.jsonPrimitive?.contentOrNull ?: ""
        val type = fileObj["type"]?.jsonPrimitive?.contentOrNull ?: "output"
        
        return "${base}view?filename=${URLEncoder.encode(filename, "UTF-8")}" +
               "&subfolder=${URLEncoder.encode(subfolder, "UTF-8")}" +
               "&type=${URLEncoder.encode(type, "UTF-8")}"
    }
    
    private fun isVideoFile(fileObj: JsonObject): Boolean {
        val filename = fileObj["filename"]?.jsonPrimitive?.contentOrNull
            ?: fileObj["name"]?.jsonPrimitive?.contentOrNull
            ?: return false
        val ext = filename.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }
    
    private fun isAudioFile(fileObj: JsonObject): Boolean {
        val filename = fileObj["filename"]?.jsonPrimitive?.contentOrNull
            ?: fileObj["name"]?.jsonPrimitive?.contentOrNull
            ?: return false
        val ext = filename.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }
    
    private fun extractAudioFromField(base: String, element: JsonElement?): List<String>? {
        if (element == null) return null
        
        val urls = mutableListOf<String>()
        
        when (element) {
            is JsonArray -> {
                for (item in element) {
                    when (item) {
                        is JsonObject -> {
                            if (isAudioFile(item)) {
                                buildViewUrl(base, item, emptyList())?.let { urls.add(it) }
                            }
                        }
                        is JsonPrimitive -> {
                            if (item.isString) {
                                val filename = item.content
                                val ext = filename.substringAfterLast('.', "").lowercase()
                                if (ext in AUDIO_EXTENSIONS) {
                                    urls.add("${base}view?filename=${URLEncoder.encode(filename, "UTF-8")}&subfolder=&type=output")
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
            is JsonObject -> {
                if (isAudioFile(element)) {
                    buildViewUrl(base, element, emptyList())?.let { urls.add(it) }
                }
            }
            is JsonPrimitive -> {
                if (element.isString) {
                    val filename = element.content
                    val ext = filename.substringAfterLast('.', "").lowercase()
                    if (ext in AUDIO_EXTENSIONS) {
                        urls.add("${base}view?filename=${URLEncoder.encode(filename, "UTF-8")}&subfolder=&type=output")
                    }
                }
            }
        }
        
        return urls.takeIf { it.isNotEmpty() }
    }
    
    private fun extractTextFromElement(element: JsonElement?): List<String>? {
        if (element == null) return null
        
        val texts = mutableListOf<String>()
        
        when (element) {
            is JsonArray -> {
                for (item in element) {
                    val text = item.jsonPrimitive?.contentOrNull
                    if (!text.isNullOrBlank()) {
                        texts.add(text)
                    }
                }
            }
            is JsonPrimitive -> {
                val text = element.contentOrNull
                if (!text.isNullOrBlank()) {
                    texts.add(text)
                }
            }
            else -> {
                val text = element.toString()
                if (text.isNotBlank()) {
                    texts.add(text)
                }
            }
        }
        
        return texts.takeIf { it.isNotEmpty() }
    }
}

