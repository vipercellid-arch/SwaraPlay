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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.ui.components.MediaPosterCard
import com.swara.play.ui.theme.LocalSwaraPalette
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private val CINEJOY_GENRES = listOf(
    "Semua", "Action", "Sci-Fi", "Horror", "Comedy", "Animation", "Thriller", "Fantasy", "Romance", "Crime", "Drama"
)

@Composable
fun CategoryCatalogScreen(
    title: String,
    category: MediaCategory,
    initialGenre: String = "Semua",
    items: List<MediaItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    isTv: Boolean,
    onLoadFilter: (MediaCategory, String) -> Unit,
    onLoadMore: () -> Unit,
    onMediaClick: (MediaItem) -> Unit
) {
    val pal = LocalSwaraPalette.current
    val hPad = if (isTv) 48.dp else 16.dp
    var selectedGenre by remember(category, initialGenre) { mutableStateOf(initialGenre.ifBlank { "Semua" }) }

    LaunchedEffect(category, selectedGenre) {
        onLoadFilter(category, selectedGenre)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(pal.glowPrimary.copy(alpha = 0.25f), pal.bg, pal.bg))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (isTv) 86.dp else 94.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = hPad)
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = hPad),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CINEJOY_GENRES) { g ->
                    val active = selectedGenre == g
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (active) Color.White else pal.surface)
                            .border(1.dp, if (active) Color.White else pal.border, RoundedCornerShape(99.dp))
                            .clickable { selectedGenre = g }
                            .padding(horizontal = 15.dp, vertical = 8.dp)
                    ) {
                        Text(g, color = if (active) Color.Black else TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            if (isLoading && items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = pal.brandAccent)
                }
            } else {
                val clean = remember(items) { items.filter { it.posterUrl.isNotBlank() } }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = if (isTv) 150.dp else 112.dp),
                    contentPadding = PaddingValues(start = hPad, end = hPad, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(clean, key = { "cat_${it.id}_${it.category.name}" }) { item ->
                        MediaPosterCard(item = item, fillWidth = true, onClick = { onMediaClick(item) })
                    }
                    if (clean.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(color = pal.brandAccent, modifier = Modifier.size(28.dp))
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(pal.surface)
                                            .border(1.dp, pal.border, RoundedCornerShape(99.dp))
                                            .clickable(onClick = onLoadMore)
                                            .padding(horizontal = 24.dp, vertical = 10.dp)
                                    ) {
                                        Text("Muat Lebih Banyak", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
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
fun ExploreScreen(
    results: List<MediaItem>,
    trendingToday: List<MediaItem>,
    recentSearches: List<String>,
    isSearching: Boolean,
    isTv: Boolean,
    onSearch: (String) -> Unit,
    onSaveRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onMediaClick: (MediaItem) -> Unit
) {
    val pal = LocalSwaraPalette.current
    val hPad = if (isTv) 48.dp else 16.dp
    var query by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        if (query.trim().length >= 2) {
            delay(350L)
            onSearch(query.trim())
            onSaveRecent(query.trim())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(pal.glowPrimary.copy(alpha = 0.32f), pal.bg, pal.bg))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (isTv) 86.dp else 94.dp)
        ) {
            Text(
                text = "Siap untuk petualangan?",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = hPad)
            )
            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad)
                    .clip(RoundedCornerShape(99.dp))
                    .background(pal.glass)
                    .border(1.dp, pal.border, RoundedCornerShape(99.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Cari film, serial TV, anime, atau drama...", color = TextSecondary, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(pal.brandAccent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Hapus",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp).clickable { query = "" }
                    )
                }
            }

            if (recentSearches.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = hPad),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recentSearches) { rec ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(pal.surface)
                                .border(1.dp, pal.border, RoundedCornerShape(99.dp))
                                .clickable { query = rec }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(rec, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp).clickable { onRemoveRecent(rec) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            if (query.trim().length < 2) {
                Text(
                    "Trending Hari Ini",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = hPad)
                )
                Spacer(Modifier.height(12.dp))
                val cleanTrend = remember(trendingToday) { trendingToday.filter { it.posterUrl.isNotBlank() } }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = if (isTv) 150.dp else 112.dp),
                    contentPadding = PaddingValues(start = hPad, end = hPad, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(cleanTrend, key = { "tr_${it.id}" }) { item ->
                        MediaPosterCard(item = item, fillWidth = true, onClick = { onMediaClick(item) })
                    }
                }
            } else if (isSearching) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = pal.brandAccent)
                }
            } else {
                val cleanRes = remember(results) { results.filter { it.posterUrl.isNotBlank() } }
                if (cleanRes.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Tidak ada hasil untuk \"$query\"", color = TextSecondary, fontSize = 14.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (isTv) 150.dp else 112.dp),
                        contentPadding = PaddingValues(start = hPad, end = hPad, bottom = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(cleanRes, key = { "sr_${it.id}_${it.category.name}" }) { item ->
                            MediaPosterCard(item = item, fillWidth = true, onClick = { onMediaClick(item) })
                        }
                    }
                }
            }
        }
    }
}
