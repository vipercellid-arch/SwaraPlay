package com.swara.play.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swara.play.BuildConfig
import com.swara.play.R
import com.swara.play.model.AppReleaseInfo
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraCardElevated
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
fun SwaraUpdatePopup(
    availableUpdate: AppReleaseInfo?,
    isChecking: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    onCheckNow: () -> Unit,
    onDownloadInstall: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDonate: () -> Unit,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "updRing")
    val ringAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
        label = "updAngle"
    )

    Dialog(onDismissRequest = { if (!isDownloading) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(28.dp))
                .background(SwaraCard)
        ) {
            // Subtle rotating ring watermark in top-right (matching Video 2 00:14)
            Canvas(
                modifier = Modifier
                    .size(190.dp)
                    .align(Alignment.TopEnd)
                    .graphicsLayer { translationX = 56f; translationY = -48f; rotationZ = ringAngle }
            ) {
                drawArc(Color(0x14FFFFFF), 0f, 300f, false, style = Stroke(30f, cap = StrokeCap.Round))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.swaraplay_logo),
                    contentDescription = "SwaraPlay",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text("SwaraPlay", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(BuildConfig.VERSION_NAME, color = TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(SwaraCardElevated).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(if (availableUpdate?.isPrerelease == true) "PRE-RELEASE" else "STABLE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = when {
                        isChecking -> "Mencari pembaruan..."
                        availableUpdate != null -> "Versi baru ${availableUpdate.tagName}"
                        else -> "Versi terbaru"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = when {
                        isChecking -> "..."
                        availableUpdate != null -> "Pembaruan tersedia"
                        else -> "Sudah terbaru"
                    },
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { downloadProgress }, color = SwaraAccent, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Mengunduh ${(downloadProgress * 100).toInt()}%...", color = TextSecondary, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text("Aksi", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (availableUpdate != null) {
                        PopupActionRow(Icons.Default.Download, "Unduh & pasang ${availableUpdate.tagName}", highlight = true, onClick = onDownloadInstall)
                    }
                    PopupActionRow(Icons.Default.Refresh, if (isChecking) "Memeriksa..." else "Periksa pembaruan", onClick = onCheckNow)
                    PopupActionRow(Icons.Default.Info, "Tentang aplikasi", onClick = onOpenAbout)
                    PopupActionRow(Icons.Default.Favorite, "Dukung SwaraPlay", onClick = onOpenDonate)
                }

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onDismiss,
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(containerColor = SwaraCardElevated, contentColor = TextPrimary),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Selesai", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun SwaraAboutPopup(onOpenDonate: () -> Unit, onOpenDev: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(28.dp))
                .background(SwaraCard)
                .verticalScroll(rememberScrollState())
                .padding(22.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.swaraplay_logo),
                contentDescription = "SwaraPlay",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Tentang SwaraPlay", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("SwaraPlay v${BuildConfig.VERSION_NAME} • Oleh Teves", color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "SwaraPlay adalah aplikasi pemutar film, serial TV, drama Asia, dan anime modern buatan Teves dengan tampilan OLED hitam murni serta dukungan penuh untuk ponsel, tablet, dan Android TV.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PopupActionRow(Icons.Default.OpenInNew, "Pembuat & Pengembang (Teves)", onClick = onOpenDev)
                PopupActionRow(Icons.Default.OpenInNew, "Instagram Pembuat (@t3ves_)") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/t3ves_"))) }
                }
                PopupActionRow(Icons.Default.Favorite, "Dukung SwaraPlay (Donasi QRIS)", onClick = onOpenDonate)
            }
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SwaraCardElevated, contentColor = TextPrimary),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Selesai", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun SwaraDeveloperPopup(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .clip(RoundedCornerShape(28.dp))
                .background(SwaraCard)
                .padding(22.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.swaraplay_mascot),
                contentDescription = "Teves",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("Teves", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("@t3ves_ • Pembuat SwaraPlay", color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PopupActionRow(Icons.Default.OpenInNew, "Instagram (@t3ves_)") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/t3ves_"))) }
                }
                PopupActionRow(Icons.Default.OpenInNew, "TikTok (@miller_tipak)") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@miller_tipak"))) }
                }
                PopupActionRow(Icons.Default.OpenInNew, "GitHub (vipercellid-arch)") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/vipercellid-arch"))) }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SwaraCardElevated, contentColor = TextPrimary),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) { Text("Tutup", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun QrisDonateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showZoomDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val ringAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )

    if (showZoomDialog) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        Dialog(onDismissRequest = { showZoomDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xF0000000))) {
                Image(
                    painter = painterResource(id = R.drawable.qris_vipercell),
                    contentDescription = "Zoom QRIS ViperCell",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val ns = (scale * zoom).coerceIn(1f, 3.5f)
                                scale = ns
                                if (ns > 1f) {
                                    offsetX = (offsetX + pan.x).coerceIn(-300f, 300f)
                                    offsetY = (offsetY + pan.y).coerceIn(-300f, 300f)
                                } else {
                                    offsetX = 0f; offsetY = 0f
                                }
                            }
                        }
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }
                )
                IconButton(
                    onClick = { showZoomDialog = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).clip(CircleShape).background(SwaraCardElevated)
                ) { Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextPrimary) }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(28.dp))
                .background(SwaraCard)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(168.dp)) {
                Canvas(modifier = Modifier.size(168.dp).graphicsLayer { rotationZ = ringAngle }) {
                    drawArc(Color(0x18FFFFFF), 0f, 290f, false, style = Stroke(32f, cap = StrokeCap.Round))
                }
                Image(
                    painter = painterResource(id = R.drawable.swaraplay_mascot),
                    contentDescription = "Maskot SwaraPlay",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(126.dp).clip(CircleShape)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("Dukung SwaraPlay", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Dukunganmu membantu pengembangan SwaraPlay oleh Teves.", color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .clickable { showZoomDialog = true }
                    .padding(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.qris_vipercell),
                    contentDescription = "QRIS ViperCell",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Ketuk gambar QRIS untuk memperbesar", color = TextSecondary, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val saved = saveQrisToGallery(context)
                    Toast.makeText(context, if (saved) "QRIS disimpan ke Galeri" else "Gagal menyimpan QRIS", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("Simpan QRIS ke Galeri", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SwaraCardElevated, contentColor = TextPrimary),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) { Text("Tutup", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun PopupActionRow(icon: ImageVector, label: String, highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) Color.White else SwaraCardElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (highlight) Color.Black else TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = if (highlight) Color.Black else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun saveQrisToGallery(context: Context): Boolean {
    return try {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.qris_vipercell) ?: return false
        val filename = "QRIS_ViperCell_SwaraPlay_${System.currentTimeMillis()}.png"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SwaraPlay")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (_: Exception) {
        false
    }
}
