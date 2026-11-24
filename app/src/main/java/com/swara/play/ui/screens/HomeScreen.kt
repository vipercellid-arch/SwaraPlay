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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swara.play.R
import com.swara.play.model.AppReleaseInfo
import com.swara.play.model.CastMember
import com.swara.play.model.ContentRestrictionPrefs
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.UserProfile
import com.swara.play.ui.components.MediaPosterCard
import com.swara.play.ui.components.UserAvatar
import com.swara.play.ui.theme.StarGold
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraAccentSoft
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraCardElevated
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.ui.theme.TextTertiary
import com.swara.play.viewmodel.HomeUiState
import java.util.Calendar
import java.util.TimeZone

private fun dailyRotationSeedAt7AmWit(): Int {
    val witCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jayapura"))
    witCal.add(Calendar.HOUR_OF_DAY, -7)
    return witCal.get(Calendar.YEAR) * 1000 + witCal.get(Calendar.DAY_OF_YEAR)
}

private fun rotateDaily(list: List<MediaItem>, seed: Int): List<MediaItem> {
    if (list.size <= 1) return list
    val shift = ((seed % list.size) + list.size) % list.size
    return list.drop(shift) + list.take(shift)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    contentRestriction: ContentRestrictionPrefs,
    favorites: List<MediaItem>,
    userProfile: UserProfile,
    availableUpdate: AppReleaseInfo?,
    isWideLayout: Boolean,
    isTv: Boolean,
    onCategorySelected: (MediaCategory) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onPlayNow: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onActorClick: (CastMember) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenUpdatePopup: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit
) {
    val topTabCategories = listOf(MediaCategory.ALL, MediaCategory.MOVIE, MediaCategory.SERIES, MediaCategory.ASIAN_DRAMA, MediaCategory.ANIME)
    var topPicksSubTab by remember { mutableIntStateOf(0) }
    val subTabLabels = listOf("Global", "K-Drama", "Film", "Anime", "Aksi", "Horor")
    val daySeed = remember { dailyRotationSeedAt7AmWit() }
    val listState = rememberLazyListState()

    fun filterAllowed(list: List<MediaItem>): List<MediaItem> = list.filter {
        !(contentRestriction.blockAdult18 && it.isAdult18) && !(contentRestriction.blockLgbt && it.isLgbt)
    }

    val rtMovies = filterAllowed(state.realtimeMovies.ifEmpty { state.movies })
    val rtSeries = filterAllowed(state.realtimeSeries.ifEmpty { state.series })
    val moviesF = filterAllowed(state.movies)
    val seriesF = filterAllowed(state.series)
    val dramasF = filterAllowed(state.asianDramas)
    val animeTrendF = filterAllowed(state.animeTrending)
    val animeTopF = filterAllowed(state.animeTopRated)
    val actionF = filterAllowed(state.actionMovies)
    val horrorF = filterAllowed(state.horrorMovies)
    val extraF = filterAllowed(state.extraCatalog)

    val heroPool = when (state.selectedCategory) {
        MediaCategory.MOVIE -> rtMovies
        MediaCategory.SERIES -> rtSeries
        MediaCategory.ASIAN_DRAMA -> dramasF
        MediaCategory.ANIME -> animeTrendF
        MediaCategory.ALL -> (rtMovies.take(6) + dramasF.take(6) + animeTrendF.take(6))
    }
    val heroItems = remember(heroPool, daySeed) { rotateDaily(heroPool, daySeed).take(6) }

    val topPicksList = when (topPicksSubTab) {
        1 -> dramasF
        2 -> rtMovies
        3 -> animeTrendF
        4 -> actionF
        5 -> horrorF
        else -> (rtMovies.take(4) + rtSeries.take(2) + dramasF.take(2) + animeTrendF.take(2)).distinctBy { it.id }
    }.take(10)

    val fullVerticalCatalog = remember(state, contentRestriction) {
        val base = when (state.selectedCategory) {
            MediaCategory.MOVIE -> (moviesF + actionF + horrorF)
            MediaCategory.SERIES -> seriesF
            MediaCategory.ASIAN_DRAMA -> dramasF
            MediaCategory.ANIME -> (animeTrendF + animeTopF)
            MediaCategory.ALL -> (moviesF + seriesF + dramasF + animeTrendF + actionF)
        }
        (base + extraF).distinctBy { it.id }
    }

    val cols = if (isTv) 6 else if (isWideLayout) 5 else 3
    val gridRows = remember(fullVerticalCatalog, cols) { fullVerticalCatalog.chunked(cols) }
    val hPad = if (isTv) 32.dp else 16.dp
    val heroHeight = if (isWideLayout || isTv) 440.dp else 490.dp

    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 4 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore, state.isLoading, state.isLoadingMore, state.hasMore) {
        if (shouldLoadMore && !state.isLoading && !state.isLoadingMore && state.hasMore) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize().background(SwaraBlack)
    ) {
        item(key = "euthopiar_hero") {
            Box(modifier = Modifier.fillMaxWidth().height(heroHeight).background(SwaraBlack)) {
                if (heroItems.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { heroItems.size })
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        val item = heroItems[page]
                        val isSaved = favorites.any { it.id == item.id }
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = item.backdropUrl.ifBlank { item.posterUrl },
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        0.00f to Color(0x880B0B0F),
                                        0.30f to Color.Transparent,
                                        0.62f to Color(0xAA0B0B0F),
                                        0.88f to Color(0xEB0B0B0F),
                                        1.00f to SwaraBlack
                                    )
                                )
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = hPad, vertical = 26.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    color = TextPrimary,
                                    fontSize = if (isWideLayout) 34.sp else 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // Euthopiar frosted metadata pills (Year / Category / Rating)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (item.year.isNotBlank()) HeroMetaPill(item.year.take(4))
                                    HeroMetaPill(item.category.label)
                                    if (item.rating.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0x99181820))
                                                .border(0.5.dp, SwaraSubtleBorder, RoundedCornerShape(10.dp))
                                                .padding(horizontal = 9.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(item.rating, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                // Euthopiar Hero Buttons: White Play + Glass Detail + Circle '+'
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = { onPlayNow(item) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0B0B0F)),
                                        shape = RoundedCornerShape(26.dp),
                                        modifier = Modifier.weight(1.2f).height(48.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Putar", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(26.dp))
                                            .background(Color(0xB31A1A22))
                                            .border(0.5.dp, SwaraSubtleBorder, RoundedCornerShape(26.dp))
                                            .clickable { onMediaClick(item) }
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Detail", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xB31A1A22))
                                            .border(0.5.dp, SwaraSubtleBorder, CircleShape)
                                            .clickable { onToggleFavorite(item) }
                                    ) {
                                        Icon(
                                            imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Add,
                                            contentDescription = "Koleksi",
                                            tint = TextPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Centered slim dash pager indicator (exact Euthopiar style)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                    ) {
                        repeat(heroItems.size) { idx ->
                            val active = pagerState.currentPage == idx
                            Box(
                                modifier = Modifier.height(3.dp).width(if (active) 22.dp else 14.dp)
                                    .clip(CircleShape).background(if (active) Color.White else Color(0x44FFFFFF))
                            )
                        }
                    }
                } else if (state.isLoading) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(color = SwaraAccent)
                    }
                }

                // Euthopiar Top Header Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = hPad, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.swaraplay_logo),
                            contentDescription = "SwaraPlay",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("SWARAPLAY", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp)
                    }
                    UserAvatar(profile = userProfile, size = 36.dp, modifier = Modifier.clickable(onClick = onOpenProfile))
                }
            }
        }

        if (availableUpdate != null) {
            item(key = "update_banner") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth().padding(horizontal = hPad)
                        .clip(RoundedCornerShape(14.dp)).background(SwaraCardElevated)
                        .border(0.5.dp, SwaraSubtleBorder, RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenUpdatePopup)
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.NewReleases, contentDescription = null, tint = SwaraAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Versi baru ${availableUpdate.tagName} tersedia", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Perbarui", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // Euthopiar Minimalist Centered Category Tabs with dot below active item
        item(key = "categories") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = hPad),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(topTabCategories, key = { it.name }) { cat ->
                    val selected = state.selectedCategory == cat
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onCategorySelected(cat) }.padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = cat.label,
                            color = if (selected) TextPrimary else TextTertiary,
                            fontSize = if (selected) 16.sp else 14.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (selected) Color.White else Color.Transparent))
                    }
                }
            }
        }

        if (!state.isLoading) {
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

            // Euthopiar "Top 10 Hari Ini" with vertical crimson accent bar!
            if (topPicksList.isNotEmpty()) {
                item(key = "top_picks_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = hPad)) {
                            Box(modifier = Modifier.width(3.dp).height(28.dp).clip(CircleShape).background(SwaraAccent))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Top 10 Hari Ini", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Diperbarui setiap hari", color = TextTertiary, fontSize = 11.sp)
                            }
                        }
                        LazyRow(contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(subTabLabels, key = { idx, _ -> "sub_$idx" }) { idx, label ->
                                val selected = topPicksSubTab == idx
                                Text(
                                    text = label,
                                    color = if (selected) TextPrimary else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
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

            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.MOVIE) && moviesF.isNotEmpty()) {
                item(key = "movies_row") { MediaShelf("Film Populer", moviesF, hPad, onMediaClick) }
            }
            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.SERIES) && seriesF.isNotEmpty()) {
                item(key = "series_row") { MediaShelf("Serial TV Pilihan", seriesF, hPad, onMediaClick) }
            }
            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.ASIAN_DRAMA) && dramasF.isNotEmpty()) {
                item(key = "drama_row") { MediaShelf("Drama Asia Populer", dramasF, hPad, onMediaClick) }
            }
            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.ASIAN_DRAMA) && state.koreanStars.isNotEmpty()) {
                item(key = "kr_stars") { StarPortraitShelf("Bintang Korea", state.koreanStars, hPad, onActorClick) }
            }
            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.MOVIE) && state.hollywoodStars.isNotEmpty()) {
                item(key = "hw_stars") { StarPortraitShelf("Bintang Hollywood", state.hollywoodStars, hPad, onActorClick) }
            }
            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.ANIME) && animeTrendF.isNotEmpty()) {
                item(key = "anime_trend_row") { MediaShelf("Anime Trending", animeTrendF, hPad, onMediaClick) }
            }
            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.ANIME) && animeTopF.isNotEmpty()) {
                item(key = "anime_top_row") { MediaShelf("Anime Rating Tertinggi", animeTopF, hPad, onMediaClick) }
            }
            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.MOVIE) && actionF.isNotEmpty()) {
                item(key = "action_row") { MediaShelf("Aksi & Petualangan", actionF, hPad, onMediaClick) }
            }
            if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.MOVIE) && horrorF.isNotEmpty()) {
                item(key = "horror_row") { MediaShelf("Horor & Thriller", horrorF, hPad, onMediaClick) }
            }

            if (gridRows.isNotEmpty()) {
                item(key = "all_catalog_header") { SectionTitle("Jelajahi Lebih Banyak", hPad) }
                itemsIndexed(gridRows, key = { idx, _ -> "grid_row_$idx" }) { _, rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = hPad)) {
                        rowItems.forEach { item -> MediaPosterCard(item = item, onClick = { onMediaClick(item) }, modifier = Modifier.weight(1f)) }
                        repeat(cols - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }

            if (state.isLoadingMore) {
                item(key = "home_load_more") {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                        CircularProgressIndicator(color = SwaraAccent, modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetaPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x99181820))
            .border(0.5.dp, SwaraSubtleBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionTitle(title: String, hPad: Dp) {
    Text(text = title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = hPad))
}

@Composable
private fun MediaShelf(title: String, items: List<MediaItem>, hPad: Dp, onMediaClick: (MediaItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title, hPad)
        LazyRow(contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { "${title}_${it.id}" }) { media -> MediaPosterCard(item = media, onClick = { onMediaClick(media) }) }
        }
    }
}

@Composable
private fun StarPortraitShelf(title: String, stars: List<CastMember>, hPad: Dp, onActorClick: (CastMember) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title, hPad)
        LazyRow(contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(stars, key = { "${title}_${it.name}" }) { star ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(96.dp).clickable { onActorClick(star) }
                ) {
                    AsyncImage(
                        model = star.photoUrl,
                        contentDescription = star.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(88.dp).clip(CircleShape).background(SwaraCard).border(0.5.dp, SwaraSubtleBorder, CircleShape)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(star.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
