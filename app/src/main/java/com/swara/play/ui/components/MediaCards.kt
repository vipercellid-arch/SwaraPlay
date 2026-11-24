package com.swara.play.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.WatchProgress
import com.swara.play.ui.theme.StarGold
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraCardElevated
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
fun MediaPosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rankBadge: Int? = null
) {
    var focused by remember { mutableStateOf(false) }
    var useBackdropFallback by remember(item.id) { mutableStateOf(false) }
    var imageFailed by remember(item.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.05f else 1f, label = "cardScale")
    val activeImageUrl = if (!useBackdropFallback) item.posterUrl.ifBlank { item.backdropUrl } else item.backdropUrl

    Column(
        modifier = modifier
            .width(126.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(SwaraCardElevated)
                .border(if (focused) 2.dp else 0.5.dp, if (focused) SwaraAccent else SwaraSubtleBorder, RoundedCornerShape(14.dp))
        ) {
            if (!imageFailed && activeImageUrl.isNotBlank()) {
                AsyncImage(
                    model = activeImageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    onError = {
                        if (!useBackdropFallback && item.backdropUrl.isNotBlank() && item.backdropUrl != item.posterUrl) {
                            useBackdropFallback = true
                        } else {
                            imageFailed = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(10.dp)
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(item.title, color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            )
            if (rankBadge != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (rankBadge <= 3) StarGold else Color(0xCC000000)).padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text("$rankBadge", color = if (rankBadge <= 3) Color.Black else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            if (item.rating.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000)).padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(item.rating, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            val bottomTag = if (item.totalEpisodes > 0) "Total ${item.totalEpisodes} Eps" else item.category.label
            Text(
                text = bottomTag,
                color = Color(0xFFEAEAEA),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(item.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.year.isNotBlank()) Text(item.year, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
fun ContinueWatchingCard(progress: WatchProgress, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.04f else 1f, label = "resumeScale")

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(14.dp))
            .background(SwaraCard)
            .border(if (focused) 2.dp else 0.5.dp, if (focused) SwaraAccent else SwaraSubtleBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(SwaraBlack)) {
            AsyncImage(model = progress.posterUrl, contentDescription = progress.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.align(Alignment.Center).size(38.dp).clip(RoundedCornerShape(19.dp)).background(Color(0xEEFFFFFF))
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Lanjut Nonton", tint = Color.Black, modifier = Modifier.size(22.dp))
            }
            if (progress.category != MediaCategory.MOVIE) {
                Text("S${progress.season} • E${progress.episode}", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
        LinearProgressIndicator(
            progress = { if (progress.progressFraction > 0f) progress.progressFraction else 0.15f },
            color = SwaraAccent,
            trackColor = SwaraSubtleBorder,
            modifier = Modifier.fillMaxWidth().height(3.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(10.dp)) {
            Text(progress.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(progress.category.label, color = TextSecondary, fontSize = 11.sp)
        }
    }
}
