package com.swara.play.player

import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.SubtitleStylePrefs
import java.io.File

private val dubbedAudioLabels = listOf(
    "dub", "hindi", "hin", "tamil", "tam", "telugu", "tel", "rus", "russian",
    "spa", "spanish", "latino", "fre", "french", "ger", "german", "ita", "italian",
    "por", "portuguese", "pol", "tur", "ara", "ukr"
)

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

@Composable
fun DirectExoPlayerView(
    streamUrl: String,
    preferredAudioLang: String = "",
    preferredQualityHeight: Int = 720,
    referer: String,
    origin: String = "",
    vttContent: String,
    subtitleEnabled: Boolean,
    subtitleStyle: SubtitleStylePrefs,
    playbackSpeed: Float,
    videoFillMode: Boolean,
    controlCommand: Pair<Int, String>,
    onStreamError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(context, referer, origin) {
        val headers = mutableMapOf("User-Agent" to HttpClientProvider.USER_AGENT)
        if (referer.isNotBlank()) headers["Referer"] = referer
        if (origin.isNotBlank()) headers["Origin"] = origin
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                val tpBuilder = trackSelectionParameters.buildUpon()
                    .setPreferredTextLanguages("id", "in", "ind")
                if (preferredAudioLang.isNotBlank()) {
                    tpBuilder.setPreferredAudioLanguages(preferredAudioLang, "und")
                }
                if (preferredQualityHeight > 0) {
                    tpBuilder.setMaxVideoSize(Int.MAX_VALUE, preferredQualityHeight)
                }
                trackSelectionParameters = tpBuilder.build()
            }
    }

    LaunchedEffect(streamUrl, vttContent, subtitleEnabled, exoPlayer) {
        val builder = MediaItem.Builder().setUri(Uri.parse(streamUrl))
        if (subtitleEnabled && vttContent.isNotBlank()) {
            runCatching {
                val subFile = File(context.cacheDir, "swara_active_sub.vtt")
                subFile.writeText(vttContent, Charsets.UTF_8)
                val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subFile))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage("id")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                builder.setSubtitleConfigurations(listOf(subConfig))
            }
        }
        val prevPos = if (exoPlayer.currentMediaItem != null) exoPlayer.currentPosition else 0L
        exoPlayer.setMediaItem(builder.build())
        exoPlayer.prepare()
        if (prevPos > 1000L) exoPlayer.seekTo(prevPos)
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(playbackSpeed, exoPlayer) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(preferredQualityHeight, preferredAudioLang, subtitleEnabled, exoPlayer) {
        val tpBuilder = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitleEnabled)
            .setPreferredTextLanguages("id", "in", "ind")
        if (preferredAudioLang.isNotBlank()) {
            tpBuilder.setPreferredAudioLanguages(preferredAudioLang, "und")
        }
        if (preferredQualityHeight > 0) {
            tpBuilder.setMaxVideoSize(Int.MAX_VALUE, preferredQualityHeight)
        } else {
            tpBuilder.clearVideoSizeConstraints()
        }
        exoPlayer.trackSelectionParameters = tpBuilder.build()
    }

    LaunchedEffect(controlCommand, exoPlayer) {
        val (seq, cmd) = controlCommand
        if (seq > 0 && cmd.isNotBlank()) {
            val parts = cmd.split(":")
            when (parts[0]) {
                "skip" -> {
                    val deltaMs = (parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L) * 1000L
                    exoPlayer.seekTo((exoPlayer.currentPosition + deltaMs).coerceAtLeast(0L))
                }
                "playpause" -> {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                }
            }
        }
    }

    DisposableEffect(exoPlayer, preferredAudioLang) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onStreamError()
            }

            override fun onTracksChanged(tracks: Tracks) {
                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
                if (audioGroups.size <= 1) return

                var bestGroup: Tracks.Group? = null
                var bestTrackIdx = -1
                var bestScore = Int.MIN_VALUE

                for (group in audioGroups) {
                    for (tIdx in 0 until group.length) {
                        if (!group.isTrackSupported(tIdx)) continue
                        val fmt = group.getTrackFormat(tIdx)
                        val lang = (fmt.language ?: "").lowercase()
                        val label = (fmt.label ?: "").lowercase()
                        val combined = "$lang $label"
                        val isDubbed = dubbedAudioLabels.any { combined.contains(it) } ||
                            (preferredAudioLang in listOf("ja", "ko") && (lang == "en" || lang == "eng" || label.contains("english")))
                        var score = if (isDubbed) -100 else 10
                        if (preferredAudioLang.isNotBlank() && lang.startsWith(preferredAudioLang)) score += 60
                        if (label.contains("orig") || (fmt.roleFlags and C.ROLE_FLAG_MAIN) != 0) score += 30
                        if (score > bestScore) {
                            bestScore = score
                            bestGroup = group
                            bestTrackIdx = tIdx
                        }
                    }
                }
                if (bestGroup != null && bestTrackIdx >= 0 && !bestGroup.isTrackSelected(bestTrackIdx)) {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(bestGroup.mediaTrackGroup, listOf(bestTrackIdx)))
                        .build()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            runCatching { exoPlayer.release() }
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                keepScreenOn = true
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { pv ->
            if (pv.player !== exoPlayer) pv.player = exoPlayer
            pv.resizeMode = if (videoFillMode) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            pv.subtitleView?.apply {
                val bgColor = if (subtitleStyle.showBackground) android.graphics.Color.argb(145, 0, 0, 0) else android.graphics.Color.TRANSPARENT
                setStyle(
                    CaptionStyleCompat(
                        subtitleStyle.textColorHex.toInt(),
                        bgColor,
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                        android.graphics.Color.BLACK,
                        Typeface.SANS_SERIF
                    )
                )
                setFractionalTextSize(0.0533f * subtitleStyle.fontSizeScale)
                setBottomPaddingFraction(subtitleStyle.bottomPosition.coerceIn(0.04f, 0.30f))
            }
        },
        modifier = modifier.fillMaxSize().background(Color.Black)
    )
}
