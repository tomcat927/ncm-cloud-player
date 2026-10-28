package com.ncmcloud.player.ui.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.data.AuthRepository
import com.ncmcloud.player.data.CloudDiskSummary
import com.ncmcloud.player.data.CloudRepository
import com.ncmcloud.player.domain.CloudSong
import com.ncmcloud.player.playback.PlayerController
import com.ncmcloud.player.playback.QueueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "CloudViewModel"
private const val PAGE_SIZE = 100

sealed interface CloudState {
    data object Loading : CloudState
    data class Content(val songs: List<CloudSong>) : CloudState
    data class Error(val message: String) : CloudState
}

class CloudViewModel(
    private val cloudRepository: CloudRepository,
    private val playerController: PlayerController,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<CloudState>(CloudState.Loading)
    val state: StateFlow<CloudState> = _state.asStateFlow()

    private val _diskSummary = MutableStateFlow<CloudDiskSummary?>(null)
    val diskSummary: StateFlow<CloudDiskSummary?> = _diskSummary.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadMoreError = MutableStateFlow<String?>(null)
    val loadMoreError: StateFlow<String?> = _loadMoreError.asStateFlow()

    private var offset = 0
    private val loaded = mutableListOf<CloudSong>()
    private val loadedIds = mutableSetOf<Long>()
    private var loadingJob: Job? = null

    init { loadFirstPage() }

    fun loadFirstPage() {
        loadingJob?.cancel()
        offset = 0
        loaded.clear()
        loadedIds.clear()
        _diskSummary.value = null
        _loadMoreError.value = null
        _isLoadingMore.value = false
        _hasMore.value = true
        _state.value = CloudState.Loading
        loadingJob = viewModelScope.launch { loadPagesFromCurrentOffset() }
    }

    fun loadNextPage() {
        if (loadingJob?.isActive == true || !_hasMore.value) return
        _loadMoreError.value = null
        loadingJob = viewModelScope.launch { loadPagesFromCurrentOffset() }
    }

    private suspend fun loadPagesFromCurrentOffset() {
        var firstPage = loaded.isEmpty()
        while (_hasMore.value) {
            _isLoadingMore.value = !firstPage
            val page = try {
                cloudRepository.getCloudSongs(limit = PAGE_SIZE, offset = offset)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = error.message ?: "加载云盘失败"
                _isLoadingMore.value = false
                if (loaded.isEmpty()) {
                    _state.value = CloudState.Error(message)
                } else {
                    _loadMoreError.value = message
                    AppLogger.e(TAG, "云盘分页失败 offset=$offset loaded=${loaded.size}", error)
                }
                return
            }

            val pageSongs = page.songs
            val newSongs = pageSongs.filter { loadedIds.add(it.songId) }
            if (pageSongs.isNotEmpty()) {
                offset += pageSongs.size
                loaded.addAll(newSongs)
            }
            _diskSummary.value = page.summary
            _state.value = CloudState.Content(loaded.toList())

            if (!firstPage && newSongs.isNotEmpty()) {
                playerController.appendSongsFromSource(newSongs, QueueSource.CLOUD)
            }

            // 部分服务端响应不带 hasMore；用总数兜底，避免只加载首屏。
            _hasMore.value = pageSongs.isNotEmpty() &&
                (page.hasMore || page.summary.songCount > loaded.size)
            AppLogger.i(
                TAG,
                "云盘分页 offset=$offset returned=${pageSongs.size} unique=${newSongs.size} " +
                    "loaded=${loaded.size}/${page.summary.songCount} hasMore=${_hasMore.value}",
            )
            firstPage = false
        }
        _isLoadingMore.value = false
    }

    fun play(song: CloudSong) {
        val index = loaded.indexOfFirst { it.songId == song.songId }
        viewModelScope.launch {
            runCatching {
                if (index >= 0) {
                    playerController.playQueue(
                        songs = loaded.toList(),
                        startIndex = index,
                        source = QueueSource.CLOUD,
                    )
                } else {
                    playerController.play(song)
                }
            }.onFailure { _state.value = CloudState.Error(it.message ?: "播放失败") }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
