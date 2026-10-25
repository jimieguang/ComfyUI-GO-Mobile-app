package com.example.myapplication.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.update.ApkInstallHelper
import com.example.myapplication.domain.update.InstalledVersion
import com.example.myapplication.domain.update.ReleaseManifest
import com.example.myapplication.domain.update.UpdateCheckResult
import com.example.myapplication.domain.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** 更新流程的阶段，UI 直接按它渲染，避免多套布尔量互相冲突 */
enum class UpdatePhase {
    IDLE,          // 未检查
    CHECKING,      // 检查中
    UP_TO_DATE,    // 已是最新
    AVAILABLE,     // 有新版本
    DOWNLOADING,   // 下载中
    DOWNLOADED,    // 下载并校验完成，等待安装
    ERROR          // 出错
}

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val current: InstalledVersion? = null,
    val remote: ReleaseManifest? = null,
    val progress: Float = 0f,
    val message: String? = null,
    val downloadUrl: String? = null
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateManager: UpdateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var downloadedApk: File? = null

    init {
        _uiState.update { it.copy(current = runCatching { updateManager.installedVersion() }.getOrNull()) }
    }

    fun checkForUpdate() {
        if (_uiState.value.phase == UpdatePhase.CHECKING ||
            _uiState.value.phase == UpdatePhase.DOWNLOADING
        ) return

        viewModelScope.launch {
            _uiState.update { it.copy(phase = UpdatePhase.CHECKING, message = null, progress = 0f) }
            when (val result = updateManager.checkForUpdate()) {
                is UpdateCheckResult.UpToDate -> _uiState.update {
                    it.copy(phase = UpdatePhase.UP_TO_DATE, current = result.current, message = null)
                }
                is UpdateCheckResult.Available -> _uiState.update {
                    it.copy(
                        phase = UpdatePhase.AVAILABLE,
                        current = result.current,
                        remote = result.remote,
                        message = null
                    )
                }
                is UpdateCheckResult.Failed -> _uiState.update {
                    it.copy(phase = UpdatePhase.ERROR, message = result.message)
                }
            }
        }
    }

    fun download() {
        val remote = _uiState.value.remote ?: return
        if (_uiState.value.phase == UpdatePhase.DOWNLOADING) return

        viewModelScope.launch {
            _uiState.update { it.copy(phase = UpdatePhase.DOWNLOADING, progress = 0f, message = null) }
            val result = updateManager.downloadAndVerify(remote) { p ->
                _uiState.update { it.copy(progress = p) }
            }
            result.fold(
                onSuccess = { file ->
                    downloadedApk = file
                    _uiState.update {
                        it.copy(phase = UpdatePhase.DOWNLOADED, progress = 1f, message = null)
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(phase = UpdatePhase.ERROR, message = e.message ?: "下载失败")
                    }
                }
            )
        }
    }

    /**
     * 拉起系统安装器。
     * 若尚未授予「安装未知应用」权限，先跳授权页，返回后再点一次即可。
     */
    fun install(): Result<Unit> {
        val apk = downloadedApk ?: return Result.failure(IllegalStateException("尚未下载完成"))
        if (!ApkInstallHelper.canInstall(context)) {
            ApkInstallHelper.openUnknownSourcesSettings(context)
            return Result.failure(IllegalStateException("请先允许安装未知应用，返回后再点安装"))
        }
        return ApkInstallHelper.install(context, apk)
    }
}
