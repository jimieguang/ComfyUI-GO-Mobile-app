package com.example.myapplication.util

import java.util.*

/**
 * 扩展函数工具类
 */

/**
 * 生成客户端ID
 */
fun generateClientId(): String {
    return Constants.DEFAULT_CLIENT_ID_PREFIX + UUID.randomUUID().toString()
}

/**
 * 安全的字符串转换
 */
fun Any?.toSafeString(): String {
    return this?.toString() ?: ""
}

/**
 * 安全的整数转换
 */
fun String?.toSafeInt(defaultValue: Int = 0): Int {
    return try {
        this?.toIntOrNull() ?: defaultValue
    } catch (e: Exception) {
        defaultValue
    }
}

/**
 * 安全的浮点数转换
 */
fun String?.toSafeFloat(defaultValue: Float = 0f): Float {
    return try {
        this?.toFloatOrNull() ?: defaultValue
    } catch (e: Exception) {
        defaultValue
    }
}

/**
 * 验证URL格式
 */
fun String.isValidUrl(): Boolean {
    return try {
        val url = java.net.URL(this)
        url.protocol in listOf("http", "https")
    } catch (e: Exception) {
        false
    }
}
