package com.swara.play.data.provider

import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object WyzieSubtitleResolver {

    suspend fun fetchRankedSynchronizedVtts(
        item: MediaItem,
        season: Int,
        episode: Int,
        langIso: String
    ): List<String> = withContext(Dispatchers.IO) {
        val imdb = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else "" }
        if (imdb.isBlank()) return@withContext emptyList()
        val lang = langIso.lowercase().ifBlank { "id" }
        val url = if (item.category == MediaCategory.MOVIE) {
            "https://sub.wyzie.io/search?id=$imdb&language=$lang&format=vtt"
        } else {
            "https://sub.wyzie.io/search?id=$imdb&season=$season&episode=$episode&language=$lang&format=vtt"
        }
        val body = httpGet(url) ?: return@withContext emptyList()
        try {
            val arr = JSONArray(body)
            if (arr.length() == 0) return@withContext emptyList()

            val scored = mutableListOf<Pair<Int, String>>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val subUrl = o.optString("url", "")
                if (subUrl.isBlank()) continue
                val display = (o.optString("display", "") + " " + o.optString("fileName", "")).lowercase()
                val isHi = o.optBoolean("isHearingImpaired", false)
                val encoding = o.optString("encoding", "").lowercase()
                var score = 100 - i * 2
                if (!isHi) score += 45
                if (display.contains("web-dl") || display.contains("webdl") || display.contains("webrip") ||
                    display.contains("nf") || display.contains("amzn") || display.contains("dsnp") || display.contains("hmax")) {
                    score += 75
                }
                if (display.contains("bluray") || display.contains("brrip")) score += 30
                if (display.contains("cam") || display.contains("ts") || display.contains("hdcam") || display.contains("telesync")) {
                    score -= 150
                }
                if (encoding.contains("utf")) score += 15
                scored.add(score to subUrl)
            }

            val topUrls = scored.sortedByDescending { it.first }.map { it.second }.distinct().take(3)
            val vtts = mutableListOf<String>()
            for (u in topUrls) {
                val rawSub = httpGet(u)
                if (!rawSub.isNullOrBlank()) vtts.add(normalizeToVtt(rawSub))
            }
            vtts
        } catch (_: Exception) {
            emptyList()
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

    /**
     * Resolves a direct playable MP4/HLS stream URL for a YouTube trailer via public Piped/Invidious APIs
     * so ExoPlayer plays trailers natively without YouTube WebView Error 152-4.
     */
    suspend fun resolveDirectTrailerStreamUrl(youtubeVideoId: String): String = withContext(Dispatchers.IO) {
        if (youtubeVideoId.isBlank()) return@withContext ""
        val pipedHosts = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://api.piped.private.coffee"
        )
        for (host in pipedHosts) {
            try {
                val body = httpGet("$host/streams/$youtubeVideoId") ?: continue
                val root = JSONObject(body)
                val hls = root.optString("hls", "")
                if (hls.startsWith("http")) return@withContext hls
                val streams = root.optJSONArray("videoStreams") ?: continue
                for (i in 0 until streams.length()) {
                    val s = streams.optJSONObject(i) ?: continue
                    val videoOnly = s.optBoolean("videoOnly", true)
                    val streamUrl = s.optString("url", "")
                    if (!videoOnly && streamUrl.startsWith("http")) return@withContext streamUrl
                }
            } catch (_: Exception) { }
        }
        val invidiousHosts = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de"
        )
        for (host in invidiousHosts) {
            try {
                val body = httpGet("$host/api/v1/videos/$youtubeVideoId") ?: continue
                val root = JSONObject(body)
                val formats = root.optJSONArray("formatStreams") ?: continue
                for (i in 0 until formats.length()) {
                    val f = formats.optJSONObject(i) ?: continue
                    val u = f.optString("url", "")
                    if (u.startsWith("http")) return@withContext u
                }
            } catch (_: Exception) { }
        }
        ""
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
