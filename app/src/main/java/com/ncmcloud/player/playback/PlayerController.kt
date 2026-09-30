package com.ncmcloud.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    private val _queue = MutableStateFlow<List<CloudSong>>(emptyList())
    val queue: StateFlow<List<CloudSong>> = _queue

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private var queueList: List<CloudSong> = emptyList()
    private var currentIndexValue = -1
    private var progressJob: Job? = null

    suspend fun connect() = withContext(Dispatchers.Main) {
        if (controller != null) return@withContext
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val built = awaitController(token)
        controller = built
        built.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startProgressUpdates() else stopProgressUpdates()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val player = controller ?: return
                if (playbackState == Player.STATE_READY) {
                    if (player.duration > 0L) _duration.value = player.duration
                    _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    stopProgressUpdates()
                    if (currentIndexValue < queueList.lastIndex) {
                        skipToNext()
                    } else {
                        _currentPosition.value = _duration.value
                    }
                }
            }
        })
        _duration.value = built.duration.takeIf { it > 0L } ?: 0L
        _currentPosition.value = built.currentPosition.coerceAtLeast(0L)
    }

    private suspend fun awaitController(token: SessionToken): MediaController {
        val future = MediaController.Builder(context, token).buildAsync()
        return suspendCancellableCoroutine { cont ->
            future.addListener({
                try {
                    cont.resume(future.get())
                } catch (e: Throwable) {
                    val cause = if (e is ExecutionException) e.cause ?: e else e
                    cont.resumeWithException(cause)
                }
            }, ContextCompat.getMainExecutor(context))
            cont.invokeOnCancellation { future.cancel(true) }
        }
    }

    fun disconnect() {
        progressJob?.cancel()
        progressJob = null
        controller?.release()
        controller = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    suspend fun playQueue(songs: List<CloudSong>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        queueList = songs
        currentIndexValue = startIndex.coerceIn(0, songs.lastIndex)
        publishQueue()
        playCurrent()
    }

    suspend fun play(song: CloudSong) = playQueue(listOf(song), 0)

    fun skipToNext() {
        if (currentIndexValue < 0 || currentIndexValue >= queueList.lastIndex) return
        currentIndexValue++
        publishQueue()
        launchPlayCurrent()
    }

    fun skipToPrevious() {
        if (currentIndexValue <= 0) return
        currentIndexValue--
        publishQueue()
        launchPlayCurrent()
    }

    fun playAtIndex(index: Int) {
        if (index < 0 || index >= queueList.size) return
        currentIndexValue = index
        publishQueue()
        launchPlayCurrent()
    }

    fun togglePlay() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        val player = controller ?: return
        val durationMs = _duration.value
        val target = if (durationMs > 0L) {
            positionMs.coerceIn(0L, durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        player.seekTo(target)
        _currentPosition.value = target
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        controller?.stop()
        queueList = emptyList()
        currentIndexValue = -1
        publishQueue()
        _nowPlaying.value = null
        _currentPosition.value = 0L
        _duration.value = 0L
        updateSkipFlags()
    }

    private fun launchPlayCurrent() {
        scope.launch {
            runCatching { playCurrent() }
                .onFailure { AppLogger.e(TAG, "播放当前曲目失败", it) }
        }
    }

    private suspend fun playCurrent() {
        if (currentIndexValue < 0 || currentIndexValue >= queueList.size) return
        val song = queueList[currentIndexValue]
        val level = settingsPreferences.playQuality.first()
        val url = runCatching { playbackRepository.getSongUrl(song.songId, level) }
            .onFailure { AppLogger.e(TAG, "获取播放地址失败: ${song.songId}", it) }
            .getOrNull()
            ?: throw IllegalStateException("获取播放地址失败")
        val metadata = MediaMetadata.Builder()
            .setTitle(song.displayTitle)
            .setArtist(song.displayArtist)
            .setAlbumTitle(song.album)
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
        _currentPosition.value = 0L
        _duration.value = 0L
        updateSkipFlags()
    }

    private fun publishQueue() {
        _queue.value = queueList
        _currentIndex.value = currentIndexValue
    }

    private fun updateSkipFlags() {
        _canSkipNext.value = currentIndexValue in 0 until queueList.lastIndex
        _canSkipPrevious.value = currentIndexValue > 0
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = controller ?: break
                _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
                if (player.duration > 0L) _duration.value = player.duration
                delay(500L)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }
}

