package com.ncmcloud.player.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class UpdateViewModel(private val updateService: UpdateService) : ViewModel() {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

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
        viewModelScope.launch {
            _state.value = UpdateState.Downloading(-1f)
            _state.value = runCatching { updateService.downloadAndInstall(info) { p ->
                _state.value = UpdateState.Downloading(p)
            } }
                .fold(
                    onSuccess = { UpdateState.Idle },
                    onFailure = { UpdateState.Error(it.message ?: "下载或安装失败") },
                )
        }
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }
}
