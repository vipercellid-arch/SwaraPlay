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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swara.play.model.EpisodeItem
import com.swara.play.model.MediaDetail
import com.swara.play.model.MediaItem
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraCard
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
    onPlay: (MediaItem, Int, Int, List<EpisodeItem>) -> Unit
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
    val seasonEpisodes = remember(detail.episodes, selectedSeason) { detail.episodes.filter { it.season == selectedSeason } }
    val epRows = remember(seasonEpisodes) { seasonEpisodes.chunked(6) }

    LazyColumn(contentPadding = PaddingValues(bottom = 36.dp), modifier = Modifier.fillMaxSize().background(SwaraBlack)) {
        item(key = "backdrop") {
            Box(modifier = Modifier.fillMaxWidth().height(290.dp)) {
                AsyncImage(model = item.backdropUrl.ifBlank { item.posterUrl }, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x55000000), SwaraBlack))))
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xAA000000))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
                }
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(
                        text = listOfNotNull(
                            item.category.label,
                            item.year.takeIf { it.isNotBlank() },
                            detail.runtime.takeIf { it.isNotBlank() },
                            item.rating.takeIf { it.isNotBlank() }?.let { "★ $it" },
                            detail.episodes.size.takeIf { it > 0 }?.let { "$it Episode" }
                        ).joinToString(" • "),
                        color = Color(0xFFD4D4DC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(item.title, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Button(
                    onClick = { onPlay(item, selectedSeason, seasonEpisodes.firstOrNull()?.episode ?: 1, detail.episodes) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Putar Sekarang", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { onToggleFavorite(item) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Koleksi",
                        tint = TextPrimary
                    )
                }
            }
        }

        if (item.overview.isNotBlank()) {
            item(key = "overview") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("Sinopsis", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(item.overview, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
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
            item(key = "seasons") {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text("Episode (${seasonEpisodes.size})", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons, key = { "s_$it" }) { s ->
                            val active = selectedSeason == s
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (active) Color.White else SwaraCard)
                                    .clickable { selectedSeason = s }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text("Musim $s", color = if (active) Color.Black else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            items(epRows.size, key = { "ep_row_$it" }) { rIdx ->
                val rowEps = epRows[rIdx]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    rowEps.forEach { ep ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SwaraCard)
                                .border(0.5.dp, SwaraSubtleBorder, RoundedCornerShape(12.dp))
                                .clickable { onPlay(item, ep.season, ep.episode, detail.episodes) }
                        ) {
                            Text("${ep.episode}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    repeat(6 - rowEps.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}
