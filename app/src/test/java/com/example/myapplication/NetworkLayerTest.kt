package com.example.myapplication

import com.example.myapplication.data.api.ComfyApiService
import com.example.myapplication.data.websocket.ComfyWebSocketManager
import com.example.myapplication.util.Constants
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/**
 * 验证网络层实现
 */
class NetworkLayerTest {
    
    private lateinit var mockServer: MockWebServer
    private lateinit var apiService: ComfyApiService
    private lateinit var webSocketManager: ComfyWebSocketManager
    
    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(mockServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            
        apiService = retrofit.create(ComfyApiService::class.java)
        webSocketManager = ComfyWebSocketManager(Json { ignoreUnknownKeys = true })
    }
    
    @After
    fun teardown() {
        mockServer.shutdown()
    }
    
    @Test
    fun `test API service interface is properly defined`() = runTest {
        // 测试 API 服务接口定义正确
        assertNotNull("API service should be instantiated", apiService)
    }
    
    @Test
    fun `test WebSocket manager initialization`() {
        assertNotNull("WebSocket manager should be instantiable", webSocketManager)
        assertFalse("Should not be connected initially", webSocketManager.isConnected())
        val stats = webSocketManager.getConnectionStats()
        assertNotNull("Connection stats should be available", stats)
    }
    
    @Test
    fun `test API endpoints constants are defined`() {
        assertEquals("Object info endpoint", "/object_info", Constants.ApiEndpoints.OBJECT_INFO)
        assertEquals("Prompt endpoint", "/prompt", Constants.ApiEndpoints.PROMPT)
        assertEquals("Upload endpoint", "/upload/image", Constants.ApiEndpoints.UPLOAD_IMAGE)
    }
    
    @Test
    fun `test WebSocket message types are defined`() {
        assertEquals("Status message type", "status", Constants.WebSocketMessageType.STATUS)
        assertEquals("Progress message type", "progress", Constants.WebSocketMessageType.PROGRESS)
        assertEquals("Executing message type", "executing", Constants.WebSocketMessageType.EXECUTING)
        assertEquals("Executed message type", "executed", Constants.WebSocketMessageType.EXECUTED)
    }
}
