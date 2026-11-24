package com.swara.play.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.player.OfflineExoPlayerView
import com.swara.play.player.StreamWebPlayer
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraCardElevated
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.viewmodel.PlayerUiState
import kotlinx.coroutines.delay
import java.util.Locale

private tailrec fun Context.findPlayerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPlayerActivity()
    else -> null
}

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    preferredLangIso: String,
    onBack: () -> Unit,
    onSelectEpisode: (MediaItem, Int, Int) -> Unit,
    onSelectSimilar: (MediaItem) -> Unit,
    onMediaUrlSniffed: (String) -> Unit,
    onDownloadClick: () -> Unit,
    onToggleSubtitle: () -> Unit,
    onAdjustSubtitleOffset: (Float) -> Unit,
    onResetSubtitleOffset: () -> Unit,
    onSilentFallback: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findPlayerActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var reloadToken by remember { mutableIntStateOf(0) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var showHudControls by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
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

    LaunchedEffect(gestureHudLabel) {
        if (gestureHudLabel.isNotBlank()) {
            delay(1300)
            gestureHudLabel = ""
        }
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
    }

    fun applyVolume(newVal: Float) {
        val clamped = newVal.coerceIn(0f, 1f)
        volumeLevel = clamped
        runCatching {
            val volInt = (clamped * maxVolume).toInt().coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volInt, 0)
        }
        gestureHudLabel = "Suara ${(clamped * 100).toInt()}%"
    }

    fun dispatchControl(cmd: String) {
        commandSeq += 1
        commandPayload = cmd
    }

    val item = state.mediaItem
    val activeSource = state.activeSource
    val offlineItem = state.offlineItem
    val hasEpisodes = state.episodes.isNotEmpty()
    var panelTab by remember(hasEpisodes) { mutableIntStateOf(if (hasEpisodes) 0 else 1) }

    val nextEpisode = remember(state.episodes, state.season, state.episode) {
        val idx = state.episodes.indexOfFirst { it.season == state.season && it.episode == state.episode }
        if (idx >= 0 && idx + 1 < state.episodes.size) state.episodes[idx + 1] else null
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(SwaraBlack).statusBarsPadding()) {
        val isSplitLandscape = maxWidth >= 600.dp

        @Composable
        fun VideoStage(modifier: Modifier) {
            Box(contentAlignment = Alignment.Center, modifier = modifier.background(Color.Black)) {
                if (offlineItem != null) {
                    OfflineExoPlayerView(
                        localVideoPath = offlineItem.localVideoPath,
                        localSubtitlePath = offlineItem.localSubtitlePath
                    )
                } else if (state.isLoading || activeSource == null) {
                    CircularProgressIndicator(color = SwaraAccent)
                } else {
                    StreamWebPlayer(
                        embedUrl = activeSource.url,
                        referer = activeSource.referer,
                        reloadToken = reloadToken,
                        preferredLangIso = preferredLangIso,
                        subtitleEnabled = state.subtitleEnabled,
                        subtitleOffsetSec = state.subtitleOffsetSec,
                        synchronizedVtt = state.synchronizedVttContent,
                        playbackSpeed = playbackSpeed,
                        videoFillMode = videoFillMode,
                        controlCommand = commandSeq to commandPayload,
                        onMediaUrlSniffed = onMediaUrlSniffed,
                        onSilentFallbackNeeded = onSilentFallback
                    )
                }

                // Left-edge vertical drag zone for Brightness (narrow edge so center clicks pass to player)
                if (!isScreenLocked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight(0.75f)
                            .width(42.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    applyBrightness(brightnessLevel - dragAmount / 380f)
                                }
                            }
                    )
                    // Right-edge vertical drag zone for Volume
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(0.75f)
                            .width(42.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    applyVolume(volumeLevel - dragAmount / 380f)
                                }
                            }
                    )
                }

                // Screen Lock full interceptor
                if (isScreenLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(enabled = true) { gestureHudLabel = "Layar Terkunci — Tekan Gembok untuk Buka" }
                    )
                }

                // Floating Gesture Indicator Pill (Brightness / Volume / Lock)
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

                // Compact Floating Lock + Controls Trigger (Top-Right inside video stage)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isScreenLocked) Color.White else Color(0xBB141418))
                            .clickable {
                                isScreenLocked = !isScreenLocked
                                if (isScreenLocked) showHudControls = false
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
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (showHudControls) Color.White else Color(0xBB141418))
                                .clickable { showHudControls = !showHudControls }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Kontrol Pemutar",
                                tint = if (showHudControls) Color.Black else TextPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                // Expandable Native HUD Controls Drawer (Brightness, Volume, 1-Click Subtitle + Sync Offset, Skip, Speed, Ratio)
                AnimatedVisibility(
                    visible = showHudControls && !isScreenLocked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xE6111115))
                            .border(0.75.dp, SwaraSubtleBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        // Row 1: Brightness & Volume Sliders
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Brightness6, contentDescription = "Kecerahan", tint = TextPrimary, modifier = Modifier.size(16.dp))
                                Slider(
                                    value = brightnessLevel,
                                    onValueChange = { applyBrightness(it) },
                                    valueRange = 0.05f..1f,
                                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
                                    modifier = Modifier.weight(1f).height(24.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Suara", tint = TextPrimary, modifier = Modifier.size(16.dp))
                                Slider(
                                    value = volumeLevel,
                                    onValueChange = { applyVolume(it) },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
                                    modifier = Modifier.weight(1f).height(24.dp)
                                )
                            }
                        }

                        // Row 2: 1-Click Subtitle Switch + Live Sync Offset + Quick Controls
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            item {
                                HudChip(
                                    label = if (state.subtitleEnabled) "Subtitle: Aktif" else "Subtitle: Nonaktif",
                                    icon = if (state.subtitleEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                                    active = state.subtitleEnabled,
                                    onClick = onToggleSubtitle
                                )
                            }
                            if (state.subtitleEnabled) {
                                item {
                                    HudChip(label = "-0.5d", active = false, onClick = { onAdjustSubtitleOffset(-0.5f) })
                                }
                                item {
                                    val offStr = String.format(Locale.US, "%+.1fd", state.subtitleOffsetSec)
                                    HudChip(label = if (state.subtitleOffsetSec == 0f) "Sinkron" else offStr, active = state.subtitleOffsetSec != 0f, onClick = onResetSubtitleOffset)
                                }
                                item {
                                    HudChip(label = "+0.5d", active = false, onClick = { onAdjustSubtitleOffset(0.5f) })
                                }
                            }
                            item {
                                HudChip(label = "-10d", icon = Icons.Default.Replay10, active = false, onClick = { dispatchControl("skip:-10") })
                            }
                            item {
                                HudChip(label = "+10d", icon = Icons.Default.Forward10, active = false, onClick = { dispatchControl("skip:10") })
                            }
                            item {
                                HudChip(
                                    label = "${playbackSpeed}x",
                                    icon = Icons.Default.Speed,
                                    active = playbackSpeed != 1.0f,
                                    onClick = {
                                        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                                        playbackSpeed = speeds[(speeds.indexOf(playbackSpeed) + 1) % speeds.size]
                                    }
                                )
                            }
                            item {
                                HudChip(
                                    label = if (videoFillMode) "Penuh" else "Pas",
                                    icon = Icons.Default.AspectRatio,
                                    active = videoFillMode,
                                    onClick = { videoFillMode = !videoFillMode }
                                )
                            }
                            if (nextEpisode != null && item != null) {
                                item {
                                    HudChip(
                                        label = "Eps ${nextEpisode.episode}",
                                        icon = Icons.Default.SkipNext,
                                        active = false,
                                        onClick = { onSelectEpisode(item, nextEpisode.season, nextEpisode.episode) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun SideTabPanel(modifier: Modifier) {
            val seasons = remember(state.episodes) { state.episodes.map { it.season }.distinct().sorted() }
            var viewingSeason by remember(state.season, seasons) { mutableIntStateOf(state.season) }
            val seasonEpisodes = remember(state.episodes, viewingSeason) { state.episodes.filter { it.season == viewingSeason } }
            val rangeCount = ((seasonEpisodes.size + 49) / 50).coerceAtLeast(1)
            val initialRange = remember(state.episode, viewingSeason) { ((state.episode - 1) / 50).coerceIn(0, rangeCount - 1) }
            var activeRange by remember(viewingSeason, rangeCount) { mutableIntStateOf(initialRange) }
            val pagedEpisodes = remember(seasonEpisodes, activeRange) {
                if (seasonEpisodes.size <= 50) seasonEpisodes
                else seasonEpisodes.drop(activeRange * 50).take(50)
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

                TabRow(
                    selectedTabIndex = panelTab,
                    containerColor = SwaraCard,
                    contentColor = TextPrimary,
                    indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(modifier = Modifier.tabIndicatorOffset(tabPositions[panelTab]), color = SwaraAccent) }
                ) {
                    Tab(
                        selected = panelTab == 0,
                        onClick = { panelTab = 0 },
                        text = { Text("Episode", color = if (panelTab == 0) TextPrimary else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = panelTab == 1,
                        onClick = { panelTab = 1 },
                        text = { Text("Mirip", color = if (panelTab == 1) TextPrimary else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
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
                                            ) {
                                                Text("Musim $s", color = if (active) Color.Black else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
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
                                            ) {
                                                Text("$start–$end", color = if (active) Color.Black else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
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
                                        ) {
                                            Text("${ep.episode}", color = if (isCurrent) Color.Black else TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                    repeat(5 - epRows[rIdx].size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        } else if (item != null) {
                            item(key = "movie_info") {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(item.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    if (offlineItem != null) {
                                        Text("Memutar dari penyimpanan offline", color = TextSecondary, fontSize = 12.sp)
                                    } else if (item.overview.isNotBlank()) {
                                        Text(item.overview, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        if (state.similar.isEmpty()) {
                            item(key = "no_sim") { Text("Belum ada rekomendasi serupa.", color = TextSecondary, fontSize = 13.sp) }
                        } else {
                            items(state.similar, key = { "psim_${it.id}" }) { sim ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SwaraCard)
                                        .clickable { onSelectSimilar(sim) }.padding(8.dp)
                                ) {
                                    AsyncImage(
                                        model = sim.posterUrl,
                                        contentDescription = sim.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.width(56.dp).height(78.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sim.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = listOfNotNull(sim.category.label, sim.year.takeIf { it.isNotBlank() }, sim.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }).joinToString(" • "),
                                            color = TextSecondary, fontSize = 11.sp
                                        )
                                    }
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
                modifier = Modifier.fillMaxWidth().background(SwaraCard).padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(item?.title.orEmpty(), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item != null && item.category != MediaCategory.MOVIE) {
                        Text("Musim ${state.season} • Episode ${state.episode}", color = TextSecondary, fontSize = 11.sp)
                    }
                }
                if (offlineItem == null) {
                    IconButton(onClick = onToggleSubtitle) {
                        Icon(
                            imageVector = if (state.subtitleEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                            contentDescription = "Subtitle Aktif/Nonaktif",
                            tint = if (state.subtitleEnabled) Color.White else TextSecondary
                        )
                    }
                    IconButton(onClick = onDownloadClick, enabled = !state.isDownloadingMedia) {
                        Icon(Icons.Default.Download, contentDescription = "Unduh Offline", tint = TextPrimary)
                    }
                    IconButton(onClick = { reloadToken += 1 }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang", tint = TextPrimary)
                    }
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
private fun HudChip(
    label: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color.White else Color(0xFF222228))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (active) Color.Black else TextPrimary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(label, color = if (active) Color.Black else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
