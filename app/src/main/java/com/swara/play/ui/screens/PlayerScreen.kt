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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.play.model.CastMember
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.SubtitleStylePrefs
import com.swara.play.player.DirectExoPlayerView
import com.swara.play.player.OfflineExoPlayerView
import com.swara.play.player.safeSetFullscreen
import com.swara.play.ui.theme.LocalSwaraPalette
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

private enum class PlayerOverlayPanel { NONE, HUB, SERVER, QUALITY, SUBTITLE, SUBTITLE_STYLE, SPEED, EPISODE }

private tailrec fun Context.findPlayerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPlayerActivity()
    else -> null
}

/**
 * SwaraPlay v11.7.0 — 1:1 Web-Parity Direct Fullscreen Cinema Player
 * Saat pengguna menekan Putar/Nonton, langsung masuk ke pemutar layar penuh imersif
 * dengan HUD melayang, overlay kaca untuk Server, Kualitas HLS dinamis, Subtitle 5-Lapis,
 * Episode, dan Pengaturan Pemutar.
 */
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
    onSelectServer: (Int) -> Unit,
    onRetryPlayback: () -> Unit,
    onDownloadClick: () -> Unit,
    onSelectSubtitleLanguage: (String?) -> Unit,
    onUpdateSubtitleStyle: (SubtitleStylePrefs) -> Unit,
    onDirectStreamError: () -> Unit,
    onActorClick: (CastMember) -> Unit = {}
) {
    val context = LocalContext.current
    val pal = LocalSwaraPalette.current
    val activity = remember(context) { context.findPlayerActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val batteryManager = remember(context) { context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager }
    val maxVolume = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var isScreenLocked by remember { mutableStateOf(false) }
    var showHudOverlay by remember { mutableStateOf(true) }
    var hudInteractionTick by remember { mutableIntStateOf(0) }
    var activePanel by remember { mutableStateOf(PlayerOverlayPanel.NONE) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var selectedQualityHeight by remember { mutableIntStateOf(0) }
    var detectedHeights by remember(state.directStreamUrl) { mutableStateOf<List<Int>>(emptyList()) }
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

    // Direct Fullscreen Immersive mode immediately upon entering PlayerScreen (1:1 with Web!)
    LaunchedEffect(Unit) {
        safeSetFullscreen(activity, true)
    }
    DisposableEffect(Unit) {
        onDispose { safeSetFullscreen(activity, false) }
    }

    LaunchedEffect(showHudOverlay, hudInteractionTick, activePanel) {
        if (showHudOverlay && activePanel == PlayerOverlayPanel.NONE) {
            delay(3600)
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
            delay(1400)
            gestureHudLabel = ""
        }
    }

    BackHandler(enabled = activePanel != PlayerOverlayPanel.NONE) {
        activePanel = PlayerOverlayPanel.NONE
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
    val offlineItem = state.offlineItem
    val activeQualityText = when {
        selectedQualityHeight > 0 -> "${selectedQualityHeight}p"
        detectedHeights.isNotEmpty() -> "Auto (${detectedHeights.first()}p)"
        else -> "Auto"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        // VIDEO SURFACE
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
                origin = state.directStreamOrigin,
                vttContent = state.synchronizedVttContent,
                subtitleEnabled = state.subtitleEnabled,
                subtitleStyle = subtitleStyle,
                playbackSpeed = playbackSpeed,
                videoFillMode = videoFillMode,
                controlCommand = commandSeq to commandPayload,
                onStreamError = onDirectStreamError,
                onVideoTracksDetected = { heights -> detectedHeights = heights }
            )
        } else if (state.isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = pal.brandAccent, strokeWidth = 3.dp, modifier = Modifier.size(38.dp))
                Text(
                    text = "Menyiapkan server Hexa, Nexa & MeowTV...",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(28.dp)
            ) {
                Text(
                    text = state.streamErrorMessage ?: "Aliran video tidak tersedia saat ini.",
                    color = TextSecondary,
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onRetryPlayback,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text("Coba Lagi", fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                    }
                    if (state.sources.isNotEmpty()) {
                        Button(
                            onClick = { activePanel = PlayerOverlayPanel.SERVER },
                            colors = ButtonDefaults.buttonColors(containerColor = SwaraCardElevated, contentColor = TextPrimary),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text("Pilih Server (${state.sources.size})", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }

        if (isInPipMode) return@Box

        // GESTURE ZONES (Brightness Left / Volume Right / Tap Top to Toggle HUD)
        if (!isScreenLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(0.75f)
                    .width(58.dp)
                    .clickable { bumpHud() }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount -> applyBrightness(brightnessLevel - dragAmount / 360f) }
                    }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.75f)
                    .width(58.dp)
                    .clickable { bumpHud() }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount -> applyVolume(volumeLevel - dragAmount / 360f) }
                    }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { bumpHud() }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        bumpHud()
                        gestureHudLabel = "Layar Terkunci — Tekan Gembok untuk Buka"
                    }
            )
        }

        // DOWNLOAD PROGRESS BANNER
        if (state.isDownloadingMedia) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 66.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xDD101312))
                    .border(1.dp, pal.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(state.downloadStatusMessage ?: "Mengunduh...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { state.mediaDownloadProgress }, color = pal.brandAccent, modifier = Modifier.width(200.dp))
            }
        }

        // GESTURE TOAST PILL
        AnimatedVisibility(
            visible = gestureHudLabel.isNotBlank(),
            enter = fadeIn() + slideInVertically { -20 },
            exit = fadeOut() + slideOutVertically { -20 },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xE0101413))
                    .border(1.dp, pal.border, RoundedCornerShape(99.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(gestureHudLabel, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        // LEFT LOCK + BRIGHTNESS BAR
        AnimatedVisibility(
            visible = showHudOverlay && activePanel == PlayerOverlayPanel.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isScreenLocked) Color.White else Color(0xBB0D100F))
                        .border(1.dp, pal.border, CircleShape)
                        .clickable {
                            isScreenLocked = !isScreenLocked
                            activePanel = PlayerOverlayPanel.NONE
                            bumpHud()
                        }
                ) {
                    Icon(
                        imageVector = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Kunci Layar",
                        tint = if (isScreenLocked) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
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

        // RIGHT VOLUME BAR
        AnimatedVisibility(
            visible = showHudOverlay && !isScreenLocked && activePanel == PlayerOverlayPanel.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)
        ) {
            VerticalLevelBar(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                level = volumeLevel,
                onDragDelta = { applyVolume(volumeLevel - it / 160f) }
            )
        }

        // TOP WEB-PARITY GLASS HUD BAR
        AnimatedVisibility(
            visible = showHudOverlay && !isScreenLocked && activePanel == PlayerOverlayPanel.NONE,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xD9000000), Color.Transparent)))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x990D100F))
                        .border(1.dp, pal.border, CircleShape)
                        .clickable(onClick = onBack)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val epTag = if (item != null && item.category != MediaCategory.MOVIE) " • S${state.season}E${state.episode}" else ""
                    Text(
                        text = "${item?.title.orEmpty()}$epTag",
                        color = Color.White,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subInfo = listOfNotNull(
                        state.activeServerName.takeIf { it.isNotBlank() },
                        activeQualityText
                    ).joinToString(" • ")
                    if (subInfo.isNotBlank()) {
                        Text(subInfo, color = Color(0xFFB8C4BE), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color(0x880D100F))
                            .border(1.dp, pal.border, RoundedCornerShape(99.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("$clockText • $batteryPct%", color = Color(0xFFE2E8E5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (offlineItem == null) {
                        IconButton(onClick = onRetryPlayback, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang", tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                    }
                    IconButton(onClick = onRequestPip, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                    if (showDeviceButton) {
                        IconButton(onClick = onOpenDeviceIntegration, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Cast, contentDescription = "Cast", tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        }

        // BOTTOM WEB-PARITY FLOATING ACTION DOCK (Servers • Subtitle • Kualitas • Episode • Pengaturan)
        AnimatedVisibility(
            visible = showHudOverlay && !isScreenLocked && activePanel == PlayerOverlayPanel.NONE,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 58.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xCC0C100F))
                    .border(1.dp, pal.border, RoundedCornerShape(99.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                if (offlineItem == null) {
                    HudDockButton(
                        icon = Icons.Default.Dns,
                        label = "Server${if (state.sources.isNotEmpty()) " (${state.sources.size})" else ""}",
                        active = false,
                        onClick = { activePanel = PlayerOverlayPanel.SERVER }
                    )
                }
                HudDockButton(
                    icon = Icons.Default.HighQuality,
                    label = activeQualityText,
                    active = selectedQualityHeight > 0,
                    onClick = { activePanel = PlayerOverlayPanel.QUALITY }
                )
                HudDockButton(
                    icon = Icons.Default.ClosedCaption,
                    label = if (state.subtitleEnabled) "Sub ID" else "Sub Off",
                    active = state.subtitleEnabled,
                    onClick = { activePanel = PlayerOverlayPanel.SUBTITLE }
                )
                if (state.episodes.isNotEmpty()) {
                    HudDockButton(
                        icon = Icons.Default.List,
                        label = "Episode",
                        active = false,
                        onClick = { activePanel = PlayerOverlayPanel.EPISODE }
                    )
                }
                HudDockButton(
                    icon = Icons.Default.Settings,
                    label = "Pengaturan",
                    active = false,
                    onClick = { activePanel = PlayerOverlayPanel.HUB }
                )
            }
        }

        // ANIMATED WEB-PARITY GLASS OVERLAY PANEL (Right Side Sheet)
        AnimatedVisibility(
            visible = activePanel != PlayerOverlayPanel.NONE,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 2 }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable { activePanel = PlayerOverlayPanel.NONE; bumpHud() }
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(310.dp)
                        .background(Color(0xF20B0F0E))
                        .border(1.dp, pal.border)
                        .clickable(enabled = false) {}
                        .padding(18.dp)
                ) {
                    // Panel Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val headerTitle = when (activePanel) {
                            PlayerOverlayPanel.HUB -> "Pengaturan Player"
                            PlayerOverlayPanel.SERVER -> "Pilih Server (${state.sources.size})"
                            PlayerOverlayPanel.QUALITY -> "Kualitas Video"
                            PlayerOverlayPanel.SUBTITLE -> "Subtitle (5-Lapis)"
                            PlayerOverlayPanel.SUBTITLE_STYLE -> "Tampilan Subtitle"
                            PlayerOverlayPanel.SPEED -> "Kecepatan Putar"
                            PlayerOverlayPanel.EPISODE -> "Daftar Episode"
                            PlayerOverlayPanel.NONE -> ""
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (activePanel != PlayerOverlayPanel.HUB) {
                                IconButton(onClick = { activePanel = PlayerOverlayPanel.HUB }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            Text(headerTitle, color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        IconButton(onClick = { activePanel = PlayerOverlayPanel.NONE; bumpHud() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    when (activePanel) {
                        PlayerOverlayPanel.HUB -> {
                            // 4 Web-Parity Stat Cards (2x2 Grid)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                HubStatCard("KUALITAS", activeQualityText, Modifier.weight(1f)) { activePanel = PlayerOverlayPanel.QUALITY }
                                HubStatCard("SERVER", state.activeServerName.substringBefore(" •").ifBlank { "Auto" }, Modifier.weight(1f)) { activePanel = PlayerOverlayPanel.SERVER }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                HubStatCard("SUBTITLE", if (state.subtitleEnabled) "Indonesia" else "Mati", Modifier.weight(1f)) { activePanel = PlayerOverlayPanel.SUBTITLE }
                                HubStatCard("KECEPATAN", "${playbackSpeed}x", Modifier.weight(1f)) { activePanel = PlayerOverlayPanel.SPEED }
                            }
                            DrawerOptionRow("Kustomisasi Subtitle", "Ukuran & Warna ›") { activePanel = PlayerOverlayPanel.SUBTITLE_STYLE }
                            DrawerSwitchRow("Penuhi Layar (Zoom)", videoFillMode) { videoFillMode = it }
                            DrawerSwitchRow("PiP Otomatis", pipEnabled) { onTogglePipSetting(it) }
                            if (item != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                    if (offlineItem == null) {
                                        HubActionChip(Icons.Default.Download, "Unduh", Modifier.weight(1f)) {
                                            onDownloadClick()
                                            activePanel = PlayerOverlayPanel.NONE
                                        }
                                    }
                                    HubActionChip(
                                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        if (isFavorite) "Tersimpan" else "Simpan",
                                        Modifier.weight(1f)
                                    ) { onToggleFavorite(item) }
                                    HubActionChip(Icons.Default.Share, "Bagikan", Modifier.weight(1f)) {
                                        val shareText = "Nonton ${item.title} subtitle Indonesia gratis di SwaraPlay!"
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        runCatching { context.startActivity(Intent.createChooser(sendIntent, "Bagikan")) }
                                    }
                                }
                            }
                        }

                        PlayerOverlayPanel.SERVER -> {
                            if (state.sources.isEmpty()) {
                                Text("Sedang mencari server aktif...", color = TextSecondary, fontSize = 12.5.sp)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxSize()) {
                                    itemsIndexed(state.sources, key = { idx, s -> "srv_${idx}_${s.id}" }) { idx, srv ->
                                        val active = idx == state.activeSourceIndex
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (active) pal.brandAccent.copy(alpha = 0.22f) else SwaraCard)
                                                .border(1.dp, if (active) pal.brandAccent else pal.border, RoundedCornerShape(12.dp))
                                                .clickable {
                                                    onSelectServer(idx)
                                                    activePanel = PlayerOverlayPanel.NONE
                                                    gestureHudLabel = "Server: ${srv.label}"
                                                    bumpHud()
                                                }
                                                .padding(horizontal = 12.dp, vertical = 11.dp)
                                        ) {
                                            Text(
                                                text = srv.label.ifBlank { srv.id },
                                                color = if (active) Color.White else TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (active) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = pal.brandAccent, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        PlayerOverlayPanel.QUALITY -> {
                            val qualityOptions = remember(detectedHeights) {
                                val dynamic = detectedHeights.distinct().sortedDescending()
                                if (dynamic.isNotEmpty()) dynamic else listOf(2160, 1080, 720, 480, 360)
                            }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxSize()) {
                                item {
                                    QualityRowItem("Auto (Adaptif)", selectedQualityHeight == 0, pal.brandAccent, pal.border) {
                                        selectedQualityHeight = 0
                                        activePanel = PlayerOverlayPanel.NONE
                                        gestureHudLabel = "Kualitas: Auto"
                                        bumpHud()
                                    }
                                }
                                items(qualityOptions) { h ->
                                    val lbl = if (h >= 2160) "4K (2160p)" else "${h}p"
                                    QualityRowItem(lbl, selectedQualityHeight == h, pal.brandAccent, pal.border) {
                                        selectedQualityHeight = h
                                        activePanel = PlayerOverlayPanel.NONE
                                        gestureHudLabel = "Kualitas: $lbl"
                                        bumpHud()
                                    }
                                }
                                if (detectedHeights.size <= 1 && state.sources.size > 1) {
                                    item {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(SwaraCard)
                                                .border(1.dp, pal.border, RoundedCornerShape(12.dp))
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                "Server aktif hanya menyediakan 1 resolusi. Beralih ke server Hexa/Nexa/MeowTV lain untuk pilihan resolusi lebih lengkap.",
                                                color = TextSecondary,
                                                fontSize = 11.5.sp,
                                                lineHeight = 16.sp
                                            )
                                            Button(
                                                onClick = { activePanel = PlayerOverlayPanel.SERVER },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                                shape = RoundedCornerShape(99.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Ganti Server (${state.sources.size})", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        PlayerOverlayPanel.SUBTITLE -> {
                            QualityRowItem("Matikan Subtitle", !state.subtitleEnabled, pal.brandAccent, pal.border) {
                                onSelectSubtitleLanguage(null)
                                activePanel = PlayerOverlayPanel.NONE
                                gestureHudLabel = "Subtitle Dimatikan"
                                bumpHud()
                            }
                            QualityRowItem("Bahasa Indonesia (Otomatis 5-Lapis)", state.subtitleEnabled && state.activeSubtitleLangIso == "id", pal.brandAccent, pal.border) {
                                onSelectSubtitleLanguage("id")
                                activePanel = PlayerOverlayPanel.NONE
                                gestureHudLabel = "Memuat Subtitle Indonesia..."
                                bumpHud()
                            }
                            QualityRowItem("Terjemahkan Online (Paksa EN → ID)", state.subtitleEnabled && state.activeSubtitleLangIso == "id_translate_online", pal.brandAccent, pal.border) {
                                onSelectSubtitleLanguage("id_translate_online")
                                activePanel = PlayerOverlayPanel.NONE
                                gestureHudLabel = "Menerjemahkan ke Bahasa Indonesia..."
                                bumpHud()
                            }
                            Spacer(Modifier.height(6.dp))
                            DrawerOptionRow("Kustomisasi Tampilan Subtitle", "Atur ›") { activePanel = PlayerOverlayPanel.SUBTITLE_STYLE }
                        }

                        PlayerOverlayPanel.SUBTITLE_STYLE -> {
                            Text("Ukuran Teks", color = TextSecondary, fontSize = 12.sp)
                            Slider(
                                value = subtitleStyle.fontSizeScale,
                                onValueChange = { onUpdateSubtitleStyle(subtitleStyle.copy(fontSizeScale = it)) },
                                valueRange = 0.8f..1.8f,
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = pal.brandAccent)
                            )
                            Text("Posisi Bawah", color = TextSecondary, fontSize = 12.sp)
                            Slider(
                                value = subtitleStyle.bottomPosition,
                                onValueChange = { onUpdateSubtitleStyle(subtitleStyle.copy(bottomPosition = it)) },
                                valueRange = 0.04f..0.28f,
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = pal.brandAccent)
                            )
                            Text("Warna Teks", color = TextSecondary, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(0xFFFFFFFFL, 0xFFFFEB3BL, 0xFF4DD0E1L, 0xFF81C784L, 0xFFE91E63L).forEach { hex ->
                                    val active = subtitleStyle.textColorHex == hex
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color(hex))
                                            .border(if (active) 2.dp else 1.dp, if (active) Color.White else SwaraSubtleBorder, CircleShape)
                                            .clickable { onUpdateSubtitleStyle(subtitleStyle.copy(textColorHex = hex)) }
                                    )
                                }
                            }
                            DrawerSwitchRow("Latar Belakang Gelap", subtitleStyle.showBackground) {
                                onUpdateSubtitleStyle(subtitleStyle.copy(showBackground = it))
                            }
                        }

                        PlayerOverlayPanel.SPEED -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)) { spd ->
                                    QualityRowItem("${spd}x", playbackSpeed == spd, pal.brandAccent, pal.border) {
                                        playbackSpeed = spd
                                        activePanel = PlayerOverlayPanel.NONE
                                        gestureHudLabel = "Kecepatan: ${spd}x"
                                        bumpHud()
                                    }
                                }
                            }
                        }

                        PlayerOverlayPanel.EPISODE -> {
                            val seasons = remember(state.episodes) { state.episodes.map { it.season }.distinct().sorted() }
                            var viewingSeason by remember(state.season, seasons) { mutableIntStateOf(state.season) }
                            val seasonEps = remember(state.episodes, viewingSeason) { state.episodes.filter { it.season == viewingSeason } }
                            if (seasons.size > 1) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(seasons) { s ->
                                        val active = viewingSeason == s
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(99.dp))
                                                .background(if (active) Color.White else SwaraCard)
                                                .clickable { viewingSeason = s }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Musim $s", color = if (active) Color.Black else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            val rows = remember(seasonEps) { seasonEps.chunked(5) }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                                items(rows.size, key = { "dr_ep_${viewingSeason}_$it" }) { rIdx ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                        rows[rIdx].forEach { ep ->
                                            val isCur = ep.episode == state.episode && ep.season == state.season
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                                                    .background(if (isCur) pal.brandAccent else SwaraCard)
                                                    .clickable {
                                                        if (item != null) onSelectEpisode(item, ep.season, ep.episode)
                                                        activePanel = PlayerOverlayPanel.NONE
                                                        bumpHud()
                                                    }
                                            ) {
                                                Text("${ep.episode}", color = if (isCur) Color.Black else Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
                                        repeat(5 - rows[rIdx].size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }

                        PlayerOverlayPanel.NONE -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun HudDockButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val pal = LocalSwaraPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (active) pal.brandAccent.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (active) pal.brandAccent else Color.White, modifier = Modifier.size(16.dp))
        Text(label, color = if (active) pal.brandAccent else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HubStatCard(title: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pal = LocalSwaraPalette.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SwaraCard)
            .border(1.dp, pal.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(11.dp)
    ) {
        Text(title, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HubActionChip(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pal = LocalSwaraPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SwaraCard)
            .border(1.dp, pal.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(label, color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QualityRowItem(label: String, selected: Boolean, accent: Color, border: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else SwaraCard)
            .border(1.dp, if (selected) accent else border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Text(label, color = if (selected) Color.White else TextPrimary, fontSize = 13.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold)
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
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
        Text(value, color = TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DrawerSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val pal = LocalSwaraPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = pal.brandAccent)
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
            .background(Color(0x99101413))
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
