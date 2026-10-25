package com.example.myapplication.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.presentation.viewmodel.UpdatePhase
import com.example.myapplication.presentation.viewmodel.UpdateUiState

/**
 * 应用内更新卡片。
 *
 * 受 Android 平台限制：下载校验后仍需用户在系统安装界面点一次「安装」，
 * 这里只能把权限申请和拉起安装器的步骤做到最省事。
 */
@Composable
fun AppUpdateCard(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0EFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "应用更新",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val current = state.current
            Text(
                text = "当前版本：${current?.versionName ?: "-"}（${current?.versionCode ?: 0}）",
                style = MaterialTheme.typography.bodyMedium
            )

            when (state.phase) {
                UpdatePhase.IDLE -> Unit

                UpdatePhase.CHECKING -> Text("正在检查更新…", style = MaterialTheme.typography.bodyMedium)

                UpdatePhase.UP_TO_DATE -> Text(
                    "已是最新版本",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2E7D32)
                )

                UpdatePhase.AVAILABLE -> {
                    val remote = state.remote
                    Text(
                        "发现新版本：${remote?.versionName ?: ""}（${remote?.versionCode ?: 0}）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1565C0),
                        fontWeight = FontWeight.Bold
                    )
                    remote?.notes?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }

                UpdatePhase.DOWNLOADING -> {
                    Text("正在下载…${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = state.progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                UpdatePhase.DOWNLOADED -> Text(
                    "下载完成，已通过哈希与签名校验",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2E7D32)
                )

                UpdatePhase.ERROR -> Text(
                    state.message ?: "更新失败",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val busy = state.phase == UpdatePhase.CHECKING || state.phase == UpdatePhase.DOWNLOADING
                OutlinedButton(onClick = onCheck, enabled = !busy) {
                    Text(if (state.phase == UpdatePhase.IDLE) "检查更新" else "重新检查")
                }

                if (state.phase == UpdatePhase.AVAILABLE || state.phase == UpdatePhase.ERROR) {
                    Button(onClick = onDownload) { Text("下载并安装") }
                }

                if (state.phase == UpdatePhase.DOWNLOADED) {
                    Button(onClick = onInstall) { Text("立即安装") }
                }
            }

            // 只在还没授予权限时提示，避免用户点了安装才发现被拦
            if (state.phase == UpdatePhase.DOWNLOADED) {
                val canInstall = com.example.myapplication.domain.update.ApkInstallHelper.canInstall(context)
                if (!canInstall) {
                    Text(
                        "首次安装需要允许「安装未知应用」，点「立即安装」会跳到授权页，返回后再点一次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
