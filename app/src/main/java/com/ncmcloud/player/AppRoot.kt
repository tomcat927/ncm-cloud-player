package com.ncmcloud.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmcloud.player.data.AuthRepository
import com.ncmcloud.player.playback.PlayerController
import com.ncmcloud.player.ui.cloud.CloudScreen
import com.ncmcloud.player.ui.login.LoginScreen
import org.koin.androidx.compose.koinInject

@Composable
fun AppRoot() {
    val authRepository = koinInject<AuthRepository>()
    val playerController = koinInject<PlayerController>()

    LaunchedEffect(Unit) { playerController.connect() }

    val cookies by authRepository.cookies.collectAsStateWithLifecycle(initialValue = null)

    if (cookies.isNullOrBlank()) {
        LoginScreen()
    } else {
        CloudScreen()
    }
}
