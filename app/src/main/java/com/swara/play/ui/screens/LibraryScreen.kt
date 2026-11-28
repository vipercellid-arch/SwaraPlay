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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.play.model.MediaItem
import com.swara.play.model.OfflineDownloadItem
import com.swara.play.ui.components.MediaPosterCard
import com.swara.play.ui.theme.LocalSwaraPalette
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
fun LibraryScreen(
    favorites: List<MediaItem>,
    history: List<MediaItem>,
    offlineDownloads: List<OfflineDownloadItem>,
    isTv: Boolean,
    initialTab: Int = 0,
    onMediaClick: (MediaItem) -> Unit,
    onPlayOffline: (OfflineDownloadItem) -> Unit,
    onDeleteOffline: (OfflineDownloadItem) -> Unit
) {
    val pal = LocalSwaraPalette.current
    val hPad = if (isTv) 48.dp else 16.dp
    var tab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val tabs = listOf("Daftar Saya (${favorites.size})", "Riwayat (${history.size})", "Offline (${offlineDownloads.size})")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(pal.glowPrimary.copy(alpha = 0.28f), pal.bg, pal.bg)))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = if (isTv) 86.dp else 94.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = hPad),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                tabs.forEachIndexed { idx, label ->
                    val active = tab == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (active) Color.White else pal.surface)
                            .border(1.dp, if (active) Color.White else pal.border, RoundedCornerShape(99.dp))
                            .clickable { tab = idx }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(label, color = if (active) Color.Black else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            when (tab) {
                0 -> {
                    if (favorites.isEmpty()) {
                        EmptyListState("Daftar Saya masih kosong", "Tekan tombol + pada film atau serial untuk menyimpannya di sini.")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = if (isTv) 150.dp else 112.dp),
                            contentPadding = PaddingValues(start = hPad, end = hPad, bottom = 40.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(favorites, key = { "fav_${it.id}" }) { item ->
                                MediaPosterCard(item = item, fillWidth = true, onClick = { onMediaClick(item) })
                            }
                        }
                    }
                }
                1 -> {
                    if (history.isEmpty()) {
                        EmptyListState("Belum ada riwayat tontonan", "Film dan serial yang kamu putar akan tercatat di sini.")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = if (isTv) 150.dp else 112.dp),
                            contentPadding = PaddingValues(start = hPad, end = hPad, bottom = 40.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(history, key = { "hist_${it.id}" }) { item ->
                                MediaPosterCard(item = item, fillWidth = true, onClick = { onMediaClick(item) })
                            }
                        }
                    }
                }
                else -> {
                    if (offlineDownloads.isEmpty()) {
                        EmptyListState("Belum ada unduhan offline", "Unduh film atau episode dari pemutar video untuk ditonton tanpa internet.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = hPad, end = hPad, bottom = 40.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(offlineDownloads, key = { "off_${it.id}" }) { dl ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(pal.surface)
                                        .border(1.dp, pal.border, RoundedCornerShape(14.dp))
                                        .clickable { onPlayOffline(dl) }
                                        .padding(14.dp)
                                ) {
                                    Icon(Icons.Default.OfflinePin, contentDescription = null, tint = pal.brandAccent, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.size(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(dl.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("S${dl.season} E${dl.episode}", color = TextSecondary, fontSize = 12.sp)
                                    }
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Hapus",
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(22.dp).clickable { onDeleteOffline(dl) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyListState(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextSecondary, fontSize = 13.5.sp)
        }
    }
}
