package com.swara.play.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.play.BuildConfig
import com.swara.play.model.PerformanceMode
import com.swara.play.model.UserProfile
import com.swara.play.ui.components.UserAvatar
import com.swara.play.ui.theme.CinemaCrimson
import com.swara.play.ui.theme.OledBlack
import com.swara.play.ui.theme.OledBorder
import com.swara.play.ui.theme.OledSurface
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    userProfile: UserProfile,
    adBlockEnabled: Boolean,
    dohEnabled: Boolean,
    performanceMode: PerformanceMode,
    customAddons: List<String>,
    updateStatusText: String?,
    isWideLayout: Boolean,
    onSaveProfile: (String, String?) -> Unit,
    onToggleAdBlock: (Boolean) -> Unit,
    onToggleDoh: (Boolean) -> Unit,
    onSelectPerformanceMode: (PerformanceMode) -> Unit,
    onAddAddon: (String) -> Unit,
    onRemoveAddon: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenDonate: () -> Unit
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var draftName by remember(userProfile.displayName) { mutableStateOf(userProfile.displayName) }
    var draftPhoto by remember(userProfile.photoUri) { mutableStateOf<String?>(null) }
    var addonInput by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            draftPhoto = uri.toString()
            onSaveProfile(userProfile.displayName, uri.toString())
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = OledSurface,
            title = { Text("Edit Profil Pengguna", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = { Text("Nama Tampilan", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinemaCrimson,
                            unfocusedBorderColor = OledBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { photoPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pilih Foto dari Galeri")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveProfile(draftName, draftPhoto)
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaCrimson)
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (isWideLayout) 32.dp else 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().background(OledBlack)
    ) {
        item(key = "title") {
            Text("Profil & Setelan", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // User Custom Profile Card (Name & Photo from device)
        item(key = "user_profile_card") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(OledSurface)
                    .border(1.dp, OledBorder, RoundedCornerShape(18.dp))
                    .clickable {
                        draftName = userProfile.displayName
                        showEditDialog = true
                    }
                    .padding(16.dp)
            ) {
                UserAvatar(
                    profile = userProfile,
                    size = 64.dp,
                    modifier = Modifier.clickable { photoPicker.launch(arrayOf("image/*")) }
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(userProfile.displayName, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Ketuk foto atau ikon pensil untuk ubah profil", color = TextSecondary, fontSize = 12.sp)
                    Text("SwaraPlay v${BuildConfig.VERSION_NAME} • Gratis Tanpa Login", color = CinemaCrimson, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = {
                    draftName = userProfile.displayName
                    showEditDialog = true
                }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Profil", tint = CinemaCrimson)
                }
            }
        }

        item(key = "perf_mode_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(OledSurface)
                    .border(1.dp, OledBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = CinemaCrimson, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Mode Performa", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(performanceMode.desc, color = TextSecondary, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PerformanceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = performanceMode == mode,
                            onClick = { onSelectPerformanceMode(mode) },
                            label = { Text(mode.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CinemaCrimson,
                                selectedLabelColor = TextPrimary,
                                containerColor = OledBlack,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
            }
        }

        item(key = "doh_card") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(OledSurface)
                    .border(1.dp, OledBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Dns, contentDescription = null, tint = CinemaCrimson, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("DNS-over-HTTPS (Cloudflare)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Bypass blokir DNS ISP untuk katalog & API", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = dohEnabled,
                    onCheckedChange = onToggleDoh,
                    colors = SwitchDefaults.colors(checkedThumbColor = TextPrimary, checkedTrackColor = CinemaCrimson)
                )
            }
        }

        item(key = "adblock_card") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(OledSurface)
                    .border(1.dp, OledBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = CinemaCrimson, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pemblokir Iklan Bawaan", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Blokir popup & iklan tanpa ganggu stream video", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = adBlockEnabled,
                    onCheckedChange = onToggleAdBlock,
                    colors = SwitchDefaults.colors(checkedThumbColor = TextPrimary, checkedTrackColor = CinemaCrimson)
                )
            }
        }

        item(key = "addons_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(OledSurface)
                    .border(1.dp, OledBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Text("Stremio Addons (Opsional)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Tambah URL manifest addon untuk ExoPlayer native", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = addonInput,
                        onValueChange = { addonInput = it },
                        placeholder = { Text("https://.../manifest.json", fontSize = 12.sp, color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CinemaCrimson,
                            unfocusedBorderColor = OledBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (addonInput.isNotBlank()) {
                            onAddAddon(addonInput)
                            addonInput = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah", tint = CinemaCrimson)
                    }
                }
                customAddons.forEach { url ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(url, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemoveAddon(url) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = CinemaCrimson, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        item(key = "updater_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(OledSurface)
                    .border(1.dp, OledBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = CinemaCrimson, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Pembaruan Otomatis (GitHub Releases)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("v${BuildConfig.VERSION_NAME} • vipercellid-arch/SwaraPlay", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                if (!updateStatusText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(updateStatusText, color = CinemaCrimson, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onCheckUpdate,
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaCrimson),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cek Pembaruan Sekarang", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item(key = "donate_card") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(OledSurface)
                    .border(1.dp, CinemaCrimson, RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenDonate)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, tint = CinemaCrimson, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dukung SwaraPlay — QRIS ViperCell", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Ketuk untuk buka sheet donasi & simpan QRIS", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
