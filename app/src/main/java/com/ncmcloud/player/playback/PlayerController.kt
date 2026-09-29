package com.ncmcloud.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ncmcloud.player.data.PlaybackRepository
import com.ncmcloud.player.core.preferences.SettingsPreferences
import com.ncmcloud.player.domain.CloudSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

data class NowPlaying(
    val song: CloudSong,
    val url: String,
)

class PlayerController(
    private val context: Context,
    private val playbackRepository: PlaybackRepository,
    private val settingsPreferences: SettingsPreferences,
) {
    private var controller: MediaController? = null

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

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

    suspend fun play(song: CloudSong) {
        connect()
        val level = settingsPreferences.playQuality.first()
        val url = playbackRepository.getSongUrl(song.songId, level)
            ?: throw IllegalStateException("获取播放地址失败")
        val metadata = MediaMetadata.Builder()
            .setTitle(song.displayTitle)
            .setArtist(song.displayArtist)
            .build()
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()
        controller?.setMediaItem(item)
        controller?.prepare()
        controller?.play()
        _nowPlaying.update { NowPlaying(song, url) }
    }

    fun togglePlay() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun stop() {
        controller?.stop()
        _nowPlaying.value = null
    }
}
