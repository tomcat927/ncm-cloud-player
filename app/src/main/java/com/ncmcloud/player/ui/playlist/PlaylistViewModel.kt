package com.ncmcloud.player.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.core.auth.UserPreferences
import com.ncmcloud.player.data.PlaylistRepository
import com.ncmcloud.player.domain.CloudSong
import com.ncmcloud.player.feature.playlist.data.NeteaseTrack
import com.ncmcloud.player.playback.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "PlaylistViewModel"

// 用户歌单（网易服务器端）列表条目
data class PlaylistUiItem(
    val id: Long,
    val name: String,
    val trackCount: Int,
)

// 歌单详情（曲目已映射为 CloudSong，可直接进播放队列）
data class PlaylistDetailUi(
    val id: Long,
    val name: String,
    val tracks: List<CloudSong>,
)

// "添加到歌单"弹层的单个歌单条目
data class CollectUiItem(
    val playlistId: Long,
    val name: String,
    val contains: Boolean,
    val working: Boolean = false,
)

// 收藏弹层状态：song 为目标歌曲，null 表示弹层关闭
data class CollectState(
    val song: CloudSong,
    val items: List<CollectUiItem>,
    val loading: Boolean,
    val error: String? = null,
)

class PlaylistViewModel(
    private val playlistRepository: PlaylistRepository,
    private val userPreferences: UserPreferences,
    private val playerController: PlayerController,
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<PlaylistUiItem>>(emptyList())
    val playlists: StateFlow<List<PlaylistUiItem>> = _playlists.asStateFlow()

    private val _playlistsLoading = MutableStateFlow(false)
    val playlistsLoading: StateFlow<Boolean> = _playlistsLoading.asStateFlow()

    private val _playlistsError = MutableStateFlow<String?>(null)
    val playlistsError: StateFlow<String?> = _playlistsError.asStateFlow()

    private val _detail = MutableStateFlow<PlaylistDetailUi?>(null)
    val detail: StateFlow<PlaylistDetailUi?> = _detail.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    private val _collect = MutableStateFlow<CollectState?>(null)
    val collect: StateFlow<CollectState?> = _collect.asStateFlow()

    // 各歌单的曲目 id 集合缓存，用于计算勾选状态；收藏操作会同步更新
    private val playlistTrackCache = mutableMapOf<Long, Set<Long>>()

    fun load() {
        viewModelScope.launch {
            _playlistsLoading.value = true
            _playlistsError.value = null
            _playlists.value = runCatching { fetchPlaylists() }
                .onFailure { AppLogger.e(TAG, "拉取用户歌单失败", it) }
                .getOrElse { emptyList() }
                .also { if (it.isEmpty()) _playlistsError.value = "暂无歌单" }
            _playlistsLoading.value = false
        }
    }

    private suspend fun fetchPlaylists(): List<PlaylistUiItem> {
        val uid = userPreferences.userProfile.first()?.uid
            ?: throw IllegalStateException("未获取到用户信息")
        return playlistRepository.getMyPlaylists(uid).map {
            PlaylistUiItem(id = it.id, name = it.name, trackCount = it.trackCount)
        }
    }

    fun openDetail(id: Long) {
        viewModelScope.launch {
            _detailLoading.value = true
            _detail.value = runCatching {
                val data = playlistRepository.getPlaylistDetail(id)
                PlaylistDetailUi(
                    id = data.id,
                    name = data.name,
                    tracks = data.tracks.map { it.toCloudSong() },
                )
            }.onFailure { AppLogger.e(TAG, "拉取歌单详情失败 id=$id", it) }
                .getOrNull()
            _detailLoading.value = false
        }
    }

    fun closeDetail() {
        _detail.value = null
    }

    fun playPlaylist() {
        val detail = _detail.value ?: return
        if (detail.tracks.isNotEmpty()) {
            viewModelScope.launch { playerController.playQueue(detail.tracks) }
        }
    }

    fun playTrack(index: Int) {
        val detail = _detail.value ?: return
        if (index in detail.tracks.indices) {
            viewModelScope.launch { playerController.playQueue(detail.tracks, index) }
        }
    }

    // 歌单列表页的"新建歌单"（不涉及收藏）
    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching { playlistRepository.createPlaylist(trimmed) }
                .onSuccess { load() }
                .onFailure {
                    AppLogger.e(TAG, "创建歌单失败 name=$trimmed", it)
                    _playlistsError.value = it.message ?: "创建失败"
                }
        }
    }

    // 打开"添加到歌单"弹层：拉用户歌单 + 各歌单曲目 id，计算勾选状态
    fun openCollect(song: CloudSong) {
        viewModelScope.launch {
            _collect.value = CollectState(song = song, items = emptyList(), loading = true)
            runCatching {
                val playlists = fetchPlaylists()
                val items = playlists.map { playlist ->
                    val trackIds = playlistTrackCache.getOrPut(playlist.id) {
                        runCatching { playlistRepository.getPlaylistDetail(playlist.id).tracks.map { it.id } }
                            .onFailure { AppLogger.w(TAG, "拉取歌单曲目失败 pid=${playlist.id}", it) }
                            .getOrDefault(emptySet())
                            .toSet()
                    }
                    CollectUiItem(
                        playlistId = playlist.id,
                        name = playlist.name,
                        contains = song.songId in trackIds,
                    )
                }
                _collect.value = CollectState(song = song, items = items, loading = false)
            }.onFailure {
                AppLogger.e(TAG, "打开收藏弹层失败", it)
                _collect.value = CollectState(song = song, items = emptyList(), loading = false, error = it.message ?: "加载失败")
            }
        }
    }

    fun toggleCollect(playlistId: Long, add: Boolean) {
        val state = _collect.value ?: return
        val song = state.song
        viewModelScope.launch {
            _collect.value = state.copy(
                items = state.items.map {
                    if (it.playlistId == playlistId) it.copy(working = true) else it
                },
            )
            val result = runCatching {
                if (add) playlistRepository.addTrack(playlistId, song.songId)
                else playlistRepository.removeTrack(playlistId, song.songId)
            }
            val current = _collect.value ?: return@launch
            _collect.value = current.copy(
                items = current.items.map { item ->
                    if (item.playlistId == playlistId) {
                        if (result.isSuccess) {
                            val updated = if (add) {
                                playlistTrackCache[playlistId].orEmpty() + song.songId
                            } else {
                                playlistTrackCache[playlistId].orEmpty() - song.songId
                            }
                            playlistTrackCache[playlistId] = updated
                            item.copy(contains = add, working = false)
                        } else {
                            item.copy(working = false)
                        }
                    } else {
                        item
                    }
                },
                error = result.exceptionOrNull()?.message,
            )
        }
    }

    fun createAndCollect(name: String) {
        val state = _collect.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val newId = playlistRepository.createPlaylist(trimmed)
                playlistRepository.addTrack(newId, state.song.songId)
                // 新歌单进入缓存与列表
                playlistTrackCache[newId] = setOf(state.song.songId)
                val playlists = fetchPlaylists()
                _playlists.value = playlists
                _collect.value = _collect.value?.copy(
                    items = playlists.map { playlist ->
                        CollectUiItem(
                            playlistId = playlist.id,
                            name = playlist.name,
                            contains = playlist.id == newId ||
                                playlistTrackCache[playlist.id].orEmpty().contains(state.song.songId),
                        )
                    },
                    error = null,
                )
            }.onFailure {
                AppLogger.e(TAG, "创建并收藏失败 name=$trimmed", it)
                _collect.value = _collect.value?.copy(error = it.message ?: "创建失败")
            }
        }
    }

    fun closeCollect() {
        _collect.value = null
    }

    // 云盘歌曲删除/改名后歌单快照会过期，外部通知失效清空缓存
    fun clearCache() {
        playlistTrackCache.clear()
    }

    private fun NeteaseTrack.toCloudSong(): CloudSong = CloudSong(
        songId = id,
        songName = name,
        artist = ar.joinToString("/") { it.name }.ifBlank { "未知艺人" },
        album = al.name,
        fileName = "",
        fileSize = 0,
        bitrate = 0,
        addTime = 0,
        matchType = "",
        simpleSongId = id,
        albumPicUrl = al.picUrl,
    )
}
