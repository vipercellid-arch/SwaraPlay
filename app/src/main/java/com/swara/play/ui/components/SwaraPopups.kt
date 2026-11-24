package com.swara.play.ui.components

import androidx.compose.material3.CircularProgressIndicator

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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.DisposableEffect
import coil.compose.AsyncImage
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
import com.swara.play.model.ActorProfile
import com.swara.play.model.AppReleaseInfo
import com.swara.play.model.ConnectedDevice
import com.swara.play.model.DeviceIntegrationPrefs
import com.swara.play.model.MediaItem
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraCardElevated
import com.swara.play.ui.theme.SwaraStableGreen
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
private fun SwaraDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 8.dp)
            .width(46.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(Color(0x55FFFFFF))
    )
}

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
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.90f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0E0E11))
        ) {
            Canvas(
                modifier = Modifier
                    .size(180.dp)
                    .align(Alignment.TopEnd)
                    .graphicsLayer { translationX = 52f; translationY = -44f; rotationZ = ringAngle }
            ) {
                drawArc(Color(0x16FFFFFF), 0f, 300f, false, style = Stroke(30f, cap = StrokeCap.Round))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SwaraDragHandle() }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(SwaraCardElevated)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.swaraplay_logo),
                        contentDescription = "SwaraPlay",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(30.dp).clip(RoundedCornerShape(7.dp))
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text("SwaraPlay", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(BuildConfig.VERSION_NAME, color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, SwaraStableGreen, RoundedCornerShape(12.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(if (availableUpdate?.isPrerelease == true) "PRE-RELEASE" else "STABLE", color = SwaraStableGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SwaraCard)
                        .padding(16.dp)
                ) {
                    Text(
                        text = when {
                            isChecking -> "Mencari pembaruan..."
                            availableUpdate != null -> "Versi baru ${availableUpdate.tagName}"
                            else -> "Versi terbaru"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
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
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { downloadProgress }, color = SwaraAccent, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Mengunduh ${(downloadProgress * 100).toInt()}%...", color = TextSecondary, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Aksi", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (availableUpdate != null) {
                        SwaraActionPill(Icons.Default.Download, "Unduh & pasang ${availableUpdate.tagName}", highlight = true, onClick = onDownloadInstall)
                    }
                    SwaraActionPill(Icons.Default.Refresh, if (isChecking) "Memeriksa..." else "Periksa pembaruan", onClick = onCheckNow)
                    SwaraActionPill(Icons.Default.Info, "Tentang aplikasi", onClick = onOpenAbout)
                    SwaraActionPill(Icons.Default.Favorite, "Dukung SwaraPlay!", onClick = onOpenDonate)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E3E44), contentColor = TextPrimary),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) { Text("Selesai", fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(10.dp))
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
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.90f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0E0E11))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            SwaraDragHandle()
            Spacer(modifier = Modifier.height(6.dp))
            Image(
                painter = painterResource(id = R.drawable.swaraplay_logo),
                contentDescription = "SwaraPlay",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("Tentang SwaraPlay", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("SwaraPlay v${BuildConfig.VERSION_NAME} • Oleh Teves", color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "SwaraPlay adalah aplikasi pemutar film, serial TV, drama Asia, dan anime buatan Teves dengan tampilan OLED hitam murni serta dukungan ponsel, tablet, dan Android TV.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SwaraActionPill(Icons.Default.OpenInNew, "Pembuat (Teves)", onClick = onOpenDev)
                SwaraActionPill(Icons.Default.OpenInNew, "Instagram (@t3ves_)") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/t3ves_"))) }
                }
                SwaraActionPill(Icons.Default.Favorite, "Dukung SwaraPlay (QRIS)", onClick = onOpenDonate)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E3E44), contentColor = TextPrimary),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) { Text("Selesai", fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.height(8.dp))
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
                .widthIn(max = 330.dp)
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF0E0E11))
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            SwaraDragHandle()
            Spacer(modifier = Modifier.height(8.dp))
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
                DevLinkPill("Instagram (@t3ves_)") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/t3ves_"))) }
                }
                DevLinkPill("TikTok (@miller_tipak)") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@miller_tipak"))) }
                }
                DevLinkPill("GitHub (vipercellid-arch)") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/vipercellid-arch"))) }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
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
        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.90f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0E0E11))
        ) {
            Canvas(
                modifier = Modifier
                    .size(190.dp)
                    .align(Alignment.TopEnd)
                    .graphicsLayer { translationX = 48f; translationY = -20f; rotationZ = ringAngle }
            ) {
                drawArc(Color(0x14FFFFFF), 0f, 290f, false, style = Stroke(32f, cap = StrokeCap.Round))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                SwaraDragHandle()
                Spacer(modifier = Modifier.height(6.dp))
                Image(
                    painter = painterResource(id = R.drawable.swaraplay_mascot),
                    contentDescription = "Maskot SwaraPlay",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(116.dp).clip(CircleShape)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Dukung SwaraPlay", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Dukunganmu membantu pengembangan SwaraPlay oleh Teves.", color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                        .clickable { showZoomDialog = true }
                        .padding(8.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.qris_vipercell),
                        contentDescription = "QRIS ViperCell",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(300.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ketuk gambar QRIS untuk memperbesar", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val saved = saveQrisToGallery(context)
                        Toast.makeText(context, if (saved) "QRIS disimpan ke Galeri" else "Gagal menyimpan QRIS", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text("Simpan QRIS ke Galeri", fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SwaraCardElevated, contentColor = TextPrimary),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text("Tutup", fontWeight = FontWeight.SemiBold) }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SwaraActionPill(icon: ImageVector, label: String, highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) Color.White else SwaraCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).clip(CircleShape).background(if (highlight) Color.Black else Color.White)
        ) {
            Icon(icon, contentDescription = null, tint = if (highlight) Color.White else Color.Black, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = if (highlight) Color.Black else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DevLinkPill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SwaraCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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


@Composable
fun DeviceIntegrationPopup(
    prefs: DeviceIntegrationPrefs,
    savedDevices: List<ConnectedDevice>,
    discoveredDevices: List<ConnectedDevice>,
    isScanning: Boolean,
    isFromPlayer: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onUpdatePrefs: (DeviceIntegrationPrefs) -> Unit,
    onAddManualDevice: (String, String) -> Unit,
    onSelectDevice: (ConnectedDevice) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showManualInput by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("") }
    var manualIp by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onStartScan()
        onDispose { onStopScan() }
    }

    val allDevices = remember(savedDevices, discoveredDevices) {
        (savedDevices + discoveredDevices).distinctBy { it.host }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF101012))
                .border(0.75.dp, SwaraSubtleBorder, RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(4.dp)
                    .clip(CircleShape).background(Color(0xFF45454D))
            )

            Text("Integrasi Perangkat", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SwaraCard).padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Text("→", color = TextSecondary, fontSize = 14.sp)
                    Icon(Icons.Default.Tv, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                }
                Text(
                    "Hubungkan hingga 10 perangkat di Wi-Fi kamu: HP lain, tablet, TV, head unit mobil, dan lainnya.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF151519))
                    .border(1.dp, Color(0xFF3A3A44), RoundedCornerShape(16.dp))
                    .clickable { showManualInput = !showManualInput; onStartScan() }
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                Text(
                    if (isScanning) "Memindai perangkat di Wi-Fi..." else "Tambah perangkat di sini",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (showManualInput) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SwaraCard).padding(12.dp)) {
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text("Nama Perangkat (misal: TV Ruang Tamu)", color = TextSecondary, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SwaraAccent, unfocusedBorderColor = SwaraSubtleBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("IP Wi-Fi (misal: 192.168.1.12)", color = TextSecondary, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SwaraAccent, unfocusedBorderColor = SwaraSubtleBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (manualIp.isNotBlank()) {
                                onAddManualDevice(manualName, manualIp)
                                manualName = ""; manualIp = ""; showManualInput = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Hubungkan Perangkat", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
            }

            if (allDevices.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allDevices.forEach { dev ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SwaraCard)
                                .clickable { onSelectDevice(dev) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(if (dev.deviceType == "TV") Icons.Default.Tv else Icons.Default.PhoneAndroid, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dev.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (isFromPlayer) "Ketuk untuk kirim tontonan • ${dev.host}" else dev.host, color = TextSecondary, fontSize = 11.sp)
                            }
                            IconButton(onClick = { onRemoveDevice(dev.id) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Text("OPSI", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SwaraCard).padding(14.dp)
            ) {
                DeviceOptionSwitchRow(
                    icon = Icons.Default.Cast,
                    title = "Tetap terhubung di latar belakang?",
                    subtitle = "Perangkat dapat sinkron, streaming, dan dikontrol meski SwaraPlay ditutup.",
                    checked = prefs.keepConnectedBackground,
                    onCheckedChange = { onUpdatePrefs(prefs.copy(keepConnectedBackground = it)) }
                )
                DeviceOptionSwitchRow(
                    icon = Icons.Default.Notifications,
                    title = "Beri tahu saat TV tersinkronisasi",
                    subtitle = "Notifikasi singkat saat TV terhubung dan menerima pustaka terbaru.",
                    checked = prefs.notifyWhenTvSyncs,
                    onCheckedChange = { onUpdatePrefs(prefs.copy(notifyWhenTvSyncs = it)) }
                )
                DeviceOptionSwitchRow(
                    icon = Icons.Default.PlaylistPlay,
                    title = "Tampilkan perangkat di menu pemutar",
                    subtitle = "Putar, lanjutkan episode, atau kontrol perangkat langsung dari menu pemutar.",
                    checked = prefs.showInPlayerMenu,
                    onCheckedChange = { onUpdatePrefs(prefs.copy(showInPlayerMenu = it)) }
                )
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42424A), contentColor = TextPrimary),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) { Text("Selesai", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun DeviceOptionSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp).clip(CircleShape).background(SwaraCardElevated)) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(17.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White)
        )
    }
}

@Composable
fun ActorProfileDialog(
    profile: ActorProfile,
    isLoading: Boolean,
    onSelectMedia: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF101012))
                .border(0.75.dp, SwaraSubtleBorder, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(68.dp).clip(CircleShape).background(SwaraCardElevated)) {
                    if (profile.photoUrl.isNotBlank()) {
                        AsyncImage(model = profile.photoUrl, contentDescription = profile.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(34.dp))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    if (profile.subtitle.isNotBlank()) {
                        Text(profile.subtitle, color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            if (isLoading) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
                    CircularProgressIndicator(color = SwaraAccent, modifier = Modifier.size(28.dp))
                }
            } else {
                if (profile.biography.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SwaraCard).padding(12.dp)) {
                        Text("Profil & Biodata", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(profile.biography, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }

                if (profile.filmography.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Film & Serial yang Dibintangi", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(profile.filmography, key = { "af_${it.id}" }) { media ->
                                MediaPosterCard(item = media, onClick = { onSelectMedia(media) })
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42424A), contentColor = TextPrimary),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) { Text("Tutup", fontWeight = FontWeight.Bold) }
        }
    }
}
