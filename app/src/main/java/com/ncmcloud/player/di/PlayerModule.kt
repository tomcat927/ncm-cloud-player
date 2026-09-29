package com.ncmcloud.player.di

import com.ncmcloud.player.playback.PlayerController
import com.ncmcloud.player.update.UpdateService
import org.koin.dsl.module

val playerModule = module {
    single { PlayerController(get(), get(), get()) }
    single { UpdateService(get()) }
}
