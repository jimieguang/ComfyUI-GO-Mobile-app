package com.example.myapplication.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.model.SystemStatsSimple

/**
 * 系统资源监控卡片
 * 显示 CPU、RAM、GPU 使用情况
 */
@Composable
fun SystemStatsCard(
    stats: SystemStatsSimple?,
    isAvailable: Boolean,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onStartPolling: () -> Unit,
    onStopPolling: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 自动开始轮询
    DisposableEffect(Unit) {
        onStartPolling()
        onDispose {
            onStopPolling()
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE0EFFF)  // 浅蓝色背景 (与主界面一致)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "系统资源监控",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 刷新按钮
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                }
            }
            
            // 内容区域
            AnimatedVisibility(
                visible = isAvailable && stats != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                stats?.let { s ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // CPU 使用率
                        ResourceBar(
                            label = "CPU",
                            percent = s.cpu_percent,
                            color = Color(0xFF2196F3)  // 蓝色
                        )
                        
                        // RAM 使用率
                        ResourceBar(
                            label = "RAM",
                            percent = s.ram_percent,
                            usedText = "%.1f GB".format(s.ram_used_gb),
                            color = Color(0xFF4CAF50)  // 绿色
                        )
                        
                        // GPU 显存
                        if (s.gpu_total_gb > 0) {
                            ResourceBar(
                                label = "VRAM",
                                percent = s.gpu_percent,
                                usedText = "%.1f / %.1f GB".format(s.gpu_used_gb, s.gpu_total_gb),
                                color = Color(0xFFFF9800)  // 橙色
                            )
                        }
                    }
                }
            }
            
            // 不可用提示
            AnimatedVisibility(
                visible = !isAvailable && !isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "⚠️ 系统监控不可用\n请在 ComfyUI 安装 comfyui_system_monitor 插件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 资源进度条
 */
@Composable
private fun ResourceBar(
    label: String,
    percent: Float,
    usedText: String? = null,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedPercent by animateFloatAsState(
        targetValue = percent / 100f,
        animationSpec = tween(durationMillis = 300),
        label = "progress"
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = usedText ?: "%.1f%%".format(percent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 进度条
        LinearProgressIndicator(
            progress = { animatedPercent.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

