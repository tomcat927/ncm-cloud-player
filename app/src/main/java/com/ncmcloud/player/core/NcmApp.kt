package com.ncmcloud.player

import android.app.Application
import com.ncmcloud.player.core.AppEnvironment
import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.di.networkModule
import com.ncmcloud.player.di.playerModule
import com.ncmcloud.player.di.preferenceModule
import com.ncmcloud.player.di.repositoryModule
import com.ncmcloud.player.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class NcmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppEnvironment.isDebug = BuildConfig.DEBUG
        AppLogger.init(this)
        startKoin {
            androidContext(this@NcmApp)
            modules(
                preferenceModule,
                networkModule,
                repositoryModule,
                playerModule,
                viewModelModule,
            )
        }
    }
}
