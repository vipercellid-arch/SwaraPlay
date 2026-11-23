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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swara.play.model.MediaDetail
import com.swara.play.model.MediaItem
import com.swara.play.ui.theme.CinemaCrimson
import com.swara.play.ui.theme.OledBlack
import com.swara.play.ui.theme.OledBorder
import com.swara.play.ui.theme.OledSurface
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
fun DetailScreen(
    detail: MediaDetail?,
    isLoading: Boolean,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onPlay: (MediaItem, Int, Int) -> Unit
) {
    if (detail == null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(OledBlack)) {
            CircularProgressIndicator(color = CinemaCrimson)
        }
        return
    }

    val item = detail.item
    val seasons = remember(detail.episodes) { detail.episodes.map { it.season }.distinct().sorted() }
    var selectedSeason by remember(seasons) { mutableIntStateOf(seasons.firstOrNull() ?: 1) }
    val seasonEpisodes = remember(detail.episodes, selectedSeason) { detail.episodes.filter { it.season == selectedSeason } }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = Modifier.fillMaxSize().background(OledBlack)
    ) {
        item(key = "backdrop") {
            Box(modifier = Modifier.fillMaxWidth().height(290.dp)) {
                AsyncImage(
                    model = item.backdropUrl.ifBlank { item.posterUrl },
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0x660A0A0C), OledBlack)))
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xAA0A0A0C))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
                }
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(
                        text = listOfNotNull(
                            item.category.label,
                            item.year.takeIf { it.isNotBlank() },
                            detail.runtime.takeIf { it.isNotBlank() },
                            item.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }
                        ).joinToString(" • "),
                        color = CinemaCrimson,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(item.title, color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item(key = "actions") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Button(
                    onClick = { onPlay(item, selectedSeason, seasonEpisodes.firstOrNull()?.episode ?: 1) },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaCrimson),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Putar Sekarang", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { onToggleFavorite(item) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorit",
                        tint = if (isFavorite) CinemaCrimson else TextPrimary
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
                    CircularProgressIndicator(color = CinemaCrimson, modifier = Modifier.size(28.dp))
                }
            }
        }

        if (seasons.isNotEmpty()) {
            item(key = "seasons") {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text("Daftar Episode", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons, key = { "s_$it" }) { s ->
                            FilterChip(
                                selected = selectedSeason == s,
                                onClick = { selectedSeason = s },
                                label = { Text("Season $s", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CinemaCrimson,
                                    selectedLabelColor = TextPrimary,
                                    containerColor = OledSurface,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            items(seasonEpisodes, key = { it.id }) { ep ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(OledSurface)
                        .border(1.dp, OledBorder, RoundedCornerShape(14.dp))
                        .clickable { onPlay(item, ep.season, ep.episode) }
                        .padding(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(CinemaCrimson)
                    ) {
                        Text("${ep.episode}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ep.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ep.overview.isNotBlank()) {
                            Text(ep.overview, color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(Icons.Default.PlayArrow, contentDescription = "Putar", tint = TextPrimary)
                }
            }
        }
    }
}
