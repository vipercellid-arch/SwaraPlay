package com.swara.play.data.provider

import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

object WyzieSubtitleResolver {

    suspend fun fetchBestSynchronizedVtt(
        item: MediaItem,
        season: Int,
        episode: Int,
        langIso: String
    ): String = withContext(Dispatchers.IO) {
        val imdb = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else "" }
        if (imdb.isBlank()) return@withContext ""
        val lang = langIso.lowercase().ifBlank { "id" }
        val url = if (item.category == MediaCategory.MOVIE) {
            "https://sub.wyzie.io/search?id=$imdb&language=$lang&format=vtt"
        } else {
            "https://sub.wyzie.io/search?id=$imdb&season=$season&episode=$episode&language=$lang&format=vtt"
        }
        val body = httpGet(url) ?: return@withContext ""
        try {
            val arr = JSONArray(body)
            if (arr.length() == 0) return@withContext ""

            var bestUrl = ""
            var bestScore = Int.MIN_VALUE

            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val subUrl = o.optString("url", "")
                if (subUrl.isBlank()) continue
                val display = (o.optString("display", "") + " " + o.optString("fileName", "")).lowercase()
                val isHi = o.optBoolean("isHearingImpaired", false)
                val encoding = o.optString("encoding", "").lowercase()
                var score = 100 - i * 3
                if (!isHi) score += 40
                if (display.contains("web-dl") || display.contains("webdl") || display.contains("webrip") || display.contains("nf") || display.contains("amzn") || display.contains("dsnp")) {
                    score += 65
                }
                if (display.contains("bluray") || display.contains("brrip")) score += 25
                if (display.contains("cam") || display.contains("ts") || display.contains("hdcam") || display.contains("telesync")) {
                    score -= 120
                }
                if (encoding.contains("utf")) score += 15
                if (score > bestScore) {
                    bestScore = score
                    bestUrl = subUrl
                }
            }

            if (bestUrl.isBlank()) return@withContext ""
            val rawSub = httpGet(bestUrl) ?: return@withContext ""
            normalizeToVtt(rawSub)
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun resolveYoutubeTrailerId(title: String, year: String = ""): String = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return@withContext ""
        try {
            val q = URLEncoder.encode("$cleanTitle ${year.take(4)} official trailer".trim(), "UTF-8")
            val html = httpGet("https://www.youtube.com/results?search_query=$q") ?: return@withContext ""
            val match = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"").find(html)
            match?.groupValues?.getOrNull(1).orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun normalizeToVtt(raw: String): String {
        val trimmed = raw.trim().removePrefix("\uFEFF")
        if (trimmed.startsWith("WEBVTT", ignoreCase = true)) return trimmed
        val converted = trimmed.replace(Regex("(\\d{2}:\\d{2}:\\d{2}),(\\d{3})"), "$1.$2")
        return "WEBVTT\n\n$converted"
    }

    private fun httpGet(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8")
                .build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }
}
