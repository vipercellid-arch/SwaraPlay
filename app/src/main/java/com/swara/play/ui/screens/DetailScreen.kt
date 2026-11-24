package com.swara.play.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swara.play.model.CastMember
import com.swara.play.model.EpisodeItem
import com.swara.play.model.MediaDetail
import com.swara.play.model.MediaItem
import com.swara.play.player.TrailerPopupDialog
import com.swara.play.ui.components.MediaPosterCard
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraCardElevated
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.ui.theme.TextTertiary

@Composable
fun DetailScreen(
    detail: MediaDetail?,
    isLoading: Boolean,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onSelectSimilar: (MediaItem) -> Unit,
    onActorClick: (CastMember) -> Unit,
    onPlay: (MediaItem, Int, Int, List<EpisodeItem>, List<MediaItem>) -> Unit
) {
    if (detail == null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(SwaraBlack)) {
            CircularProgressIndicator(color = SwaraAccent)
        }
        return
    }

    val context = LocalContext.current
    val item = detail.item
    val seasons = remember(detail.episodes) { detail.episodes.map { it.season }.distinct().sorted() }
    var selectedSeason by remember(seasons) { mutableIntStateOf(seasons.firstOrNull() ?: 1) }
    var episodeQuery by remember(item.id, selectedSeason) { mutableStateOf("") }
    var reverseOrder by remember(item.id, selectedSeason) { mutableStateOf(false) }
    var selectedRangeIndex by remember(item.id, selectedSeason) { mutableIntStateOf(0) }
    var showTrailerDialog by remember(item.id) { mutableStateOf(false) }
    var expandSynopsis by remember(item.id) { mutableStateOf(false) }
    var activeTab by remember(item.id) { mutableIntStateOf(0) }

    val seasonEpisodes = remember(detail.episodes, selectedSeason) { detail.episodes.filter { it.season == selectedSeason } }
    val orderedEpisodes = remember(seasonEpisodes, reverseOrder) { if (reverseOrder) seasonEpisodes.reversed() else seasonEpisodes }
    val rangeSize = 50
    val totalRanges = ((orderedEpisodes.size + rangeSize - 1) / rangeSize).coerceAtLeast(1)

    val visibleEpisodes = remember(orderedEpisodes, episodeQuery, selectedRangeIndex) {
        val q = episodeQuery.trim()
        if (q.isNotBlank()) orderedEpisodes.filter { it.episode.toString().contains(q) || it.title.contains(q, ignoreCase = true) }
        else if (orderedEpisodes.size > rangeSize) orderedEpisodes.drop(selectedRangeIndex.coerceIn(0, totalRanges - 1) * rangeSize).take(rangeSize)
        else orderedEpisodes
    }
    val epRows = remember(visibleEpisodes) { visibleEpisodes.chunked(6) }

    if (showTrailerDialog) {
        TrailerPopupDialog(
            title = item.title,
            year = item.year,
            initialYoutubeId = detail.trailerYoutubeId.ifBlank { item.trailerYoutubeId },
            onDismiss = { showTrailerDialog = false }
        )
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), modifier = Modifier.fillMaxSize().background(SwaraBlack)) {
        item(key = "backdrop") {
            Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                AsyncImage(model = item.backdropUrl.ifBlank { item.posterUrl }, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.0f to Color(0x660B0B0F), 0.35f to Color.Transparent, 0.70f to Color(0xB30B0B0F), 1.0f to SwaraBlack)
                    )
                )

                Column(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(item.title, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = listOfNotNull(
                            item.year.take(4).takeIf { it.isNotBlank() },
                            detail.runtime.takeIf { it.isNotBlank() },
                            item.genres.take(2).joinToString(", ").takeIf { it.isNotBlank() }
                        ).joinToString("  •  "),
                        color = TextSecondary, fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.isAdult18) DetailBadge("18+", Color(0xFFE91E63), Color.White)
                        if (item.isLgbt) DetailBadge("LGBT", Color(0xFFE91E63), Color.White)
                        DetailBadge("HD", SwaraCardElevated, TextPrimary)
                        if (item.rating.isNotBlank()) DetailBadge("IMDb ${item.rating}", SwaraCardElevated, TextPrimary)
                        if (seasons.isNotEmpty()) DetailBadge("${seasons.size} Musim • ${detail.episodes.size} Eps", SwaraCardElevated, TextPrimary)
                    }
                }

                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).clip(CircleShape).background(Color(0x9914141A)).border(0.5.dp, SwaraSubtleBorder, CircleShape)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary) }
            }
        }

        // Euthopiar Full-Width White Putar Button + Glass Square Quick Action Tiles
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Button(
                    onClick = { onPlay(item, selectedSeason, seasonEpisodes.firstOrNull()?.episode ?: 1, detail.episodes, detail.similar) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0B0B0F)),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Putar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    GlassQuickActionTile(
                        icon = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        label = if (isFavorite) "Tersimpan" else "Koleksi",
                        onClick = { onToggleFavorite(item) }
                    )
                    GlassQuickActionTile(
                        icon = Icons.Default.Movie,
                        label = "Trailer",
                        onClick = { showTrailerDialog = true }
                    )
                    GlassQuickActionTile(
                        icon = Icons.Default.Share,
                        label = "Bagikan",
                        onClick = {
                            runCatching {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Tonton ${item.title} di SwaraPlay!")
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Bagikan"))
                            }
                        }
                    )
                }
            }
        }

        if (item.overview.isNotBlank()) {
            item(key = "overview") {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(
                        text = if (expandSynopsis || item.overview.length <= 210) item.overview else item.overview.take(210) + "...",
                        color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp
                    )
                    if (item.overview.length > 210) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (expandSynopsis) "Ringkas" else "Selengkapnya",
                            color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { expandSynopsis = !expandSynopsis }
                        )
                    }
                }
            }
        }

        // Euthopiar Clean Underline Tabs: Detail & Pemeran | Serupa
        item(key = "detail_tabs") {
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = SwaraBlack,
                contentColor = TextPrimary,
                indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]), color = Color.White, height = 2.dp) },
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Detail & Pemeran", color = if (activeTab == 0) TextPrimary else TextTertiary, fontSize = 14.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Tontonan Serupa", color = if (activeTab == 1) TextPrimary else TextTertiary, fontSize = 14.sp, fontWeight = FontWeight.Bold) })
            }
        }

        if (activeTab == 0) {
            if (detail.cast.isNotEmpty()) {
                item(key = "cast") {
                    Column(modifier = Modifier.padding(vertical = 10.dp)) {
                        Text("Pemeran & Kru", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(detail.cast, key = { "cast_${it.name}" }) { actor ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(74.dp).clickable { onActorClick(actor) }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp).clip(CircleShape).background(SwaraCardElevated).border(0.5.dp, SwaraSubtleBorder, CircleShape)) {
                                        if (actor.photoUrl.isNotBlank()) {
                                            AsyncImage(model = actor.photoUrl, contentDescription = actor.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        } else {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(actor.name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                item(key = "detail_loading") {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        CircularProgressIndicator(color = SwaraAccent, modifier = Modifier.size(28.dp))
                    }
                }
            }

            if (seasons.isNotEmpty()) {
                item(key = "seasons_and_search") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                            Text("Episode (${seasonEpisodes.size})", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            if (seasonEpisodes.size > 12) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(SwaraCard).clickable { reverseOrder = !reverseOrder; selectedRangeIndex = 0 }.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(Icons.Default.SwapVert, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (reverseOrder) "Terbaru" else "Terlama", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (seasons.size > 1) {
                            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(seasons, key = { "s_$it" }) { s ->
                                    val active = selectedSeason == s
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (active) Color.White else SwaraCard)
                                            .clickable { selectedSeason = s; selectedRangeIndex = 0; episodeQuery = "" }.padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) { Text("Musim $s", color = if (active) Color.Black else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                        if (seasonEpisodes.size > 20) {
                            OutlinedTextField(
                                value = episodeQuery,
                                onValueChange = { episodeQuery = it },
                                placeholder = { Text("Cari nomor episode (misal: 1050)...", color = TextSecondary, fontSize = 13.sp) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (episodeQuery.isNotBlank()) IconButton(onClick = { episodeQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "Hapus", tint = TextSecondary, modifier = Modifier.size(18.dp)) }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White, unfocusedBorderColor = SwaraSubtleBorder, focusedContainerColor = SwaraCard, unfocusedContainerColor = SwaraCard, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                            )
                        }
                        if (episodeQuery.isBlank() && totalRanges > 1) {
                            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(totalRanges, key = { "range_$it" }) { idx ->
                                    val slice = orderedEpisodes.drop(idx * rangeSize).take(rangeSize)
                                    val firstNum = slice.firstOrNull()?.episode ?: 1
                                    val lastNum = slice.lastOrNull()?.episode ?: firstNum
                                    val active = selectedRangeIndex == idx
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (active) Color.White else SwaraCard)
                                            .border(0.5.dp, if (active) Color.White else SwaraSubtleBorder, RoundedCornerShape(12.dp))
                                            .clickable { selectedRangeIndex = idx }.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) { Text("$firstNum–$lastNum", color = if (active) Color.Black else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }
                if (epRows.isEmpty()) {
                    item(key = "ep_empty") {
                        Text("Episode $episodeQuery tidak ditemukan.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
                    }
                } else {
                    items(epRows.size, key = { "ep_row_${selectedRangeIndex}_${reverseOrder}_$it" }) { rIdx ->
                        val rowEps = epRows[rIdx]
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
                            rowEps.forEach { ep ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp)).background(SwaraCard).border(0.5.dp, SwaraSubtleBorder, RoundedCornerShape(12.dp))
                                        .clickable { onPlay(item, ep.season, ep.episode, detail.episodes, detail.similar) }
                                ) { Text("${ep.episode}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                            }
                            repeat(6 - rowEps.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
        } else {
            if (detail.similar.isEmpty()) {
                item(key = "sim_empty") { Text("Belum ada tontonan serupa.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(18.dp)) }
            } else {
                val simRows = detail.similar.chunked(3)
                items(simRows.size, key = { "sim_r_$it" }) { idx ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                        simRows[idx].forEach { sim -> MediaPosterCard(item = sim, onClick = { onSelectSimilar(sim) }, modifier = Modifier.weight(1f)) }
                        repeat(3 - simRows[idx].size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailBadge(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).border(0.5.dp, SwaraSubtleBorder, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
    ) { Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun GlassQuickActionTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(SwaraCard).border(0.5.dp, SwaraSubtleBorder, RoundedCornerShape(16.dp))
        ) {
            Icon(icon, contentDescription = label, tint = TextPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
