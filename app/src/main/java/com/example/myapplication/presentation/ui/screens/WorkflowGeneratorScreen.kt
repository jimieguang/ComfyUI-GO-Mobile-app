package com.example.myapplication.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColor
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.presentation.ui.components.ServerConnectionCard
import com.example.myapplication.presentation.ui.components.WorkflowImportCard
import com.example.myapplication.presentation.viewmodel.ServerConnectionViewModel
import com.example.myapplication.presentation.viewmodel.WorkflowImportViewModel
import com.example.myapplication.presentation.viewmodel.WorkflowParserViewModel
import com.example.myapplication.presentation.viewmodel.LLMAssistantViewModel
import com.example.myapplication.presentation.viewmodel.SavedWorkflowsViewModel
import com.example.myapplication.presentation.viewmodel.ExecutionHistoryViewModel
import com.example.myapplication.data.model.SavedWorkflow
import java.util.UUID
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import coil.compose.AsyncImage
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.presentation.ui.components.ImageMaskEditor
import com.example.myapplication.util.ImageUtils
import com.example.myapplication.util.MediaSaveUtils
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.example.myapplication.presentation.ui.model.MediaItem
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

/**
 * GitHub 官方 Logo 矢量图标
 */
private val GitHubIcon: ImageVector
    get() {
        if (_gitHubIcon != null) {
            return _gitHubIcon!!
        }
        _gitHubIcon = ImageVector.Builder(
            name = "GitHub",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Butt,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                // GitHub Logo Path
                moveTo(12f, 0.297f)
                curveToRelative(-6.63f, 0f, -12f, 5.373f, -12f, 12f)
                curveToRelative(0f, 5.303f, 3.438f, 9.8f, 8.205f, 11.385f)
                curveToRelative(0.6f, 0.113f, 0.82f, -0.258f, 0.82f, -0.577f)
                curveToRelative(0f, -0.285f, -0.01f, -1.04f, -0.015f, -2.04f)
                curveToRelative(-3.338f, 0.724f, -4.042f, -1.61f, -4.042f, -1.61f)
                curveTo(4.422f, 18.07f, 3.633f, 17.7f, 3.633f, 17.7f)
                curveToRelative(-1.087f, -0.744f, 0.084f, -0.729f, 0.084f, -0.729f)
                curveToRelative(1.205f, 0.084f, 1.838f, 1.236f, 1.838f, 1.236f)
                curveToRelative(1.07f, 1.835f, 2.809f, 1.305f, 3.495f, 0.998f)
                curveToRelative(0.108f, -0.776f, 0.417f, -1.305f, 0.76f, -1.605f)
                curveToRelative(-2.665f, -0.3f, -5.466f, -1.332f, -5.466f, -5.93f)
                curveToRelative(0f, -1.31f, 0.465f, -2.38f, 1.235f, -3.22f)
                curveToRelative(-0.135f, -0.303f, -0.54f, -1.523f, 0.105f, -3.176f)
                curveToRelative(0f, 0f, 1.005f, -0.322f, 3.3f, 1.23f)
                curveToRelative(0.96f, -0.267f, 1.98f, -0.399f, 3f, -0.405f)
                curveToRelative(1.02f, 0.006f, 2.04f, 0.138f, 3f, 0.405f)
                curveToRelative(2.28f, -1.552f, 3.285f, -1.23f, 3.285f, -1.23f)
                curveToRelative(0.645f, 1.653f, 0.24f, 2.873f, 0.12f, 3.176f)
                curveToRelative(0.765f, 0.84f, 1.23f, 1.91f, 1.23f, 3.22f)
                curveToRelative(0f, 4.61f, -2.805f, 5.625f, -5.475f, 5.92f)
                curveToRelative(0.42f, 0.36f, 0.81f, 1.096f, 0.81f, 2.22f)
                curveToRelative(0f, 1.606f, -0.015f, 2.896f, -0.015f, 3.286f)
                curveToRelative(0f, 0.315f, 0.21f, 0.69f, 0.825f, 0.57f)
                curveTo(20.565f, 22.092f, 24f, 17.592f, 24f, 12.297f)
                curveToRelative(0f, -6.627f, -5.373f, -12f, -12f, -12f)
            }
        }.build()
        return _gitHubIcon!!
    }

private var _gitHubIcon: ImageVector? = null

/**
 * 工作流执行页面
 * 对应Web版的主页面功能：
 * - 服务器连接配置
 * - 工作流导入
 * - 参数设置
 * - 执行控制
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowGeneratorScreen(
    onNavigateToParameterEdit: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    serverViewModel: ServerConnectionViewModel = hiltViewModel(),
    importViewModel: WorkflowImportViewModel = hiltViewModel(),
    parserViewModel: WorkflowParserViewModel,
    savedVm: SavedWorkflowsViewModel = hiltViewModel(),
    historyViewModel: ExecutionHistoryViewModel = hiltViewModel()
) {
    val serverUiState by serverViewModel.uiState.collectAsStateWithLifecycle()
    val importUiState by importViewModel.uiState.collectAsStateWithLifecycle()
    val parserUiState by parserViewModel.uiState.collectAsStateWithLifecycle()
    // AI助手（用于在重新加载/清空工作流时清空上一次AI结果，避免串到新工作流）
    val llmViewModel: LLMAssistantViewModel = hiltViewModel()
    val context = LocalContext.current

    // 清理提示：显示一会儿自动消失（不影响 errorMessage）
    LaunchedEffect(serverUiState.statusMessage) {
        if (serverUiState.statusMessage.isNotBlank()) {
            delay(1500)
            serverViewModel.clearStatusMessage()
        }
    }
    
    // 根据连接状态判断是否已连接
    val isServerConnected = serverUiState.connectionState == com.example.myapplication.data.model.ConnectionState.CONNECTED
    // 系统资源监控由 ViewModel 在连接成功时自动启动，无需页面控制
    val hasWorkflowLoaded = importUiState.hasWorkflow
    val hasWorkflowParsed = parserUiState.hasParsedWorkflow
    
    // 当工作流导入成功后自动解析（智能检测版本 - 避免重复解析）
    LaunchedEffect(importUiState.currentWorkflow, importUiState.hasWorkflow) {
        val currentWorkflow = importUiState.currentWorkflow
        
        if (currentWorkflow != null) {
            // ✅ 重新加载/导入工作流：清空AI助手的上一次结果/撤回记录，并取消可能仍在进行的AI任务
            llmViewModel.resetForWorkflowReload()
            // 一次性清空：仅当导入层明确要求清空时执行，并立即消费该标记
            if (importUiState.clearQuickParamsOnce) {
                parserViewModel.clearQuickParams()
                // 消费标记：避免后续状态变化再次清空
                importViewModel.consumeClearQuickParamsOnce()
            }
            // 有工作流时，让ViewModel自己判断是否需要重新解析
            // ✅ ViewModel内部会根据 shouldPreserveQuickParams 标记来决定是否保留快速参数
            val serverUrl = if (isServerConnected) serverUiState.serverUrl else null
            parserViewModel.parseWorkflow(currentWorkflow, serverUrl)
        } else if (!importUiState.hasWorkflow && parserUiState.hasParsedWorkflow) {
            // ✅ 工作流被清除：同样清空AI助手状态，避免旧结果残留
            llmViewModel.resetForWorkflowReload()
            // 只有在工作流被明确清除时才清除解析结果
            parserViewModel.clearParseResult()
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 页面标题和作者信息
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp) // 原为 16.dp，增加 10dp 至 26.dp
            ) {
                // 标题行（包含标题和设置按钮）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 页面标题
                    Text(
                        text = "COMFYUI GO",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5AA9E6)
                    )
                    
                    // 设置按钮
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = Color(0xFF5AA9E6),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // GitHub 作者标识
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                
                // 点击时的缩放动画
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1f,
                    animationSpec = tween(durationMillis = 100),
                    label = "scale"
                )
                
                Row(
                    modifier = Modifier
                        .scale(scale)
                        .background(
                            color = if (isPressed) Color(0xFFE0E0E0) else Color(0xFFF5F5F5),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable(
                            indication = null,
                            interactionSource = interactionSource
                        ) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/huafitwjb"))
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        imageVector = GitHubIcon,
                        contentDescription = "GitHub",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF666666)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "由 huafit 制作",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                }
            }
            
            // 工作流导入卡片
            WorkflowImportCard(
                uiState = importUiState,
                onFileImport = importViewModel::importFromFile,
                onClipboardImport = importViewModel::importFromClipboard,
                onClearWorkflow = importViewModel::clearWorkflow,
                onSaveWorkflow = {
                    val json = importUiState.currentWorkflow
                    val name = importUiState.workflowName ?: "工作流${System.currentTimeMillis()}"
                    if (json != null) {
                        val item = SavedWorkflow(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            json = json,
                            timestamp = System.currentTimeMillis(),
                            source = "GENERATOR",
                            quickParams = parserViewModel.getCurrentQuickParams()  // ✅ 保存快速参数配置
                        )
                        savedVm.save(item)
                        Toast.makeText(context, "已保存到工作流库", Toast.LENGTH_SHORT).show()
                    }
                },
                onClearError = importViewModel::clearError,
                onConfirmClipboardName = importViewModel::confirmClipboardWorkflowName,
                onCancelClipboardImport = importViewModel::cancelClipboardWorkflowImport
            )
            
            // 参数设置区域：仅保留"编辑参数"按钮 + 保存到库
            if (hasWorkflowParsed) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE0EFFF)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 8.dp)
                    ) {
                        // 编辑参数：整行
                        Button(
                            onClick = onNavigateToParameterEdit,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !parserUiState.isParsing && parserUiState.nodeInstances.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                contentColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("编辑参数")
                        }

                        Spacer(Modifier.height(4.dp))

                        // 第二排：清理内存 + 清理显存
                        val connected = serverUiState.connectionState == com.example.myapplication.data.model.ConnectionState.CONNECTED
                        val cleanTextColor = MaterialTheme.colorScheme.onSurface
                        val memInteraction = remember { MutableInteractionSource() }
                        val memPressed by memInteraction.collectIsPressedAsState()
                        val vramInteraction = remember { MutableInteractionSource() }
                        val vramPressed by vramInteraction.collectIsPressedAsState()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { serverViewModel.freeMemory() },
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer(alpha = if (memPressed) 0.75f else 1f),
                                enabled = connected,
                                interactionSource = memInteraction,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = cleanTextColor
                                )
                            ) {
                                Text("清理内存")
                            }
                            OutlinedButton(
                                onClick = { serverViewModel.unloadModels() },
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer(alpha = if (vramPressed) 0.75f else 1f),
                                enabled = connected,
                                interactionSource = vramInteraction,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = cleanTextColor
                                )
                            ) {
                                Text("清理显存")
                            }
                        }

                        // RAM/VRAM 进度条区域
                        val stats = serverUiState.systemStats
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // 第一行：两个进度条并排
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // RAM 进度条（绿色）
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFF4CAF50).copy(alpha = 0.2f))
                                ) {
                                    val ramPercent = stats?.ram_percent ?: 0f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = (ramPercent / 100f).coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .background(Color(0xFF4CAF50), RoundedCornerShape(3.dp))
                                    )
                                }
                                // VRAM 进度条（橙色）
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFFFF9800).copy(alpha = 0.2f))
                                ) {
                                    val vramPercent = stats?.gpu_percent ?: 0f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = (vramPercent / 100f).coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .background(Color(0xFFFF9800), RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                            // 第二行：RAM/VRAM 标签 + 数值
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // RAM 标签 + 数值
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "RAM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        text = "%.1fGB".format(stats?.ram_used_gb ?: 0f),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // VRAM 标签 + 数值
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "VRAM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFFF9800)
                                    )
                                    Text(
                                        text = "%.1f/%.0fGB".format(stats?.gpu_used_gb ?: 0f, stats?.gpu_total_gb ?: 0f),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (serverUiState.statusMessage.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = serverUiState.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 快速参数托盘：仅在存在至少一个已选参数时展示
                        val quickByNode = parserUiState.quickParamsByNode
                        val hasAnyQuickParam = remember(quickByNode) {
                            quickByNode.values.any { it.isNotEmpty() }
                        }
                        if (hasAnyQuickParam) {
                            Spacer(Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "快速参数",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                TextButton(onClick = { parserViewModel.clearQuickParams() }) {
                                    Text("清空全部")
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            // 记录每组展开状态
                            val groupExpanded: SnapshotStateMap<String, Boolean> = remember { mutableStateMapOf() }
                            var isFirstGroup = true
                            quickByNode.forEach { (nodeId, paramIds) ->
                                if (paramIds.isNotEmpty()) {
                                    if (!isFirstGroup) {
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFFE0EFFF)
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            // 节点标题
                                            val node = parserUiState.nodeInstances.firstOrNull { it.nodeId == nodeId }
                                            val nodeTitle = node?.title ?: "节点 $nodeId"
                                            val nodeBadge = node?.classType ?: ""
                                            val expanded = groupExpanded[nodeId] ?: true
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    IconButton(onClick = { groupExpanded[nodeId] = !(groupExpanded[nodeId] ?: true) }, modifier = Modifier.size(30.dp)) {
                                                        Icon(
                                                            if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Column {
                                                        Text(nodeTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                                    }
                                                }
                                                // 顶部已提供“清空全部”，组头不再显示
                                            }

                                            if (expanded) {
                                                // 渲染选中的每个参数（复用高级输入组件，UI一致）
                                                paramIds.forEach { pid ->
                                                    val param = parserUiState.parseResult?.editableParams?.firstOrNull { it.id == pid }
                                                    val value = parserUiState.parameterValues[pid] ?: param?.value
                                                    if (param != null && value != null) {
                                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                            com.example.myapplication.presentation.ui.components.AdvancedParameterInputField(
                                                                parameter = param,
                                                                currentValue = value,
                                                                onValueChange = { newVal -> parserViewModel.updateParameterValue(pid, newVal) },
                                                                mediaCallbacks = com.example.myapplication.presentation.ui.components.MediaCallbacks(
                                                                    onStageImage = parserViewModel::stageImageForParam,
                                                                    getStagedImageBytes = parserViewModel::getStagedImageBytes,
                                                                    onEditMask = parserViewModel::startMaskEditing,
                                                                    onStageVideo = parserViewModel::stageVideoForParam,
                                                                    onRemoveVideo = parserViewModel::removeVideo,
                                                                    getVideoUri = parserViewModel::getVideoUri,
                                                                    videoThumbnails = parserUiState.videoThumbnails,
                                                                    onStageAudio = parserViewModel::stageAudioForParam,
                                                                    onRemoveAudio = parserViewModel::removeAudio,
                                                                    getAudioUri = parserViewModel::getAudioUri
                                                                ),
                                                                getSeedMode = parserViewModel::getSeedMode,
                                                                setSeedMode = parserViewModel::setSeedMode,
                                                                compact = true
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    isFirstGroup = false
                                }
                            }
                        }
                    }
                }
            }
            
            
            // 执行控制区（主页面显示）
            if (hasWorkflowLoaded && isServerConnected) {
                // 呼吸灯动画效果（执行中时）
                val isExecuting = parserUiState.executionStatus.state == com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.EXECUTING ||
                                 parserUiState.executionStatus.state == com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.PREPARING ||
                                 parserUiState.executionStatus.state == com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.QUEUED
                
                // Cyan 色系呼吸灯：从中青到浅青（与主题蓝绿色系协调）
                val infiniteTransition = rememberInfiniteTransition(label = "breathing")
                val breathingColor by infiniteTransition.animateColor(
                    initialValue = Color(0xFF80D8FF),  // Cyan 200 - 基底色
                    targetValue = Color(0xFFB3E5FC),   // Cyan 100 - 高光色
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "breathing_color"
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (parserUiState.executionStatus.state) {
                            com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.ERROR -> 
                                Color(0xFFFFCDD2)  // 不透明的淡红色 (Red 100)
                            com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.EXECUTING,
                            com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.PREPARING,
                            com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.QUEUED -> 
                                if (isExecuting) breathingColor else Color(0xFF80D8FF)  // 青色呼吸灯效果
                            else -> Color(0xFFE0EFFF)  // 默认浅蓝色（包括完成状态）
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .animateContentSize(
                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                            ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 统一显示执行操作行（执行中也可追加执行与取消）
                        run {
                            // 从 parserUiState 中获取持久化的批次数量
                            var batchText by remember(parserUiState.batchCount) { mutableStateOf(parserUiState.batchCount.toString()) }
                            val queueTotal = parserUiState.queueTotal
                            
                            // 飞机动画状态
                            var triggerAnimation by remember { mutableStateOf(false) }
                            var planePosition by remember { mutableStateOf(0f) }
                            
                            // 使用 LaunchedEffect 控制连续动画
                            LaunchedEffect(triggerAnimation) {
                                if (triggerAnimation) {
                                    // 第一段：飞出右边
                                    androidx.compose.animation.core.animate(
                                        initialValue = 0f,
                                        targetValue = 300f,
                                        animationSpec = tween(durationMillis = 600)
                                    ) { value, _ ->
                                        planePosition = value
                                    }
                                    // 瞬间移到左边
                                    planePosition = -300f
                                    // 第二段：从左边飞回中心
                                    androidx.compose.animation.core.animate(
                                        initialValue = -300f,
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 600)
                                    ) { value, _ ->
                                        planePosition = value
                                    }
                                    // 动画完成，重置状态
                                    triggerAnimation = false
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 队列任务数量标签（始终显示）
                                Text(
                                    text = "${parserUiState.queueTotal}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (parserUiState.queueTotal > 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                Button(
                                    onClick = {
                                        // 触发飞机动画
                                        triggerAnimation = true
                                        // 执行时从 parserUiState 获取最新的批次数量，而不是依赖当前的 batchText
                                        val times = parserUiState.batchCount
                                        parserViewModel.executeWorkflowBatch(times)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(ButtonDefaults.MinHeight),
                                    enabled = hasWorkflowParsed,
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    // 飞机图标，带平移动画
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = com.example.myapplication.R.drawable.ic_telegram),
                                            contentDescription = "执行",
                                            modifier = Modifier
                                                .size(24.dp)
                                                .graphicsLayer {
                                                    translationX = planePosition
                                                    alpha = if (kotlin.math.abs(planePosition) > 250f) 0f else 1f
                                                }
                                        )
                                    }
                                }
                                // 取消当前执行（统一为胶囊按钮，背景固定灰色，有任务时图标变红）
                                val cancelTint = if (queueTotal > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                val fixedGrayBg = Color(0xFFE8E8E8)  // 明确的浅灰色
                                val darkGrayBg = Color(0xFF555555) // 深灰色
                                FilledTonalButton(
                                    onClick = { parserViewModel.cancelExecution() },
                                    modifier = Modifier.height(ButtonDefaults.MinHeight),
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = darkGrayBg,
                                        disabledContainerColor = darkGrayBg
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "取消当前",
                                        tint = if (queueTotal > 0) Color(0xFFFF5252) else Color.White // 队列>0时变红，否则白色
                                    )
                                }
                                // 清除待处理队列（统一为胶囊按钮，背景固定灰色，队列>1时图标变红）
                                val deleteTint = if (queueTotal > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                FilledTonalButton(
                                    onClick = { parserViewModel.clearPendingQueue() },
                                    modifier = Modifier.height(ButtonDefaults.MinHeight),
                                    enabled = parserUiState.queueTotal > 0,
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = darkGrayBg,
                                        disabledContainerColor = darkGrayBg
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "清队列",
                                        tint = if (queueTotal > 1) Color(0xFFFF5252) else Color.White // 有任务时红色，无任务白色
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .height(ButtonDefaults.MinHeight)
                                        .clip(RoundedCornerShape(50))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 输入框
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .padding(start = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (batchText.isEmpty()) {
                                            Text(
                                                text = "批次",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        BasicTextField(
                                            value = batchText,
                                            onValueChange = { input ->
                                                val filtered = input.filter { it.isDigit() }.take(4)
                                                batchText = if (filtered.isEmpty()) "" else filtered
                                                
                                                // 更新 ViewModel 状态
                                                val newCount = batchText.toIntOrNull() ?: 1
                                                parserViewModel.updateBatchCount(newCount)
                                            },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    // 右侧箭头按钮（上下排列）
                                    Column(
                                        modifier = Modifier
                                            .width(28.dp)
                                            .fillMaxHeight(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        // 增加按钮（上箭头）
                                        IconButton(
                                            onClick = {
                                                val current = batchText.toIntOrNull() ?: 1
                                                if (current < 9999) {
                                                    val newCount = current + 1
                                                    batchText = newCount.toString()
                                                    parserViewModel.updateBatchCount(newCount)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "增加",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        // 减少按钮（下箭头）
                                        IconButton(
                                            onClick = {
                                                val current = batchText.toIntOrNull() ?: 1
                                                if (current > 1) {
                                                    val newCount = current - 1
                                                    batchText = newCount.toString()
                                                    parserViewModel.updateBatchCount(newCount)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "减少",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 🎥 视频上传进度（紧凑样式，与执行区域一致）
                        if (parserUiState.isUploadingVideo) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🎥 上传视频",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${(parserUiState.videoUploadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            if (parserUiState.videoUploadFileName.isNotEmpty()) {
                                Text(
                                    text = parserUiState.videoUploadFileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            LinearProgressIndicator(
                                progress = { parserUiState.videoUploadProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        // 执行状态详细显示（不再使用内层卡片）
                        if (parserUiState.executionStatus.state != com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.IDLE) {
                            // 状态标题行
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when (parserUiState.executionStatus.state) {
                                            com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.PREPARING -> "准备中"
                                            com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.QUEUED -> "队列中"
                                            com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.EXECUTING -> "执行中"
                                            com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.COMPLETED -> "执行完成"
                                            com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.ERROR -> "执行失败"
                                            else -> ""
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when (parserUiState.executionStatus.state) {
                                            com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.ERROR -> 
                                                MaterialTheme.colorScheme.error
                                            com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.COMPLETED -> 
                                                MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    
                                    // 显示百分比（仅在执行中且有进度时）
                                    if (parserUiState.executionStatus.state == com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.EXECUTING && 
                                        parserUiState.executionStatus.progress > 0f) {
                                        Text(
                                            text = "${(parserUiState.executionStatus.progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                
                                // 当前节点信息已删除（不需要显示）
                                
                                // 状态消息
                                // 说明：COMPLETED 状态下标题已经显示“执行完成”，避免 message 再重复显示一遍“执行完成”
                                val shouldShowStatusMessage = parserUiState.executionStatus.message.isNotEmpty() &&
                                    !(
                                        parserUiState.executionStatus.state ==
                                            com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.COMPLETED &&
                                            (
                                                parserUiState.executionStatus.message == "执行完成" ||
                                                    parserUiState.executionStatus.message.startsWith("执行完成（")
                                            )
                                    )

                                if (shouldShowStatusMessage) {
                                    Text(
                                        text = parserUiState.executionStatus.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                // 进度条（仅在执行中时显示）
                                if (parserUiState.executionStatus.state == com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.EXECUTING) {
                                    LinearProgressIndicator(
                                        progress = { parserUiState.executionStatus.progress },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                                
                                // Prompt ID 显示已删除（不需要显示）

                                // 执行结果图像/视频/音频/文本展示（尽量上移并减少间距）
                                val images = parserUiState.executionStatus.result?.outputImages ?: emptyList()
                                val videos = parserUiState.executionStatus.result?.outputVideos ?: emptyList()
                                val audios = parserUiState.executionStatus.result?.outputAudios ?: emptyList() // 🎵 音频输出
                                val textOutputs = parserUiState.executionStatus.result?.outputTexts ?: emptyMap() // 📝 文本输出
                                
                                // 🎥 获取当前执行的ExecutionRecord（用于视频下载进度）
                                val currentPromptId = parserUiState.executionStatus.promptId
                                val allRecords by historyViewModel.records.collectAsState()
                                val currentRecord = remember(currentPromptId, allRecords) {
                                    if (currentPromptId != null) {
                                        allRecords.firstOrNull { it.promptId == currentPromptId }
                                    } else {
                                        null
                                    }
                                }
                                
                                // 仅将确认为图像的URL纳入展示（避免只有视频时误显示"结果图像"）
                                val imageExtensions = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif")
                                val shownImages = images.filter { url ->
                                    val u = url.trim().lowercase()
                                    u.isNotEmpty() && imageExtensions.any { u.contains(it) }
                                }
                                val shownVideos = videos.filter { it.isNotBlank() }
                                
                                // 🎯 混合图片和“视频/动图”为统一的媒体列表：
                                // - 若是 gif/webp 等动图，按 Image 展示（避免用 ExoPlayer 播放失败）
                                // - 其他走 Video
                                val mediaItems = remember(shownImages, shownVideos, currentRecord) {
                                    val items = mutableListOf<MediaItem>()
                                    shownImages.forEach { items.add(MediaItem.Image(it)) }
                                    // 视频使用本地路径（如果已下载）或URL
                                    shownVideos.forEachIndexed { index, url ->
                                        val localPath = currentRecord?.localVideoPaths?.getOrNull(index)
                                        val downloadProgress = currentRecord?.videoDownloadProgress?.get(index)
                                        val source = localPath?.takeIf { it.isNotEmpty() } ?: url
                                        val lower = source.trim().lowercase()
                                        val isGifLike = lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                                        if (isGifLike) {
                                            // 本地文件给 file://，远程保持原样
                                            val img = if (!source.startsWith("http", ignoreCase = true) && !source.startsWith("file://")) {
                                                "file://$source"
                                            } else source
                                            items.add(MediaItem.Image(img))
                                        } else {
                                            items.add(MediaItem.Video(source))
                                        }
                                    }
                                    items
                                }

                                // ✅ 结果区域出现/消失时做丝滑过渡（不改变“执行中不显示上一次结果”的逻辑，只让尺寸变化不生硬）
                                val hasAnyOutputs = mediaItems.isNotEmpty() || textOutputs.isNotEmpty() || audios.isNotEmpty()
                                AnimatedVisibility(
                                    visible = hasAnyOutputs,
                                    enter = expandVertically(
                                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                    ) + fadeIn(animationSpec = tween(durationMillis = 120)),
                                    exit = shrinkVertically(
                                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                    ) + fadeOut(animationSpec = tween(durationMillis = 100))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize(
                                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                            ),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (mediaItems.isNotEmpty()) {
                                            // 统一媒体预览状态
                                            var mediaGalleryOpen by remember { mutableStateOf(false) }
                                            var mediaGalleryStartIndex by remember { mutableStateOf(0) }

                                            // 执行结果：去掉“结果”标题，减少顶部留白，让结果更靠上
                                            Spacer(Modifier.height(2.dp))
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                itemsIndexed(mediaItems) { index, mediaItem ->
                                                    when (mediaItem) {
                                                        is MediaItem.Image -> {
                                                            // 图片缩略图
                                                            AsyncImage(
                                                                model = mediaItem.url,
                                                                contentDescription = null,
                                                                contentScale = ContentScale.FillHeight,
                                                                modifier = Modifier
                                                                    .height(120.dp)
                                                                    .wrapContentWidth()
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .clickable(
                                                                        indication = null,
                                                                        interactionSource = remember { MutableInteractionSource() }
                                                                    ) {
                                                                        mediaGalleryStartIndex = index
                                                                        mediaGalleryOpen = true
                                                                    }
                                                            )
                                                        }
                                                        is MediaItem.Video -> {
                                                            // 视频缩略图（支持下载进度）
                                                            // 计算实际视频索引（减去图片数量）
                                                            val videoIndex = index - shownImages.size
                                                            val videoUrl = shownVideos[videoIndex]
                                                            val localPath = currentRecord?.localVideoPaths?.getOrNull(videoIndex)
                                                            val downloadProgress = currentRecord?.videoDownloadProgress?.get(videoIndex)
                                                            val downloadFailed =
                                                                currentRecord?.videoDownloadFailed?.contains(videoIndex) == true

                                                            VideoThumbnailItem(
                                                                videoUrl = videoUrl,
                                                                localPath = localPath,
                                                                downloadProgress = downloadProgress,
                                                                downloadFailed = downloadFailed,
                                                                onClick = {
                                                                    mediaGalleryStartIndex = index
                                                                    mediaGalleryOpen = true
                                                                },
                                                                onRetry = if (downloadFailed) {
                                                                    {
                                                                        // TODO: 触发重新下载
                                                                        Toast.makeText(
                                                                            context,
                                                                            "重试下载功能开发中",
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    }
                                                                } else null
                                                            )
                                                        }
                                                        is MediaItem.Audio -> {
                                                            // 音频：用简单占位缩略图，点击进入全屏画廊播放
                                                            androidx.compose.material3.Card(
                                                                modifier = Modifier
                                                                    .height(120.dp)
                                                                    .width(160.dp)
                                                                    .clickable(
                                                                        indication = null,
                                                                        interactionSource = remember { MutableInteractionSource() }
                                                                    ) {
                                                                        mediaGalleryStartIndex = index
                                                                        mediaGalleryOpen = true
                                                                    },
                                                                shape = RoundedCornerShape(8.dp),
                                                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                                                    // ✅ 音频结果卡片改为灰色，并用 Card 的 shape 处理圆角，避免视觉瑕疵
                                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                                )
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text("🎵 音频", style = MaterialTheme.typography.bodyMedium)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // 全屏多媒体预览对话框（统一支持图片和视频左右切换）
                                            if (mediaGalleryOpen) {
                                                val saveScope = rememberCoroutineScope()
                                                com.example.myapplication.presentation.ui.components.media.EnhancedMediaGallery(
                                                    mediaItems = mediaItems,
                                                    initialIndex = mediaGalleryStartIndex,
                                                    onDismiss = { mediaGalleryOpen = false },
                                                    onSaveClick = { url ->
                                                        saveScope.launch {
                                                            val ok = MediaSaveUtils.saveMediaToGallery(context, url)
                                                            Toast.makeText(
                                                                context,
                                                                if (ok) "已保存到相册" else "保存失败",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                )
                                            }
                                        }

                                        // 📝 显示文本输出结果
                                        if (textOutputs.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            var textPreviewOpen by remember { mutableStateOf(false) }

                                            // 计算文本条数
                                            val textCount = remember(textOutputs) { textOutputs.values.sumOf { it.size } }

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0EFFF))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = "📝 文本结果",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Text(
                                                            text = "共 $textCount 条",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    OutlinedButton(onClick = { textPreviewOpen = true }) {
                                                        Text("查看")
                                                    }
                                                }
                                            }

                                            // 弹窗查看全文
                                            if (textPreviewOpen) {
                                                Dialog(
                                                    onDismissRequest = { textPreviewOpen = false },
                                                    properties = DialogProperties(usePlatformDefaultWidth = false)
                                                ) {
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .fillMaxHeight(0.85f)
                                                            .padding(16.dp),
                                                        shape = RoundedCornerShape(16.dp),
                                                        // ✅ 使用与历史记录卡片一致的浅蓝
                                                        color = Color(0xFFE0EFFF)
                                                    ) {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .padding(16.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(
                                                                    text = "📝 文本结果",
                                                                    style = MaterialTheme.typography.titleMedium,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                                IconButton(onClick = { textPreviewOpen = false }) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Close,
                                                                        contentDescription = "关闭",
                                                                        tint = MaterialTheme.colorScheme.primary
                                                                    )
                                                                }
                                                            }
                                                            Spacer(Modifier.height(8.dp))
                                                            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                                            Spacer(Modifier.height(8.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .weight(1f)
                                                                    .verticalScroll(rememberScrollState())
                                                            ) {
                                                                com.example.myapplication.presentation.ui.components.TextOutputDisplay(
                                                                    textOutputs = textOutputs
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 🎵 显示音频输出结果
                                        if (audios.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            com.example.myapplication.presentation.ui.components.AudioOutputDisplay(
                                                audioUrls = audios
                                            )
                                        }
                                    }
                                }

                                // 错误详细信息（仅在错误状态时显示，放到结果之后避免挤压内容）
                                if (parserUiState.executionStatus.state == com.example.myapplication.domain.execution.WorkflowExecutor.ExecutionState.ERROR) {
                                    Spacer(Modifier.height(6.dp))
                                    // 错误类型显示
                                    parserUiState.executionStatus.errorType?.let { errorType ->
                                        val (errorIcon, errorTypeText, errorColor) = when (errorType) {
                                            com.example.myapplication.domain.execution.WorkflowExecutor.ErrorType.NETWORK_ERROR ->
                                                Triple(Icons.Default.Warning, "网络连接错误", MaterialTheme.colorScheme.error)
                                            com.example.myapplication.domain.execution.WorkflowExecutor.ErrorType.SERVER_ERROR ->
                                                Triple(Icons.Default.Warning, "服务器错误", MaterialTheme.colorScheme.error)
                                            com.example.myapplication.domain.execution.WorkflowExecutor.ErrorType.TIMEOUT_ERROR ->
                                                Triple(Icons.Default.Warning, "执行超时", MaterialTheme.colorScheme.error)
                                            com.example.myapplication.domain.execution.WorkflowExecutor.ErrorType.VALIDATION_ERROR ->
                                                Triple(Icons.Default.Warning, "参数验证错误", MaterialTheme.colorScheme.error)
                                            com.example.myapplication.domain.execution.WorkflowExecutor.ErrorType.WORKFLOW_ERROR ->
                                                Triple(Icons.Default.Warning, "工作流错误", MaterialTheme.colorScheme.error)
                                            com.example.myapplication.domain.execution.WorkflowExecutor.ErrorType.UNKNOWN_ERROR ->
                                                Triple(Icons.Default.Warning, "未知错误", MaterialTheme.colorScheme.error)
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                errorIcon,
                                                contentDescription = null,
                                                tint = errorColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = errorTypeText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = errorColor,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // 错误详情
                                    if (!parserUiState.executionStatus.errorDetails.isNullOrBlank()) {
                                        Text(
                                            text = parserUiState.executionStatus.errorDetails!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }

                                    // 重试次数显示
                                    if (parserUiState.executionStatus.retryCount > 0) {
                                        Text(
                                            text = "重试次数: ${parserUiState.executionStatus.retryCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }

                                    // 重置按钮
                                    OutlinedButton(
                                        onClick = { parserViewModel.resetExecution() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("重置")
                                    }
                                }
                            }
                        }
                    }
                }
            
            // 使用提示已移除
            Spacer(Modifier.height(42.dp))
        }
        
        // 遮罩编辑器全屏覆盖层（与参数设置界面一致，直接显示）
        if (parserUiState.showMaskEditor && parserUiState.maskEditingOriginalBytes != null) {
            val originalBitmap = remember(parserUiState.maskEditingOriginalBytes) {
                ImageUtils.byteArrayToBitmap(parserUiState.maskEditingOriginalBytes!!)
            }
            
            originalBitmap?.let { bitmap ->
                ImageMaskEditor(
                    originalBitmap = bitmap,
                    existingPaths = parserUiState.maskEditingExistingPaths,
                    onSave = { allPaths, canvasSize ->
                        parserViewModel.completeMaskEditing(allPaths, canvasSize)
                    },
                    onCancel = {
                        parserViewModel.cancelMaskEditing()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * 视频缩略图展示组件
 * 显示视频第一帧作为缩略图，支持下载进度显示
 * 
 * @param videoUrl 视频URL（用于生成缩略图）
 * @param localPath 本地视频路径（下载完成后）
 * @param downloadProgress 下载进度 (0.0-1.0)，null表示已下载或无需下载
 * @param downloadFailed 是否下载失败
 * @param onClick 点击回调（仅当视频已下载时可点击）
 * @param onRetry 重试回调（仅当下载失败时显示）
 */
@Composable
private fun VideoThumbnailItem(
    videoUrl: String,
    localPath: String? = null,
    downloadProgress: Float? = null,
    downloadFailed: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null
) {
    var showPlayer by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var thumbnailBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var imageAspectRatio by remember { mutableStateOf<Float?>(null) }
    
    // 视频是否已下载完成
    val isDownloaded = localPath != null && localPath.isNotEmpty()

    fun String?.looksLikeImageOrGif(): Boolean {
        val u = (this ?: return false).trim().lowercase()
        return u.endsWith(".gif") || u.endsWith(".webp") || u.endsWith(".png") || u.endsWith(".jpg") || u.endsWith(".jpeg")
    }
    val downloadedLooksLikeImage = localPath.looksLikeImageOrGif()

    // 下载中固定 4:3（等价于之前 160x120），下载完成后尽量使用真实比例
    val placeholderRatio = 4f / 3f
    val targetRatio = remember(isDownloaded, downloadedLooksLikeImage, thumbnailBitmap, imageAspectRatio) {
        if (!isDownloaded) return@remember placeholderRatio
        val ratio = when {
            downloadedLooksLikeImage -> imageAspectRatio
            thumbnailBitmap != null -> {
                val w = thumbnailBitmap!!.width.takeIf { it > 0 } ?: 0
                val h = thumbnailBitmap!!.height.takeIf { it > 0 } ?: 0
                if (w > 0 && h > 0) w.toFloat() / h.toFloat() else null
            }
            else -> null
        }
        (ratio ?: placeholderRatio).coerceIn(0.5f, 3.0f)
    }
    val animatedRatio by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetRatio,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 260,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "VideoThumbnailItem_ratio"
    )
    
    // ✅ 新逻辑：下载中不获取缩略图（避免与下载进度叠加出现双转圈）
    // 下载完成后：
    // - gif/图片：交给 AsyncImage 直接显示缩略图
    // - 视频：再尝试抽帧，失败则用占位图但仍可点击播放
    androidx.compose.runtime.LaunchedEffect(isDownloaded, localPath) {
        if (!isDownloaded) {
            thumbnailBitmap = null
            imageAspectRatio = null
            return@LaunchedEffect
        }
        if (downloadedLooksLikeImage) {
            thumbnailBitmap = null
            return@LaunchedEffect
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val source = localPath ?: videoUrl
            thumbnailBitmap = com.example.myapplication.util.VideoUtils.getVideoThumbnail(context, source)
        }
    }
    
    Box(
        modifier = Modifier
            .height(120.dp)
            .aspectRatio(animatedRatio)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .then(
                // 仅当已下载完成时可点击
                if (isDownloaded) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { 
                        if (onClick != null) {
                            onClick()
                        } else {
                            showPlayer = true
                        }
                    }
                } else {
                    Modifier  // 下载中，不可点击
                }
            )
    ) {
        // 背景：下载中固定占位（不转圈）；下载完成后按格式展示缩略图
        when {
            isDownloaded && downloadedLooksLikeImage -> {
                // gif/图片：用 Coil 渲染；成功后读取真实宽高来驱动比例动画
                coil.compose.SubcomposeAsyncImage(
                    model = "file://${localPath}",
                    contentDescription = "动图/图片",
                    modifier = Modifier.matchParentSize()
                ) {
                    when (val s = painter.state) {
                        is coil.compose.AsyncImagePainter.State.Success -> {
                            val d = s.result.drawable
                            val w = d.intrinsicWidth.takeIf { it > 0 } ?: 0
                            val h = d.intrinsicHeight.takeIf { it > 0 } ?: 0
                            val r = if (w > 0 && h > 0) (w.toFloat() / h.toFloat()).coerceIn(0.5f, 3.0f) else null
                            androidx.compose.runtime.LaunchedEffect(r) {
                                if (r != null) imageAspectRatio = r
                            }
                            // 注意：本项目的 Coil 用法是通过 SubcomposeAsyncImage 的 receiver 调用 Content
                            this@SubcomposeAsyncImage.SubcomposeAsyncImageContent(
                                modifier = Modifier.matchParentSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                }
            }
            isDownloaded && thumbnailBitmap != null -> {
                // 视频：抽帧成功
                androidx.compose.foundation.Image(
                    bitmap = thumbnailBitmap!!.asImageBitmap(),
                    contentDescription = "视频缩略图",
                    modifier = Modifier.matchParentSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
            else -> {
                // 占位背景（不转圈、无播放键；未下载时避免出现“可播放”的误导）
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    // empty
                }
            }
        }
        
        // 根据下载状态显示不同的叠加层
        if (downloadFailed) {
            // 🔄 下载失败：显示重试按钮
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.7f)),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                        contentDescription = "下载失败",
                        modifier = Modifier.size(48.dp),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "下载失败",
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    if (onRetry != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.Button(
                            onClick = onRetry,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color.White,
                                contentColor = androidx.compose.ui.graphics.Color.Red
                            ),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "重试",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        } else if (!isDownloaded && downloadProgress != null) {
            // 下载中：显示进度条
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                // ✅ 只保留居中的进度圆圈（无文字、无播放键）
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.size(52.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    strokeWidth = 4.dp
                )
            }
        } else if (isDownloaded) {
            // 已下载：显示播放图标
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f)),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                    contentDescription = "播放视频",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
    
    // 视频播放器对话框（仅当已下载时）
    if (showPlayer && isDownloaded) {
        // gif/图片：不走视频播放器，直接走统一预览器体验更一致（这里简单复用图片预览）
        if (downloadedLooksLikeImage) {
                val saveScope = rememberCoroutineScope()
            com.example.myapplication.presentation.ui.components.media.EnhancedMediaGallery(
                mediaItems = listOf(MediaItem.Image("file://${localPath}")),
                initialIndex = 0,
                    onDismiss = { showPlayer = false },
                    onSaveClick = { url ->
                        saveScope.launch {
                            val ok = MediaSaveUtils.saveMediaToGallery(context, url)
                            Toast.makeText(
                                context,
                                if (ok) "已保存到相册" else "保存失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            )
        } else {
            com.example.myapplication.presentation.ui.components.VideoPlayerDialog(
                videoUrl = localPath!!,  // 使用本地路径（播放器内部会兼容 file path）
                videoName = "结果视频",
                onDismiss = { showPlayer = false }
            )
        }
    }
}

/**
 * 全屏多媒体预览对话框（统一支持图片和视频左右切换）
 */

