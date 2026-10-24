package com.example.myapplication.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.presentation.viewmodel.ServerConnectionViewModel
import com.example.myapplication.presentation.viewmodel.LLMAssistantViewModel
import com.example.myapplication.data.model.ConnectionState
import com.example.myapplication.presentation.ui.components.ServerConnectionCard
import com.example.myapplication.presentation.ui.components.SystemStatsCard
import com.example.myapplication.presentation.ui.components.llm.LLMSettingsContent
import com.example.myapplication.util.LLMPreferences
import com.example.myapplication.util.LLMProvider

/**
 * 统一设置界面
 * 整合 ComfyUI 服务器连接 + API 设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    serverViewModel: ServerConnectionViewModel,
    llmViewModel: LLMAssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val serverUiState by serverViewModel.uiState.collectAsStateWithLifecycle()
    
    // 从 DataStore 获取 LLM 配置
    val llmConfig by llmViewModel.llmConfig.collectAsState()
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color(0xFFF5F5F5)
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5AA9E6),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            // ===== ComfyUI 服务器连接 =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE0EFFF)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
            ServerConnectionCard(
                uiState = serverUiState,
                onServerUrlChange = { serverViewModel.updateServerUrl(it) },
                onConnect = { serverViewModel.connectToServer() },
                onDisconnect = { serverViewModel.disconnectFromServer() },
                onClearError = { serverViewModel.clearError() },
                alwaysExpanded = true  // 在设置界面中始终展开
            )
                }
            }
            
            // ===== 系统资源监控 =====
            // 只在服务器连接成功时显示
            if (serverUiState.connectionState == ConnectionState.CONNECTED) {
                SystemStatsCard(
                    stats = serverUiState.systemStats,
                    isAvailable = serverUiState.isSystemStatsAvailable,
                    isLoading = serverUiState.isSystemStatsLoading,
                    onRefresh = { serverViewModel.refreshSystemStats() },
                    onStartPolling = { /* 由 ViewModel 自动管理 */ },
                    onStopPolling = { /* 不停止，保持全局轮询 */ }
                )
            }

            // ===== API 设置 =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE0EFFF)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
            LLMSettingsContent(
                currentConfig = llmConfig,
                onSave = { baseUrl, apiKey, modelName, temperature, maxTokens, apiStyle ->
                    llmViewModel.saveCustomConfig(baseUrl, apiKey, modelName, apiStyle)
                    llmViewModel.saveModelConfig(modelName, temperature, maxTokens)
                    android.widget.Toast.makeText(context, "配置已保存", android.widget.Toast.LENGTH_SHORT).show()
                },
                onProviderChange = { provider ->
                    llmViewModel.saveProvider(provider)
                },
                llmViewModel = llmViewModel
            )
                }
            }
        }
    }
}
