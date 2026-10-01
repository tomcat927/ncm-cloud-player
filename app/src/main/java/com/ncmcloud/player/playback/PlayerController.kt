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
import com.ncmcloud.player.domain.PlayMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    // 本次取址服务端实际返回的码率/格式/大小，用于与云盘元数据对比确认音质档位是否生效
    val actualBitrate: Long = 0,
    val actualType: String? = null,
    val actualSize: Long = 0,
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

    private val _playMode = MutableStateFlow(PlayMode.ORDER)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private var queueList: List<CloudSong> = emptyList()
    private var originalQueue: List<CloudSong> = emptyList()
    private var currentIndexValue = -1
    private var progressJob: Job? = null

    init {
        scope.launch {
            val saved = PlayMode.fromName(settingsPreferences.playMode.first())
            if (saved != _playMode.value) {
                _playMode.value = saved
                applyRepeatMode()
                updateSkipFlags()
            }
        }
    }

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
                    when (_playMode.value) {
                        // REPEAT_MODE_ONE 正常不会走到 ENDED，这里兜底重播
                        PlayMode.SINGLE_LOOP -> launchPlayCurrent()
                        PlayMode.LIST_LOOP, PlayMode.SHUFFLE -> skipToNext()
                        PlayMode.ORDER -> {
                            if (currentIndexValue < queueList.lastIndex) {
                                skipToNext()
                            } else {
                                _currentPosition.value = _duration.value
                            }
                        }
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
        originalQueue = songs
        currentIndexValue = startIndex.coerceIn(0, songs.lastIndex)
        if (_playMode.value == PlayMode.SHUFFLE) reshuffleQueue()
        publishQueue()
        playCurrent()
    }

    suspend fun play(song: CloudSong) = playQueue(listOf(song), 0)

    fun cyclePlayMode(): PlayMode {
        val next = _playMode.value.next()
        applyPlayMode(next)
        return next
    }

    fun skipToNext() {
        if (queueList.isEmpty()) return
        val wraps = _playMode.value != PlayMode.ORDER
        if (!wraps && currentIndexValue >= queueList.lastIndex) return
        currentIndexValue = if (currentIndexValue >= queueList.lastIndex) 0 else currentIndexValue + 1
        publishQueue()
        launchPlayCurrent()
    }

    fun skipToPrevious() {
        if (queueList.isEmpty()) return
        val wraps = _playMode.value != PlayMode.ORDER
        if (!wraps && currentIndexValue <= 0) return
        currentIndexValue = if (currentIndexValue <= 0) queueList.lastIndex else currentIndexValue - 1
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
        originalQueue = emptyList()
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
        val stream = runCatching { playbackRepository.getSongStream(song.songId, level) }
            .onFailure { AppLogger.e(TAG, "获取播放地址失败: ${song.songId}", it) }
            .getOrNull()
            ?: throw IllegalStateException("获取播放地址失败")
        val url = stream.url ?: throw IllegalStateException("获取播放地址失败")
        AppLogger.i(TAG, "取址完成 level=$level br=${stream.br} type=${stream.type} size=${stream.size}")
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
        applyRepeatMode()
        player?.setMediaItem(item)
        player?.prepare()
        player?.play()
        _nowPlaying.value = NowPlaying(
            song = song,
            url = url,
            actualBitrate = stream.br,
            actualType = stream.type,
            actualSize = stream.size,
        )
        _currentPosition.value = 0L
        _duration.value = 0L
        updateSkipFlags()
    }

    private fun applyPlayMode(mode: PlayMode) {
        if (_playMode.value == mode) return
        val wasShuffle = _playMode.value == PlayMode.SHUFFLE
        _playMode.value = mode
        scope.launch { settingsPreferences.setPlayMode(mode.name) }
        if (mode == PlayMode.SHUFFLE) {
            reshuffleQueue()
        } else if (wasShuffle) {
            restoreQueueOrder()
        }
        applyRepeatMode()
        updateSkipFlags()
    }

    private fun applyRepeatMode() {
        val player = controller ?: return
        player.repeatMode = if (_playMode.value == PlayMode.SINGLE_LOOP) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    /** 随机模式下重排队列：当前曲目固定到首位，其余打乱；原顺序留在 originalQueue 以便还原。 */
    private fun reshuffleQueue() {
        if (queueList.isEmpty()) return
        val current = queueList.getOrNull(currentIndexValue) ?: return
        originalQueue = queueList
        queueList = listOf(current) + queueList
            .filterIndexed { index, _ -> index != currentIndexValue }
            .shuffled()
        currentIndexValue = 0
        publishQueue()
    }

    private fun restoreQueueOrder() {
        val original = originalQueue
        if (original.isEmpty()) return
        val current = queueList.getOrNull(currentIndexValue)
        val restoredIndex = current
            ?.let { song -> original.indexOfFirst { it.songId == song.songId } }
            ?: -1
        queueList = original
        currentIndexValue = if (restoredIndex >= 0) restoredIndex else 0
        publishQueue()
    }

    private fun publishQueue() {
        _queue.value = queueList
        _currentIndex.value = currentIndexValue
    }

    private fun updateSkipFlags() {
        val wraps = _playMode.value != PlayMode.ORDER
        _canSkipNext.value = if (wraps) {
            queueList.isNotEmpty()
        } else {
            currentIndexValue in 0 until queueList.lastIndex
        }
        _canSkipPrevious.value = if (wraps) {
            queueList.isNotEmpty()
        } else {
            currentIndexValue > 0
        }
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

