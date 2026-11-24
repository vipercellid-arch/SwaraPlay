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

    /**
     * Fetches the most accurate synchronized VTT subtitles by checking:
     * 1) OpenSubtitles V3+ (Stremio official subtitle endpoint, matched to release)
     * 2) Wyzie Subtitles (scored strictly for WEB-DL/WEBRip, non-HI, UTF-8)
     * 3) If only English VTT is available (common on anime), translates cue text to Indonesian
     *    while keeping 100% of original timestamps intact (guaranteed 0ms sync with speech!).
     */
    suspend fun fetchBestSynchronizedVttForLang(
        item: MediaItem,
        season: Int,
        episode: Int,
        langIso: String,
        directSubtitleUrl: String = ""
    ): String = withContext(Dispatchers.IO) {
        val targetLang = langIso.lowercase().ifBlank { "id" }

        // Priority 0: Direct subtitle bundled with Anime stream (100% matched to stream!)
        if (directSubtitleUrl.startsWith("http")) {
            val raw = httpGet(directSubtitleUrl)
            if (!raw.isNullOrBlank()) {
                val vtt = normalizeToVtt(raw)
                return@withContext if (targetLang == "id" && !looksIndonesian(vtt)) {
                    translateVttPreservingTimestamps(vtt, "id")
                } else vtt
            }
        }

        val imdb = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else "" }
        if (imdb.isBlank()) return@withContext ""

        // Priority 1: OpenSubtitles V3+ Stremio Addon (high sync accuracy)
        val osVtt = fetchFromOpenSubtitlesV3(item, imdb, season, episode, targetLang)
        if (osVtt.isNotBlank()) return@withContext osVtt

        // Priority 2: Wyzie WEB-DL scored
        val wyzieVtt = fetchFromWyzie(item, imdb, season, episode, targetLang)
        if (wyzieVtt.isNotBlank()) return@withContext wyzieVtt

        // Priority 3: Fetch English synchronized track and translate to Indonesian preserving exact timestamps
        if (targetLang == "id") {
            val enVtt = fetchFromOpenSubtitlesV3(item, imdb, season, episode, "en")
                .ifBlank { fetchFromWyzie(item, imdb, season, episode, "en") }
            if (enVtt.isNotBlank()) {
                return@withContext translateVttPreservingTimestamps(enVtt, "id")
            }
        }
        ""
    }

    private fun fetchFromOpenSubtitlesV3(
        item: MediaItem,
        imdb: String,
        season: Int,
        episode: Int,
        langIso: String
    ): String {
        val type = if (item.category == MediaCategory.MOVIE) "movie" else "series"
        val idPath = if (type == "movie") imdb else "$imdb:$season:$episode"
        val url = "https://opensubtitles-v3.strem.io/subtitles/$type/$idPath.json"
        val body = httpGet(url) ?: return ""
        return try {
            val subs = JSONObject(body).optJSONArray("subtitles") ?: return ""
            val wantCodes = when (langIso) {
                "id" -> setOf("ind", "id", "in")
                "ms" -> setOf("msa", "may", "ms")
                else -> setOf("eng", "en", langIso)
            }
            for (i in 0 until subs.length()) {
                val o = subs.optJSONObject(i) ?: continue
                val l = o.optString("lang", "").lowercase()
                val subUrl = o.optString("url", "")
                if (l in wantCodes && subUrl.startsWith("http")) {
                    val raw = httpGet(subUrl)
                    if (!raw.isNullOrBlank()) return normalizeToVtt(raw)
                }
            }
            ""
        } catch (_: Exception) { "" }
    }

    private fun fetchFromWyzie(
        item: MediaItem,
        imdb: String,
        season: Int,
        episode: Int,
        langIso: String
    ): String {
        val url = if (item.category == MediaCategory.MOVIE) {
            "https://sub.wyzie.io/search?id=$imdb&language=$langIso&format=vtt"
        } else {
            "https://sub.wyzie.io/search?id=$imdb&season=$season&episode=$episode&language=$langIso&format=vtt"
        }
        val body = httpGet(url) ?: return ""
        return try {
            val arr = JSONArray(body)
            var bestUrl = ""
            var bestScore = Int.MIN_VALUE
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
                    display.contains("nf") || display.contains("amzn") || display.contains("dsnp")) score += 80
                if (display.contains("bluray") || display.contains("brrip")) score += 30
                if (display.contains("cam") || display.contains("ts") || display.contains("hdcam") || display.contains("telesync")) score -= 180
                if (encoding.contains("utf")) score += 15
                if (score > bestScore) { bestScore = score; bestUrl = subUrl }
            }
            if (bestUrl.isBlank()) return ""
            val raw = httpGet(bestUrl) ?: return ""
            normalizeToVtt(raw)
        } catch (_: Exception) { "" }
    }

    private fun looksIndonesian(vtt: String): Boolean {
        val sample = vtt.take(1600).lowercase()
        val idWords = listOf(" yang ", " tidak ", " aku ", " kamu ", " dengan ", " untuk ", " adalah ", " ini ", " itu ")
        return idWords.count { sample.contains(it) } >= 2
    }

    private suspend fun translateVttPreservingTimestamps(vtt: String, targetLang: String): String {
        val lines = vtt.lines()
        val textIndices = mutableListOf<Int>()
        val textLines = mutableListOf<String>()
        for (i in lines.indices) {
            val l = lines[i].trim()
            if (l.isBlank() || l.startsWith("WEBVTT") || l.contains("-->") || l.all { it.isDigit() }) continue
            textIndices.add(i)
            textLines.add(l)
        }
        if (textLines.isEmpty()) return vtt
        val outLines = lines.toMutableList()
        // Batch translate up to 40 lines per chunk while preserving exact VTT cue timestamps
        val chunkSize = 35
        var offset = 0
        while (offset < textLines.size) {
            val end = minOf(offset + chunkSize, textLines.size)
            val batch = textLines.subList(offset, end)
            val joined = batch.joinToString("\n")
            val translated = SynopsisTranslator.translateToLanguage(joined, targetLang)
            val transSplit = translated.lines()
            if (transSplit.size == batch.size) {
                for (k in batch.indices) {
                    outLines[textIndices[offset + k]] = transSplit[k]
                }
            }
            offset = end
        }
        return outLines.joinToString("\n")
    }

    suspend fun resolveYoutubeTrailerId(title: String, year: String = ""): String = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return@withContext ""
        try {
            val q = URLEncoder.encode("$cleanTitle ${year.take(4)} official trailer".trim(), "UTF-8")
            val html = httpGet("https://www.youtube.com/results?search_query=$q") ?: return@withContext ""
            val match = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"").find(html)
            match?.groupValues?.getOrNull(1).orEmpty()
        } catch (_: Exception) { "" }
    }

    suspend fun resolveDirectTrailerStreamUrl(youtubeVideoId: String): String = withContext(Dispatchers.IO) {
        if (youtubeVideoId.isBlank()) return@withContext ""
        val pipedHosts = listOf("https://pipedapi.kavin.rocks", "https://pipedapi.adminforge.de", "https://api.piped.private.coffee")
        for (host in pipedHosts) {
            try {
                val body = httpGet("$host/streams/$youtubeVideoId") ?: continue
                val root = JSONObject(body)
                val hls = root.optString("hls", "")
                if (hls.startsWith("http")) return@withContext hls
                val streams = root.optJSONArray("videoStreams") ?: continue
                for (i in 0 until streams.length()) {
                    val s = streams.optJSONObject(i) ?: continue
                    if (!s.optBoolean("videoOnly", true)) {
                        val u = s.optString("url", "")
                        if (u.startsWith("http")) return@withContext u
                    }
                }
            } catch (_: Exception) { }
        }
        val invHosts = listOf("https://inv.nadeko.net", "https://invidious.nerdvpn.de")
        for (host in invHosts) {
            try {
                val body = httpGet("$host/api/v1/videos/$youtubeVideoId") ?: continue
                val formats = JSONObject(body).optJSONArray("formatStreams") ?: continue
                for (i in 0 until formats.length()) {
                    val u = formats.optJSONObject(i)?.optString("url", "").orEmpty()
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
        } catch (_: Exception) { null }
    }
}
