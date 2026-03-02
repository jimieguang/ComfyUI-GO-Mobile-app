package com.example.myapplication.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.myapplication.presentation.util.LoadedState
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

/**
 * 工作流存储页面
 * 对应Web版的工作流存储功能：
 * - 显示保存的工作流列表
 * - 工作流分类管理
 * - 导入导出工作流
 * - 工作流编辑和删除
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowStorageScreen(
    viewModel: com.example.myapplication.presentation.viewmodel.SavedWorkflowsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onUseWorkflow: (com.example.myapplication.data.model.SavedWorkflow) -> Unit = {},
    onImportFromFile: (android.net.Uri) -> Unit = {},
    onImportFromClipboard: () -> Unit = {}
) {
    val saved by viewModel.items.collectAsState()
    var renameTarget by remember { mutableStateOf<com.example.myapplication.data.model.SavedWorkflow?>(null) }
    var newName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<com.example.myapplication.data.model.SavedWorkflow?>(null) }
    
    var showAddDialog by remember { mutableStateOf(false) }
    
    // 剪贴板命名对话框相关
    val showClipboardNameDialog by viewModel.showClipboardNameDialog.collectAsState()
    var workflowNameInput by remember { mutableStateOf("") }
    
    // 当对话框显示时，重置输入
    LaunchedEffect(showClipboardNameDialog) {
        if (showClipboardNameDialog) {
            workflowNameInput = ""
        }
    }

    // 文件选择器（JSON 或 文本）
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) onImportFromFile(uri)
            showAddDialog = false
        }
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 页面标题
        Text(
            text = "工作流存储",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 统计信息和操作按钮
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
                // 左侧：统计信息
                StatisticItem("总工作流", saved.size.toString())
                
                // 右侧：操作按钮
                OutlinedButton(
                    onClick = { showAddDialog = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导入工作流")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 工作流列表
        if (saved.isEmpty()) {
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
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无保存的工作流",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "导入或保存工作流后将在这里显示",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 62.dp)
            ) {
                items(saved) { item ->
                    val isLoaded = (LoadedState.source == LoadedState.Source.STORAGE && LoadedState.storageId == item.id)
                    run {
                        val scope = rememberCoroutineScope()
                        val offsetX = remember { Animatable(0f) }
                        var cardWidthPx by remember { mutableStateOf(0) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { cardWidthPx = it.width }
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
                                                viewModel.setLastLoadedId(item.id)
                                                LoadedState.markStorage(item.id)
                                                onUseWorkflow(item)
                                                offsetX.snapTo(0f)
                                            }
                                        } else {
                                            scope.launch { offsetX.animateTo(0f, animationSpec = spring()) }
                                        }
                                    }
                                )
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
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "加载",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Box(modifier = Modifier.graphicsLayer { translationX = offsetX.value }) {
                                WorkflowItemCard(
                                    item = item,
                                    isLoaded = isLoaded,
                                    onLoad = { /* 已移除按钮加载，使用滑动加载 */ },
                                    onEdit = {
                                        renameTarget = item
                                        newName = item.name
                                    },
                                    onDelete = { deleteTarget = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 导入对话框
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("导入工作流") },
            text = {
                Column {
                    Text("选择导入方式:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            filePicker.launch(arrayOf("application/json", "text/plain"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("从文件导入")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onImportFromClipboard()
                            showAddDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("从剪贴板导入")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }

    // 重命名对话框
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名工作流") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = renameTarget!!.id
                    val name = newName.trim().ifEmpty { renameTarget!!.name }
                    viewModel.rename(id, name)
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } }
        )
    }

    // 删除确认对话框
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除工作流") },
            text = { Text("确定要删除「${deleteTarget!!.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(deleteTarget!!.id)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
    
    // 剪贴板工作流命名对话框
    if (showClipboardNameDialog) {
        AlertDialog(
            onDismissRequest = viewModel::cancelClipboardWorkflowImport,
            title = {
                Text(
                    text = "为工作流命名",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "已成功识别ComfyUI工作流格式！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "请为此工作流命名：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = workflowNameInput,
                        onValueChange = { workflowNameInput = it },
                        label = { Text("工作流名称") },
                        placeholder = { Text("例如：图像生成工作流") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    if (workflowNameInput.isBlank()) {
                        Text(
                            text = "提示：名称不能为空",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (workflowNameInput.isNotBlank()) {
                            viewModel.confirmClipboardWorkflowNameAndSave(workflowNameInput.trim())
                        }
                    },
                    enabled = workflowNameInput.isNotBlank()
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelClipboardWorkflowImport) {
                    Text("取消")
                }
            }
        )
    }
}

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

private const val SWIPE_VELOCITY_THRESHOLD: Float = 2500f // px/s，负值方向为向左

@Composable
private fun WorkflowItemCard(
    item: com.example.myapplication.data.model.SavedWorkflow,
    isLoaded: Boolean = false,
    onLoad: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE0EFFF)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：标题和时间信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "保存于 ${formatTime(item.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 右侧：状态和菜单按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isLoaded) {
                    Text(
                        text = "已加载",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60000 -> "刚刚"
        diff < 3600000 -> "${diff / 60000}分钟前"
        diff < 86400000 -> "${diff / 3600000}小时前"
        else -> "${diff / 86400000}天前"
    }
}

data class StoredWorkflow(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val timestamp: Long
)
