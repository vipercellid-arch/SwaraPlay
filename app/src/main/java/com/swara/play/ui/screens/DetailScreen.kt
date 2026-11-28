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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
    val pal = LocalSwaraPalette.current
    val hPad = if (isTv) 48.dp else 16.dp
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
    var logoFailed by remember(item.id) { mutableStateOf(false) }
    var showTrailerDialog by remember(item.id) { mutableStateOf(false) }

    val seasonEpisodes = remember(detail.episodes, selectedSeason, epQuery) {
        val list = detail.episodes.filter { it.season == selectedSeason }
        val q = epQuery.trim().lowercase()
        if (q.isBlank()) list else list.filter {
            "${it.episode} ${it.title} ${it.overview}".lowercase().contains(q)
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
                                .background(if (active) pal.brandAccent.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    selectedSeason = s
                                    epQuery = ""
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(pal.bg),
        contentPadding = PaddingValues(bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // HERO BACKDROP
        item(key = "detail_hero") {
            Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                AsyncImage(
                    model = item.backdropUrl.ifBlank { item.posterUrl },
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0x66000000), Color(0x22000000), pal.bg))
                    )
                )
                Row(
                    modifier = Modifier.statusBarsPadding().padding(horizontal = hPad, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x99000000))
                            .border(1.dp, pal.border, CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = hPad, vertical = 14.dp)
                ) {
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
                            modifier = Modifier.height(66.dp).fillMaxWidth(0.75f)
                        )
                    } else {
                        Text(item.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.White)
                                .clickable {
                                    val firstEp = seasonEpisodes.firstOrNull()
                                    onPlay(item, firstEp?.season ?: selectedSeason, firstEp?.episode ?: 1, detail.episodes)
                                }
                                .padding(horizontal = 24.dp, vertical = 11.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Text("Putar", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isFavorite) pal.brandAccent else Color(0x33FFFFFF))
                                .clickable { onToggleFavorite(item) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isFavorite) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = "Simpan",
                                tint = if (isFavorite) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }

        // METADATA & SYNOPSIS
        item(key = "detail_meta") {
            Column(modifier = Modifier.padding(horizontal = hPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (item.year.isNotBlank()) Text(item.year, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (detail.runtime.isNotBlank()) Text("• ${detail.runtime}", color = TextSecondary, fontSize = 13.sp)
                    if (item.rating.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(14.dp))
                            Text(item.rating, color = StarGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(pal.surfaceElevated).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text("HD", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(pal.surfaceElevated).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text("CC", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (item.overview.isNotBlank()) {
                    Text(item.overview, color = Color(0xFFD4D8D5), fontSize = 13.5.sp, lineHeight = 20.sp)
                } else if (isLoading) {
                    Text("Memuat sinopsis & episode...", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }

        // EPISODES SECTION WITH SEASON PICKER & SEARCH
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
                                .width(170.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(pal.surface)
                                .border(1.dp, pal.border, RoundedCornerShape(99.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.weight(1f)) {
                                if (epQuery.isEmpty()) Text("Cari episode", color = TextSecondary, fontSize = 12.sp)
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

                    if (uiPrefs.episodeGridMode) {
                        Column(
                            modifier = Modifier.padding(horizontal = hPad),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            seasonEpisodes.forEach { ep ->
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
                            items(seasonEpisodes, key = { "ep_${it.season}_${it.episode}" }) { ep ->
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

        // CAST PORTRAITS
        if (detail.cast.isNotEmpty()) {
            item(key = "cast_row") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pemeran", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = hPad))
                    LazyRow(contentPadding = PaddingValues(horizontal = hPad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(detail.cast.take(12), key = { "cast_${it.name}" }) { actor ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(72.dp).clickable { onActorClick(actor) }
                            ) {
                                Box(
                                    modifier = Modifier.size(58.dp).clip(CircleShape).background(pal.surface).border(1.dp, pal.border, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (actor.photoUrl.isNotBlank()) {
                                        AsyncImage(model = actor.photoUrl, contentDescription = actor.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary)
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(actor.name, color = TextPrimary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // TRAILER
        if (uiPrefs.showTrailers && detail.trailerYoutubeId.isNotBlank()) {
            item(key = "trailer_box") {
                Column(modifier = Modifier.padding(horizontal = hPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Trailer Resmi", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
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
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Text("Putar Trailer", color = Color.Black, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // SIMILAR
        if (detail.similar.isNotEmpty()) {
            item(key = "similar_row") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Mungkin Kamu Suka", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = hPad))
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
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.width(232.dp))
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
