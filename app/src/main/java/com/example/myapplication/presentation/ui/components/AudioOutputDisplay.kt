package com.example.myapplication.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.myapplication.util.MediaSaveUtils
import kotlinx.coroutines.launch

// 🎵 自定义暂停图标（两个竖杠）
private val PauseIcon: ImageVector
    get() {
        return ImageVector.Builder(
            name = "Pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                // 左边的竖杠
                moveTo(6f, 4f)
                lineTo(10f, 4f)
                lineTo(10f, 20f)
                lineTo(6f, 20f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                // 右边的竖杠
                moveTo(14f, 4f)
                lineTo(18f, 4f)
                lineTo(18f, 20f)
                lineTo(14f, 20f)
                close()
            }
        }.build()
    }

/**
 * 音频输出显示组件
 * 用于显示工作流执行后的音频结果列表
 */
@Composable
fun AudioOutputDisplay(
    audioUrls: List<String>,
    modifier: Modifier = Modifier
) {
    if (audioUrls.isEmpty()) {
        return
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 标题
        Text(
            text = "🎵 音频结果",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        // 音频列表
        audioUrls.forEachIndexed { index, audioUrl ->
            AudioResultCard(
                audioUrl = audioUrl,
                audioName = "音频 ${index + 1}"
            )
        }
    }
}

/**
 * 单个音频结果卡片
 */
@Composable
private fun AudioResultCard(
    audioUrl: String,
    audioName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    
    // 创建 ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放/暂停按钮
            FilledIconButton(
                onClick = {
                    if (isPlaying) {
                        exoPlayer.pause()
                        isPlaying = false
                    } else {
                        exoPlayer.setMediaItem(MediaItem.fromUri(audioUrl))
                        exoPlayer.prepare()
                        exoPlayer.play()
                        isPlaying = true
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) PauseIcon else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(28.dp)
                )
            }
            
            // 音频信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audioName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = extractFilename(audioUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 右侧下载/保存按钮
            IconButton(
                onClick = {
                    scope.launch {
                        val ok = MediaSaveUtils.saveAudioToGallery(context, audioUrl)
                        android.widget.Toast.makeText(
                            context,
                            if (ok) "已保存到音乐/相册" else "保存失败",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "下载",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 从URL中提取文件名
 */
private fun extractFilename(url: String): String {
    return try {
        val filename = url.substringAfter("filename=").substringBefore("&")
        java.net.URLDecoder.decode(filename, "UTF-8")
    } catch (e: Exception) {
        "未知文件"
    }
}

