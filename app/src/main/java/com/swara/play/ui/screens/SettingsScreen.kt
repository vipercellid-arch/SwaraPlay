package com.swara.play.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.play.BuildConfig
import com.swara.play.model.AppThemeId
import com.swara.play.model.AppUiPrefs
import com.swara.play.model.SubtitleStylePrefs
import com.swara.play.model.UserProfile
import com.swara.play.ui.components.UserAvatar
import com.swara.play.ui.theme.LocalSwaraPalette
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    uiPrefs: AppUiPrefs,
    userProfile: UserProfile,
    subtitleStyle: SubtitleStylePrefs,
    dohEnabled: Boolean,
    pipEnabled: Boolean,
    isTv: Boolean,
    onUpdateUiPrefs: ((AppUiPrefs) -> AppUiPrefs) -> Unit,
    onUpdateSubtitleStyle: (SubtitleStylePrefs) -> Unit,
    onSetDoh: (Boolean) -> Unit,
    onSetPip: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDonate: () -> Unit
) {
    val pal = LocalSwaraPalette.current
    val hPad = if (isTv) 48.dp else 16.dp
    val subColors = listOf(0xFFFFFFFFL, 0xFFFACC15L, 0xFF22D3EEL, 0xFF4ADE80L, 0xFFE879F9L)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(pal.glowPrimary.copy(alpha = 0.28f), pal.bg, pal.bg)))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = hPad, end = hPad, top = if (isTv) 86.dp else 94.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Pengaturan", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }

            // 1. DATA LOKAL & PROFIL (TANPA LOGIN)
            item {
                SettingsGlassCard(title = "Penyimpanan Lokal & Aplikasi", subtitle = "Tanpa login — semua progres tontonan, daftar, dan riwayat tersimpan lokal.") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UserAvatar(profile = userProfile, size = 46.dp)
                        Column {
                            Text(userProfile.displayName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Mode Lokal Aktif • Tanpa Login", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsActionChip("Cek Update (v${BuildConfig.VERSION_NAME})", onOpenUpdate)
                        SettingsActionChip("Perangkat / TV", onOpenDevices)
                        SettingsActionChip("Donasi QRIS", onOpenDonate)
                    }
                }
            }

            // 2. TAMPILAN (5 TEMA + GAYA EPISODE + LOGO + TRAILER + BEBAS SPOILER)
            item {
                SettingsGlassCard(title = "Tampilan", subtitle = "Atur warna tema, gaya episode, dan tampilan halaman detail.") {
                    Text("Tema Warna", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AppThemeId.entries) { theme ->
                            val active = uiPrefs.themeId == theme
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (active) Color.White else pal.surfaceElevated)
                                    .border(1.dp, if (active) Color.White else pal.border, RoundedCornerShape(99.dp))
                                    .clickable { onUpdateUiPrefs { it.copy(themeId = theme) } }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(theme.label, color = if (active) Color.Black else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Gaya Daftar Episode", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Pilih Carousel menyamping atau Grid ke bawah", color = TextSecondary, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SettingsToggleChip("Carousel", !uiPrefs.episodeGridMode) { onUpdateUiPrefs { it.copy(episodeGridMode = false) } }
                            SettingsToggleChip("Grid", uiPrefs.episodeGridMode) { onUpdateUiPrefs { it.copy(episodeGridMode = true) } }
                        }
                    }

                    SettingsSwitchRow("Gunakan Logo Gambar", "Tampilkan logo gambar judul pada Hero & Detail", uiPrefs.useImageLogos) { v ->
                        onUpdateUiPrefs { it.copy(useImageLogos = v) }
                    }
                    SettingsSwitchRow("Tampilkan Trailer", "Tampilkan pratinjau trailer YouTube di halaman Detail", uiPrefs.showTrailers) { v ->
                        onUpdateUiPrefs { it.copy(showTrailers = v) }
                    }
                    SettingsSwitchRow("Mode Bebas Spoiler", "Sembunyikan thumbnail & deskripsi episode", uiPrefs.spoilerFreeMode) { v ->
                        onUpdateUiPrefs { it.copy(spoilerFreeMode = v) }
                    }
                }
            }

            // 3. PEMUTARAN
            item {
                SettingsGlassCard(title = "Pemutaran", subtitle = "Perilaku pemutar video, lompat waktu, dan jaringan.") {
                    SettingsSwitchRow("Putar Otomatis Episode Berikutnya", "Lanjut otomatis saat episode selesai", uiPrefs.autoplayNext) { v ->
                        onUpdateUiPrefs { it.copy(autoplayNext = v) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Durasi Lompat Waktu", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5, 10, 15, 30).forEach { sec ->
                                SettingsToggleChip("${sec}d", uiPrefs.seekSeconds == sec) {
                                    onUpdateUiPrefs { it.copy(seekSeconds = sec) }
                                }
                            }
                        }
                    }
                    SettingsSwitchRow("Picture-in-Picture (PiP)", "Lanjutkan video di jendela mengambang saat keluar", pipEnabled, onSetPip)
                    SettingsSwitchRow("DNS-over-HTTPS (Cloudflare)", "Bantu melewati blokir ISP untuk akses katalog & stream", dohEnabled, onSetDoh)
                }
            }

            // 4. SUBTITLE
            item {
                SettingsGlassCard(title = "Subtitle", subtitle = "Sesuaikan ukuran, warna, dan latar belakang subtitle.") {
                    Text("Ukuran Teks (${(subtitleStyle.fontSizeScale * 100).toInt()}%)", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = subtitleStyle.fontSizeScale,
                        onValueChange = { onUpdateSubtitleStyle(subtitleStyle.copy(fontSizeScale = it)) },
                        valueRange = 0.75f..1.8f,
                        colors = SliderDefaults.colors(thumbColor = pal.brandAccent, activeTrackColor = pal.brandAccent)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Warna Teks", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            subColors.forEach { hex ->
                                val active = subtitleStyle.textColorHex == hex
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(if (active) 2.5.dp else 1.dp, if (active) pal.brandAccent else Color.White.copy(alpha = 0.3f), CircleShape)
                                        .clickable { onUpdateSubtitleStyle(subtitleStyle.copy(textColorHex = hex)) }
                                )
                            }
                        }
                    }
                    SettingsSwitchRow("Latar Belakang Subtitle", "Kotak gelap transparan di belakang teks", subtitleStyle.showBackground) { v ->
                        onUpdateSubtitleStyle(subtitleStyle.copy(showBackground = v))
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xAA000000))
                            .padding(vertical = 18.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (subtitleStyle.showBackground) Color(0xAA0A0C0A) else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                "Contoh subtitle Bahasa Indonesia di SwaraPlay.",
                                color = Color(subtitleStyle.textColorHex),
                                fontSize = (14f * subtitleStyle.fontSizeScale).sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 5. LAINNYA
            item {
                SettingsGlassCard(title = "Tentang & Pemeliharaan", subtitle = "SwaraPlay v${BuildConfig.VERSION_NAME} — 100% EncDecEndpoints.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsActionChip("Tentang Aplikasi", onOpenAbout)
                        SettingsActionChip("Hapus Riwayat", onClearHistory)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGlassCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val pal = LocalSwaraPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(pal.surface.copy(alpha = 0.85f))
            .border(1.dp, pal.border, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val pal = LocalSwaraPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(hint, color = TextSecondary, fontSize = 11.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = pal.brandAccent)
        )
    }
}

@Composable
private fun SettingsToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    val pal = LocalSwaraPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (active) Color.White else pal.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (active) Color.Black else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsActionChip(label: String, onClick: () -> Unit) {
    val pal = LocalSwaraPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(pal.surfaceElevated)
            .border(1.dp, pal.border, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    ) {
        Text(label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
