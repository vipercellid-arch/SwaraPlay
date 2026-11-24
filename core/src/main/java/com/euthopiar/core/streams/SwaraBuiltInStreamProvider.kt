package com.euthopiar.core.streams

import com.euthopiar.core.network.DohDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * SwaraPlay Built-in Zero-Setup Stream Engine:
 * - Anime: AllAnime Direct GraphQL HLS/MP4 (Original Japanese Audio 'sub') + Indonesian Sub Indo Mirrors (Otakudesu / Samehadaku + StreamTape Direct MP4)
 * - Movies/Series/Drama: Public Stremio HTTP stream resolver (WebStreamr) as zero-setup companion to Euthopiar JS Plugins & Addons
 */
object SwaraBuiltInStreamProvider {
    const val ADDON_ID = "builtin:swaraplay_direct"
    const val ADDON_NAME = "SwaraPlay Direct"

    private const val ALLANIME_API = "https://api.allanime.day/api"
    private const val ALLANIME_REFERER = "https://allmanga.to"
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    suspend fun resolveStreams(
        normType: String,
        isAnime: Boolean,
        title: String?,
        romajiTitle: String?,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        absoluteEpisode: Int?
    ): List<StreamItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<StreamItem>()

        if (isAnime || normType.equals("anime", ignoreCase = true)) {
            val epNum = absoluteEpisode ?: episode ?: 1
            val queries = listOfNotNull(romajiTitle, title)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            for (q in queries) {
                val aa = resolveViaAllAnime(q, epNum)
                if (aa.isNotEmpty()) {
                    results.addAll(aa)
                    break
                }
            }

            for (q in queries) {
                val indo = resolveViaIndoAnimeMirrors(q, epNum)
                if (indo.isNotEmpty()) {
                    results.addAll(indo)
                    break
                }
            }
        }

        if (!imdbId.isNullOrBlank() && imdbId.startsWith("tt")) {
            val stremioType = if (normType == "movie" && (season == null || season <= 0)) "movie" else "series"
            val stremioId = if (stremioType == "series" && season != null && episode != null) {
                "$imdbId:$season:$episode"
            } else imdbId
            results.addAll(resolveViaPublicStremioHttp(stremioType, stremioId))
        }

        results.distinctBy { it.url }
    }

    private fun resolveViaAllAnime(queryTitle: String, episode: Int): List<StreamItem> {
        val out = mutableListOf<StreamItem>()
        try {
            val searchGql = """query(${'$'}search: SearchInput, ${'$'}limit: Int, ${'$'}page: Int, ${'$'}translationType: VaildTranslationTypeEnumType, ${'$'}countryOrigin: VaildCountryOriginEnumType) { shows(search: ${'$'}search, limit: ${'$'}limit, page: ${'$'}page, translationType: ${'$'}translationType, countryOrigin: ${'$'}countryOrigin) { edges { _id name englishName } } }"""
            val vars = JSONObject()
                .put("search", JSONObject().put("allowAdult", false).put("allowUnknown", false).put("query", queryTitle))
                .put("limit", 8)
                .put("page", 1)
                .put("translationType", "sub")
                .put("countryOrigin", "ALL")

            val searchUrl = "$ALLANIME_API?variables=${URLEncoder.encode(vars.toString(), "UTF-8")}&query=${URLEncoder.encode(searchGql, "UTF-8")}"
            val searchBody = httpGet(searchUrl, ALLANIME_REFERER) ?: return out
            val edges = JSONObject(searchBody).optJSONObject("data")?.optJSONObject("shows")?.optJSONArray("edges") ?: return out
            if (edges.length() == 0) return out

            val showId = edges.optJSONObject(0)?.optString("_id", "").orEmpty()
            if (showId.isBlank()) return out

            val epGql = """query(${'$'}showId: String!, ${'$'}translationType: VaildTranslationTypeEnumType!, ${'$'}episodeString: String!) { episode(showId: ${'$'}showId, translationType: ${'$'}translationType, episodeString: ${'$'}episodeString) { sourceUrls } }"""
            val epVars = JSONObject()
                .put("showId", showId)
                .put("translationType", "sub")
                .put("episodeString", episode.toString())

            val epUrl = "$ALLANIME_API?variables=${URLEncoder.encode(epVars.toString(), "UTF-8")}&query=${URLEncoder.encode(epGql, "UTF-8")}"
            val epBody = httpGet(epUrl, ALLANIME_REFERER) ?: return out
            val sourceUrls = JSONObject(epBody).optJSONObject("data")?.optJSONObject("episode")?.optJSONArray("sourceUrls") ?: return out

            for (i in 0 until sourceUrls.length()) {
                val obj = sourceUrls.optJSONObject(i) ?: continue
                val rawUrl = obj.optString("sourceUrl", "")
                val srcName = obj.optString("sourceName", "AllAnime")
                val decoded = if (rawUrl.startsWith("--")) decodeAllAnimeHex(rawUrl.removePrefix("--")) else rawUrl
                if (decoded.contains("/clock")) {
                    val clockUrl = if (decoded.startsWith("http")) decoded.replace("/clock?", "/clock.json?")
                    else "https://allanime.day" + decoded.replace("/clock?", "/clock.json?")
                    val resolved = fetchClockJson(clockUrl, srcName)
                    if (resolved != null) out.add(resolved)
                } else if (decoded.startsWith("http") && (decoded.contains(".m3u8") || decoded.contains(".mp4"))) {
                    out.add(
                        StreamItem(
                            name = "SwaraPlay Anime • $srcName (Sub)",
                            title = "Audio Asli Jepang (HLS/MP4)",
                            url = decoded,
                            quality = "1080p",
                            sourceName = srcName,
                            addonName = ADDON_NAME,
                            addonId = ADDON_ID,
                            headers = mapOf("Referer" to ALLANIME_REFERER, "User-Agent" to UA),
                            audioLanguages = listOf("ja"),
                            matchScore = 1.0
                        )
                    )
                }
                if (out.size >= 3) break
            }
        } catch (_: Exception) { }
        return out
    }

    private fun fetchClockJson(clockUrl: String, srcName: String): StreamItem? {
        val body = httpGet(clockUrl, ALLANIME_REFERER) ?: return null
        return try {
            val links = JSONObject(body).optJSONArray("links") ?: return null
            var bestStream = ""
            val subs = mutableListOf<StreamSubtitle>()
            for (i in 0 until links.length()) {
                val item = links.optJSONObject(i) ?: continue
                val link = item.optString("link", "")
                if (link.startsWith("http") && bestStream.isBlank()) bestStream = link
                val subArr = item.optJSONArray("subtitles")
                if (subArr != null) {
                    for (s in 0 until subArr.length()) {
                        val so = subArr.optJSONObject(s) ?: continue
                        val src = so.optString("src", "")
                        val lang = so.optString("lang", "Indonesian")
                        if (src.startsWith("http")) {
                            subs.add(StreamSubtitle(url = src, language = lang, name = lang))
                        }
                    }
                }
            }
            if (bestStream.isBlank()) return null
            StreamItem(
                name = "SwaraPlay Anime • $srcName (Sub)",
                title = "Audio Asli Jepang • Direct ExoPlayer",
                url = bestStream,
                quality = "1080p",
                sourceName = srcName,
                addonName = ADDON_NAME,
                addonId = ADDON_ID,
                headers = mapOf("Referer" to ALLANIME_REFERER, "User-Agent" to UA),
                externalSubtitles = subs,
                subtitles = subs,
                audioLanguages = listOf("ja"),
                matchScore = 1.0
            )
        } catch (_: Exception) { null }
    }

    private fun resolveViaIndoAnimeMirrors(queryTitle: String, episode: Int): List<StreamItem> {
        val out = mutableListOf<StreamItem>()
        val cleanQuery = queryTitle.replace(Regex("[^a-zA-Z0-9 ]"), " ").trim()
        if (cleanQuery.isBlank()) return out
        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
        val mirrors = listOf(
            "Otakudesu" to "https://otakudesu.cloud",
            "Otakudesu Alt" to "https://otakudesu.best",
            "Samehadaku" to "https://samehadaku.mba"
        )
        for ((label, base) in mirrors) {
            try {
                val searchHtml = httpGet("$base/?s=$encoded&post_type=post", "$base/") ?: continue
                val animeHref = Regex("href=\"($base/(?:anime|series)/[^\"]+)\"").find(searchHtml)?.groupValues?.getOrNull(1) ?: continue
                val detailHtml = httpGet(animeHref, "$base/") ?: continue
                val epPattern = Regex("href=\"($base/[^\"]*(?:episode|eps)[-_]?0?$episode(?:[^0-9\"][^\" ]*)?)\"", RegexOption.IGNORE_CASE)
                val epHref = epPattern.find(detailHtml)?.groupValues?.getOrNull(1) ?: continue
                val epHtml = httpGet(epHref, animeHref) ?: continue

                val directVid = Regex("(https?://[^\"'\\s]+\\.(?:m3u8|mp4)[^\"'\\s]*)").find(epHtml)?.groupValues?.getOrNull(1)
                if (!directVid.isNullOrBlank()) {
                    out.add(
                        StreamItem(
                            name = "$label • Sub Indo",
                            title = "Audio Asli Jepang • Subtitle Indonesia",
                            url = directVid,
                            quality = "720p",
                            sourceName = label,
                            addonName = ADDON_NAME,
                            addonId = ADDON_ID,
                            headers = mapOf("Referer" to epHref, "User-Agent" to UA),
                            audioLanguages = listOf("ja"),
                            matchScore = 1.0
                        )
                    )
                    break
                }

                val stUrl = Regex("https?://(?:www\\.)?streamtape\\.[a-z]+/e/[^\"'\\s<]+", RegexOption.IGNORE_CASE)
                    .find(epHtml)?.value
                if (!stUrl.isNullOrBlank()) {
                    val stDirect = extractStreamTapeDirect(stUrl)
                    if (stDirect.isNotBlank()) {
                        out.add(
                            StreamItem(
                                name = "$label • Sub Indo (StreamTape)",
                                title = "Audio Asli Jepang • Subtitle Indonesia",
                                url = stDirect,
                                quality = "720p",
                                sourceName = label,
                                addonName = ADDON_NAME,
                                addonId = ADDON_ID,
                                headers = mapOf("Referer" to stUrl, "User-Agent" to UA),
                                audioLanguages = listOf("ja"),
                                matchScore = 1.0
                            )
                        )
                        break
                    }
                }
            } catch (_: Exception) { }
        }
        return out
    }

    private fun resolveViaPublicStremioHttp(type: String, id: String): List<StreamItem> {
        val out = mutableListOf<StreamItem>()
        try {
            val body = httpGet("https://webstreamr.hayd.uk/stream/$type/$id.json", "https://webstreamr.hayd.uk/") ?: return out
            val arr = JSONObject(body).optJSONArray("streams") ?: return out
            for (i in 0 until minOf(arr.length(), 6)) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url", "").trim()
                if (!url.startsWith("http")) continue
                val name = o.optString("name", "SwaraPlay HD").ifBlank { "SwaraPlay HD" }
                val title = o.optString("title", o.optString("description", "")).trim()
                val hdrs = mutableMapOf<String, String>()
                val reqHdrs = o.optJSONObject("behaviorHints")?.optJSONObject("proxyHeaders")?.optJSONObject("request")
                if (reqHdrs != null) {
                    val keys = reqHdrs.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        hdrs[k] = reqHdrs.optString(k, "")
                    }
                }
                out.add(
                    StreamItem(
                        name = name,
                        title = title.ifBlank { "Direct HTTP Stream" },
                        description = title.ifBlank { null },
                        url = url,
                        quality = if (title.contains("1080", true)) "1080p" else if (title.contains("4k", true) || title.contains("2160", true)) "4K" else "720p",
                        sourceName = "SwaraPlay Direct",
                        addonName = ADDON_NAME,
                        addonId = ADDON_ID,
                        headers = hdrs,
                        matchScore = 1.0
                    )
                )
            }
        } catch (_: Exception) { }
        return out
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

    private fun decodeAllAnimeHex(hex: String): String {
        val out = StringBuilder()
        var i = 0
        while (i + 1 < hex.length) {
            val b = hex.substring(i, i + 2).toIntOrNull(16)
            if (b != null) out.append((b xor 0x38).toChar())
            i += 2
        }
        return out.toString()
    }

    private fun httpGet(url: String, referer: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", referer)
                .build()
            DohDns.metadataClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) { null }
    }
}
