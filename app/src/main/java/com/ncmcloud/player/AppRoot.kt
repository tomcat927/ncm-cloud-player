package com.ncmcloud.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmcloud.player.data.AuthRepository
import com.ncmcloud.player.playback.PlayerController
import com.ncmcloud.player.ui.cloud.CloudScreen
import com.ncmcloud.player.ui.log.LogScreen
import com.ncmcloud.player.ui.login.LoginScreen
import org.koin.core.context.GlobalContext

@Composable
fun AppRoot() {
    val authRepository = remember { GlobalContext.get().get<AuthRepository>() }
    val playerController = remember { GlobalContext.get().get<PlayerController>() }
    var showLogs by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { playerController.connect() }

    val cookies by authRepository.cookies.collectAsStateWithLifecycle(initialValue = null)

    when {
        showLogs -> LogScreen(onBack = { showLogs = false })
        cookies.isNullOrBlank() -> LoginScreen(onOpenLogs = { showLogs = true })
        else -> CloudScreen(onOpenLogs = { showLogs = true })
    }
}
