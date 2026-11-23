package com.swara.play.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.play.BuildConfig
import com.swara.play.R
import com.swara.play.ui.components.QrisDonateDialog
import com.swara.play.ui.theme.CinemaCrimson
import com.swara.play.ui.theme.OledBlack
import com.swara.play.ui.theme.OledBorder
import com.swara.play.ui.theme.OledSurface
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    adBlockEnabled: Boolean,
    customAddons: List<String>,
    updateStatusText: String?,
    onToggleAdBlock: (Boolean) -> Unit,
    onAddAddon: (String) -> Unit,
    onRemoveAddon: (String) -> Unit,
    onCheckUpdate: () -> Unit
) {
    val context = LocalContext.current
    var showQris by remember { mutableStateOf(false) }
    var addonInput by remember { mutableStateOf("") }

    if (showQris) {
        QrisDonateDialog(onDismiss = { showQris = false })
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        item(key = "title") {
            Text(
                text = "Setelan SwaraPlay",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item(key = "mascot_brand_card") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OledSurface)
                    .border(1.dp, OledBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.swaraplay_mascot),
                    contentDescription = "Maskot SwaraPlay",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .border(2.dp, CinemaCrimson, CircleShape)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("SwaraPlay v${BuildConfig.VERSION_NAME}", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("OLED Cinema • oleh Teves (ViperCell)", color = TextSecondary, fontSize = 12.sp)
                    Text("Tanpa TMDB API Key • AdBlock & Wyzie Subs", color = CinemaCrimson, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        item(key = "adblock_card") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OledSurface)
                    .border(1.dp, OledBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = CinemaCrimson, modifier = Modifier.size(26.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pemblokir Iklan Bawaan", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Blokir popup, redirect, dan domain iklan di player", color = TextSecondary, fontSize = 12.sp)
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(OledSurface)
                    .border(1.dp, OledBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text("Stremio Addons (Opsional)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Tambahkan URL manifest addon Stremio untuk stream ExoPlayer langsung",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
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
                    IconButton(
                        onClick = {
                            if (addonInput.isNotBlank()) {
                                onAddAddon(addonInput)
                                addonInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah", tint = CinemaCrimson)
                    }
                }
                customAddons.forEach { url ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(OledSurface)
                    .border(1.dp, OledBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = CinemaCrimson, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Pembaruan Otomatis (GitHub Releases)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Versi terpasang: v${BuildConfig.VERSION_NAME} • vipercellid-arch/SwaraPlay", color = TextSecondary, fontSize = 12.sp)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OledSurface)
                    .border(1.dp, CinemaCrimson, RoundedCornerShape(16.dp))
                    .clickable { showQris = true }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = CinemaCrimson, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Dukung Developer — QRIS ViperCell", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Ketuk untuk melihat QRIS (Zoom & Simpan ke Galeri)", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/vipercellid-arch")))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("vipercellid-arch", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/t3ves_")))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("@t3ves_", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
