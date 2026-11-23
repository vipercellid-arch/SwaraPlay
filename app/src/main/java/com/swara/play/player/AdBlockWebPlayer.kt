package com.swara.play.player

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.swara.play.data.network.HttpClientProvider
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdBlockWebPlayer(
    embedUrl: String,
    referer: String,
    adBlockEnabled: Boolean,
    onBlockedCount: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allowedHost = remember(embedUrl) {
        runCatching { Uri.parse(embedUrl).host.orEmpty() }.getOrDefault("")
    }

    val webView = remember(context) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = HttpClientProvider.USER_AGENT
                useWideViewPort = true
                loadWithOverviewMode = true
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    // Block popup ad windows
                    return false
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        update = { wv ->
            var blocked = 0
            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val reqUrl = request?.url?.toString().orEmpty()
                    if (adBlockEnabled && AdBlockEngine.shouldBlock(reqUrl)) {
                        blocked += 1
                        onBlockedCount(blocked)
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val targetUrl = request?.url?.toString().orEmpty()
                    if (adBlockEnabled && AdBlockEngine.shouldBlock(targetUrl)) {
                        blocked += 1
                        onBlockedCount(blocked)
                        return true
                    }
                    val targetHost = request?.url?.host.orEmpty()
                    if (adBlockEnabled && allowedHost.isNotBlank() &&
                        targetHost.isNotBlank() && !targetHost.endsWith(allowedHost) &&
                        !targetUrl.contains("embed", ignoreCase = true) &&
                        !targetUrl.contains("player", ignoreCase = true)
                    ) {
                        blocked += 1
                        onBlockedCount(blocked)
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (adBlockEnabled) {
                        view?.evaluateJavascript(AdBlockEngine.CLEANUP_JS, null)
                    }
                }
            }
            if (wv.tag != embedUrl) {
                wv.tag = embedUrl
                val headers = if (referer.isNotBlank()) mapOf("Referer" to referer) else emptyMap()
                wv.loadUrl(embedUrl, headers)
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}
