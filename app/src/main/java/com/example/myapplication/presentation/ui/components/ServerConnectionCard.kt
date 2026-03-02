package com.example.myapplication.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.model.ConnectionState
import com.example.myapplication.presentation.viewmodel.ServerConnectionViewModel

/**
 * 服务器连接配置内容组件
 * 提供服务器URL输入、连接状态显示和连接操作
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConnectionCard(
    uiState: ServerConnectionViewModel.ServerConnectionUiState,
    onServerUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClearError: () -> Unit,
    onFreeMemory: (() -> Unit)? = null,
    onUnloadModels: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    alwaysExpanded: Boolean = false  // 新增参数：是否始终展开
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // 连接状态对应的颜色动画
    val connectionColor by animateColorAsState(
        targetValue = when (uiState.connectionState) {
            ConnectionState.CONNECTED -> Color(0xFF4CAF50)
            ConnectionState.CONNECTING -> Color(0xFFFF9800)
            ConnectionState.ERROR -> Color(0xFFF44336)
            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(300),
        label = "connection_color"
    )
    
    var collapsed by rememberSaveable { mutableStateOf(false) }
    
    // 如果 alwaysExpanded = true，强制展开
    val isExpanded = alwaysExpanded || !collapsed
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题和状态指示器
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ComfyUI 服务器连接",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 只有在非强制展开模式下才显示折叠按钮
            if (!alwaysExpanded) {
                IconButton(onClick = { collapsed = !collapsed }) {
                    Icon(
                        imageVector = if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                        contentDescription = if (collapsed) "展开" else "折叠",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            
            ConnectionStatusIndicator(
                connectionState = uiState.connectionState,
                color = connectionColor
            )
        }
            
            if (isExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 胶囊样式的服务器地址输入框（自绘容器，严格 40.dp 高度）
                val outlineColor = when {
                    uiState.showUrlError -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                }
                var isFocused by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .border(
                            width = 1.dp,
                            color = if (isFocused && !uiState.showUrlError) MaterialTheme.colorScheme.primary else outlineColor,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                ) {
                    // 预留右侧错误清除按钮的内边距（32.dp）避免文本被遮挡
                    val endPadding = if (uiState.showUrlError) 36.dp else 12.dp
                    androidx.compose.foundation.text.BasicTextField(
                        value = uiState.serverUrl,
                        onValueChange = onServerUrlChange,
                        singleLine = true,
                        enabled = !uiState.isConnecting,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                if (uiState.connectionState == ConnectionState.DISCONNECTED) {
                                    onConnect()
                                }
                            }
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = endPadding)
                            .onFocusChanged { isFocused = it.isFocused }
                    )

                    // 占位提示
                    if (uiState.serverUrl.isBlank()) {
                        Text(
                            text = "服务器地址",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                        )
                    }

                    // 错误清除按钮（仅在有错误时显示）
                    if (uiState.showUrlError) {
                        IconButton(
                            onClick = onClearError,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "清除错误",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                
                // 连接按钮移到右边
                when (uiState.connectionState) {
                    ConnectionState.DISCONNECTED -> {
                        Button(
                            onClick = onConnect,
                            enabled = !uiState.isConnecting,
                            modifier = Modifier.height(40.dp) // 与选择文件按钮高度一致
                        ) {
                            if (uiState.isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                if (uiState.isConnecting) "连接中" else "连接",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    ConnectionState.CONNECTING -> {
                        Button(
                            onClick = { },
                            enabled = false,
                            modifier = Modifier.height(40.dp) // 与选择文件按钮高度一致
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "连接中",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    ConnectionState.CONNECTED -> {
                        Button(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.height(40.dp) // 与选择文件按钮高度一致
                        ) {
                            Text(
                                "断开",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    ConnectionState.ERROR -> {
                        Button(
                            onClick = onConnect,
                            modifier = Modifier.height(40.dp) // 与选择文件按钮高度一致
                        ) {
                            Text(
                                "重连",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // 资源释放/卸载（仅在已连接时显示）
            if (uiState.connectionState == ConnectionState.CONNECTED &&
                (onFreeMemory != null || onUnloadModels != null)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onFreeMemory != null) {
                        OutlinedButton(
                            onClick = onFreeMemory,
                            modifier = Modifier
                                .height(40.dp)
                                .weight(1f)
                        ) {
                            Text("释放内存", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (onUnloadModels != null) {
                        OutlinedButton(
                            onClick = onUnloadModels,
                            modifier = Modifier
                                .height(40.dp)
                                .weight(1f)
                        ) {
                            Text("卸载模型", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            
            // 错误信息显示（移到输入框下方）
            if (uiState.showUrlError) {
                Text(
                    text = uiState.urlErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp) // 移除左侧缩进
                )
            }

            // 状态提示（非错误）
            if (uiState.statusMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClearError,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            
            // 错误信息显示
            if (uiState.errorMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClearError,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 连接状态指示器
 */
@Composable
private fun ConnectionStatusIndicator(
    connectionState: ConnectionState,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态点
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = color,
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 状态文本
        Text(
            text = when (connectionState) {
                ConnectionState.CONNECTED -> "已连接"
                ConnectionState.CONNECTING -> "连接中"
                ConnectionState.ERROR -> "连接失败"
                ConnectionState.DISCONNECTED -> "未连接"
            },
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
