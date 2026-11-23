package com.swara.play.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.StreamKind
import com.swara.play.model.StreamServer
import com.swara.play.model.SubtitleTrack
import com.swara.play.player.AdBlockWebPlayer
import com.swara.play.player.ExoPlayerView
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraCardElevated
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.viewmodel.PlayerUiState

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    adBlockEnabled: Boolean,
    onBack: () -> Unit,
    onSelectEpisode: (MediaItem, Int, Int) -> Unit,
    onSelectServer: (StreamServer) -> Unit,
    onAutoFallback: () -> Unit,
    onSelectSubtitle: (SubtitleTrack?) -> Unit,
    onProgressUpdate: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    var blockedAdsCount by remember { mutableIntStateOf(0) }
    var reloadToken by remember { mutableIntStateOf(0) }
    val item = state.mediaItem
    val activeServer = state.activeServer

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(SwaraBlack)) {
        val isSplitLandscape = maxWidth >= 600.dp

        @Composable
        fun VideoStage(modifier: Modifier) {
            Box(contentAlignment = Alignment.Center, modifier = modifier.background(Color.Black)) {
                if (state.isLoading || activeServer == null) {
                    CircularProgressIndicator(color = SwaraAccent)
                } else if (activeServer.kind == StreamKind.DIRECT_EXO) {
                    ExoPlayerView(
                        streamUrl = activeServer.url,
                        subtitleTrack = state.activeSubtitle,
                        startPositionMs = state.startPositionMs,
                        onProgressUpdate = onProgressUpdate
                    )
                } else {
                    AdBlockWebPlayer(
                        embedUrl = activeServer.url,
                        referer = activeServer.referer,
                        reloadToken = reloadToken,
                        adBlockEnabled = adBlockEnabled,
                        onBlockedCount = { count -> blockedAdsCount = count },
                        onServerErrorDetected = onAutoFallback
                    )
                }
            }
        }

        @Composable
        fun ControlSidePanel(modifier: Modifier) {
            val seasonEpisodes = remember(state.episodes, state.season) {
                state.episodes.filter { it.season == state.season }
            }
            val epRows = remember(seasonEpisodes) { seasonEpisodes.chunked(5) }

            LazyColumn(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = modifier.background(SwaraBlack)
            ) {
                if (item != null) {
                    item(key = "p_title") {
                        Column {
                            Text(item.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            if (item.category != MediaCategory.MOVIE) {
                                Text("Musim ${state.season} • Episode ${state.episode}", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (!state.fallbackNotice.isNullOrBlank()) {
                    item(key = "fallback") {
                        Text(
                            text = state.fallbackNotice,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SwaraCardElevated).padding(10.dp)
                        )
                    }
                }

                if (item != null && seasonEpisodes.isNotEmpty()) {
                    item(key = "ep_header") {
                        Text("Episode (${seasonEpisodes.size})", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    items(epRows.size, key = { "pe_$it" }) { rIdx ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            epRows[rIdx].forEach { ep ->
                                val isCurrent = ep.episode == state.episode && ep.season == state.season
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
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
                }

                item(key = "servers_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pilih Server (${state.servers.size})", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        state.servers.forEach { server ->
                            val selected = server.id == activeServer?.id
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Color.White else SwaraCard)
                                    .clickable { onSelectServer(server) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(server.name, color = if (selected) Color.Black else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(server.providerTag, color = if (selected) Color(0xFF333333) else TextSecondary, fontSize = 11.sp)
                                }
                                Text(server.quality, color = if (selected) Color.Black else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item(key = "subtitles_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ClosedCaption, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Subtitle Wyzie (${state.subtitles.size})", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            val activeSub = state.activeSubtitle
                            if (activeSub != null) {
                                IconButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("Wyzie Subtitle", activeSub.url))
                                    Toast.makeText(context, "Tautan subtitle ${activeSub.languageLabel} disalin", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Salin", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (state.subtitles.isEmpty()) {
                            Text("Subtitle bawaan tersedia di ikon CC player.", color = TextSecondary, fontSize = 12.sp)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item(key = "sub_off") {
                                    SubChip("Nonaktif", state.activeSubtitle == null) { onSelectSubtitle(null) }
                                }
                                items(state.subtitles, key = { it.id }) { sub ->
                                    SubChip(sub.languageLabel, state.activeSubtitle?.id == sub.id) { onSelectSubtitle(sub) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(SwaraCard).padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary) }
                Text(
                    text = item?.title.orEmpty(),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { reloadToken += 1 }) { Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang", tint = TextPrimary) }
                if (adBlockEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(SwaraCardElevated).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AdBlock ($blockedAdsCount)", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (isSplitLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    VideoStage(modifier = Modifier.weight(0.62f).fillMaxHeight())
                    ControlSidePanel(modifier = Modifier.weight(0.38f).fillMaxHeight())
                }
            } else {
                VideoStage(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                ControlSidePanel(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun SubChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White else SwaraCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) Color.Black else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
