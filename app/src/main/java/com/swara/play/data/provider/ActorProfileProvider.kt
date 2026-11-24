package com.swara.play.data.provider

import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.ActorProfile
import com.swara.play.model.CastMember
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object ActorProfileProvider {
    private const val WIKI_UA = "SwaraPlay/6.3 (https://github.com/vipercellid-arch/SwaraPlay)"

    suspend fun resolveCastMember(name: String): CastMember = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isBlank()) return@withContext CastMember(name = name)

        // Priority 1: IMDb Suggestion API (always returns real HD portrait photos for actors/actresses)
        val imdbPair = fetchImdbPersonPhoto(clean)
        if (imdbPair.first.isNotBlank()) {
            return@withContext CastMember(name = clean, photoUrl = imdbPair.first, imdbPersonId = imdbPair.second)
        }

        // Priority 2: Wikipedia REST Summary with proper Wikimedia User-Agent + actor suffix fallback
        val wikiPhoto = fetchWikiSummary(clean)?.second.orEmpty()
        CastMember(name = clean, photoUrl = wikiPhoto)
    }

    suspend fun loadFullActorProfile(actor: CastMember, targetLangIso: String = "id"): ActorProfile = withContext(Dispatchers.IO) {
        val clean = actor.name.trim()
        var photo = actor.photoUrl
        var subtitle = ""
        var rawBio = ""

        if (photo.isBlank()) {
            photo = fetchImdbPersonPhoto(clean).first
        }

        val wiki = fetchWikiSummary(clean)
        if (wiki != null) {
            subtitle = wiki.first
            if (photo.isBlank()) photo = wiki.second
            rawBio = wiki.third
        }

        val translatedBio = if (rawBio.isNotBlank()) {
            SynopsisTranslator.translateToLanguage(rawBio, targetLangIso)
        } else {
            "Aktor / aktris yang berperan dalam film dan serial pilihan di SwaraPlay."
        }
        val translatedSub = if (subtitle.isNotBlank()) {
            SynopsisTranslator.translateToLanguage(subtitle, targetLangIso)
        } else "Aktor & Aktris"

        val filmography = fetchActorFilmography(clean)

        ActorProfile(
            name = clean,
            photoUrl = photo,
            subtitle = translatedSub,
            biography = translatedBio,
            filmography = filmography
        )
    }

    private fun fetchImdbPersonPhoto(name: String): Pair<String, String> {
        return try {
            val lower = name.lowercase().trim()
            val first = lower.firstOrNull()?.takeIf { it.isLetterOrDigit() } ?: return "" to ""
            val encoded = URLEncoder.encode(lower, "UTF-8")
            val body = httpGet("https://v3.sg.media-imdb.com/suggestion/$first/$encoded.json", HttpClientProvider.USER_AGENT)
                ?: return "" to ""
            val arr = JSONObject(body).optJSONArray("d") ?: return "" to ""
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id", "")
                if (id.startsWith("nm")) {
                    val img = o.optJSONObject("i")?.optString("imageUrl", "").orEmpty()
                    if (img.startsWith("http")) {
                        // Request optimized portrait size from Amazon/IMDb CDN
                        val sized = img.replace("._V1_.", "._V1_UX360_.")
                        return sized to id
                    }
                }
            }
            "" to ""
        } catch (_: Exception) {
            "" to ""
        }
    }

    private fun fetchWikiSummary(name: String): Triple<String, String, String>? {
        val candidates = listOf(
            name.replace(" ", "_"),
            "${name.replace(" ", "_")}_(actor)",
            "${name.replace(" ", "_")}_(actress)"
        )
        for (cand in candidates) {
            try {
                val slug = URLEncoder.encode(cand, "UTF-8")
                val body = httpGet("https://en.wikipedia.org/api/rest_v1/page/summary/$slug", WIKI_UA) ?: continue
                val root = JSONObject(body)
                val type = root.optString("type", "")
                if (type == "disambiguation") continue
                val desc = root.optString("description", "")
                val thumb = root.optJSONObject("thumbnail")?.optString("source", "").orEmpty()
                val extract = root.optString("extract", "")
                if (extract.isNotBlank() || thumb.isNotBlank()) {
                    return Triple(desc, thumb, extract)
                }
            } catch (_: Exception) { }
        }
        return null
    }

    private fun fetchActorFilmography(actorName: String): List<MediaItem> {
        val out = LinkedHashMap<String, MediaItem>()
        try {
            val encoded = URLEncoder.encode(actorName, "UTF-8")
            for ((type, cat) in listOf("movie" to MediaCategory.MOVIE, "series" to MediaCategory.SERIES)) {
                val body = httpGet("https://v3-cinemeta.strem.io/catalog/$type/top/search=$encoded.json", HttpClientProvider.USER_AGENT)
                    ?: continue
                val metas = JSONObject(body).optJSONArray("metas") ?: continue
                for (i in 0 until minOf(metas.length(), 14)) {
                    val o = metas.optJSONObject(i) ?: continue
                    val id = o.optString("id", "")
                    val title = o.optString("name", "")
                    val poster = o.optString("poster", "").trim()
                    if (id.isBlank() || title.isBlank() || !poster.startsWith("http")) continue
                    if (!out.containsKey(id)) {
                        out[id] = MediaItem(
                            id = id,
                            title = title,
                            category = cat,
                            posterUrl = poster,
                            backdropUrl = o.optString("background", ""),
                            overview = o.optString("description", ""),
                            year = o.optString("releaseInfo", o.optString("year", "")),
                            rating = o.optString("imdbRating", ""),
                            imdbId = if (id.startsWith("tt")) id else ""
                        )
                    }
                }
            }
        } catch (_: Exception) { }
        return out.values.take(18)
    }

    private fun httpGet(url: String, ua: String): String? {
        return try {
            val req = Request.Builder().url(url).header("User-Agent", ua).build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) { null }
    }
}
