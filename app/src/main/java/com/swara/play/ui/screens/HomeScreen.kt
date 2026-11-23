package com.swara.play.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swara.play.R
import com.swara.play.model.AppReleaseInfo
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.WatchProgress
import com.swara.play.ui.components.ContinueWatchingCard
import com.swara.play.ui.components.MediaPosterCard
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraAccentSoft
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraCardElevated
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.viewmodel.HomeUiState

@Composable
fun HomeScreen(
    state: HomeUiState,
    resumeList: List<WatchProgress>,
    availableUpdate: AppReleaseInfo?,
    isWideLayout: Boolean,
    isTv: Boolean,
    onCategorySelected: (MediaCategory) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onResumeClick: (WatchProgress) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDonate: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onRefresh: () -> Unit
) {
    val topTabCategories = listOf(MediaCategory.ALL, MediaCategory.MOVIE, MediaCategory.SERIES, MediaCategory.ASIAN_DRAMA, MediaCategory.ANIME)
    var topPicksSubTab by remember { mutableIntStateOf(0) }
    val subTabLabels = listOf("Global", "K-Drama", "Film", "Anime", "Aksi", "Horor")

    val showcaseItems = when (state.selectedCategory) {
        MediaCategory.MOVIE -> state.movies.take(6)
        MediaCategory.SERIES -> state.series.take(6)
        MediaCategory.ASIAN_DRAMA -> state.asianDramas.take(6)
        MediaCategory.ANIME -> state.animeTrending.take(6)
        MediaCategory.ALL -> (state.asianDramas.take(2) + state.movies.take(2) + state.animeTrending.take(2))
    }

    val topPicksList = when (topPicksSubTab) {
        1 -> state.asianDramas
        2 -> state.movies
        3 -> state.animeTrending
        4 -> state.actionMovies
        5 -> state.horrorMovies
        else -> (state.movies.take(4) + state.asianDramas.take(3) + state.animeTrending.take(3))
    }.take(10)

    val fullVerticalCatalog = remember(state) {
        when (state.selectedCategory) {
            MediaCategory.MOVIE -> (state.movies + state.actionMovies + state.horrorMovies).distinctBy { it.id }
            MediaCategory.SERIES -> state.series
            MediaCategory.ASIAN_DRAMA -> state.asianDramas
            MediaCategory.ANIME -> (state.animeTrending + state.animeTopRated).distinctBy { it.id }
            MediaCategory.ALL -> (state.movies + state.series + state.asianDramas + state.animeTrending + state.actionMovies).distinctBy { it.id }
        }.take(36)
    }

    val cols = if (isTv) 6 else if (isWideLayout) 5 else 3
    val gridRows = remember(fullVerticalCatalog, cols) { fullVerticalCatalog.chunked(cols) }
    val hPad = if (isTv) 32.dp else 16.dp

    LazyColumn(
        contentPadding = PaddingValues(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize().background(SwaraBlack)
    ) {
        item(key = "header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = hPad, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.swaraplay_logo),
                        contentDescription = "SwaraPlay",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("SwaraPlay", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Default.Search, contentDescription = "Cari", tint = TextPrimary) }
                    IconButton(onClick = onOpenLibrary) { Icon(Icons.Default.History, contentDescription = "Riwayat", tint = TextSecondary) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SwaraCardElevated)
                            .clickable(onClick = onOpenDonate)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Dukung", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang", tint = TextSecondary) }
                }
            }
        }

        if (availableUpdate != null) {
            item(key = "update_banner") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = hPad)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SwaraCardElevated)
                        .border(1.dp, SwaraSubtleBorder, RoundedCornerShape(16.dp))
                        .clickable(onClick = onOpenUpdateDialog)
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.NewReleases, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Versi baru ${availableUpdate.tagName} tersedia", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Ketuk untuk mengunduh pembaruan langsung.", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                    Text("Perbarui", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        item(key = "categories") {
            LazyRow(contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(topTabCategories, key = { it.name }) { cat ->
                    val selected = state.selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) Color.White else SwaraCard)
                            .clickable { onCategorySelected(cat) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = cat.label,
                            color = if (selected) Color.Black else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (state.isLoading) {
            item(key = "loading") {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    CircularProgressIndicator(color = SwaraAccent)
                }
            }
        } else {
            if (!state.errorMessage.isNullOrBlank() && state.movies.isEmpty() && state.animeTrending.isEmpty()) {
                item(key = "error") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(state.errorMessage, color = TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                            Text("Coba Lagi", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (showcaseItems.isNotEmpty()) {
                item(key = "showcase_row") {
                    LazyRow(contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(showcaseItems, key = { "show_${it.id}" }) { item ->
                            ShowcaseWideCard(item = item, isWide = isWideLayout || isTv, onClick = { onMediaClick(item) })
                        }
                    }
                }
            }

            if (topPicksList.isNotEmpty()) {
                item(key = "top_picks_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Pilihan Teratas", hPad)
                        LazyRow(contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(subTabLabels, key = { idx, _ -> "sub_$idx" }) { idx, label ->
                                val selected = topPicksSubTab == idx
                                Text(
                                    text = label,
                                    color = if (selected) TextPrimary else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) SwaraAccentSoft else Color.Transparent)
                                        .clickable { topPicksSubTab = idx }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                        LazyRow(contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(topPicksList, key = { idx, it -> "rank_${idx}_${it.id}" }) { idx, media ->
                                MediaPosterCard(item = media, rankBadge = idx + 1, onClick = { onMediaClick(media) })
                            }
                        }
                    }
                }
            }

            if (resumeList.isNotEmpty() && state.selectedCategory == MediaCategory.ALL) {
                item(key = "resume_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Lanjut Nonton", hPad)
                        LazyRow(contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(resumeList, key = { "res_${it.mediaId}_${it.season}_${it.episode}" }) { prog ->
                                ContinueWatchingCard(progress = prog, onClick = { onResumeClick(prog) })
                            }
                        }
                    }
                }
            }

            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.MOVIE) && state.movies.isNotEmpty()) {
                item(key = "movies_row") { MediaShelf("Rilisan Film Populer", state.movies, hPad, onMediaClick) }
            }
            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.SERIES) && state.series.isNotEmpty()) {
                item(key = "series_row") { MediaShelf("Serial TV Pilihan", state.series, hPad, onMediaClick) }
            }
            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.ASIAN_DRAMA) && state.asianDramas.isNotEmpty()) {
                item(key = "drama_row") { MediaShelf("Drama Asia Populer", state.asianDramas, hPad, onMediaClick) }
            }
            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.ANIME) && state.animeTrending.isNotEmpty()) {
                item(key = "anime_trend_row") { MediaShelf("Anime Trending", state.animeTrending, hPad, onMediaClick) }
            }
            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.ANIME) && state.animeTopRated.isNotEmpty()) {
                item(key = "anime_top_row") { MediaShelf("Anime Rating Tertinggi", state.animeTopRated, hPad, onMediaClick) }
            }
            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.MOVIE) && state.actionMovies.isNotEmpty()) {
                item(key = "action_row") { MediaShelf("Aksi & Petualangan", state.actionMovies, hPad, onMediaClick) }
            }
            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.MOVIE) && state.horrorMovies.isNotEmpty()) {
                item(key = "horror_row") { MediaShelf("Horor & Thriller", state.horrorMovies, hPad, onMediaClick) }
            }

            if (gridRows.isNotEmpty()) {
                item(key = "all_catalog_header") { SectionTitle("Jelajahi Lebih Banyak", hPad) }
                itemsIndexed(gridRows, key = { idx, _ -> "grid_row_$idx" }) { _, rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = hPad)) {
                        rowItems.forEach { item ->
                            MediaPosterCard(item = item, onClick = { onMediaClick(item) }, modifier = Modifier.weight(1f))
                        }
                        repeat(cols - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowcaseWideCard(item: MediaItem, isWide: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableIntStateOf(0) }
    val cardWidth = if (isWide) 285.dp else 248.dp
    Box(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(16f / 10f)
            .onFocusChanged { focused = if (it.isFocused) 1 else 0 }
            .focusable()
            .clip(RoundedCornerShape(16.dp))
            .background(SwaraCard)
            .border(if (focused == 1) 2.dp else 0.5.dp, if (focused == 1) SwaraAccent else SwaraSubtleBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(model = item.backdropUrl.ifBlank { item.posterUrl }, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x11000000), Color(0xEB000000)))))
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(
                text = listOfNotNull(item.category.label, item.year.takeIf { it.isNotBlank() }).joinToString(" • "),
                color = Color(0xFFD0D0D8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(item.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitle(title: String, hPad: Dp) {
    Text(text = title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = hPad))
}

@Composable
private fun MediaShelf(title: String, items: List<MediaItem>, hPad: Dp, onMediaClick: (MediaItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title, hPad)
        LazyRow(contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { "${title}_${it.id}" }) { media ->
                MediaPosterCard(item = media, onClick = { onMediaClick(media) })
            }
        }
    }
}
