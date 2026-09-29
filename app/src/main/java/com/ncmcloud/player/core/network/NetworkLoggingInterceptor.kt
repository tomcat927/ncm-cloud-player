package com.ncmcloud.player.core.network

import com.ncmcloud.player.core.log.AppLogger
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

private const val TAG = "Network"
private const val MAX_BODY = 4096

// 调试阶段详细网络日志：请求方法/URL/请求体/响应码/响应体，Cookie 等敏感头脱敏。
class NetworkLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val method = request.method

        val reqBody = readRequestBody(request)
        AppLogger.i(TAG, ">> $method $url")
        if (reqBody.isNotEmpty()) {
            AppLogger.i(TAG, ">> body: ${reqBody.take(MAX_BODY)}")
        }

        val response = chain.proceed(request)
        val respBody = runCatching { response.peekBody(256 * 1024).string() }.getOrNull().orEmpty()
        AppLogger.i(TAG, "<< $response.code $url")
        if (respBody.isNotEmpty()) {
            AppLogger.i(TAG, "<< body: ${respBody.take(MAX_BODY)}")
        }
        if (!response.isSuccessful) {
            AppLogger.w(TAG, "<< non-2xx: $method $url code=${response.code} body=${respBody.take(MAX_BODY)}")
        }
        return response
    }

    private fun readRequestBody(request: Request): String {
        val body = request.body ?: return ""
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: Exception) {
            "（无法读取请求体: ${e.message}）"
        }
    }
}
