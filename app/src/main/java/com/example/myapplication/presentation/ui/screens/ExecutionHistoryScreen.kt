package com.example.myapplication.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.myapplication.presentation.util.LoadedState
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.example.myapplication.presentation.ui.model.MediaItem
import com.example.myapplication.util.MediaSaveUtils
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaScannerConnection
import android.widget.Toast
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 视图模式枚举
 */
private enum class HistoryViewMode {
    CARD_LIST,    // 历史记录卡片
    WATERFALL     // 图片瀑布流
}

/**
 * 执行历史页面（统一入口）
 * 包含两种视图模式：
 * - 历史记录卡片：显示执行记录列表，支持滑动加载、删除等
 * - 图片瀑布流：以瀑布流形式展示所有输出图片
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
fun ExecutionHistoryScreen(
    viewModel: com.example.myapplication.presentation.viewmodel.ExecutionHistoryViewModel,
    onUseWorkflow: (kotlinx.serialization.json.JsonObject, String?, Map<String, List<String>>?, Map<String, kotlinx.serialization.json.JsonElement>?) -> Unit,
    onOpenWaterfall: () -> Unit = {}
) {
    val records by viewModel.records.collectAsState()
    val names by viewModel.workflowNames.collectAsState()
    val outputs by viewModel.recordOutputs.collectAsState()

    var currentViewMode by remember { mutableStateOf(HistoryViewMode.CARD_LIST) }
    var galleryOpen by remember { mutableStateOf(false) }
    var galleryMediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var galleryStartIndex by remember { mutableStateOf(0) }
    var textDialogOpen by remember { mutableStateOf(false) }
    var textDialogTitle by remember { mutableStateOf("") }
    var textDialogOutputs by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 胶囊式视图切换器
        CapsuleViewToggle(
            currentMode = currentViewMode,
            onModeChange = { currentViewMode = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 根据视图模式显示不同的内容（带滑动动画）
        AnimatedContent(
            targetState = currentViewMode,
            transitionSpec = {
                if (targetState == HistoryViewMode.CARD_LIST) {
                    // 切换到左侧（历史记录）：从左侧滑入
                    slideInHorizontally(animationSpec = tween(300)) { -it } with
                    slideOutHorizontally(animationSpec = tween(300)) { it }
                } else {
                    // 切换到右侧（瀑布流）：从右侧滑入
                    slideInHorizontally(animationSpec = tween(300)) { it } with
                    slideOutHorizontally(animationSpec = tween(300)) { -it }
                }
            },
            label = "ViewModeAnimation"
        ) { mode ->
            when (mode) {
                HistoryViewMode.CARD_LIST -> {
                    HistoryCardView(
                        viewModel = viewModel,
                        records = records,
                        names = names,
                        outputs = outputs,
                        onUseWorkflow = onUseWorkflow,
                        onOpenGallery = { mediaItems, startIndex ->
                            galleryMediaItems = mediaItems
                            galleryStartIndex = startIndex
                            galleryOpen = true
                        },
                        onOpenTextOutputs = { recordTitle, textOutputs ->
                            textDialogTitle = recordTitle
                            textDialogOutputs = textOutputs
                            textDialogOpen = true
                        }
                    )
                }
                HistoryViewMode.WATERFALL -> {
                    WaterfallView(
                        viewModel = viewModel,
                        records = records,
                        outputs = outputs,
                        onOpenGallery = { mediaItems, startIndex ->
                            galleryMediaItems = mediaItems
                            galleryStartIndex = startIndex
                            galleryOpen = true
                        },
                        onOpenTextOutputs = { recordTitle, textOutputs ->
                            textDialogTitle = recordTitle
                            textDialogOutputs = textOutputs
                            textDialogOpen = true
                        }
                    )
                }
            }
        }
    }
    
    // 全屏多媒体预览对话框（两个视图共享）
    if (galleryOpen) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        com.example.myapplication.presentation.ui.components.media.EnhancedMediaGallery(
            mediaItems = galleryMediaItems,
            initialIndex = galleryStartIndex,
            onDismiss = { galleryOpen = false },
            onSaveClick = { url ->
                scope.launch {
                    val ok = MediaSaveUtils.saveMediaToGallery(context, url)
                    Toast.makeText(context, if (ok) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 📝 文本结果预览对话框（历史卡片/瀑布流共用）
    if (textDialogOpen) {
        Dialog(
            onDismissRequest = { textDialogOpen = false },
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
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (textDialogTitle.isBlank()) "📝 文本结果" else "📝 $textDialogTitle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { textDialogOpen = false }) {
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
                            textOutputs = textDialogOutputs
                        )
                    }
                }
            }
        }
    }
}

/**
 * 胶囊式视图切换器 - 带滑动背景动画
 */
@Composable
private fun CapsuleViewToggle(
    currentMode: HistoryViewMode,
    onModeChange: (HistoryViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf("历史记录卡片", "图片瀑布流")
    val selectedIndex = when (currentMode) {
        HistoryViewMode.CARD_LIST -> 0
        HistoryViewMode.WATERFALL -> 1
    }
    
    // 滑动动画 - 计算偏移量百分比
    val animatedOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "segmented_control_offset"
    )
    
    // 外层大胶囊容器
    Surface(
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFE0EFFF),  // 浅蓝色背景
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            // 滑动的背景指示器（小胶囊）
            val itemWidth = 1f / items.size
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(itemWidth)
                    .graphicsLayer {
                        translationX = size.width * animatedOffset
                    }
                    .background(
                        color = MaterialTheme.colorScheme.primary,  // 深蓝色滑动指示器
                        shape = RoundedCornerShape(20.dp)
                    )
            )
            
            // 按钮层
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items.forEachIndexed { index, item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                onClick = {
                                    val newMode = when (index) {
                                        0 -> HistoryViewMode.CARD_LIST
                                        1 -> HistoryViewMode.WATERFALL
                                        else -> HistoryViewMode.CARD_LIST
                                    }
                                    onModeChange(newMode)
                                },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedIndex == index) 
                                MaterialTheme.colorScheme.onPrimary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 历史记录卡片视图
 */
@Composable
private fun HistoryCardView(
    viewModel: com.example.myapplication.presentation.viewmodel.ExecutionHistoryViewModel,
    records: List<com.example.myapplication.data.model.ExecutionRecord>,
    names: Map<String, String>,
    outputs: Map<String, List<String>>,
    onUseWorkflow: (kotlinx.serialization.json.JsonObject, String?, Map<String, List<String>>?, Map<String, kotlinx.serialization.json.JsonElement>?) -> Unit,
    onOpenGallery: (List<MediaItem>, Int) -> Unit,
    onOpenTextOutputs: (String, Map<String, List<String>>) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 统计信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFE0EFFF)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    StatisticItem("总执行次数", records.size.toString())
                    StatisticItem("成功", records.count { it.success }.toString())
                    StatisticItem("失败", records.count { !it.success }.toString())
                }
                
                // 清空历史按钮
                if (records.isNotEmpty()) {
                    OutlinedButton(onClick = { viewModel.clearAll() }) {
                        Text("清空历史")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // 历史记录列表
        if (records.isEmpty()) {
            // 空状态
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE0EFFF)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "暂无执行历史",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "执行工作流后将在这里显示历史记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // 历史记录列表
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 62.dp)
            ) {
                 items(records) { rec ->
                    LaunchedEffect(rec.promptId) { 
                        viewModel.ensureWorkflowNameLoaded(rec.promptId)
                        viewModel.ensureOutputsLoaded(rec.promptId)
                        viewModel.ensureWorkflowJsonLoaded(rec.promptId)
                    }
                    run         {
                        val scope = rememberCoroutineScope()
                        val offsetX = remember { Animatable(0f) }
                        var cardWidthPx by remember { mutableStateOf(0) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { cardWidthPx = it.width }
                        ) {
                            // 背景提示（露出时可见）
                            Row(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "使用",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            val density = LocalDensity.current
                            var deleteBtnWidthPx by remember { mutableStateOf(0) }

                            Box(modifier = Modifier.graphicsLayer { translationX = offsetX.value }) {
                        val isLoaded = (LoadedState.source == LoadedState.Source.HISTORY && LoadedState.historyId == rec.promptId)
                    val recordTitle = (rec.workflowName?.takeIf { it.isNotBlank() } ?: names[rec.promptId] ?: ("工作流 " + rec.promptId.take(8))).replace("ComfyUI", "")
                        HistoryRecordCard(
                            record = rec,
                        title = recordTitle,
                            thumbUrl = outputs[rec.promptId]?.firstOrNull(),
                            isLoaded = isLoaded,
                            onThumbClick = {
                                // 🔧 画廊改为跨记录拼接：只有一张图时也能左右滑到相邻任务的结果
                                val ordered = records.sortedByDescending { it.timestamp }
                                val target = com.example.myapplication.util.HistoryMediaUtils
                                    .buildGalleryTarget(ordered, outputs, rec)
                                if (target.items.isNotEmpty()) {
                                    onOpenGallery(target.items, target.startIndex)
                                }
                            },
                        onTextClick = {
                            if (rec.textOutputs.isNotEmpty()) {
                                onOpenTextOutputs(recordTitle, rec.textOutputs)
                            }
                        },
                            onUseWorkflow = { /* 已移除按钮使用，改为滑动 */ },
                            onDelete = {
                                val rawJson = kotlinx.serialization.json.Json.encodeToString(
                                    com.example.myapplication.data.model.ExecutionRecord.serializer(),
                                    rec
                                )
                                viewModel.deleteRecordByJson(rawJson)
                            },
                            onDeleteMeasured = { w -> deleteBtnWidthPx = w }
                                )
                            }

                            // 可拖拽的有效区域覆盖（排除左侧缩略图与右侧删除按钮区域）
                            val leftExclude = 16.dp + 72.dp + 12.dp
                            val rightExclude = 16.dp + with(density) { deleteBtnWidthPx.toDp() }
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(start = leftExclude, end = rightExclude)
                                    .draggable(
                                        orientation = Orientation.Horizontal,
                                        state = rememberDraggableState { delta ->
                                            val next = (offsetX.value + delta).coerceAtMost(0f)
                                            scope.launch { offsetX.snapTo(next) }
                                        },
                                        onDragStopped = { velocity ->
                                            if (velocity < -SWIPE_VELOCITY_THRESHOLD) {
                                                scope.launch {
                                                    val target = if (cardWidthPx > 0) -cardWidthPx.toFloat() else -300f
                                                    offsetX.animateTo(target, animationSpec = tween(180))
                                        val cached = viewModel.getCachedWorkflow(rec.promptId)
                                                if (cached != null) {
                                                    viewModel.setLastLoadedId(rec.promptId)
                                                    LoadedState.markHistory(rec.promptId)
                                                        val preferredName = (rec.workflowName?.takeIf { it.isNotBlank() }
                                                            ?: names[rec.promptId]
                                                            ?: ("工作流 " + rec.promptId.take(8))).replace("ComfyUI", "")
                                            onUseWorkflow(cached, preferredName, rec.quickParams, rec.parameterValues)  // ✅ 传入快速参数
                                            // 同步生成器的导入名称与来源
                                            // 通过共享 ImportViewModel 在 MainScreen 中传入，此处仅触发回调由上层设置
                                                    } else {
                                                        scope.launch {
                                                            val result = viewModel.fetchWorkflowFromHistory(rec.promptId)
                                                            if (result.isSuccess) {
                                                                val workflow = result.getOrThrow()
                                                            viewModel.setLastLoadedId(rec.promptId)
                                                            LoadedState.markHistory(rec.promptId)
                                                                val preferredName = (rec.workflowName?.takeIf { it.isNotBlank() }
                                                                    ?: names[rec.promptId]
                                                                    ?: ("工作流 " + rec.promptId.take(8))).replace("ComfyUI", "")
                                                                onUseWorkflow(workflow, preferredName, rec.quickParams, rec.parameterValues)  // ✅ 传入快速参数
                                                            }
                                                        }
                                                    }
                                                    offsetX.snapTo(0f)
                                                }
                                            } else {
                                                scope.launch { offsetX.animateTo(0f, animationSpec = spring()) }
                                            }
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 瀑布流视图
 */
@Composable
private fun WaterfallView(
    viewModel: com.example.myapplication.presentation.viewmodel.ExecutionHistoryViewModel,
    records: List<com.example.myapplication.data.model.ExecutionRecord>,
    outputs: Map<String, List<String>>,
    onOpenGallery: (List<MediaItem>, Int) -> Unit,
    onOpenTextOutputs: (String, Map<String, List<String>>) -> Unit
) {
    // 进入页面后为所有当前记录触发一次输出加载（轻量查询，内部有去重）
    LaunchedEffect(records) {
        records.forEach { rec -> viewModel.ensureOutputsLoaded(rec.promptId) }
    }
    
    val orderedRecords = remember(records) { records.sortedByDescending { it.timestamp } }
    val recordItems = remember(orderedRecords, outputs) {
        orderedRecords.map { rec -> rec to (outputs[rec.promptId] ?: emptyList()) }
            .filter { (rec, urls) ->
                // ✅ 让音频/视频/文本也能出现在瀑布流：只要任意输出存在就展示
                val hasImages = urls.isNotEmpty()
                val hasVideos = rec.localVideoPaths.any { it.isNotBlank() } || rec.videoUrls.isNotEmpty()
                val hasAudios = rec.localAudioPaths.any { it.isNotBlank() } || rec.audioUrls.isNotEmpty()
                val hasTexts = rec.textOutputs.isNotEmpty()
                hasImages || hasVideos || hasAudios || hasTexts
            }
    }

    // 列数选择（1/2/3列）
    var columns by remember { mutableStateOf(2) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 列数切换器
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WaterfallColumnSelector(columns) { columns = it }
        }

        Spacer(Modifier.height(12.dp))

        if (recordItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无媒体", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(columns.coerceIn(1, 3)),
                verticalItemSpacing = 8.dp,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 62.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(recordItems) { (rec, urls) ->
                    val videoItems = rec.localVideoPaths
                        .filter { it.isNotBlank() }
                        .map { path ->
                            val lower = path.trim().lowercase()
                            val isGifLike = lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                            if (isGifLike) MediaItem.Image("file://$path") else MediaItem.Video(path)
                        }
                    val audioItems =
                        rec.localAudioPaths
                            .filter { it.isNotBlank() }
                            .map { path -> MediaItem.Audio("file://$path") }
                            .ifEmpty { rec.audioUrls.map { MediaItem.Audio(it) } }
                    val recordTitle = (rec.workflowName?.takeIf { it.isNotBlank() } ?: ("工作流 " + rec.promptId.take(8))).replace("ComfyUI", "")
                    val hasTexts = rec.textOutputs.isNotEmpty()

                    if (urls.isNotEmpty()) {
                        val cover = urls.first()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFE0EFFF)
                            )
                        ) {
                            // 使用 SubcomposeAsyncImage 读取真实尺寸，按比例设置高度，避免空白并保持不裁剪
                            SubcomposeAsyncImage(
                                model = cover,
                                contentDescription = null
                            ) {
                                when (val s = painter.state) {
                                    is coil.compose.AsyncImagePainter.State.Success -> {
                                        val d = s.result.drawable
                                        val w = d.intrinsicWidth.takeIf { it > 0 } ?: 1
                                        val h = d.intrinsicHeight.takeIf { it > 0 } ?: 1
                                        val ratio = w.toFloat() / h.toFloat()
                                        val subScope = this
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(ratio)
                                                .clip(RoundedCornerShape(8.dp))  // 圆角裁剪
                                                .background(Color.Black)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = LocalIndication.current
                                                ) {
                                                    // 🔧 跨记录拼接：单图任务也能滑到相邻任务
                                                    val galleryTarget = com.example.myapplication.util.HistoryMediaUtils
                                                        .buildGalleryTarget(orderedRecords, outputs, rec)
                                                    onOpenGallery(galleryTarget.items, galleryTarget.startIndex)
                                                }
                                        ) {
                                            with(subScope) {
                                                SubcomposeAsyncImageContent(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Fit,
                                                    alignment = Alignment.Center
                                                )
                                            }
                                            // 左上角数量角标（显示图片+视频+音频总数）
                                            val audioCount = rec.localAudioPaths.count { it.isNotBlank() }.takeIf { it > 0 }
                                                ?: rec.audioUrls.size
                                            val totalMediaCount =
                                                urls.size + rec.localVideoPaths.count { it.isNotBlank() } + audioCount
                                            if (totalMediaCount > 1) {  // 只在有多个媒体时显示角标
                                                Surface(
                                                    color = UiColor.Black.copy(alpha = 0.6f),
                                                    shape = MaterialTheme.shapes.small,
                                                    tonalElevation = 0.dp,
                                                    shadowElevation = 0.dp,
                                                    modifier = Modifier
                                                        .align(Alignment.TopStart)
                                                        .padding(6.dp)
                                                ) {
                                                    Text(
                                                        text = totalMediaCount.toString(),
                                                        color = UiColor.White,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            // 📝 文本角标：有文本输出时提供入口
                                            if (hasTexts) {
                                                Surface(
                                                    color = UiColor.Black.copy(alpha = 0.6f),
                                                    shape = MaterialTheme.shapes.small,
                                                    tonalElevation = 0.dp,
                                                    shadowElevation = 0.dp,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = { onOpenTextOutputs(recordTitle, rec.textOutputs) },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.TextSnippet,
                                                            contentDescription = "查看文本结果",
                                                            tint = UiColor.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        // 占位高度，避免首次测量空白跳动
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(160.dp)
                                                .background(Color.Black)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // ✅ 无图片时：展示音频/视频占位卡，点击进入统一预览器
                        val hasAudio = audioItems.isNotEmpty()
                        if (hasAudio) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = LocalIndication.current
                                    ) {
                                        // 纯音频记录：退回自身媒体列表（含音频）
                                        val galleryTarget = com.example.myapplication.util.HistoryMediaUtils
                                            .buildGalleryTarget(orderedRecords, outputs, rec)
                                        onOpenGallery(galleryTarget.items, galleryTarget.startIndex)
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0EFFF))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("🎵 音频", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            val hasVideo = videoItems.isNotEmpty() || rec.videoUrls.isNotEmpty()
                            if (!hasVideo && hasTexts) {
                                // 📝 纯文本输出：方形占位卡，点击打开文本预览
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = LocalIndication.current
                                        ) { onOpenTextOutputs(recordTitle, rec.textOutputs) },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0EFFF))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("📝 文本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                return@items
                            }
                            // ✅ 视频：像图片一样显示缩略图（本地视频可抽帧）
                            val context = LocalContext.current
                            val sourceVideo = rec.localVideoPaths.firstOrNull { it.isNotBlank() }
                                ?: rec.videoUrls.firstOrNull()
                            var thumbnail by remember(sourceVideo) { mutableStateOf<android.graphics.Bitmap?>(null) }
                            LaunchedEffect(sourceVideo) {
                                if (sourceVideo != null && !sourceVideo.startsWith("http", ignoreCase = true)) {
                                    withContext(Dispatchers.IO) {
                                        thumbnail = com.example.myapplication.util.VideoUtils.getVideoThumbnail(context, sourceVideo)
                                    }
                                } else {
                                    thumbnail = null
                                }
                            }
                            val ratio = remember(thumbnail) {
                                val w = thumbnail?.width ?: 0
                                val h = thumbnail?.height ?: 0
                                if (w > 0 && h > 0) (w.toFloat() / h.toFloat()).coerceIn(0.5f, 3.0f) else (4f / 3f)
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(ratio)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = LocalIndication.current
                                    ) {
                                        // 🔧 跨记录拼接：视频也参与左右滑动序列
                                        val galleryTarget = com.example.myapplication.util.HistoryMediaUtils
                                            .buildGalleryTarget(orderedRecords, outputs, rec)
                                        onOpenGallery(galleryTarget.items, galleryTarget.startIndex)
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0EFFF))
                            ) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                                    if (thumbnail != null) {
                                        Image(
                                            bitmap = thumbnail!!.asImageBitmap(),
                                            contentDescription = "视频缩略图",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(Color.Black.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "播放视频",
                                            tint = Color.White,
                                            modifier = Modifier.size(44.dp)
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
}

@Composable
private fun WaterfallColumnSelector(current: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2, 3).forEach { c ->
            FilterChip(
                selected = current == c,
                onClick = { onChange(c) },
                label = { Text("${c}列") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFE0EFFF),  // 浅蓝色选中背景
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

private const val SWIPE_VELOCITY_THRESHOLD: Float = 2500f // px/s，负为向左

@Composable
private fun StatisticItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HistoryRecordCard(
    record: com.example.myapplication.data.model.ExecutionRecord,
    title: String,
    thumbUrl: String?,
    isLoaded: Boolean = false,
    onThumbClick: () -> Unit = {},
    onTextClick: (() -> Unit)? = null,
    onUseWorkflow: () -> Unit,
    onDelete: () -> Unit,
    onDeleteMeasured: (Int) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE0EFFF)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图/视频播放入口（固定大小，不改变卡片整体高度）
            Box(
                modifier = Modifier
                    .size(96.dp)  // 加大缩略图尺寸
                    .clip(RoundedCornerShape(8.dp))  // 圆角裁剪
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val firstLocalVideo = record.localVideoPaths.firstOrNull { it.isNotBlank() }
                val firstAudio = record.localAudioPaths.firstOrNull { it.isNotBlank() }
                    ?: record.audioUrls.firstOrNull()
                if (firstLocalVideo != null) {
                    // 显示视频缩略图 + 播放图标
                    val context = LocalContext.current
                    var thumbnailBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                    
                    // 异步加载视频缩略图
                    LaunchedEffect(firstLocalVideo) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            thumbnailBitmap = com.example.myapplication.util.VideoUtils.getVideoThumbnail(context, firstLocalVideo)
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                indication = LocalIndication.current,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onThumbClick() }  // 改为调用 onThumbClick，打开统一预览器
                    ) {
                        // 显示视频缩略图或占位符
                        if (thumbnailBitmap != null) {
                            Image(
                                bitmap = thumbnailBitmap!!.asImageBitmap(),
                                contentDescription = "视频缩略图",
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // 加载中的占位符
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        
                        // 播放图标叠加层
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "播放视频",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else if (thumbUrl != null) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                indication = LocalIndication.current,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onThumbClick() },
                        contentScale = ContentScale.Crop
                    )
                } else if (firstAudio != null) {
                    // 🎵 音频占位：支持进入统一预览器（播放/保存）
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                indication = LocalIndication.current,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onThumbClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎵",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                } else {
                    // 无图占位
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 标题与状态
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!record.success) {
                            StatusChip(status = "失败")
                            Spacer(Modifier.width(8.dp))
                        }
                        if (isLoaded) {
                            Text(
                                text = "已加载",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Text(
                    text = "执行时间: ${formatExactTime(record.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // ✅ 恢复最初位置：文本按钮在删除按钮左边（不再叠放）
                    Row(
                        // ✅ 关键修复：测量“文本+删除”整块按钮组宽度，用于外层拖拽遮罩排除区域
                        modifier = Modifier.onSizeChanged { onDeleteMeasured(it.width) },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (record.textOutputs.isNotEmpty() && onTextClick != null) {
                            OutlinedButton(onClick = onTextClick) {
                                Icon(
                                    imageVector = Icons.Default.TextSnippet,
                                    contentDescription = "查看文本结果",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("文本")
                            }
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 全屏多媒体预览对话框（统一支持图片和视频左右切换）
 */


@Composable
private fun SaveButton(
    modifier: Modifier = Modifier,
    currentUrlProvider: () -> String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = UiColor.Black.copy(alpha = 0.5f)
    ) {
        IconButton(onClick = {
            val url = currentUrlProvider() ?: return@IconButton
            scope.launch {
                val ok = saveImageToGallery(context, url)
                Toast.makeText(context, if (ok) "已保存到相册/ComfyUI" else "保存失败", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("保存", color = UiColor.White)
        }
    }
}

/**
 * 统一保存函数：自动判断图片或视频
 */
private suspend fun saveMediaToGallery(context: android.content.Context, url: String): Boolean {
    val lower = url.lowercase()
    val isVideo = lower.endsWith(".mp4") || lower.endsWith(".mov") || 
                  lower.endsWith(".avi") || lower.endsWith(".mkv") ||
                  lower.contains("/history_videos/") // 本地视频目录
    
    return if (isVideo) {
        saveVideoToGallery(context, url)
    } else {
        saveImageToGallery(context, url)
    }
}

private suspend fun saveVideoToGallery(context: android.content.Context, videoPath: String): Boolean {
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
                // Android 10+ 使用 MediaStore
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
                // Android 9 及以下
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
                
                // 通知系统刷新媒体库
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
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}

private suspend fun saveImageToGallery(context: android.content.Context, url: String): Boolean {
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
 * 支持缩放和拖动的图片组件
 * 特性：
 * - 双击缩放 1x ↔ 2.5x
 * - 双指捏合缩放 1x-4x
 * - 放大后可拖动查看细节
 */


@Composable
private fun StatusChip(status: String) {
    val (color, icon) = when (status) {
        "已完成" -> Color(0xFF4CAF50) to Icons.Default.CheckCircle
        "执行中" -> Color(0xFFFF9800) to Icons.Default.Info
        "失败" -> Color(0xFFF44336) to Icons.Default.Warning
        else -> MaterialTheme.colorScheme.primary to Icons.Default.CheckCircle
    }
    
    AssistChip(
        onClick = { },
        label = { Text(status) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            leadingIconContentColor = color,
            labelColor = color
        )
    )
}

private fun formatExactTime(timestamp: Long): String {
    val dt = java.time.Instant.ofEpochMilli(timestamp)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return dt.format(formatter)
}

// 旧的占位数据结构已移除，改为使用真实的 ExecutionRecord
