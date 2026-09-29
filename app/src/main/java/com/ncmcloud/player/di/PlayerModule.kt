package com.ncmcloud.player.di

import com.ncmcloud.player.playback.PlayerController
import org.koin.dsl.module

val playerModule = module {
    single { PlayerController(get(), get(), get()) }
}
