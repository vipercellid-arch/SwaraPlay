package com.swara.play.data.provider

import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * EncDecEndpoints Direct Stream & Subtitle Engine (https://enc-dec.app):
 * 1. Resolves IMDb -> TMDB/Flix ID via /db/flix/find?imdb_id=...
 * 2. Cinejoy (api.shegu.st) via /api/enc-cinejoy?url=... & /api/dec-cinejoy -> Direct .m3u8/.mp4 + Subtitles
 * 3. yFlix (yflix.to) via /api/enc-movies-flix & /api/dec-movies-flix -> Direct .m3u8 + Subtitles
 * 4. AnimeKai (animekai.to) via /db/kai/find?anilist_id=... & /api/enc-kai & /api/dec-kai -> Original JP Audio + Subtitles
 */
object EncDecStreamResolver {
    private const val ENC_DEC_BASE = "https://enc-dec.app"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val strictDubRegex = Regex(
        """\b(dub|dubbed|dubbing|dual[\s._-]*audio|multi[\s._-]*audio|hindi|hin|tamil|tam|telugu|tel|latino|spanish|espanol|rus|russian|french|vf|vff|german|italian|dublado)\b""",
        RegexOption.IGNORE_CASE
    )

    private data class FlixMapping(val tmdbId: String, val flixId: String)

    private fun lookupFlixMapping(item: MediaItem): FlixMapping? {
        val imdb = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else "" }
        val url = if (imdb.startsWith("tt")) {
            "$ENC_DEC_BASE/db/flix/find?imdb_id=${URLEncoder.encode(imdb, "UTF-8")}"
        } else {
            "$ENC_DEC_BASE/db/flix/search?query=${URLEncoder.encode(item.title, "UTF-8")}"
        }
        val body = httpGet(url, ENC_DEC_BASE) ?: return null
        val tmdb = extractFirstString(body, listOf("tmdb_id", "tmdbId"))
        val flix = extractFirstString(body, listOf("flix_id", "flixId", "content_id", "id"))
        return if (tmdb.isNotBlank() || flix.isNotBlank()) FlixMapping(tmdb, flix) else null
    }

    /**
     * Resolves Movie / Series via Cinejoy (api.shegu.st) using exact ?url= param from enc-dec.app docs,
     * with yFlix direct fallback using flix_id.
     */
    fun resolveViaCinejoy(item: MediaItem, season: Int, episode: Int): DirectStreamResult? {
        val mapping = lookupFlixMapping(item)
        val tmdbId = mapping?.tmdbId.orEmpty()
        val isMovie = item.category == MediaCategory.MOVIE

        // 1. Try Cinejoy (api.shegu.st) using exact ?url= parameter
        if (tmdbId.isNotBlank()) {
            try {
                val sheguPath = if (isMovie) {
                    "https://api.shegu.st/movie/$tmdbId"
                } else {
                    "https://api.shegu.st/tv/$tmdbId/$season/$episode"
                }
                val encQuery = "$ENC_DEC_BASE/api/enc-cinejoy?url=${URLEncoder.encode(sheguPath, "UTF-8")}"
                val encBody = httpGet(encQuery, "https://cinejoy.pk/")
                if (encBody != null) {
                    val encJson = JSONObject(encBody)
                    val resultObj = encJson.optJSONObject("result") ?: encJson
                    val targetUrl = resultObj.optString("url", encJson.optString("url", sheguPath)).trim()
                    val sign = resultObj.optString("sign", encJson.optString("sign", "")).trim()
                    val headers = mutableMapOf(
                        "Referer" to "https://cinejoy.pk/",
                        "Origin" to "https://cinejoy.pk"
                    )
                    if (sign.isNotBlank()) headers["X-Sign"] = sign
                    val rawShegu = httpGet(targetUrl, "https://cinejoy.pk/", headers)
                    if (rawShegu != null) {
                        val decBody = httpPostJson("$ENC_DEC_BASE/api/dec-cinejoy", JSONObject().put("text", rawShegu).toString())
                        if (decBody != null) {
                            val parsed = parseDecryptedMediaJson(decBody, "https://cinejoy.pk/")
                            if (parsed != null) return parsed
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        // 2. Try yFlix (enc-movies-flix / dec-movies-flix)
        val flixId = mapping?.flixId.orEmpty()
        if (flixId.isNotBlank()) {
            try {
                val encCid = fetchEncText("$ENC_DEC_BASE/api/enc-movies-flix?text=${URLEncoder.encode(flixId, "UTF-8")}") ?: return null
                val epListBody = httpGet("https://yflix.to/ajax/episodes/list?id=$flixId&_=$encCid", "https://yflix.to/") ?: return null
                val epHtml = runCatching { JSONObject(epListBody).optString("result", epListBody) }.getOrDefault(epListBody)
                val eid = Regex("""data-eid=["']([^"']+)["']""").find(epHtml)?.groupValues?.getOrNull(1) ?: return null
                val encEid = fetchEncText("$ENC_DEC_BASE/api/enc-movies-flix?text=${URLEncoder.encode(eid, "UTF-8")}") ?: return null
                val srvBody = httpGet("https://yflix.to/ajax/links/list?eid=$eid&_=$encEid", "https://yflix.to/") ?: return null
                val srvHtml = runCatching { JSONObject(srvBody).optString("result", srvBody) }.getOrDefault(srvBody)
                val lid = Regex("""data-lid=["']([^"']+)["']""").find(srvHtml)?.groupValues?.getOrNull(1) ?: return null
                val encLid = fetchEncText("$ENC_DEC_BASE/api/enc-movies-flix?text=${URLEncoder.encode(lid, "UTF-8")}") ?: return null
                val viewBody = httpGet("https://yflix.to/ajax/links/view?id=$lid&_=$encLid", "https://yflix.to/") ?: return null
                val encryptedResult = JSONObject(viewBody).optString("result", "").trim()
                if (encryptedResult.isNotBlank()) {
                    val decBody = httpPostJson("$ENC_DEC_BASE/api/dec-movies-flix", JSONObject().put("text", encryptedResult).toString())
                    if (decBody != null) {
                        val parsed = parseDecryptedMediaJson(decBody, "https://yflix.to/")
                        if (parsed != null) return parsed
                    }
                }
            } catch (_: Exception) { }
        }
        return null
    }

    /**
     * Resolves Anime via AnimeKai (animekai.to) using enc-dec.app/db/kai AniList mapping + enc-kai/dec-kai.
     */
    fun resolveViaAnimeKai(item: MediaItem, episode: Int): DirectStreamResult? {
        return try {
            val findUrl = when {
                item.aniListId != null && item.aniListId > 0 -> "$ENC_DEC_BASE/db/kai/find?anilist_id=${item.aniListId}"
                item.malId != null && item.malId > 0 -> "$ENC_DEC_BASE/db/kai/find?mal_id=${item.malId}"
                else -> "$ENC_DEC_BASE/db/kai/search?query=${URLEncoder.encode(item.romajiTitle.ifBlank { item.title }, "UTF-8")}"
            }
            val findBody = httpGet(findUrl, "https://animekai.to/") ?: return null
            val kaiId = extractFirstString(findBody, listOf("kai_id", "ani_id", "id"))
            if (kaiId.isBlank()) return null

            val encCid = fetchEncText("$ENC_DEC_BASE/api/enc-kai?text=${URLEncoder.encode(kaiId, "UTF-8")}") ?: return null
            val epListBody = httpGet("https://animekai.to/ajax/episodes/list?ani_id=$kaiId&_=$encCid", "https://animekai.to/") ?: return null
            val epHtml = runCatching { JSONObject(epListBody).optString("result", epListBody) }.getOrDefault(epListBody)

            val epTokenRegex = Regex("""data-num=["']$episode["'][^>]*token=["']([^"']+)["']|token=["']([^"']+)["'][^>]*data-num=["']$episode["']""", RegexOption.IGNORE_CASE)
            val m = epTokenRegex.find(epHtml) ?: return null
            val epToken = m.groupValues[1].ifBlank { m.groupValues[2] }
            if (epToken.isBlank()) return null

            val encToken = fetchEncText("$ENC_DEC_BASE/api/enc-kai?text=${URLEncoder.encode(epToken, "UTF-8")}") ?: return null
            val srvBody = httpGet("https://animekai.to/ajax/links/list?token=$epToken&_=$encToken", "https://animekai.to/") ?: return null
            val srvHtml = runCatching { JSONObject(srvBody).optString("result", srvBody) }.getOrDefault(srvBody)

            val subSection = srvHtml.substringAfter("data-id=\"sub\"", srvHtml).substringBefore("data-id=\"dub\"")
            val lid = Regex("""data-lid=["']([^"']+)["']""").find(subSection)?.groupValues?.getOrNull(1) ?: return null

            val encLid = fetchEncText("$ENC_DEC_BASE/api/enc-kai?text=${URLEncoder.encode(lid, "UTF-8")}") ?: return null
            val viewBody = httpGet("https://animekai.to/ajax/links/view?id=$lid&_=$encLid", "https://animekai.to/") ?: return null
            val encryptedResult = JSONObject(viewBody).optString("result", "").trim()
            if (encryptedResult.isBlank()) return null

            val decBody = httpPostJson("$ENC_DEC_BASE/api/dec-kai", JSONObject().put("text", encryptedResult).toString()) ?: return null
            parseDecryptedMediaJson(decBody, "https://animekai.to/")
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchEncText(url: String): String? {
        val body = httpGet(url, ENC_DEC_BASE) ?: return null
        return runCatching {
            val j = JSONObject(body)
            j.optString("result", j.optString("enc", "")).trim().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun parseDecryptedMediaJson(jsonStr: String, defaultReferer: String): DirectStreamResult? {
        return try {
            val root = JSONObject(jsonStr)
            val data = root.optJSONObject("result") ?: root.optJSONObject("data") ?: root

            var bestStream = ""
            var bestScore = Int.MIN_VALUE
            var bestSubId = ""
            var bestSubEn = ""

            fun considerStream(url: String, label: String = "") {
                val u = url.trim()
                if (!u.startsWith("http")) return
                if (strictDubRegex.containsMatchIn("$u $label")) return
                val low = "$u $label".lowercase()
                var score = when {
                    low.contains("720") -> 60
                    low.contains("1080") -> 55
                    low.contains("480") -> 35
                    else -> 45
                }
                if (u.contains(".m3u8", true) || u.contains(".mp4", true)) score += 20
                if (score > bestScore) {
                    bestScore = score
                    bestStream = u
                }
            }

            fun considerSub(url: String, lang: String) {
                val u = url.trim()
                if (!u.startsWith("http")) return
                val l = lang.lowercase()
                if (l.contains("indo") || l == "id" || l == "in" || l == "ind") {
                    if (bestSubId.isBlank()) bestSubId = u
                } else if (l.contains("eng") || l == "en") {
                    if (bestSubEn.isBlank()) bestSubEn = u
                }
            }

            listOf("url", "file", "stream", "hls", "src").forEach { k -> considerStream(data.optString(k, "")) }

            fun scanSources(arr: JSONArray?) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val el = arr.opt(i)
                    when (el) {
                        is String -> considerStream(el)
                        is JSONObject -> {
                            val lbl = el.optString("label", el.optString("quality", el.optString("name", "")))
                            listOf("url", "file", "src", "link").forEach { k -> considerStream(el.optString(k, ""), lbl) }
                        }
                    }
                }
            }
            scanSources(data.optJSONArray("sources"))
            scanSources(data.optJSONArray("streams"))
            scanSources(data.optJSONArray("servers"))

            fun scanSubs(arr: JSONArray?) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val u = o.optString("file", o.optString("url", o.optString("src", "")))
                    val lang = o.optString("label", o.optString("lang", o.optString("language", "")))
                    considerSub(u, lang)
                }
            }
            scanSubs(data.optJSONArray("subtitles"))
            scanSubs(data.optJSONArray("tracks"))
            scanSubs(data.optJSONArray("captions"))

            if (bestStream.isBlank()) return null
            val isDirect = bestStream.contains(".m3u8", true) || bestStream.contains(".mp4", true)
            DirectStreamResult(
                streamUrl = bestStream,
                referer = defaultReferer,
                subtitleVttUrl = bestSubId.ifBlank { bestSubEn },
                isWebEmbed = !isDirect
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFirstString(jsonStr: String, keys: List<String>): String {
        return try {
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                val first = arr.optJSONObject(0) ?: return ""
                for (k in keys) {
                    val v = first.optString(k, "").trim()
                    if (v.isNotBlank()) return v
                }
                ""
            } else {
                val root = JSONObject(trimmed)
                val target = root.optJSONObject("result")
                    ?: root.optJSONObject("data")
                    ?: root.optJSONArray("results")?.optJSONObject(0)
                    ?: root.optJSONArray("data")?.optJSONObject(0)
                    ?: root
                for (k in keys) {
                    val v = target.optString(k, "").trim()
                    if (v.isNotBlank()) return v
                }
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun httpGet(url: String, referer: String, extraHeaders: Map<String, String> = emptyMap()): String? {
        return try {
            val b = Request.Builder()
                .url(url)
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .header("Referer", referer)
            extraHeaders.forEach { (k, v) -> b.header(k, v) }
            HttpClientProvider.client.newCall(b.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun httpPostJson(url: String, jsonBody: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .header("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
