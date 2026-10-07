package com.ncmcloud.player.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ncmcloud.player.BuildConfig
import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.core.preferences.SettingsPreferences
import com.ncmcloud.player.update.UpdateDownloadWorker
import com.ncmcloud.player.update.UpdateInfo
import com.ncmcloud.player.update.UpdateService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "UpdateViewModel"

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data object NoUpdate : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    // 下载完成但 app 在后台（无法直接拉起安装器）时的兜底：等用户回来点一下
    data class ReadyToInstall(val file: File, val tagName: String) : UpdateState
    data class InstallPermissionRequired(val file: File, val tagName: String) : UpdateState
    data class Error(val message: String) : UpdateState
}

class UpdateViewModel(
    private val updateService: UpdateService,
    private val context: Context,
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val workManager = WorkManager.getInstance(context)

    // 由 UpdateDialog 的生命周期回调维护，决定下载完成后能否直接拉起安装器
    private var appInForeground = false

    // 「稍后」后本次会话不再弹下载进度/完成框（后台预下载继续跑，装不装由系统通知引导）
    private var userDismissed = false

    fun setAppForeground(value: Boolean) {
        appInForeground = value
    }

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
                            info != null -> {
                                maybeAutoDownload(info)
                                UpdateState.Available(info)
                            }
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
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setInputData(updateWorkData(info))
            .addTag("update-download")
            .build()
        // REPLACE 覆盖可能还挂在等 Wi-Fi 的自动预下载任务：用户明确点更新，不受网络约束
        workManager.enqueueUniqueWork("update-download", ExistingWorkPolicy.REPLACE, request)
        userDismissed = false
        _state.value = UpdateState.Downloading(-1f)
        AppLogger.i(TAG, "已提交更新下载任务: ${info.tagName}")
    }

    // 发现新版本后的后台预下载：仅非计费网络（Wi-Fi 等）执行，任务持久化、断网挂起回网续跑，
    // 完成后由 Worker 发系统通知引导安装。可在设置里关闭（updateAutoDownload 开关）
    private fun maybeAutoDownload(info: UpdateInfo) {
        viewModelScope.launch {
            val enabled = runCatching { settingsPreferences.updateAutoDownload.first() }.getOrDefault(true)
            if (!enabled) return@launch
            val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setInputData(updateWorkData(info))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .addTag("update-download")
                .build()
            // KEEP：已有任务（运行中/等网络/手动提交）不打断
            workManager.enqueueUniqueWork("update-download", ExistingWorkPolicy.KEEP, request)
            AppLogger.i(TAG, "发现新版本，已排队后台预下载（非计费网络）: ${info.tagName}")
        }
    }

    private fun updateWorkData(info: UpdateInfo) = workDataOf(
        "tagName" to info.tagName,
        "versionCode" to info.versionCode,
        "downloadUrl" to info.downloadUrl,
        "fallbackDownloadUrl" to info.fallbackDownloadUrl,
        "checksumUrl" to info.checksumUrl,
        "fallbackChecksumUrl" to info.fallbackChecksumUrl,
        "releaseUrl" to info.releaseUrl,
        "releaseNotes" to info.releaseNotes,
    )

    fun install() {
        // 兜底路径：app 在后台时完成的下载，等用户回到 app 手动触发
        val current = _state.value as? UpdateState.ReadyToInstall ?: return
        tryFireInstaller(current.file, current.tagName)
    }

    // 前台直接拉起系统安装器（与 notion-app-android 一致，系统安装器即唯一一次确认）。
    // 未授予安装权限时先引导授权，返回后自动继续；后台时兜底为 ReadyToInstall 对话框。
    private fun tryFireInstaller(file: File, tagName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _state.value = UpdateState.InstallPermissionRequired(file, tagName)
            AppLogger.w(TAG, "系统未授予安装未知应用权限")
            return
        }

        runCatching {
            context.startActivity(updateService.createInstallIntent(file))
            _state.value = UpdateState.Idle
            AppLogger.i(TAG, "已拉起系统安装器: $tagName")
        }.onFailure {
            AppLogger.e(TAG, "拉起系统安装器失败，回退为手动安装", it)
            _state.value = UpdateState.ReadyToInstall(file, tagName)
        }
    }

    fun refreshInstallPermission() {
        val current = _state.value as? UpdateState.InstallPermissionRequired ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.canRequestPackageInstalls()
        ) {
            AppLogger.i(TAG, "安装未知应用权限已授予，继续拉起安装器")
            tryFireInstaller(current.file, current.tagName)
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
                        if (!userDismissed) {
                            _state.value = UpdateState.Downloading(info.progress.getFloat("progress", -1f))
                        }
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        val path = info.outputData.getString("apkPath")
                        val tagName = info.outputData.getString("tagName").orEmpty()
                        val downloadedVersionCode = info.outputData.getLong("versionCode", 0L)
                        if (path.isNullOrBlank()) {
                            if (!userDismissed) _state.value = UpdateState.Error("下载完成但未获取到 APK 路径")
                            AppLogger.e(TAG, "下载成功但输出数据缺少 apkPath")
                        } else if (downloadedVersionCode <= BuildConfig.VERSION_CODE.toLong()) {
                            // WorkManager 的完成记录会跨进程存活：每次启动都会重新上报。
                            // 版本不高于当前 app 的记录属于已安装/同版本的残留（含旧构建未写
                            // versionCode 的记录），忽略并清理，否则每次打开 app 都会对同版本拉起安装器
                            AppLogger.i(
                                TAG,
                                "忽略残留下载记录: $tagName versionCode=$downloadedVersionCode <= 当前 ${BuildConfig.VERSION_CODE}",
                            )
                            runCatching { File(path).delete() }
                            workManager.pruneWork()
                        } else if (userDismissed) {
                            // 点过「稍后」：不弹框不打断，装不装交给 Worker 发的系统通知
                            AppLogger.i(TAG, "下载完成（已稍后），由系统通知引导安装: $tagName")
                            workManager.pruneWork()
                        } else if (appInForeground) {
                            // 前台：跳过 App 内确认，直接拉起系统安装器（一次确认）
                            AppLogger.i(TAG, "下载完成（前台），直接拉起安装器: $tagName")
                            tryFireInstaller(File(path), tagName)
                            workManager.pruneWork()
                        } else {
                            _state.value = UpdateState.ReadyToInstall(File(path), tagName)
                            AppLogger.i(TAG, "下载完成（后台），进入待安装状态: $tagName")
                            workManager.pruneWork()
                        }
                    }

                    WorkInfo.State.FAILED -> {
                        val message = info.outputData.getString("error") ?: "下载或安装失败"
                        if (!userDismissed) _state.value = UpdateState.Error(message)
                        AppLogger.e(TAG, "更新下载失败: $message")
                    }

                    WorkInfo.State.CANCELLED -> {
                        if (!userDismissed) _state.value = UpdateState.Idle
                        AppLogger.w(TAG, "更新下载任务被取消")
                    }

                    else -> {}
                }
            }
        }
    }

    fun dismiss() {
        // 「稍后」只收起界面：已排队的后台预下载继续跑，完成后靠系统通知引导安装
        userDismissed = true
        _state.value = UpdateState.Idle
    }
}
