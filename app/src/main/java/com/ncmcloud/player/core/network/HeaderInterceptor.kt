package com.ncmcloud.player.core.network

import com.ncmcloud.player.core.auth.UserPreferences
import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.core.preferences.SettingsPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

private const val TAG = "HeaderInterceptor"

// 网易云反风控拦截器。UA、Referer、海外 IP 伪装及用户 Cookie。
class HeaderInterceptor(
    private val userPreferences: UserPreferences,
    private val settingsPreferences: SettingsPreferences,
    private val realIpProvider: RealIpProvider,
    private val cookieJar: MemoryCookieJar,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url
        val urlString = url.toString()
        val newRequestBuilder = originalRequest.newBuilder()

        val storedCookies = try {
            runBlocking { userPreferences.cookies.first() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取存储的 Cookie 失败，本次请求按未登录处理", e)
            null
        }
        val useRealIp = try {
            runBlocking { settingsPreferences.useRealIp.first() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取真实IP开关失败，本次请求跳过IP伪装", e)
            false
        }
        val realIpValue = try {
            runBlocking { settingsPreferences.realIpValue.first() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取真实IP值失败", e)
            ""
        }

        val jarCookies = cookieJar.loadForRequest(url).map { "${it.name}=${it.value}" }

        val isQrLogin = urlString.contains("/login/qrcode/")
        val ipAddress = realIpProvider.resolveIp(useRealIp, realIpValue)
        if (!isQrLogin && ipAddress != null && url.host.contains(NeteaseEndpoints.DOMAIN_SUFFIX)) {
            newRequestBuilder.header("X-Real-IP", ipAddress)
            newRequestBuilder.header("X-Forwarded-For", ipAddress)
        }

        if (urlString.contains("/eapi/")) {
            val newUrl = url.newBuilder()
                .host(NeteaseEndpoints.EAPI_HOST)
                .build()
            newRequestBuilder.url(newUrl)

            val androidUA = "NeteaseMusic/9.0.90 (Linux; U; Android ${DeviceInfo.osRelease}; zh_CN; ${DeviceInfo.model})"
            newRequestBuilder.header("User-Agent", androidUA)
            newRequestBuilder.removeHeader("Referer")

            val requestCookies = originalRequest.headers("Cookie").toMutableList()
            if (storedCookies != null) requestCookies.add(storedCookies)
            requestCookies.addAll(jarCookies)

            val cookiesStr = requestCookies.joinToString("; ")
            val filteredCookies = cookiesStr.split("; ").filterNot { it.trim().startsWith("os=") }.joinToString("; ")
            val osCookie = if (urlString.contains("/eapi/feedback/weblog")) {
                "os=osx"
            } else {
                "os=android; appver=9.0.90; osver=${DeviceInfo.osRelease}"
            }
            newRequestBuilder.header("Cookie", if (filteredCookies.isEmpty()) osCookie else "$filteredCookies; $osCookie")
        } else if (urlString.contains("/xeapi/")) {
            val newUrl = url.newBuilder()
                .host(NeteaseEndpoints.XEAPI_HOST)
                .build()
            newRequestBuilder.url(newUrl)

            val deviceId = NeteaseDeviceId.current()
            val osVer = DeviceInfo.osRelease
            val appVer = "9.5.61"
            val buildVer = System.currentTimeMillis().toString().substring(0, 10)

            newRequestBuilder.header(
                "User-Agent",
                "NeteaseMusic/9.5.61.260802021928(9005061);Dalvik/2.1.0 (Linux; U; Android $osVer; ${DeviceInfo.model})"
            )
            newRequestBuilder.header("X-Client-Enc-State", "ENCRYPTED")
            newRequestBuilder.header("x-aeapi", "true")
            newRequestBuilder.header("content-type", "application/x-www-form-urlencoded;charset=utf-8")
            newRequestBuilder.header("x-deviceid", deviceId)
            newRequestBuilder.header("x-os", "android")
            newRequestBuilder.header("x-osver", osVer)
            newRequestBuilder.header("x-appver", appVer)
            newRequestBuilder.header("x-sdeviceid", deviceId)
            newRequestBuilder.header("x-buildver", buildVer)
            newRequestBuilder.removeHeader("Referer")

            val musicU = storedCookies?.let { Regex("MUSIC_U=([^;]+)").find(it)?.groupValues?.get(1) }
            if (!musicU.isNullOrEmpty()) {
                newRequestBuilder.header("x-music-u", musicU)
            }

            val cookieParts = mutableListOf(
                "os=android",
                "osver=$osVer",
                "appver=$appVer",
                "deviceId=$deviceId",
                "sDeviceId=$deviceId",
                "buildver=$buildVer",
            )
            if (storedCookies != null) cookieParts.add(storedCookies)
            cookieParts.addAll(jarCookies)
            newRequestBuilder.header("Cookie", cookieParts.joinToString("; "))
        } else {
            newRequestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            newRequestBuilder.header("Referer", NeteaseEndpoints.WEB_BASE_URL)

            val requestCookies = originalRequest.headers("Cookie").toMutableList()
            if (!isQrLogin && storedCookies != null) requestCookies.add(storedCookies)
            requestCookies.addAll(jarCookies)

            val cookiesStr = requestCookies.joinToString("; ")
            if (!cookiesStr.contains("os=")) {
                val osCookie = "os=pc; osver=Microsoft-Windows-10-Professional-build-10512-64bit; appver=3.0.1.201552"
                newRequestBuilder.header("Cookie", if (cookiesStr.isEmpty()) osCookie else "$cookiesStr; $osCookie")
            } else {
                newRequestBuilder.header("Cookie", cookiesStr)
            }
        }

        return chain.proceed(newRequestBuilder.build())
    }
}
