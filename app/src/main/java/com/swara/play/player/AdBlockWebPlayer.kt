package com.swara.play.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.swara.play.data.network.HttpClientProvider
import java.io.ByteArrayInputStream

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun setImmersiveFullscreen(activity: Activity?, fullscreen: Boolean) {
    if (activity == null) return
    val window = activity.window ?: return
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (fullscreen) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdBlockWebPlayer(
    embedUrl: String,
    referer: String,
    reloadToken: Int,
    adBlockEnabled: Boolean,
    onBlockedCount: (Int) -> Unit,
    onServerErrorDetected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val allowedHost = remember(embedUrl) {
        runCatching { Uri.parse(embedUrl).host.orEmpty() }.getOrDefault("")
    }

    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var fallbackTriggeredForKey by remember { mutableStateOf("") }

    fun exitFullscreen() {
        val decor = activity?.window?.decorView as? FrameLayout
        val cv = customView
        if (decor != null && cv != null) decor.removeView(cv)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        setImmersiveFullscreen(activity, false)
    }

    BackHandler(enabled = customView != null) {
        exitFullscreen()
    }

    val webView = remember(context) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            keepScreenOn = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
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
                allowContentAccess = true
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean = false

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null) return
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    val decor = activity?.window?.decorView as? FrameLayout
                    if (decor != null) {
                        customView = view
                        customViewCallback = callback
                        decor.addView(
                            view,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        setImmersiveFullscreen(activity, true)
                    }
                }

                override fun onHideCustomView() {
                    exitFullscreen()
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            exitFullscreen()
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        update = { wv ->
            val loadKey = "$embedUrl#$reloadToken"
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
                        !targetUrl.contains("player", ignoreCase = true) &&
                        !targetUrl.contains("vid", ignoreCase = true) &&
                        !targetUrl.contains("cloud", ignoreCase = true)
                    ) {
                        blocked += 1
                        onBlockedCount(blocked)
                        return true
                    }
                    return false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true && fallbackTriggeredForKey != loadKey) {
                        fallbackTriggeredForKey = loadKey
                        onServerErrorDetected()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    val code = errorResponse?.statusCode ?: 200
                    if (request?.isForMainFrame == true && code >= 500 && fallbackTriggeredForKey != loadKey) {
                        fallbackTriggeredForKey = loadKey
                        onServerErrorDetected()
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(AdBlockEngine.CLEANUP_AND_DETECT_ERROR_JS) { res ->
                        if (res != null && res.contains("CF_ERROR") && fallbackTriggeredForKey != loadKey) {
                            fallbackTriggeredForKey = loadKey
                            onServerErrorDetected()
                        }
                    }
                }
            }
            if (wv.tag != loadKey) {
                wv.tag = loadKey
                val headers = if (referer.isNotBlank()) mapOf("Referer" to referer) else emptyMap()
                wv.loadUrl(embedUrl, headers)
            }
        },
        modifier = modifier.fillMaxSize().background(Color.Black)
    )
}
