package com.swara.play.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.play.model.CalendarDaySchedule
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.isKidsFriendly
import com.swara.play.ui.components.AnimatedAmbientBackground
import com.swara.play.ui.components.MediaPosterCard
import com.swara.play.ui.theme.LocalSwaraPalette
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
fun CalendarScreen(
    days: List<CalendarDaySchedule>,
    fallbackMovies: List<MediaItem>,
    fallbackAnime: List<MediaItem>,
    isLoading: Boolean,
    isKidsMode: Boolean,
    isTv: Boolean,
    onLoadCalendar: () -> Unit,
    onMediaClick: (MediaItem) -> Unit
) {
    val pal = LocalSwaraPalette.current
    val hPad = if (isTv) 48.dp else 16.dp
    var selectedDayIdx by remember { mutableIntStateOf(0) }
    var filterCat by remember { mutableStateOf("ALL") }

    LaunchedEffect(Unit) { onLoadCalendar() }

    val activeDay = days.getOrNull(selectedDayIdx) ?: days.firstOrNull()
    val rawDayItems = remember(activeDay, fallbackMovies, fallbackAnime, selectedDayIdx) {
        val base = activeDay?.items.orEmpty()
        val extraMovies = fallbackMovies.drop(selectedDayIdx * 3).take(4)
        val extraAnime = fallbackAnime.drop(selectedDayIdx * 3).take(4)
        (base + extraAnime + extraMovies).distinctBy { it.id }
    }
    val filteredItems = remember(rawDayItems, filterCat, isKidsMode) {
        val kidsSafe = if (isKidsMode) rawDayItems.filter { it.isKidsFriendly() } else rawDayItems
        when (filterCat) {
            "SERIES" -> kidsSafe.filter { it.category == MediaCategory.SERIES }
            "MOVIE" -> kidsSafe.filter { it.category == MediaCategory.MOVIE }
            "ANIME" -> kidsSafe.filter { it.category == MediaCategory.ANIME }
            else -> kidsSafe
        }
    }
    val cols = if (isTv) 5 else 3
    val rows = remember(filteredItems, cols) { filteredItems.chunked(cols) }

    AnimatedAmbientBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = if (isTv) 86.dp else 88.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "cal_header") {
                Column(modifier = Modifier.padding(horizontal = hPad), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Kalender Rilis", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = if (isKidsMode) "Jadwal rilis animasi & keluarga 7 hari ke depan" else "Jadwal rilis episode & film terbaru 7 hari ke depan",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            if (days.isNotEmpty()) {
                item(key = "cal_days") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = hPad),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(days) { idx, day ->
                            val active = idx == selectedDayIdx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (active) Color.White else pal.surface)
                                    .border(1.dp, if (active) Color.White else pal.border, RoundedCornerShape(14.dp))
                                    .focusable()
                                    .clickable { selectedDayIdx = idx }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = "${day.shortLabel} (${day.items.size})",
                                    color = if (active) Color.Black else TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item(key = "cal_filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = hPad),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chips = listOf("ALL" to "Semua", "SERIES" to "Serial TV", "MOVIE" to "Film", "ANIME" to "Anime")
                    itemsIndexed(chips) { _, (code, lbl) ->
                        val active = filterCat == code
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (active) pal.brandAccent else pal.surface)
                                .border(1.dp, if (active) pal.brandAccent else pal.border, RoundedCornerShape(99.dp))
                                .focusable()
                                .clickable { filterCat = code }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(lbl, color = if (active) Color.Black else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (isLoading && filteredItems.isEmpty()) {
                item(key = "cal_loading") {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = pal.brandAccent)
                    }
                }
            } else if (filteredItems.isEmpty()) {
                item(key = "cal_empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Belum ada jadwal rilis pada filter ini.", color = TextSecondary, fontSize = 13.5.sp)
                    }
                }
            } else {
                items(rows.size, key = { "cal_row_${selectedDayIdx}_${filterCat}_$it" }) { rIdx ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = hPad),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rows[rIdx].forEach { item ->
                            MediaPosterCard(
                                item = item,
                                onClick = { onMediaClick(item) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(cols - rows[rIdx].size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
