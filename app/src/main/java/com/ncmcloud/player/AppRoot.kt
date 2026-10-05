package com.ncmcloud.player

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.data.AuthRepository
import com.ncmcloud.player.playback.PlayerController
import com.ncmcloud.player.ui.cloud.CloudScreen
import com.ncmcloud.player.ui.log.LogScreen
import com.ncmcloud.player.ui.login.LoginScreen
import com.ncmcloud.player.ui.playlist.PlaylistScreen
import com.ncmcloud.player.ui.settings.SettingsScreen
import com.ncmcloud.player.ui.player.PlayerDetailScreen
import com.ncmcloud.player.ui.player.PlayerQueueSheet
import com.ncmcloud.player.ui.update.UpdateDialog
import com.ncmcloud.player.ui.update.UpdateViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.GlobalContext

@Composable
fun AppRoot() {
    val authRepository = remember { GlobalContext.get().get<AuthRepository>() }
    val playerController = remember { GlobalContext.get().get<PlayerController>() }
    val updateViewModel: UpdateViewModel = koinViewModel()
    var showLogs by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var showPlayerDetail by remember { mutableStateOf(false) }
    var showPlayerQueue by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        playerController.connect()
        updateViewModel.check(manual = false)
    }

    val cookies by authRepository.cookies.collectAsStateWithLifecycle(initialValue = null)
    val nowPlaying by playerController.nowPlaying.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(nowPlaying) {
        if (nowPlaying == null) {
            showPlayerDetail = false
            showPlayerQueue = false
        }
    }

    BackHandler(enabled = showPlayerQueue) { showPlayerQueue = false }
    BackHandler(enabled = showPlayerDetail && !showPlayerQueue) {
        AppLogger.i("Nav", "返回键：关闭播放详情页")
        showPlayerDetail = false
    }

    LaunchedEffect(showLogs, showSettings, showPlayerDetail, showPlayerQueue) {
        AppLogger.i(
            "Nav",
            "页面状态: 日志页=$showLogs 设置页=$showSettings 详情页=$showPlayerDetail 队列=$showPlayerQueue",
        )
    }

    // 临时埋点：记录详情页关闭后新画面真正渲染出来的时刻，用于区分"处理慢"还是"渲染慢"
    LaunchedEffect(showPlayerDetail) {
        if (!showPlayerDetail) {
            withFrameNanos { }
            AppLogger.i("Nav", "详情页关闭后首帧已渲染")
        }
    }

    when {
        showLogs -> LogScreen(onBack = { showLogs = false })
        showSettings -> SettingsScreen(
            onBack = { showSettings = false },
            onOpenLogs = {
                showSettings = false
                showLogs = true
            },
            onCheckUpdate = { updateViewModel.check(manual = true) },
        )
        showPlaylists -> PlaylistScreen(onClose = { showPlaylists = false })
        cookies.isNullOrBlank() -> LoginScreen(onOpenLogs = { showLogs = true }, onCheckUpdate = { updateViewModel.check(manual = true) })
        else -> CloudScreen(
            onOpenSettings = { showSettings = true },
            onOpenPlayerDetail = { showPlayerDetail = true },
            onOpenPlayerQueue = { showPlayerQueue = true },
            onOpenPlaylists = { showPlaylists = true },
        )
    }

    // 临时埋点：主线程心跳，若时间戳出现大间隔说明主线程被阻塞
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            AppLogger.i("Nav", "主线程心跳")
        }
    }

    if (showPlayerDetail) {
        PlayerDetailScreen(
            playerController = playerController,
            onClose = { showPlayerDetail = false },
            onOpenQueue = { showPlayerQueue = true },
        )
    }

    if (showPlayerQueue) {
        PlayerQueueSheet(
            playerController = playerController,
            onDismiss = { showPlayerQueue = false },
        )
    }

    UpdateDialog()
}


