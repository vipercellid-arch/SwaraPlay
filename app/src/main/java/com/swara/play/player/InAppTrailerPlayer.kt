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
import com.swara.play.data.network.HttpClientProvider
import com.swara.play.data.provider.WyzieSubtitleResolver
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerPopupDialog(
    title: String,
    year: String,
    initialYoutubeId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var resolvedYtId by remember(initialYoutubeId, title) { mutableStateOf(initialYoutubeId) }
    var isResolving by remember(initialYoutubeId, title) { mutableStateOf(initialYoutubeId.isBlank()) }

    LaunchedEffect(initialYoutubeId, title) {
        if (resolvedYtId.isBlank()) {
            isResolving = true
            resolvedYtId = WyzieSubtitleResolver.resolveYoutubeTrailerId(title, year)
            isResolving = false
        }
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
                } else if (resolvedYtId.isNotBlank()) {
                    DirectYoutubeEmbedPlayer(youtubeVideoId = resolvedYtId)
                } else {
                    Text("Trailer tidak tersedia untuk judul ini.", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DirectYoutubeEmbedPlayer(youtubeVideoId: String) {
    val context = LocalContext.current
    val embedHtml = remember(youtubeVideoId) {
        """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
        <style>
          html, body { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
          iframe { width:100%; height:100%; border:0; }
        </style>
        </head>
        <body>
          <iframe
            src="https://www.youtube-nocookie.com/embed/$youtubeVideoId?autoplay=1&rel=0&modestbranding=1&playsinline=1&fs=1"
            allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
            allowfullscreen>
          </iframe>
        </body>
        </html>
        """.trimIndent()
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
                userAgentString = HttpClientProvider.USER_AGENT
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
            if (wv.tag != youtubeVideoId) {
                wv.tag = youtubeVideoId
                wv.loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "utf-8", null)
            }
        },
        modifier = Modifier.fillMaxSize().background(Color.Black)
    )
}
