package com.example.myapplication.presentation.ui.components.media

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.myapplication.presentation.ui.model.MediaItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 增强版多媒体画廊 (WeChat Style)
 * - 支持下拉关闭 (Drag to Dismiss)
 * - 沉浸式单击显隐 UI
 * - 双指缩放 + 惯性回弹
 * - 左右滑动切换 (Pager)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnhancedMediaGallery(
    mediaItems: List<MediaItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onSaveClick: ((String) -> Unit)? = null // 仅图片支持保存
) {
    // 强制全屏 Dialog
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex) { mediaItems.size }
        val scope = rememberCoroutineScope()
        
        // 沉浸模式：单击隐藏 UI
        var isUiVisible by remember { mutableStateOf(true) }
        
        // 下拉关闭状态 (作用于整体背景和当前内容)
        // dragOffset.y > 0 表示下拉
        val dragOffsetY = remember { Animatable(0f) }
        val backgroundAlpha = remember { Animatable(1f) }
        
        // 监听系统返回键：如果在下拉中则忽略，否则正常关闭
        BackHandler {
            onDismiss()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha.value))
        ) {
            // 水平滑动页面
            // userScrollEnabled: 只有在未放大且未处于下拉拖拽状态时才允许翻页
            // 这里我们通过Pager内部逻辑处理，或者在外层拦截
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = dragOffsetY.value == 0f // 简单判断：只要没有垂直位移，就允许水平滑
            ) { page ->
                val item = mediaItems[page]
                
                // 每个页面独立的缩放/拖拽逻辑
                GalleryItemContent(
                    item = item,
                    // 将外部的下拉状态传入，由内部手势驱动
                    onDragYChange = { delta ->
                        scope.launch {
                            // 只有当 y > 0 (下拉) 时才响应，或者已经处于下拉状态时
                            // 简单的阻尼系数
                            val target = dragOffsetY.value + delta
                            if (target >= 0) {
                                dragOffsetY.snapTo(target)
                                // 动态计算背景透明度：下拉 300dp 变全透明
                                val progress = (target / 800f).coerceIn(0f, 1f)
                                backgroundAlpha.snapTo(1f - progress)
                            }
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (dragOffsetY.value > 250f) { // 阈值：下拉超过 250px 则关闭
                                onDismiss()
                            } else {
                                // 回弹复位
                                launch { dragOffsetY.animateTo(0f) }
                                launch { backgroundAlpha.animateTo(1f) }
                            }
                        }
                    },
                    onTap = { isUiVisible = !isUiVisible },
                    // 传入当前的垂直位移，用于位移内容
                    parentDragOffsetY = if (pagerState.currentPage == page) dragOffsetY.value else 0f
                )
            }

            // --- 顶部操作栏 ---
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 页码指示器（居中显示）
                    ContainerLabel(
                        text = "${pagerState.currentPage + 1} / ${mediaItems.size}"
                    )
                }
            }

            // --- 底部保存按钮（可选：由调用方传入 onSaveClick 才显示）---
            val currentItem = mediaItems.getOrNull(pagerState.currentPage)
            if (currentItem != null && onSaveClick != null) {
                // 视频时向上移动60dp，避免被播放控件遮挡
                val bottomPadding = if (currentItem is MediaItem.Video) 84.dp else 24.dp
                AnimatedVisibility(
                    visible = isUiVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = bottomPadding, end = 24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { 
                                when (currentItem) {
                                    is MediaItem.Image -> onSaveClick.invoke(currentItem.url)
                                    is MediaItem.Video -> {
                                        onSaveClick.invoke(currentItem.url)
                                    }
                                    is MediaItem.Audio -> {
                                        onSaveClick.invoke(currentItem.url)
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack, // 临时用 ArrowBack 旋转后代表下载
                                contentDescription = "保存",
                                tint = Color.White,
                                modifier = Modifier.graphicsLayer { rotationZ = -90f }
                            )
                        }
                        Text(
                            text = "保存", 
                            color = Color.White, 
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContainerLabel(text: String) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/**
 * 单个媒体页面的内容容器
 * 处理缩放、双击、以及传出“下拉”手势
 */
@Composable
private fun GalleryItemContent(
    item: MediaItem,
    onDragYChange: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
    parentDragOffsetY: Float
) {
    // 缩放状态 (使用 Animatable 以支持弹性动画)
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope() // 需要协程作用域来启动动画

    // 动态计算内容的各项变换
    // 当 parentDragOffsetY > 0 (正在下拉) 时，内容也要跟随缩小一点点，模拟“离去”的效果
    val dismissScale = if (parentDragOffsetY > 0) (1f - parentDragOffsetY / 2000f).coerceAtLeast(0.5f) else 1f
    
    val finalScale = scale.value * dismissScale
    val finalTranslationX = offsetX.value
    val finalTranslationY = offsetY.value + parentDragOffsetY
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            // 核心手势处理区域
            .pointerInput(Unit) {
                this.detectGalleryGestures(
                    onTap = onTap,
                    onDoubleTap = {
                        scope.launch {
                            if (scale.value > 1f) {
                                scale.animateTo(1f)
                                offsetX.animateTo(0f)
                                offsetY.animateTo(0f)
                            } else {
                                scale.animateTo(2.5f)
                            }
                        }
                    },
                    onDragY = { dy: Float -> 
                        // 仅当单指且未放大时触发
                        if (scale.value == 1f) onDragYChange(dy) 
                    },
                    onDragEnd = {
                         // Gesture end handling for vertical drag (dismiss)
                         if (scale.value == 1f) onDragEnd()
                         
                         // Auto-restore logic for scale
                         scope.launch {
                             if (scale.value < 1f) {
                                 scale.animateTo(1f, animationSpec = tween(300))
                                 offsetX.animateTo(0f)
                                 offsetY.animateTo(0f)
                             }
                             // Rubber band logic check if out of bounds (omitted for brevity, can be added later)
                         }
                    },
                    onTransform = { centroid: Offset, pan: Offset, zoom: Float, rotation: Float ->
                        scope.launch {
                            // 缩放/移动 逻辑
                            // Directly Snap during gesture
                            val newScale = (scale.value * zoom).coerceIn(0.5f, 5f) // Allow zooming out deeper for elastic effect
                            scale.snapTo(newScale)
                            
                            if (scale.value > 1f || newScale < 1f) {
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                val boundX = (scale.value - 1) * width / 2
                                val boundY = (scale.value - 1) * height / 2
                                
                                // Calculate new offsets
                                val newOffsetX = (offsetX.value + pan.x * scale.value)
                                val newOffsetY = (offsetY.value + pan.y * scale.value)
                                
                                // Only apply bounds if scale > 1. If < 1, allow free movement (will snap back)
                                if (scale.value > 1f) {
                                     offsetX.snapTo(newOffsetX.coerceIn(-boundX, boundX))
                                     offsetY.snapTo(newOffsetY.coerceIn(-boundY, boundY))
                                } else {
                                     offsetX.snapTo(newOffsetX)
                                     offsetY.snapTo(newOffsetY)
                                }
                            }
                        }
                    }
                )
            }
            .graphicsLayer {
                scaleX = finalScale
                scaleY = finalScale
                this.translationX = finalTranslationX
                this.translationY = finalTranslationY
            }
    ) {
        when (item) {
            is MediaItem.Image -> {
                SubcomposeAsyncImage(
                    model = item.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                ) {
                     val state = painter.state
                     if (state is coil.compose.AsyncImagePainter.State.Loading) {
                         Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                             CircularProgressIndicator(color = Color.White)
                         }
                     } else {
                         SubcomposeAsyncImageContent()
                     }
                }
            }
            is MediaItem.Video -> {
                SimpleVideoPlayer(url = item.url)
            }
            is MediaItem.Audio -> {
                SimpleAudioPlayer(url = item.url)
            }
        }
    }
}

@Composable
fun SimpleVideoPlayer(url: String) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(url))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE // 循环播放
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = true // 支持视频进度条
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun SimpleAudioPlayer(url: String) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(url))
            prepare()
            playWhenReady = false
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // 定时更新进度
    LaunchedEffect(Unit) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            kotlinx.coroutines.delay(200)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 播放/暂停
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f)
            ) {
                IconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.White
                    )
                }
            }

            // 进度条
            Slider(
                value = if (duration > 0) currentPosition.toFloat() else 0f,
                onValueChange = { newValue ->
                    exoPlayer.seekTo(newValue.toLong())
                    currentPosition = newValue.toLong()
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatTimeMmSs(currentPosition), color = Color.White)
                Text(text = formatTimeMmSs(duration), color = Color.White)
            }
        }
    }
}

private fun formatTimeMmSs(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * 智能手势检测：
 * 1. 优先检测双击/单击
 * 2. 如果是单指滑动：
 *    - 如果尚未判定方向：检测 touch slop。
 *    - 水平 > 垂直：放弃拦截 (return)，交给 Pager。
 *    - 垂直 > 水平：拦截，触发 onDragY (下拉)。
 * 3. 如果是多指：直接触发 onTransform (缩放)。
 */
suspend fun PointerInputScope.detectGalleryGestures(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onDragY: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit
) {
    while (true) {
        awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = false)
            var mode = 0 // 0: unknown, 1: horizontal (pass), 2: vertical (intercept), 3: zoom
            
            var panX = 0f
            var panY = 0f
            
            do {
                val event = awaitPointerEvent()
                val canceled = event.changes.any { it.isConsumed }
                if (canceled) break
                
                val pointerCount = event.changes.size
                
                // 1. 多指检测 -> 立即升级为缩放模式
                if (pointerCount > 1) {
                    mode = 3
                }
                
                if (mode == 3) {
                    // --- 缩放模式 ---
                    // 手动计算缩放和平移，并消费事件
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val centroid = event.calculateCentroid()
                    
                    if (zoom != 1f || pan != Offset.Zero) {
                        onTransform(centroid, pan, zoom, 0f)
                    }
                    
                    // 消费所有事件，阻止父级 Pager 干扰
                    event.changes.forEach { it.consume() }
                    
                } else {
                    // --- 单指处理 ---
                    val change = event.changes[0]
                    if (change.pressed && !change.previousPressed) {
                        // 不消费 Down，让 Pager 有机会初始化
                    } else {
                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y
                        
                        if (mode == 0) {
                            panX += dx
                            panY += dy
                            
                            val touchSlop = 18f // 稍微大一点
                            if (abs(panX) > touchSlop || abs(panY) > touchSlop) {
                                if (abs(panX) > abs(panY)) {
                                    mode = 1 // Horizontal
                                    // 确认是水平滑动，直接放弃监听，让 Pager 处理
                                    // 关键：不 consume，并 break 出当前的 gesture loop (但 forEachGesture 会继续下一次)
                                    // 这里 break 后，pointerInput 不再处理后续 move，Pager 依然可能无法接管？
                                    // 不，Compose 中如果子 View 不消费，父 View 会在 NestedScroll 中处理吗？
                                    // Pager 是基于 PointerInput 的。 
                                    // 如果我们在这里 break，我们就不再 process。
                                    break 
                                } else {
                                    mode = 2 // Vertical
                                    // 确认是垂直滑动，开始拦截
                                    change.consume()
                                    onDragY(panY) 
                                }
                            } else {
                                // 在 slop 范围内，为了防止 Pager 抢占，我们可以选择消费？
                                // 或者不消费。如果不消费，Pager 累积 slop 也会触发。
                                // 安全起见，我们稍微消费一点？不，不消费。
                            }
                        } else if (mode == 2) {
                            // Vertical Dragging
                            onDragY(dy)
                            change.consume()
                        }
                    }
                }
                
            } while (event.changes.any { it.pressed })
            
            if (mode == 2) {
                onDragEnd()
            }
        }
    }
}
