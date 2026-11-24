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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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

    val item = detail.item
    val seasons = remember(detail.episodes) { detail.episodes.map { it.season }.distinct().sorted() }
    var selectedSeason by remember(seasons) { mutableIntStateOf(seasons.firstOrNull() ?: 1) }
    var episodeQuery by remember(item.id, selectedSeason) { mutableStateOf("") }
    var reverseOrder by remember(item.id, selectedSeason) { mutableStateOf(false) }
    var selectedRangeIndex by remember(item.id, selectedSeason) { mutableIntStateOf(0) }
    var showTrailerDialog by remember(item.id) { mutableStateOf(false) }

    val seasonEpisodes = remember(detail.episodes, selectedSeason) {
        detail.episodes.filter { it.season == selectedSeason }
    }
    val orderedEpisodes = remember(seasonEpisodes, reverseOrder) {
        if (reverseOrder) seasonEpisodes.reversed() else seasonEpisodes
    }
    val rangeSize = 50
    val totalRanges = ((orderedEpisodes.size + rangeSize - 1) / rangeSize).coerceAtLeast(1)

    val visibleEpisodes = remember(orderedEpisodes, episodeQuery, selectedRangeIndex) {
        val q = episodeQuery.trim()
        if (q.isNotBlank()) {
            orderedEpisodes.filter { ep ->
                ep.episode.toString().contains(q) || ep.title.contains(q, ignoreCase = true)
            }
        } else if (orderedEpisodes.size > rangeSize) {
            val safeIdx = selectedRangeIndex.coerceIn(0, totalRanges - 1)
            orderedEpisodes.drop(safeIdx * rangeSize).take(rangeSize)
        } else {
            orderedEpisodes
        }
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

    LazyColumn(contentPadding = PaddingValues(bottom = 44.dp), modifier = Modifier.fillMaxSize().background(SwaraBlack)) {
        item(key = "backdrop") {
            Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                AsyncImage(
                    model = item.backdropUrl.ifBlank { item.posterUrl },
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), SwaraBlack))))

                Column(modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    val seasonSummary = if (seasons.isNotEmpty()) "${seasons.size} Musim • ${detail.episodes.size} Episode" else null
                    Text(
                        text = listOfNotNull(
                            item.category.label,
                            item.year.takeIf { it.isNotBlank() },
                            detail.runtime.takeIf { it.isNotBlank() },
                            item.rating.takeIf { it.isNotBlank() }?.let { "★ $it" },
                            seasonSummary
                        ).joinToString(" • "),
                        color = Color(0xFFD8D8E0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(item.title, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }

                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).clip(CircleShape).background(Color(0xAA000000))
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary) }
            }
        }

        // Single clean Trailer button next to Putar Sekarang (zero duplicate Trailer pill!)
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                Button(
                    onClick = { onPlay(item, selectedSeason, seasonEpisodes.firstOrNull()?.episode ?: 1, detail.episodes, detail.similar) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Putar Sekarang", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { showTrailerDialog = true },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.OndemandVideo, contentDescription = "Trailer", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Trailer", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { onToggleFavorite(item) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Koleksi", tint = TextPrimary)
                }
            }
        }

        if (item.genres.isNotEmpty()) {
            item(key = "genres") {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(item.genres, key = { "dg_$it" }) { g ->
                        Box(modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(SwaraCard).padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Text(g, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        if (item.overview.isNotBlank()) {
            item(key = "overview") {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text("Sinopsis", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(item.overview, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }

        if (detail.cast.isNotEmpty()) {
            item(key = "cast") {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Aktor & Aktris", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(detail.cast, key = { "cast_${it.name}" }) { actor ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(SwaraCard).clickable { onActorClick(actor) }.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp).clip(CircleShape).background(SwaraCardElevated)) {
                                    if (actor.photoUrl.isNotBlank()) {
                                        AsyncImage(model = actor.photoUrl, contentDescription = actor.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(actor.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                    ) {
                        Text("Episode (${seasonEpisodes.size})", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (seasonEpisodes.size > 12) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SwaraCard)
                                    .clickable { reverseOrder = !reverseOrder; selectedRangeIndex = 0 }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
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
                                        .clickable { selectedSeason = s; selectedRangeIndex = 0; episodeQuery = "" }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Text("Musim $s", color = if (active) Color.Black else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
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
                                if (episodeQuery.isNotBlank()) {
                                    IconButton(onClick = { episodeQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Hapus", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SwaraAccent, unfocusedBorderColor = SwaraSubtleBorder,
                                focusedContainerColor = SwaraCard, unfocusedContainerColor = SwaraCard,
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                            ),
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
                                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                        .background(if (active) Color.White else SwaraCard)
                                        .border(0.5.dp, if (active) Color.White else SwaraSubtleBorder, RoundedCornerShape(14.dp))
                                        .clickable { selectedRangeIndex = idx }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("$firstNum–$lastNum", color = if (active) Color.Black else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (epRows.isEmpty()) {
                item(key = "ep_empty") {
                    Text(
                        "Episode $episodeQuery tidak ditemukan.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(epRows.size, key = { "ep_row_${selectedRangeIndex}_${reverseOrder}_$it" }) { rIdx ->
                    val rowEps = epRows[rIdx]
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
                        rowEps.forEach { ep ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp))
                                    .background(SwaraCard).border(0.5.dp, SwaraSubtleBorder, RoundedCornerShape(12.dp))
                                    .clickable { onPlay(item, ep.season, ep.episode, detail.episodes, detail.similar) }
                            ) { Text("${ep.episode}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        }
                        repeat(6 - rowEps.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (detail.similar.isNotEmpty()) {
            item(key = "similar_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 18.dp)) {
                    Text("Tontonan Serupa", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(detail.similar, key = { "sim_${it.id}" }) { sim ->
                            MediaPosterCard(item = sim, onClick = { onSelectSimilar(sim) })
                        }
                    }
                }
            }
        }
    }
}
