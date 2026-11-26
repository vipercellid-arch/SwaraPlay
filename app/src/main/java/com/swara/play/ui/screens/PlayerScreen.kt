package com.swara.play.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.AudioManager
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swara.play.model.CastMember
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.SubtitleStylePrefs
import com.swara.play.player.DirectExoPlayerView
import com.swara.play.player.OfflineExoPlayerView
import com.swara.play.player.StreamWebPlayer
import com.swara.play.player.safeSetFullscreen
import com.swara.play.ui.components.MediaPosterCard
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraCardElevated
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.viewmodel.PlayerUiState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PlayerRightDrawer { NONE, MORE, SPEED, QUALITY, SUBTITLE, SUBTITLE_STYLE, EPISODE }

private tailrec fun Context.findPlayerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPlayerActivity()
    else -> null
}

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    preferredLangIso: String,
    isInPipMode: Boolean,
    pipEnabled: Boolean,
    subtitleStyle: SubtitleStylePrefs,
    isFavorite: Boolean,
    showDeviceButton: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onTogglePipSetting: (Boolean) -> Unit,
    onRequestPip: () -> Unit,
    onOpenDeviceIntegration: () -> Unit,
    onSelectEpisode: (MediaItem, Int, Int) -> Unit,
    onSelectSimilar: (MediaItem) -> Unit,
    onMediaUrlSniffed: (String) -> Unit,
    onDownloadClick: () -> Unit,
    onSelectSubtitleLanguage: (String?) -> Unit,
    onUpdateSubtitleStyle: (SubtitleStylePrefs) -> Unit,
    onDirectStreamError: () -> Unit,
    onSilentFallback: () -> Unit,
    onActorClick: (CastMember) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findPlayerActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val batteryManager = remember(context) { context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager }
    val maxVolume = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var reloadToken by remember { mutableIntStateOf(0) }
    var isComposeFullscreen by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var showHudOverlay by remember { mutableStateOf(true) }
    var hudInteractionTick by remember { mutableIntStateOf(0) }
    var activeDrawer by remember { mutableStateOf(PlayerRightDrawer.NONE) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var selectedQualityHeight by remember { mutableIntStateOf(720) }
    var videoFillMode by remember { mutableStateOf(false) }
    var commandSeq by remember { mutableIntStateOf(0) }
    var commandPayload by remember { mutableStateOf("") }

    var brightnessLevel by remember {
        val cur = activity?.window?.attributes?.screenBrightness ?: 0.7f
        mutableFloatStateOf(if (cur in 0.05f..1f) cur else 0.7f)
    }
    var volumeLevel by remember {
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf(cur.toFloat() / maxVolume.toFloat())
    }
    var gestureHudLabel by remember { mutableStateOf("") }
    var clockText by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    var batteryPct by remember { mutableIntStateOf(batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100) }

    // Auto-hide HUD cleanly after 3 seconds of inactivity!
    LaunchedEffect(showHudOverlay, hudInteractionTick, activeDrawer) {
        if (showHudOverlay && activeDrawer == PlayerRightDrawer.NONE) {
            delay(3000)
            showHudOverlay = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            clockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            batteryPct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            delay(30_000)
        }
    }

    LaunchedEffect(gestureHudLabel) {
        if (gestureHudLabel.isNotBlank()) {
            delay(1300)
            gestureHudLabel = ""
        }
    }

    LaunchedEffect(isComposeFullscreen) {
        safeSetFullscreen(activity, isComposeFullscreen)
    }

    DisposableEffect(Unit) {
        onDispose { safeSetFullscreen(activity, false) }
    }

    BackHandler(enabled = activeDrawer != PlayerRightDrawer.NONE || isComposeFullscreen) {
        if (activeDrawer != PlayerRightDrawer.NONE) {
            activeDrawer = PlayerRightDrawer.NONE
        } else {
            isComposeFullscreen = false
        }
    }

    fun bumpHud() {
        showHudOverlay = true
        hudInteractionTick += 1
    }

    fun applyBrightness(newVal: Float) {
        val clamped = newVal.coerceIn(0.05f, 1f)
        brightnessLevel = clamped
        runCatching {
            val w = activity?.window ?: return
            val lp = w.attributes
            lp.screenBrightness = clamped
            w.attributes = lp
        }
        gestureHudLabel = "Kecerahan ${(clamped * 100).toInt()}%"
        bumpHud()
    }

    fun applyVolume(newVal: Float) {
        val clamped = newVal.coerceIn(0f, 1f)
        volumeLevel = clamped
        runCatching {
            val volInt = (clamped * maxVolume).toInt().coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volInt, 0)
        }
        gestureHudLabel = "Suara ${(clamped * 100).toInt()}%"
        bumpHud()
    }

    val item = state.mediaItem
    val activeSource = state.activeSource
    val offlineItem = state.offlineItem
    val hasEpisodes = state.episodes.isNotEmpty()
    var panelTab by remember(hasEpisodes) { mutableIntStateOf(if (hasEpisodes) 0 else 1) }

    @Composable
    fun VideoStage(modifier: Modifier) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.background(Color.Black)) {
            if (offlineItem != null) {
                OfflineExoPlayerView(
                    localVideoPath = offlineItem.localVideoPath,
                    localSubtitlePath = offlineItem.localSubtitlePath
                )
            } else if (state.directStreamUrl.isNotBlank()) {
                val origAudioLang = when (item?.category) {
                    MediaCategory.ANIME -> "ja"
                    MediaCategory.ASIAN_DRAMA -> "ko"
                    else -> ""
                }
                DirectExoPlayerView(
                    streamUrl = state.directStreamUrl,
                    preferredAudioLang = origAudioLang,
                    preferredQualityHeight = selectedQualityHeight,
                    referer = state.directStreamReferer,
                    vttContent = state.synchronizedVttContent,
                    subtitleEnabled = state.subtitleEnabled,
                    subtitleStyle = subtitleStyle,
                    playbackSpeed = playbackSpeed,
                    videoFillMode = videoFillMode,
                    controlCommand = commandSeq to commandPayload,
                    onStreamError = onDirectStreamError
                )
            } else if (state.isLoading || activeSource == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(color = Color(0xFFE91E63), strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                    Text(item?.title.orEmpty(), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Menyiapkan audio asli & subtitle Bahasa Indonesia...", color = TextSecondary, fontSize = 11.sp)
                }
            } else {
                StreamWebPlayer(
                    embedUrl = activeSource.url,
                    referer = activeSource.referer,
                    reloadToken = reloadToken,
                    preferredLangIso = state.activeSubtitleLangIso.ifBlank { preferredLangIso },
                    subtitleEnabled = state.subtitleEnabled,
                    subtitleStyle = subtitleStyle,
                    synchronizedVtt = state.synchronizedVttContent,
                    playbackSpeed = playbackSpeed,
                    videoFillMode = videoFillMode,
                    controlCommand = commandSeq to commandPayload,
                    onMediaUrlSniffed = onMediaUrlSniffed,
                    onSilentFallbackNeeded = onSilentFallback
                )
            }

            // Pure 100% clean video in Picture-in-Picture mini mode
            if (isInPipMode) return@Box

            // Left-edge Brightness drag + Tap to reveal HUD; Right-edge Volume drag + Tap to reveal HUD
            if (!isScreenLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight(0.8f)
                        .width(52.dp)
                        .clickable { bumpHud() }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount -> applyBrightness(brightnessLevel - dragAmount / 360f) }
                        }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(0.8f)
                        .width(52.dp)
                        .clickable { bumpHud() }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount -> applyVolume(volumeLevel - dragAmount / 360f) }
                        }
                )
                // Top thin tap strip to wake HUD cleanly without blocking center video controls
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(34.dp)
                        .clickable { bumpHud() }
                )
            }

            if (isScreenLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            bumpHud()
                            gestureHudLabel = "Layar Terkunci — Tekan Gembok untuk Buka"
                        }
                )
            }

            // Floating Gesture Pill
            AnimatedVisibility(
                visible = gestureHudLabel.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xDD141418))
                        .border(0.5.dp, SwaraSubtleBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(gestureHudLabel, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Left Vertical Brightness Slider + Lock Button (Auto-Hides after 3s!)
            AnimatedVisibility(
                visible = showHudOverlay && activeDrawer == PlayerRightDrawer.NONE,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isScreenLocked) Color.White else Color(0xBB141418))
                            .clickable {
                                isScreenLocked = !isScreenLocked
                                activeDrawer = PlayerRightDrawer.NONE
                                bumpHud()
                            }
                    ) {
                        Icon(
                            imageVector = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Kunci Layar",
                            tint = if (isScreenLocked) Color.Black else TextPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    if (!isScreenLocked) {
                        VerticalLevelBar(
                            icon = Icons.Default.Brightness6,
                            level = brightnessLevel,
                            onDragDelta = { applyBrightness(brightnessLevel - it / 160f) }
                        )
                    }
                }
            }

            // Right Vertical Volume Slider + Favorite Button (Auto-Hides after 3s!)
            AnimatedVisibility(
                visible = showHudOverlay && !isScreenLocked && activeDrawer == PlayerRightDrawer.NONE,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VerticalLevelBar(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        level = volumeLevel,
                        onDragDelta = { applyVolume(volumeLevel - it / 160f) }
                    )
                    if (item != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xBB141418))
                                .clickable { onToggleFavorite(item); bumpHud() }
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorit",
                                tint = TextPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            // Fullscreen Top Bar (Auto-Hides after 3s! Matches Video 2 exact layout)
            AnimatedVisibility(
                visible = showHudOverlay && !isScreenLocked && isComposeFullscreen && activeDrawer == PlayerRightDrawer.NONE,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    IconButton(onClick = { isComposeFullscreen = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Keluar Layar Penuh", tint = TextPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        val suffix = if (item != null && item.category != MediaCategory.MOVIE) " (Episode ${state.episode})" else ""
                        Text("${item?.title.orEmpty()}$suffix", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("WIFI  $clockText  $batteryPct%", color = Color(0xFFD8D8E0), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = onRequestPip, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Pemutaran Mini", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                        if (showDeviceButton) {
                            IconButton(onClick = onOpenDeviceIntegration, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Cast, contentDescription = "Integrasi Perangkat", tint = TextPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(onClick = { activeDrawer = PlayerRightDrawer.MORE }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = "Menu", tint = TextPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Non-Fullscreen Top-Right Video HUD Overlay (PiP & Cast moved INSIDE video player per user feedback!)
            AnimatedVisibility(
                visible = showHudOverlay && !isScreenLocked && !isComposeFullscreen && activeDrawer == PlayerRightDrawer.NONE,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xBB101014))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    IconButton(onClick = onRequestPip, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Pemutaran Mini", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                    if (showDeviceButton) {
                        IconButton(onClick = onOpenDeviceIntegration, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Cast, contentDescription = "Integrasi Perangkat", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Minimalist Bottom-Right Action Strip (Auto-Hides after 3s! Zero clutter bottom chips!)
            AnimatedVisibility(
                visible = showHudOverlay && !isScreenLocked && activeDrawer == PlayerRightDrawer.NONE,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xBB101014))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { activeDrawer = PlayerRightDrawer.SUBTITLE }
                    ) {
                        Icon(Icons.Default.ClosedCaption, contentDescription = "Subtitle", tint = if (state.subtitleEnabled) Color.White else TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    if (state.episodes.isNotEmpty()) {
                        Text(
                            text = "Episode",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { activeDrawer = PlayerRightDrawer.EPISODE }
                        )
                    }
                    if (!isComposeFullscreen) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "Menu",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp).clickable { activeDrawer = PlayerRightDrawer.MORE }
                        )
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Layar Penuh",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp).clickable { isComposeFullscreen = true; bumpHud() }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Kecilkan",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp).clickable { isComposeFullscreen = false }
                        )
                    }
                }
            }

            // Right-Side Sleek Drawers (Exact Video 2 style: More, Speed, Subtitle, Subtitle Style, Episode Grid)
            if (activeDrawer != PlayerRightDrawer.NONE) {
                Box(modifier = Modifier.fillMaxSize().clickable { activeDrawer = PlayerRightDrawer.NONE; bumpHud() }) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(265.dp)
                            .background(Color(0xEE0D0D11))
                            .clickable(enabled = false) {}
                            .padding(16.dp)
                    ) {
                        when (activeDrawer) {
                            PlayerRightDrawer.MORE -> {
                                val qLabel = if (selectedQualityHeight == 0) "Otomatis" else "${selectedQualityHeight}p"
                                DrawerOptionRow("Kualitas Video", qLabel) { activeDrawer = PlayerRightDrawer.QUALITY }
                                DrawerOptionRow("Kecepatan Pemutaran", "${playbackSpeed}X") { activeDrawer = PlayerRightDrawer.SPEED }
                                DrawerSwitchRow("Layar Penuh", videoFillMode) { videoFillMode = it }
                                DrawerSwitchRow("Pemutar mini otomatis saat pindah aplikasi", pipEnabled) { onTogglePipSetting(it) }
                            }
                            PlayerRightDrawer.QUALITY -> {
                                Text("Kualitas Video", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                listOf(480 to "480p", 720 to "720p (Standar)", 1080 to "1080p HD", 0 to "Otomatis").forEach { (h, label) ->
                                    val sel = selectedQualityHeight == h
                                    Text(
                                        text = label,
                                        color = if (sel) Color(0xFFE91E63) else TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium,
                                        modifier = Modifier.fillMaxWidth().clickable { selectedQualityHeight = h; activeDrawer = PlayerRightDrawer.NONE; bumpHud() }.padding(vertical = 7.dp)
                                    )
                                }
                            }
                            PlayerRightDrawer.SPEED -> {
                                Text("Kecepatan Pemutaran", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                    val sel = playbackSpeed == spd
                                    Text(
                                        text = "${spd}x",
                                        color = if (sel) Color(0xFFE91E63) else TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium,
                                        modifier = Modifier.fillMaxWidth().clickable { playbackSpeed = spd; activeDrawer = PlayerRightDrawer.NONE; bumpHud() }.padding(vertical = 6.dp)
                                    )
                                }
                            }
                            PlayerRightDrawer.SUBTITLE -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Semua Subtitle", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { activeDrawer = PlayerRightDrawer.SUBTITLE_STYLE }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.Settings, contentDescription = "Pengaturan Subtitle", tint = TextPrimary, modifier = Modifier.size(18.dp))
                                    }
                                }
                                val langs = listOf(null to "Nonaktif", "id" to "Bahasa Indonesia")
                                langs.forEach { (code, label) ->
                                    val selected = if (code == null) !state.subtitleEnabled else state.subtitleEnabled
                                    Text(
                                        text = label,
                                        color = if (selected) Color(0xFFE91E63) else TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            onSelectSubtitleLanguage(code)
                                            activeDrawer = PlayerRightDrawer.NONE
                                            bumpHud()
                                        }.padding(vertical = 7.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                        .clickable {
                                            onSelectSubtitleLanguage("id_translate_online")
                                            activeDrawer = PlayerRightDrawer.NONE
                                            bumpHud()
                                        }
                                        .padding(vertical = 10.dp, horizontal = 12.dp)
                                ) {
                                    Text("Cari & Terjemahkan Online", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            PlayerRightDrawer.SUBTITLE_STYLE -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { activeDrawer = PlayerRightDrawer.SUBTITLE }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pengaturan Subtitle", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Ukuran", color = TextSecondary, fontSize = 12.sp)
                                Slider(
                                    value = subtitleStyle.fontSizeScale,
                                    onValueChange = { onUpdateSubtitleStyle(subtitleStyle.copy(fontSizeScale = it)) },
                                    valueRange = 0.8f..1.8f,
                                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFFE91E63))
                                )
                                Text("Posisi", color = TextSecondary, fontSize = 12.sp)
                                Slider(
                                    value = subtitleStyle.bottomPosition,
                                    onValueChange = { onUpdateSubtitleStyle(subtitleStyle.copy(bottomPosition = it)) },
                                    valueRange = 0.04f..0.28f,
                                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFFE91E63))
                                )
                                Text("Warna", color = TextSecondary, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    listOf(0xFFFFFFFFL, 0xFFFFEB3BL, 0xFF4DD0E1L, 0xFF81C784L, 0xFFE91E63L).forEach { hex ->
                                        val active = subtitleStyle.textColorHex == hex
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(hex))
                                                .border(if (active) 2.dp else 0.5.dp, if (active) Color.White else SwaraSubtleBorder, CircleShape)
                                                .clickable { onUpdateSubtitleStyle(subtitleStyle.copy(textColorHex = hex)) }
                                        )
                                    }
                                }
                                DrawerSwitchRow("Warna latar belakang", subtitleStyle.showBackground) {
                                    onUpdateSubtitleStyle(subtitleStyle.copy(showBackground = it))
                                }
                            }
                            PlayerRightDrawer.EPISODE -> {
                                Text("Daftar Episode", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                val curEps = remember(state.episodes, state.season) { state.episodes.filter { it.season == state.season }.chunked(5) }
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                                    items(curEps.size, key = { "dr_ep_$it" }) { rIdx ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                            curEps[rIdx].forEach { ep ->
                                                val isCur = ep.episode == state.episode
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp))
                                                        .background(if (isCur) Color(0xFFE91E63) else SwaraCard)
                                                        .clickable {
                                                            if (item != null) onSelectEpisode(item, ep.season, ep.episode)
                                                            activeDrawer = PlayerRightDrawer.NONE
                                                            bumpHud()
                                                        }
                                                ) {
                                                    Text("${ep.episode}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            repeat(5 - curEps[rIdx].size) { Spacer(modifier = Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                            PlayerRightDrawer.NONE -> {}
                        }
                    }
                }
            }
        }
    }

    if (isInPipMode || isComposeFullscreen) {
        VideoStage(modifier = Modifier.fillMaxSize())
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(SwaraBlack).statusBarsPadding()) {
        val isSplitLandscape = maxWidth >= 600.dp

        @Composable
        fun SideTabPanel(modifier: Modifier) {
            val seasons = remember(state.episodes) { state.episodes.map { it.season }.distinct().sorted() }
            var viewingSeason by remember(state.season, seasons) { mutableIntStateOf(state.season) }
            val seasonEpisodes = remember(state.episodes, viewingSeason) { state.episodes.filter { it.season == viewingSeason } }
            val rangeCount = ((seasonEpisodes.size + 49) / 50).coerceAtLeast(1)
            val initialRange = remember(state.episode, viewingSeason) { ((state.episode - 1) / 50).coerceIn(0, rangeCount - 1) }
            var activeRange by remember(viewingSeason, rangeCount) { mutableIntStateOf(initialRange) }
            val pagedEpisodes = remember(seasonEpisodes, activeRange) {
                if (seasonEpisodes.size <= 50) seasonEpisodes else seasonEpisodes.drop(activeRange * 50).take(50)
            }
            val epRows = remember(pagedEpisodes) { pagedEpisodes.chunked(5) }

            Column(modifier = modifier.background(SwaraBlack)) {
                if (state.isDownloadingMedia || !state.downloadStatusMessage.isNullOrBlank()) {
                    Column(modifier = Modifier.fillMaxWidth().background(SwaraCardElevated).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(state.downloadStatusMessage.orEmpty(), color = TextPrimary, fontSize = 12.sp)
                        if (state.isDownloadingMedia) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(progress = { state.mediaDownloadProgress }, color = SwaraAccent, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                if (item != null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp)
                    ) {
                        Text(item.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val metaStr = listOfNotNull(
                            item.rating.takeIf { it.isNotBlank() }?.let { "★ $it" },
                            item.year.take(4).takeIf { it.isNotBlank() },
                            item.category.label
                        ).joinToString(" • ")
                        if (metaStr.isNotBlank()) {
                            Text(metaStr, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (item.overview.isNotBlank()) {
                            Text(item.overview, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        // 3 Quick Action Buttons: Unduh, Koleksi, Bagikan
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            if (offlineItem == null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(SwaraCard)
                                        .clickable(enabled = !state.isDownloadingMedia, onClick = onDownloadClick)
                                        .padding(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Unduh", tint = TextPrimary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Unduh", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .background(if (isFavorite) Color(0x33E91E63) else SwaraCard)
                                    .clickable { onToggleFavorite(item) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Koleksi",
                                    tint = if (isFavorite) Color(0xFFE91E63) else TextPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(if (isFavorite) "Tersimpan" else "Koleksi", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(SwaraCard)
                                    .clickable {
                                        val shareText = "Nonton ${item.title} subtitle Indonesia gratis di SwaraPlay: https://swaraplay.my.id"
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        runCatching { context.startActivity(Intent.createChooser(sendIntent, "Bagikan Tontonan")) }
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Bagikan", tint = TextPrimary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Bagikan", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        // Circular Cast / Pemeran Row (clickable to ActorProfileDialog!)
                        if (state.cast.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                                items(state.cast.take(10), key = { "pcast_${it.name}" }) { actor ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(56.dp).clickable { onActorClick(actor) }
                                    ) {
                                        AsyncImage(
                                            model = actor.photoUrl,
                                            contentDescription = actor.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(46.dp).clip(CircleShape).background(SwaraCard)
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(actor.name, color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().background(SwaraBlack).padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    listOf(0 to "Episode", 1 to "Tontonan Mirip").forEach { (idx, title) ->
                        val active = panelTab == idx
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (active) Color.White else SwaraCard)
                                .border(0.5.dp, if (active) Color.White else SwaraSubtleBorder, RoundedCornerShape(14.dp))
                                .clickable { panelTab = idx }
                                .padding(vertical = 9.dp)
                        ) {
                            Text(title, color = if (active) Color.Black else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    if (panelTab == 0) {
                        if (item != null && seasonEpisodes.isNotEmpty()) {
                            if (seasons.size > 1) {
                                item(key = "p_seasons") {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(seasons, key = { "ps_$it" }) { s ->
                                            val active = viewingSeason == s
                                            Box(
                                                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                                    .background(if (active) Color.White else SwaraCard)
                                                    .clickable { viewingSeason = s; activeRange = 0 }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) { Text("Musim $s", color = if (active) Color.Black else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                }
                            }
                            if (rangeCount > 1) {
                                item(key = "p_ranges") {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(rangeCount, key = { "pr_$it" }) { idx ->
                                            val start = idx * 50 + 1
                                            val end = minOf((idx + 1) * 50, seasonEpisodes.size)
                                            val active = activeRange == idx
                                            Box(
                                                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                                    .background(if (active) Color.White else SwaraCard)
                                                    .clickable { activeRange = idx }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) { Text("$start–$end", color = if (active) Color.Black else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                }
                            }
                            items(epRows.size, key = { "pe_${activeRange}_$it" }) { rIdx ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    epRows[rIdx].forEach { ep ->
                                        val isCurrent = ep.episode == state.episode && ep.season == state.season
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp))
                                                .background(if (isCurrent) Color.White else SwaraCard)
                                                .border(0.5.dp, if (isCurrent) Color.White else SwaraSubtleBorder, RoundedCornerShape(10.dp))
                                                .clickable { onSelectEpisode(item, ep.season, ep.episode) }
                                        ) { Text("${ep.episode}", color = if (isCurrent) Color.Black else TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                    }
                                    repeat(5 - epRows[rIdx].size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        } else if (item != null) {
                            item(key = "movie_info") {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(item.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    if (offlineItem != null) Text("Memutar dari penyimpanan offline", color = TextSecondary, fontSize = 12.sp)
                                    else if (item.overview.isNotBlank()) Text(item.overview, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                    } else {
                        if (state.similar.isEmpty()) {
                            item(key = "no_sim") { Text("Belum ada rekomendasi serupa.", color = TextSecondary, fontSize = 13.sp) }
                        } else {
                            val simRows = state.similar.chunked(2)
                            items(simRows.size, key = { "psim_grid_$it" }) { rIdx ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    simRows[rIdx].forEach { sim ->
                                        MediaPosterCard(item = sim, onClick = { onSelectSimilar(sim) }, modifier = Modifier.weight(1f))
                                    }
                                    if (simRows[rIdx].size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(SwaraCard).padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(item?.title.orEmpty(), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item != null && item.category != MediaCategory.MOVIE) {
                        Text("Musim ${state.season} • Episode ${state.episode}", color = TextSecondary, fontSize = 11.sp)
                    }
                }
                if (offlineItem == null) {
                    IconButton(onClick = { reloadToken += 1 }) { Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang", tint = TextPrimary, modifier = Modifier.size(20.dp)) }
                }
            }

            if (isSplitLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    VideoStage(modifier = Modifier.weight(0.64f).fillMaxHeight())
                    SideTabPanel(modifier = Modifier.weight(0.36f).fillMaxHeight())
                }
            } else {
                VideoStage(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                SideTabPanel(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun DrawerOptionRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DrawerSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFE91E63))
        )
    }
}

@Composable
private fun VerticalLevelBar(icon: ImageVector, level: Float, onDragDelta: (Float) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x99101014))
            .pointerInput(Unit) { detectVerticalDragGestures { _, dragAmount -> onDragDelta(dragAmount) } }
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier.width(4.dp).height(86.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF))
        ) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(level.coerceIn(0.05f, 1f)).clip(RoundedCornerShape(2.dp)).background(Color.White))
        }
    }
}
