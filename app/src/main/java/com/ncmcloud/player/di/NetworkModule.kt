package com.ncmcloud.player.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.ncmcloud.player.core.api.NeteaseApiService
import com.ncmcloud.player.core.network.CryptoInterceptor
import com.ncmcloud.player.core.network.EmptyBodyInterceptor
import com.ncmcloud.player.core.network.HeaderInterceptor
import com.ncmcloud.player.core.network.NeteaseEndpoints
import com.ncmcloud.player.core.network.NetworkLoggingInterceptor
import com.ncmcloud.player.core.log.RemoteLogService
import com.ncmcloud.player.core.network.RealIpProvider
import com.ncmcloud.player.core.network.crypto.XeapiKeyStore
import com.ncmcloud.player.core.network.crypto.XeapiKeyStoreImpl
import com.ncmcloud.player.core.preferences.SettingsPreferences
import com.ncmcloud.player.core.auth.UserPreferences
import com.ncmcloud.player.feature.cloud.data.CloudApi
import com.ncmcloud.player.core.player.data.PlaybackApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

val networkModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
            isLenient = true
            explicitNulls = false
        }
    }

    single { RealIpProvider() }
    single { EmptyBodyInterceptor() }
    single { NetworkLoggingInterceptor() }

    single<XeapiKeyStore> {
        val appDataStore: DataStore<Preferences> = get(qualifier = named("app"))
        XeapiKeyStoreImpl(appDataStore)
    }

    single { CryptoInterceptor(get()) }
    single { HeaderInterceptor(get(), get(), get()) }

    single {
        OkHttpClient.Builder()
            .addInterceptor(get<EmptyBodyInterceptor>())
            .addInterceptor(get<HeaderInterceptor>())
            .addInterceptor(get<CryptoInterceptor>())
            .addInterceptor(get<NetworkLoggingInterceptor>())
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Cookie")
                    redactHeader("Set-Cookie")
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    single {
        val json: Json = get()
        Retrofit.Builder()
            .baseUrl(NeteaseEndpoints.WEB_BASE_URL)
            .client(get())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single<NeteaseApiService> { get<Retrofit>().create(NeteaseApiService::class.java) }
    single<CloudApi> { get<Retrofit>().create(CloudApi::class.java) }
    single<PlaybackApi> { get<Retrofit>().create(PlaybackApi::class.java) }
}

val preferenceModule = module {
    single<DataStore<Preferences>>(qualifier = named("app")) {
        com.ncmcloud.player.core.DataStores.app(get())
    }
    single { UserPreferences(get(qualifier = named("app"))) }
    single { SettingsPreferences(get(qualifier = named("app"))) }
    single { RemoteLogService(get(), get(qualifier = named("app"))) }
}

