package com.ncmcloud.player.di

import com.ncmcloud.player.data.AuthRepository
import com.ncmcloud.player.data.CloudRepository
import com.ncmcloud.player.data.PlaybackRepository
import com.ncmcloud.player.data.PlaylistRepository
import org.koin.dsl.module

val repositoryModule = module {
    single { AuthRepository(get(), get()) }
    single { CloudRepository(get()) }
    single { PlaybackRepository(get()) }
    single { PlaylistRepository(get()) }
}
