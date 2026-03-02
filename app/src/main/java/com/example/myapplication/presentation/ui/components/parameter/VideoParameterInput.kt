package com.example.myapplication.presentation.ui.components.parameter

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.myapplication.util.VideoUtils
import kotlinx.coroutines.launch

/**
 * 视频参数输入组件
 * 支持选择、上传和显示进度以及视频缩略图预览
 */
@Composable
fun VideoParameterInput(
    label: String,
    currentValue: String,
    onVideoSelected: (Uri) -> Unit,
    onVideoRemove: () -> Unit,
    isUploading: Boolean = false,
    uploadProgress: Float = 0f,
    thumbnail: Bitmap? = null,  // 🎥 视频缩略图
    videoUri: Uri? = null,  // 🎥 视频URI用于播放
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 🎥 视频播放对话框状态
    var showVideoPlayer by remember { mutableStateOf(false) }
    
    // 视频选择器
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // 检查文件大小
            val fileSize = VideoUtils.getFileSize(context, selectedUri)
            if (VideoUtils.isLargeFile(fileSize)) {
                // TODO: 显示警告对话框（可在 ViewModel 中处理）
            }
            onVideoSelected(selectedUri)
        }
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
            // 已选择视频
            VideoSelectedCard(
                videoName = currentValue.removeSurrounding("\""),
                isUploading = isUploading,
                uploadProgress = uploadProgress,
                thumbnail = thumbnail,  // 🎥 传递缩略图
                onRemove = onVideoRemove,
                onPlayVideo = {
                    // 点击播放视频
                    if (videoUri != null) {
                        showVideoPlayer = true
                    }
                }
            )
        } else {
            // 未选择视频 - 显示选择按钮
            OutlinedButton(
                onClick = { videoPickerLauncher.launch("video/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUploading
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "选择视频"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("选择视频文件")
            }
        }
    }
    
    // 🎥 视频播放对话框
    if (showVideoPlayer && videoUri != null) {
        com.example.myapplication.presentation.ui.components.VideoPlayerDialog(
            videoUrl = videoUri.toString(),
            videoName = currentValue.removeSurrounding("\""),
            onDismiss = { showVideoPlayer = false }
        )
    }
}

/**
 * 已选择视频卡片
 */
@Composable
private fun VideoSelectedCard(
    videoName: String,
    isUploading: Boolean,
    uploadProgress: Float,
    thumbnail: Bitmap? = null,  // 🎥 缩略图
    onRemove: () -> Unit,
    onPlayVideo: () -> Unit  // 🎥 播放视频回调
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 🎥 视频缩略图（如果有）
            if (thumbnail != null) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            enabled = !isUploading,
                            onClick = { onPlayVideo() },
                            indication = null,  // 🔧 明确指定 indication 为 null
                            interactionSource = remember { MutableInteractionSource() }
                        )  // 🎥 点击播放
                ) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "视频预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // 视频播放图标覆盖层
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "播放视频",
                                modifier = Modifier.padding(8.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // 文件信息和操作区域
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
                // 文件名和状态行
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
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = videoName,
                        style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                    )
                    
                    if (isUploading) {
                        Text(
                            text = "上传中 ${(uploadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                    // 删除按钮（移除编辑按钮）
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
            
            // 上传进度条
            if (isUploading) {
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
                }
            }
        }
    }
}

/**
 * 视频上传状态数据类
 */
data class VideoUploadState(
    val isUploading: Boolean = false,
    val progress: Float = 0f,
    val fileName: String = "",
    val error: String? = null
)

