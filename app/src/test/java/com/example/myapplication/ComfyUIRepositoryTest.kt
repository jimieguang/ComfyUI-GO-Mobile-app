package com.example.myapplication

import com.example.myapplication.data.repository.ComfyUIRepositoryImpl
import com.example.myapplication.util.generateClientId
import com.example.myapplication.util.isValidUrl
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * 验证 ComfyUI Repository 和基础架构
 */
class ComfyUIRepositoryTest {
    
    @Test
    fun `test generateClientId creates unique IDs`() {
        val id1 = generateClientId()
        val id2 = generateClientId()
        
        assertNotEquals("Client IDs should be unique", id1, id2)
        assertTrue("Client ID should start with prefix", 
            id1.startsWith("comfyui_go_android_"))
        assertTrue("Client ID should be valid length", id1.length > 20)
    }
    
    @Test
    fun `test URL validation works correctly`() {
        // 有效的 URLs
        assertTrue("Valid HTTP URL", "http://127.0.0.1:8188".isValidUrl())
        assertTrue("Valid HTTPS URL", "https://example.com:8188".isValidUrl())
        assertTrue("Valid IP with port", "http://192.168.1.100:8188".isValidUrl())
        
        // 无效的 URLs
        assertFalse("Invalid URL format", "not-a-url".isValidUrl())
        assertFalse("Missing protocol", "127.0.0.1:8188".isValidUrl())
        assertFalse("Empty string", "".isValidUrl())
        assertFalse("Invalid protocol", "ftp://example.com".isValidUrl())
    }
    
}
