package com.example.myapplication.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
// remove duplicate ambiguous imports
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.myapplication.presentation.viewmodel.ExecutionHistoryViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.example.myapplication.presentation.ui.model.MediaItem
import com.example.myapplication.util.MediaSaveUtils

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ExecutionHistoryWaterfallScreen(
    viewModel: ExecutionHistoryViewModel = hiltViewModel(),
    onOpenGallery: (List<MediaItem>, Int) -> Unit
) {
    val records by viewModel.records.collectAsState()
    val outputs by viewModel.recordOutputs.collectAsState()
    LaunchedEffect(records) {
        // 进入页面后为所有当前记录触发一次输出加载（轻量查询，内部有去重）
        records.forEach { rec -> viewModel.ensureOutputsLoaded(rec.promptId) }
    }
    val orderedRecords = remember(records) { records.sortedByDescending { it.timestamp } }
    val recordItems = remember(orderedRecords, outputs) {
        orderedRecords.map { rec -> rec to (outputs[rec.promptId] ?: emptyList()) }
            .filter { (rec, urls) ->
                val hasImages = urls.isNotEmpty()
                val hasVideos = rec.localVideoPaths.any { it.isNotBlank() } || rec.videoUrls.isNotEmpty()
                val hasAudios = rec.localAudioPaths.any { it.isNotBlank() } || rec.audioUrls.isNotEmpty()
                hasImages || hasVideos || hasAudios
            }
    }

    // 列数选择（1/2/3列）
    var columns by remember { mutableStateOf(2) }

    var previewOpen by remember { mutableStateOf(false) }
    var previewStart by remember { mutableStateOf(0) }
    var previewMediaItems by remember { mutableStateOf(listOf<MediaItem>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题与列数切换
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "图片瀑布流",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            SegmentedButtons(columns) { columns = it }
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
                    val imageItems = urls.map { MediaItem.Image(it) }
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
                    val allMediaItems = imageItems + videoItems + audioItems

                    if (urls.isNotEmpty()) {
                        val cover = urls.first()
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
                                                .background(Color.Black)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = LocalIndication.current
                                                ) {
                                                    previewMediaItems = allMediaItems
                                                    previewStart = 0
                                                    previewOpen = true
                                                }
                                        ) {
                                            with(subScope) {
                                                SubcomposeAsyncImageContent(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Fit,
                                                    alignment = Alignment.Center
                                                )
                                            }
                                            val audioCount = rec.localAudioPaths.count { it.isNotBlank() }.takeIf { it > 0 }
                                                ?: rec.audioUrls.size
                                            val totalMediaCount = urls.size + rec.localVideoPaths.count { it.isNotBlank() } + audioCount
                                            if (totalMediaCount > 1) {
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
                                        }
                                    }
                                    else -> {
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
                        val hasAudio = audioItems.isNotEmpty()
                        if (hasAudio) {
                            // ✅ 音频占位：正方形（随列宽变化）
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = LocalIndication.current
                                    ) {
                                        previewMediaItems = allMediaItems
                                        previewStart = 0
                                        previewOpen = true
                                    }
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("🎵 音频", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // ✅ 视频占位：像图片一样显示缩略图（本地视频可抽帧；未下载则显示占位+播放图标）
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
                                        previewMediaItems = allMediaItems
                                        previewStart = 0
                                        previewOpen = true
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (thumbnail != null) {
                                        Image(
                                            bitmap = thumbnail!!.asImageBitmap(),
                                            contentDescription = "视频缩略图",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        // 未下载/无缩略图：保持“图片式”占位（不再用文字卡片）
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

    // 全屏多媒体预览对话框
    if (previewOpen) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        com.example.myapplication.presentation.ui.components.media.EnhancedMediaGallery(
            mediaItems = previewMediaItems,
            initialIndex = previewStart,
            onDismiss = { previewOpen = false },
            onSaveClick = { url ->
                scope.launch {
                    val ok = MediaSaveUtils.saveMediaToGallery(context, url)
                    Toast.makeText(context, if (ok) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

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

@Composable
private fun SegmentedButtons(current: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2, 3).forEach { c ->
            FilterChip(
                selected = current == c,
                onClick = { onChange(c) },
                label = { Text("${c}列") }
            )
        }
    }
}


