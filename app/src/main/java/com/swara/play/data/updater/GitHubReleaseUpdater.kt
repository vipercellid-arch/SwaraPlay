package com.swara.play.data.updater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.swara.play.BuildConfig
import com.swara.play.MainActivity
import com.swara.play.R
import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.AppReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

class GitHubReleaseUpdater {
    companion object {
        const val REPO_OWNER = "vipercellid-arch"
        const val REPO_NAME = "SwaraPlay"
        const val RELEASES_API = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"
        private const val CHANNEL_ID = "swaraplay_updates"
        private const val NOTIF_ID = 5001
    }

    suspend fun checkForUpdate(): AppReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(RELEASES_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SwaraPlay-Android/${BuildConfig.VERSION_NAME}")
                .build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val raw = resp.body?.string() ?: return@withContext null
                val releases = JSONArray(raw)
                val currentBaseVersion = BuildConfig.VERSION_NAME.substringBefore("-")
                for (i in 0 until releases.length()) {
                    val rel = releases.optJSONObject(i) ?: continue
                    if (rel.optBoolean("draft", false)) continue
                    val tag = rel.optString("tag_name", "")
                    val ver = tag.removePrefix("v").removePrefix("V")
                    val assets = rel.optJSONArray("assets") ?: continue
                    var matchedApkUrl = ""
                    var fallbackApkUrl = ""
                    for (j in 0 until assets.length()) {
                        val asset = assets.optJSONObject(j) ?: continue
                        val name = asset.optString("name", "")
                        if (!name.endsWith(".apk", ignoreCase = true)) continue
                        val url = asset.optString("browser_download_url", "")
                        if (fallbackApkUrl.isBlank()) fallbackApkUrl = url
                        val isTvAsset = name.contains("-TV-", ignoreCase = true)
                        if (BuildConfig.IS_TV_BUILD && isTvAsset) {
                            matchedApkUrl = url
                            break
                        } else if (!BuildConfig.IS_TV_BUILD && !isTvAsset) {
                            matchedApkUrl = url
                            break
                        }
                    }
                    val finalUrl = matchedApkUrl.ifBlank { fallbackApkUrl }
                    if (finalUrl.isNotBlank() && isNewerVersion(ver, currentBaseVersion)) {
                        return@withContext AppReleaseInfo(
                            tagName = tag,
                            versionName = ver,
                            title = rel.optString("name", "SwaraPlay $tag").ifBlank { "SwaraPlay $tag" },
                            notes = rel.optString("body", "Pembaruan terbaru tersedia."),
                            apkDownloadUrl = finalUrl,
                            htmlUrl = rel.optString("html_url", "https://github.com/$REPO_OWNER/$REPO_NAME/releases"),
                            isPrerelease = rel.optBoolean("prerelease", false)
                        )
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun showUpdateNotification(context: Context, release: AppReleaseInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Pembaruan SwaraPlay", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Versi baru SwaraPlay tersedia (${release.tagName})")
            .setContentText("Ketuk untuk mengunduh dan memasang pembaruan.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, notif) }
    }

    suspend fun downloadApk(context: Context, release: AppReleaseInfo, onProgress: (Float) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val outFile = File(updatesDir, "SwaraPlay-${release.tagName}.apk")
                val req = Request.Builder().url(release.apkDownloadUrl).header("User-Agent", "SwaraPlay-Android/${BuildConfig.VERSION_NAME}").build()
                HttpClientProvider.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body ?: return@withContext null
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(outFile).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var downloaded = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0L) onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                onProgress(1f)
                outFile
            } catch (_: Exception) {
                null
            }
        }

    fun launchInstaller(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val rParts = remote.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val cParts = current.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
