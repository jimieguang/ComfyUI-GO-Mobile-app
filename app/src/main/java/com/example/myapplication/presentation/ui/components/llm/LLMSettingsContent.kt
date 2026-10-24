package com.example.myapplication.presentation.ui.components.llm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.util.LLMPreferences
import com.example.myapplication.util.LLMProvider
import com.example.myapplication.util.LLMApiStyle
import com.example.myapplication.presentation.viewmodel.LLMAssistantViewModel
import com.example.myapplication.data.model.TextOperationType
import com.example.myapplication.data.model.CustomRule
import java.util.UUID

/**
 * API 设置表单内容（可复用组件）
 */
@Composable
fun LLMSettingsContent(
    currentConfig: LLMPreferences.LLMConfig,
    onSave: (baseUrl: String, apiKey: String, modelName: String, temperature: Float, maxTokens: Int, apiStyle: String) -> Unit,
    onProviderChange: (String) -> Unit,
    llmViewModel: LLMAssistantViewModel,
    modifier: Modifier = Modifier
) {
    val operations by llmViewModel.operations.collectAsState()
    val disabledTypes by llmViewModel.disabledPresetTypes.collectAsState()
    val customRules by llmViewModel.customRules.collectAsState()
    
    var baseUrl by remember { mutableStateOf(currentConfig.baseUrl) }
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var modelName by remember { mutableStateOf(currentConfig.modelName) }
    var temperature by remember { mutableStateOf(currentConfig.temperature) }
    var maxTokens by remember { mutableStateOf(currentConfig.maxTokens) }
    var apiStyle by remember { mutableStateOf(currentConfig.apiStyle) }
    var showPassword by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    
    // 动态加载的模型列表（提升到顶层作用域，便于在测试按钮中访问）
    var dynamicModels by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    
    // 统一走 OpenAI 兼容协议，模型列表一律动态拉取（拉取失败时手动填写）
    val defaultModels: List<Pair<String, String>> = emptyList()
    
    // 实际使用的模型列表（优先使用动态加载的，否则使用默认）
    val models = if (dynamicModels.isNotEmpty()) dynamicModels else defaultModels

    // 当外部传入的配置（尤其是服务商）变化时，同步更新内部状态
    LaunchedEffect(currentConfig) {
        baseUrl = currentConfig.baseUrl
        apiKey = currentConfig.apiKey
        modelName = currentConfig.modelName
        temperature = currentConfig.temperature
        maxTokens = currentConfig.maxTokens
        apiStyle = currentConfig.apiStyle
    }
    
    // 当服务商变化时，清除测试结果（避免显示旧服务商的测试状态）
    LaunchedEffect(currentConfig.baseUrl, currentConfig.apiStyle) {
        testResult = null
        isTestingConnection = false
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题
        Text(
            text = "API 设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // 接口地址（自定义 provider）
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "接口地址",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            var urlFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .border(
                        width = 1.dp,
                        color = if (urlFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                BasicTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp)
                        .align(Alignment.CenterStart)
                        .onFocusChanged { urlFocused = it.isFocused }
                )
                if (baseUrl.isBlank()) {
                    Text(
                        text = "例如 https://api.openai.com/v1",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                    )
                }
            }
        }

        // API Key输入
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "API Key",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            
            // 胶囊样式输入框 + 测试按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var isFocused by remember { mutableStateOf(false) }
                
                // 矩形样式输入框
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .border(
                            width = 1.dp,
                            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    // 预留右侧显示/隐藏按钮的空间
                    val endPadding = 80.dp
                    BasicTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = endPadding)
                            .onFocusChanged { isFocused = it.isFocused }
                    )
                    
                    // 占位提示
                    if (apiKey.isBlank()) {
                        Text(
                            text = "输入 API Key",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                        )
                    }
                    
                    // 显示/隐藏按钮
                    TextButton(
                        onClick = { showPassword = !showPassword },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text(
                            text = if (showPassword) "隐藏" else "显示",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                
                // 测试连接按钮
                val coroutineScope = rememberCoroutineScope()
                Button(
                    onClick = {
                        // 验证模型是否有效，如果不在列表中则使用第一个有效模型
                        val finalModelName = if (models.any { it.first == modelName }) {
                            modelName
                        } else {
                            models.firstOrNull()?.first ?: modelName
                        }
                        
                        isTestingConnection = true
                        testResult = null
                        
                        // 使用协程确保保存完成后再测试
                        coroutineScope.launch {
                            // 先保存配置（等待完成）
                            onSave(baseUrl, apiKey, finalModelName, temperature, maxTokens, apiStyle)
                            
                            // 等待一小段时间确保 DataStore 写入完成
                            delay(100)
                            
                            // 再执行测试
                            llmViewModel.testConnection(currentConfig.provider) { success, message ->
                                isTestingConnection = false
                                testResult = message
                            }
                        }
                    },
                    enabled = apiKey.isNotBlank() && !isTestingConnection,
                    modifier = Modifier.height(40.dp)
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        if (isTestingConnection) "测试中" else "测试",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // 测试结果提示
            if (testResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (testResult!!.contains("成功")) 
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else 
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = testResult!!,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = if (testResult!!.contains("成功"))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        // 模型选择
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "模型",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            
            var showModelDialog by remember { mutableStateOf(false) }
            
            // 当接口地址/Key 变化时，尝试动态加载模型列表
            LaunchedEffect(baseUrl, currentConfig.apiKey) {
                if (apiStyle == LLMApiStyle.OPENAI && baseUrl.isNotBlank() && currentConfig.apiKey.isNotBlank()) {
                    isLoadingModels = true
                    llmViewModel.fetchAvailableModels(LLMProvider.CUSTOM) { success, fetchedModels ->
                        isLoadingModels = false
                        if (success && fetchedModels.isNotEmpty()) {
                            dynamicModels = fetchedModels
                            
                            // 验证当前选择的模型是否在新列表中
                            val isCurrentModelValid = fetchedModels.any { it.first == modelName }
                            if (!isCurrentModelValid && fetchedModels.isNotEmpty()) {
                                // 如果当前模型不在列表中，自动选择第一个模型
                                modelName = fetchedModels.first().first
                                android.util.Log.d("LLMSettingsContent", 
                                    "当前模型 $modelName 不在对话模型列表中，已重置为: ${fetchedModels.first().first}")
                            }
                        }
                    }
                } else {
                    // 未配置或非 OpenAI 兼容：清空动态列表，模型名由用户手动填写
                    dynamicModels = emptyList()
                }
            }
            
            // 矩形样式的选择框
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(
                        onClick = { showModelDialog = true },
                        enabled = !isLoadingModels,
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    )
            ) {
                if (isLoadingModels) {
                    // 显示加载指示器
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "加载模型列表...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = models.find { it.first == modelName }?.second ?: modelName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp, end = 40.dp)
                    )
                    
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "展开",
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 全屏模态选择器
            if (showModelDialog) {
                ModelSelectDialog(
                    models = models,
                    selectedModel = modelName,
                    onModelSelected = { 
                        modelName = it
                        showModelDialog = false
                    },
                    onDismiss = { showModelDialog = false }
                )
            }

            // 列表拉取不到时（未配置 Key / 端点不支持 /models）允许手动填写模型名
            if (models.isEmpty() && !isLoadingModels) {
                var modelFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .border(
                            width = 1.dp,
                            color = if (modelFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    BasicTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp)
                            .align(Alignment.CenterStart)
                            .onFocusChanged { modelFocused = it.isFocused }
                    )
                    if (modelName.isBlank()) {
                        Text(
                            text = "输入模型名，例如 gpt-4o-mini",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                        )
                    }
                }
            }

            // 调试信息：显示模型来源和数量
            if (!isLoadingModels) {
                val debugInfo = if (dynamicModels.isNotEmpty()) {
                    "✓ 从API动态获取了 ${dynamicModels.size} 个模型"
                } else if (apiStyle == LLMApiStyle.OPENAI && baseUrl.isNotBlank() && currentConfig.apiKey.isNotBlank()) {
                    "⚠ API获取失败，使用默认 ${defaultModels.size} 个模型"
                } else {
                    "📋 使用默认 ${defaultModels.size} 个模型"
                }
                
                Text(
                    text = debugInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (dynamicModels.isNotEmpty()) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
        
        // Temperature输入
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "温度",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            
            var isFocused by remember { mutableStateOf(false) }
            
            // 矩形样式输入框
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .border(
                        width = 1.dp,
                        color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                BasicTextField(
                    value = temperature.toString(),
                    onValueChange = { 
                        it.toFloatOrNull()?.let { value -> 
                            if (value >= 0f && value <= 2f) {
                                temperature = value
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .onFocusChanged { isFocused = it.isFocused }
                )
            }
            
            Text(
                text = "控制输出随机性（0.0-2.0，推荐0.0-1.5）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Max Tokens输入
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "最大Token数",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            
            var isFocused by remember { mutableStateOf(false) }
            
            // 矩形样式输入框
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .border(
                        width = 1.dp,
                        color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                BasicTextField(
                    value = maxTokens.toString(),
                    onValueChange = { 
                        it.toIntOrNull()?.let { value -> maxTokens = value }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .onFocusChanged { isFocused = it.isFocused }
                )
            }
        }
        
        // ===== 预设管理 =====
        // 增加顶部间距，并在标题前增加装饰性分隔
        HorizontalDivider(
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "预设管理",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            var showAddDialog by remember { mutableStateOf(false) }
            // 优化添加按钮样式：使用 TextButton 以减少视觉干扰，颜色使用 Primary
            TextButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加", style = MaterialTheme.typography.labelMedium)
            }
            
            // 添加自定义预设对话框（简化版）
            if (showAddDialog) {
                var newName by remember { mutableStateOf("") }
                var newPrompt by remember { mutableStateOf("") }
                
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text("添加自定义预设", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("预设名称 *") },
                                placeholder = { Text("例如：提取关键词、生成摘要") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = newPrompt,
                                onValueChange = { newPrompt = it },
                                label = { Text("处理指令 *") },
                                placeholder = { Text("例如：提取文本中的关键词") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 6,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                llmViewModel.addCustomRule(CustomRule(
                                    id = UUID.randomUUID().toString(),
                                    name = newName.trim(),
                                    icon = "✨",  // 统一使用默认图标
                                    systemPrompt = "你是一个专业的文本处理助手。",  // 默认系统提示
                                    userPromptTemplate = "${newPrompt.trim()}\n\n文本内容：{text}",  // 自动构建模板
                                    createdAt = System.currentTimeMillis()
                                ))
                                showAddDialog = false
                            },
                            enabled = newName.isNotBlank() && newPrompt.isNotBlank(),
                            shape = RoundedCornerShape(20.dp)
                        ) { Text("添加", fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showAddDialog = false },
                            shape = RoundedCornerShape(20.dp)
                        ) { Text("取消") }
                    }
                )
            }
        }

        // 统一的预设列表
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // 内置预设
            val builtinPresets = listOf(
                Triple(TextOperationType.TRANSLATE_ZH_TO_EN, "🌐 中译英", true),
                Triple(TextOperationType.TRANSLATE_EN_TO_ZH, "🌐 英译中", true)
            )
            
            builtinPresets.forEach { (type, displayName, isTranslate) ->
                val isDisabled = disabledTypes.contains(type)
                // 使用 OutlinedCard 替代 Card，背景使用浅蓝色，增强视觉层级
                // 解决在浅蓝底色上叠加白底卡片造成的视觉突兀问题
                Card(
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        // 修正：不再使用 primaryContainer (可能偏紫)，改用明确的深一点的淡蓝色
                        containerColor = if (isDisabled) 
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        else 
                            Color(0xFFCFE5FF)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        // 恢复：描边颜色恢复为原来的灰色
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp, vertical = 0.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDisabled) 
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                            if (isTranslate) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "内置",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        if (!isTranslate) {
                            Switch(
                                checked = !isDisabled,
                                onCheckedChange = { enabled ->
                                    val newSet = disabledTypes.toMutableSet()
                                    if (enabled) newSet.remove(type) else newSet.add(type)
                                    llmViewModel.setDisabledPresetTypes(newSet)
                                }
                            )
                        }
                    }
                }
            }
            
            // 自定义预设
            customRules.forEach { rule ->
                var showEditDialog by remember { mutableStateOf(false) }
                
                Card(
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        // 修正：不再使用 primaryContainer (可能偏紫)，改用明确的深一点的淡蓝色
                        containerColor = if (!rule.isEnabled)
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        else
                            Color(0xFFCFE5FF)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        // 恢复：描边颜色恢复为原来的灰色
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp, vertical = 0.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${rule.icon} ${rule.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!rule.isEnabled)
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "自定义",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { showEditDialog = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit, 
                                    contentDescription = "编辑",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { llmViewModel.deleteCustomRule(rule.id) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete, 
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Switch(
                                checked = rule.isEnabled,
                                onCheckedChange = { enabled ->
                                    llmViewModel.toggleCustomRuleEnabled(rule.id, enabled)
                                }
                            )
                        }
                    }
                }
                
                // 编辑预设对话框（简化版）
                if (showEditDialog) {
                    var editName by remember { mutableStateOf(rule.name) }
                    // 从模板中提取原始指令（去掉 "\n\n文本内容：{text}" 部分）
                    val originalPrompt = rule.userPromptTemplate.replace("\n\n文本内容：{text}", "")
                    var editPrompt by remember { mutableStateOf(originalPrompt) }
                    
                    AlertDialog(
                        onDismissRequest = { showEditDialog = false },
                        title = { Text("✏️ 编辑自定义预设", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("预设名称 *") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = editPrompt,
                                    onValueChange = { editPrompt = it },
                                    label = { Text("处理指令 *") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    maxLines = 6,
                                    supportingText = {
                                        Text("描述你希望AI如何处理文本")
                                    }
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    llmViewModel.updateCustomRule(rule.copy(
                                        name = editName.trim(),
                                        userPromptTemplate = "${editPrompt.trim()}\n\n文本内容：{text}"
                                    ))
                                    showEditDialog = false
                                },
                                enabled = editName.isNotBlank() && editPrompt.isNotBlank(),
                                shape = RoundedCornerShape(20.dp)
                            ) { Text("保存", fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showEditDialog = false },
                                shape = RoundedCornerShape(20.dp)
                            ) { Text("取消") }
                        }
                    )
                }
            }
        }
        
        // 保存按钮
        Button(
            onClick = {
                onSave(baseUrl, apiKey, modelName, temperature, maxTokens, apiStyle)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = apiKey.isNotBlank(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("保存配置", fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 模型选择全屏弹窗
 * 支持大量模型的滚动显示（如硅基流动的125个模型）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectDialog(
    models: List<Pair<String, String>>,
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .statusBarsPadding()
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // 中心选择器卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
                    .heightIn(max = 600.dp) // 最大高度限制
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { /* 阻止点击穿透到背景 */ },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // 标题栏
                    Text(
                        text = "选择模型 (${models.size}个)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 使用 LazyColumn 实现可滚动列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(models.size) { index ->
                            val (value, label) = models[index]
                            ModelSelectionItem(
                                text = label,
                                isSelected = value == selectedModel,
                                onClick = { onModelSelected(value) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 模型选择项组件
 */
@Composable
private fun ModelSelectionItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) 
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null,
                modifier = Modifier.size(20.dp),
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primary
                else 
                    MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

