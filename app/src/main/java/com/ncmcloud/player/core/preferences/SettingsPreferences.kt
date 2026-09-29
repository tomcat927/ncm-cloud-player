package com.ncmcloud.player.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsPreferences(private val dataStore: DataStore<Preferences>) {
    companion object {
        private val KEY_USE_REAL_IP = booleanPreferencesKey("use_real_ip")
        private val KEY_REAL_IP_VALUE = stringPreferencesKey("real_ip_value")
        private val KEY_QUALITY = stringPreferencesKey("play_quality")
    }

    val useRealIp: Flow<Boolean> = dataStore.data.map { it[KEY_USE_REAL_IP] ?: true }
    val realIpValue: Flow<String> = dataStore.data.map { it[KEY_REAL_IP_VALUE] ?: "" }
    val playQuality: Flow<String> = dataStore.data.map { it[KEY_QUALITY] ?: "exhigh" }

    suspend fun setUseRealIp(value: Boolean) {
        dataStore.edit { it[KEY_USE_REAL_IP] = value }
    }

    suspend fun setRealIpValue(value: String) {
        dataStore.edit { it[KEY_REAL_IP_VALUE] = value }
    }

    suspend fun setPlayQuality(value: String) {
        dataStore.edit { it[KEY_QUALITY] = value }
    }
}
