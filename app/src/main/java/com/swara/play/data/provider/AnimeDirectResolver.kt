package com.swara.play.data.provider

import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONArray
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
    private val dubRegex = Regex(
        """\b(dub|dubbed|eng[\s._-]*dub|english[\s._-]*dub|dual[\s._-]*audio|multi[\s._-]*audio|hindi|hin|tamil|telugu|latino|spanish|espanol|rus|russian|vf|vff|dublado)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parallel Race Engine v9.4.0:
     * Races Kumanime API (AnimeIndo Sub Indo), SankaVollereii/Otakudesu/Kuramanime APIs,
     * Indonesian Anime mirrors (AnimeIndo/Otakudesu/Samehadaku), and AllAnime ('sub' original JP audio)
     * with a fast 5.5s timeout so Anime starts cleanly without Error 522 or dubbing!
     */
    suspend fun resolveDirectAnimeStream(item: MediaItem, episode: Int): DirectStreamResult? = withContext(Dispatchers.IO) {
        val queries = listOf(item.romajiTitle, item.title)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (queries.isEmpty()) return@withContext null
        val primaryQ = queries.first()
        val secondaryQ = queries.getOrNull(1)

        val candidates = withTimeoutOrNull(5800L) {
            coroutineScope {
                val tasks = mutableListOf(
                    async(Dispatchers.IO) { resolveViaKumanimeApi(primaryQ, episode) },
                    async(Dispatchers.IO) { resolveViaSankaAnimeApi(primaryQ, episode) },
                    async(Dispatchers.IO) { resolveViaOtakudesuApis(primaryQ, episode) },
                    async(Dispatchers.IO) { resolveViaIndoAnimeMirror(primaryQ, episode) },
                    async(Dispatchers.IO) { resolveViaAllAnime(primaryQ, episode) }
                )
                if (!secondaryQ.isNullOrBlank()) {
                    tasks.add(async(Dispatchers.IO) { resolveViaKumanimeApi(secondaryQ, episode) })
                    tasks.add(async(Dispatchers.IO) { resolveViaAllAnime(secondaryQ, episode) })
                }
                tasks.awaitAll().filterNotNull()
            }
        }.orEmpty()

        // Prefer direct native ExoPlayer streams (isWebEmbed = false) first, then clean Sub Indo embed
        candidates.firstOrNull { !it.isWebEmbed } ?: candidates.firstOrNull()
    }

    /**
     * Kumanime API (https://github.com/MastayY/kumanime-api -> AnimeIndo Sub Indo):
     * Routes: /api/search/:query -> /api/anime/:slug -> /api/episode/:slug
     */
    private fun resolveViaKumanimeApi(queryTitle: String, episode: Int): DirectStreamResult? {
        val clean = queryTitle.replace(Regex("[^a-zA-Z0-9 ]"), " ").trim()
        if (clean.isBlank()) return null
        val base = "https://kumanime.vercel.app/api"
        return try {
            val enc = URLEncoder.encode(clean, "UTF-8")
            val searchBody = httpGet("$base/search/$enc", "https://kumanime.vercel.app/") ?: return null
            val root = JSONObject(searchBody)
            val arr = root.optJSONArray("data") ?: root.optJSONArray("results") ?: root.optJSONArray("anime") ?: return null
            if (arr.length() == 0) return null
            val first = arr.optJSONObject(0) ?: return null
            val slug = extractSlug(first.optString("slug", first.optString("endpoint", first.optString("id", ""))))
            if (slug.isBlank()) return null

            val detailBody = httpGet("$base/anime/$slug", "https://kumanime.vercel.app/") ?: return null
            val detailRoot = JSONObject(detailBody)
            val dataObj = detailRoot.optJSONObject("data") ?: detailRoot
            val eps = dataObj.optJSONArray("episodes")
                ?: dataObj.optJSONArray("episode_list")
                ?: dataObj.optJSONArray("episodeList")
                ?: return null

            var epSlug = ""
            for (i in 0 until eps.length()) {
                val eo = eps.optJSONObject(i) ?: continue
                val epTitle = eo.optString("title", eo.optString("episode", eo.optString("name", "")))
                val epNum = eo.optInt("number", -1)
                if (epNum == episode || Regex("""(?:episode|eps?)\s*0?$episode\b""", RegexOption.IGNORE_CASE).containsMatchIn(epTitle) || epTitle.trim() == episode.toString()) {
                    epSlug = extractSlug(eo.optString("slug", eo.optString("endpoint", eo.optString("id", ""))))
                    break
                }
            }
            if (epSlug.isBlank()) return null

            val epBody = httpGet("$base/episode/$epSlug", "https://kumanime.vercel.app/") ?: return null
            extractStreamFromGenericJson(epBody, "https://animeindo.lol/")
        } catch (_: Exception) { null }
    }

    /**
     * SankaVollereii Anime API (https://www.sankavollerei.web.id/anime):
     * Aggregates AnimeIndo / Otakudesu / Kuramanime Sub Indo streaming links.
     */
    private fun resolveViaSankaAnimeApi(queryTitle: String, episode: Int): DirectStreamResult? {
        val clean = queryTitle.replace(Regex("[^a-zA-Z0-9 ]"), " ").trim()
        if (clean.isBlank()) return null
        val base = "https://www.sankavollerei.web.id/anime"
        return try {
            val enc = URLEncoder.encode(clean, "UTF-8")
            val searchBody = httpGet("$base/search/$enc", "https://www.sankavollerei.web.id/") ?: return null
            val root = JSONObject(searchBody)
            val arr = root.optJSONArray("data") ?: root.optJSONArray("animeList") ?: root.optJSONArray("results") ?: return null
            if (arr.length() == 0) return null
            val first = arr.optJSONObject(0) ?: return null
            val slug = extractSlug(first.optString("slug", first.optString("animeId", first.optString("endpoint", ""))))
            if (slug.isBlank()) return null

            val detailBody = httpGet("$base/anime/$slug", "https://www.sankavollerei.web.id/") ?: return null
            val dRoot = JSONObject(detailBody)
            val dObj = dRoot.optJSONObject("data") ?: dRoot
            val eps = dObj.optJSONArray("episodeList") ?: dObj.optJSONArray("episodes") ?: return null
            var epSlug = ""
            for (i in 0 until eps.length()) {
                val eo = eps.optJSONObject(i) ?: continue
                val epTitle = eo.optString("title", eo.optString("episode", ""))
                val epNum = eo.optInt("eps", eo.optInt("number", -1))
                if (epNum == episode || Regex("""(?:episode|eps?)\s*0?$episode\b""", RegexOption.IGNORE_CASE).containsMatchIn(epTitle)) {
                    epSlug = extractSlug(eo.optString("slug", eo.optString("episodeId", eo.optString("endpoint", ""))))
                    break
                }
            }
            if (epSlug.isBlank()) return null

            val epBody = httpGet("$base/episode/$epSlug", "https://www.sankavollerei.web.id/") ?: return null
            extractStreamFromGenericJson(epBody, "https://www.sankavollerei.web.id/")
        } catch (_: Exception) { null }
    }

    /**
     * Otakudesu Public APIs (otakudesu-api-jade.vercel.app & otakudesu-anime-api.vercel.app)
     */
    private fun resolveViaOtakudesuApis(queryTitle: String, episode: Int): DirectStreamResult? {
        val clean = queryTitle.replace(Regex("[^a-zA-Z0-9 ]"), " ").trim()
        if (clean.isBlank()) return null
        val enc = URLEncoder.encode(clean, "UTF-8")
        val hosts = listOf(
            "https://otakudesu-api-jade.vercel.app/api",
            "https://otakudesu-anime-api.vercel.app/api/v1"
        )
        for (base in hosts) {
            try {
                val searchBody = httpGet("$base/search/$enc", "$base/") ?: continue
                val sRoot = JSONObject(searchBody)
                val list = sRoot.optJSONArray("search") ?: sRoot.optJSONArray("data") ?: continue
                if (list.length() == 0) continue
                val first = list.optJSONObject(0) ?: continue
                val endpoint = extractSlug(first.optString("endpoint", first.optString("slug", "")))
                if (endpoint.isBlank()) continue

                val detailBody = httpGet("$base/detail/$endpoint", "$base/")
                    ?: httpGet("$base/anime/$endpoint", "$base/")
                    ?: continue
                val dRoot = JSONObject(detailBody)
                val dObj = dRoot.optJSONObject("data") ?: dRoot
                val eps = dObj.optJSONArray("episode_list") ?: dObj.optJSONArray("episodeList") ?: continue
                var epEndpoint = ""
                for (i in 0 until eps.length()) {
                    val eo = eps.optJSONObject(i) ?: continue
                    val epTitle = eo.optString("episode_title", eo.optString("title", eo.optString("episode", "")))
                    if (Regex("""(?:episode|eps?)\s*0?$episode\b""", RegexOption.IGNORE_CASE).containsMatchIn(epTitle)) {
                        epEndpoint = extractSlug(eo.optString("episode_endpoint", eo.optString("slug", eo.optString("endpoint", ""))))
                        break
                    }
                }
                if (epEndpoint.isBlank()) continue

                val epBody = httpGet("$base/episode/$epEndpoint", "$base/") ?: continue
                val found = extractStreamFromGenericJson(epBody, "https://otakudesu.cloud/")
                if (found != null) return found
            } catch (_: Exception) { }
        }
        return null
    }

    private fun extractStreamFromGenericJson(jsonStr: String, defaultReferer: String): DirectStreamResult? {
        return try {
            val root = JSONObject(jsonStr)
            val data = root.optJSONObject("data") ?: root
            val directCandidates = mutableListOf<String>()
            val embedCandidates = mutableListOf<String>()

            fun inspectUrl(raw: String) {
                val u = raw.trim()
                if (!u.startsWith("http") || dubRegex.containsMatchIn(u)) return
                if (u.contains(".m3u8", true) || u.contains(".mp4", true)) directCandidates.add(u)
                else embedCandidates.add(u)
            }

            listOf("streamLink", "stream_link", "defaultStreamingUrl", "streamUrl", "iframe", "embed", "video").forEach { k ->
                inspectUrl(data.optString(k, ""))
                inspectUrl(root.optString(k, ""))
            }

            fun scanArray(arr: JSONArray?) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val el = arr.opt(i)
                    when (el) {
                        is String -> inspectUrl(el)
                        is JSONObject -> {
                            listOf("url", "link", "src", "href", "stream", "embed").forEach { k ->
                                inspectUrl(el.optString(k, ""))
                            }
                            scanArray(el.optJSONArray("links"))
                            scanArray(el.optJSONArray("servers"))
                            scanArray(el.optJSONArray("streams"))
                        }
                    }
                }
            }
            scanArray(data.optJSONArray("streams"))
            scanArray(data.optJSONArray("servers"))
            scanArray(data.optJSONArray("mirror"))
            scanArray(data.optJSONArray("streamList"))

            val bestDirect = directCandidates.firstOrNull { it.contains("720") } ?: directCandidates.firstOrNull()
            if (bestDirect != null) {
                return DirectStreamResult(bestDirect, defaultReferer, isWebEmbed = false)
            }
            for (emb in embedCandidates) {
                if (emb.contains("streamtape", true)) {
                    val st = extractStreamTapeDirect(emb)
                    if (st.isNotBlank()) return DirectStreamResult(st, emb, isWebEmbed = false)
                }
            }
            val bestEmbed = embedCandidates.firstOrNull { !it.contains("facebook") && !it.contains("disqus") }
            if (bestEmbed != null) {
                return DirectStreamResult(bestEmbed, defaultReferer, isWebEmbed = true)
            }
            null
        } catch (_: Exception) { null }
    }

    private fun extractSlug(raw: String): String {
        val clean = raw.trim().trimEnd('/')
        if (clean.isBlank()) return ""
        return if (clean.contains("/")) clean.substringAfterLast('/') else clean
    }

    private fun resolveViaIndoAnimeMirror(queryTitle: String, episode: Int): DirectStreamResult? {
        val cleanQuery = queryTitle.replace(Regex("[^a-zA-Z0-9 ]"), " ").trim()
        if (cleanQuery.isBlank()) return null
        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
        val mirrors = listOf("https://animeindo.lol", "https://otakudesu.cloud", "https://samehadaku.mba")
        for (base in mirrors) {
            try {
                val searchHtml = httpGet("$base/?s=$encoded&post_type=post", "$base/") ?: continue
                val animeHref = Regex("href=\"($base/(?:anime|series)/[^\"]+)\"").find(searchHtml)?.groupValues?.getOrNull(1) ?: continue
                val detailHtml = httpGet(animeHref, "$base/") ?: continue
                val epPattern = Regex("href=\"($base/[^\"]*(?:episode|eps)[-_]?0?$episode(?:[^0-9\"][^\" ]*)?)\"", RegexOption.IGNORE_CASE)
                val epHref = epPattern.find(detailHtml)?.groupValues?.getOrNull(1) ?: continue
                val epHtml = httpGet(epHref, animeHref) ?: continue

                val directVid = Regex("(https?://[^\"'\\s]+\\.(?:m3u8|mp4)[^\"'\\s]*)").find(epHtml)?.groupValues?.getOrNull(1)
                if (!directVid.isNullOrBlank() && !dubRegex.containsMatchIn(directVid)) {
                    return DirectStreamResult(streamUrl = directVid, referer = epHref, isWebEmbed = false)
                }
                val iframeSrc = Regex("<iframe[^>]+src=\"(https?://[^\"]+)\"", RegexOption.IGNORE_CASE)
                    .findAll(epHtml)
                    .map { it.groupValues[1] }
                    .firstOrNull { !it.contains("facebook") && !it.contains("disqus") && !it.contains("ads") && !dubRegex.containsMatchIn(it) }
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
                .put("limit", 8)
                .put("page", 1)
                .put("translationType", "sub")
                .put("countryOrigin", "ALL")

            val searchUrl = "$ALLANIME_API?variables=${URLEncoder.encode(vars.toString(), "UTF-8")}&query=${URLEncoder.encode(searchGql, "UTF-8")}"
            val searchBody = httpGet(searchUrl, ALLANIME_REFERER) ?: return null
            val edges = JSONObject(searchBody).optJSONObject("data")?.optJSONObject("shows")?.optJSONArray("edges") ?: return null
            if (edges.length() == 0) return null

            val showId = edges.optJSONObject(0)?.optString("_id", "").orEmpty()
            if (showId.isBlank()) return null

            val epGql = """query(${'$'}showId: String!, ${'$'}translationType: VaildTranslationTypeEnumType!, ${'$'}episodeString: String!) { episode(showId: ${'$'}showId, translationType: ${'$'}translationType, episodeString: ${'$'}episodeString) { sourceUrls } }"""
            val epVars = JSONObject()
                .put("showId", showId)
                .put("translationType", "sub")
                .put("episodeString", episode.toString())

            val epUrl = "$ALLANIME_API?variables=${URLEncoder.encode(epVars.toString(), "UTF-8")}&query=${URLEncoder.encode(epGql, "UTF-8")}"
            val epBody = httpGet(epUrl, ALLANIME_REFERER) ?: return null
            val sourceUrls = JSONObject(epBody).optJSONObject("data")?.optJSONObject("episode")?.optJSONArray("sourceUrls") ?: return null

            val candidates = mutableListOf<String>()
            for (i in 0 until sourceUrls.length()) {
                val obj = sourceUrls.optJSONObject(i) ?: continue
                val srcName = obj.optString("sourceName", "")
                if (dubRegex.containsMatchIn(srcName)) continue
                val rawUrl = obj.optString("sourceUrl", "")
                val decoded = if (rawUrl.startsWith("--")) decodeAllAnimeHex(rawUrl.removePrefix("--")) else rawUrl
                if (decoded.contains("/clock")) {
                    val clockUrl = if (decoded.startsWith("http")) decoded.replace("/clock?", "/clock.json?")
                    else "https://allanime.day" + decoded.replace("/clock?", "/clock.json?")
                    val resolved = fetchClockJson(clockUrl)
                    if (resolved != null) return resolved
                } else if (decoded.startsWith("http") && !dubRegex.containsMatchIn(decoded)) {
                    candidates.add(decoded)
                }
            }

            val direct = candidates
                .filter { it.contains(".m3u8") || it.contains(".mp4") }
                .maxByOrNull { u ->
                    when {
                        u.contains("720") -> 50
                        u.contains("1080") -> 40
                        u.contains("480") -> 30
                        else -> 35
                    }
                }
            if (direct != null) {
                return DirectStreamResult(streamUrl = direct, referer = ALLANIME_REFERER, isWebEmbed = false)
            }
        } catch (_: Exception) { }
        return null
    }

    private fun fetchClockJson(clockUrl: String): DirectStreamResult? {
        val body = httpGet(clockUrl, ALLANIME_REFERER) ?: return null
        return try {
            val links = JSONObject(body).optJSONArray("links") ?: return null
            var bestStream = ""
            var bestScore = Int.MIN_VALUE
            var bestSub = ""
            for (i in 0 until links.length()) {
                val item = links.optJSONObject(i) ?: continue
                val link = item.optString("link", "")
                val resStr = item.optString("resolutionStr", "").lowercase()
                if (link.startsWith("http") && !dubRegex.containsMatchIn(link)) {
                    val score = when {
                        resStr.contains("720") -> 50
                        resStr.contains("1080") -> 40
                        resStr.contains("480") -> 30
                        else -> 35
                    }
                    if (score > bestScore) { bestScore = score; bestStream = link }
                }
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
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) { null }
    }
}
