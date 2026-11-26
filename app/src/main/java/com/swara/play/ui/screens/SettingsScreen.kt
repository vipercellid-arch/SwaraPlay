package com.swara.play.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.play.BuildConfig
import com.swara.play.model.ContentRestrictionPrefs
import com.swara.play.model.MediaItem
import com.swara.play.model.SubtitleLanguagePref
import com.swara.play.model.UserProfile
import com.swara.play.ui.components.MediaPosterCard
import com.swara.play.ui.components.UserAvatar
import com.swara.play.ui.theme.SwaraAccent
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraCard
import com.swara.play.ui.theme.SwaraCardElevated
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    watchMinutes: Int,
    onSaveProfile: (String, String?) -> Unit,
    onOpenFullSettings: () -> Unit,
    onOpenUpdatePopup: () -> Unit,
    onOpenAboutPopup: () -> Unit,
    onOpenDeveloperPopup: () -> Unit,
    onOpenDonatePopup: () -> Unit,
    onOpenDeviceIntegration: () -> Unit,
    history: List<MediaItem> = emptyList(),
    onMediaClick: (MediaItem) -> Unit = {}
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var draftName by remember(userProfile.displayName) { mutableStateOf(userProfile.displayName) }
    var draftPhoto by remember(userProfile.photoUri) { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            draftPhoto = uri.toString()
            onSaveProfile(userProfile.displayName, uri.toString())
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = SwaraCard,
            title = { Text("Edit Kartu Profil", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = { Text("Nama Tampilan", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SwaraAccent, unfocusedBorderColor = SwaraSubtleBorder,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pilih Foto dari Galeri")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSaveProfile(draftName, draftPhoto); showEditDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) { Text("Simpan", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Batal", color = TextSecondary) } }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().background(SwaraBlack).statusBarsPadding()
    ) {
        // Profile Card cleanly at the TOP (OLED Black, HiTV-inspired clarity)
        item(key = "top_profile_card") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(SwaraCard)
                    .border(0.6.dp, SwaraSubtleBorder, RoundedCornerShape(22.dp))
                    .clickable { draftName = userProfile.displayName; showEditDialog = true }
                    .padding(18.dp)
            ) {
                UserAvatar(
                    profile = userProfile,
                    size = 68.dp,
                    modifier = Modifier.clickable { photoPicker.launch(arrayOf("image/*")) }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("KARTU PROFIL", color = TextSecondary, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(userProfile.displayName, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(userProfile.joinedDate, color = TextSecondary, fontSize = 12.sp)
                }
                IconButton(
                    onClick = onOpenFullSettings,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(SwaraCardElevated)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Pengaturan", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Horizontal Riwayat Tontonan Row (right below Profile Card!)
        if (history.isNotEmpty()) {
            item(key = "profile_history_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Text("Riwayat Tontonan", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(history.take(15), key = { "ph_${it.id}" }) { media ->
                            MediaPosterCard(item = media, onClick = { onMediaClick(media) })
                        }
                    }
                }
            }
        }

        // Version & Update Card
        item(key = "version_card") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SwaraCard)
                    .border(0.5.dp, SwaraSubtleBorder, RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenUpdatePopup)
                    .padding(16.dp)
            ) {
                Column {
                    Text("Versi & pembaruan aplikasi", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("v${BuildConfig.VERSION_NAME}", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SwaraCardElevated)
                        .clickable(onClick = onOpenAboutPopup)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("Tentang aplikasi", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        // Clean Vertical Menu List (OLED Black)
        item(key = "device_tile") { SwaraListTile("SYNC", "Integrasi Perangkat", "Hubungkan HP, Tablet & Android TV di Wi-Fi", onOpenDeviceIntegration) }
        item(key = "donate_tile") { SwaraListTile("QRIS", "Dukung SwaraPlay (Donasi QRIS)", "QRIS ViperCell", onOpenDonatePopup) }
        item(key = "dev_tile") { SwaraListTile("DEV", "Dibuat oleh Teves (@t3ves_)", "Profil Pembuat", onOpenDeveloperPopup) }
        item(key = "open_settings_tile") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SwaraCard)
                    .border(0.5.dp, SwaraSubtleBorder, RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenFullSettings)
                    .padding(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp).clip(CircleShape).background(SwaraCardElevated)) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pengaturan Lengkap", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("DNS-over-HTTPS, PiP, Pemblokiran Konten & Cache", color = TextSecondary, fontSize = 12.sp)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun SettingsDetailScreen(
    dohEnabled: Boolean,
    pipEnabled: Boolean,
    contentRestriction: ContentRestrictionPrefs,
    subtitleLangPref: SubtitleLanguagePref,
    onBack: () -> Unit,
    onToggleDoh: (Boolean) -> Unit,
    onTogglePip: (Boolean) -> Unit,
    onUpdateContentRestriction: (ContentRestrictionPrefs) -> Unit,
    onSelectSubtitleLang: (SubtitleLanguagePref) -> Unit,
    onClearHistoryAndCache: () -> Unit,
    onOpenUpdatePopup: () -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(SwaraBlack).statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary) }
            Spacer(modifier = Modifier.width(6.dp))
            Text("Pengaturan", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }

        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "doh") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SwaraCard).padding(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DNS-over-HTTPS (Cloudflare)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Bantu akses katalog tetap lancar di semua jaringan", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = dohEnabled,
                        onCheckedChange = onToggleDoh,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White)
                    )
                }
            }

            item(key = "pip_mini") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SwaraCard).padding(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pemutaran Mini (Picture-in-Picture)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Munculkan jendela video melayang saat keluar dari aplikasi", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = pipEnabled,
                        onCheckedChange = onTogglePip,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White)
                    )
                }
            }

            item(key = "content_block") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SwaraCard).padding(16.dp)) {
                    Text("Pemblokiran Konten", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("SwaraPlay tidak akan merekomendasikan jenis konten berikut lagi:", color = TextSecondary, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("18+ / Konten Dewasa", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Sembunyikan film, drama, dan anime berlabel 18+", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = contentRestriction.blockAdult18,
                            onCheckedChange = { onUpdateContentRestriction(contentRestriction.copy(blockAdult18 = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White)
                        )
                    }
                    HorizontalDivider(color = SwaraSubtleBorder)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("LGBTQ", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Sembunyikan konten bertema LGBTQ / BL / GL", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = contentRestriction.blockLgbt,
                            onCheckedChange = { onUpdateContentRestriction(contentRestriction.copy(blockLgbt = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White)
                        )
                    }
                }
            }

            item(key = "sub_lang") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SwaraCard).padding(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bahasa Aplikasi & Subtitle", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Khusus Bahasa Indonesia dengan audio asli negara asal (tanpa dubbing)", color = TextSecondary, fontSize = 12.sp)
                    }
                    Text("ID", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            item(key = "clear") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SwaraCard)
                        .clickable {
                            onClearHistoryAndCache()
                            Toast.makeText(context, "Riwayat & cache dibersihkan", Toast.LENGTH_SHORT).show()
                        }
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Bersihkan Riwayat & Cache", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Hapus daftar riwayat tontonan di perangkat ini", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            item(key = "update_btn") {
                Button(
                    onClick = onOpenUpdatePopup,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Periksa Pembaruan", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SwaraListTile(badgeText: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SwaraCard)
            .border(0.5.dp, SwaraSubtleBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp).clip(CircleShape).background(SwaraCardElevated)) {
            Text(badgeText, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}
