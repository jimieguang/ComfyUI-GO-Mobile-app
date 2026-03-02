package com.example.myapplication.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animateFloatAsState
import com.example.myapplication.domain.execution.WorkflowExecutor
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.presentation.ui.components.WorkflowParameterCard
import com.example.myapplication.presentation.ui.components.ImageMaskEditor
import com.example.myapplication.util.ImageUtils
import com.example.myapplication.presentation.viewmodel.WorkflowParserViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 参数编辑屏幕
 * 专门用于编辑工作流参数的全屏界面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ParameterEditScreen(
    onNavigateBack: () -> Unit,
    parserViewModel: WorkflowParserViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by parserViewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val nodeList = uiState.nodeInstances
    val nodeCount = nodeList.size
    // Selection mode & selected params (selected from ViewModel state)
    var selectionModeNodeId by remember { mutableStateOf<String?>(null) }
    val selectedParamIds = remember(uiState.quickParamsByNode) {
        uiState.quickParamsByNode.values.flatten().toSet()
    }
    // BringIntoView requesters for each node card
    val bringRequesters = remember(nodeList) {
        nodeList.associate { it.nodeId to BringIntoViewRequester() }
    }

    // Wheel panel state
    var panelVisible by remember { mutableStateOf(false) }
    val panelFraction by animateFloatAsState(targetValue = if (panelVisible) 1f else 0f, label = "panelWidthFraction")

    // Infinite wheel list setup
    val infiniteCount = if (nodeCount == 0) 0 else Int.MAX_VALUE
    val startIndex = remember(nodeCount) {
        if (nodeCount == 0) 0 else (Int.MAX_VALUE / 2) - (Int.MAX_VALUE / 2) % max(nodeCount, 1)
    }
    val wheelListState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    var currentSelectedIndexMod by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val itemHeight = 40.dp
    val itemHeightPx = with(density) { itemHeight.toPx() }

    // Observe center item and fire haptics when selection changes
    LaunchedEffect(wheelListState, nodeCount) {
        if (nodeCount == 0) return@LaunchedEffect
        snapshotFlow { wheelListState.layoutInfo }.map { layoutInfo ->
            val viewportCenter = layoutInfo.viewportStartOffset + (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
            val closest = layoutInfo.visibleItemsInfo.minByOrNull { info ->
                abs((info.offset + info.size / 2) - viewportCenter)
            }
            closest?.index ?: startIndex
        }.distinctUntilChanged().collectLatest { absIndex ->
            val mod = if (nodeCount == 0) 0 else ((absIndex % nodeCount) + nodeCount) % nodeCount
            if (mod != currentSelectedIndexMod) {
                currentSelectedIndexMod = mod
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
    
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "参数设置",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        floatingActionButton = {
            if (uiState.hasParsedWorkflow && nodeCount > 0) {
                // 纯拖拽的节点选择器（无点击功能，只支持拖拽操作）
                Surface(
                    modifier = Modifier
                        .offset(y = (-260).dp) // 调整位置：下移 40dp，增大上方空间
                        .pointerInput(nodeCount) {
                            detectDragGestures(
                                onDragStart = {
                                    if (nodeCount > 0) panelVisible = true
                                },
                                onDrag = { _, dragAmount ->
                                    if (!panelVisible) return@detectDragGestures
                                    val dy = dragAmount.y
                                    coroutineScope.launch {
                                        // 动态灵敏度：节点越多，基础灵敏度越高
                                        val dynamicBoost = 1f + (nodeCount / 20f).coerceAtMost(2f)
                                        val baseSensitivity = 1.6f * dynamicBoost
                                        // 加速度：随拖动距离增加，并在快速拖动时进一步放大
                                        val accelSlope = 0.035f
                                        val accelCap = 3.5f
                                        val gestureBoost = if (abs(dy) > itemHeightPx * 0.6f) 1.4f else 1f
                                        val boost = (1f + (abs(dy) * accelSlope).coerceAtMost(accelCap)) * gestureBoost
                                        val deltaPx = -dy * baseSensitivity * boost
                                        var newIndex = wheelListState.firstVisibleItemIndex
                                        var newOffset = (wheelListState.firstVisibleItemScrollOffset + deltaPx).toInt()
                                        val step = itemHeightPx.toInt().coerceAtLeast(1)
                                        while (newOffset < 0) {
                                            newIndex = (newIndex - 1).coerceAtLeast(0)
                                            newOffset += step
                                        }
                                        while (newOffset >= step) {
                                            newIndex = (newIndex + 1).coerceAtMost(infiniteCount - 1)
                                            newOffset -= step
                                        }
                                        wheelListState.scrollToItem(newIndex, newOffset)
                                    }
                                },
                                onDragEnd = {
                                    if (!panelVisible) return@detectDragGestures
                                    panelVisible = false
                                    val targetNode = nodeList.getOrNull(currentSelectedIndexMod)
                                    if (targetNode != null) {
                                        val requester = bringRequesters[targetNode.nodeId]
                                        if (requester != null) {
                                            coroutineScope.launch { requester.bringIntoView() }
                                        }
                                    }
                                },
                                onDragCancel = {
                                    panelVisible = false
                                }
                            )
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 6.dp,
                    tonalElevation = 6.dp
                ) {
                    // 竖条样式：窄且高的可视控件，仅作拖拽手柄
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(180.dp)
                            .background(
                                // 浅蓝色外条
                                color = Color(0xFF5AA9E6).copy(alpha = 0.28f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 内部再放一个更深的细竖条，增强可感知性
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(4.dp)
                                .background(
                                    // 深蓝色内线
                                    color = Color(0xFF1E88E5),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            if (uiState.hasParsedWorkflow && uiState.parseResult != null) {
                // 参数编辑卡片
                WorkflowParameterCard(
                    uiState = uiState,
                    onParameterValueChange = parserViewModel::updateParameterValue,
                    mediaCallbacks = com.example.myapplication.presentation.ui.components.MediaCallbacks(
                        onStageImage = parserViewModel::stageImageForParam,
                        getStagedImageBytes = parserViewModel::getStagedImageBytes,
                        onEditMask = parserViewModel::startMaskEditing,
                        onStageVideo = parserViewModel::stageVideoForParam,
                        onRemoveVideo = parserViewModel::removeVideo,
                        getVideoUri = parserViewModel::getVideoUri,
                        onStageAudio = parserViewModel::stageAudioForParam,
                        onRemoveAudio = parserViewModel::removeAudio,
                        getAudioUri = parserViewModel::getAudioUri
                    ),
                    selectionModeNodeId = selectionModeNodeId,
                    selectedParamIds = selectedParamIds,
                    onToggleParamSelected = { pid -> parserViewModel.toggleQuickParam(pid) },
                    onLongPressNode = { nid ->
                        selectionModeNodeId = if (selectionModeNodeId == nid) null else nid
                    },
                    getSeedMode = parserViewModel::getSeedMode,
                    setSeedMode = parserViewModel::setSeedMode,
                    bringIntoViewRequesters = bringRequesters
                )
                
                // 更改状态提示
                if (parserViewModel.hasUnsavedChanges()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "有未保存的更改",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
            } else {
                // 无数据状态
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "暂无可编辑参数",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "请先导入工作流文件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        OutlinedButton(onClick = onNavigateBack) {
                            Text("返回导入工作流")
                        }
                    }
                }
            }
            // Close Column before overlay
            }
            // Right sliding wheel panel overlay
            if (nodeCount > 0 && panelFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.4f)
                        .fillMaxWidth(panelFraction)
                        .align(Alignment.BottomEnd)
                        .zIndex(10f)
                ) {
                    if (panelFraction > 0f) {
                        Card(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            // Wheel list
                            LazyColumn(
                                state = wheelListState,
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (infiniteCount > 0) {
                                    items(infiniteCount) { index ->
                                        val mod = ((index % nodeCount) + nodeCount) % nodeCount
                                        val node = nodeList[mod]
                                        val layoutInfo = wheelListState.layoutInfo
                                        val viewportCenter = layoutInfo.viewportStartOffset + (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
                                        // Approximate distance for visual scaling
                                        val itemCenterOffset = (index - wheelListState.firstVisibleItemIndex) * itemHeightPx - wheelListState.firstVisibleItemScrollOffset.toFloat() + itemHeightPx / 2f
                                        val dist = abs(itemCenterOffset - viewportCenter)
                                        val norm = (dist / (itemHeightPx * 3f)).coerceIn(0f, 1f)
                                        val alpha = 1f - 0.6f * norm
                                        val scale = 1f - 0.1f * norm
                                        Box(
                                            modifier = Modifier
                                                .height(itemHeight)
                                                .fillMaxWidth()
                                                .graphicsLayer { this.alpha = alpha; this.scaleX = scale; this.scaleY = scale },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = node.title,
                                                maxLines = 1,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // Center highlight
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .height(itemHeight)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        )
                    }
                }
            }
        }
    }
    
    // 遮罩编辑器全屏覆盖层
    if (uiState.showMaskEditor && uiState.maskEditingOriginalBytes != null) {
        // 调试：检查传入遮罩编辑器的图像数据
        android.util.Log.d("ParameterEditScreen", "遮罩编辑器 - 接收的原始图像字节数组大小: ${uiState.maskEditingOriginalBytes!!.size}")
        android.util.Log.d("ParameterEditScreen", "遮罩编辑器 - 已有路径数量: ${uiState.maskEditingExistingPaths.size}")
        
        val originalBitmap = remember(uiState.maskEditingOriginalBytes) {
            val bitmap = ImageUtils.byteArrayToBitmap(uiState.maskEditingOriginalBytes!!)
            android.util.Log.d("ParameterEditScreen", "遮罩编辑器 - 解码后Bitmap尺寸: ${bitmap?.width ?: "null"}x${bitmap?.height ?: "null"}")
            bitmap
        }
        
        originalBitmap?.let { bitmap ->
            ImageMaskEditor(
                originalBitmap = bitmap,
                existingPaths = uiState.maskEditingExistingPaths,
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

// 执行状态卡片已移动到工作流生成器页面显示

