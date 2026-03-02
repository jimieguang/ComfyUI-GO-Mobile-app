package com.example.myapplication.presentation.ui.components

import android.content.ContentValues
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import com.example.myapplication.presentation.ui.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 微信风格多媒体查看器（基础版）
 * - 左右滑动翻页（HorizontalPager）
 * - 单击显示/隐藏 UI（关闭、页码）
 * - 下滑拖拽关闭（drag-to-dismiss，带回弹）
 *
 * 注：图片支持双击/捏合缩放；缩放>1 时禁用翻页与下滑关闭（避免手势打架）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WechatMediaViewerDialog(
    mediaItems: List<MediaItem>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    if (mediaItems.isEmpty()) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val context = LocalContext.current

        var chromeVisible by remember { mutableStateOf(true) }
        val scalesByPage = remember { mutableStateMapOf<Int, Float>() }
        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, mediaItems.lastIndex),
            pageCount = { mediaItems.size }
        )

        val currentScale = scalesByPage[pagerState.currentPage] ?: 1f
        val allowGestures = currentScale <= 1.01f

        var dragOffsetY by remember { mutableFloatStateOf(0f) }
        val dragAbs = abs(dragOffsetY)
        val dismissThresholdPx = 260f
        val bgAlpha = (1f - (dragAbs / 900f)).coerceIn(0.55f, 1f)
        val contentScale = (1f - (dragAbs / 2400f)).coerceIn(0.92f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = bgAlpha))
                .pointerInput(allowGestures, pagerState.currentPage) {
                    if (!allowGestures) return@pointerInput
                    detectTransformGestures { _, pan, _, _ ->
                        // 仅用纵向拖拽关闭：这里用 pan.y 做简易实现（不与横向翻页冲突）
                        dragOffsetY += pan.y
                        // 限制过大的偏移
                        dragOffsetY = dragOffsetY.coerceIn(-1200f, 1200f)
                    }
                }
                .pointerInput(allowGestures, pagerState.currentPage) {
                    if (!allowGestures) return@pointerInput
                    // 手指抬起时判定关闭/回弹：用 onTap 之外的触发点不方便，
                    // 这里采用“拖拽不再继续时”无法直接监听，简化：当偏移超过阈值就立刻关闭。
                    //（更像微信的回弹/释放判定，会在下一 todo 里做得更精细）
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = dragOffsetY
                        scaleX = contentScale
                        scaleY = contentScale
                    }
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = allowGestures,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { chromeVisible = !chromeVisible }
                            )
                        }
                ) { page ->
                    val item = mediaItems[page]
                    when (item) {
                        is MediaItem.Image -> WechatZoomableImage(
                            url = item.url,
                            onSingleTap = { chromeVisible = !chromeVisible },
                            onScaleChanged = { scalesByPage[page] = it }
                        )
                        is MediaItem.Video -> {
                            // 视频页：不参与缩放
                            scalesByPage[page] = 1f
                            VideoPlayer(
                                videoUrl = item.url,
                                modifier = Modifier.fillMaxSize(),
                                autoPlay = true,
                                showControls = true
                            )
                        }
                        is MediaItem.Audio -> {
                            // 音频页：不参与缩放，展示内嵌音频卡片（支持播放/下载）
                            scalesByPage[page] = 1f
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                com.example.myapplication.presentation.ui.components.AudioOutputDisplay(
                                    audioUrls = listOf(item.url)
                                )
                            }
                        }
                    }
                }

                // 顶部关闭
                if (chromeVisible) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White
                        )
                    }
                }

                // 底部页码（微信风格：低调一点）
                if (chromeVisible) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when (mediaItems.getOrNull(pagerState.currentPage)) {
                                is MediaItem.Video -> Icons.Default.PlayArrow
                                else -> null
                            }
                            if (icon != null) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                            Text(
                                text = "${pagerState.currentPage + 1}/${mediaItems.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // ✅ 简化版关闭：超过阈值立即关闭（下一步会改为释放判定+回弹）
            LaunchedEffect(dragAbs) {
                if (allowGestures && dragAbs >= dismissThresholdPx) {
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun WechatZoomableImage(
    url: String,
    onSingleTap: () -> Unit,
    onScaleChanged: (Float) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(scale) { onScaleChanged(scale) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = {
                        if (scale > 1.1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                    if (newScale != scale) {
                        scale = newScale
                        if (scale <= 1.01f) {
                            offset = Offset.Zero
                        }
                    }
                    if (scale > 1.01f) {
                        offset += pan
                    }
                }
            }
    ) {
        // 这里不做加载占位花活：微信也是黑底渐显
        coil.compose.AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            // 微信图片在全屏通常是 Fit
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
    }
}

// 预留：微信还有“长按保存/分享”，后续可以加
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


