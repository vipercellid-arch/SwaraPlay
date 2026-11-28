package com.swara.play.data.provider

import android.util.Base64
import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.security.SecureRandom

/**
 * SwaraPlay v10.0.0 — 100% Pure EncDecEndpoints Native Stream Engine (https://enc-dec.app)
 * Zero WebView embeds, zero legacy third-party scrapers.
 *
 * Implements the exact protocols from smy778/encdecendpoints:
 * - Movies & Series: VidLink, Cinejoy (api.wing.st/g), Videasy (api.wecollege.net),
 *   Nexa (providers.nexa.mov), MeowTV (api.meowtv.ru), yFlix + Rapidshare, Hexa,
 *   VidFast (vidfast.vc), VidCore, VidUp
 * - Anime: AnimeKai + MegaUp (/db/kai + dec-mega), MegaPlay (megaplay.buzz), Nexa Anime (AniList)
 * - Asian Drama: KissKH (enc-kisskh + dec-kisskh subtitles) + Movie/Series providers
 */
data class DirectStreamResult(
    val providerId: String,
    val providerName: String,
    val streamUrl: String,
    val referer: String = "",
    val origin: String = "",
    val subtitleVttUrl: String = "",
    val qualityLabel: String = "HD"
)

object EncDecStreamResolver {
    private const val ENC_DEC_BASE = "https://enc-dec.app"
    private const val API = "$ENC_DEC_BASE/api"
    private const val DB = "$ENC_DEC_BASE/db"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val octetMediaType = "application/octet-stream".toMediaType()
    private val emptyMediaType = "text/plain; charset=utf-8".toMediaType()
    private val secureRandom = SecureRandom()

    private val strictDubRegex = Regex(
        """\b(dub|dubbed|dubbing|dual[\s._-]*audio|multi[\s._-]*audio|hindi|hin|tamil|tam|telugu|tel|latino|spanish|espanol|rus|russian|french|vf|vff|german|italian|dublado)\b""",
        RegexOption.IGNORE_CASE
    )

    private data class FlixMapping(
        val tmdbId: String,
        val flixId: String,
        val episodesJson: JSONObject?
    )

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun base64UrlDecode(str: String): ByteArray {
        val clean = str.trim()
        val pad = (4 - clean.length % 4) % 4
        return Base64.decode(clean + "=".repeat(pad), Base64.URL_SAFE or Base64.NO_WRAP)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    private fun randomHex32Bytes(): String {
        val b = ByteArray(32)
        secureRandom.nextBytes(b)
        return bytesToHex(b)
    }

    private fun urlEnc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun lookupFlixMapping(item: MediaItem): FlixMapping? {
        val imdb = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else "" }
        val typeParam = if (item.category == MediaCategory.MOVIE) "movie" else "tv"
        val urls = buildList {
            if (imdb.startsWith("tt")) {
                add("$DB/flix/find?imdb_id=${urlEnc(imdb)}&type=$typeParam")
                add("$DB/flix/find?imdb_id=${urlEnc(imdb)}")
            }
            add("$DB/flix/search?query=${urlEnc(item.title)}&type=$typeParam")
        }
        for (u in urls) {
            val body = httpGetText(u, ENC_DEC_BASE) ?: continue
            val entry = extractFirstEntryObject(body) ?: continue
            val info = entry.optJSONObject("info") ?: entry
            val tmdb = firstNonBlank(
                info.optString("tmdb_id", ""),
                info.optString("tmdbId", ""),
                entry.optString("tmdb_id", ""),
                entry.optString("tmdbId", "")
            )
            val flix = firstNonBlank(
                info.optString("flix_id", ""),
                info.optString("flixId", ""),
                entry.optString("flix_id", ""),
                entry.optString("id", "")
            )
            val eps = entry.optJSONObject("episodes")
            if (tmdb.isNotBlank() || flix.isNotBlank()) {
                return FlixMapping(tmdb, flix, eps)
            }
        }
        return null
    }

    private val tmdbCache = ConcurrentHashMap<String, String>()
    @Volatile private var hexaCapToken: String = ""
    @Volatile private var hexaCapUses: Int = 0
    @Volatile private var hexaCapExp: Long = 0L

    fun resolveTmdbIdQuick(item: MediaItem): String {
        val key = "${item.id}_${item.category.name}"
        tmdbCache[key]?.let { if (it.isNotBlank()) return it }
        val found = lookupFlixMapping(item)?.tmdbId.orEmpty()
        if (found.isNotBlank()) tmdbCache[key] = found
        return found
    }

    private fun getHexaCapToken(): String? {
        val now = System.currentTimeMillis()
        if (hexaCapToken.isNotBlank() && hexaCapUses < 18 && now < hexaCapExp) {
            hexaCapUses += 1
            return hexaCapToken
        }
        val capHeaders = mapOf(
            "accept-language" to "en-US,en;q=0.9,id;q=0.5",
            "origin" to "https://hexa.su",
            "referer" to "https://hexa.su/"
        )
        val challengeBody = httpPostEmpty("https://cap.hexa.su/15d2cf0395/challenge", capHeaders) ?: return null
        val challengeJson = runCatching { JSONObject(challengeBody) }.getOrNull() ?: return null
        val encHexaBody = httpPostJson("$API/enc-hexa", JSONObject().put("challenge_data", challengeJson).toString()) ?: return null
        val solved = validateResultObject(encHexaBody) ?: return null
        val redeemBody = httpPostJson("https://cap.hexa.su/15d2cf0395/redeem", solved.toString(), capHeaders) ?: return null
        val token = runCatching { JSONObject(redeemBody).optString("token", "") }.getOrDefault("")
        if (token.isBlank()) return null
        hexaCapToken = token
        hexaCapUses = 1
        hexaCapExp = now + 4 * 60 * 1000L
        return token
    }

    /**
     * Resolves ALL working EncDecEndpoints direct streams for Movies, TV Series, and Asian Drama
     * concurrently so playback starts cleanly and user can switch servers in PlayerScreen.
     */
    suspend fun resolveAllMovieSeriesStreams(
        item: MediaItem,
        season: Int,
        episode: Int
    ): List<DirectStreamResult> = withContext(Dispatchers.IO) {
        val mapping = lookupFlixMapping(item)
        val tmdbId = mapping?.tmdbId.orEmpty()
        if (tmdbId.isNotBlank()) tmdbCache["${item.id}_${item.category.name}"] = tmdbId
        val isMovie = item.category == MediaCategory.MOVIE
        val isAsianDrama = item.category == MediaCategory.ASIAN_DRAMA

        val tasks = mutableListOf<suspend () -> List<DirectStreamResult>>()

        if (isAsianDrama) {
            tasks += { listOfNotNull(withTimeoutOrNull(7500L) { resolveViaKissKh(item, episode) }) }
            tasks += { listOfNotNull(withTimeoutOrNull(6500L) { resolveViaOneTouchTv(item, episode) }) }
        }
        if (tmdbId.isNotBlank()) {
            // 3 Penyedia Prioritas Utama: Hexa, Nexa, MeowTV
            tasks += { withTimeoutOrNull(9000L) { resolveAllViaHexa(tmdbId, isMovie, season, episode) }.orEmpty() }
            tasks += { withTimeoutOrNull(9000L) { resolveAllViaNexa(tmdbId, isMovie, season, episode) }.orEmpty() }
            tasks += { withTimeoutOrNull(9000L) { resolveAllViaMeowTv(tmdbId, isMovie, season, episode) }.orEmpty() }
            // Penyedia Cadangan EncDecEndpoints
            tasks += { listOfNotNull(withTimeoutOrNull(6500L) { resolveViaVidLink(tmdbId, isMovie, season, episode) }) }
            tasks += { listOfNotNull(withTimeoutOrNull(8500L) { resolveViaCinejoy(item, tmdbId, isMovie, season, episode) }) }
            tasks += { listOfNotNull(withTimeoutOrNull(8000L) { resolveViaCineSrc(tmdbId, isMovie, season, episode) }) }
            tasks += { listOfNotNull(withTimeoutOrNull(8500L) { resolveViaVideasy(item, tmdbId, isMovie, season, episode) }) }
            tasks += { listOfNotNull(withTimeoutOrNull(9000L) { resolveViaVidStageProvider("vidfast", "VidFast", "https://vidfast.vc", tmdbId, isMovie, season, episode) }) }
            tasks += { listOfNotNull(withTimeoutOrNull(9000L) { resolveViaVidStageProvider("vidcore", "VidCore", "https://vidcore.io", tmdbId, isMovie, season, episode) }) }
            tasks += { listOfNotNull(withTimeoutOrNull(9000L) { resolveViaVidStageProvider("vidup", "VidUp", "https://vidup.to", tmdbId, isMovie, season, episode) }) }
            tasks += { listOfNotNull(withTimeoutOrNull(7500L) { resolveViaByse(item.imdbId, tmdbId, isMovie, season, episode) }) }
        }
        if (mapping != null && (mapping.episodesJson != null || mapping.flixId.isNotBlank())) {
            tasks += { listOfNotNull(withTimeoutOrNull(9000L) { resolveViaYFlixRapid(mapping, season, episode) }) }
        }

        coroutineScope {
            tasks.map { fn -> async { runCatching { fn() }.getOrDefault(emptyList()) } }
                .awaitAll()
                .flatten()
                .distinctBy { it.streamUrl }
        }
    }

    /**
     * Resolves ALL working EncDecEndpoints direct streams for Anime concurrently:
     * 1. AnimeKai + MegaUp (/db/kai + dec-mega)
     * 2. MegaPlay (megaplay.buzz + dec-megaplay)
     * 3. Nexa Anime (providers.nexa.mov platform=anilist + enc-nexa/dec-nexa)
     */
    suspend fun resolveAllAnimeStreams(
        item: MediaItem,
        episode: Int
    ): List<DirectStreamResult> = withContext(Dispatchers.IO) {
        coroutineScope {
            val d1 = async { withTimeoutOrNull(9000L) { resolveViaAnimeKaiMegaUp(item, episode) } }
            val d2 = async { withTimeoutOrNull(8000L) { resolveViaMegaPlay(item, episode) } }
            val d3 = async { withTimeoutOrNull(8000L) { resolveViaNexaAnime(item, episode) } }
            listOfNotNull(d1.await(), d2.await(), d3.await()).distinctBy { it.streamUrl }
        }
    }

    // 1. VidLink (samples/vidlink.py) — Fastest (2 calls, no decrypt needed)
    private fun resolveViaVidLink(tmdbId: String, isMovie: Boolean, season: Int, episode: Int): DirectStreamResult? {
        val encResp = httpGetText("$API/enc-vidlink?text=${urlEnc(tmdbId)}", ENC_DEC_BASE) ?: return null
        val encryptedId = validateResultString(encResp) ?: return null
        val url = if (isMovie) {
            "https://vidlink.pro/api/b/movie/$encryptedId"
        } else {
            "https://vidlink.pro/api/b/tv/$encryptedId/$season/$episode"
        }
        val headers = mapOf("Origin" to "https://vidlink.pro", "Referer" to "https://vidlink.pro/")
        val body = httpGetText(url, "https://vidlink.pro/", headers) ?: return null
        return parseDecryptedMediaJson(body, "vidlink", "VidLink", "https://vidlink.pro/", "https://vidlink.pro")
    }

    // 2. Cinejoy (samples/cinejoy.py) — api.wing.st/servers -> enc-cinejoy -> POST api.wing.st/g -> dec-cinejoy
    private fun resolveViaCinejoy(
        item: MediaItem,
        tmdbId: String,
        isMovie: Boolean,
        season: Int,
        episode: Int
    ): DirectStreamResult? {
        val cineHeaders = mapOf(
            "Accept" to "*/*",
            "Origin" to "https://cinejoy.pk",
            "Referer" to "https://cinejoy.pk/"
        )
        val srvBody = httpGetText("https://api.shegu.st/servers", "https://cinejoy.pk/", cineHeaders) ?: return null
        val srvArr = runCatching { JSONObject(srvBody).optJSONArray("servers") }.getOrNull() ?: return null
        val serverNames = mutableListOf<String>()
        for (i in 0 until minOf(srvArr.length(), 4)) {
            val name = srvArr.optJSONObject(i)?.optString("name", "").orEmpty().trim()
            if (name.isNotBlank()) serverNames += name
        }
        val type = if (isMovie) "movie" else "series"
        val year = Regex("""\d{4}""").find(item.year)?.value ?: "2024"
        val imdb = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else "" }

        for (srv in serverNames) {
            try {
                val target = buildString {
                    append("https://api.shegu.st/?title=").append(urlEnc(item.title))
                    append("&type=").append(type)
                    append("&year=").append(year)
                    append("&imdb=").append(urlEnc(imdb))
                    append("&tmdb=").append(urlEnc(tmdbId))
                    append("&server=").append(urlEnc(srv))
                    if (!isMovie) append("&season=").append(season).append("&episode=").append(episode)
                }
                val encBody = httpGetText("$API/enc-cinejoy?url=${urlEnc(target)}", ENC_DEC_BASE) ?: continue
                val encRes = validateResultObject(encBody) ?: continue
                val encData = encRes.optString("data", "").trim()
                val state = encRes.opt("state") ?: continue
                if (encData.isBlank()) continue

                val rawBytes = httpPostBytes("https://api.shegu.st/g", base64UrlDecode(encData), cineHeaders) ?: continue
                val decPayload = JSONObject()
                    .put("text", base64UrlEncode(rawBytes))
                    .put("state", state)
                val decBody = httpPostJson("$API/dec-cinejoy", decPayload.toString()) ?: continue
                val parsed = parseDecryptedMediaJson(decBody, "cinejoy_$srv", "Cinejoy ($srv)", "https://cinejoy.pk/", "https://cinejoy.pk")
                if (parsed != null) return parsed
            } catch (_: Exception) { }
        }
        return null
    }

    // 3. Videasy (samples/videasy.py) — api.wecollege.net/seed -> {server}/sources -> dec-videasy

    private fun resolveViaCineSrc(tmdbId: String, isMovie: Boolean, season: Int, episode: Int): DirectStreamResult? {
        val target = if (isMovie) {
            "https://cinesrc.st/embed/movie/$tmdbId"
        } else {
            "https://cinesrc.st/embed/tv/$tmdbId?s=$season&e=$episode"
        }
        val encBody = httpGetText("$API/enc-cinesrc?url=${urlEnc(target)}", ENC_DEC_BASE) ?: return null
        val encObj = runCatching { JSONObject(encBody) }.getOrNull() ?: return null
        val resObj = encObj.optJSONObject("result") ?: encObj
        val reqUrl = resObj.optString("url", target).ifBlank { target }
        val headers = mutableMapOf("Referer" to "https://cinesrc.st/", "Origin" to "https://cinesrc.st")
        resObj.optJSONObject("headers")?.let { hObj ->
            val keys = hObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = hObj.optString(k, "")
                if (v.isNotBlank()) headers[k] = v
            }
        }
        val raw = httpGetText(reqUrl, "https://cinesrc.st/", headers) ?: return null
        val decBody = httpPostJson("$API/dec-cinesrc", JSONObject().put("text", raw.trim()).toString()) ?: return null
        return parseDecryptedMediaJson(decBody, "cinesrc", "CineSrc", "https://cinesrc.st/", "https://cinesrc.st")
    }

    private fun resolveViaVideasy(
        item: MediaItem,
        tmdbId: String,
        isMovie: Boolean,
        season: Int,
        episode: Int
    ): DirectStreamResult? {
        val headers = mapOf(
            "Accept" to "*/*",
            "Origin" to "https://vidy.st",
            "Referer" to "https://vidy.st/"
        )
        val seedBody = httpGetText("https://api.wecollege.net/seed?mediaId=${urlEnc(tmdbId)}", "https://vidy.st/", headers) ?: return null
        val seed = runCatching { JSONObject(seedBody).optString("seed", "") }.getOrDefault("")
        if (seed.isBlank()) return null

        val doubleEncodedTitle = urlEnc(urlEnc(item.title))
        val type = if (isMovie) "movie" else "tv"
        val year = Regex("""\d{4}""").find(item.year)?.value ?: "2024"
        val imdb = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else "" }

        val candidateServers = listOf("miami", "boise", "vegas", "seattle")
        for (srv in candidateServers) {
            try {
                val url = if (isMovie) {
                    "https://api.wecollege.net/$srv/sources?title=$doubleEncodedTitle&mediaType=movie&year=$year&tmdbId=$tmdbId&imdbId=$imdb&enc=2&seed=${urlEnc(seed)}"
                } else {
                    "https://api.wecollege.net/$srv/sources?title=$doubleEncodedTitle&mediaType=$type&year=$year&episodeId=$episode&seasonId=$season&tmdbId=$tmdbId&imdbId=$imdb&enc=2&seed=${urlEnc(seed)}"
                }
                val encData = httpGetText(url, "https://vidy.st/", headers) ?: continue
                if (encData.isBlank()) continue
                val decPayload = JSONObject()
                    .put("text", encData)
                    .put("id", tmdbId)
                    .put("seed", seed)
                val decBody = httpPostJson("$API/dec-videasy", decPayload.toString()) ?: continue
                val parsed = parseDecryptedMediaJson(decBody, "videasy_$srv", "Videasy (${srv.replaceFirstChar { it.uppercase() }})", "https://vidy.st/", "https://vidy.st")
                if (parsed != null) return parsed
            } catch (_: Exception) { }
        }
        return null
    }

    // 4. Nexa (EXACT samples/nexa.py — parallel multi-server)
    private suspend fun resolveAllViaNexa(tmdbId: String, isMovie: Boolean, season: Int, episode: Int): List<DirectStreamResult> = coroutineScope {
        val animeOnly = setOf("kyoto", "tokyo", "hokkaido", "fuji")
        val nexaHeaders = mapOf("Referer" to "https://nexa.mov/")
        val statusBody = httpGetText("https://providers.nexa.mov/v1/status", "https://nexa.mov/", nexaHeaders) ?: return@coroutineScope emptyList()
        val sourcesArr = runCatching { JSONObject(statusBody).optJSONArray("sources") }.getOrNull() ?: return@coroutineScope emptyList()
        val servers = mutableListOf<String>()
        for (i in 0 until sourcesArr.length()) {
            val obj = sourcesArr.optJSONObject(i) ?: continue
            if (obj.optBoolean("online", true) == false) continue
            val code = obj.optString("codename", "").trim()
            if (code.isNotBlank() && code.lowercase() !in animeOnly) servers += code
            if (servers.size >= 6) break
        }
        servers.map { srv ->
            async(Dispatchers.IO) {
                runCatching {
                    val url = if (isMovie) {
                        "https://providers.nexa.mov/?platform=tmdb&id=$tmdbId&server=$srv"
                    } else {
                        "https://providers.nexa.mov/?platform=tmdb&id=$tmdbId&server=$srv&season=$season&episode=$episode"
                    }
                    val encBody = httpGetText("$API/enc-nexa?url=${urlEnc(url)}", ENC_DEC_BASE) ?: return@runCatching emptyList()
                    val encodedB64 = validateResultString(encBody) ?: return@runCatching emptyList()
                    val reqBytes = Base64.decode(encodedB64, Base64.DEFAULT)
                    val respBytes = httpPostBytes(
                        "https://providers.nexa.mov/v1/playlist",
                        reqBytes,
                        mapOf("Referer" to "https://nexa.mov/", "Content-Type" to "application/octet-stream")
                    ) ?: return@runCatching emptyList()
                    val hex = bytesToHex(respBytes)
                    val decBody = httpPostJson("$API/dec-nexa", JSONObject().put("text", hex).put("server", srv).toString()) ?: return@runCatching emptyList()
                    val niceName = srv.replaceFirstChar { it.uppercase() }
                    parseAllDecryptedMediaJson(decBody, "nexa_$srv", "Nexa ($niceName)", "https://nexa.mov/", "https://nexa.mov")
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten().distinctBy { it.streamUrl }
    }

    // 4b. Nexa Anime (samples/nexa.py with platform=anilist)
    private fun resolveViaNexaAnime(item: MediaItem, episode: Int): DirectStreamResult? {
        val aid = item.aniListId?.takeIf { it > 0 }?.toString() ?: return null
        val animeServers = listOf("kyoto", "tokyo", "hokkaido", "fuji")
        for (srv in animeServers) {
            try {
                val url = "https://providers.nexa.mov/?platform=anilist&id=$aid&server=$srv&season=1&episode=$episode"
                val encBody = httpGetText("$API/enc-nexa?url=${urlEnc(url)}", ENC_DEC_BASE) ?: continue
                val encodedB64 = validateResultString(encBody) ?: continue
                val reqBytes = Base64.decode(encodedB64, Base64.DEFAULT)
                val respBytes = httpPostBytes(
                    "https://providers.nexa.mov/v1/playlist",
                    reqBytes,
                    mapOf("Referer" to "https://nexa.mov/", "Content-Type" to "application/octet-stream")
                ) ?: continue
                val hex = bytesToHex(respBytes)
                val decBody = httpPostJson("$API/dec-nexa", JSONObject().put("text", hex).put("server", srv).toString()) ?: continue
                val parsed = parseDecryptedMediaJson(decBody, "nexa_anime_$srv", "Nexa Anime ($srv)", "https://nexa.mov/", "https://nexa.mov")
                if (parsed != null) return parsed
            } catch (_: Exception) { }
        }
        return null
    }

    // 5. MeowTV (EXACT samples/meowtv.py — parallel multi-server)
    private suspend fun resolveAllViaMeowTv(tmdbId: String, isMovie: Boolean, season: Int, episode: Int): List<DirectStreamResult> = coroutineScope {
        val baseHeaders = mapOf("Origin" to "https://meowtv.ru", "Referer" to "https://meowtv.ru/")
        val ticketBody = httpPostEmpty("https://api.meowtv.ru/streams/ticket", baseHeaders) ?: return@coroutineScope emptyList()
        val ticket = runCatching { JSONObject(ticketBody).optString("ticket", "") }.getOrDefault("")
        if (ticket.isBlank()) return@coroutineScope emptyList()

        val reqHeaders = baseHeaders + ("X-Stream-Ticket" to ticket)
        val servers = listOf(
            "english" to "English",
            "acme" to "Acme",
            "dcloud" to "Dcloud",
            "tik" to "TCloud",
            "hindi" to "Hindi"
        )
        servers.map { (srvId, srvLabel) ->
            async(Dispatchers.IO) {
                runCatching {
                    val url = if (isMovie) {
                        "https://api.meowtv.ru/streams/movie/$tmdbId?s=$srvId"
                    } else {
                        "https://api.meowtv.ru/streams/tv/$tmdbId/$season/$episode?s=$srvId"
                    }
                    val dataStr = httpGetText(url, "https://meowtv.ru/", reqHeaders) ?: return@runCatching emptyList()
                    val dataObj = runCatching { JSONObject(dataStr) }.getOrNull() ?: return@runCatching emptyList()
                    val decBody = httpPostJson("$API/dec-meowtv", JSONObject().put("data", dataObj).toString()) ?: return@runCatching emptyList()
                    parseAllDecryptedMediaJson(decBody, "meowtv_$srvId", "MeowTV ($srvLabel)", "https://meowtv.ru/", "https://meowtv.ru")
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten().distinctBy { it.streamUrl }
    }

    // 6. yFlix + Rapidshare (samples/database_flix.py + samples/rapidshare.py)
    private fun resolveViaYFlixRapid(mapping: FlixMapping, season: Int, episode: Int): DirectStreamResult? {
        return try {
            val yHeaders = mapOf("Referer" to "https://yflix.to/", "Accept" to "application/json")
            var eid = mapping.episodesJson
                ?.optJSONObject(season.toString())
                ?.optJSONObject(episode.toString())
                ?.optString("eid", "")
                .orEmpty()

            if (eid.isBlank() && mapping.flixId.isNotBlank()) {
                val encCid = fetchEncText("$API/enc-movies-flix?text=${urlEnc(mapping.flixId)}") ?: return null
                val epResp = httpGetText("https://yflix.to/ajax/episodes/list?id=${mapping.flixId}&_=$encCid", "https://yflix.to/", yHeaders) ?: return null
                val epHtml = JSONObject(epResp).optString("result", "")
                val parsedEps = httpPostJson("$API/parse-html", JSONObject().put("text", epHtml).toString()) ?: return null
                val epRoot = JSONObject(parsedEps).optJSONObject("result")
                eid = epRoot?.optJSONObject(season.toString())?.optJSONObject(episode.toString())?.optString("eid", "").orEmpty()
                    .ifBlank { Regex("""data-eid=["']([^"']+)["']""").find(epHtml)?.groupValues?.getOrNull(1).orEmpty() }
            }
            if (eid.isBlank()) return null

            val encEid = fetchEncText("$API/enc-movies-flix?text=${urlEnc(eid)}") ?: return null
            val srvResp = httpGetText("https://yflix.to/ajax/links/list?eid=$eid&_=$encEid", "https://yflix.to/", yHeaders) ?: return null
            val srvHtml = JSONObject(srvResp).optString("result", "")
            val parsedSrvs = httpPostJson("$API/parse-html", JSONObject().put("text", srvHtml).toString()) ?: return null
            val srvObj = JSONObject(parsedSrvs).optJSONObject("result")
            val defObj = srvObj?.optJSONObject("default")
            val lid = defObj?.optJSONObject("1")?.optString("lid", "").orEmpty()
                .ifBlank { defObj?.optJSONObject("2")?.optString("lid", "").orEmpty() }
                .ifBlank { Regex("""data-lid=["']([^"']+)["']""").find(srvHtml)?.groupValues?.getOrNull(1).orEmpty() }
            if (lid.isBlank()) return null

            val encLid = fetchEncText("$API/enc-movies-flix?text=${urlEnc(lid)}") ?: return null
            val viewResp = httpGetText("https://yflix.to/ajax/links/view?id=$lid&_=$encLid", "https://yflix.to/", yHeaders) ?: return null
            val encEmbed = JSONObject(viewResp).optString("result", "").trim()
            if (encEmbed.isBlank()) return null

            val decFlix = httpPostJson("$API/dec-movies-flix", JSONObject().put("text", encEmbed).toString()) ?: return null
            val embedUrl = validateResultObject(decFlix)?.optString("url", "").orEmpty().trim()
            if (embedUrl.isBlank()) return null

            // Second hop: Rapidshare hoster (/e/ -> /media/ -> dec-rapid)
            if (embedUrl.contains("/e/")) {
                val hostReferer = embedUrl.substringBefore("/e/") + "/"
                val mediaUrl = embedUrl.replace("/e/", "/media/")
                val mediaResp = httpGetText(mediaUrl, hostReferer, mapOf("Accept" to "application/json", "Referer" to hostReferer)) ?: return null
                val mediaEnc = JSONObject(mediaResp).optString("result", "").trim()
                if (mediaEnc.isNotBlank()) {
                    val decRapid = httpPostJson(
                        "$API/dec-rapid",
                        JSONObject().put("text", mediaEnc).put("agent", HttpClientProvider.USER_AGENT).toString()
                    ) ?: return null
                    return parseDecryptedMediaJson(decRapid, "yflix", "yFlix (Rapid)", hostReferer, hostReferer.trimEnd('/'))
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    // 7. Hexa (EXACT samples/hexa.py + 20-use cap cache + flixer.sh fallback + multi-server/quality extraction)
    private fun resolveAllViaHexa(tmdbId: String, isMovie: Boolean, season: Int, episode: Int): List<DirectStreamResult> {
        return try {
            val capToken = getHexaCapToken() ?: return emptyList()
            val apiKey = randomHex32Bytes()
            val reqHeaders = mapOf(
                "Referer" to "https://hexa.su/",
                "Accept" to "text/plain",
                "X-Fingerprint-Lite" to "e9136c41504646444",
                "X-Cap-Token" to capToken,
                "X-Api-Key" to apiKey
            )
            val pathPart = if (isMovie) {
                "api/tmdb/movie/$tmdbId/images"
            } else {
                "api/tmdb/tv/$tmdbId/season/$season/episode/$episode/images"
            }
            var refUsed = "https://hexa.su/"
            var orgUsed = "https://hexa.su"
            var encrypted = httpGetText("https://theemoviedb.hexa.su/$pathPart", refUsed, reqHeaders)
            if (encrypted.isNullOrBlank()) {
                encrypted = httpGetText(
                    "https://theemoviedb.flixer.sh/$pathPart",
                    "https://flixer.su/",
                    reqHeaders + ("Referer" to "https://flixer.su/")
                )
                if (!encrypted.isNullOrBlank()) {
                    refUsed = "https://flixer.su/"
                    orgUsed = "https://flixer.su"
                }
            }
            if (encrypted.isNullOrBlank()) return emptyList()
            val decBody = httpPostJson("$API/dec-hexa", JSONObject().put("text", encrypted.trim()).put("key", apiKey).toString()) ?: return emptyList()
            parseAllDecryptedMediaJson(decBody, "hexa", "Hexa", refUsed, orgUsed)
        } catch (_: Exception) {
            emptyList()
        }
    }

    // 8. VidFast / VidCore / VidUp (samples/vidfast.py, vidcore.py, vidup.py)
    private fun resolveViaVidStageProvider(
        apiSlug: String,
        displayName: String,
        baseDomain: String,
        tmdbId: String,
        isMovie: Boolean,
        season: Int,
        episode: Int
    ): DirectStreamResult? {
        return try {
            val pageUrl = if (isMovie) "$baseDomain/movie/$tmdbId/" else "$baseDomain/tv/$tmdbId/$season/$episode/"
            val html = httpGetText(pageUrl, "$baseDomain/") ?: return null
            val tokenMatch = Regex("""\\\x22(?:en|token)\\\x22:\\\x22(.*?)\\\x22""").find(html)
                ?: Regex("""\x22(?:en|token)\x22:\x22([^\x22]+)\x22""").find(html)
                ?: return null
            val enToken = tokenMatch.groupValues[1]

            val st1Body = httpGetText("$API/enc-$apiSlug?text=${urlEnc(enToken)}&stage=1", ENC_DEC_BASE) ?: return null
            val st1 = validateResultObject(st1Body) ?: return null
            val stage1Url = st1.optString("stage1", "")
            val csrf1 = st1.optString("token", "")
            if (stage1Url.isBlank()) return null

            val h1 = mapOf("Referer" to "$baseDomain/", "X-Requested-With" to "XMLHttpRequest", "X-CSRF-Token" to csrf1)
            val stage1Text = httpPostEmpty(stage1Url, h1) ?: return null

            val st2Body = httpGetText("$API/enc-$apiSlug?text=${urlEnc(stage1Text)}&stage=2", ENC_DEC_BASE) ?: return null
            val st2 = validateResultObject(st2Body) ?: return null
            val serversUrl = st2.optString("servers", "")
            val streamBase = st2.optString("stream", "")
            val csrf2 = st2.optString("token", "")
            if (serversUrl.isBlank() || streamBase.isBlank()) return null

            val h2 = mapOf("Referer" to "$baseDomain/", "X-Requested-With" to "XMLHttpRequest", "X-CSRF-Token" to csrf2)
            val serversEnc = httpPostEmpty(serversUrl, h2) ?: return null
            val serversDecBody = httpPostJson("$API/dec-$apiSlug", JSONObject().put("text", serversEnc).toString()) ?: return null
            val serversArr = JSONObject(serversDecBody).optJSONArray("result") ?: return null

            for (i in 0 until minOf(serversArr.length(), 3)) {
                val dataVal = serversArr.optJSONObject(i)?.optString("data", "").orEmpty()
                if (dataVal.isBlank()) continue
                val streamEnc = httpPostEmpty("$streamBase/$dataVal", h2) ?: continue
                val streamDecBody = httpPostJson("$API/dec-$apiSlug", JSONObject().put("text", streamEnc).toString()) ?: continue
                val parsed = parseDecryptedMediaJson(streamDecBody, apiSlug, displayName, "$baseDomain/", baseDomain)
                if (parsed != null) return parsed
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    // 9. AnimeKai + MegaUp (samples/database_kai.py + samples/animekai.py + samples/megaup.py)
    private fun resolveViaAnimeKaiMegaUp(item: MediaItem, episode: Int): DirectStreamResult? {
        return try {
            val findUrl = when {
                item.aniListId != null && item.aniListId > 0 -> "$DB/kai/find?anilist_id=${item.aniListId}"
                item.malId != null && item.malId > 0 -> "$DB/kai/find?mal_id=${item.malId}"
                else -> "$DB/kai/search?query=${urlEnc(item.romajiTitle.ifBlank { item.title })}"
            }
            val findBody = httpGetText(findUrl, ENC_DEC_BASE) ?: return null
            val entry = extractFirstEntryObject(findBody) ?: return null
            val info = entry.optJSONObject("info")
            val episodesObj = entry.optJSONObject("episodes")
            val epObj = episodesObj?.optJSONObject("1")?.optJSONObject(episode.toString())
                ?: episodesObj?.keys()?.asSequence()?.mapNotNull { episodesObj.optJSONObject(it)?.optJSONObject(episode.toString()) }?.firstOrNull()

            // Fast path: pre-scraped megaup mirror + sources from /db/kai/find
            val megaMirrors = info?.optJSONObject("mirrors")?.optJSONArray("megaup")
            val sourcesObj = epObj?.optJSONObject("sources")
            if (megaMirrors != null && megaMirrors.length() > 0 && sourcesObj != null) {
                val mirrorBase = megaMirrors.optString(0, "").trimEnd('/')
                val prefTypes = listOf("softsub", "sub")
                for (t in prefTypes) {
                    val tObj = sourcesObj.optJSONObject(t) ?: continue
                    for (srvKey in listOf("server1", "server2")) {
                        val mediaPath = tObj.optString(srvKey, "").trim()
                        if (mediaPath.isBlank()) continue
                        val fullUrl = if (mediaPath.startsWith("http")) mediaPath else "$mirrorBase$mediaPath"
                        val resolved = decryptMegaUpEmbedOrMedia(fullUrl)
                        if (resolved != null) return resolved
                    }
                }
            }

            // Fallback path: token -> enc-kai -> links/list -> links/view -> dec-kai -> dec-mega
            val epToken = epObj?.optString("token", "").orEmpty()
            if (epToken.isBlank()) return null
            val kaiHeaders = mapOf("Referer" to "https://animekai.to/", "Accept" to "application/json")
            val encToken = fetchEncText("$API/enc-kai?text=${urlEnc(epToken)}") ?: return null
            val srvResp = httpGetText("https://animekai.to/ajax/links/list?token=$epToken&_=$encToken", "https://animekai.to/", kaiHeaders) ?: return null
            val srvHtml = JSONObject(srvResp).optString("result", "")
            val parsedSrvs = httpPostJson("$API/parse-html", JSONObject().put("text", srvHtml).toString()) ?: return null
            val srvRoot = JSONObject(parsedSrvs).optJSONObject("result")
            val lid = srvRoot?.optJSONObject("softsub")?.optJSONObject("1")?.optString("lid", "").orEmpty()
                .ifBlank { srvRoot?.optJSONObject("sub")?.optJSONObject("1")?.optString("lid", "").orEmpty() }
            if (lid.isBlank()) return null

            val encLid = fetchEncText("$API/enc-kai?text=${urlEnc(lid)}") ?: return null
            val viewResp = httpGetText("https://animekai.to/ajax/links/view?id=$lid&_=$encLid", "https://animekai.to/", kaiHeaders) ?: return null
            val encEmbed = JSONObject(viewResp).optString("result", "").trim()
            if (encEmbed.isBlank()) return null
            val decKai = httpPostJson("$API/dec-kai", JSONObject().put("text", encEmbed).toString()) ?: return null
            val embedUrl = validateResultObject(decKai)?.optString("url", "").orEmpty().trim()
            if (embedUrl.isBlank()) return null
            decryptMegaUpEmbedOrMedia(embedUrl)
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptMegaUpEmbedOrMedia(url: String): DirectStreamResult? {
        val referer = when {
            url.contains("/e/") -> url.substringBefore("/e/") + "/"
            url.contains("/media/") -> url.substringBefore("/media/") + "/"
            else -> "https://megaup.site/"
        }
        val mediaUrl = if (url.contains("/e/")) url.replace("/e/", "/media/") else url
        val mediaResp = httpGetText(mediaUrl, referer, mapOf("Accept" to "application/json", "Referer" to referer)) ?: return null
        val enc = JSONObject(mediaResp).optString("result", "").trim()
        if (enc.isBlank()) return null
        val decMega = httpPostJson(
            "$API/dec-mega",
            JSONObject().put("text", enc).put("agent", HttpClientProvider.USER_AGENT).toString()
        ) ?: return null
        return parseDecryptedMediaJson(decMega, "animekai", "AnimeKai (MegaUp)", referer, referer.trimEnd('/'))
    }

    // 10. MegaPlay (samples/megaplay.py) — MAL/AniList -> getSources -> dec-megaplay
    private fun resolveViaMegaPlay(item: MediaItem, episode: Int): DirectStreamResult? {
        return try {
            val headers = mapOf(
                "Origin" to "https://megaplay.buzz",
                "Referer" to "https://megaplay.buzz/",
                "X-Requested-With" to "XMLHttpRequest"
            )
            val streamPageUrl = when {
                item.malId != null && item.malId > 0 -> "https://megaplay.buzz/stream/mal/${item.malId}/$episode/sub"
                item.aniListId != null && item.aniListId > 0 -> "https://megaplay.buzz/stream/ani/${item.aniListId}/$episode/sub"
                else -> return null
            }
            val html = httpGetText(streamPageUrl, "https://megaplay.buzz/", headers) ?: return null
            val cid = Regex("""<title>File (\d+) - MegaPlay</title>""").find(html)?.groupValues?.getOrNull(1) ?: return null
            val srcResp = httpGetText("https://megaplay.buzz/stream/getSources?id=$cid&platform=OTHER", "https://megaplay.buzz/", headers) ?: return null
            val srcJson = JSONObject(srcResp)
            val enc = srcJson.optString("enc", "").trim()
            if (enc.isBlank()) return null
            val decResp = httpPostJson("$API/dec-megaplay", JSONObject().put("text", enc).toString()) ?: return null
            parseDecryptedMediaJson(decResp, "megaplay", "MegaPlay", "https://megaplay.buzz/", "https://megaplay.buzz")
        } catch (_: Exception) {
            null
        }
    }

    // 11. KissKH (samples/kisskh.py) — Asian Drama + decrypted subtitles via enc-kisskh / dec-kisskh
    private fun resolveViaKissKh(item: MediaItem, episode: Int): DirectStreamResult? {
        return try {
            val searchBody = httpGetText("https://kisskh.do/api/DramaList/Search?q=${urlEnc(item.title)}&type=0", "https://kisskh.do/") ?: return null
            val arr = JSONArray(searchBody)
            if (arr.length() == 0) return null
            val dramaId = arr.optJSONObject(0)?.optInt("id", 0) ?: 0
            if (dramaId <= 0) return null

            val detailBody = httpGetText("https://kisskh.do/api/DramaList/Drama/$dramaId?isq=false", "https://kisskh.do/") ?: return null
            val eps = JSONObject(detailBody).optJSONArray("episodes") ?: return null
            var epId = ""
            for (i in 0 until eps.length()) {
                val eo = eps.optJSONObject(i) ?: continue
                if (eo.optDouble("number", -1.0).toInt() == episode) {
                    epId = eo.optString("id", "")
                    break
                }
            }
            if (epId.isBlank()) return null

            val vidKey = validateResultString(httpGetText("$API/enc-kisskh?text=$epId&type=vid", ENC_DEC_BASE) ?: return null) ?: return null
            val vidBody = httpGetText("https://kisskh.do/api/DramaList/Episode/$epId.png?err=false&ts=&time=&kkey=${urlEnc(vidKey)}", "https://kisskh.do/") ?: return null
            val vidUrl = JSONObject(vidBody).optString("Video", "").trim()
            if (!vidUrl.startsWith("http")) return null

            var subUrl = ""
            runCatching {
                val subKey = validateResultString(httpGetText("$API/enc-kisskh?text=$epId&type=sub", ENC_DEC_BASE) ?: "") ?: ""
                if (subKey.isNotBlank()) {
                    val subBody = httpGetText("https://kisskh.do/api/Sub/$epId?kkey=${urlEnc(subKey)}", "https://kisskh.do/")
                    if (!subBody.isNullOrBlank()) {
                        val sArr = JSONArray(subBody)
                        var idSrc = ""
                        var enSrc = ""
                        for (i in 0 until sArr.length()) {
                            val so = sArr.optJSONObject(i) ?: continue
                            val l = so.optString("land", so.optString("label", "")).lowercase()
                            val src = so.optString("src", "").trim()
                            if (l.contains("indo") || l == "id") idSrc = src
                            else if (l.contains("eng") || l == "en") enSrc = src
                        }
                        val chosen = idSrc.ifBlank { enSrc }
                        if (chosen.startsWith("http")) {
                            subUrl = "$API/dec-kisskh?url=${urlEnc(chosen)}"
                        }
                    }
                }
            }

            DirectStreamResult(
                providerId = "kisskh",
                providerName = "KissKH",
                streamUrl = vidUrl,
                referer = "https://kisskh.do/",
                origin = "https://kisskh.do",
                subtitleVttUrl = subUrl,
                qualityLabel = "HD"
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAllDecryptedMediaJson(
        jsonStr: String,
        providerId: String,
        providerName: String,
        defaultReferer: String,
        defaultOrigin: String
    ): List<DirectStreamResult> {
        return try {
            val root = JSONObject(jsonStr)
            val data = root.optJSONObject("result")
                ?: root.optJSONObject("stream")
                ?: root.optJSONObject("data")
                ?: root

            data class Cand(val url: String, val score: Int, val quality: String, val tag: String)
            val candidates = mutableListOf<Cand>()
            var bestSubId = ""
            var bestSubEn = ""

            fun considerStream(url: String, label: String = "", srvTag: String = "") {
                val u = url.trim()
                if (!u.startsWith("http")) return
                if (strictDubRegex.containsMatchIn("$u $label")) return
                val isDirect = u.contains(".m3u8", true) || u.contains(".mp4", true) || u.contains("playlist", true) || u.contains("master", true) || u.contains("manifest", true)
                if (!isDirect && (u.contains("/e/", true) || u.contains("/embed/", true))) return
                val low = "$u $label".lowercase()
                var score = when {
                    low.contains("2160") || low.contains("4k") -> 70
                    low.contains("1080") -> 65
                    low.contains("720") -> 60
                    low.contains("480") -> 45
                    low.contains("360") -> 35
                    else -> 52
                }
                if (u.contains(".m3u8", true)) score += 25
                else if (u.contains(".mp4", true)) score += 18
                if (low.contains("master")) score += 8
                val qLabel = when {
                    low.contains("2160") || low.contains("4k") -> "4K"
                    low.contains("1440") -> "1440p"
                    low.contains("1080") -> "1080p"
                    low.contains("720") -> "720p"
                    low.contains("480") -> "480p"
                    low.contains("360") -> "360p"
                    else -> "HD"
                }
                candidates += Cand(u, score, qLabel, srvTag.trim())
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

            listOf("playlist", "url", "file", "stream", "hls", "src").forEach { k ->
                considerStream(data.optString(k, ""))
            }
            data.optJSONObject("stream")?.let { sObj ->
                listOf("playlist", "url", "file", "hls").forEach { k -> considerStream(sObj.optString(k, "")) }
            }

            fun scanSources(arr: JSONArray?) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val el = arr.opt(i)
                    when (el) {
                        is String -> considerStream(el)
                        is JSONObject -> {
                            val lbl = el.optString("label", el.optString("quality", el.optString("resolution", el.optString("name", ""))))
                            val srv = el.optString("server", el.optString("provider", el.optString("source", "")))
                            listOf("url", "file", "src", "link", "playlist").forEach { k -> considerStream(el.optString(k, ""), lbl, srv) }
                        }
                    }
                }
            }
            scanSources(data.optJSONArray("sources"))
            scanSources(data.optJSONArray("streams"))
            scanSources(data.optJSONArray("servers"))
            scanSources(data.optJSONArray("qualities"))
            scanSources(root.optJSONArray("sources"))

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
            scanSubs(root.optJSONArray("captions"))
            scanSubs(root.optJSONArray("subtitles"))

            val subChosen = bestSubId.ifBlank { bestSubEn }
            val unique = candidates
                .sortedByDescending { it.score }
                .distinctBy { it.url }

            unique.mapIndexed { idx, c ->
                val cleanTag = c.tag.takeIf { it.isNotBlank() && !it.equals(providerName, true) }
                val finalName = when {
                    cleanTag != null -> "$providerName ($cleanTag)"
                    unique.size > 1 && idx > 0 -> "$providerName #${idx + 1}"
                    else -> providerName
                }
                val finalId = if (idx == 0) providerId else "${providerId}_$idx"
                DirectStreamResult(
                    providerId = finalId,
                    providerName = finalName,
                    streamUrl = c.url,
                    referer = defaultReferer,
                    origin = defaultOrigin,
                    subtitleVttUrl = subChosen,
                    qualityLabel = c.quality
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseDecryptedMediaJson(
        jsonStr: String,
        providerId: String,
        providerName: String,
        defaultReferer: String,
        defaultOrigin: String
    ): DirectStreamResult? {
        return try {
            val root = JSONObject(jsonStr)
            val data = root.optJSONObject("result")
                ?: root.optJSONObject("stream")
                ?: root.optJSONObject("data")
                ?: root

            var bestStream = ""
            var bestScore = Int.MIN_VALUE
            var bestQuality = "HD"
            var bestSubId = ""
            var bestSubEn = ""

            fun considerStream(url: String, label: String = "") {
                val u = url.trim()
                if (!u.startsWith("http")) return
                if (strictDubRegex.containsMatchIn("$u $label")) return
                val isDirect = u.contains(".m3u8", true) || u.contains(".mp4", true) || u.contains("playlist", true) || u.contains("master", true) || u.contains("manifest", true)
                if (!isDirect && (u.contains("/e/", true) || u.contains("/embed/", true))) return
                val low = "$u $label".lowercase()
                var score = when {
                    low.contains("1080") -> 65
                    low.contains("720") -> 60
                    low.contains("2160") || low.contains("4k") -> 58
                    low.contains("480") -> 35
                    else -> 50
                }
                if (u.contains(".m3u8", true)) score += 25
                else if (u.contains(".mp4", true)) score += 20
                if (score > bestScore) {
                    bestScore = score
                    bestStream = u
                    bestQuality = when {
                        low.contains("2160") || low.contains("4k") -> "4K"
                        low.contains("1080") -> "1080p"
                        low.contains("720") -> "720p"
                        else -> "HD"
                    }
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

            listOf("playlist", "url", "file", "stream", "hls", "src").forEach { k ->
                considerStream(data.optString(k, ""))
            }
            data.optJSONObject("stream")?.let { sObj ->
                listOf("playlist", "url", "file", "hls").forEach { k -> considerStream(sObj.optString(k, "")) }
            }

            fun scanSources(arr: JSONArray?) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val el = arr.opt(i)
                    when (el) {
                        is String -> considerStream(el)
                        is JSONObject -> {
                            val lbl = el.optString("label", el.optString("quality", el.optString("name", "")))
                            listOf("url", "file", "src", "link", "playlist").forEach { k -> considerStream(el.optString(k, ""), lbl) }
                        }
                    }
                }
            }
            scanSources(data.optJSONArray("sources"))
            scanSources(data.optJSONArray("streams"))
            scanSources(data.optJSONArray("servers"))
            scanSources(root.optJSONArray("sources"))

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
            scanSubs(root.optJSONArray("captions"))

            if (bestStream.isBlank()) return null
            DirectStreamResult(
                providerId = providerId,
                providerName = providerName,
                streamUrl = bestStream,
                referer = defaultReferer,
                origin = defaultOrigin,
                subtitleVttUrl = bestSubId.ifBlank { bestSubEn },
                qualityLabel = bestQuality
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFirstEntryObject(jsonStr: String): JSONObject? {
        return try {
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed).optJSONObject(0)
            } else {
                val root = JSONObject(trimmed)
                root.optJSONArray("result")?.optJSONObject(0)
                    ?: root.optJSONObject("result")
                    ?: root.optJSONArray("results")?.optJSONObject(0)
                    ?: root
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun validateResultString(jsonStr: String): String? {
        return runCatching {
            val j = JSONObject(jsonStr)
            if (j.has("status") && j.optInt("status", 200) != 200) return null
            j.optString("result", "").trim().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun validateResultObject(jsonStr: String): JSONObject? {
        return runCatching {
            val j = JSONObject(jsonStr)
            if (j.has("status") && j.optInt("status", 200) != 200) return null
            j.optJSONObject("result")
        }.getOrNull()
    }

    private fun fetchEncText(url: String): String? =
        httpGetText(url, ENC_DEC_BASE)?.let { validateResultString(it) }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    private fun httpGetText(url: String, referer: String, extraHeaders: Map<String, String> = emptyMap()): String? {
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

    private fun httpPostJson(url: String, jsonBody: String, extraHeaders: Map<String, String> = emptyMap()): String? {
        return try {
            val b = Request.Builder()
                .url(url)
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .header("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(jsonMediaType))
            extraHeaders.forEach { (k, v) -> b.header(k, v) }
            HttpClientProvider.client.newCall(b.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun httpPostEmpty(url: String, extraHeaders: Map<String, String> = emptyMap()): String? {
        return try {
            val b = Request.Builder()
                .url(url)
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .post("".toRequestBody(emptyMediaType))
            extraHeaders.forEach { (k, v) -> b.header(k, v) }
            HttpClientProvider.client.newCall(b.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun httpPostBytes(url: String, bytes: ByteArray, extraHeaders: Map<String, String> = emptyMap()): ByteArray? {
        return try {
            val b = Request.Builder()
                .url(url)
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .post(bytes.toRequestBody(octetMediaType))
            extraHeaders.forEach { (k, v) -> b.header(k, v) }
            HttpClientProvider.client.newCall(b.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // --- v11.0.0 Instant Pre-Resolve Cache & First-Wins Progressive Engine ---
    private val streamCache = ConcurrentHashMap<String, Pair<Long, List<DirectStreamResult>>>()
    private const val STREAM_CACHE_TTL_MS = 12 * 60 * 1000L

    private fun cacheKey(item: MediaItem, season: Int, episode: Int): String =
        "${item.id}_${item.category.name}_${season}_${episode}"

    suspend fun preResolveStreams(item: MediaItem, season: Int = 1, episode: Int = 1) {
        val key = cacheKey(item, season, episode)
        val cached = streamCache[key]
        if (cached != null && System.currentTimeMillis() - cached.first < STREAM_CACHE_TTL_MS && cached.second.isNotEmpty()) return
        resolveProgressiveStreams(item, season, episode) {}
    }

    suspend fun resolveProgressiveStreams(
        item: MediaItem,
        season: Int,
        episode: Int,
        onFirstResult: (DirectStreamResult) -> Unit
    ): List<DirectStreamResult> = withContext(Dispatchers.IO) {
        val key = cacheKey(item, season, episode)
        val cached = streamCache[key]
        if (cached != null && System.currentTimeMillis() - cached.first < STREAM_CACHE_TTL_MS && cached.second.isNotEmpty()) {
            onFirstResult(cached.second.first())
            return@withContext cached.second
        }

        val firstEmitted = AtomicBoolean(false)
        val collected = java.util.Collections.synchronizedList(mutableListOf<DirectStreamResult>())

        fun offerResult(r: DirectStreamResult?) {
            if (r == null || r.streamUrl.isBlank()) return
            synchronized(collected) {
                if (collected.none { it.streamUrl == r.streamUrl }) {
                    collected.add(r)
                    if (firstEmitted.compareAndSet(false, true)) {
                        onFirstResult(r)
                    }
                }
            }
        }

        if (item.category == MediaCategory.ANIME) {
            coroutineScope {
                val jobs = listOf(
                    async { offerResult(withTimeoutOrNull(8500L) { resolveViaAnimeKaiMegaUp(item, episode) }) },
                    async { offerResult(withTimeoutOrNull(7500L) { resolveViaMegaPlay(item, episode) }) },
                    async { offerResult(withTimeoutOrNull(7500L) { resolveViaNexaAnime(item, episode) }) }
                )
                jobs.awaitAll()
            }
        } else {
            val mapping = lookupFlixMapping(item)
            val tmdbId = mapping?.tmdbId.orEmpty()
            val isMovie = item.category == MediaCategory.MOVIE
            val isAsianDrama = item.category == MediaCategory.ASIAN_DRAMA

            if (tmdbId.isNotBlank()) tmdbCache["${item.id}_${item.category.name}"] = tmdbId
            val multiTasks = mutableListOf<suspend () -> List<DirectStreamResult>>()
            if (isAsianDrama) {
                multiTasks += { listOfNotNull(withTimeoutOrNull(7000L) { resolveViaKissKh(item, episode) }) }
                multiTasks += { listOfNotNull(withTimeoutOrNull(6500L) { resolveViaOneTouchTv(item, episode) }) }
            }
            if (tmdbId.isNotBlank()) {
                // 3 Penyedia Prioritas Utama: Hexa, Nexa, MeowTV
                multiTasks += { withTimeoutOrNull(9000L) { resolveAllViaHexa(tmdbId, isMovie, season, episode) }.orEmpty() }
                multiTasks += { withTimeoutOrNull(9000L) { resolveAllViaNexa(tmdbId, isMovie, season, episode) }.orEmpty() }
                multiTasks += { withTimeoutOrNull(9000L) { resolveAllViaMeowTv(tmdbId, isMovie, season, episode) }.orEmpty() }
                // Penyedia Cadangan EncDecEndpoints
                multiTasks += { listOfNotNull(withTimeoutOrNull(6000L) { resolveViaVidLink(tmdbId, isMovie, season, episode) }) }
                multiTasks += { listOfNotNull(withTimeoutOrNull(8000L) { resolveViaCinejoy(item, tmdbId, isMovie, season, episode) }) }
                multiTasks += { listOfNotNull(withTimeoutOrNull(7500L) { resolveViaCineSrc(tmdbId, isMovie, season, episode) }) }
                multiTasks += { listOfNotNull(withTimeoutOrNull(8000L) { resolveViaVideasy(item, tmdbId, isMovie, season, episode) }) }
                multiTasks += { listOfNotNull(withTimeoutOrNull(8500L) { resolveViaVidStageProvider("vidfast", "VidFast", "https://vidfast.vc", tmdbId, isMovie, season, episode) }) }
                multiTasks += { listOfNotNull(withTimeoutOrNull(8500L) { resolveViaVidStageProvider("vidcore", "VidCore", "https://vidcore.io", tmdbId, isMovie, season, episode) }) }
                multiTasks += { listOfNotNull(withTimeoutOrNull(8500L) { resolveViaVidStageProvider("vidup", "VidUp", "https://vidup.to", tmdbId, isMovie, season, episode) }) }
                multiTasks += { listOfNotNull(withTimeoutOrNull(7500L) { resolveViaByse(item.imdbId, tmdbId, isMovie, season, episode) }) }
            }
            if (mapping != null && (mapping.episodesJson != null || mapping.flixId.isNotBlank())) {
                multiTasks += { listOfNotNull(withTimeoutOrNull(8500L) { resolveViaYFlixRapid(mapping, season, episode) }) }
            }

            coroutineScope {
                multiTasks.map { fn ->
                    async {
                        runCatching { fn() }.getOrDefault(emptyList()).forEach { offerResult(it) }
                    }
                }.awaitAll()
            }
        }

        val finalList = synchronized(collected) { collected.toList() }
        if (finalList.isNotEmpty()) {
            streamCache[key] = System.currentTimeMillis() to finalList
        }
        finalList
    }

    // OneTouchTV (samples/onetouchtv.py) — Asian Drama via dec-onetouchtv
    private fun resolveViaOneTouchTv(item: MediaItem, episode: Int): DirectStreamResult? {
        val cleanTitle = item.title.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-')
        if (cleanTitle.isBlank()) return null
        val slug = if (item.year.length == 4) "$cleanTitle-${item.year}" else cleanTitle
        val headers = mapOf("Origin" to "https://onetouchtv.xyz", "Referer" to "https://onetouchtv.xyz/")
        val encrypted = httpGetText("https://api3.devcorp.me/web/vod/$slug/episode/$episode", "https://onetouchtv.xyz/", headers) ?: return null
        if (encrypted.length < 20) return null
        val payload = JSONObject().put("text", encrypted.trim()).toString()
        val decResp = httpPostJson("$API/dec-onetouchtv", payload) ?: return null
        return parseDecryptedMediaJson(decResp, "onetouchtv", "OneTouchTV", "https://onetouchtv.xyz/", "https://onetouchtv.xyz")
    }

    // Byse / 1hd.one (samples/byse.py) — via dec-byse
    private fun resolveViaByse(imdbId: String, tmdbId: String, isMovie: Boolean, season: Int, episode: Int): DirectStreamResult? {
        val entryUrl = if (isMovie) {
            "https://0123movie.space/mv/${imdbId.ifBlank { "tt0000000" }}/$tmdbId/"
        } else {
            "https://0123movie.space/pl/$tmdbId/$season/$episode"
        }
        val req = Request.Builder()
            .url(entryUrl)
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .header("Referer", "https://1hd.one/")
            .build()
        val loc = runCatching {
            HttpClientProvider.client.newBuilder().followRedirects(false).build().newCall(req).execute().use { resp ->
                resp.header("Location")
            }
        }.getOrNull() ?: return null
        val m = Regex("""^(https?://[^/]+)/[A-Za-z]/([^/?#]+)""").find(loc) ?: return null
        val embedOrigin = m.groupValues[1]
        val code = m.groupValues[2]
        val bHeaders = mapOf("Referer" to loc, "Origin" to embedOrigin)
        val pbText = httpGetText("$embedOrigin/api/videos/$code/embed/playback", loc, bHeaders) ?: return null
        val pbObj = runCatching { JSONObject(pbText) }.getOrNull() ?: return null
        val playback = pbObj.optString("playback")
        if (playback.isBlank()) return null
        val decResp = httpPostJson("$API/dec-byse", JSONObject().put("text", playback).toString()) ?: return null
        return parseDecryptedMediaJson(decResp, "byse", "Byse HD", loc, embedOrigin)
    }
}
