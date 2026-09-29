package com.ncmcloud.player.ui.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmcloud.player.data.CloudRepository
import com.ncmcloud.player.domain.CloudSong
import com.ncmcloud.player.playback.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CloudState {
    data object Loading : CloudState
    data class Content(val songs: List<CloudSong>) : CloudState
    data class Error(val message: String) : CloudState
}

class CloudViewModel(
    private val cloudRepository: CloudRepository,
    private val playerController: PlayerController,
) : ViewModel() {
    private val _state = MutableStateFlow<CloudState>(CloudState.Loading)
    val state: StateFlow<CloudState> = _state.asStateFlow()

    private var offset = 0
    private val loaded = mutableListOf<CloudSong>()

    init { loadFirstPage() }

    fun loadFirstPage() {
        offset = 0
        loaded.clear()
        loadNextPage()
    }

    fun loadNextPage() {
        viewModelScope.launch {
            _state.value = CloudState.Loading
            _state.value = runCatching { cloudRepository.getCloudSongs(limit = 100, offset = offset) }
                .fold(
                    onSuccess = { songs ->
                        loaded.addAll(songs)
                        offset += songs.size
                        CloudState.Content(loaded.toList())
                    },
                    onFailure = { CloudState.Error(it.message ?: "加载云盘失败") },
                )
        }
    }

    fun play(song: CloudSong) {
        viewModelScope.launch {
            runCatching { playerController.play(song) }
                .onFailure { _state.value = CloudState.Error(it.message ?: "播放失败") }
        }
    }
}
