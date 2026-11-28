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
import com.swara.play.data.network.LocalStreamProxyServer
import com.swara.play.model.SubtitleStylePrefs
import kotlinx.coroutines.delay
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
    preferredAudioLang: String = "original",
    forceOriginalAudio: Boolean = true,
    selectedAudioTrackIndex: Int = -1,
    preferredQualityHeight: Int = 0,
    initialPositionMs: Long = 0L,
    referer: String,
    origin: String = "",
    vttContent: String,
    subtitleEnabled: Boolean,
    subtitleStyle: SubtitleStylePrefs,
    playbackSpeed: Float,
    videoFillMode: Boolean,
    controlCommand: Pair<Int, String>,
    onStreamError: () -> Unit,
    onVideoTracksDetected: (List<Int>) -> Unit = {},
    onAudioTracksDetected: (List<Pair<Int, String>>) -> Unit = {},
    onPositionUpdate: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Keep single ExoPlayer instance across server switches — LocalStreamProxyServer injects per-URL Referer/Origin/DoH!
    val exoPlayer = remember(context) {
        LocalStreamProxyServer.ensureStarted()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(HttpClientProvider.USER_AGENT)
            .setConnectTimeoutMs(18_000)
            .setReadTimeoutMs(25_000)
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                val tpBuilder = trackSelectionParameters.buildUpon()
                    .setPreferredTextLanguages("id", "in", "ind")
                if (preferredQualityHeight > 0) {
                    tpBuilder.setMaxVideoSize(Int.MAX_VALUE, preferredQualityHeight)
                }
                trackSelectionParameters = tpBuilder.build()
            }
    }

    // Periodically save playback position so server/resolution switch NEVER restarts from 00:00
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(1500L)
            val pos = exoPlayer.currentPosition
            if (pos > 1000L) onPositionUpdate(pos)
        }
    }

    LaunchedEffect(streamUrl, referer, origin, vttContent, subtitleEnabled, exoPlayer) {
        val currentPos = exoPlayer.currentPosition.takeIf { it > 1500L } ?: initialPositionMs
        val proxiedUri = LocalStreamProxyServer.buildProxyUrl(streamUrl, referer, origin)
        val builder = MediaItem.Builder().setUri(Uri.parse(proxiedUri))
        // Force HLS MIME type when URL is not a direct MP4/MKV/WEBM so extensionless HLS URLs from Hexa/Nexa/MeowTV never fail
        val isDirectProgressive = streamUrl.contains(".mp4", true) || streamUrl.contains(".mkv", true) || streamUrl.contains(".webm", true)
        if (!isDirectProgressive) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
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
        exoPlayer.setMediaItem(builder.build(), currentPos.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(playbackSpeed, exoPlayer) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(preferredQualityHeight, preferredAudioLang, forceOriginalAudio, subtitleEnabled, exoPlayer) {
        val tpBuilder = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitleEnabled)
            .setPreferredTextLanguages("id", "in", "ind")
        val targetLang = if (!forceOriginalAudio && preferredAudioLang.isNotBlank() && preferredAudioLang != "original") {
            preferredAudioLang
        } else {
            ""
        }
        if (targetLang.isNotBlank()) {
            tpBuilder.setPreferredAudioLanguages(targetLang, "und")
        }
        if (preferredQualityHeight > 0) {
            tpBuilder.setMaxVideoSize(Int.MAX_VALUE, preferredQualityHeight)
        } else {
            tpBuilder.clearVideoSizeConstraints()
        }
        exoPlayer.trackSelectionParameters = tpBuilder.build()
    }

    // Apply explicit user audio track pick from Player Audio panel
    LaunchedEffect(selectedAudioTrackIndex, exoPlayer) {
        if (selectedAudioTrackIndex < 0) return@LaunchedEffect
        val audioGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        var flatIdx = 0
        for (group in audioGroups) {
            for (tIdx in 0 until group.length) {
                if (!group.isTrackSupported(tIdx)) continue
                if (flatIdx == selectedAudioTrackIndex) {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(tIdx)))
                        .build()
                    return@LaunchedEffect
                }
                flatIdx++
            }
        }
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

    DisposableEffect(exoPlayer, preferredAudioLang, forceOriginalAudio, selectedAudioTrackIndex) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val pos = exoPlayer.currentPosition
                if (pos > 1000L) onPositionUpdate(pos)
                onStreamError()
            }

            override fun onTracksChanged(tracks: Tracks) {
                // Report real HLS/DASH video resolutions dynamically to PlayerScreen
                val detectedHeights = mutableSetOf<Int>()
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO && group.isSupported) {
                        for (tIdx in 0 until group.length) {
                            if (!group.isTrackSupported(tIdx)) continue
                            val h = group.getTrackFormat(tIdx).height
                            if (h >= 240) detectedHeights.add(h)
                        }
                    }
                }
                if (detectedHeights.isNotEmpty()) {
                    onVideoTracksDetected(detectedHeights.sortedDescending())
                }

                // Collect & report all Audio / Dubbing tracks
                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
                val audioList = mutableListOf<Pair<Int, String>>()
                var flatIdx = 0
                for (group in audioGroups) {
                    for (tIdx in 0 until group.length) {
                        if (!group.isTrackSupported(tIdx)) continue
                        val fmt = group.getTrackFormat(tIdx)
                        val lang = (fmt.language ?: "").uppercase()
                        val lbl = fmt.label?.takeIf { it.isNotBlank() }
                            ?: if (lang.isNotBlank() && lang != "UND") "Audio ($lang)" else "Audio #${flatIdx + 1}"
                        audioList.add(flatIdx to lbl)
                        flatIdx++
                    }
                }
                if (audioList.isNotEmpty()) {
                    onAudioTracksDetected(audioList)
                }

                if (audioGroups.size <= 1 || selectedAudioTrackIndex >= 0) return

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
                        var score = 10
                        if (forceOriginalAudio || preferredAudioLang == "original" || preferredAudioLang.isBlank()) {
                            val isDubbed = dubbedAudioLabels.any { combined.contains(it) }
                            if (isDubbed) score -= 100
                            if (preferredAudioLang in listOf("ja", "ko") && lang.startsWith(preferredAudioLang)) score += 70
                            if (label.contains("orig") || (fmt.roleFlags and C.ROLE_FLAG_MAIN) != 0) score += 40
                        } else {
                            if (lang.startsWith(preferredAudioLang.lowercase()) || combined.contains(preferredAudioLang.lowercase())) {
                                score += 100
                            }
                        }
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
