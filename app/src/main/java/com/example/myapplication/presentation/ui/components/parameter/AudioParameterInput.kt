package com.example.myapplication.presentation.ui.components.parameter

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.example.myapplication.util.AudioUtils
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
 * 音频参数输入组件
 * 支持选择、上传和显示进度
 */
@Composable
fun AudioParameterInput(
    label: String,
    currentValue: String,
    onAudioSelected: (Uri) -> Unit,
    onAudioRemove: () -> Unit,
    isUploading: Boolean = false,
    uploadProgress: Float = 0f,
    audioUri: Uri? = null,  // 🎵 音频URI用于播放
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 🎵 音频播放状态
    var isPlaying by remember { mutableStateOf(false) }
    
    // 🎵 创建 ExoPlayer 用于后台播放
    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    // 🎵 音频选择器 - 使用自定义Intent支持更多文件管理器
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            // 检查文件大小
            val fileSize = AudioUtils.getFileSize(context, uri)
            if (AudioUtils.isLargeFile(fileSize)) {
                // TODO: 显示警告对话框（可在 ViewModel 中处理）
            }
            onAudioSelected(uri)
        }
    }
    
    // 创建自定义音频选择Intent（支持更多格式和文件管理器）
    fun createAudioPickerIntent(): Intent {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            // 添加额外的音频MIME类型支持
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "audio/*",
                "audio/mpeg",      // MP3
                "audio/wav",       // WAV
                "audio/ogg",       // OGG
                "audio/flac",      // FLAC
                "audio/mp4",       // M4A
                "audio/aac",       // AAC
                "audio/x-ms-wma",  // WMA
                "audio/opus",      // OPUS
                "audio/x-aiff"     // AIFF
            ))
        }
        // 如果可能，显示所有可用的文件选择器
        return Intent.createChooser(intent, "选择音频文件")
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // 标签
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 当前值显示或选择按钮
        if (currentValue.isNotEmpty() && currentValue != "\"\"") {
            // 已选择音频
            AudioSelectedCard(
                audioName = currentValue.removeSurrounding("\""),
                isUploading = isUploading,
                uploadProgress = uploadProgress,
                isPlaying = isPlaying,
                onRemove = onAudioRemove,
                onTogglePlay = {
                    // 直接播放/暂停音频
                    if (audioUri != null) {
                        if (isPlaying) {
                            exoPlayer.pause()
                            isPlaying = false
                        } else {
                            exoPlayer.setMediaItem(MediaItem.fromUri(audioUri))
                            exoPlayer.prepare()
                            exoPlayer.play()
                            isPlaying = true
                        }
                    }
                }
            )
        } else {
            // 未选择音频 - 显示选择按钮
            OutlinedButton(
                onClick = { 
                    audioPickerLauncher.launch(createAudioPickerIntent())
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUploading
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "选择音频"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("选择音频文件")
            }
        }
    }
    
}

/**
 * 已选择音频卡片
 */
@Composable
private fun AudioSelectedCard(
    audioName: String,
    isUploading: Boolean,
    uploadProgress: Float,
    isPlaying: Boolean,
    onRemove: () -> Unit,
    onTogglePlay: () -> Unit // 🎵 播放/暂停切换
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🎵 音频播放/暂停按钮
            if (!isUploading) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) PauseIcon else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    progress = { uploadProgress }
                )
            }
            
            // 文件信息和操作区域
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 文件名
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isUploading) Icons.Default.Settings else Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isUploading) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = audioName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // 上传状态
                if (isUploading) {
                    Text(
                        text = "上传中 ${(uploadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            
            // 删除按钮
            if (!isUploading) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "移除",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

