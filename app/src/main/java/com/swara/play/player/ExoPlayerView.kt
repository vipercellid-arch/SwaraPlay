package com.swara.play.player

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
import androidx.media3.ui.PlayerView
import com.swara.play.model.SubtitleTrack
import kotlinx.coroutines.delay

@Composable
fun ExoPlayerView(
    streamUrl: String,
    subtitleTrack: SubtitleTrack?,
    startPositionMs: Long,
    onProgressUpdate: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(context) { ExoPlayer.Builder(context).build() }

    LaunchedEffect(streamUrl, subtitleTrack) {
        val builder = MediaItem.Builder().setUri(Uri.parse(streamUrl))
        if (subtitleTrack != null && subtitleTrack.url.isNotBlank()) {
            val mime = if (subtitleTrack.format.equals("vtt", ignoreCase = true) ||
                subtitleTrack.url.endsWith(".vtt", ignoreCase = true)
            ) {
                MimeTypes.TEXT_VTT
            } else {
                MimeTypes.APPLICATION_SUBRIP
            }
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleTrack.url))
                .setMimeType(mime)
                .setLanguage(subtitleTrack.languageCode)
                .setLabel(subtitleTrack.languageLabel)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(subConfig))
        }
        exoPlayer.setMediaItem(builder.build())
        exoPlayer.prepare()
        if (startPositionMs > 5000L) exoPlayer.seekTo(startPositionMs)
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(5000L)
            val dur = exoPlayer.duration
            val pos = exoPlayer.currentPosition
            if (dur > 0L && pos > 0L) onProgressUpdate(pos, dur)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            val dur = exoPlayer.duration
            val pos = exoPlayer.currentPosition
            if (dur > 0L && pos > 0L) onProgressUpdate(pos, dur)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                keepScreenOn = true
            }
        },
        modifier = modifier.fillMaxSize().background(Color.Black)
    )
}
