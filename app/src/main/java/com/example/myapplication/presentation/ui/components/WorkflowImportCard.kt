package com.example.myapplication.presentation.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.domain.manager.WorkflowImportManager
import com.example.myapplication.presentation.viewmodel.WorkflowImportViewModel

/**
 * 工作流导入卡片组件
 * 提供多种方式导入ComfyUI工作流
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowImportCard(
    uiState: WorkflowImportViewModel.WorkflowImportUiState,
    onFileImport: (Uri) -> Unit,
    onClipboardImport: () -> Unit,
    onClearWorkflow: () -> Unit,
    onSaveWorkflow: (() -> Unit)? = null,
    onClearError: () -> Unit,
    onConfirmClipboardName: (String) -> Unit,
    onCancelClipboardImport: () -> Unit,
    onFolderSelected: (Uri) -> Unit = {},
    onOpenFolderPicker: () -> Unit = {},
    onFolderFileSelected: (com.example.myapplication.util.WorkflowFolderStore.FolderFile) -> Unit = {},
    onDismissFolderPicker: () -> Unit = {},
    onClearFolder: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 工作流命名对话框状态
    var workflowNameInput by remember { mutableStateOf("") }
    
    // 折叠状态
    var collapsed by rememberSaveable { mutableStateOf(false) }
    
    // 当对话框显示时，重置输入
    LaunchedEffect(uiState.showClipboardNameDialog) {
        if (uiState.showClipboardNameDialog) {
            workflowNameInput = ""
        }
    }
    
    // 文本导入已移除
    
    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onFileImport(it) }
    }

    // 工作流文件夹选择器（SAF 目录授权，一次授权长期有效）
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { onFolderSelected(it) }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE0EFFF)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            // 仅缩小卡片上下内边距，让卡片更紧凑；不改变内部控件大小与控件间距
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 标题和状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val headerTitle = if (uiState.hasWorkflow) {
                    val rawName = if (!uiState.workflowName.isNullOrBlank()) uiState.workflowName else "未命名工作流"
                    rawName.removeSuffix(".json")
                } else {
                    "工作流导入"
                }
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                IconButton(onClick = { collapsed = !collapsed }) {
                    Icon(
                        imageVector = if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                        contentDescription = if (collapsed) "展开" else "折叠",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 状态圆点
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (uiState.hasWorkflow) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.hasWorkflow) "已加载" else "未导入",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.hasWorkflow) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // 可折叠内容
            if (!collapsed) {
            // 错误信息显示
            if (uiState.showError) {
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
            
            // 导入选项（紧凑排布）
            if (!uiState.hasWorkflow || !uiState.isImporting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (uiState.hasFolder) {
                                onOpenFolderPicker()
                            } else {
                                folderPickerLauncher.launch(null)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isImporting
                    ) {
                        if (uiState.isImporting && uiState.importSource == WorkflowImportManager.ImportSource.FILE) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            when {
                                !uiState.hasFolder -> "选择文件夹"
                                uiState.hasWorkflow -> "更换文件"
                                else -> "选择文件"
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = onClipboardImport,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isImporting
                    ) {
                        if (uiState.isImporting && uiState.importSource == WorkflowImportManager.ImportSource.CLIPBOARD) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("剪贴板")
                    }
                }
            }
            
            // 工作流信息（两行展示）
            if (uiState.hasWorkflow && uiState.workflowInfo != null) {
                val sourceText = uiState.displaySource ?: getImportSourceText(uiState.importSource)
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 只保留一行：节点、输出、来源信息（工作流名已在标题栏展示）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 预留右侧空间给垃圾桶，避免遮挡文字（不改变高度）
                            .padding(end = if (onSaveWorkflow != null) 88.dp else 44.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "节点: ${uiState.workflowInfo.nodeCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "输出: ${uiState.workflowInfo.outputNodes}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "来源: ${sourceText}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 右下角操作区：保存（可选） + 清除（覆盖定位，不占用额外布局空间）
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(y = (-6).dp),  // 向上移动6dp
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onSaveWorkflow != null) {
                            IconButton(
                                onClick = onSaveWorkflow,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Save,
                                    contentDescription = "保存到库",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = onClearWorkflow,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "清除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            } // 折叠内容结束
        }
    }
    
    // 剪贴板工作流命名对话框
    if (uiState.showClipboardNameDialog) {
        AlertDialog(
            onDismissRequest = onCancelClipboardImport,
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
                            onConfirmClipboardName(workflowNameInput.trim())
                        }
                    },
                    enabled = workflowNameInput.isNotBlank()
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelClipboardImport) {
                    Text("取消")
                }
            }
        )
    }

    // 工作流文件夹文件列表弹层
    if (uiState.showFolderPicker) {
        val folderFiles = uiState.folderFiles
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismissFolderPicker,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.75f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.folderName ?: "工作流文件夹",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${folderFiles.size} 个 JSON 文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { folderPickerLauncher.launch(null) }) {
                            Text("换文件夹")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.isLoadingFolderFiles) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else if (folderFiles.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.folderError ?: "该文件夹下没有 .json 文件",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(folderFiles.size) { index ->
                                val file = folderFiles[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onFolderFileSelected(file) }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onClearFolder) {
                            Text("取消授权", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = onDismissFolderPicker) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 工作流信息卡片
 */
@Composable
private fun WorkflowInfoCard(
    workflowInfo: WorkflowImportManager.WorkflowInfo,
    importSource: WorkflowImportManager.ImportSource,
    workflowName: String?,
    displaySource: String?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "工作流信息",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    run {
                        val nameToShow = if (!workflowName.isNullOrBlank()) workflowName else "未命名工作流"
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "工作流名称: ${nameToShow}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                }
                
                TextButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("清除", style = MaterialTheme.typography.bodySmall)
                }
            }
            
            // 基本信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem("节点数量", workflowInfo.nodeCount.toString())
                InfoItem("输出节点", workflowInfo.outputNodes.toString())
                InfoItem("导入方式", displaySource ?: getImportSourceText(importSource))
            }
            
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
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

private fun getImportSourceText(source: WorkflowImportManager.ImportSource): String {
    return when (source) {
        WorkflowImportManager.ImportSource.FILE -> "文件"
        WorkflowImportManager.ImportSource.CLIPBOARD -> "剪贴板"
        WorkflowImportManager.ImportSource.TEXT_INPUT -> "文本输入"
        WorkflowImportManager.ImportSource.URL -> "URL"
        WorkflowImportManager.ImportSource.UNKNOWN -> "未知"
    }
}
