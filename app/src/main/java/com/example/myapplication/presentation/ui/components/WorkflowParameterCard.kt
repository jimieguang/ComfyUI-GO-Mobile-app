package com.example.myapplication.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import kotlinx.serialization.json.JsonPrimitive
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.example.myapplication.domain.parser.WorkflowParser
import com.example.myapplication.presentation.viewmodel.WorkflowParserViewModel
import com.example.myapplication.presentation.ui.components.parameter.*
import kotlinx.serialization.json.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/**
 * 工作流参数卡片组件
 * 显示和编辑工作流参数，直接显示所有节点卡片
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WorkflowParameterCard(
    uiState: WorkflowParserViewModel.WorkflowParserUiState,
    onParameterValueChange: (String, JsonElement) -> Unit,
    mediaCallbacks: MediaCallbacks = MediaCallbacks.Empty,
    selectionModeNodeId: String? = null,
    selectedParamIds: Set<String> = emptySet(),
    onToggleParamSelected: (String) -> Unit = {},
    onLongPressNode: (String) -> Unit = {},
    getSeedMode: (paramId: String) -> WorkflowParserViewModel.SeedMode = { WorkflowParserViewModel.SeedMode.FIXED },
    setSeedMode: (paramId: String, mode: WorkflowParserViewModel.SeedMode) -> Unit = { _, _ -> },
    bringIntoViewRequesters: Map<String, BringIntoViewRequester> = emptyMap(),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 紧凑显示所有节点实例卡片
        uiState.nodeInstances.forEach { nodeInstance ->
            val requester = bringIntoViewRequesters[nodeInstance.nodeId]
            NodeInstanceCard(
                nodeInstance = nodeInstance,
                parameterValues = uiState.parameterValues,
                onParameterValueChange = onParameterValueChange,
                mediaCallbacks = mediaCallbacks.copy(videoThumbnails = uiState.videoThumbnails),
                selectionModeNodeId = selectionModeNodeId,
                selectedParamIds = selectedParamIds,
                onToggleParamSelected = onToggleParamSelected,
                onLongPressNode = onLongPressNode,
                getSeedMode = getSeedMode,
                setSeedMode = setSeedMode,
                bringIntoViewRequester = requester
            )
        }
    }
}

/**
 * 节点实例卡片 - 显示单个节点实例的参数
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NodeInstanceCard(
    nodeInstance: WorkflowParserViewModel.NodeInstance,
    parameterValues: Map<String, JsonElement>,
    onParameterValueChange: (String, JsonElement) -> Unit,
    mediaCallbacks: MediaCallbacks,
    selectionModeNodeId: String?,
    selectedParamIds: Set<String>,
    onToggleParamSelected: (String) -> Unit,
    onLongPressNode: (String) -> Unit,
    getSeedMode: (paramId: String) -> WorkflowParserViewModel.SeedMode,
    setSeedMode: (paramId: String, mode: WorkflowParserViewModel.SeedMode) -> Unit,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (bringIntoViewRequester != null) Modifier.bringIntoViewRequester(bringIntoViewRequester) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFB5D9FB)
        ),
        border = BorderStroke(1.dp, Color.Gray),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(nodeInstance.nodeId) {
                    detectTapGestures(
                        onLongPress = { onLongPressNode(nodeInstance.nodeId) }
                        // 不处理onTap，让子组件接收点击事件
                    )
                }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 节点标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = nodeInstance.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // 移除参数数量等额外信息
                }
            }
            
            // 参数列表
            nodeInstance.parameters.forEach { param ->
                val showSelectionButton = selectionModeNodeId == nodeInstance.nodeId
                val isSelected = selectedParamIds.contains(param.id)
                
                AdvancedParameterInputField(
                    parameter = param,
                    currentValue = parameterValues[param.id] ?: param.value,
                    onValueChange = { newValue ->
                        onParameterValueChange(param.id, newValue)
                    },
                    mediaCallbacks = mediaCallbacks,
                    getSeedMode = getSeedMode,
                    setSeedMode = setSeedMode,
                    compact = false,
                    showSelectionButton = showSelectionButton,
                    isSelected = isSelected,
                    onToggleSelection = { onToggleParamSelected(param.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickParamSelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val bgColor = MaterialTheme.colorScheme.surface
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface

    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        Row(
            modifier = modifier
                .height(IntrinsicSize.Min)
                .border(1.dp, borderColor.copy(alpha = if (selected) 0.6f else 0.8f), shape)
                .background(bgColor, shape)
                .clickable(
                    indication = LocalIndication.current,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = textColor)
        }
    }
}

/**
 * 高级参数输入字段 - 使用新的高级组件
 */
@Composable
fun AdvancedParameterInputField(
    parameter: WorkflowParser.EditableParam,
    currentValue: JsonElement,
    onValueChange: (JsonElement) -> Unit,
    mediaCallbacks: MediaCallbacks = MediaCallbacks.Empty,
    getSeedMode: (paramId: String) -> WorkflowParserViewModel.SeedMode = { WorkflowParserViewModel.SeedMode.FIXED },
    setSeedMode: (paramId: String, mode: WorkflowParserViewModel.SeedMode) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showSelectionButton: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {}
) {
    // 使用Column布局，让选择按钮与输入区域对齐
    Column(modifier = modifier.fillMaxWidth()) {
        // 外部统一标题（除了自带标题的组件）
        val needsExternalTitle = when (parameter.uiConfig.type) {
            WorkflowParser.UiType.CHECKBOX, WorkflowParser.UiType.UPLOAD_IMAGE, WorkflowParser.UiType.UPLOAD_VIDEO, WorkflowParser.UiType.UPLOAD_AUDIO -> false
            else -> true
        }
        
        if (needsExternalTitle) {
            // 标题已经在 AdvancedTextInput 内部处理了，这里不需要额外显示
            // 只有非文本类型的参数才在这里显示标题
            val isTextType = parameter.uiConfig.type == WorkflowParser.UiType.TEXT || 
                            parameter.uiConfig.type == WorkflowParser.UiType.MULTILINE_TEXT
            
            if (!isTextType) {
                // 判断是否为提示词参数（用于设置颜色）
                val isPrompt = parameter.classType.contains("CLIPTextEncode") ||
                               parameter.name.lowercase().contains("prompt") ||
                               (parameter.name.lowercase() == "text" && parameter.type == WorkflowParser.ParamType.STRING)
                
                Text(
                    text = parameter.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isPrompt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = if (compact) 2.dp else 6.dp)
                )
            }
        }
        
        // 输入区域与选择按钮的行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (parameter.uiConfig.type) {
                    WorkflowParser.UiType.TEXT, WorkflowParser.UiType.MULTILINE_TEXT -> {
                        AdvancedTextInput(
                            parameter = parameter,
                            currentValue = currentValue,
                            onValueChange = onValueChange,
                            compact = compact,
                            hideTitle = needsExternalTitle
                        )
                    }
                    
                    WorkflowParser.UiType.NUMBER -> {
                        AdvancedNumberInput(
                            parameter = parameter,
                            currentValue = currentValue,
                            onValueChange = onValueChange,
                            getSeedMode = getSeedMode,
                            setSeedMode = setSeedMode,
                            compact = compact,
                            hideTitle = needsExternalTitle
                        )
                    }
                    
                    WorkflowParser.UiType.CHECKBOX -> {
                        AdvancedCheckboxInput(
                            parameter = parameter,
                            currentValue = currentValue,
                            onValueChange = onValueChange
                        )
                    }
                    
                    WorkflowParser.UiType.SELECT -> {
                        AdvancedSelectInput(
                            parameter = parameter,
                            currentValue = currentValue,
                            onValueChange = onValueChange,
                            compact = compact,
                            hideTitle = needsExternalTitle
                        )
                    }
                    
                    WorkflowParser.UiType.UPLOAD_IMAGE -> {
                        ImageUploadInput(
                            parameter = parameter,
                            currentValue = currentValue,
                            onValueChange = onValueChange,
                            getStagedImageBytes = { mediaCallbacks.getStagedImageBytes(parameter.id) },
                            onStageImage = mediaCallbacks.onStageImage,
                            onEditMask = mediaCallbacks.onEditMask
                        )
                    }
                    
                    WorkflowParser.UiType.UPLOAD_VIDEO -> {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val scope = rememberCoroutineScope()
                        
                        VideoParameterInput(
                            label = parameter.displayName,
                            currentValue = currentValue.toString(),
                            onVideoSelected = { uri ->
                                scope.launch {
                                    try {
                                        val fileName = com.example.myapplication.util.VideoUtils.getFileName(context, uri)
                                        val fileSize = com.example.myapplication.util.VideoUtils.getFileSize(context, uri)
                                        mediaCallbacks.onStageVideo(parameter.id, uri, fileName, fileSize, 0L)
                                    } catch (e: Exception) {
                                        android.util.Log.e("WorkflowParamCard", "视频读取失败: ${e.message}")
                                    }
                                }
                            },
                            onVideoRemove = { mediaCallbacks.onRemoveVideo(parameter.id) },
                            isUploading = false,
                            uploadProgress = 0f,
                            thumbnail = mediaCallbacks.videoThumbnails[parameter.id],
                            videoUri = mediaCallbacks.getVideoUri(parameter.id)
                        )
                    }
                    
                    WorkflowParser.UiType.UPLOAD_AUDIO -> {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val scope = rememberCoroutineScope()
                        
                        com.example.myapplication.presentation.ui.components.parameter.AudioParameterInput(
                            label = parameter.displayName,
                            currentValue = currentValue.toString(),
                            onAudioSelected = { uri ->
                                scope.launch {
                                    try {
                                        val fileName = com.example.myapplication.util.AudioUtils.getFileName(context, uri)
                                        val fileSize = com.example.myapplication.util.AudioUtils.getFileSize(context, uri)
                                        mediaCallbacks.onStageAudio(parameter.id, uri, fileName, fileSize, 0L)
                                    } catch (e: Exception) {
                                        android.util.Log.e("WorkflowParamCard", "音频读取失败: ${e.message}")
                                    }
                                }
                            },
                            onAudioRemove = { mediaCallbacks.onRemoveAudio(parameter.id) },
                            isUploading = false,
                            uploadProgress = 0f,
                            audioUri = mediaCallbacks.getAudioUri(parameter.id)
                        )
                    }
                }
            }
            
            // 选择按钮（仅在选择模式下显示，与输入区域对齐）
            if (showSelectionButton) {
                Spacer(modifier = Modifier.width(8.dp))
                QuickParamSelectChip(
                    label = if (isSelected) "已选" else "选择",
                    selected = isSelected,
                    onClick = onToggleSelection
                )
            }
        }
    }
}
