package com.ncmcloud.player.ui.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
import com.ncmcloud.player.core.network.NeteaseEndpoints
import com.ncmcloud.player.core.network.RealIpProvider
import com.ncmcloud.player.core.preferences.SettingsPreferences
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewLoginScreen(
    onClose: () -> Unit,
    onLoginSuccess: (String) -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var extraHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // 系统返回键：网页能后退就后退，到首页才关闭登录页
    BackHandler {
        val webView = webViewRef
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            onClose()
        }
    }

    val baseUA = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"

    LaunchedEffect(Unit) {
        val settings = GlobalContext.get().get<SettingsPreferences>()
        val realIpProvider = GlobalContext.get().get<RealIpProvider>()
        val useRealIp = settings.useRealIp.first()
        val realIpValue = settings.realIpValue.first()
        val ip = realIpProvider.resolveIp(useRealIp, realIpValue)
        extraHeaders = if (ip != null) mapOf("X-Real-IP" to ip, "X-Forwarded-For" to ip) else emptyMap()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("网页登录") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        val webView = this
                        webViewRef = this
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(webView, true)
                            removeAllCookies(null)
                            flush()
                        }
                        setBackgroundColor(android.graphics.Color.parseColor("#F5F5F7"))
                        webChromeClient = WebChromeClient()
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = baseUA
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                checkCookies(onLoginSuccess)
                                isLoading = false
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                checkCookies(onLoginSuccess)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                        }
                        loadUrl(NeteaseEndpoints.LOGIN_URL, extraHeaders)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

private fun checkCookies(onLoginSuccess: (String) -> Unit) {
    val cookies = CookieManager.getInstance().getCookie(NeteaseEndpoints.WEB_BASE_URL)
    if (cookies != null && cookies.contains("MUSIC_U=")) {
        onLoginSuccess(cookies)
    }
}
