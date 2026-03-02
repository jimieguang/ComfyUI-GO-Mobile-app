package com.example.myapplication.presentation.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * 绘制模式
 */
sealed class DrawingMode {
    object Brush : DrawingMode()
    object Eraser : DrawingMode()
}

/**
 * 图像遮罩编辑器组件
 * 支持在图像上绘制遮罩，支持连续编辑
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageMaskEditor(
    originalBitmap: Bitmap,
    existingPaths: List<DrawingPath> = emptyList(),  // 已有的遮罩路径
    onSave: (allPaths: List<DrawingPath>, canvasSize: androidx.compose.ui.geometry.Size) -> Unit,  // 返回所有路径和Canvas尺寸
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 与节点卡片一致的浅蓝色
    val LightBlue = Color(0xFFB5D9FB)
    // 🔥 关键修改：将所有路径合并到一个列表中，支持橡皮擦擦除已有路径
    var allDrawingPaths by remember(existingPaths) { mutableStateOf(existingPaths.toMutableList()) }
    var currentPath by remember { mutableStateOf<DrawingPath?>(null) }
    var brushSize by remember { mutableFloatStateOf(20f) }
    var drawingMode by remember { mutableStateOf<DrawingMode>(DrawingMode.Brush) }
    var showPreview by remember { mutableStateOf(false) }
    val savingScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    
    // 画布尺寸状态
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    
    // 放大镜（视口）状态
    var magnifierVisible by remember { mutableStateOf(false) }
    var lastPointerOffset by remember { mutableStateOf(Offset.Zero) }
    val magnifierSizeDp = 120.dp
    val magnifierScale = 2f
    val magnifierOffsetDp = 80.dp // 视口默认位于手指上方
    
    // 双指确认绘制状态
    var isDrawingActive by remember { mutableStateOf(false) }  // 是否开始实际绘制
    var pointerCount by remember { mutableIntStateOf(0) }  // 当前触摸点数量
    
    // 将原始Bitmap转换为ImageBitmap
    val imageBitmap = remember(originalBitmap) { 
        originalBitmap.asImageBitmap() 
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 顶部工具栏
        TopAppBar(
            title = { 
                Column {
                    Text(
                        "遮罩编辑器",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (existingPaths.isNotEmpty()) {
                        Text(
                            "已有 ${existingPaths.size} 笔画",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Default.ArrowBack, 
                        contentDescription = "取消"
                    )
                }
            },
            actions = {
                // 预览切换按钮
                IconButton(
                    onClick = { showPreview = !showPreview }
                ) {
                    Icon(
                        if (showPreview) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (showPreview) "隐藏预览" else "显示预览"
                    )
                }
                
                // 保存按钮
                IconButton(
                    enabled = !isSaving,
                    onClick = {
                        if (isSaving) return@IconButton
                        isSaving = true
                        savingScope.launch {
                            try {
                                // 直接返回路径信息，不生成合成图片
                                onSave(allDrawingPaths, canvasSize)
                            } catch (e: Throwable) {
                                android.util.Log.e("ImageMaskEditor", "保存遮罩路径失败: ${e.message}", e)
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.Check, 
                        contentDescription = "保存"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = LightBlue,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        
        // 工具栏 + 放大镜（顶部）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 左侧工具栏
            Card(
                modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
                Column(
                modifier = Modifier
                    .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 第一行：画笔/橡皮擦 + 撤销/清除按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                        // 左侧：画笔/橡皮擦切换
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        onClick = { drawingMode = DrawingMode.Brush },
                        label = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Edit, 
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("画笔", fontSize = 12.sp)
                            }
                        },
                        selected = drawingMode == DrawingMode.Brush,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LightBlue,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    
                    FilterChip(
                        onClick = { 
                            drawingMode = DrawingMode.Eraser
                        },
                        label = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Delete, 
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("橡皮擦", fontSize = 12.sp)
                            }
                        },
                        selected = drawingMode == DrawingMode.Eraser,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                        )
                    )
                }
                
                        // 右侧：撤销/清除按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                            // 恢复到已有路径按钮
                            IconButton(
                                onClick = { 
                                    allDrawingPaths = existingPaths.toMutableList()
                                    currentPath = null
                                },
                                enabled = allDrawingPaths.size > existingPaths.size
                            ) {
                                Icon(
                                    Icons.Default.Clear, 
                                    contentDescription = "恢复到已有路径",
                                    tint = if (allDrawingPaths.size > existingPaths.size) 
                                        MaterialTheme.colorScheme.onSurface 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // 从头开始按钮（清除所有包括已有的）
                            if (existingPaths.isNotEmpty()) {
                                IconButton(
                                    onClick = { 
                                        allDrawingPaths = mutableListOf()
                                        currentPath = null
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Refresh, 
                                        contentDescription = "从头开始",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    
                    // 第二行：画笔大小调节
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "大小", 
                            color = MaterialTheme.colorScheme.onSurfaceVariant, 
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        ) {
                    Slider(
                        value = brushSize,
                        onValueChange = { brushSize = it },
                        valueRange = 5f..80f,
                                modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = LightBlue,
                            activeTrackColor = LightBlue,
                            inactiveTrackColor = LightBlue.copy(alpha = 0.4f)
                        )
                    )
                    
                    // 画笔大小指示器
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size((brushSize / 80f * 24f + 4f).dp)
                                .background(LightBlue, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
            
            // 右侧固定放大镜
            if (magnifierVisible) {
                Card(
                    modifier = Modifier.size(magnifierSizeDp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                ) {
                    // 预先获取颜色（在 Composable 上下文中）
                    val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    val brushCircleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    
                    // 在放大镜内重绘内容并放大
                    Canvas(Modifier.fillMaxSize()) {
                        // 使用与主画布一致的显示计算
                        val imageAspectRatio = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
                        val canvasAspectRatio = if (canvasSize.height != 0f) canvasSize.width / canvasSize.height else 1f

                        val worldDisplay = if (imageAspectRatio > canvasAspectRatio) {
                            val displayWidth = canvasSize.width
                            val displayHeight = if (imageAspectRatio != 0f) canvasSize.width / imageAspectRatio else canvasSize.height
                            val offsetY = (canvasSize.height - displayHeight) / 2f
                            floatArrayOf(displayWidth, displayHeight, 0f, offsetY)
                        } else {
                            val displayHeight = canvasSize.height
                            val displayWidth = canvasSize.height * imageAspectRatio
                            val offsetX = (canvasSize.width - displayWidth) / 2f
                            floatArrayOf(displayWidth, displayHeight, offsetX, 0f)
                        }

                        val displayWidth = worldDisplay[0]
                        val displayHeight = worldDisplay[1]
                        val offsetX = worldDisplay[2]
                        val offsetY = worldDisplay[3]

                        // 变换：将手指周围区域放大到整个视口
                        withTransform({
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            translate(centerX - lastPointerOffset.x * magnifierScale, centerY - lastPointerOffset.y * magnifierScale)
                            scale(magnifierScale, magnifierScale, Offset.Zero)
                        }) {
                            // 绘制原图
                            drawImage(
                                image = imageBitmap,
                                dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(displayWidth.toInt(), displayHeight.toInt())
                            )
                            
                            // 使用离屏图层绘制遮罩路径
                            val offscreenBounds = androidx.compose.ui.geometry.Rect(Offset.Zero, androidx.compose.ui.geometry.Size(canvasSize.width * magnifierScale, canvasSize.height * magnifierScale))
                            drawContext.canvas.saveLayer(offscreenBounds, Paint())
                            
                            // 绘制所有路径
                            allDrawingPaths.forEachIndexed { index, drawingPath ->
                                val isExistingPath = index < existingPaths.size
                                val color = when (drawingPath.mode) {
                                    DrawingMode.Brush -> {
                                        if (showPreview) {
                                            if (isExistingPath) Color.Red.copy(alpha = 0.3f)
                                            else Color.Red.copy(alpha = 0.6f)
                                        } else {
                                            if (isExistingPath) Color.Black.copy(alpha = 0.3f)
                                            else Color.Black.copy(alpha = 0.5f)
                                        }
                                    }
                                    DrawingMode.Eraser -> Color.Transparent
                                }
                                drawPath(
                                    path = drawingPath.path,
                                    color = color,
                                    style = Stroke(width = drawingPath.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                    blendMode = BlendMode.Src
                                )
                            }
                            
                            // 绘制当前正在绘制的路径
                            currentPath?.let { drawingPath ->
                                val color = when (drawingPath.mode) {
                                    DrawingMode.Brush -> if (showPreview)
                                        Color.Red.copy(alpha = 0.8f)
                                    else
                                        Color.Black.copy(alpha = 0.7f)
                                    DrawingMode.Eraser -> Color.Transparent
                                }
                                drawPath(
                                    path = drawingPath.path,
                                    color = color,
                                    style = Stroke(width = drawingPath.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                    blendMode = BlendMode.Src
                                )
                            }
                            
                            drawContext.canvas.restore()
                            
                            // 在同一个变换内绘制笔刷圆圈
                            drawCircle(
                                color = brushCircleColor,
                                radius = brushSize / 2f,
                                center = lastPointerOffset,
                                style = Stroke(width = 1f / magnifierScale)
                            )
                        }

                        // 绘制十字准星（在视口坐标，不在变换内）
                        val center = Offset(this.size.width / 2f, this.size.height / 2f)
                        drawLine(
                            color = crosshairColor,
                            start = Offset(center.x - 12f, center.y),
                            end = Offset(center.x + 12f, center.y),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = crosshairColor,
                            start = Offset(center.x, center.y - 12f),
                            end = Offset(center.x, center.y + 12f),
                            strokeWidth = 2f
                        )
                    }
                }
            }
        }
        
        // 主绘制画布区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val changes = event.changes
                                
                                // 当前按下的触点数量
                                pointerCount = changes.count { it.pressed }
                                
                                if (pointerCount == 0) {
                                    // 所有手指抬起：完成路径（若在绘制）并隐藏放大镜
                                    if (isDrawingActive) {
                                        currentPath?.let { path ->
                                            android.util.Log.d("ImageMaskEditor", "完成绘制 - 当前路径总数: ${allDrawingPaths.size + 1}")
                                            allDrawingPaths = (allDrawingPaths + path).toMutableList()
                                            currentPath = null
                                        }
                                        isDrawingActive = false
                                    }
                                    magnifierVisible = false
                                    continue
                                }
                                
                                // 选取主触点（第一个按下的触点）
                                val primary = changes.firstOrNull { it.pressed } ?: continue
                                lastPointerOffset = primary.position
                                
                                // 单指：只显示放大镜预览，不绘制
                                magnifierVisible = true
                                
                                if (!isDrawingActive && pointerCount >= 2) {
                                    // 第二个手指按下，开始绘制
                                    isDrawingActive = true
                                    val newPath = Path().apply { moveTo(primary.position.x, primary.position.y) }
                                    currentPath = DrawingPath(
                                        path = newPath,
                                        mode = drawingMode,
                                        strokeWidth = brushSize
                                    )
                                    android.util.Log.d("ImageMaskEditor", "开始绘制 - 双指激活 at (${primary.position.x}, ${primary.position.y})")
                                } else if (isDrawingActive && pointerCount >= 2) {
                                    // 双指保持按下：继续绘制（使用绝对坐标，避免高速移动跳跃）
                                    currentPath?.let { drawingPath ->
                                        val updatedPath = Path().apply {
                                            addPath(drawingPath.path)
                                            lineTo(primary.position.x, primary.position.y)
                                        }
                                        currentPath = drawingPath.copy(path = updatedPath)
                                    }
                                } else if (isDrawingActive && pointerCount == 1) {
                                    // 回到单指：结束当前路径，继续放大镜预览
                                    currentPath?.let { path ->
                                        allDrawingPaths = (allDrawingPaths + path).toMutableList()
                                        currentPath = null
                                    }
                                    isDrawingActive = false
                                    android.util.Log.d("ImageMaskEditor", "暂停绘制 - 返回预览模式")
                                }
                                
                                // 消费事件，避免系统手势干扰
                                primary.consume()
                            }
                        }
                    }
            ) {
                // 更新canvas尺寸状态
                canvasSize = size
                
                // 🔥 关键：使用与generateMaskedImageFast完全相同的显示逻辑
                val imageAspectRatio = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
                val canvasAspectRatio = size.width / size.height
                
                val (displayWidth, displayHeight, offsetX, offsetY) = if (imageAspectRatio > canvasAspectRatio) {
                    // 图像更宽，以宽度为准
                    val displayWidth = size.width
                    val displayHeight = size.width / imageAspectRatio
                    val offsetY = (size.height - displayHeight) / 2f
                    listOf(displayWidth, displayHeight, 0f, offsetY)
                } else {
                    // 图像更高，以高度为准
                    val displayHeight = size.height
                    val displayWidth = size.height * imageAspectRatio
                    val offsetX = (size.width - displayWidth) / 2f
                    listOf(displayWidth, displayHeight, offsetX, 0f)
                }
                
                val imageRect = androidx.compose.ui.geometry.Rect(
                    offset = Offset(offsetX, offsetY),
                    size = androidx.compose.ui.geometry.Size(displayWidth, displayHeight)
                )
                
                // 调试日志：记录显示参数（只在放大镜显示时输出，避免刷屏）
                if (magnifierVisible) {
                    android.util.Log.d("ImageMaskEditor", "===== 主画布绘制参数 =====")
                    android.util.Log.d("ImageMaskEditor", "Canvas尺寸: ${size.width}x${size.height}")
                    android.util.Log.d("ImageMaskEditor", "图像显示尺寸: ${displayWidth}x${displayHeight}")
                    android.util.Log.d("ImageMaskEditor", "图像显示偏移: ($offsetX, $offsetY)")
                    android.util.Log.d("ImageMaskEditor", "原图尺寸: ${imageBitmap.width}x${imageBitmap.height}")
                    android.util.Log.d("ImageMaskEditor", "图像宽高比: $imageAspectRatio, Canvas宽高比: $canvasAspectRatio")
                }
                
                // 绘制原始图像
                drawImage(
                    image = imageBitmap,
                    dstOffset = androidx.compose.ui.unit.IntOffset(imageRect.topLeft.x.toInt(), imageRect.topLeft.y.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(imageRect.size.width.toInt(), imageRect.size.height.toInt())
                )
                
                // 🔥 统一绘制所有路径，支持橡皮擦擦除已有路径
                drawAllMaskPaths(allDrawingPaths, currentPath, existingPaths.size, showPreview)
                
                // 在主画布上显示笔刷位置预览圆圈（当放大镜显示时）
                if (magnifierVisible) {
                    // 调试日志
                    android.util.Log.d("ImageMaskEditor", "主画布 - 笔刷中心: (${lastPointerOffset.x}, ${lastPointerOffset.y}), 半径: ${brushSize / 2f}")
                    
                    drawCircle(
                        color = Color.Cyan.copy(alpha = 0.4f),  // 半透明青色圆圈
                        radius = brushSize / 2f,
                        center = lastPointerOffset,
                        style = Stroke(width = 2f)
                    )
                }
            }
        }
    }
}

/**
 * 绘制路径数据类
 */
data class DrawingPath(
    val path: Path,
    val mode: DrawingMode,
    val strokeWidth: Float
)

/**
 * 可序列化的绘制路径（用于存储）
 */
@kotlinx.serialization.Serializable
data class SerializableDrawingPath(
    val points: List<SerializableOffset>,
    val mode: DrawingModeType,
    val strokeWidth: Float
)

@kotlinx.serialization.Serializable
data class SerializableOffset(val x: Float, val y: Float)

@kotlinx.serialization.Serializable
enum class DrawingModeType { BRUSH, ERASER }

@kotlinx.serialization.Serializable
data class SerializableSize(val width: Float, val height: Float)

/**
 * 图片参数值（包含原图和遮罩信息）
 */
@kotlinx.serialization.Serializable
data class ImageParameterValue(
    val originalImageKey: String,
    val maskPaths: List<SerializableDrawingPath> = emptyList(),
    val canvasSize: SerializableSize? = null,
    val lastModified: Long = 0L
)

/**
 * 暂存的图片数据
 */
data class PendingImageData(
    val originalBytes: ByteArray,
    val filename: String,
    val maskPaths: List<DrawingPath> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PendingImageData
        if (!originalBytes.contentEquals(other.originalBytes)) return false
        if (filename != other.filename) return false
        if (maskPaths != other.maskPaths) return false
        return true
    }

    override fun hashCode(): Int {
        var result = originalBytes.contentHashCode()
        result = 31 * result + filename.hashCode()
        result = 31 * result + maskPaths.hashCode()
        return result
    }
}

/**
 * 转换函数：DrawingPath -> SerializableDrawingPath
 */
fun DrawingPath.toSerializable(): SerializableDrawingPath {
    val points = mutableListOf<SerializableOffset>()
    val androidPath = this.path.asAndroidPath()
    
    // 简化：使用PathMeasure获取路径上的点
    val pathMeasure = android.graphics.PathMeasure(androidPath, false)
    val length = pathMeasure.length
    val numPoints = kotlin.math.min(100, kotlin.math.max(10, (length / 10).toInt()))
    
    for (i in 0 until numPoints) {
        val distance = (i.toFloat() / (numPoints - 1)) * length
        val coords = FloatArray(2)
        pathMeasure.getPosTan(distance, coords, null)
        points.add(SerializableOffset(coords[0], coords[1]))
    }
    
    return SerializableDrawingPath(
        points = points,
        mode = when (this.mode) {
            DrawingMode.Brush -> DrawingModeType.BRUSH
            DrawingMode.Eraser -> DrawingModeType.ERASER
        },
        strokeWidth = this.strokeWidth
    )
}

/**
 * 转换函数：SerializableDrawingPath -> DrawingPath
 */
fun SerializableDrawingPath.toDrawingPath(): DrawingPath {
    val path = Path()
    if (points.isNotEmpty()) {
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
    }
    
    return DrawingPath(
        path = path,
        mode = when (this.mode) {
            DrawingModeType.BRUSH -> DrawingMode.Brush
            DrawingModeType.ERASER -> DrawingMode.Eraser
        },
        strokeWidth = this.strokeWidth
    )
}

/**
 * 转换函数：Size -> SerializableSize
 */
fun androidx.compose.ui.geometry.Size.toSerializable(): SerializableSize {
    return SerializableSize(this.width, this.height)
}

/**
 * 转换函数：SerializableSize -> Size
 */
fun SerializableSize.toSize(): androidx.compose.ui.geometry.Size {
    return androidx.compose.ui.geometry.Size(this.width, this.height)
}

/**
 * 🔥 统一绘制所有遮罩路径，支持橡皮擦擦除已有路径
 */
private fun DrawScope.drawAllMaskPaths(
    allPaths: List<DrawingPath>,
    currentPath: DrawingPath?,
    existingPathsCount: Int,
    showPreview: Boolean
) {
    // 使用离屏图层 + BlendMode.Src，避免重复涂抹叠加加深
    val offscreen = androidx.compose.ui.geometry.Rect(Offset.Zero, size)
    val paint = Paint()
    drawContext.canvas.saveLayer(offscreen, paint)

    // 绘制所有已保存的路径
    allPaths.forEachIndexed { index, drawingPath ->
        // 区分已有路径和新路径的视觉效果
        val isExistingPath = index < existingPathsCount
        val color = when (drawingPath.mode) {
            DrawingMode.Brush -> {
                if (showPreview) {
                    if (isExistingPath) Color.Red.copy(alpha = 0.3f)  // 已有路径：更淡的红色
                    else Color.Red.copy(alpha = 0.6f)                  // 新路径：正常红色
                } else {
                    if (isExistingPath) Color.Black.copy(alpha = 0.3f) // 已有路径：更淡的黑色
                    else Color.Black.copy(alpha = 0.5f)                 // 新路径：正常黑色
                }
            }
            DrawingMode.Eraser -> Color.Transparent  // 橡皮擦：透明（擦除效果）
        }
        drawPath(
            path = drawingPath.path,
            color = color,
            style = Stroke(width = drawingPath.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            blendMode = BlendMode.Src
        )
    }

    // 绘制当前正在绘制的路径（最高透明度）
    currentPath?.let { drawingPath ->
        val color = when (drawingPath.mode) {
            DrawingMode.Brush -> if (showPreview) 
                Color.Red.copy(alpha = 0.8f)    // 最鲜明的红色，表示正在绘制
            else 
                Color.Black.copy(alpha = 0.7f)  // 最鲜明的黑色，表示正在绘制
            DrawingMode.Eraser -> Color.Transparent
        }
        drawPath(
            path = drawingPath.path,
            color = color,
            style = Stroke(width = drawingPath.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            blendMode = BlendMode.Src
        )
    }

    drawContext.canvas.restore()
}

/**
 * 生成带正确Alpha通道的合成图像（方案C：符合ComfyUI标准）
 * 保持RGB内容不变，只修改Alpha通道来标记遮罩区域
 */
fun generateMaskedImageFast(originalBitmap: Bitmap, drawingPaths: List<DrawingPath>, canvasSize: androidx.compose.ui.geometry.Size): Bitmap {
    android.util.Log.d("ImageMaskEditor", "=== 开始生成合成图像 ===")
    android.util.Log.d("ImageMaskEditor", "路径数量: ${drawingPaths.size}")
    android.util.Log.d("ImageMaskEditor", "原图尺寸: ${originalBitmap.width}x${originalBitmap.height}")
    android.util.Log.d("ImageMaskEditor", "Canvas尺寸: ${canvasSize.width}x${canvasSize.height}")
    
    // 打印路径信息用于调试
    drawingPaths.forEachIndexed { index, path ->
        android.util.Log.d("ImageMaskEditor", "路径[$index]: 模式=${path.mode}, 笔刷大小=${path.strokeWidth}")
    }
    
    // 1. 创建ARGB_8888格式的结果图像，复制原图内容
    val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
    // 确保启用Alpha通道，并关闭预乘，避免透明像素下RGB被抹黑
    try { resultBitmap.setHasAlpha(true) } catch (_: Throwable) {}
    try { resultBitmap.setPremultiplied(false) } catch (_: Throwable) {}
    
    // 如果没有绘制路径，直接返回原图（所有区域都不透明）
    if (drawingPaths.isEmpty()) {
        android.util.Log.d("ImageMaskEditor", "没有绘制路径，返回原图")
        return resultBitmap
    }
    
    // 2. 创建遮罩位图用于判断哪些区域被画过
    val maskBitmap = Bitmap.createBitmap(
        originalBitmap.width, 
        originalBitmap.height, 
        Bitmap.Config.ARGB_8888
    )
    
    // 计算坐标转换比例
    val imageAspectRatio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
    val canvasAspectRatio = canvasSize.width / canvasSize.height
    
    val (displayWidth, displayHeight, offsetX, offsetY) = if (imageAspectRatio > canvasAspectRatio) {
        // 图像更宽，以宽度为准
        val displayWidth = canvasSize.width
        val displayHeight = canvasSize.width / imageAspectRatio
        val offsetY = (canvasSize.height - displayHeight) / 2f
        listOf(displayWidth, displayHeight, 0f, offsetY)
    } else {
        // 图像更高，以高度为准
        val displayHeight = canvasSize.height
        val displayWidth = canvasSize.height * imageAspectRatio
        val offsetX = (canvasSize.width - displayWidth) / 2f
        listOf(displayWidth, displayHeight, offsetX, 0f)
    }
    
    // 计算从Canvas坐标到图像坐标的缩放比例
    val scaleX = originalBitmap.width.toFloat() / displayWidth
    val scaleY = originalBitmap.height.toFloat() / displayHeight
    
    android.util.Log.d("ImageMaskEditor", "合成时显示尺寸: ${displayWidth}x${displayHeight}, 偏移: ($offsetX, $offsetY)")
    android.util.Log.d("ImageMaskEditor", "合成时缩放比例: scaleX=$scaleX, scaleY=$scaleY")
    android.util.Log.d("ImageMaskEditor", "图像宽高比: $imageAspectRatio, Canvas宽高比: ${canvasSize.width / canvasSize.height}")
    
    // 3. 在遮罩位图上绘制用户的绘制路径
    val maskCanvas = android.graphics.Canvas(maskBitmap)
    maskCanvas.drawColor(android.graphics.Color.WHITE) // 默认白色（非遮罩区域）
    
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    
    // 绘制每个路径到遮罩位图
    drawingPaths.forEach { drawingPath ->
        // 创建变换后的路径
        val transformedPath = android.graphics.Path()
        val srcPath = drawingPath.path.asAndroidPath()
        
        // 应用坐标变换矩阵
        val matrix = android.graphics.Matrix()
        matrix.setTranslate(-offsetX, -offsetY) // 移除偏移
        matrix.postScale(scaleX, scaleY) // 缩放到图像坐标系
        
        srcPath.transform(matrix, transformedPath)
        
        when (drawingPath.mode) {
            DrawingMode.Brush -> {
                // 画笔模式：绘制黑色（标记为遮罩区域）
                paint.color = android.graphics.Color.BLACK
                paint.strokeWidth = drawingPath.strokeWidth * scaleX
                maskCanvas.drawPath(transformedPath, paint)
            }
            DrawingMode.Eraser -> {
                // 橡皮擦模式：绘制白色（恢复为非遮罩区域）
                paint.color = android.graphics.Color.WHITE
                paint.strokeWidth = drawingPath.strokeWidth * scaleX
                maskCanvas.drawPath(transformedPath, paint)
            }
        }
    }
    // 4. 核心逻辑（高效版）：使用像素数组一次性处理
    val width = resultBitmap.width
    val height = resultBitmap.height
    val total = width * height
    val origPixels = IntArray(total)
    val maskPixels = IntArray(total)
    resultBitmap.getPixels(origPixels, 0, width, 0, 0, width, height)
    maskBitmap.getPixels(maskPixels, 0, width, 0, 0, width, height)
    var i = 0
    while (i < total) {
        val originalPixel = origPixels[i]
        val maskPixel = maskPixels[i]
        val originalRGB = originalPixel and 0x00FFFFFF
        val maskGray = (maskPixel shr 16) and 0xFF // red channel
        val newAlpha = if (maskGray < 128) 0 else 255
        origPixels[i] = originalRGB or (newAlpha shl 24)
        i++
    }
    resultBitmap.setPixels(origPixels, 0, width, 0, 0, width, height)
    
    maskBitmap.recycle() // 释放遮罩位图内存
    android.util.Log.d("ImageMaskEditor", "合成完成：保持RGB内容，修改了Alpha通道")
    return resultBitmap
}

 
