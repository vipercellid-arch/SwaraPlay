package com.swara.play.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.play.model.MediaCategory
import com.swara.play.model.StreamKind
import com.swara.play.model.StreamServer
import com.swara.play.model.SubtitleTrack
import com.swara.play.player.AdBlockWebPlayer
import com.swara.play.player.ExoPlayerView
import com.swara.play.ui.theme.CinemaCrimson
import com.swara.play.ui.theme.OledBlack
import com.swara.play.ui.theme.OledBorder
import com.swara.play.ui.theme.OledSurface
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.viewmodel.PlayerUiState

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    adBlockEnabled: Boolean,
    onBack: () -> Unit,
    onSelectServer: (StreamServer) -> Unit,
    onSelectSubtitle: (SubtitleTrack?) -> Unit,
    onProgressUpdate: (Long, Long) -> Unit
) {
    var blockedAdsCount by remember { mutableIntStateOf(0) }
    val item = state.mediaItem
    val activeServer = state.activeServer

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(OledSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item?.title.orEmpty(),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item != null && item.category != MediaCategory.MOVIE) {
                    Text(
                        text = "Season ${state.season} • Episode ${state.episode}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            if (adBlockEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x26E11D48))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = CinemaCrimson, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AdBlock ($blockedAdsCount)", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            if (state.isLoading || activeServer == null) {
                CircularProgressIndicator(color = CinemaCrimson)
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
                    adBlockEnabled = adBlockEnabled,
                    onBlockedCount = { count -> blockedAdsCount = count }
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "servers_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = CinemaCrimson, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pilih Server Streaming", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    state.servers.forEach { server ->
                        val selected = server.id == activeServer?.id
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Color(0x33E11D48) else OledSurface)
                                .border(
                                    1.dp,
                                    if (selected) CinemaCrimson else OledBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectServer(server) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(server.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(server.providerTag, color = TextSecondary, fontSize = 11.sp)
                            }
                            Text(
                                text = server.quality,
                                color = CinemaCrimson,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item(key = "subtitles_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ClosedCaption, contentDescription = null, tint = CinemaCrimson, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Subtitle Wyzie (${state.subtitles.size})", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    if (state.subtitles.isEmpty()) {
                        Text(
                            text = "Subtitle otomatis tersedia di dalam player atau belum ditemukan di Wyzie untuk judul ini.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item(key = "sub_off") {
                                FilterChip(
                                    selected = state.activeSubtitle == null,
                                    onClick = { onSelectSubtitle(null) },
                                    label = { Text("Nonaktif", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CinemaCrimson,
                                        selectedLabelColor = TextPrimary,
                                        containerColor = OledSurface,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                            items(state.subtitles, key = { it.id }) { sub ->
                                FilterChip(
                                    selected = state.activeSubtitle?.id == sub.id,
                                    onClick = { onSelectSubtitle(sub) },
                                    label = { Text(sub.languageLabel, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CinemaCrimson,
                                        selectedLabelColor = TextPrimary,
                                        containerColor = OledSurface,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
