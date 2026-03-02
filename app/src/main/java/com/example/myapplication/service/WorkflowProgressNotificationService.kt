package com.example.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.domain.execution.WorkflowExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工作流执行进度通知服务
 * 完全镜像app内执行区域的显示逻辑
 */
@Singleton
class WorkflowProgressNotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workflowExecutor: WorkflowExecutor
) {
    
    companion object {
        private const val CHANNEL_ID = "workflow_execution_progress"
        private const val CHANNEL_NAME = "工作流执行进度"
        private const val NOTIFICATION_ID = 10332
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    private var isEnabled = false
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示工作流执行进度和状态"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * 开始监听执行状态并显示通知
     */
    fun startMonitoring(queueStatusFlow: StateFlow<Pair<Int, Int>?>? = null) {
        if (isEnabled) return
        isEnabled = true
        
        // 监听执行状态（完全按照app内的状态流）
        scope.launch {
            workflowExecutor.executionStatus.collect { status ->
                if (isEnabled) {
                    updateNotification(status)
                }
            }
        }
    }
    
    /**
     * 停止监听并清除通知
     */
    fun stopMonitoring() {
        isEnabled = false
        notificationManager.cancel(NOTIFICATION_ID)
    }
    
    /**
     * 清理资源
     */
    fun onCleared() {
        stopMonitoring()
        scope.cancel()
    }
    
    /**
     * 更新队列信息（兼容接口，但不再使用）
     */
    fun updateQueueInfo(currentIndex: Int, total: Int) {
        // 队列信息不在通知中显示，保持接口兼容性
    }
    
    /**
     * 更新通知内容
     * 完全镜像 WorkflowGeneratorScreen.kt 第376-451行的显示逻辑
     */
    private fun updateNotification(status: WorkflowExecutor.ExecutionStatus) {
        // IDLE状态不显示通知
        if (status.state == WorkflowExecutor.ExecutionState.IDLE) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }
        
        val notification = buildNotification(status)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * 构建通知
     * 完全按照app内执行区域的显示逻辑
     */
    private fun buildNotification(status: WorkflowExecutor.ExecutionStatus): Notification {
        // 点击通知打开应用
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 🔹 状态标题（完全对应app内第383-390行）
        val stateTitle = when (status.state) {
            WorkflowExecutor.ExecutionState.PREPARING -> "准备中"
            WorkflowExecutor.ExecutionState.QUEUED -> "队列中"
            WorkflowExecutor.ExecutionState.EXECUTING -> "执行中"
            WorkflowExecutor.ExecutionState.COMPLETED -> "执行完成"
            WorkflowExecutor.ExecutionState.ERROR -> "执行失败"
            else -> "空闲"
        }
        
        // 🔹 图标
        val smallIcon = when (status.state) {
            WorkflowExecutor.ExecutionState.PREPARING -> android.R.drawable.ic_dialog_info
            WorkflowExecutor.ExecutionState.QUEUED -> android.R.drawable.ic_dialog_info
            WorkflowExecutor.ExecutionState.EXECUTING -> android.R.drawable.ic_media_play
            WorkflowExecutor.ExecutionState.COMPLETED -> android.R.drawable.stat_sys_download_done
            WorkflowExecutor.ExecutionState.ERROR -> android.R.drawable.stat_notify_error
            else -> android.R.drawable.ic_dialog_info
        }
        
        // 🔹 标题：状态 + 百分比（对应app内第402-411行）
        val title = if (status.state == WorkflowExecutor.ExecutionState.EXECUTING && status.progress > 0f) {
            "$stateTitle ${(status.progress * 100).toInt()}%"
        } else {
            stateTitle
        }
        
        // 🔹 内容文本构建
        val contentBuilder = StringBuilder()
        
        when (status.state) {
            WorkflowExecutor.ExecutionState.COMPLETED -> {
                // ✨ 完成状态：显示精美的完成信息
                val imageCount = status.result?.outputImages?.size ?: 0
                val executionTime = status.result?.executionTime ?: 0L
                
                if (imageCount > 0) {
                    contentBuilder.append("✓ 已生成 $imageCount 张图片")
                    if (executionTime > 0) {
                        val seconds = executionTime / 1000
                        if (seconds < 60) {
                            contentBuilder.append(" · 耗时 ${seconds}秒")
                        } else {
                            val minutes = seconds / 60
                            val remainingSeconds = seconds % 60
                            contentBuilder.append(" · 耗时 ${minutes}分${remainingSeconds}秒")
                        }
                    }
                } else {
                    contentBuilder.append("✓ 工作流执行成功")
                    if (executionTime > 0) {
                        val seconds = executionTime / 1000
                        contentBuilder.append(" · 耗时 ${seconds}秒")
                    }
                }
            }
            
            WorkflowExecutor.ExecutionState.ERROR -> {
                // ❌ 错误状态：显示错误详情
                contentBuilder.append(status.errorDetails ?: status.message.ifEmpty { "执行失败" })
            }
            
            else -> {
                // 🔄 执行中/准备中/队列中：显示当前进度信息
                // 当前节点信息
                if (!status.currentNode.isNullOrBlank()) {
                    contentBuilder.append("当前节点: ${status.currentNode}")
                }
                
                // 状态消息
                if (status.message.isNotEmpty()) {
                    if (contentBuilder.isNotEmpty()) {
                        contentBuilder.append(" · ")
                    }
                    contentBuilder.append(status.message)
                }
            }
        }
        
        val contentText = if (contentBuilder.isEmpty()) stateTitle else contentBuilder.toString()
        
        // 🔹 构建通知
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
        
        // 🔹 进度条（对应app内第443-451行）
        when (status.state) {
            WorkflowExecutor.ExecutionState.EXECUTING -> {
                // 执行中：显示进度条
                val progress = (status.progress * 100).toInt()
                // 特殊处理：如果进度达到100%，移除进度条（避免显示"执行中100%"）
                if (progress >= 100) {
                    builder.setProgress(0, 0, false)
                } else {
                    builder.setProgress(100, progress, false)
                }
            }
            WorkflowExecutor.ExecutionState.COMPLETED,
            WorkflowExecutor.ExecutionState.ERROR -> {
                // 完成/失败：移除进度条
                builder.setProgress(0, 0, false)
            }
            else -> {
                // 其他状态：不显示进度条
                builder.setProgress(0, 0, false)
            }
        }
        
        // 🔹 通知行为
        when (status.state) {
            WorkflowExecutor.ExecutionState.PREPARING,
            WorkflowExecutor.ExecutionState.QUEUED,
            WorkflowExecutor.ExecutionState.EXECUTING -> {
                // 执行中：持续显示，不可清除
                builder.setOngoing(true)
                    .setAutoCancel(false)
            }
            WorkflowExecutor.ExecutionState.COMPLETED,
            WorkflowExecutor.ExecutionState.ERROR -> {
                // 完成/失败：可清除
                builder.setOngoing(false)
                    .setAutoCancel(true)
            }
            else -> {
                builder.setOngoing(false)
                    .setAutoCancel(true)
            }
        }
        
        // 🔹 多行文本样式
        if (contentText.length > 40 || contentText.contains("·")) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(contentText)
            )
        }
        
        return builder.build()
    }
}
