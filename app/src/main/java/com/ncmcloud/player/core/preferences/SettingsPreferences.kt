package com.ncmcloud.player.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ncmcloud.player.domain.PlayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsPreferences(private val dataStore: DataStore<Preferences>) {
    companion object {
        private val KEY_USE_REAL_IP = booleanPreferencesKey("use_real_ip")
        private val KEY_REAL_IP_VALUE = stringPreferencesKey("real_ip_value")
        private val KEY_QUALITY = stringPreferencesKey("play_quality")
        private val KEY_PLAY_MODE = stringPreferencesKey("play_mode")
        private val KEY_LYRIC_CACHE = booleanPreferencesKey("lyric_cache_enabled")
        private val KEY_LYRIC_TRANSLATION = booleanPreferencesKey("lyric_translation_enabled")
        private val KEY_LYRIC_FONT_SIZE = intPreferencesKey("lyric_font_size")

        const val DEFAULT_LYRIC_FONT_SIZE = 20
    }

    val useRealIp: Flow<Boolean> = dataStore.data.map { it[KEY_USE_REAL_IP] ?: true }
    val realIpValue: Flow<String> = dataStore.data.map { it[KEY_REAL_IP_VALUE] ?: "" }
    val playQuality: Flow<String> = dataStore.data.map { it[KEY_QUALITY] ?: "exhigh" }
    val playMode: Flow<String> = dataStore.data.map { it[KEY_PLAY_MODE] ?: PlayMode.ORDER.name }

    val lyricCacheEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_LYRIC_CACHE] ?: true }
    val lyricTranslationEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_LYRIC_TRANSLATION] ?: true }
    // 歌词当前行字号（sp），其余行按固定差值缩小
    val lyricFontSize: Flow<Int> = dataStore.data.map { it[KEY_LYRIC_FONT_SIZE] ?: DEFAULT_LYRIC_FONT_SIZE }

    suspend fun setUseRealIp(value: Boolean) {
        dataStore.edit { it[KEY_USE_REAL_IP] = value }
    }

    suspend fun setRealIpValue(value: String) {
        dataStore.edit { it[KEY_REAL_IP_VALUE] = value }
    }

    suspend fun setPlayQuality(value: String) {
        dataStore.edit { it[KEY_QUALITY] = value }
    }

    suspend fun setPlayMode(value: String) {
        dataStore.edit { it[KEY_PLAY_MODE] = value }
    }

    suspend fun setLyricCacheEnabled(value: Boolean) {
        dataStore.edit { it[KEY_LYRIC_CACHE] = value }
    }

    suspend fun setLyricTranslationEnabled(value: Boolean) {
        dataStore.edit { it[KEY_LYRIC_TRANSLATION] = value }
    }

    suspend fun setLyricFontSize(value: Int) {
        dataStore.edit { it[KEY_LYRIC_FONT_SIZE] = value }
    }
}
