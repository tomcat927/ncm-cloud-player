package com.ncmcloud.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ncmcloud.player.MainActivity
import org.koin.core.context.GlobalContext

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val sessionActivityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // App 的队列由 PlayerController 管理，底层 ExoPlayer 每次只装载当前歌曲。
        // 通过 ForwardingPlayer 把系统媒体卡片的上一首/下一首命令映射回应用队列。
        val queuePlayer = QueueAwarePlayer(
            delegate = player,
            onPrevious = { playerController().skipToPrevious() },
            onNext = { playerController().skipToNext() },
        )
        mediaSession = MediaSession.Builder(this, queuePlayer)
            .setSessionActivity(pendingIntent)
            .build()
    }

    private fun playerController(): PlayerController =
        GlobalContext.get().get()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

private class QueueAwarePlayer(
    delegate: Player,
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit,
) : ForwardingPlayer(delegate) {
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .build()

    override fun seekToPreviousMediaItem() = onPrevious()

    override fun seekToNextMediaItem() = onNext()

    @Suppress("DEPRECATION")
    override fun seekToPrevious() = onPrevious()

    @Suppress("DEPRECATION")
    override fun seekToNext() = onNext()
}
