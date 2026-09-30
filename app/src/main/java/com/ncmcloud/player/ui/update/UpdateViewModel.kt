package com.ncmcloud.player.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.update.UpdateDownloadWorker
import com.ncmcloud.player.update.UpdateInfo
import com.ncmcloud.player.update.UpdateService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "UpdateViewModel"

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data object NoUpdate : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class ReadyToInstall(val file: File, val tagName: String) : UpdateState
    data class InstallPermissionRequired(val file: File, val tagName: String) : UpdateState
    data object Installing : UpdateState
    data class Error(val message: String) : UpdateState
}

class UpdateViewModel(
    private val updateService: UpdateService,
    private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val workManager = WorkManager.getInstance(context)

    init {
        observeDownload()
    }

    fun check(manual: Boolean = false) {
        viewModelScope.launch {
            _state.value = UpdateState.Checking
            _state.value = runCatching { updateService.checkForUpdate() }
                .fold(
                    onSuccess = { info ->
                        when {
                            info != null -> UpdateState.Available(info)
                            manual -> UpdateState.NoUpdate
                            else -> UpdateState.Idle
                        }
                    },
                    onFailure = { UpdateState.Error(it.message ?: "检查更新失败") },
                )
        }
    }

    fun download() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        val data = workDataOf(
            "tagName" to info.tagName,
            "versionCode" to info.versionCode,
            "downloadUrl" to info.downloadUrl,
            "fallbackDownloadUrl" to info.fallbackDownloadUrl,
            "checksumUrl" to info.checksumUrl,
            "fallbackChecksumUrl" to info.fallbackChecksumUrl,
            "releaseUrl" to info.releaseUrl,
            "releaseNotes" to info.releaseNotes,
        )
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setInputData(data)
            .addTag("update-download")
            .build()
        workManager.enqueueUniqueWork("update-download", ExistingWorkPolicy.REPLACE, request)
        _state.value = UpdateState.Downloading(-1f)
        AppLogger.i(TAG, "已提交更新下载任务: ${info.tagName}")
    }

    fun install() {
        val current = _state.value as? UpdateState.ReadyToInstall ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _state.value = UpdateState.InstallPermissionRequired(current.file, current.tagName)
            AppLogger.w(TAG, "系统未授予安装未知应用权限")
            return
        }

        runCatching {
            context.startActivity(updateService.createInstallIntent(current.file))
            _state.value = UpdateState.Installing
            AppLogger.i(TAG, "已打开系统安装器: ${current.tagName}")
        }.onFailure {
            _state.value = UpdateState.Error(it.message ?: "无法打开系统安装器")
            AppLogger.e(TAG, "打开系统安装器失败", it)
        }
    }

    fun refreshInstallPermission() {
        val current = _state.value as? UpdateState.InstallPermissionRequired ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.canRequestPackageInstalls()
        ) {
            _state.value = UpdateState.ReadyToInstall(current.file, current.tagName)
            AppLogger.i(TAG, "安装未知应用权限已授予")
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val uri = Uri.parse("package:${context.packageName}")
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            AppLogger.i(TAG, "已打开安装未知应用授权设置")
        }.onFailure {
            _state.value = UpdateState.Error(it.message ?: "无法打开授权设置")
            AppLogger.e(TAG, "打开安装授权设置失败", it)
        }
    }

    private fun observeDownload() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow("update-download").collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        _state.value = UpdateState.Downloading(info.progress.getFloat("progress", -1f))
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        val path = info.outputData.getString("apkPath")
                        val tagName = info.outputData.getString("tagName").orEmpty()
                        if (path.isNullOrBlank()) {
                            _state.value = UpdateState.Error("下载完成但未获取到 APK 路径")
                            AppLogger.e(TAG, "下载成功但输出数据缺少 apkPath")
                        } else {
                            _state.value = UpdateState.ReadyToInstall(File(path), tagName)
                            AppLogger.i(TAG, "进入安装确认状态: $tagName")
                        }
                    }

                    WorkInfo.State.FAILED -> {
                        val message = info.outputData.getString("error") ?: "下载或安装失败"
                        _state.value = UpdateState.Error(message)
                        AppLogger.e(TAG, "更新下载失败: $message")
                    }

                    WorkInfo.State.CANCELLED -> {
                        _state.value = UpdateState.Idle
                        AppLogger.w(TAG, "更新下载任务被取消")
                    }

                    else -> {}
                }
            }
        }
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }
}
