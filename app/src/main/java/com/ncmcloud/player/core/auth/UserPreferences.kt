package com.ncmcloud.player.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ncmcloud.player.core.log.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "UserPreferences"

@Serializable
data class UserProfile(
    val uid: Long,
    val nickname: String,
    val avatarUrl: String = ""
)

class UserPreferences(private val dataStore: DataStore<Preferences>) {
    companion object {
        private val KEY_USER_PROFILE = stringPreferencesKey("user_profile_json")
        private val KEY_COOKIES = stringPreferencesKey("user_cookies")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val userProfile: Flow<UserProfile?> = dataStore.data.map { prefs ->
        prefs[KEY_USER_PROFILE]?.let { jsonStr ->
            runCatching { json.decodeFromString<UserProfile>(jsonStr) }
                .onFailure { AppLogger.w(TAG, "用户信息反序列化失败", it) }
                .getOrNull()
        }
    }

    val cookies: Flow<String?> = dataStore.data.map { prefs -> prefs[KEY_COOKIES] }

    suspend fun saveUserProfile(profile: UserProfile) {
        dataStore.edit { it[KEY_USER_PROFILE] = json.encodeToString(profile) }
    }

    suspend fun saveCookies(cookies: String) {
        dataStore.edit { it[KEY_COOKIES] = cookies }
    }

    suspend fun clearUserProfile() {
        dataStore.edit { it.remove(KEY_USER_PROFILE); it.remove(KEY_COOKIES) }
    }
}
