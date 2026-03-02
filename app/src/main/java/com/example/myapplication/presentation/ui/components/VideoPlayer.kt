package com.example.myapplication.presentation.ui.components

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 视频播放器组件（基于 Media3 ExoPlayer）- 简化版
 * 
 * @param videoUrl 视频URL
 * @param modifier 修饰符
 * @param autoPlay 是否自动播放
 * @param showControls 是否显示控制条（已简化为播放/暂停按钮）
 */
@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    showControls: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun toMediaUri(input: String): Uri {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) ->
                Uri.parse(trimmed)
            trimmed.startsWith("file://", ignoreCase = true) ->
                Uri.parse(trimmed)
            // 兼容传入的是本地绝对路径（/data/...），Media3 更稳的方式是 file Uri
            File(trimmed).exists() ->
                Uri.fromFile(File(trimmed))
            else ->
                Uri.parse(trimmed)
        }
    }
    
    // 创建 ExoPlayer 实例
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(toMediaUri(videoUrl))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = autoPlay
        }
    }
    
    // 播放状态
    var isPlaying by remember { mutableStateOf(autoPlay) }
    var showControlsState by remember { mutableStateOf(showControls) }
    var hasEnded by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    
    // 定时更新进度
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            kotlinx.coroutines.delay(100)
        }
    }
    
    // 监听播放状态变化
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                hasEnded = playbackState == Player.STATE_ENDED
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
    
    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            showControlsState = !showControlsState
        }
    ) {
        // ExoPlayer 视图（禁用默认控制条）
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false  // 禁用默认控制条
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 简化的自定义控制层
        if (showControls && showControlsState) {
            // 中心播放/暂停按钮
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(80.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                IconButton(
                    onClick = {
                        if (hasEnded) {
                            // 视频结束：重新播放
                            exoPlayer.seekTo(0)
                            exoPlayer.play()
                            hasEnded = false
                        } else if (isPlaying) {
                            // 播放中：暂停
                            exoPlayer.pause()
                        } else {
                            // 暂停中：播放
                            exoPlayer.play()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isPlaying && !hasEnded) {
                        // 播放中：显示暂停符号
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(24.dp)
                                    .background(Color.White, MaterialTheme.shapes.small)
                            )
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(24.dp)
                                    .background(Color.White, MaterialTheme.shapes.small)
                            )
                        }
                    } else {
                        // 暂停/结束：显示播放图标
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = if (hasEnded) "重新播放" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
            
            // 底部进度条
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
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
                
                // 时间显示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatTime(duration),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // 右下角保存按钮（向上移动30dp）
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 42.dp, end = 12.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            val success = saveVideoToGallery(context, videoUrl)
                            Toast.makeText(
                                context,
                                if (success) "已保存到相册/ComfyUI" else "保存失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text("保存", color = Color.White)
                }
            }
        }
    }
}

/**
 * 简单视频播放器对话框
 * 用于全屏播放视频，仅包含播放/暂停和保存按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerDialog(
    videoUrl: String,
    videoName: String = "视频",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun toMediaUri(input: String): Uri {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) ->
                Uri.parse(trimmed)
            trimmed.startsWith("file://", ignoreCase = true) ->
                Uri.parse(trimmed)
            File(trimmed).exists() ->
                Uri.fromFile(File(trimmed))
            else ->
                Uri.parse(trimmed)
        }
    }
    
    // 创建 ExoPlayer 实例
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(toMediaUri(videoUrl))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }
    
    // 播放状态
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var hasEnded by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    
    // 定时更新进度
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            kotlinx.coroutines.delay(100)
        }
    }
    
    // 监听播放状态变化
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                hasEnded = playbackState == Player.STATE_ENDED
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
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showControls = !showControls
                    }
            ) {
                // 视频播放器
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false  // 禁用默认控制条
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // 自定义控制层（仅显示时可见）
                if (showControls) {
                    // 顶部关闭按钮
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = Color.White,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    
                    // 顶部标题
                    Text(
                        text = videoName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 24.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    // 中心播放/暂停按钮
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(80.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        IconButton(
                            onClick = {
                                if (hasEnded) {
                                    // 视频结束：重新播放
                                    exoPlayer.seekTo(0)
                                    exoPlayer.play()
                                    hasEnded = false
                                } else if (isPlaying) {
                                    // 播放中：暂停
                                    exoPlayer.pause()
                                } else {
                                    // 暂停中：播放
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (isPlaying && !hasEnded) {
                                // 播放中：显示暂停符号
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .height(24.dp)
                                            .background(Color.White, MaterialTheme.shapes.small)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .height(24.dp)
                                            .background(Color.White, MaterialTheme.shapes.small)
                                    )
                                }
                            } else {
                                // 暂停/结束：显示播放图标
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = if (hasEnded) "重新播放" else "播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                    
                    // 底部进度条
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(16.dp)
                    ) {
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
                        
                        // 时间显示
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = formatTime(duration),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    // 右下角保存按钮（向上移动30dp）
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 42.dp, end = 12.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f)
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val success = saveVideoToGallery(context, videoUrl)
                                    Toast.makeText(
                                        context,
                                        if (success) "已保存到相册/ComfyUI" else "保存失败",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) {
                            Text("保存", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 保存视频到相册
 */
private suspend fun saveVideoToGallery(context: android.content.Context, videoPath: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val videoFile = File(videoPath)
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

/**
 * 格式化时间（毫秒 → mm:ss）
 */
private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * 视频缩略图卡片
 * 点击后打开全屏播放器
 */
@Composable
fun VideoThumbnailCard(
    videoUrl: String,
    videoName: String,
    duration: String? = null,
    modifier: Modifier = Modifier
) {
    var showPlayer by remember { mutableStateOf(false) }
    
    Card(
        onClick = { showPlayer = true },
        modifier = modifier
    ) {
        Box {
            // 缩略图区域（可以用第一帧或占位符）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // 时长标签（右下角）
            if (duration != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = duration,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        // 文件名
        Text(
            text = videoName,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    
    // 全屏播放器对话框
    if (showPlayer) {
        VideoPlayerDialog(
            videoUrl = videoUrl,
            videoName = videoName,
            onDismiss = { showPlayer = false }
        )
    }
}

