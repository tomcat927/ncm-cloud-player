package com.ncmcloud.player.ui.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ncmcloud.player.update.UpdateDownloadWorker
import com.ncmcloud.player.update.UpdateInfo
import com.ncmcloud.player.update.UpdateService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data object NoUpdate : UpdateState
    data class Downloading(val progress: Float) : UpdateState
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
                        _state.value = UpdateState.Idle
                    }
                    WorkInfo.State.FAILED -> {
                        _state.value = UpdateState.Error(info.outputData.getString("error") ?: "下载或安装失败")
                    }
                    WorkInfo.State.CANCELLED -> {
                        _state.value = UpdateState.Idle
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
