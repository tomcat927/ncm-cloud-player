package com.ncmcloud.player.ui.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ncmcloud.player.core.auth.UserPreferences
import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.core.network.MemoryCookieJar
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.koin.core.context.GlobalContext

private const val TAG = "RiskVerify"

// 扫码风控验证页（网易 phoneReuse 等）。官方流程是在官方 App 的内置 WebView 里打开 redirectUrl，
// 页面依赖该上下文里的登录 Cookie 与风控 Cookie；系统浏览器裸开会直接判「风险设备」。
// 这里把 App 的 Cookie（登录态 + server/login 响应下发的临时风控 Cookie）注入 WebView 再加载验证页。
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RiskVerifyScreen(
    verifyUrl: String,
    onClose: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var cookiesReady by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val webView = webViewRef
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            onClose()
        }
    }

    // Cookie 注入必须先于 loadUrl，故加载前先完成准备
    LaunchedEffect(verifyUrl) {
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            val userPreferences = GlobalContext.get().get<UserPreferences>()
            val stored = userPreferences.cookies.first() ?: ""
            val jar = GlobalContext.get().get<MemoryCookieJar>()
            val jarCookies = jar.loadForRequest("https://music.163.com/".toHttpUrl())
                .joinToString("; ") { "${it.name}=${it.value}" }
            val pairs = (stored.split("; ") + jarCookies.split("; "))
                .filter { it.contains("=") && it.isNotBlank() }
                .distinct()
            pairs.forEach { pair ->
                cookieManager.setCookie("https://music.163.com/", "$pair; Domain=.music.163.com; Path=/")
            }
            cookieManager.flush()
            AppLogger.i(TAG, "已注入 ${pairs.size} 条 Cookie 到验证 WebView")
        }.onFailure { e ->
            AppLogger.e(TAG, "Cookie 注入失败，验证页将无登录态", e)
        }
        cookiesReady = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安全验证") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (cookiesReady) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewRef = this
                            setBackgroundColor(android.graphics.Color.parseColor("#F5F5F7"))
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    consoleMessage?.let {
                                        AppLogger.i(TAG, "[网页控制台] ${it.message()} @${it.sourceId()?.substringAfterLast('/')}:${it.lineNumber()}")
                                    }
                                    return super.onConsoleMessage(consoleMessage)
                                }
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): WebResourceResponse? {
                                    request?.let { req ->
                                        if (req.method == "POST" && req.url.toString().contains("music.163.com")) {
                                            AppLogger.i(TAG, "[网页请求] POST ${req.url.encodedPath}")
                                        }
                                    }
                                    return null
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    AppLogger.w(TAG, "[网页] 加载错误: ${request?.url} ${error?.description}")
                                    super.onReceivedError(view, request, error)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    AppLogger.i(TAG, "[网页] 加载完成: $url")
                                    isLoading = false
                                }
                            }
                            loadUrl(verifyUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (cookiesReady && isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
