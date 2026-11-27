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

@Composable
fun DirectExoPlayerView(
    streamUrl: String,
    preferredAudioLang: String = "",
    preferredQualityHeight: Int = 720,
    referer: String,
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
    val exoPlayer = remember(context, referer) {
        val headers = mutableMapOf("User-Agent" to HttpClientProvider.USER_AGENT)
        if (referer.isNotBlank()) headers["Referer"] = referer
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

    LaunchedEffect(streamUrl, vttContent, subtitleEnabled) {
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

    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(preferredQualityHeight, preferredAudioLang, subtitleEnabled) {
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

    LaunchedEffect(controlCommand) {
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
                // Real-time anti-dubbing audio lock: if multiple audio tracks exist, force original non-dubbed track!
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
