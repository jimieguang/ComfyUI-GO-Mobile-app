package com.example.myapplication.presentation.ui.components.parameter

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myapplication.domain.parser.WorkflowParser
import com.example.myapplication.presentation.viewmodel.WorkflowParserViewModel
import com.example.myapplication.presentation.viewmodel.LLMAssistantViewModel
import com.example.myapplication.presentation.ui.components.llm.*
import com.example.myapplication.util.ImageUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlin.math.roundToInt

/**
 * 高级数字输入组件 - 支持滑块、步进器等
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedNumberInput(
    parameter: WorkflowParser.EditableParam,
    currentValue: JsonElement,
    onValueChange: (JsonElement) -> Unit,
    getSeedMode: (paramId: String) -> WorkflowParserViewModel.SeedMode = { WorkflowParserViewModel.SeedMode.FIXED },
    setSeedMode: (paramId: String, mode: WorkflowParserViewModel.SeedMode) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    hideTitle: Boolean = false
) {
    val uiConfig = parameter.uiConfig
    val min = uiConfig.min?.toFloat() ?: 0f
    val max = uiConfig.max?.toFloat() ?: 100f
    val step = uiConfig.step?.toFloat() ?: 1f
    
    // 获取当前数值
    val currentNumber = when {
        currentValue is JsonPrimitive && currentValue.doubleOrNull != null -> currentValue.double.toFloat()
        currentValue is JsonPrimitive && currentValue.intOrNull != null -> currentValue.int.toFloat()
        else -> min
    }
    
    // 🔥 对于种子参数，直接获取Long值，避免Float精度丢失
    val currentSeedLong = if (uiConfig.isSeed && currentValue is JsonPrimitive) {
        currentValue.longOrNull ?: currentValue.intOrNull?.toLong() ?: currentNumber.toLong()
    } else {
        null
    }
    // ✅ 格式化显示值：用于“失焦/未编辑”时同步显示，避免清空时立刻回填旧值
    fun formatDisplayValue(): String {
        return if (uiConfig.isSeed && currentSeedLong != null) {
            currentSeedLong.toString()
        } else if (uiConfig.isSeed) {
            currentNumber.toLong().toString()
        } else if (step >= 1.0) {
            currentNumber.toInt().toString()
        } else {
            if (currentNumber % 1.0f == 0.0f) currentNumber.toInt().toString() else currentNumber.toString()
        }
    }

    // 文本输入框的编辑态：允许短暂为空，避免用户清空后被立即回填（你反馈的“清空后冒出数字”）
    var isFocused by remember(parameter.id) { mutableStateOf(false) }
    var textValue by remember(parameter.id) { mutableStateOf(formatDisplayValue()) }

    // 当外部值变化且当前不在编辑（未聚焦）时，同步输入框显示
    LaunchedEffect(currentValue, isFocused) {
        if (!isFocused) {
            textValue = formatDisplayValue()
        }
    }
    // 记忆“固定”所用的最近一次非随机种子（仅用于种子参数）
    var lastFixedSeed by remember(uiConfig.isSeed) {
        mutableStateOf<Long?>(
            if (uiConfig.isSeed && currentValue is JsonPrimitive) {
                currentValue.longOrNull?.takeIf { it >= 0 }
            } else null
        )
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)
    ) {
            // 参数标题和控制按钮
            if (!hideTitle) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = parameter.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        // 去除种子参数解释
                    }
                    
                    // 种子随机按钮已内嵌到输入框的 trailingIcon
                }
            }
            
            
            // 文本输入框
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (true) {
                    // 自绘 40.dp 高度的紧凑输入框
                    val shape = RoundedCornerShape(8.dp)
                    val containerColor = if (compact) Color(0xFFEFF5FF) else Color(0xFFE8F3FF)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                            .background(containerColor, shape)
                    ) {
                        // 预留尾部微调按钮宽度
                        val endPad = 36.dp
                        BasicTextField(
                            value = textValue,
                            onValueChange = { newText ->
                                // 判断是否为整数参数（step >= 1.0）
                                val isIntegerParam = uiConfig.isSeed || step >= 1.0
                                
                                if (isIntegerParam) {
                                    // 整数参数：只允许输入数字（过滤掉字母、小数点等）
                                    val filtered = newText.filter { it.isDigit() }
                                    // ✅ 允许用户清空（用于重新输入），不要立刻把旧值写回来
                                    if (filtered.isEmpty()) {
                                        textValue = ""
                                        return@BasicTextField
                                    }
                                    textValue = filtered
                                    
                                    if (uiConfig.isSeed) {
                                        // 种子参数：使用Long，不限制范围
                                        val numberValue = filtered.toLongOrNull()
                                        if (numberValue != null) {
                                            onValueChange(JsonPrimitive(numberValue))
                                        }
                                    } else {
                                        // 普通整数参数：使用Int，有范围限制
                                        val numberValue = filtered.toIntOrNull()
                                        if (numberValue != null) {
                                            val clampedValue = numberValue.coerceIn(
                                                (uiConfig.min ?: Int.MIN_VALUE.toDouble()).toInt(),
                                                (uiConfig.max ?: Int.MAX_VALUE.toDouble()).toInt()
                                            )
                                            onValueChange(JsonPrimitive(clampedValue))
                                        }
                                    }
                                } else {
                                    // 小数参数：允许输入数字和小数点
                                    val filtered = newText.filter { it.isDigit() || it == '.' }
                                    if (filtered.isEmpty()) {
                                        textValue = ""
                                        return@BasicTextField
                                    }
                                    textValue = filtered
                                    val numberValue = filtered.toDoubleOrNull()
                                    if (numberValue != null) {
                                        val clampedValue = numberValue.coerceIn(
                                            uiConfig.min ?: Double.MIN_VALUE,
                                            uiConfig.max ?: Double.MAX_VALUE
                                        )
                                        onValueChange(JsonPrimitive(clampedValue))
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (uiConfig.isSeed || step >= 1.0) KeyboardType.NumberPassword else KeyboardType.Decimal
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = endPad)
                                .onFocusChanged { focusState ->
                                    isFocused = focusState.isFocused
                                    // 失焦时，如果用户清空了输入，则恢复为当前值，避免出现“空值提交导致异常”
                                    if (!focusState.isFocused && textValue.isBlank()) {
                                        textValue = formatDisplayValue()
                                    }
                                }
                        )

                        // 微调按钮（紧凑版）
                        Column(
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(onClick = {
                                val isIntegerParam = uiConfig.isSeed || step >= 1.0
                                if (isIntegerParam) {
                                    if (uiConfig.isSeed) {
                                        // 种子参数：使用Long整数运算
                                        val cv = textValue.toLongOrNull() ?: currentNumber.toLong()
                                        val nv = cv + step.toLong()
                                        textValue = nv.toString()
                                        onValueChange(JsonPrimitive(nv))
                                    } else {
                                        // 普通整数参数：使用Int运算
                                        val cv = textValue.toIntOrNull() ?: currentNumber.toInt()
                                        val nv = (cv + step.toInt()).coerceAtMost(max.toInt())
                                        textValue = nv.toString()
                                        onValueChange(JsonPrimitive(nv))
                                    }
                                } else {
                                    // 小数参数：使用Double运算
                                    val cv = textValue.toDoubleOrNull() ?: currentNumber.toDouble()
                                    val nv = (cv + step).coerceAtMost(max.toDouble())
                                    // 如果是整数值，显示为整数；否则显示为小数
                                    textValue = if (nv % 1.0 == 0.0) nv.toInt().toString() else nv.toString()
                                    onValueChange(JsonPrimitive(nv))
                                }
                            }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "增加", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = {
                                val isIntegerParam = uiConfig.isSeed || step >= 1.0
                                if (isIntegerParam) {
                                    if (uiConfig.isSeed) {
                                        // 种子参数：使用Long整数运算
                                        val cv = textValue.toLongOrNull() ?: currentNumber.toLong()
                                        val nv = if (cv > 0) cv - step.toLong() else 0L
                                        textValue = nv.toString()
                                        onValueChange(JsonPrimitive(nv))
                                    } else {
                                        // 普通整数参数：使用Int运算
                                        val cv = textValue.toIntOrNull() ?: currentNumber.toInt()
                                        val nv = (cv - step.toInt()).coerceAtLeast(min.toInt())
                                        textValue = nv.toString()
                                        onValueChange(JsonPrimitive(nv))
                                    }
                                } else {
                                    // 小数参数：使用Double运算
                                    val cv = textValue.toDoubleOrNull() ?: currentNumber.toDouble()
                                    val nv = (cv - step).coerceAtLeast(min.toDouble())
                                    // 如果是整数值，显示为整数；否则显示为小数
                                    textValue = if (nv % 1.0 == 0.0) nv.toInt().toString() else nv.toString()
                                    onValueChange(JsonPrimitive(nv))
                                }
                            }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "减少", modifier = Modifier.size(16.dp))
                            }
                        }

                        if (textValue.isBlank()) {
                            Text(
                                text = parameter.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
                            )
                        }
                    }
                }
                // 种子模式选择：放在输入框右侧，与其它下拉一致，避免被裁切
                if (uiConfig.isSeed) {
                    var modeExpanded by remember { mutableStateOf(false) }
                    val modeLabels = mapOf(
                        WorkflowParserViewModel.SeedMode.FIXED to "固定",
                        WorkflowParserViewModel.SeedMode.INCREMENT to "增加",
                        WorkflowParserViewModel.SeedMode.DECREMENT to "减少",
                        WorkflowParserViewModel.SeedMode.RANDOM to "随机"
                    )
                    val currentMode = getSeedMode(parameter.id)
                    Spacer(Modifier.width(6.dp))
                    ExposedDropdownMenuBox(
                        expanded = modeExpanded,
                        onExpandedChange = { modeExpanded = it }
                    ) {
                        // 始终使用自绘 40dp 高度，保证参数设置页与快速参数统一
                        val shape = RoundedCornerShape(8.dp)
                        val containerColor = if (compact) Color(0xFFEFF5FF) else Color(0xFFE8F3FF)
                        Box(
                            modifier = Modifier
                                .menuAnchor()
                                .height(40.dp)
                                .widthIn(min = 96.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                                .background(containerColor, shape)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { modeExpanded = true }
                        ) {
                            Text(
                                text = modeLabels[currentMode] ?: "固定",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 12.dp)
                            )
                            Row(
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                            ) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded)
                            }
                        }
                        ExposedDropdownMenu(
                            expanded = modeExpanded,
                            onDismissRequest = { modeExpanded = false }
                        ) {
                            modeLabels.forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        setSeedMode(parameter.id, mode)
                                        modeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 种子模式选择已并入到输入框
            
        // 去除范围与步长信息的展示
    }
}

/**
 * 高级文本输入组件 - 支持多行、语法高亮等
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedTextInput(
    parameter: WorkflowParser.EditableParam,
    currentValue: JsonElement,
    onValueChange: (JsonElement) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    hideTitle: Boolean = false
) {
    val isMultiline = parameter.uiConfig.isMultiline
    // 识别 CLIP 文本编码器和其他提示词相关参数
    val isPrompt = parameter.classType.contains("CLIPTextEncode") ||
                   parameter.name.lowercase().contains("prompt") ||
                   (parameter.name.lowercase() == "text" && parameter.type == WorkflowParser.ParamType.STRING)
    
    var textValue by remember(currentValue) {
        mutableStateOf(
            if (currentValue is JsonPrimitive && currentValue.isString) {
                currentValue.content
            } else {
                currentValue.toString().removeSurrounding("\"")
            }
        )
    }
    
    // 始终展开显示输入区域，取消下拉收起功能
    
    // LLM相关状态（提前声明，用于多行文本）
    val llmViewModel: LLMAssistantViewModel = hiltViewModel()
    val llmUiState by llmViewModel.uiState.collectAsStateWithLifecycle()
    val hasApiKey by llmViewModel.hasApiKey.collectAsStateWithLifecycle()
    val llmOperations by llmViewModel.operations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var showFullscreenEditor by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
    ) {
            // 标题（独立，不带按钮）
            if (!hideTitle) {
                Text(
                    text = parameter.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isPrompt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            
            // 文本输入区域（始终展示）
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                if (true && !isMultiline && !isPrompt) {
                    // 自绘 40dp 高度的单行文本框
                    var isFocused by remember { mutableStateOf(false) }
                    val shape = RoundedCornerShape(8.dp)
                    val containerColor = if (compact) Color(0xFFEFF5FF) else Color(0xFFE8F3FF)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                            .background(containerColor, shape)
                    ) {
                        BasicTextField(
                            value = textValue,
                            onValueChange = { newText ->
                                textValue = newText
                                onValueChange(JsonPrimitive(newText))
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp)
                                .onFocusChanged { isFocused = it.isFocused }
                        )
                        if (textValue.isBlank()) {
                            Text(
                                text = "输入${parameter.displayName}...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 12.dp)
                            )
                        }
                    }
                } else {
                    // 多行/Prompt 自绘版：不固定高度，保持至少约3行的视觉高度
                    var isFocused by remember { mutableStateOf(false) }
                    val shape = RoundedCornerShape(8.dp)
                    val containerColor = if (compact) Color(0xFFEFF5FF) else Color(0xFFE8F3FF)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (isPrompt) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline, shape)
                            .background(containerColor, shape)
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = textValue,
                            onValueChange = { newText ->
                                textValue = newText
                                onValueChange(JsonPrimitive(newText))
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp) // 约等于3行高度，不严格改变外观高度
                                .onFocusChanged { isFocused = it.isFocused },
                            maxLines = if (isMultiline || isPrompt) 10 else 1
                        )
                        if (textValue.isBlank()) {
                            Text(
                                text = if (isPrompt) "输入提示词..." else "输入${parameter.displayName}...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.TopStart)
                            )
                        }
                    }
                }
                // 取消外部字符/词数统计显示
            }
            
            // 多行文本的操作按钮区域（像图像卡片的遮罩编辑按钮）
            if (isMultiline || isPrompt) {
                // 监听AI操作结果，自动替换文本
                LaunchedEffect(llmUiState.resultText, llmUiState.isLoading, llmUiState.targetParamId) {
                    if (!llmUiState.isLoading && llmUiState.resultText.isNotBlank() && llmUiState.targetParamId == parameter.id) {
                        textValue = llmUiState.resultText
                        onValueChange(JsonPrimitive(llmUiState.resultText))
                        Toast.makeText(context, "已替换为AI处理结果", Toast.LENGTH_SHORT).show()
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // AI助手（按钮样式，与"全屏编辑"一致）
                    var showAiMenu by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = {
                            showAiMenu = true
                        },
                        enabled = !llmUiState.isLoading,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isPrompt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (isPrompt) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    ) {
                        // 显示加载状态或正常图标
                        if (llmUiState.isLoading && llmUiState.targetParamId == parameter.id) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = if (isPrompt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (llmUiState.isLoading && llmUiState.targetParamId == parameter.id) "处理中..." else "AI助手")
                    }

                    // AI操作选择对话框（全屏样式，与参数选择器一致）
                    if (showAiMenu) {
                        AIOperationSelectDialog(
                            operations = llmOperations,
                            onOperationSelected = { operation ->
                                showAiMenu = false
                                if (textValue.isBlank()) {
                                    Toast.makeText(context, "请先输入文本", Toast.LENGTH_SHORT).show()
                                } else {
                                    // ✅ 传入 targetParamId，确保结果回填到当前参数
                                    llmViewModel.executeOperation(
                                        operation = operation,
                                        inputText = textValue,
                                        targetParamId = parameter.id
                                    )
                                }
                            },
                            onDismiss = { showAiMenu = false }
                        )
                    }

                    // 撤回按钮
                    if (llmUiState.lastOperation != null && llmUiState.targetParamId == parameter.id) {
                        OutlinedButton(
                            onClick = {
                                val originalText = llmViewModel.undoLastOperation()
                                if (originalText != null) {
                                    textValue = originalText
                                    onValueChange(JsonPrimitive(originalText))
                                    Toast.makeText(context, "已撤回", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isPrompt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                if (isPrompt) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "撤回",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("撤回")
                        }
                    }

                    // 全屏编辑按钮（占满剩余宽度）
                    OutlinedButton(
                        onClick = { showFullscreenEditor = true },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isPrompt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (isPrompt) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("全屏编辑")
                    }
                }
                
                // 全屏文本编辑器对话框
                if (showFullscreenEditor) {
                    FullscreenTextEditorDialog(
                        title = parameter.displayName,
                        text = textValue,
                        onDismiss = { showFullscreenEditor = false },
                        onConfirm = { newText ->
                            textValue = newText
                            onValueChange(JsonPrimitive(newText))
                            showFullscreenEditor = false
                        },
                        isPrompt = isPrompt
                    )
                }
                
                // LLM结果对话框（仅当前参数ID匹配时显示与处理）
                if (llmUiState.showResultDialog && llmUiState.targetParamId == parameter.id) {
                    LLMResultDialog(
                        originalText = llmUiState.originalText,
                        resultText = llmUiState.resultText,
                        operationName = llmUiState.currentOperation?.displayName ?: "",
                        tokenUsage = llmUiState.tokenUsage,
                        onReplace = {
                            textValue = llmUiState.resultText
                            onValueChange(JsonPrimitive(llmUiState.resultText))
                        },
                        onAppend = {
                            val combined = if (textValue.isBlank()) {
                                llmUiState.resultText
                            } else {
                                "$textValue\n${llmUiState.resultText}"
                            }
                            textValue = combined
                            onValueChange(JsonPrimitive(combined))
                        },
                        onDismiss = { llmViewModel.dismissResultDialog() }
                    )
                }
                
                // LLM加载对话框已移除，改为在按钮上显示加载状态
                
                // 错误提示
                llmUiState.errorMessage?.let { error ->
                    LaunchedEffect(error) {
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        llmViewModel.clearError()
                    }
                }
            }
    }
}

/**
 * 高级选择器组件 - 支持搜索、分组等
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSelectInput(
    parameter: WorkflowParser.EditableParam,
    currentValue: JsonElement,
    onValueChange: (JsonElement) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    hideTitle: Boolean = false
) {
    val options = parameter.uiConfig.options
    var expanded by remember { mutableStateOf(false) }
    
    val selectedValue = if (currentValue is JsonPrimitive && currentValue.isString) {
        currentValue.content
    } else {
        options.firstOrNull() ?: ""
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)
    ) {
            // 选择器 - 点击触发全屏模态
            if (true) {
                // 自绘 40dp 高度的下拉外观
                val shape = RoundedCornerShape(8.dp)
                val containerColor = if (compact) Color(0xFFEFF5FF) else Color(0xFFE8F3FF)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                        .background(containerColor, shape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { expanded = true }
                ) {
                    Text(
                        text = getDisplayName(selectedValue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "展开",
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { 
                            expanded = true 
                        }
                ) {
                    OutlinedTextField(
                        value = selectedValue,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false, // 禁用默认交互
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(parameter.displayName)
                                if (parameter.uiConfig.isDynamic) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "实时",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        trailingIcon = { 
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "展开选项",
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
                
            // 全屏模态选择器
            if (expanded) {
                FullScreenSelectDialog(
                    title = parameter.displayName,
                    options = options,
                    selectedValue = selectedValue,
                    onValueChange = { value ->
                        onValueChange(JsonPrimitive(value))
                        expanded = false
                    },
                    onDismiss = {
                        expanded = false
                    }
                )
            }
    }
}

/**
 * 获取文件的显示名称（去除扩展名）
 */
private fun getDisplayName(fileName: String): String {
    return when {
        fileName.endsWith(".safetensors") -> fileName.removeSuffix(".safetensors")
        fileName.endsWith(".ckpt") -> fileName.removeSuffix(".ckpt")
        fileName.endsWith(".pt") -> fileName.removeSuffix(".pt")
        fileName.endsWith(".pth") -> fileName.removeSuffix(".pth")
        else -> fileName
    }
}

// 已移除文件格式副标题展示逻辑及相关辅助函数

/**
 * 全屏模态选择器
 * 中心显示选项列表，周围高斯模糊背景
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenSelectDialog(
    title: String,
    options: List<String>,
    selectedValue: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .statusBarsPadding()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // 中心选择器卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.7f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* 阻止点击穿透到背景 */ },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 标题栏
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    
                    // 选项列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(options) { option ->
                            SelectionItem(
                                text = getDisplayName(option),
                                isSelected = option == selectedValue,
                                onClick = { onValueChange(option) }
                            )
                        }
                        
                        if (options.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "无匹配结果",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * 选择项组件
 */
@Composable
private fun SelectionItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) 
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null,
                modifier = Modifier.size(20.dp),
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primary
                else 
                    MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

/**
 * 高级复选框输入组件
 */
@Composable
fun AdvancedCheckboxInput(
    parameter: WorkflowParser.EditableParam,
    currentValue: JsonElement,
    onValueChange: (JsonElement) -> Unit,
    modifier: Modifier = Modifier
) {
    val isChecked = when (currentValue) {
        is JsonPrimitive -> currentValue.booleanOrNull ?: false
        else -> false
    }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = parameter.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
        }
        
        Switch(
            checked = isChecked,
            onCheckedChange = { checked ->
                onValueChange(JsonPrimitive(checked))
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

/**
 * 图片上传输入组件
 * 支持从相册选择图片，显示预览，全屏模态界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageUploadInput(
    parameter: WorkflowParser.EditableParam,
    currentValue: JsonElement,
    onValueChange: (JsonElement) -> Unit,
    modifier: Modifier = Modifier,
    onStageImage: (paramId: String, bytes: ByteArray, filename: String) -> Unit = { _, _, _ -> },
    getStagedImageBytes: (paramId: String) -> ByteArray? = { null },
    onEditMask: (paramId: String) -> Unit = { _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    // 如果已有暂存字节（返回后再次进入），优先显示为预览
    val stagedBytes = remember(currentValue) { getStagedImageBytes(parameter.id) }
    // 延迟上传设计：无上传中状态
    var uploadError by remember { mutableStateOf<String?>(null) }
    
    // 从当前值获取图片文件名
    val currentImageName = when (currentValue) {
        is JsonPrimitive -> currentValue.contentOrNull
        else -> null
    }
    
    // 图片选择器启动器 - 直接启动系统相册
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            uploadError = null
            
            // 先标记占位并暂存图片，执行时统一上传（对齐Web逻辑）
            coroutineScope.launch {
                try {
                    val imageData = ImageUtils.uriToByteArray(context, uri)
                    if (imageData != null) {
                        val fileName = ImageUtils.getFileName(context, uri)
                        // 通过占位值标记该参数，并让ViewModel暂存字节
                        val stagedValue = "staged::${System.currentTimeMillis()}::$fileName"
                        // 调用方是ViewModel的updateParameterValue，因此会收到此值
                        onValueChange(JsonPrimitive(stagedValue))
                        // 暂存字节到ViewModel，执行时统一上传
                        onStageImage(parameter.id, imageData, fileName)
                    } else {
                        uploadError = "无法读取图片文件"
                        selectedImageUri = null
                    }
                } catch (e: Exception) {
                    uploadError = e.message ?: "上传异常"
                    selectedImageUri = null
                } finally {
                }
            }
        }
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 标题
        Text(
            text = parameter.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // 图片选择卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clickable(
                    enabled = true,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { 
                    // 直接启动系统相册选择器
                    imagePickerLauncher.launch("image/*")
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, 
                if (uploadError != null) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    selectedImageUri != null -> {
                        // 显示选中的图片预览
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(selectedImageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "选中的图片",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        
                        // 覆盖层，显示更换按钮
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Black.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "更换图片",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    stagedBytes != null -> {
                        // 显示暂存的字节预览（跨页面返回后）
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(stagedBytes)
                                .crossfade(true)
                                .build(),
                            contentDescription = "选中的图片",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Black.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "更换图片",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    currentImageName != null && currentImageName.isNotBlank() -> {
                        // 显示当前图片信息
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = currentImageName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "点击更换图片",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        // 空状态，提示选择图片
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "选择图片",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "点击从相册选择",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        
        // 操作按钮区域
        if (selectedImageUri != null || stagedBytes != null || (currentImageName != null && currentImageName.isNotBlank())) {
            // 遮罩编辑按钮（只有支持遮罩的参数才显示）
            if (parameter.uiConfig.supportsMask) {
                OutlinedButton(
                    onClick = { 
                        // 直接调用遮罩编辑，ViewModel会从暂存数据中获取原图和已有路径
                        onEditMask(parameter.id)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, 
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    enabled = (stagedBytes != null || selectedImageUri != null)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("编辑遮罩")
                }
                
                Spacer(modifier = Modifier.height(0.dp))
            }
            
            // 已移除清除图片按钮
        }
        
        // 错误信息显示
        if (uploadError != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, 
                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
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
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uploadError ?: "未知错误",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { uploadError = null }
                    ) {
                        Text(
                            text = "关闭",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * AI操作选择对话框（全屏样式，与参数选择器一致）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIOperationSelectDialog(
    operations: List<com.example.myapplication.data.model.TextOperation>,
    onOperationSelected: (com.example.myapplication.data.model.TextOperation) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .statusBarsPadding()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // 中心选择器卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.7f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* 阻止点击穿透到背景 */ },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 标题栏
                    Text(
                        text = "选择AI操作",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 操作列表（支持滚动）
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(operations.size) { index ->
                            val operation = operations[index]
                            AIOperationItem(
                                icon = operation.icon,
                                text = operation.displayName,
                                onClick = {
                                    onOperationSelected(operation)
                                }
                            )
                        }
                        
                        if (operations.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无可用的AI操作",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * AI操作项组件
 */
@Composable
private fun AIOperationItem(
    icon: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 图标
            Text(
                text = icon,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.size(24.dp),
                textAlign = TextAlign.Center
            )
            // 操作名称
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            // 右侧箭头
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

