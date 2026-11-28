package com.swara.play.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.StreamingProviderInfo
import com.swara.play.model.WatchProgress
import com.swara.play.ui.theme.LocalSwaraPalette
import com.swara.play.ui.theme.StarGold
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
fun MediaPosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rankBadge: Int? = null,
    cardWidth: Dp = 132.dp,
    fillWidth: Boolean = false
) {
    val pal = LocalSwaraPalette.current
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(cardWidth))
            .graphicsLayer {
                val s = if (isFocused) 1.05f else 1f
                scaleX = s
                scaleY = s
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(pal.surface)
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) pal.brandAccent else pal.border,
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            if (item.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(32.dp))
                }
            }

            if (item.rating.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xB8000000))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(11.dp))
                    Text(item.rating, color = StarGold, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (rankBadge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text("#$rankBadge", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        Text(
            text = item.title,
            color = TextPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = buildString {
                if (item.year.isNotBlank()) append("${item.year} • ")
                append(if (item.category == MediaCategory.MOVIE) "Film" else "Serial")
            },
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ContinueWatchingCard(
    progress: WatchProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEditing: Boolean = false,
    onRemove: (() -> Unit)? = null
) {
    val pal = LocalSwaraPalette.current
    var isFocused by remember { mutableStateOf(false) }
    val leftMinutes = if (progress.durationMs > 0L) {
        ((progress.durationMs - progress.positionMs).coerceAtLeast(0L) / 60_000L).toInt()
    } else 0
    val leftLabel = when {
        leftMinutes >= 60 -> "${leftMinutes / 60}j ${leftMinutes % 60}m tersisa"
        leftMinutes > 0 -> "${leftMinutes}m tersisa"
        else -> "Lanjutkan"
    }

    Column(
        modifier = modifier
            .width(236.dp)
            .graphicsLayer {
                val s = if (isFocused) 1.04f else 1f
                scaleX = s
                scaleY = s
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(pal.surface)
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) pal.brandAccent else pal.border,
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            AsyncImage(
                model = progress.posterUrl,
                contentDescription = progress.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }

            if (isEditing && onRemove != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0x3DFFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.progressFraction.coerceIn(0.06f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(pal.brandAccent)
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(progress.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = if (progress.category != MediaCategory.MOVIE) "S${progress.season} E${progress.episode} • $leftLabel" else leftLabel,
            color = TextSecondary,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ProviderTileCard(
    provider: StreamingProviderInfo,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = LocalSwaraPalette.current
    var isFocused by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(78.dp)
            .graphicsLayer {
                val s = if (isFocused) 1.07f else 1f
                scaleX = s
                scaleY = s
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(pal.surface)
                .border(
                    width = if (isFocused || selected) 2.dp else 1.dp,
                    color = if (isFocused || selected) pal.brandAccent else pal.border,
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = provider.shortLabel,
                color = Color(provider.accentHex),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = provider.name,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
