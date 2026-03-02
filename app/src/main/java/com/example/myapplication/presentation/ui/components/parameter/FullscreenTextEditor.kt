package com.example.myapplication.presentation.ui.components.parameter

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.snapshotFlow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.presentation.viewmodel.LLMAssistantViewModel
import com.example.myapplication.presentation.ui.components.llm.*

/**
 * 全屏文本编辑器对话框
 * 用于编辑长文本（如提示词）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenTextEditorDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isPrompt: Boolean = false
) {
    var editedText by remember { mutableStateOf(text) }
    val wordCount = editedText.length
    val lineCount = editedText.count { it == '\n' } + 1
    
    // LLM助手集成
    val llmViewModel: LLMAssistantViewModel = hiltViewModel()
    val llmUiState by llmViewModel.uiState.collectAsStateWithLifecycle()
    val hasApiKey by llmViewModel.hasApiKey.collectAsStateWithLifecycle()
    val llmOperations by llmViewModel.operations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var showAiMenu by remember { mutableStateOf(false) }
    
    // 获取屏幕和键盘信息
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenWidthDp = configuration.screenWidthDp.dp
    
    val imeInsets = WindowInsets.ime
    val imeHeightDp = with(density) { imeInsets.getBottom(this).toDp() }
    
    // 计算 Card 需要向上移动的距离
    // 公式：让 Card 底部距离键盘顶部保持 16dp 的间距
    val cardHeightRatio = 0.7f
    val cardGap = 16.dp  // Card 和键盘之间的间距
    val targetOffsetY = if (imeHeightDp > 0.dp) {
        // Card 在屏幕中心时，底部距离屏幕底部的距离
        val cardBottomToScreenBottom = screenHeightDp * (1 - cardHeightRatio) / 2
        // 键盘弹起后，Card 需要向上移动的距离 = 键盘高度 - Card底部原本的距离 + 间距
        -(imeHeightDp - cardBottomToScreenBottom + cardGap)
    } else {
        0.dp
    }
    
    // 添加平滑动画
    val cardOffsetY by animateDpAsState(
        targetValue = targetOffsetY,
        animationSpec = tween(durationMillis = 300),  // 300ms 平滑动画
        label = "cardOffset"
    )
    
    // 调试日志 - 打印所有关键尺寸
    LaunchedEffect(imeHeightDp) {
        Log.d("FullscreenTextEditor", "=" .repeat(60))
        Log.d("FullscreenTextEditor", "屏幕宽度: $screenWidthDp")
        Log.d("FullscreenTextEditor", "屏幕高度: $screenHeightDp")
        Log.d("FullscreenTextEditor", "键盘高度: $imeHeightDp")
        Log.d("FullscreenTextEditor", "Card 偏移量: $cardOffsetY")
        Log.d("FullscreenTextEditor", "=" .repeat(60))
    }
    
    Dialog(
        onDismissRequest = {
            onConfirm(editedText)  // 关闭时自动保存
            onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // 黑色半透明背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .statusBarsPadding()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onConfirm(editedText)  // 点击外部也保存
                    onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
             // 中心编辑器卡片 - 键盘弹起时向上移动，保持 16dp 间距
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.7f)
                    .offset(y = cardOffsetY)  // 动态计算的偏移量
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* 阻止点击穿透到背景 */ },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 标题栏（带AI助手和撤回按钮）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isPrompt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$wordCount 字符 · $lineCount 行",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 撤回按钮
                            if (llmUiState.lastOperation != null && llmUiState.targetParamId == null) {
                                OutlinedButton(
                                    onClick = {
                                        val originalText = llmViewModel.undoLastOperation()
                                        if (originalText != null) {
                                            editedText = originalText
                                            Toast.makeText(context, "已撤回", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isPrompt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, 
                                        if (isPrompt) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "撤回",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("撤回")
                                }
                            }
                            
                            // AI助手按钮
                            OutlinedButton(
                                onClick = {
                                    showAiMenu = true
                                },
                                enabled = !llmUiState.isLoading,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isPrompt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, 
                                    if (isPrompt) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            ) {
                                // 显示加载状态或正常图标
                                if (llmUiState.isLoading && llmUiState.targetParamId == null) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = if (isPrompt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (llmUiState.isLoading && llmUiState.targetParamId == null) "处理中..." else "AI助手")
                            }
                            
                            // AI操作选择对话框（全屏样式）
                            if (showAiMenu) {
                                AIOperationSelectDialog(
                                    operations = llmOperations,
                                    onOperationSelected = { operation ->
                                        showAiMenu = false
                                        if (editedText.isBlank()) {
                                            Toast.makeText(context, "请先输入文本", Toast.LENGTH_SHORT).show()
                                        } else {
                                            llmViewModel.executeOperation(
                                                operation = operation,
                                                inputText = editedText
                                            )
                                        }
                                    },
                                    onDismiss = { showAiMenu = false }
                                )
                            }
                        }
                    }
                    
                    // 监听AI操作结果，自动替换文本
                    LaunchedEffect(llmUiState.resultText, llmUiState.isLoading) {
                        if (!llmUiState.isLoading && llmUiState.resultText.isNotBlank() && llmUiState.targetParamId == null) {
                            editedText = llmUiState.resultText
                            Toast.makeText(context, "已替换为AI处理结果", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 文本编辑区域（支持垂直滚动和自动滚动，底部对齐键盘顶部）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(
                                color = if (isPrompt) Color(0xFFE8F3FF) else Color(0xFFF5F5F5),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        val scrollState = rememberScrollState()
                        val coroutineScope = rememberCoroutineScope()
                        
                        // 监听文本变化和键盘高度，延迟滚动以等待布局完成
                        LaunchedEffect(editedText, imeHeightDp) {
                            if (imeHeightDp > 0.dp) {
                                // 等待布局完成后再滚动
                                kotlinx.coroutines.delay(50)
                                val maxValue = scrollState.maxValue
                                val currentValue = scrollState.value
                                
                                Log.d("FullscreenTextEditor", "-" . repeat(60))
                                Log.d("FullscreenTextEditor", "滚动信息:")
                                Log.d("FullscreenTextEditor", "  maxValue (可滚动范围): $maxValue px")
                                Log.d("FullscreenTextEditor", "  currentValue (当前位置): $currentValue px")
                                Log.d("FullscreenTextEditor", "  文本长度: ${editedText.length} 字符")
                                Log.d("FullscreenTextEditor", "  底部padding: $imeHeightDp")
                                Log.d("FullscreenTextEditor", "-" .repeat(60))
                                
                                if (maxValue > 0) {
                                    scrollState.animateScrollTo(maxValue)
                                    Log.d("FullscreenTextEditor", "✓ 滚动完成 - 新位置: ${scrollState.value} px")
                                } else {
                                    Log.d("FullscreenTextEditor", "✗ 无需滚动 (maxValue = 0)")
                                }
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(16.dp)
                        ) {
                            // 文本输入框
                            BasicTextField(
                                value = editedText,
                                onValueChange = { 
                                    editedText = it
                                },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 24.sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) { innerTextField ->
                                Box {
                                    if (editedText.isEmpty()) {
                                        Text(
                                            text = if (isPrompt) "输入提示词..." else "输入$title...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // LLM结果对话框
        if (llmUiState.showResultDialog) {
            LLMResultDialog(
                originalText = llmUiState.originalText,
                resultText = llmUiState.resultText,
                operationName = llmUiState.currentOperation?.displayName ?: "",
                tokenUsage = llmUiState.tokenUsage,
                onReplace = {
                    editedText = llmUiState.resultText
                },
                onAppend = {
                    val combined = if (editedText.isBlank()) {
                        llmUiState.resultText
                    } else {
                        "$editedText\n${llmUiState.resultText}"
                    }
                    editedText = combined
                },
                onDismiss = { llmViewModel.dismissResultDialog() }
            )
        }
        
        // LLM加载对话框已移除，改为在按钮上显示加载状态
        
        // 错误提示
        llmUiState.errorMessage?.let { error ->
            LaunchedEffect(error) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                llmViewModel.clearError()
            }
        }
    }
}

/**
 * AI操作选择对话框（全屏样式，与参数选择器一致）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIOperationSelectDialog(
    operations: List<com.example.myapplication.data.model.TextOperation>,
    onOperationSelected: (com.example.myapplication.data.model.TextOperation) -> Unit,
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
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // 中心选择器卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.7f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* 阻止点击穿透到背景 */ },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 标题栏
                    Text(
                        text = "选择AI操作",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 操作列表（支持滚动）
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(operations.size) { index ->
                            val operation = operations[index]
                            AIOperationItem(
                                icon = operation.icon,
                                text = operation.displayName,
                                onClick = {
                                    onOperationSelected(operation)
                                }
                            )
                        }
                        
                        if (operations.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无可用的AI操作",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * AI操作项组件
 */
@Composable
private fun AIOperationItem(
    icon: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 图标
            Text(
                text = icon,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.size(24.dp),
                textAlign = TextAlign.Center
            )
            // 操作名称
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            // 右侧箭头
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

