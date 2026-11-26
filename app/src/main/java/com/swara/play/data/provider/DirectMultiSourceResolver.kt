package com.swara.play.data.provider

import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONObject

/**
 * Parallel Direct HTTP Multi-Provider Resolver (NuvioStreams + WebStreamr + Stremify):
 * - Automatically rejects dubbed streams (strict original audio rule)
 * - Ranks 720p (primary standard) -> 1080p -> 480p (graceful fallback)
 * - Plays natively in DirectExoPlayerView so Indonesian subtitles NEVER get blocked by iframe embeds!
 */
object DirectMultiSourceResolver {
    private val dubRegex = Regex(
        """\b(dub|dubbed|eng[\s._-]*dub|english[\s._-]*dub|hindi[\s._-]*dub|tamil[\s._-]*dub|telugu[\s._-]*dub|vf|vff|truefrench)\b""",
        RegexOption.IGNORE_CASE
    )
    private val origRegex = Regex("""\b(sub|subbed|vostfr|orig|original|raw)\b""", RegexOption.IGNORE_CASE)

    private data class Candidate(val url: String, val referer: String, val score: Int)

    suspend fun resolveDirectStream(item: MediaItem, season: Int, episode: Int): DirectStreamResult? = withContext(Dispatchers.IO) {
        val imdb = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else "" }
        if (!imdb.startsWith("tt")) return@withContext null

        val type = if (item.category == MediaCategory.MOVIE) "movie" else "series"
        val streamId = if (type == "movie") imdb else "$imdb:$season:$episode"
        val endpoints = listOf(
            "https://nuviostreams.hayd.uk/stream/$type/$streamId.json" to "https://nuviostreams.hayd.uk/",
            "https://webstreamr.hayd.uk/stream/$type/$streamId.json" to "https://webstreamr.hayd.uk/",
            "https://stremify.hayd.uk/stream/$type/$streamId.json" to "https://stremify.hayd.uk/"
        )

        val allCandidates = withTimeoutOrNull(7500L) {
            coroutineScope {
                endpoints.map { (url, ref) ->
                    async(Dispatchers.IO) { fetchEndpointCandidates(url, ref) }
                }.awaitAll().flatten()
            }
        }.orEmpty()

        val best = allCandidates.maxByOrNull { it.score } ?: return@withContext null
        DirectStreamResult(streamUrl = best.url, referer = best.referer, subtitleVttUrl = "", isWebEmbed = false)
    }

    private fun fetchEndpointCandidates(endpointUrl: String, defaultRef: String): List<Candidate> {
        val out = mutableListOf<Candidate>()
        try {
            val req = Request.Builder()
                .url(endpointUrl)
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val body = HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return out
                resp.body?.string() ?: return out
            }
            val arr = JSONObject(body).optJSONArray("streams") ?: return out
            for (i in 0 until minOf(arr.length(), 10)) {
                val o = arr.optJSONObject(i) ?: continue
                val streamUrl = o.optString("url", "").trim()
                if (!streamUrl.startsWith("http")) continue
                val metaText = listOf(
                    o.optString("name", ""),
                    o.optString("title", ""),
                    o.optString("description", "")
                ).joinToString(" ")
                // Strict anti-dubbing filter
                if (dubRegex.containsMatchIn(metaText) && !origRegex.containsMatchIn(metaText)) continue

                val low = metaText.lowercase()
                var score = when {
                    low.contains("720") -> 60
                    low.contains("1080") -> 50
                    low.contains("480") -> 30
                    low.contains("2160") || low.contains("4k") -> 40
                    else -> 45
                }
                if (streamUrl.contains(".m3u8", true) || streamUrl.contains(".mp4", true)) score += 15
                if (low.contains("indo") || low.contains("sub indo")) score += 40
                val ref = o.optJSONObject("behaviorHints")
                    ?.optJSONObject("proxyHeaders")
                    ?.optJSONObject("request")
                    ?.optString("Referer", "")
                    .orEmpty()
                    .ifBlank { defaultRef }
                out.add(Candidate(streamUrl, ref, score))
            }
        } catch (_: Exception) { }
        return out
    }
}
