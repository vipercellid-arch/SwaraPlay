package com.swara.play.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraCardElevated
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.viewmodel.HomeUiState
import java.util.Calendar
import java.util.TimeZone

private fun dailyRotationSeedAt7AmWit(): Int {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jayapura"))
    val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
    val year = cal.get(Calendar.YEAR)
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val effectiveDay = if (hour < 7) dayOfYear - 1 else dayOfYear
    return year * 400 + effectiveDay
}

private fun <T> rotateDaily(items: List<T>, seed: Int): List<T> {
    if (items.size <= 1) return items
    val shift = ((seed % items.size) + items.size) % items.size
    return items.drop(shift) + items.take(shift)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    contentRestriction: ContentRestrictionPrefs,
    userProfile: UserProfile,
    availableUpdate: AppReleaseInfo?,
    isWideLayout: Boolean,
    isTv: Boolean,
    onCategorySelected: (MediaCategory) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onActorClick: (CastMember) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenUpdatePopup: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit
) {
    val topTabCategories = listOf(MediaCategory.ALL, MediaCategory.MOVIE, MediaCategory.SERIES, MediaCategory.ASIAN_DRAMA, MediaCategory.ANIME)
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

    // Multi-card banner pool (10+ landscape cards scrollable continuously!)
    val heroPool = when (state.selectedCategory) {
        MediaCategory.MOVIE -> (rtMovies + actionF + horrorF).distinctBy { it.id }
        MediaCategory.SERIES -> (rtSeries + seriesF).distinctBy { it.id }
        MediaCategory.ASIAN_DRAMA -> dramasF
        MediaCategory.ANIME -> (animeTrendF + animeTopF).distinctBy { it.id }
        MediaCategory.ALL -> (rtMovies.take(4) + dramasF.take(4) + animeTrendF.take(4) + rtSeries.take(4)).distinctBy { it.id }
    }
    val heroItems = remember(heroPool, daySeed) { rotateDaily(heroPool, daySeed).take(12) }

    // Long continuously scrollable shelves (24+ titles per shelf, auto-loads more when scrolling sideways!)
    val topPicksList = remember(state, contentRestriction) {
        when (state.selectedCategory) {
            MediaCategory.MOVIE -> (rtMovies + moviesF + actionF).distinctBy { it.id }
            MediaCategory.SERIES -> (rtSeries + seriesF).distinctBy { it.id }
            MediaCategory.ASIAN_DRAMA -> dramasF.distinctBy { it.id }
            MediaCategory.ANIME -> (animeTrendF + animeTopF).distinctBy { it.id }
            MediaCategory.ALL -> (rtMovies + dramasF + animeTrendF + rtSeries + actionF + extraF).distinctBy { it.id }
        }.take(30)
    }

    val moviesShelf = remember(moviesF, actionF, extraF) { (moviesF + actionF + extraF.filter { it.category == MediaCategory.MOVIE }).distinctBy { it.id } }
    val seriesShelf = remember(seriesF, rtSeries, extraF) { (seriesF + rtSeries + extraF.filter { it.category == MediaCategory.SERIES }).distinctBy { it.id } }
    val dramaShelf = remember(dramasF, extraF) { (dramasF + extraF.filter { it.category == MediaCategory.ASIAN_DRAMA }).distinctBy { it.id } }
    val animeShelf = remember(animeTrendF, animeTopF, extraF) { (animeTrendF + animeTopF + extraF.filter { it.category == MediaCategory.ANIME }).distinctBy { it.id } }

    val fullVerticalCatalog = remember(state, contentRestriction) {
        val base = when (state.selectedCategory) {
            MediaCategory.MOVIE -> (moviesF + actionF + horrorF)
            MediaCategory.SERIES -> seriesF
            MediaCategory.ASIAN_DRAMA -> dramasF
            MediaCategory.ANIME -> (animeTrendF + animeTopF)
            MediaCategory.ALL -> (moviesF + seriesF + dramasF + animeTrendF + actionF + horrorF)
        }
        (base + extraF).distinctBy { "${it.id}_${it.category.name}" }
    }

    val hPad = if (isTv) 28.dp else 16.dp

    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 4 && lastVisible >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore, state.isLoading, state.isLoadingMore) {
        if (shouldLoadMore && !state.isLoading && !state.isLoadingMore && state.hasMore) {
            onLoadMore()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(SwaraBlack)) {
        // Compute exact uniform column count so bottom grid poster size matches top horizontal shelf cards (~128dp)!
        val uniformCols = if (maxWidth < 600.dp) 3 else ((maxWidth - hPad * 2) / 136.dp).toInt().coerceIn(4, 9)
        val gridRows = remember(fullVerticalCatalog, uniformCols) { fullVerticalCatalog.chunked(uniformCols) }
        val bannerWidth = if (isWideLayout || isTv) 310.dp else 255.dp
        val bannerHeight = if (isWideLayout || isTv) 182.dp else 148.dp

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // HiTV-Inspired Sleek Top Bar: Logo + Minimalist Category Tabs + Search Pill + Profile
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isWideLayout) 10.dp else 7.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = hPad, vertical = if (isWideLayout) 10.dp else 8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.swaraplay_logo),
                    contentDescription = "SwaraPlay",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(if (isWideLayout) 16.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    items(topTabCategories, key = { "top_${it.name}" }) { cat ->
                        val selected = state.selectedCategory == cat
                        val tabLabel = if (cat == MediaCategory.ALL) "Beranda" else cat.label
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onCategorySelected(cat) }.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = tabLabel,
                                color = if (selected) TextPrimary else TextSecondary,
                                fontSize = if (selected) 15.sp else 14.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .width(if (selected) 20.dp else 0.dp)
                                    .height(2.5.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) Color(0xFFE91E63) else Color.Transparent)
                            )
                        }
                    }
                }

                // Search Pill (like reference video!)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SwaraCard)
                        .border(0.5.dp, SwaraSubtleBorder, RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenSearch)
                        .padding(horizontal = if (isWideLayout) 14.dp else 10.dp, vertical = 7.dp)
                ) {
                    if (isWideLayout) {
                        Text("Cari film, drama, anime...", color = TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Icon(Icons.Default.Search, contentDescription = "Cari", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }

                UserAvatar(profile = userProfile, size = 32.dp, modifier = Modifier.clickable(onClick = onOpenProfile))
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 6.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (availableUpdate != null) {
                    item(key = "update_banner") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth().padding(horizontal = hPad)
                                .clip(RoundedCornerShape(14.dp)).background(SwaraCardElevated)
                                .clickable(onClick = onOpenUpdatePopup)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.NewReleases, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Versi baru ${availableUpdate.tagName} tersedia", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("Perbarui", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                // Multi-Card Horizontal Banner Carousel (exact video style: 2-4 wide cards side-by-side!)
                item(key = "multi_card_banner") {
                    if (heroItems.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = hPad),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(heroItems, key = { "hero_${it.id}_${it.category.name}" }) { item ->
                                Box(
                                    modifier = Modifier
                                        .width(bannerWidth)
                                        .height(bannerHeight)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(SwaraCard)
                                        .clickable { onMediaClick(item) }
                                ) {
                                    AsyncImage(
                                        model = item.backdropUrl.ifBlank { item.posterUrl },
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(
                                            Brush.verticalGradient(
                                                0.0f to Color.Transparent,
                                                0.45f to Color(0x33000000),
                                                1.0f to Color(0xDF000000)
                                            )
                                        )
                                    )
                                    Column(
                                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                                    ) {
                                        Text(
                                            text = item.title,
                                            color = TextPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = listOfNotNull(
                                                item.category.label,
                                                item.year.take(4).takeIf { it.isNotBlank() },
                                                item.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }
                                            ).joinToString(" • "),
                                            color = Color(0xFFD0D0DA),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else if (state.isLoading) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(bannerHeight)) {
                            CircularProgressIndicator(color = SwaraAccent)
                        }
                    }
                }

                if (state.isLoading && moviesF.isEmpty() && animeTrendF.isEmpty()) {
                    item(key = "loading_body") {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)) {
                            CircularProgressIndicator(color = SwaraAccent)
                        }
                    }
                } else {
                    if (state.errorMessage != null && moviesF.isEmpty()) {
                        item(key = "err") {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                                Text(state.errorMessage, color = TextSecondary, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                                    Text("Coba Lagi", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Pilihan Teratas (NO cluttered sub-tab row! Continuously scrollable sideways!)
                    if (topPicksList.isNotEmpty()) {
                        item(key = "top_picks_section") {
                            ScrollableRankedShelf("Pilihan Teratas ›", topPicksList, hPad, onMediaClick, onLoadMore)
                        }
                    }

                    if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.MOVIE) && moviesShelf.isNotEmpty()) {
                        item(key = "movies_row") { MediaShelf("Film Populer ›", moviesShelf, hPad, onMediaClick, onLoadMore) }
                    }
                    if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.SERIES) && seriesShelf.isNotEmpty()) {
                        item(key = "series_row") { MediaShelf("Serial TV Pilihan ›", seriesShelf, hPad, onMediaClick, onLoadMore) }
                    }
                    if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.ASIAN_DRAMA) && dramaShelf.isNotEmpty()) {
                        item(key = "drama_row") { MediaShelf("Drama Asia Populer ›", dramaShelf, hPad, onMediaClick, onLoadMore) }
                    }
                    if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.ASIAN_DRAMA) && state.koreanStars.isNotEmpty()) {
                        item(key = "kr_stars") { StarPortraitShelf("Bintang Korea ›", state.koreanStars, hPad, onActorClick) }
                    }
                    if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.MOVIE) && state.hollywoodStars.isNotEmpty()) {
                        item(key = "hw_stars") { StarPortraitShelf("Bintang Hollywood ›", state.hollywoodStars, hPad, onActorClick) }
                    }
                    if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.ANIME) && animeShelf.isNotEmpty()) {
                        item(key = "anime_trend_row") { MediaShelf("Anime Populer ›", animeShelf, hPad, onMediaClick, onLoadMore) }
                    }
                    if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.MOVIE) && actionF.isNotEmpty()) {
                        item(key = "action_row") { MediaShelf("Aksi & Petualangan ›", actionF, hPad, onMediaClick, onLoadMore) }
                    }
                    if ((state.selectedCategory == MediaCategory.ALL || state.selectedCategory == MediaCategory.MOVIE) && horrorF.isNotEmpty()) {
                        item(key = "horror_row") { MediaShelf("Horor & Thriller ›", horrorF, hPad, onMediaClick, onLoadMore) }
                    }

                    // Uniform Bottom Grid (EXACT SAME poster size as shelves above!)
                    if (gridRows.isNotEmpty()) {
                        item(key = "all_catalog_header") { SectionTitle("Jelajahi Lebih Banyak", hPad) }
                        itemsIndexed(gridRows, key = { idx, _ -> "grid_row_$idx" }) { _, rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = hPad)) {
                                rowItems.forEach { item -> MediaPosterCard(item = item, onClick = { onMediaClick(item) }, modifier = Modifier.weight(1f)) }
                                repeat(uniformCols - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
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
    }
}

@Composable
private fun SectionTitle(title: String, hPad: Dp) {
    Text(text = title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = hPad))
}

@Composable
private fun ScrollableRankedShelf(
    title: String,
    items: List<MediaItem>,
    hPad: Dp,
    onMediaClick: (MediaItem) -> Unit,
    onReachEnd: () -> Unit
) {
    val rowState = rememberLazyListState()
    val nearEnd by remember(items.size) {
        derivedStateOf {
            val total = rowState.layoutInfo.totalItemsCount
            val last = rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 5 && last >= total - 3
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) onReachEnd() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title, hPad)
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(items, key = { idx, it -> "rank_${idx}_${it.id}" }) { idx, media ->
                MediaPosterCard(item = media, rankBadge = if (idx < 10) idx + 1 else null, onClick = { onMediaClick(media) })
            }
        }
    }
}

@Composable
private fun MediaShelf(
    title: String,
    items: List<MediaItem>,
    hPad: Dp,
    onMediaClick: (MediaItem) -> Unit,
    onReachEnd: () -> Unit
) {
    val rowState = rememberLazyListState()
    val nearEnd by remember(items.size) {
        derivedStateOf {
            val total = rowState.layoutInfo.totalItemsCount
            val last = rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 5 && last >= total - 3
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) onReachEnd() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title, hPad)
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    modifier = Modifier.width(102.dp).clickable { onActorClick(star) }
                ) {
                    AsyncImage(
                        model = star.photoUrl,
                        contentDescription = star.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(SwaraCard)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(star.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
