package com.swara.play.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swara.play.R
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.WatchProgress
import com.swara.play.ui.components.ContinueWatchingCard
import com.swara.play.ui.components.MediaPosterCard
import com.swara.play.ui.theme.CinemaCrimson
import com.swara.play.ui.theme.OledBlack
import com.swara.play.ui.theme.OledBorder
import com.swara.play.ui.theme.OledSurface
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.viewmodel.HomeUiState

@Composable
fun HomeScreen(
    state: HomeUiState,
    resumeList: List<WatchProgress>,
    onCategorySelected: (MediaCategory) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onResumeClick: (WatchProgress) -> Unit,
    onRefresh: () -> Unit
) {
    val heroItems = when (state.selectedCategory) {
        MediaCategory.MOVIE -> state.movies.take(5)
        MediaCategory.SERIES -> state.series.take(5)
        MediaCategory.ASIAN_DRAMA -> state.asianDramas.take(5)
        MediaCategory.ANIME -> state.animeTrending.take(5)
        MediaCategory.ALL -> (state.movies.take(2) + state.animeTrending.take(2) + state.series.take(1))
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        item(key = "header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.swaraplay_mascot),
                        contentDescription = "SwaraPlay",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, CinemaCrimson, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("SWARA", color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Text("PLAY", color = CinemaCrimson, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang", tint = TextSecondary)
                }
            }
        }

        item(key = "categories") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MediaCategory.entries, key = { it.name }) { cat ->
                    FilterChip(
                        selected = state.selectedCategory == cat,
                        onClick = { onCategorySelected(cat) },
                        label = { Text(cat.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
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

        if (state.isLoading) {
            item(key = "loading") {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    CircularProgressIndicator(color = CinemaCrimson)
                }
            }
        } else {
            if (!state.errorMessage.isNullOrBlank() && state.movies.isEmpty() && state.animeTrending.isEmpty()) {
                item(key = "error") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    ) {
                        Text(state.errorMessage, color = TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = CinemaCrimson)) {
                            Text("Coba Lagi")
                        }
                    }
                }
            }

            if (heroItems.isNotEmpty()) {
                item(key = "hero_carousel") {
                    HeroShowcaseCarousel(items = heroItems, onClick = onMediaClick)
                }
            }

            if (resumeList.isNotEmpty() && state.selectedCategory == MediaCategory.ALL) {
                item(key = "resume_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Lanjut Nonton")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(resumeList, key = { "res_${it.mediaId}_${it.season}_${it.episode}" }) { prog ->
                                ContinueWatchingCard(progress = prog, onClick = { onResumeClick(prog) })
                            }
                        }
                    }
                }
            }

            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.MOVIE) && state.movies.isNotEmpty()) {
                item(key = "movies_row") { MediaShelf("Film Populer", state.movies, onMediaClick) }
            }

            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.SERIES) && state.series.isNotEmpty()) {
                item(key = "series_row") { MediaShelf("Series Pilihan", state.series, onMediaClick) }
            }

            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.ASIAN_DRAMA) && state.asianDramas.isNotEmpty()) {
                item(key = "drama_row") { MediaShelf("Drama Asia Populer", state.asianDramas, onMediaClick) }
            }

            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.ANIME) && state.animeTrending.isNotEmpty()) {
                item(key = "anime_trend_row") { MediaShelf("Anime Trending", state.animeTrending, onMediaClick) }
            }

            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.ANIME) && state.animeTopRated.isNotEmpty()) {
                item(key = "anime_top_row") { MediaShelf("Anime Rating Tertinggi", state.animeTopRated, onMediaClick) }
            }
        }
    }
}

@Composable
private fun HeroShowcaseCarousel(items: List<MediaItem>, onClick: (MediaItem) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp
        ) { page ->
            val item = items[page]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(225.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(OledSurface)
                    .border(1.dp, OledBorder, RoundedCornerShape(20.dp))
                    .clickable { onClick(item) }
            ) {
                AsyncImage(
                    model = item.backdropUrl.ifBlank { item.posterUrl },
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0x220A0A0C), Color(0xF20A0A0C))))
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = listOfNotNull(
                            item.category.label,
                            item.year.takeIf { it.isNotBlank() },
                            item.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }
                        ).joinToString(" • "),
                        color = CinemaCrimson,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.title,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onClick(item) },
                        colors = ButtonDefaults.buttonColors(containerColor = CinemaCrimson),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tonton Sekarang", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(items.size) { idx ->
                val active = pagerState.currentPage == idx
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(if (active) 20.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (active) CinemaCrimson else OledBorder)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = TextPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun MediaShelf(title: String, items: List<MediaItem>, onMediaClick: (MediaItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { "${title}_${it.id}" }) { media ->
                MediaPosterCard(item = media, onClick = { onMediaClick(media) })
            }
        }
    }
}
