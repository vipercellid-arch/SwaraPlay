package com.swara.play.player

import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import java.io.File

@Composable
fun OfflineExoPlayerView(
    localVideoPath: String,
    localSubtitlePath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(context) { ExoPlayer.Builder(context).build() }

    LaunchedEffect(localVideoPath, localSubtitlePath) {
        val videoUri = Uri.fromFile(File(localVideoPath))
        val builder = MediaItem.Builder().setUri(videoUri)
        if (localSubtitlePath.isNotBlank() && File(localSubtitlePath).exists()) {
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(File(localSubtitlePath)))
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("id")
                .setLabel("Indonesia")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(subConfig))
        }
        exoPlayer.setMediaItem(builder.build())
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                keepScreenOn = true
                subtitleView?.setStyle(
                    CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        android.graphics.Color.argb(120, 0, 0, 0),
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                        android.graphics.Color.BLACK,
                        Typeface.SANS_SERIF
                    )
                )
            }
        },
        modifier = modifier.fillMaxSize().background(Color.Black)
    )
}
