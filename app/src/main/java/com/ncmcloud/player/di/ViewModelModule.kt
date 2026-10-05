package com.ncmcloud.player.di

import com.ncmcloud.player.ui.cloud.CloudViewModel
import com.ncmcloud.player.ui.login.LoginViewModel
import com.ncmcloud.player.ui.playlist.PlaylistViewModel
import com.ncmcloud.player.ui.update.UpdateViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::CloudViewModel)
    viewModelOf(::UpdateViewModel)
    viewModelOf(::PlaylistViewModel)
}
