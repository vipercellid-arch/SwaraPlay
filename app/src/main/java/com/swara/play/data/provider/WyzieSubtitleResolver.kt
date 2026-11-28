package com.swara.play.data.provider

import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

object WyzieSubtitleResolver {

    /**
     * 4-Layer Indonesian-ONLY Synchronized Subtitle Engine:
     * 0) Stream-bundled VTT (auto-translated EN->ID preserving 0ms timestamps if not Indonesian)
     * 1) rest.opensubtitles.org (sublanguageid-ind, gzipped .gz, WEB-DL scored, rejecting CAM/TS)
     * 2) OpenSubtitles V3+ (opensubtitles-v3.strem.io)
     * 3) Wyzie Subtitles (sub.wyzie.io, WEB-DL scored, non-HI)
     * 4) Auto-Translate Online fallback: fetches synchronized English track & translates to Indonesian keeping 100% original timestamps
     */
    suspend fun fetchBestSynchronizedVttForLang(
        item: MediaItem,
        season: Int,
        episode: Int,
        langIso: String,
        directSubtitleUrl: String = ""
    ): String = withContext(Dispatchers.IO) {
        val forceTranslateOnline = langIso.equals("id_translate_online", ignoreCase = true)

        if (!forceTranslateOnline && directSubtitleUrl.startsWith("http")) {
            val raw = httpGet(directSubtitleUrl)
            if (!raw.isNullOrBlank()) {
                val vtt = normalizeToVtt(raw)
                return@withContext if (!looksIndonesian(vtt)) translateVttPreservingTimestamps(vtt, "id") else vtt
            }
        }

        val imdb = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else "" }
        if (imdb.isBlank()) {
            // Fallback Layer 5: Title + Year search on OpenSubtitles REST -> auto-translate to Indonesian if English!
            val byTitleId = fetchFromOpenSubtitlesByTitle(item, season, episode, "ind")
            if (byTitleId.isNotBlank()) return@withContext byTitleId
            val byTitleEn = fetchFromOpenSubtitlesByTitle(item, season, episode, "eng")
            if (byTitleEn.isNotBlank()) return@withContext translateVttPreservingTimestamps(byTitleEn, "id")
            return@withContext ""
        }

        if (!forceTranslateOnline) {
            val osRestId = fetchFromOpenSubtitlesRest(item, imdb, season, episode, "ind")
            if (osRestId.isNotBlank()) return@withContext osRestId

            val osV3Id = fetchFromOpenSubtitlesV3(item, imdb, season, episode, "id")
            if (osV3Id.isNotBlank()) return@withContext osV3Id

            val wyzieId = fetchFromWyzie(item, imdb, season, episode, "id")
            if (wyzieId.isNotBlank()) return@withContext wyzieId
        }

        // Fallback / On-Demand "Cari & Terjemahkan Online":
        // Try Indonesian once more if forced, else fetch synchronized English track & translate to Indonesian!
        val enVtt = fetchFromOpenSubtitlesRest(item, imdb, season, episode, "eng")
            .ifBlank { fetchFromOpenSubtitlesV3(item, imdb, season, episode, "en") }
            .ifBlank { fetchFromWyzie(item, imdb, season, episode, "en") }
        if (enVtt.isNotBlank()) {
            return@withContext translateVttPreservingTimestamps(enVtt, "id")
        }
        val byTitleId = fetchFromOpenSubtitlesByTitle(item, season, episode, "ind")
        if (byTitleId.isNotBlank()) return@withContext byTitleId
        val byTitleEn = fetchFromOpenSubtitlesByTitle(item, season, episode, "eng")
        if (byTitleEn.isNotBlank()) return@withContext translateVttPreservingTimestamps(byTitleEn, "id")
        ""
    }


    private fun fetchFromOpenSubtitlesByTitle(item: MediaItem, season: Int, episode: Int, lang3: String): String {
        val q = URLEncoder.encode(item.title.trim(), "UTF-8")
        if (q.isBlank()) return ""
        val path = if (item.category == MediaCategory.MOVIE) {
            "query-$q/sublanguageid-$lang3"
        } else {
            "episode-$episode/query-$q/season-$season/sublanguageid-$lang3"
        }
        return try {
            val req = Request.Builder()
                .url("https://rest.opensubtitles.org/search/$path")
                .header("User-Agent", "TemporaryUserAgent")
                .build()
            val body = HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return ""
                resp.body?.string() ?: return ""
            }
            val arr = JSONArray(body)
            var bestLink = ""
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val dl = o.optString("SubDownloadLink", "")
                if (dl.startsWith("http")) { bestLink = dl; break }
            }
            if (bestLink.isBlank()) return ""
            val dlReq = Request.Builder().url(bestLink).header("User-Agent", "TemporaryUserAgent").build()
            HttpClientProvider.client.newCall(dlReq).execute().use { resp ->
                if (!resp.isSuccessful) return ""
                val bytes = resp.body?.bytes() ?: return ""
                val text = if (bestLink.endsWith(".gz", true) || (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())) {
                    GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    String(bytes, Charsets.UTF_8)
                }
                if (text.isNotBlank()) normalizeToVtt(text) else ""
            }
        } catch (_: Exception) { "" }
    }

    private fun fetchFromOpenSubtitlesRest(
        item: MediaItem,
        imdb: String,
        season: Int,
        episode: Int,
        lang3: String
    ): String {
        val numericImdb = imdb.removePrefix("tt").filter { it.isDigit() }
        if (numericImdb.isBlank()) return ""
        val path = if (item.category == MediaCategory.MOVIE) {
            "imdbid-$numericImdb/sublanguageid-$lang3"
        } else {
            "episode-$episode/imdbid-$numericImdb/season-$season/sublanguageid-$lang3"
        }
        return try {
            val req = Request.Builder()
                .url("https://rest.opensubtitles.org/search/$path")
                .header("User-Agent", "TemporaryUserAgent")
                .build()
            val body = HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return ""
                resp.body?.string() ?: return ""
            }
            val arr = JSONArray(body)
            var bestLink = ""
            var bestScore = Int.MIN_VALUE
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val dl = o.optString("SubDownloadLink", "")
                if (!dl.startsWith("http")) continue
                val rel = o.optString("MovieReleaseName", "").lowercase()
                val hi = o.optString("SubHearingImpaired", "0") == "1"
                val downloads = o.optString("SubDownloadsCnt", "0").toIntOrNull() ?: 0
                var score = (downloads / 50).coerceAtMost(80)
                if (!hi) score += 40
                if (rel.contains("web-dl") || rel.contains("webdl") || rel.contains("webrip") || rel.contains("nf") || rel.contains("amzn")) score += 75
                if (rel.contains("bluray") || rel.contains("brrip")) score += 25
                if (rel.contains("cam") || rel.contains("hdcam") || rel.contains("telesync") || rel.contains(".ts.")) score -= 200
                if (score > bestScore) { bestScore = score; bestLink = dl }
            }
            if (bestLink.isBlank()) return ""
            val dlReq = Request.Builder().url(bestLink).header("User-Agent", "TemporaryUserAgent").build()
            HttpClientProvider.client.newCall(dlReq).execute().use { resp ->
                if (!resp.isSuccessful) return ""
                val bytes = resp.body?.bytes() ?: return ""
                val text = if (bestLink.endsWith(".gz", true) || (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())) {
                    GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    String(bytes, Charsets.UTF_8)
                }
                if (text.isNotBlank()) normalizeToVtt(text) else ""
            }
        } catch (_: Exception) { "" }
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
            val wantCodes = if (langIso == "id") setOf("ind", "id", "in") else setOf("eng", "en")
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
        val query = if (item.category == MediaCategory.MOVIE) {
            "https://sub.wyzie.io/search?id=$imdb&language=$langIso&format=vtt"
        } else {
            "https://sub.wyzie.io/search?id=$imdb&season=$season&episode=$episode&language=$langIso&format=vtt"
        }
        val body = httpGet(query) ?: return ""
        return try {
            val arr = JSONArray(body)
            var bestUrl = ""
            var bestScore = Int.MIN_VALUE
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val subUrl = o.optString("url", "")
                if (!subUrl.startsWith("http")) continue
                val display = o.optString("display", "").lowercase()
                val release = o.optString("release", "").lowercase()
                val isHi = o.optBoolean("isHearingImpaired", false)
                var score = 0
                if (!isHi) score += 40
                if (release.contains("web-dl") || release.contains("webdl") || release.contains("webrip") || display.contains("web")) score += 70
                if (release.contains("bluray") || release.contains("brrip")) score += 25
                if (release.contains("cam") || release.contains("ts") || release.contains("hdcam")) score -= 200
                if (score > bestScore) { bestScore = score; bestUrl = subUrl }
            }
            if (bestUrl.isBlank()) return ""
            val raw = httpGet(bestUrl) ?: return ""
            normalizeToVtt(raw)
        } catch (_: Exception) { "" }
    }

    private fun looksIndonesian(vtt: String): Boolean {
        val sample = vtt.take(2000).lowercase()
        val indoHints = listOf(" yang ", " tidak ", " kamu ", " aku ", " ini ", " itu ", " dengan ", " untuk ", " adalah ", " kita ")
        return indoHints.count { sample.contains(it) } >= 3
    }

    private suspend fun translateVttPreservingTimestamps(vtt: String, targetLang: String): String {
        val lines = vtt.lines()
        val textIndices = mutableListOf<Int>()
        val textLines = mutableListOf<String>()
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.isEmpty() || trimmed == "WEBVTT" || trimmed.contains("-->") || trimmed.all { it.isDigit() }) continue
            textIndices.add(i)
            textLines.add(trimmed)
        }
        if (textLines.isEmpty()) return vtt
        val outLines = lines.toMutableList()
        val chunkSize = 35
        var offset = 0
        while (offset < textLines.size) {
            val end = minOf(offset + chunkSize, textLines.size)
            val batch = textLines.subList(offset, end)
            val translated = SynopsisTranslator.translateToLanguage(batch.joinToString("\n"), targetLang)
            val transSplit = translated.lines()
            if (transSplit.size == batch.size) {
                for (k in batch.indices) outLines[textIndices[offset + k]] = transSplit[k]
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
        val cleaned = raw.trimStart('\uFEFF').replace("\r\n", "\n").trim()
        if (cleaned.startsWith("WEBVTT", ignoreCase = true)) return cleaned
        val converted = cleaned.replace(Regex("""(\d{2}:\d{2}:\d{2}),(\d{3})"""), "$1.$2")
        return "WEBVTT\n\n$converted"
    }

    private fun httpGet(url: String): String? {
        return try {
            val req = Request.Builder().url(url).header("User-Agent", HttpClientProvider.USER_AGENT).build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) { null }
    }
}
