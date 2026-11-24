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
import androidx.compose.runtime.LaunchedEffect
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
import com.swara.play.model.SubtitleStylePrefs
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun safeSetFullscreen(activity: Activity?, fullscreen: Boolean) {
    if (activity == null || activity.isFinishing || activity.isDestroyed) return
    runCatching {
        val window = activity.window ?: return
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StreamWebPlayer(
    embedUrl: String,
    referer: String,
    reloadToken: Int,
    preferredLangIso: String,
    subtitleEnabled: Boolean,
    subtitleStyle: SubtitleStylePrefs,
    synchronizedVtt: String,
    playbackSpeed: Float,
    videoFillMode: Boolean,
    controlCommand: Pair<Int, String>,
    onMediaUrlSniffed: (String) -> Unit,
    onSilentFallbackNeeded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val allowedHost = remember(embedUrl) { runCatching { Uri.parse(embedUrl).host.orEmpty() }.getOrDefault("") }

    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var fallbackTriggeredForKey by remember { mutableStateOf("") }
    var pageLoadedKey by remember { mutableStateOf("") }

    fun exitFullscreen() {
        runCatching {
            val cv = customView
            if (cv != null) (cv.parent as? ViewGroup)?.removeView(cv)
            customView = null
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
            safeSetFullscreen(activity, false)
        }
    }

    BackHandler(enabled = customView != null) { exitFullscreen() }

    val webView = remember(context) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.BLACK)
            keepScreenOn = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
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
                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null) return
                    runCatching {
                        if (customView != null) {
                            callback?.onCustomViewHidden()
                            return
                        }
                        val decor = activity?.window?.decorView as? FrameLayout ?: return
                        (view.parent as? ViewGroup)?.removeView(view)
                        customView = view
                        customViewCallback = callback
                        view.setBackgroundColor(android.graphics.Color.BLACK)
                        decor.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        safeSetFullscreen(activity, true)
                    }
                }

                override fun onHideCustomView() {
                    exitFullscreen()
                }
            }
        }
    }

    // Re-sync subtitle whenever toggle, offset, or fetched VTT changes
    LaunchedEffect(subtitleEnabled, subtitleStyle, synchronizedVtt, preferredLangIso, pageLoadedKey) {
        if (pageLoadedKey.isNotBlank()) {
            val js = StreamFilterEngine.buildEnhanceAndDetectJs(preferredLangIso, subtitleEnabled, subtitleStyle, synchronizedVtt)
            webView.evaluateJavascript(js, null)
        }
    }

    // Apply playback speed & fit mode live
    LaunchedEffect(playbackSpeed, pageLoadedKey) {
        if (pageLoadedKey.isNotBlank()) {
            val js = StreamFilterEngine.buildPlayerControlJs("speed", String.format(java.util.Locale.US, "%.2f", playbackSpeed))
            webView.evaluateJavascript(js, null)
        }
    }
    LaunchedEffect(videoFillMode, pageLoadedKey) {
        if (pageLoadedKey.isNotBlank()) {
            val js = StreamFilterEngine.buildPlayerControlJs("fit", if (videoFillMode) "cover" else "contain")
            webView.evaluateJavascript(js, null)
        }
    }
    LaunchedEffect(controlCommand) {
        val (seq, cmd) = controlCommand
        if (seq > 0 && cmd.isNotBlank()) {
            val parts = cmd.split(":")
            val js = StreamFilterEngine.buildPlayerControlJs(parts[0], parts.getOrElse(1) { "" })
            if (js.isNotBlank()) webView.evaluateJavascript(js, null)
        }
    }

    // Delayed health check for SPA embed players (catches async "video cannot be processed" errors in anime)
    LaunchedEffect(pageLoadedKey) {
        if (pageLoadedKey.isBlank()) return@LaunchedEffect
        delay(7500)
        val checkKey = pageLoadedKey
        val js = StreamFilterEngine.buildEnhanceAndDetectJs(preferredLangIso, subtitleEnabled, subtitleStyle, synchronizedVtt, checkEmptyVideo = true)
        webView.evaluateJavascript(js) { res ->
            if (res != null && res.contains("STREAM_ERROR") && fallbackTriggeredForKey != checkKey) {
                fallbackTriggeredForKey = checkKey
                onSilentFallbackNeeded()
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            exitFullscreen()
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }

    AndroidView(
        factory = { webView },
        update = { wv ->
            val loadKey = "$embedUrl#$reloadToken"
            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val reqUrl = request?.url?.toString().orEmpty()
                    if (StreamFilterEngine.shouldBlock(reqUrl)) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    if (StreamFilterEngine.isDownloadableMediaUrl(reqUrl)) {
                        onMediaUrlSniffed(reqUrl)
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val targetUrl = request?.url?.toString().orEmpty()
                    if (StreamFilterEngine.shouldBlock(targetUrl)) return true
                    val targetHost = request?.url?.host.orEmpty()
                    if (allowedHost.isNotBlank() && targetHost.isNotBlank() && !targetHost.endsWith(allowedHost) &&
                        !targetUrl.contains("embed", ignoreCase = true) &&
                        !targetUrl.contains("player", ignoreCase = true) &&
                        !targetUrl.contains("vid", ignoreCase = true) &&
                        !targetUrl.contains("cloud", ignoreCase = true) &&
                        !targetUrl.contains("videasy", ignoreCase = true) &&
                        !targetUrl.contains("anime", ignoreCase = true)
                    ) {
                        return true
                    }
                    return false
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true && fallbackTriggeredForKey != loadKey) {
                        fallbackTriggeredForKey = loadKey
                        onSilentFallbackNeeded()
                    }
                }

                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    val code = errorResponse?.statusCode ?: 200
                    if (request?.isForMainFrame == true && code >= 404 && fallbackTriggeredForKey != loadKey) {
                        fallbackTriggeredForKey = loadKey
                        onSilentFallbackNeeded()
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    pageLoadedKey = loadKey
                    val js = StreamFilterEngine.buildEnhanceAndDetectJs(preferredLangIso, subtitleEnabled, subtitleStyle, synchronizedVtt)
                    view?.evaluateJavascript(js) { res ->
                        if (res != null && res.contains("STREAM_ERROR") && fallbackTriggeredForKey != loadKey) {
                            fallbackTriggeredForKey = loadKey
                            onSilentFallbackNeeded()
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
