package com.ncmcloud.player.core.network

import com.ncmcloud.player.core.log.AppLogger
import okhttp3.Interceptor
import okhttp3.Response

private const val TAG = "Network"

// 诊断日志拦截器：记录请求 URL、响应码和非成功响应体，便于排查 400 等接口失败。
class NetworkLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.isSuccessful) {
            AppLogger.i(TAG, "${request.method} ${request.url} -> ${response.code}")
        } else {
            val body = runCatching { response.peekBody(64 * 1024).string() }.getOrNull()
            AppLogger.w(TAG, "${request.method} ${request.url} -> ${response.code} body=${body?.take(2000).orEmpty()}")
        }
        return response
    }
}
