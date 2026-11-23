package com.swara.play.player

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
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
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppTrailerPlayer(
    youtubeVideoId: String,
    fallbackSearchTitle: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val targetUrl = remember(youtubeVideoId, fallbackSearchTitle) {
        if (youtubeVideoId.isNotBlank()) {
            "https://www.youtube-nocookie.com/embed/$youtubeVideoId?autoplay=1&rel=0&modestbranding=1&playsinline=1"
        } else {
            val q = URLEncoder.encode("$fallbackSearchTitle official trailer", "UTF-8")
            "https://m.youtube.com/results?search_query=$q"
        }
    }

    val webView = remember(context) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
        }
    }

    DisposableEffect(webView) {
        onDispose {
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
            if (wv.tag != targetUrl) {
                wv.tag = targetUrl
                wv.loadUrl(targetUrl)
            }
        },
        modifier = modifier.fillMaxSize().background(Color.Black)
    )
}
