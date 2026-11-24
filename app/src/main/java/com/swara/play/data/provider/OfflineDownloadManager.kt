package com.swara.play.data.provider

import android.content.Context
import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.OfflineDownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

class OfflineDownloadManager(private val context: Context) {

    suspend fun fetchBestSubtitleUrl(imdbId: String, season: Int?, episode: Int?, langIso: String): String =
        withContext(Dispatchers.IO) {
            if (imdbId.isBlank()) return@withContext ""
            val url = buildString {
                append("https://sub.wyzie.io/search?id=").append(imdbId)
                if (season != null && episode != null && season > 0 && episode > 0) {
                    append("&season=").append(season).append("&episode=").append(episode)
                }
            }
            try {
                val req = Request.Builder().url(url).header("User-Agent", HttpClientProvider.USER_AGENT).build()
                HttpClientProvider.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext ""
                    val arr = JSONArray(resp.body?.string() ?: return@withContext "")
                    var fallback = ""
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val subUrl = o.optString("url", "")
                        if (subUrl.isBlank()) continue
                        val code = o.optString("language", "").lowercase()
                        if (code == langIso || (langIso == "id" && (code == "ind" || code == "in"))) {
                            return@withContext subUrl
                        }
                        if (fallback.isBlank() && (code == "en" || code == "eng")) fallback = subUrl
                    }
                    fallback
                }
            } catch (_: Exception) {
                ""
            }
        }

    suspend fun downloadStreamToOffline(
        item: MediaItem,
        season: Int,
        episode: Int,
        streamUrl: String,
        referer: String,
        langIso: String,
        onProgress: (Float) -> Unit
    ): OfflineDownloadItem? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "downloads").apply { mkdirs() }
            val safeId = "${item.id}_${season}_${episode}".replace(Regex("[^a-zA-Z0-9_]"), "_")
            val ext = if (streamUrl.contains(".m3u8", ignoreCase = true)) "m3u8" else "mp4"
            val videoFile = File(dir, "$safeId.$ext")

            val reqBuilder = Request.Builder().url(streamUrl).header("User-Agent", HttpClientProvider.USER_AGENT)
            if (referer.isNotBlank()) reqBuilder.header("Referer", referer)

            HttpClientProvider.client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(videoFile).use { output ->
                        val buf = ByteArray(16 * 1024)
                        var copied = 0L
                        while (true) {
                            val r = input.read(buf)
                            if (r <= 0) break
                            output.write(buf, 0, r)
                            copied += r
                            if (total > 0L) onProgress((copied.toFloat() / total.toFloat()).coerceIn(0f, 0.95f))
                        }
                    }
                }
            }

            // Also download single best synchronized subtitle file if available
            var localSubPath = ""
            if (item.imdbId.isNotBlank()) {
                val isSeries = item.category != MediaCategory.MOVIE
                val subUrl = fetchBestSubtitleUrl(
                    imdbId = item.imdbId,
                    season = if (isSeries) season else null,
                    episode = if (isSeries) episode else null,
                    langIso = langIso
                )
                if (subUrl.isNotBlank()) {
                    val subFile = File(dir, "$safeId.srt")
                    runCatching {
                        val sReq = Request.Builder().url(subUrl).header("User-Agent", HttpClientProvider.USER_AGENT).build()
                        HttpClientProvider.client.newCall(sReq).execute().use { sResp ->
                            if (sResp.isSuccessful) {
                                sResp.body?.byteStream()?.use { sin ->
                                    FileOutputStream(subFile).use { sout -> sin.copyTo(sout) }
                                }
                                localSubPath = subFile.absolutePath
                            }
                        }
                    }
                }
            }

            onProgress(1f)
            OfflineDownloadItem(
                id = safeId,
                mediaId = item.id,
                title = item.title,
                posterUrl = item.posterUrl,
                category = item.category,
                season = season,
                episode = episode,
                localVideoPath = videoFile.absolutePath,
                localSubtitlePath = localSubPath,
                fileSizeBytes = videoFile.length()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun deleteDownload(download: OfflineDownloadItem) {
        runCatching { File(download.localVideoPath).delete() }
        if (download.localSubtitlePath.isNotBlank()) {
            runCatching { File(download.localSubtitlePath).delete() }
        }
    }
}
