package com.swara.play.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.play.model.MediaItem
import com.swara.play.model.WatchProgress
import com.swara.play.ui.components.ContinueWatchingCard
import com.swara.play.ui.components.MediaPosterCard
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
fun LibraryScreen(
    resumeList: List<WatchProgress>,
    favorites: List<MediaItem>,
    history: List<MediaItem>,
    isWideLayout: Boolean,
    onMediaClick: (MediaItem) -> Unit,
    onResumeClick: (WatchProgress) -> Unit,
    onClearHistory: () -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Lanjut Nonton", "Koleksi Saya", "Riwayat")

    Column(modifier = Modifier.fillMaxSize().background(SwaraBlack).statusBarsPadding().padding(top = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Text("Pustaka Saya", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (tabIndex == 2 && history.isNotEmpty()) {
                TextButton(onClick = onClearHistory) { Text("Hapus Riwayat", color = TextPrimary, fontSize = 12.sp) }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = SwaraCard,
            contentColor = TextPrimary,
            indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(modifier = Modifier.tabIndicatorOffset(tabPositions[tabIndex]), color = SwaraAccent) }
        ) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = tabIndex == idx,
                    onClick = { tabIndex = idx },
                    text = { Text(label, color = if (tabIndex == idx) TextPrimary else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        when (tabIndex) {
            0 -> {
                if (resumeList.isEmpty()) EmptyState("Belum ada tontonan yang dijeda.")
                else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(resumeList, key = { "lib_res_${it.mediaId}_${it.season}_${it.episode}" }) { p ->
                        ContinueWatchingCard(progress = p, onClick = { onResumeClick(p) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            1 -> {
                if (favorites.isEmpty()) EmptyState("Koleksi favorit masih kosong.")
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isWideLayout) 6 else 3),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(favorites, key = { "fav_${it.id}" }) { item -> MediaPosterCard(item = item, onClick = { onMediaClick(item) }) }
                }
            }
            else -> {
                if (history.isEmpty()) EmptyState("Belum ada riwayat tontonan.")
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isWideLayout) 6 else 3),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(history, key = { "hist_${it.id}" }) { item -> MediaPosterCard(item = item, onClick = { onMediaClick(item) }) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Text(text = message, color = TextSecondary, fontSize = 14.sp) }
}
