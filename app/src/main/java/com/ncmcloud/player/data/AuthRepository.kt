package com.ncmcloud.player.data

import com.ncmcloud.player.core.api.NeteaseApiService
import com.ncmcloud.player.core.auth.UserPreferences
import com.ncmcloud.player.core.auth.UserProfile

class AuthRepository(
    private val apiService: NeteaseApiService,
    private val userPreferences: UserPreferences,
) {
    val cookies get() = userPreferences.cookies
    val userProfile get() = userPreferences.userProfile

    suspend fun loginWithCookie(rawCookie: String) {
        val cookie = sanitizeCookie(rawCookie)
        if (!cookie.contains("MUSIC_U")) throw IllegalArgumentException("Cookie 必须包含 MUSIC_U")
        userPreferences.saveCookies(cookie)
        val account = apiService.getAccountInfo()
        if (account.code != 200) {
            throw IllegalStateException("账号信息接口返回 code=${account.code}")
        }
        val profile = account.profile
        if (profile != null) {
            userPreferences.saveUserProfile(
                UserProfile(
                    uid = profile.userId,
                    nickname = profile.nickname,
                    avatarUrl = profile.avatarUrl,
                )
            )
        }
    }

    suspend fun logout() {
        runCatching { apiService.logoutApi() }
        userPreferences.clearUserProfile()
    }

    private fun sanitizeCookie(raw: String): String {
        return raw.trim().trim('"').replace("\n", "").replace("\r", "")
    }
}
