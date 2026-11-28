package com.swara.play.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.swara.play.model.UserProfileAccount
import com.swara.play.ui.components.AnimatedAmbientBackground
import com.swara.play.ui.theme.LocalSwaraPalette
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

/**
 * Mature Profile Picker Screen (100% D-Pad friendly for Android TV & touch-friendly for Mobile/Tablet).
 * Supports Kids Profile mode restricting the entire catalog to Animation & Family content only.
 */
@Composable
fun ProfilePickerScreen(
    profiles: List<UserProfileAccount>,
    activeProfileId: String,
    isTv: Boolean,
    onSelectProfile: (String) -> Unit,
    onAddProfile: (String, Boolean) -> Unit
) {
    val pal = LocalSwaraPalette.current
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newIsKids by remember { mutableStateOf(false) }

    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(pal.surface)
                    .border(1.dp, pal.border, RoundedCornerShape(22.dp))
                    .padding(20.dp)
            ) {
                Text("Tambah Profil Baru", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(pal.surfaceElevated)
                        .border(1.dp, pal.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (newName.isEmpty()) Text("Nama profil (mis. Adik, Keluarga)", color = TextSecondary, fontSize = 13.sp)
                    BasicTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(pal.brandAccent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Mode Profil Anak", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Hanya menampilkan film & serial animasi ramah anak", color = TextSecondary, fontSize = 11.5.sp)
                    }
                    Switch(
                        checked = newIsKids,
                        onCheckedChange = { newIsKids = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = pal.brandAccent)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showAddDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = pal.surfaceElevated, contentColor = TextPrimary),
                        shape = RoundedCornerShape(99.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Batal", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onAddProfile(newName.trim(), newIsKids)
                                newName = ""
                                newIsKids = false
                                showAddDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(99.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Simpan", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }

    AnimatedAmbientBackground {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = if (isTv) 64.dp else 24.dp)
        ) {
            Text(
                text = "Siapa yang Menonton?",
                color = Color.White,
                fontSize = if (isTv) 34.sp else 26.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Pilih profil utama atau Profil Anak untuk katalog khusus animasi.",
                color = TextSecondary,
                fontSize = if (isTv) 15.sp else 13.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(34.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(profiles, key = { it.id }) { prof ->
                    val active = prof.id == activeProfileId
                    val cardSize = if (isTv) 148.dp else 120.dp
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(cardSize)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (active) pal.brandAccent.copy(alpha = 0.22f) else pal.surface)
                            .border(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) pal.brandAccent else pal.border,
                                shape = RoundedCornerShape(22.dp)
                            )
                            .focusable()
                            .clickable { onSelectProfile(prof.id) }
                            .padding(vertical = 18.dp, horizontal = 12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(if (isTv) 72.dp else 58.dp)
                                .clip(CircleShape)
                                .background(if (prof.isKids) pal.brandAccent.copy(alpha = 0.25f) else pal.surfaceElevated)
                        ) {
                            Text(prof.avatarEmoji, fontSize = if (isTv) 34.sp else 28.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(prof.name, color = Color.White, fontSize = if (isTv) 16.sp else 14.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (prof.isKids) "Mode Anak" else "Semua Katalog",
                            color = if (prof.isKids) pal.brandAccent else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (active) {
                            Spacer(Modifier.height(6.dp))
                            Icon(Icons.Default.Check, contentDescription = "Aktif", tint = pal.brandAccent, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                item(key = "add_profile_btn") {
                    val cardSize = if (isTv) 148.dp else 120.dp
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .width(cardSize)
                            .height(if (isTv) 178.dp else 150.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(pal.surface)
                            .border(1.dp, pal.border, RoundedCornerShape(22.dp))
                            .focusable()
                            .clickable { showAddDialog = true }
                            .padding(14.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(pal.surfaceElevated)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Tambah Profil", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Tambah Profil", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
