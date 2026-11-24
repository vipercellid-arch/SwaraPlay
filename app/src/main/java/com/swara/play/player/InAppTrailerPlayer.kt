package com.swara.play.player

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.swara.play.data.network.HttpClientProvider
import com.swara.play.data.provider.WyzieSubtitleResolver
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
fun TrailerPopupDialog(
    title: String,
    year: String,
    initialYoutubeId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var resolvedYtId by remember(initialYoutubeId, title) { mutableStateOf(initialYoutubeId) }
    var directStreamUrl by remember(initialYoutubeId, title) { mutableStateOf("") }
    var exoFailed by remember(initialYoutubeId, title) { mutableStateOf(false) }
    var isResolving by remember(initialYoutubeId, title) { mutableStateOf(true) }

    LaunchedEffect(initialYoutubeId, title) {
        isResolving = true
        val ytId = if (resolvedYtId.isNotBlank()) resolvedYtId
        else WyzieSubtitleResolver.resolveYoutubeTrailerId(title, year)
        resolvedYtId = ytId
        if (ytId.isNotBlank()) {
            directStreamUrl = WyzieSubtitleResolver.resolveDirectTrailerStreamUrl(ytId)
        }
        isResolving = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 680.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(SwaraCard)
                .border(0.75.dp, SwaraSubtleBorder, RoundedCornerShape(22.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Trailer Resmi", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (resolvedYtId.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF202026))
                            .clickable {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$resolvedYtId")))
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("YouTube", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF222228))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
            ) {
                if (isResolving) {
                    CircularProgressIndicator(color = SwaraAccent, modifier = Modifier.size(32.dp))
                } else if (directStreamUrl.isNotBlank() && !exoFailed) {
                    NativeExoTrailerPlayer(streamUrl = directStreamUrl, onError = { exoFailed = true })
                } else if (resolvedYtId.isNotBlank()) {
                    MobileWatchYoutubeFallback(youtubeVideoId = resolvedYtId)
                } else {
                    Text("Trailer tidak tersedia untuk judul ini.", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun NativeExoTrailerPlayer(streamUrl: String, onError: () -> Unit) {
    val context = LocalContext.current
    val player = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    onError()
                }
            })
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { runCatching { player.release() } }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = Modifier.fillMaxSize().background(Color.Black)
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MobileWatchYoutubeFallback(youtubeVideoId: String) {
    val context = LocalContext.current
    val watchUrl = "https://m.youtube.com/watch?v=$youtubeVideoId"

    val webView = remember(context) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = HttpClientProvider.USER_AGENT
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Focus video cleanly and auto-play without /embed/ Error 152-4
                    view?.evaluateJavascript(
                        "(function(){try{var s=document.createElement('style');s.innerHTML='header,ytm-pivot-bar-renderer,ytm-single-column-watch-next-results-renderer{display:none!important;}body{background:#000!important;overflow:hidden!important;}';document.head.appendChild(s);var v=document.querySelector('video');if(v){v.play().catch(function(){});}}catch(e){}})();",
                        null
                    )
                }
            }
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
            if (wv.tag != watchUrl) {
                wv.tag = watchUrl
                wv.loadUrl(watchUrl)
            }
        },
        modifier = Modifier.fillMaxSize().background(Color.Black)
    )
}
