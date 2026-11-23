package com.swara.play.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.player.StreamWebPlayer
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.viewmodel.PlayerUiState

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    preferredLangIso: String,
    onBack: () -> Unit,
    onSelectEpisode: (MediaItem, Int, Int) -> Unit,
    onSelectSimilar: (MediaItem) -> Unit,
    onSilentFallback: () -> Unit
) {
    var reloadToken by remember { mutableIntStateOf(0) }
    val item = state.mediaItem
    val activeSource = state.activeSource
    val hasEpisodes = state.episodes.isNotEmpty()
    var panelTab by remember(hasEpisodes) { mutableIntStateOf(if (hasEpisodes) 0 else 1) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(SwaraBlack).statusBarsPadding()) {
        val isSplitLandscape = maxWidth >= 600.dp

        @Composable
        fun VideoStage(modifier: Modifier) {
            Box(contentAlignment = Alignment.Center, modifier = modifier.background(Color.Black)) {
                if (state.isLoading || activeSource == null) {
                    CircularProgressIndicator(color = SwaraAccent)
                } else {
                    StreamWebPlayer(
                        embedUrl = activeSource.url,
                        referer = activeSource.referer,
                        reloadToken = reloadToken,
                        preferredLangIso = preferredLangIso,
                        onSilentFallbackNeeded = onSilentFallback
                    )
                }
            }
        }

        @Composable
        fun SideTabPanel(modifier: Modifier) {
            val seasons = remember(state.episodes) { state.episodes.map { it.season }.distinct().sorted() }
            var viewingSeason by remember(state.season, seasons) { mutableIntStateOf(state.season) }
            val seasonEpisodes = remember(state.episodes, viewingSeason) { state.episodes.filter { it.season == viewingSeason } }
            val epRows = remember(seasonEpisodes) { seasonEpisodes.chunked(5) }

            Column(modifier = modifier.background(SwaraBlack)) {
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                                    .clickable { viewingSeason = s }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("Musim $s", color = if (active) Color.Black else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            items(epRows.size, key = { "pe_$it" }) { rIdx ->
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
                                    if (item.overview.isNotBlank()) {
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
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SwaraCard)
                                        .clickable { onSelectSimilar(sim) }
                                        .padding(8.dp)
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
                                            color = TextSecondary,
                                            fontSize = 11.sp
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
                IconButton(onClick = { reloadToken += 1 }) { Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang", tint = TextPrimary) }
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
