package com.ncmcloud.player.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "ncm_cloud_player")

object DataStores {
    fun app(context: Context): DataStore<Preferences> = context.appDataStore
}
