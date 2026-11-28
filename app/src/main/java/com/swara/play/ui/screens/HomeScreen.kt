package com.swara.play.ui.screens

import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swara.play.model.AppUiPrefs
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.isKidsFriendly
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import com.swara.play.model.WatchProgress
import com.swara.play.ui.components.ContinueWatchingCard
import com.swara.play.ui.components.MediaPosterCard
import com.swara.play.ui.components.ProviderTileCard
import com.swara.play.ui.theme.LocalSwaraPalette
import com.swara.play.ui.theme.StarGold
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.viewmodel.HomeUiState
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    uiPrefs: AppUiPrefs,
    favorites: List<MediaItem>,
    resumeList: List<WatchProgress>,
    isTv: Boolean,
    onMediaClick: (MediaItem) -> Unit,
    onPlayMedia: (MediaItem, Int, Int) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onResumeClick: (WatchProgress) -> Unit,
    onRemoveResume: (String) -> Unit,
    onProviderClick: (String) -> Unit
) {
    val pal = LocalSwaraPalette.current
    val hPad = if (isTv) 48.dp else 16.dp
    val topBarClearance = if (isTv) 86.dp else 94.dp

    fun <T> rotateDaily(list: List<T>): List<T> {
        if (list.size <= 1) return list
        val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val shift = abs(dayKey.hashCode()) % list.size
        return list.drop(shift) + list.take(shift)
    }

    val isKids = uiPrefs.isKidsMode
    val filtMovies = remember(state.movies, isKids) { if (isKids) state.movies.filter { it.isKidsFriendly() } else state.movies }
    val filtSeries = remember(state.series, isKids) { if (isKids) state.series.filter { it.isKidsFriendly() } else state.series }
    val filtRtMovies = remember(state.realtimeMovies, isKids) { if (isKids) state.realtimeMovies.filter { it.isKidsFriendly() } else state.realtimeMovies }
    val filtAnime = remember(state.animeTrending, isKids) { if (isKids) state.animeTrending.filter { it.isKidsFriendly() } else state.animeTrending }

    val heroPool = remember(filtMovies, filtSeries, filtRtMovies, filtAnime, isKids) {
        val base = if (isKids) {
            (filtAnime.take(5) + filtMovies.take(5) + filtSeries.take(4))
        } else {
            (filtRtMovies.take(4) + filtMovies.take(4) + filtSeries.take(3) + filtAnime.take(2))
        }
        rotateDaily(
            base.distinctBy { it.id }
                .filter { it.backdropUrl.isNotBlank() || it.posterUrl.isNotBlank() }
        ).take(10)
    }

    var heroIndex by remember { mutableIntStateOf(0) }
    var cwEditing by remember { mutableStateOf(false) }
    var selectedProviderId by remember { mutableStateOf("netflix") }

    LaunchedEffect(heroPool.size) {
        if (heroPool.size > 1) {
            while (true) {
                delay(6500L)
                heroIndex = (heroIndex + 1) % heroPool.size
            }
        }
    }

    val activeHero = heroPool.getOrNull(heroIndex) ?: heroPool.firstOrNull()
    val heroBackdrop = activeHero?.backdropUrl?.ifBlank { activeHero.posterUrl }.orEmpty()

    Box(modifier = Modifier.fillMaxSize().background(pal.bg)) {
        // Dynamic Blurred Background synced to Active Hero Banner (Beranda only!)
        if (heroBackdrop.isNotBlank()) {
            AsyncImage(
                model = heroBackdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (!isTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(54.dp)
                        else Modifier
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                pal.bg.copy(alpha = 0.52f),
                                pal.bg.copy(alpha = 0.78f),
                                pal.bg.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        }

        if (state.isLoading && state.movies.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = pal.brandAccent)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (isTv) 48.dp else 104.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. FULL-BLEED CINEJOY HERO BANNER
                if (activeHero != null) {
                    item(key = "hero_banner") {
                        CinejoyHeroBanner(
                            item = activeHero,
                            heroCount = heroPool.size,
                            activeIndex = heroIndex,
                            useLogos = uiPrefs.useImageLogos,
                            isSaved = favorites.any { it.id == activeHero.id },
                            resume = resumeList.firstOrNull { it.mediaId == activeHero.id },
                            hPad = hPad,
                            topPadding = topBarClearance,
                            onSelectDot = { heroIndex = it },
                            onPlay = {
                                val r = resumeList.firstOrNull { it.mediaId == activeHero.id }
                                onPlayMedia(activeHero, r?.season ?: 1, r?.episode ?: 1)
                            },
                            onToggleSave = { onToggleFavorite(activeHero) },
                            onInfo = { onMediaClick(activeHero) }
                        )
                    }
                } else {
                    item { Spacer(Modifier.height(topBarClearance)) }
                }

                // 2. CONTINUE WATCHING (16:9)
                if (resumeList.isNotEmpty()) {
                    item(key = "continue_watching") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = hPad),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Lanjutkan Menonton", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (cwEditing) pal.brandAccent else pal.surface)
                                        .clickable { cwEditing = !cwEditing },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = if (cwEditing) Color.Black else TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = hPad),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.focusRestorer()
                            ) {
                                items(resumeList.take(12), key = { "cw_${it.mediaId}" }) { prog ->
                                    ContinueWatchingCard(
                                        progress = prog,
                                        isEditing = cwEditing,
                                        onClick = { onResumeClick(prog) },
                                        onRemove = { onRemoveResume(prog.mediaId) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. BROWSE BY PROVIDER (17 PLATFORMS)
                

                // 4. FILM TRENDING
                if (state.movies.isNotEmpty()) {
                    item(key = "trending_movies") {
                        CinejoyMediaRow("Film Trending", state.movies, hPad, onMediaClick)
                    }
                }

                // 5. SERIAL TRENDING
                if (state.series.isNotEmpty()) {
                    item(key = "trending_series") {
                        CinejoyMediaRow("Serial Trending", state.series, hPad, onMediaClick)
                    }
                }

                // 6. AKSI & THRILLER POPULER
                if (state.actionMovies.isNotEmpty()) {
                    item(key = "action_movies") {
                        CinejoyMediaRow("Film Aksi Pilihan", state.actionMovies, hPad, onMediaClick)
                    }
                }

                // 7. ANIME & DRAMA POPULER (accessible directly without top navbar clutter)
                if (state.animeTrending.isNotEmpty()) {
                    item(key = "anime_popular") {
                        CinejoyMediaRow("Anime Populer", state.animeTrending, hPad, onMediaClick)
                    }
                }
                if (state.asianDramas.isNotEmpty()) {
                    item(key = "drama_popular") {
                        CinejoyMediaRow("Drama Asia Populer", state.asianDramas, hPad, onMediaClick)
                    }
                }

                // 8. HOROR & MISTERI
                if (state.horrorMovies.isNotEmpty()) {
                    item(key = "horror_movies") {
                        CinejoyMediaRow("Horor & Misteri", state.horrorMovies, hPad, onMediaClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun CinejoyHeroBanner(
    item: MediaItem,
    heroCount: Int,
    activeIndex: Int,
    useLogos: Boolean,
    isSaved: Boolean,
    resume: WatchProgress?,
    hPad: Dp,
    topPadding: Dp,
    onSelectDot: (Int) -> Unit,
    onPlay: () -> Unit,
    onToggleSave: () -> Unit,
    onInfo: () -> Unit
) {
    val pal = LocalSwaraPalette.current
    var logoFailed by remember(item.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(450.dp)
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
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x55000000),
                            Color(0x22000000),
                            pal.bg.copy(alpha = 0.78f),
                            pal.bg.copy(alpha = 0.96f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = hPad, end = hPad, top = topPadding, bottom = 20.dp)
        ) {
            val effectiveLogo = item.logoUrl.ifBlank {
                if (item.imdbId.startsWith("tt")) "https://images.metahub.space/logo/medium/${item.imdbId}/img" else ""
            }
            if (useLogos && effectiveLogo.isNotBlank() && !logoFailed) {
                AsyncImage(
                    model = effectiveLogo,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onError = { logoFailed = true },
                    modifier = Modifier.height(74.dp).fillMaxWidth(0.72f)
                )
            } else {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 34.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.rating.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(14.dp))
                        Text(item.rating, color = StarGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (item.year.isNotBlank()) {
                    Text(item.year, color = Color(0xFFD4D8D5), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                item.genres.take(2).forEach { g ->
                    Text("• $g", color = Color(0xFFD4D8D5), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (item.overview.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.overview,
                    color = Color(0xFFD0D6D2),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color.White)
                            .focusable()
                            .clickable(onClick = onPlay)
                            .padding(horizontal = 22.dp, vertical = 11.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Text(
                            if (resume != null) "Lanjutkan" else "Putar",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isSaved) pal.brandAccent else Color(0x33FFFFFF))
                            .border(1.dp, Color(0x33FFFFFF), CircleShape)
                            .focusable()
                            .clickable(onClick = onToggleSave),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isSaved) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = "Daftar Saya",
                            tint = if (isSaved) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                            .border(1.dp, Color(0x33FFFFFF), CircleShape)
                            .focusable()
                            .clickable(onClick = onInfo),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Detail", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                if (heroCount > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(heroCount) { idx ->
                            Box(
                                modifier = Modifier
                                    .height(6.dp)
                                    .width(if (idx == activeIndex) 20.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (idx == activeIndex) Color.White else Color(0x55FFFFFF))
                                    .clickable { onSelectDot(idx) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CinejoyMediaRow(
    title: String,
    items: List<MediaItem>,
    hPad: Dp,
    onMediaClick: (MediaItem) -> Unit
) {
    val clean = remember(items) { items.filter { it.posterUrl.isNotBlank() } }
    if (clean.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = hPad)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = hPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.focusRestorer()
        ) {
            items(clean, key = { "${title}_${it.id}" }) { media ->
                MediaPosterCard(item = media, onClick = { onMediaClick(media) })
            }
        }
    }
}
