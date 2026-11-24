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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.ui.components.MediaPosterCard
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.ui.theme.TextTertiary

private val areaOptions = listOf("Semua Area", "Korea Selatan", "Jepang", "Amerika", "Cina", "Thailand", "Indonesia")
private val genreOptions = listOf("Semua Kategori", "Aksi", "Romansa", "Fantasi", "Animasi", "Misteri", "Fiksi Ilmiah", "Horor", "Komedi", "Thriller", "Drama")
private val sortOptions = listOf("Populer", "Terbaru", "Skor Tinggi")

@Composable
fun ExploreScreen(
    results: List<MediaItem>,
    isSearching: Boolean,
    isLoadingMore: Boolean,
    isWideLayout: Boolean,
    onSearch: (String, MediaCategory) -> Unit,
    onFilterChanged: (MediaCategory, String, String, Int) -> Unit,
    onLoadMore: () -> Unit,
    onMediaClick: (MediaItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MediaCategory.ALL) }
    var selectedArea by remember { mutableStateOf("Semua Area") }
    var selectedGenre by remember { mutableStateOf("Semua Kategori") }
    var selectedSort by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val cols = if (isWideLayout) 6 else 3
    val showNumberedTrending = query.isBlank() && category == MediaCategory.ALL && selectedArea == "Semua Area" && selectedGenre == "Semua Kategori"

    val shouldLoadMore by remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 6 && lastVisible >= total - 6
        }
    }

    LaunchedEffect(shouldLoadMore, isSearching, isLoadingMore) {
        if (shouldLoadMore && !isSearching && !isLoadingMore) onLoadMore()
    }

    Column(modifier = Modifier.fillMaxSize().background(SwaraBlack).statusBarsPadding().padding(top = 14.dp)) {
        Text(
            text = "Temukan sesuatu.",
            color = TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Film, serial, drama, anime...", color = TextTertiary, fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onSearch(query, category) }) {
                        Icon(Icons.Default.Search, contentDescription = "Cari", tint = TextPrimary)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query, category) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White, unfocusedBorderColor = SwaraSubtleBorder,
                focusedContainerColor = SwaraCard, unfocusedContainerColor = SwaraCard,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MediaCategory.entries, key = { "t_${it.name}" }) { cat ->
                FilterPill(cat.label, category == cat) { category = cat; query = ""; onFilterChanged(cat, selectedArea, selectedGenre, selectedSort) }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(areaOptions, key = { "a_$it" }) { area ->
                FilterPill(area, selectedArea == area) { selectedArea = area; query = ""; onFilterChanged(category, area, selectedGenre, selectedSort) }
            }
            items(genreOptions.drop(1), key = { "g_$it" }) { genre ->
                FilterPill(genre, selectedGenre == genre) { selectedGenre = if (selectedGenre == genre) "Semua Kategori" else genre; query = ""; onFilterChanged(category, selectedArea, selectedGenre, selectedSort) }
            }
            itemsIndexed(sortOptions, key = { i, _ -> "s_$i" }) { idx, sort ->
                FilterPill(sort, selectedSort == idx) { selectedSort = idx; onFilterChanged(category, selectedArea, selectedGenre, idx) }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (isSearching) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { CircularProgressIndicator(color = SwaraAccent) }
        } else if (results.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Text("Tidak ada konten untuk filter ini.", color = TextSecondary, fontSize = 14.sp) }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(cols),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 118.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (showNumberedTrending) {
                    item(span = { GridItemSpan(cols) }, key = "tr_hdr") {
                        Text("TRENDING HARI INI", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    }
                    val topTen = results.take(8)
                    items(count = topTen.size, key = { "tr_row_${topTen[it].id}" }, span = { _ -> GridItemSpan(cols) }) { idx ->
                        val item = topTen[idx]
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { onMediaClick(item) }.padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = String.format("%02d", idx + 1),
                                    color = if (idx < 3) TextPrimary else TextTertiary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.width(38.dp)
                                )
                                AsyncImage(
                                    model = item.posterUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(44.dp).height(62.dp).clip(RoundedCornerShape(8.dp)).background(SwaraCard)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = listOfNotNull(item.category.label, item.year.take(4).takeIf { it.isNotBlank() }, item.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }).joinToString(" • "),
                                        color = TextSecondary, fontSize = 12.sp
                                    )
                                }
                            }
                            HorizontalDivider(color = Color(0x14FFFFFF))
                        }
                    }
                    item(span = { GridItemSpan(cols) }, key = "more_grid_hdr") {
                        Text("JELAJAHI KATALOG", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(results.drop(8), key = { "exp_${it.id}_${it.category.name}" }) { item ->
                        MediaPosterCard(item = item, onClick = { onMediaClick(item) })
                    }
                } else {
                    items(results, key = { "exp_${it.id}_${it.category.name}" }) { item ->
                        MediaPosterCard(item = item, onClick = { onMediaClick(item) })
                    }
                }

                if (isLoadingMore) {
                    item(span = { GridItemSpan(cols) }, key = "exp_load_more") {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                            CircularProgressIndicator(color = SwaraAccent, modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White else SwaraCard)
            .border(0.5.dp, if (selected) Color.White else SwaraSubtleBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, color = if (selected) Color(0xFF0B0B0F) else TextSecondary, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
