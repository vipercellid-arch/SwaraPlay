package com.swara.play.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swara.play.model.AppReleaseInfo
import com.swara.play.ui.theme.CinemaCrimson
import com.swara.play.ui.theme.OledSurface
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary

@Composable
fun UpdateDialog(
    release: AppReleaseInfo,
    isDownloading: Boolean,
    downloadProgress: Float,
    onInstallClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        containerColor = OledSurface,
        title = { Text("Pembaruan Tersedia (${release.tagName})", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(release.notes.take(400), color = TextSecondary, fontSize = 13.sp)
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(progress = { downloadProgress }, color = CinemaCrimson, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Mengunduh APK ${(downloadProgress * 100).toInt()}%...", color = TextPrimary, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onInstallClick,
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(containerColor = CinemaCrimson)
            ) {
                Text(if (isDownloading) "Mengunduh..." else "Unduh & Pasang")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDownloading) {
                Text("Nanti", color = TextSecondary)
            }
        }
    )
}
