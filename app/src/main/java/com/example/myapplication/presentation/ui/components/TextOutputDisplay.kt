package com.example.myapplication.presentation.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 文本输出显示组件
 * 用于显示工作流执行后的文本结果
 */
@Composable
fun TextOutputDisplay(
    textOutputs: Map<String, List<String>>,
    nodeNameMap: Map<String, String> = emptyMap(),  // 节点ID -> 节点名称
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    if (textOutputs.isEmpty()) {
        return
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 标题
        Text(
            text = "📝 文本结果",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        // 遍历每个节点的文本输出
        textOutputs.forEach { (nodeId, texts) ->
            TextOutputCard(
                nodeId = nodeId,
                nodeName = nodeNameMap[nodeId],
                texts = texts,
                onCopy = { text ->
                    copyToClipboard(context, text)
                },
                onShare = { text ->
                    shareText(context, text)
                }
            )
        }
    }
}

/**
 * 单个节点的文本输出卡片
 */
@Composable
private fun TextOutputCard(
    nodeId: String,
    nodeName: String?,
    texts: List<String>,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 节点名称
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = nodeName ?: "节点 $nodeId",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (nodeName != null) {
                        Text(
                            text = "ID: $nodeId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Divider()
            
            // 文本内容列表
            texts.forEachIndexed { index, text ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                SelectionContainer {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // 操作按钮（每条文本一组按钮）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    // 复制按钮
                    OutlinedButton(
                        onClick = { onCopy(text) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "复制",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制", style = MaterialTheme.typography.labelSmall)
                    }
                    
                    // 分享按钮
                    OutlinedButton(
                        onClick = { onShare(text) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "分享",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("分享", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * 复制文本到剪贴板
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("文本结果", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
}

/**
 * 分享文本
 */
private fun shareText(context: Context, text: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "分享文本"))
}

