package com.swara.play.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swara.play.model.UserProfile
import com.swara.play.ui.theme.SwaraCardElevated
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary

@Composable
fun UserAvatar(profile: UserProfile, size: Dp = 64.dp, modifier: Modifier = Modifier) {
    val initials = profile.displayName.trim().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "TV" }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).clip(CircleShape).background(SwaraCardElevated).border(1.5.dp, SwaraSubtleBorder, CircleShape)
    ) {
        if (profile.photoUri.isNotBlank()) {
            AsyncImage(model = Uri.parse(profile.photoUri), contentDescription = profile.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(text = initials, color = TextPrimary, fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
