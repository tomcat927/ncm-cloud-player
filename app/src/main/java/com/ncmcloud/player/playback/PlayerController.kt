package com.ncmcloud.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.core.preferences.SettingsPreferences
import com.ncmcloud.player.data.PlaybackRepository
import com.ncmcloud.player.domain.CloudSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "PlayerController"

data class NowPlaying(
    val song: CloudSong,
    val url: String,
)

class PlayerController(
    private val context: Context,
    private val playbackRepository: PlaybackRepository,
    private val settingsPreferences: SettingsPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _canSkipNext = MutableStateFlow(false)
    val canSkipNext: StateFlow<Boolean> = _canSkipNext

    private val _canSkipPrevious = MutableStateFlow(false)
    val canSkipPrevious: StateFlow<Boolean> = _canSkipPrevious

    private var queue: List<CloudSong> = emptyList()
    private var currentIndex = -1

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controller = MediaController.Builder(context, token).buildAsync().get()
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
        })
    }

    fun disconnect() {
        controller?.release()
        controller = null
        _isPlaying.value = false
    }

    suspend fun playQueue(songs: List<CloudSong>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        queue = songs
        currentIndex = startIndex.coerceIn(0, songs.lastIndex)
        playCurrent()
    }

    suspend fun play(song: CloudSong) = playQueue(listOf(song), 0)

    fun skipToNext() {
        if (currentIndex < 0 || currentIndex >= queue.lastIndex) return
        currentIndex++
        launchPlayCurrent()
    }

    fun skipToPrevious() {
        if (currentIndex <= 0) return
        currentIndex--
        launchPlayCurrent()
    }

    fun togglePlay() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun stop() {
        controller?.stop()
        queue = emptyList()
        currentIndex = -1
        _nowPlaying.value = null
        updateSkipFlags()
    }

    private fun launchPlayCurrent() {
        scope.launch {
            runCatching { playCurrent() }
                .onFailure { AppLogger.e(TAG, "播放当前曲目失败", it) }
        }
    }

    private suspend fun playCurrent() {
        if (currentIndex < 0 || currentIndex >= queue.size) return
        val song = queue[currentIndex]
        val level = settingsPreferences.playQuality.first()
        val url = runCatching { playbackRepository.getSongUrl(song.songId, level) }
            .onFailure { AppLogger.e(TAG, "获取播放地址失败: ${song.songId}", it) }
            .getOrNull()
            ?: throw IllegalStateException("获取播放地址失败")
        val metadata = MediaMetadata.Builder()
            .setTitle(song.displayTitle)
            .setArtist(song.displayArtist)
            .build()
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()
        val player = controller ?: run {
            connect()
            controller
        }
        player?.setMediaItem(item)
        player?.prepare()
        player?.play()
        _nowPlaying.value = NowPlaying(song, url)
        updateSkipFlags()
    }

    private fun updateSkipFlags() {
        _canSkipNext.value = currentIndex in 0 until queue.lastIndex
        _canSkipPrevious.value = currentIndex > 0
    }
}

