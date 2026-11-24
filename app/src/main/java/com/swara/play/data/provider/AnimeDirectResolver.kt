package com.swara.play.data.provider

import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class DirectStreamResult(
    val streamUrl: String,
    val referer: String = "",
    val subtitleVttUrl: String = "",
    val isWebEmbed: Boolean = false
)

object AnimeDirectResolver {
    private const val ALLANIME_API = "https://api.allanime.day/api"
    private const val ALLANIME_REFERER = "https://allmanga.to"

    suspend fun resolveDirectAnimeStream(item: MediaItem, episode: Int): DirectStreamResult? = withContext(Dispatchers.IO) {
        val queries = listOf(item.romajiTitle, item.title).map { it.trim() }.filter { it.isNotBlank() }.distinct()

        // Layer 1: AllAnime Direct HLS/MP4 (Original Japanese Audio 'sub', played in native ExoPlayer — ZERO WebView Cloudflare block!)
        for (q in queries) {
            val res = resolveViaAllAnime(q, episode)
            if (res != null) return@withContext res
        }

        // Layer 2: Indonesian Anime Sub Indo Mirrors (Otakudesu / Samehadaku — Japanese Audio + Baked Indonesian Subtitles!)
        for (q in queries) {
            val indoRes = resolveViaIndoAnimeMirror(q, episode)
            if (indoRes != null) return@withContext indoRes
        }

        null
    }

    private fun resolveViaIndoAnimeMirror(queryTitle: String, episode: Int): DirectStreamResult? {
        val cleanQuery = queryTitle.replace(Regex("[^a-zA-Z0-9 ]"), " ").trim()
        if (cleanQuery.isBlank()) return null
        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
        val mirrors = listOf(
            "https://otakudesu.cloud",
            "https://otakudesu.best",
            "https://samehadaku.mba"
        )
        for (base in mirrors) {
            try {
                val searchHtml = httpGet("$base/?s=$encoded&post_type=post", "$base/") ?: continue
                val animeHref = Regex("href=\"($base/(?:anime|series)/[^\"]+)\"").find(searchHtml)?.groupValues?.getOrNull(1)
                    ?: continue
                val detailHtml = httpGet(animeHref, "$base/") ?: continue
                val epPattern = Regex("href=\"($base/[^\"]*(?:episode|eps)[-_]?0?$episode(?:[^0-9\"][^\" ]*)?)\"", RegexOption.IGNORE_CASE)
                val epHref = epPattern.find(detailHtml)?.groupValues?.getOrNull(1) ?: continue
                val epHtml = httpGet(epHref, animeHref) ?: continue

                // Prefer direct MP4/M3U8 if present on page, else extract clean video iframe URL
                val directVid = Regex("(https?://[^\"'\\s]+\\.(?:m3u8|mp4)[^\"'\\s]*)").find(epHtml)?.groupValues?.getOrNull(1)
                if (!directVid.isNullOrBlank()) {
                    return DirectStreamResult(streamUrl = directVid, referer = epHref, isWebEmbed = false)
                }
                val iframeSrc = Regex("<iframe[^>]+src=\"(https?://[^\"]+)\"", RegexOption.IGNORE_CASE)
                    .findAll(epHtml)
                    .map { it.groupValues[1] }
                    .firstOrNull { !it.contains("facebook") && !it.contains("disqus") && !it.contains("ads") }
                if (!iframeSrc.isNullOrBlank()) {
                    if (iframeSrc.contains("streamtape", ignoreCase = true)) {
                        val stDirect = extractStreamTapeDirect(iframeSrc)
                        if (stDirect.isNotBlank()) return DirectStreamResult(streamUrl = stDirect, referer = iframeSrc, isWebEmbed = false)
                    }
                    return DirectStreamResult(streamUrl = iframeSrc, referer = epHref, isWebEmbed = true)
                }
            } catch (_: Exception) { }
        }
        return null
    }

    private fun resolveViaAllAnime(queryTitle: String, episode: Int): DirectStreamResult? {
        try {
            val searchGql = """query(${'$'}search: SearchInput, ${'$'}limit: Int, ${'$'}page: Int, ${'$'}translationType: VaildTranslationTypeEnumType, ${'$'}countryOrigin: VaildCountryOriginEnumType) { shows(search: ${'$'}search, limit: ${'$'}limit, page: ${'$'}page, translationType: ${'$'}translationType, countryOrigin: ${'$'}countryOrigin) { edges { _id name englishName availableEpisodes } } }"""
            val vars = JSONObject()
                .put("search", JSONObject().put("allowAdult", false).put("allowUnknown", false).put("query", queryTitle))
                .put("limit", 10)
                .put("page", 1)
                .put("translationType", "sub")
                .put("countryOrigin", "ALL")

            val encVars = URLEncoder.encode(vars.toString(), "UTF-8")
            val encQuery = URLEncoder.encode(searchGql, "UTF-8")
            val searchUrl = "$ALLANIME_API?variables=$encVars&query=$encQuery"
            val searchBody = httpGet(searchUrl, ALLANIME_REFERER) ?: return null
            val edges = JSONObject(searchBody)
                .optJSONObject("data")
                ?.optJSONObject("shows")
                ?.optJSONArray("edges") ?: return null
            if (edges.length() == 0) return null

            val showId = edges.optJSONObject(0)?.optString("_id", "").orEmpty()
            if (showId.isBlank()) return null

            val epGql = """query(${'$'}showId: String!, ${'$'}translationType: VaildTranslationTypeEnumType!, ${'$'}episodeString: String!) { episode(showId: ${'$'}showId, translationType: ${'$'}translationType, episodeString: ${'$'}episodeString) { episodeString sourceUrls } }"""
            val epVars = JSONObject()
                .put("showId", showId)
                .put("translationType", "sub")
                .put("episodeString", episode.toString())

            val encEpVars = URLEncoder.encode(epVars.toString(), "UTF-8")
            val encEpQuery = URLEncoder.encode(epGql, "UTF-8")
            val epUrl = "$ALLANIME_API?variables=$encEpVars&query=$encEpQuery"
            val epBody = httpGet(epUrl, ALLANIME_REFERER) ?: return null
            val sourceUrls = JSONObject(epBody)
                .optJSONObject("data")
                ?.optJSONObject("episode")
                ?.optJSONArray("sourceUrls") ?: return null

            val candidates = mutableListOf<String>()
            for (i in 0 until sourceUrls.length()) {
                val obj = sourceUrls.optJSONObject(i) ?: continue
                val rawUrl = obj.optString("sourceUrl", "")
                if (rawUrl.isBlank()) continue
                val decoded = if (rawUrl.startsWith("--")) decodeAllAnimeHex(rawUrl.removePrefix("--")) else rawUrl
                if (decoded.startsWith("http")) {
                    candidates.add(decoded)
                } else if (decoded.startsWith("/clock")) {
                    val clockJsonUrl = "https://allanime.day" + decoded.replace("/clock", "/clock.json")
                    val clockRes = parseClockJson(clockJsonUrl)
                    if (clockRes != null) return clockRes
                }
            }

            val direct = candidates.firstOrNull { it.contains(".m3u8") || it.contains(".mp4") }
            if (direct != null) {
                return DirectStreamResult(streamUrl = direct, referer = ALLANIME_REFERER, isWebEmbed = false)
            }
        } catch (_: Exception) { }
        return null
    }

    private fun parseClockJson(clockUrl: String): DirectStreamResult? {
        return try {
            val body = httpGet(clockUrl, ALLANIME_REFERER) ?: return null
            val links = JSONObject(body).optJSONArray("links") ?: return null
            var bestStream = ""
            var bestSub = ""
            for (i in 0 until links.length()) {
                val item = links.optJSONObject(i) ?: continue
                val link = item.optString("link", "")
                if (link.startsWith("http") && bestStream.isBlank()) bestStream = link
                val subs = item.optJSONArray("subtitles")
                if (subs != null) {
                    for (s in 0 until subs.length()) {
                        val subObj = subs.optJSONObject(s) ?: continue
                        val lang = subObj.optString("lang", "").lowercase()
                        val src = subObj.optString("src", "")
                        if (src.startsWith("http")) {
                            if (lang.contains("indo") || lang == "id") {
                                bestSub = src; break
                            } else if (bestSub.isBlank() && lang.contains("eng")) {
                                bestSub = src
                            }
                        }
                    }
                }
            }
            if (bestStream.isNotBlank()) DirectStreamResult(bestStream, ALLANIME_REFERER, bestSub, isWebEmbed = false) else null
        } catch (_: Exception) { null }
    }

    private fun decodeAllAnimeHex(hex: String): String {
        val out = StringBuilder()
        var i = 0
        while (i + 1 < hex.length) {
            val byteVal = hex.substring(i, i + 2).toIntOrNull(16)
            if (byteVal != null) out.append((byteVal xor 0x38).toChar())
            i += 2
        }
        return out.toString()
    }

    private fun extractStreamTapeDirect(embedUrl: String): String {
        return try {
            val html = httpGet(embedUrl, "https://streamtape.com/") ?: return ""
            val m = Regex("id='robotlink'[^>]*>([^<]+)<").find(html)
                ?: Regex("id=\"robotlink\"[^>]*>([^<]+)<").find(html)
                ?: return ""
            val raw = m.groupValues[1].trim()
            if (raw.startsWith("//")) "https:$raw&stream=1"
            else if (raw.startsWith("/")) "https://streamtape.com$raw&stream=1"
            else ""
        } catch (_: Exception) { "" }
    }

    private fun httpGet(url: String, referer: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .header("Referer", referer)
                .build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) { null }
    }
}
