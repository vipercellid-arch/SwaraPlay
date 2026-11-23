package com.swara.play.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.ui.components.MediaPosterCard
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

private val areaOptions = listOf("Semua Area", "Korea Selatan", "Jepang", "Amerika", "Cina", "Thailand", "Indonesia")
private val genreOptions = listOf("Semua Kategori", "Aksi", "Romansa", "Fantasi", "Animasi", "Misteri", "Fiksi Ilmiah", "Horor", "Komedi", "Thriller", "Drama")
private val sortOptions = listOf("Populer", "Terbaru", "Skor Tinggi")

@Composable
fun ExploreScreen(
    results: List<MediaItem>,
    isSearching: Boolean,
    isWideLayout: Boolean,
    onSearch: (String, MediaCategory) -> Unit,
    onFilterChanged: (MediaCategory, String, String, Int) -> Unit,
    onMediaClick: (MediaItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MediaCategory.ALL) }
    var selectedArea by remember { mutableStateOf("Semua Area") }
    var selectedGenre by remember { mutableStateOf("Semua Kategori") }
    var selectedSort by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(SwaraBlack).statusBarsPadding().padding(top = 12.dp)) {
        Text("Daftar Konten", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Cari Film, Serial TV, Drama Asia, Anime...", color = TextSecondary, fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            trailingIcon = { IconButton(onClick = { onSearch(query, category) }) { Icon(Icons.Default.Search, contentDescription = "Cari", tint = TextPrimary) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query, category) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SwaraAccent, unfocusedBorderColor = SwaraSubtleBorder,
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
        }
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(genreOptions, key = { "g_$it" }) { genre ->
                FilterPill(genre, selectedGenre == genre) { selectedGenre = genre; query = ""; onFilterChanged(category, selectedArea, genre, selectedSort) }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(sortOptions, key = { i, _ -> "s_$i" }) { idx, sort ->
                FilterPill(sort, selectedSort == idx) { selectedSort = idx; onFilterChanged(category, selectedArea, selectedGenre, idx) }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        if (isSearching) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { CircularProgressIndicator(color = SwaraAccent) }
        } else if (results.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Text("Tidak ada konten untuk filter ini.", color = TextSecondary, fontSize = 14.sp) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isWideLayout) 6 else 3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results, key = { "exp_${it.id}_${it.category.name}" }) { item -> MediaPosterCard(item = item, onClick = { onMediaClick(item) }) }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color.White else SwaraCard)
            .border(0.5.dp, if (selected) Color.White else SwaraSubtleBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) Color.Black else TextSecondary, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
