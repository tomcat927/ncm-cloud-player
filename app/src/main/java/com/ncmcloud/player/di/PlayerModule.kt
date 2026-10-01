package com.ncmcloud.player.di

import com.ncmcloud.player.data.LyricsCache
import com.ncmcloud.player.playback.PlayerController
import com.ncmcloud.player.update.UpdateService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val playerModule = module {
    single { LyricsCache(androidContext()) }
    single { PlayerController(get(), get(), get(), get()) }
    single { UpdateService(get()) }
}
