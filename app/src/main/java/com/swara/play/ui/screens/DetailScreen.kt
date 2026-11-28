package com.swara.play.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.swara.play.model.AppUiPrefs
import com.swara.play.model.CastMember
import com.swara.play.model.EpisodeItem
import com.swara.play.model.MediaDetail
import com.swara.play.model.MediaItem
import com.swara.play.player.TrailerPopupDialog
import com.swara.play.ui.components.MediaPosterCard
import com.swara.play.ui.theme.LocalSwaraPalette
import com.swara.play.ui.theme.StarGold
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
fun DetailScreen(
    detail: MediaDetail?,
    uiPrefs: AppUiPrefs,
    isLoading: Boolean,
    isFavorite: Boolean,
    isTv: Boolean,
    onBack: () -> Unit,
    onPlay: (MediaItem, Int, Int, List<EpisodeItem>) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onActorClick: (CastMember) -> Unit,
    onSimilarClick: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val pal = LocalSwaraPalette.current
    if (detail == null) {
        Box(Modifier.fillMaxSize().background(pal.bg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = pal.brandAccent)
        }
        return
    }

    val item = detail.item
    val seasons = remember(detail.episodes) { detail.episodes.map { it.season }.distinct().sorted() }
    var selectedSeason by remember(detail.item.id, seasons) { mutableIntStateOf(seasons.firstOrNull() ?: 1) }
    var showSeasonPicker by remember { mutableStateOf(false) }
    var epQuery by remember(detail.item.id) { mutableStateOf("") }
    var epPage by remember(detail.item.id, selectedSeason) { mutableIntStateOf(0) }
    var logoFailed by remember(item.id) { mutableStateOf(false) }
    var showTrailerDialog by remember(item.id) { mutableStateOf(false) }
    var contentVisible by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id) { contentVisible = true }

    val favScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "favScale"
    )

    val seasonEpisodes = remember(detail.episodes, selectedSeason, epQuery) {
        val list = detail.episodes.filter { it.season == selectedSeason }
        val q = epQuery.trim().lowercase()
        if (q.isBlank()) list else list.filter {
            "${it.episode} ${it.title} ${it.overview}".lowercase().contains(q)
        }
    }

    val pageSize = 50
    val totalEpPages = remember(seasonEpisodes.size) { ((seasonEpisodes.size + pageSize - 1) / pageSize).coerceAtLeast(1) }
    val safeEpPage = epPage.coerceIn(0, totalEpPages - 1)
    val pagedEpisodes = remember(seasonEpisodes, safeEpPage, epQuery) {
        if (epQuery.isBlank() && seasonEpisodes.size > pageSize) {
            seasonEpisodes.drop(safeEpPage * pageSize).take(pageSize)
        } else {
            seasonEpisodes
        }
    }

    fun seasonLabel(s: Int): String {
        val custom = detail.seasonNames[s]
        return if (!custom.isNullOrBlank() && custom != "Season $s") custom else "Musim $s"
    }

    if (showTrailerDialog) {
        TrailerPopupDialog(
            title = item.title,
            year = item.year,
            initialYoutubeId = detail.trailerYoutubeId,
            onDismiss = { showTrailerDialog = false }
        )
    }

    if (showSeasonPicker) {
        Dialog(onDismissRequest = { showSeasonPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(pal.surface)
                    .border(1.dp, pal.border, RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pilih Musim (${seasons.size})", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary, modifier = Modifier.clickable { showSeasonPicker = false })
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(seasons) { s ->
                        val active = s == selectedSeason
                        val count = detail.episodes.count { it.season == s }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) pal.brandAccent.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable {
                                    selectedSeason = s
                                    epQuery = ""
                                    epPage = 0
                                    showSeasonPicker = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                seasonLabel(s),
                                color = if (active) Color.White else TextSecondary,
                                fontSize = 15.sp,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text("$count ep", color = if (active) pal.brandAccent else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(pal.bg)) {
        val isWide = maxWidth >= 680.dp || isTv
        val hPad = if (isTv) 48.dp else if (isWide) 28.dp else 16.dp
        val heroHeight = if (isWide) 400.dp else 350.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize().background(pal.bg),
            contentPadding = PaddingValues(bottom = 52.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1:1 WEB-PARITY CINEMATIC HERO BACKDROP
            item(key = "detail_hero") {
                Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
                    AsyncImage(
                        model = item.backdropUrl.ifBlank { item.posterUrl },
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Multi-layer vignette gradient matching Web .detail-hero::after
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.0f to Color(0x88000000),
                                0.35f to Color(0x28000000),
                                0.72f to pal.bg.copy(alpha = 0.78f),
                                1.0f to pal.bg
                            )
                        )
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                0.0f to pal.bg.copy(alpha = 0.72f),
                                0.55f to Color.Transparent,
                                1.0f to Color.Transparent
                            )
                        )
                    )

                    // Top Floating Glass Back Button
                    Row(
                        modifier = Modifier
                            .statusBarsPadding()
                            .fillMaxWidth()
                            .padding(horizontal = hPad, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color(0x990B0E0D))
                                .border(1.dp, pal.border, RoundedCornerShape(99.dp))
                                .clickable(onClick = onBack)
                                .padding(horizontal = if (isWide) 16.dp else 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White, modifier = Modifier.size(18.dp))
                            if (isWide) {
                                Text("Kembali", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Bottom Hero Logo/Title + Web-Parity Action Pills
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { 24 }),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = hPad, vertical = 18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            val effectiveLogo = item.logoUrl.ifBlank {
                                if (item.imdbId.startsWith("tt")) "https://images.metahub.space/logo/medium/${item.imdbId}/img" else ""
                            }
                            if (uiPrefs.useImageLogos && effectiveLogo.isNotBlank() && !logoFailed) {
                                AsyncImage(
                                    model = effectiveLogo,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Fit,
                                    alignment = Alignment.CenterStart,
                                    onError = { logoFailed = true },
                                    modifier = Modifier.height(if (isWide) 84.dp else 68.dp).fillMaxWidth(if (isWide) 0.55f else 0.78f)
                                )
                            } else {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = if (isWide) 34.sp else 26.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(Color.White)
                                        .clickable {
                                            val firstEp = seasonEpisodes.firstOrNull()
                                            onPlay(item, firstEp?.season ?: selectedSeason, firstEp?.episode ?: 1, detail.episodes)
                                        }
                                        .padding(horizontal = 26.dp, vertical = 12.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                    Text("Putar", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
                                }

                                Box(
                                    modifier = Modifier
                                        .scale(favScale)
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(if (isFavorite) pal.brandAccent else Color(0x33FFFFFF))
                                        .border(1.dp, if (isFavorite) pal.brandAccent else pal.border, CircleShape)
                                        .clickable { onToggleFavorite(item) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isFavorite) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = "Simpan ke Daftar Saya",
                                        tint = if (isFavorite) Color.Black else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x33FFFFFF))
                                        .border(1.dp, pal.border, CircleShape)
                                        .clickable {
                                            val shareText = "Nonton ${item.title} subtitle Indonesia gratis di SwaraPlay!"
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                                type = "text/plain"
                                            }
                                            runCatching { context.startActivity(Intent.createChooser(sendIntent, "Bagikan Tontonan")) }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Bagikan", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                if (uiPrefs.showTrailers && detail.trailerYoutubeId.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(Color(0x33FFFFFF))
                                            .border(1.dp, pal.border, RoundedCornerShape(99.dp))
                                            .clickable { showTrailerDialog = true }
                                            .padding(horizontal = 16.dp, vertical = 11.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Text("Trailer", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1:1 WEB-PARITY TWO-COLUMN BODY (Meta + Overview Left | Cast Strip Right on Tablet/TV)
            item(key = "detail_cols") {
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { 20 })
                ) {
                    @Composable
                    fun LeftMetaBlock(modifier: Modifier = Modifier) {
                        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.year.isNotBlank()) Text(item.year, color = Color(0xFFD4D8D5), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                if (detail.runtime.isNotBlank()) Text("• ${detail.runtime}", color = Color(0xFFD4D8D5), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                if (item.rating.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(15.dp))
                                        Text(item.rating, color = StarGold, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                Box(
                                    Modifier.clip(RoundedCornerShape(6.dp)).background(pal.surfaceElevated)
                                        .border(1.dp, pal.border, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text("HD", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Box(
                                    Modifier.clip(RoundedCornerShape(6.dp)).background(pal.surfaceElevated)
                                        .border(1.dp, pal.border, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text("CC", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                            if (item.genres.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(item.genres.take(5)) { g ->
                                        Box(
                                            Modifier.clip(RoundedCornerShape(99.dp))
                                                .background(pal.surface)
                                                .border(1.dp, pal.border, RoundedCornerShape(99.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(g, color = TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            if (item.overview.isNotBlank()) {
                                Text(item.overview, color = Color(0xFFD4D8D5), fontSize = 14.5.sp, lineHeight = 22.sp)
                            } else if (isLoading) {
                                Text("Memuat sinopsis & episode...", color = TextSecondary, fontSize = 13.5.sp)
                            }
                        }
                    }

                    @Composable
                    fun RightCastBlock(modifier: Modifier = Modifier) {
                        if (detail.cast.isNotEmpty()) {
                            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Pemeran: ${detail.cast.take(6).joinToString(", ") { it.name }}",
                                    color = TextSecondary,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(detail.cast.take(10), key = { "cast_${it.name}" }) { actor ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.width(70.dp).clickable { onActorClick(actor) }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(58.dp)
                                                    .clip(CircleShape)
                                                    .background(pal.surface)
                                                    .border(1.dp, pal.border, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (actor.photoUrl.isNotBlank()) {
                                                    AsyncImage(model = actor.photoUrl, contentDescription = actor.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                } else {
                                                    Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary)
                                                }
                                            }
                                            Spacer(Modifier.height(5.dp))
                                            Text(
                                                actor.name,
                                                color = TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                textAlign = TextAlign.Center,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = hPad),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            LeftMetaBlock(modifier = Modifier.weight(1.45f))
                            RightCastBlock(modifier = Modifier.weight(1f))
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = hPad),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            LeftMetaBlock()
                            RightCastBlock()
                        }
                    }
                }
            }

            // EPISODES SECTION WITH SEASON PICKER, PAGINATION CHIPS & SEARCH
            if (detail.episodes.isNotEmpty()) {
                item(key = "episodes_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = hPad),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(pal.surfaceElevated)
                                    .border(1.dp, pal.border, RoundedCornerShape(12.dp))
                                    .clickable { showSeasonPicker = true }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Text(seasonLabel(selectedSeason), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .width(if (isWide) 220.dp else 170.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(pal.surface)
                                    .border(1.dp, pal.border, RoundedCornerShape(99.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.weight(1f)) {
                                    if (epQuery.isEmpty()) Text("Cari episode...", color = TextSecondary, fontSize = 12.sp)
                                    BasicTextField(
                                        value = epQuery,
                                        onValueChange = { epQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                                        cursorBrush = SolidColor(pal.brandAccent),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        if (epQuery.isBlank() && totalEpPages > 1) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = hPad),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(totalEpPages) { pi ->
                                    val a = pi * pageSize + 1
                                    val b = minOf((pi + 1) * pageSize, seasonEpisodes.size)
                                    val active = pi == safeEpPage
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(if (active) Color.White else pal.surface)
                                            .border(1.dp, if (active) Color.White else pal.border, RoundedCornerShape(99.dp))
                                            .clickable { epPage = pi }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Ep $a–$b", color = if (active) Color.Black else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (uiPrefs.episodeGridMode) {
                            Column(
                                modifier = Modifier.padding(horizontal = hPad),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pagedEpisodes.forEach { ep ->
                                    EpisodeCardItem(
                                        ep = ep,
                                        fallbackThumb = item.backdropUrl.ifBlank { item.posterUrl },
                                        spoilerFree = uiPrefs.spoilerFreeMode,
                                        fullWidth = true,
                                        onClick = { onPlay(item, ep.season, ep.episode, detail.episodes) }
                                    )
                                }
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = hPad),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(pagedEpisodes, key = { "ep_${it.season}_${it.episode}" }) { ep ->
                                    EpisodeCardItem(
                                        ep = ep,
                                        fallbackThumb = item.backdropUrl.ifBlank { item.posterUrl },
                                        spoilerFree = uiPrefs.spoilerFreeMode,
                                        fullWidth = false,
                                        onClick = { onPlay(item, ep.season, ep.episode, detail.episodes) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // TRAILER CARD (Matched Web maxWidth 540.dp 16:9 proportion)
            if (uiPrefs.showTrailers && detail.trailerYoutubeId.isNotBlank()) {
                item(key = "trailer_box") {
                    Column(modifier = Modifier.padding(horizontal = hPad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Trailer Resmi", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Box(
                            modifier = Modifier
                                .widthIn(max = 540.dp)
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(pal.surface)
                                .border(1.dp, pal.border, RoundedCornerShape(16.dp))
                                .clickable { showTrailerDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = item.backdropUrl.ifBlank { item.posterUrl },
                                contentDescription = "Trailer",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 20.dp, vertical = 11.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Text("Putar Trailer", color = Color.Black, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            // SIMILAR TITLES
            if (detail.similar.isNotEmpty()) {
                item(key = "similar_row") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Mungkin Kamu Suka", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = hPad))
                        LazyRow(contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(detail.similar, key = { "sim_${it.id}" }) { sim ->
                                MediaPosterCard(item = sim, onClick = { onSimilarClick(sim) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCardItem(
    ep: EpisodeItem,
    fallbackThumb: String,
    spoilerFree: Boolean,
    fullWidth: Boolean,
    onClick: () -> Unit
) {
    val pal = LocalSwaraPalette.current
    Column(
        modifier = Modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.width(244.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(pal.surface)
            .border(1.dp, pal.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(pal.surfaceElevated)) {
            if (!spoilerFree) {
                AsyncImage(
                    model = ep.thumbnailUrl.ifBlank { fallbackThumb },
                    contentDescription = ep.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Mode Bebas Spoiler", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .clip(RoundedCornerShape(7.dp)).background(Color(0xC0000000))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text("E${ep.episode}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (ep.runtime.isNotBlank()) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .clip(RoundedCornerShape(7.dp)).background(Color(0xC0000000))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(ep.runtime, color = Color(0xFFE4E4E7), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = if (spoilerFree) "Episode ${ep.episode}" else ep.title.ifBlank { "Episode ${ep.episode}" },
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (spoilerFree) "Deskripsi disembunyikan" else ep.overview.ifBlank { "Musim ${ep.season} • Episode ${ep.episode}" },
                color = TextSecondary,
                fontSize = 11.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
