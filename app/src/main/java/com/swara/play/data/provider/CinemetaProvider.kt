package com.swara.play.data.provider

import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.CastMember
import com.swara.play.model.EpisodeItem
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaDetail
import com.swara.play.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Calendar

class CinemetaProvider {
    private val baseUrl = "https://v3-cinemeta.strem.io"
    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    private val junkKeywords = listOf(
        "blind wave", "reaction", "all your history", "philippines", "parody",
        "recap", "explained", "review", "making of", "behind the scenes", "soundtrack"
    )

    suspend fun getTopMovies(page: Int = 0): List<MediaItem> =
        fetchCatalogPaged("movie", "top", MediaCategory.MOVIE, page, forceFresh = (page == 0))

    suspend fun getTopSeries(page: Int = 0): List<MediaItem> =
        fetchCatalogPaged("series", "top", MediaCategory.SERIES, page, forceFresh = (page == 0))

    suspend fun getRealtimeTrendingMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        val raw = fetchCatalogUrl("$baseUrl/catalog/movie/top.json", MediaCategory.MOVIE, forceFresh = true)
        val recent = raw.filter { item ->
            val y = Regex("\\d{4}").find(item.year)?.value?.toIntOrNull() ?: 0
            y >= currentYear - 2
        }
        (recent + raw).distinctBy { it.id }.take(24)
    }

    suspend fun getRealtimeTrendingSeries(): List<MediaItem> = withContext(Dispatchers.IO) {
        val raw = fetchCatalogUrl("$baseUrl/catalog/series/top.json", MediaCategory.SERIES, forceFresh = true)
        val recent = raw.filter { item ->
            val y = Regex("\\d{4}").find(item.year)?.value?.toIntOrNull() ?: 0
            y >= currentYear - 3
        }
        (recent + raw).distinctBy { it.id }.take(24)
    }

    suspend fun getByGenre(type: String, genre: String, category: MediaCategory, page: Int = 0): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(genre, "UTF-8")
            val skip = page * 20
            val url = if (skip > 0) "$baseUrl/catalog/$type/top/genre=$encoded&skip=$skip.json"
            else "$baseUrl/catalog/$type/top/genre=$encoded.json"
            fetchCatalogUrl(url, category, forceFresh = (page == 0))
        }

    suspend fun getAsianDramas(page: Int = 0): List<MediaItem> = withContext(Dispatchers.IO) {
        val allQueries = listOf(
            "squid game", "all of us are dead", "the glory", "alice in borderland",
            "crash landing on you", "sweet home", "kingdom", "moving", "goblin", "vincenzo",
            "queen of tears", "business proposal", "alchemy of souls", "itaewon class",
            "descendants of the sun", "hotel del luna", "extraordinary attorney woo",
            "weak hero class", "my demon", "marry my husband", "lovely runner", "the trauma code",
            "when life gives you tangerines", "study group", "a shop for killers", "parasyte the grey",
            "gyeongseong creature", "bloodhounds", "my name", "happiness"
        )
        val startIdx = page * 8
        if (startIdx >= allQueries.size) return@withContext emptyList()
        val batch = allQueries.subList(startIdx, minOf(startIdx + 8, allQueries.size))
        val collected = LinkedHashMap<String, MediaItem>()
        coroutineScope {
            val results = batch.map { q ->
                async {
                    val encoded = URLEncoder.encode(q, "UTF-8")
                    fetchCatalogUrl("$baseUrl/catalog/series/top/search=$encoded.json", MediaCategory.ASIAN_DRAMA, forceFresh = false)
                        .filter { isCleanAsianDrama(it) }
                        .take(2)
                }
            }.awaitAll().flatten()
            for (item in results) {
                if (!collected.containsKey(item.id)) collected[item.id] = item
            }
        }
        collected.values.toList()
    }

    private fun isCleanAsianDrama(item: MediaItem): Boolean {
        if (!isValidPosterUrl(item.posterUrl)) return false
        val lower = item.title.lowercase()
        if (junkKeywords.any { lower.contains(it) }) return false
        if (item.rating.isBlank() && item.overview.isBlank()) return false
        return true
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val movies = fetchCatalogUrl("$baseUrl/catalog/movie/top/search=$encoded.json", MediaCategory.MOVIE)
        val series = fetchCatalogUrl("$baseUrl/catalog/series/top/search=$encoded.json", MediaCategory.SERIES)
        val combined = (movies.take(20) + series.take(20))
        if (combined.isNotEmpty()) return@withContext combined
        searchImdbFallback(clean)
    }

    suspend fun lookupImdbIdForTitle(title: String): String = withContext(Dispatchers.IO) {
        val clean = title.trim()
        if (clean.isBlank()) return@withContext ""
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val series = fetchCatalogUrl("$baseUrl/catalog/series/top/search=$encoded.json", MediaCategory.SERIES)
        series.firstOrNull { it.imdbId.startsWith("tt") }?.imdbId.orEmpty()
    }

    private fun searchImdbFallback(query: String): List<MediaItem> {
        val lower = query.lowercase().trim()
        val first = lower.firstOrNull()?.takeIf { it.isLetterOrDigit() } ?: return emptyList()
        val encoded = URLEncoder.encode(lower, "UTF-8")
        val body = executeGet("https://v3.sg.media-imdb.com/suggestion/$first/$encoded.json") ?: return emptyList()
        return try {
            val arr = JSONObject(body).optJSONArray("d") ?: return emptyList()
            val out = mutableListOf<MediaItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id", "")
                if (!id.startsWith("tt")) continue
                val title = o.optString("l", "")
                val qid = o.optString("qid", "")
                val cat = if (qid.contains("tv", true) || qid.contains("series", true)) MediaCategory.SERIES else MediaCategory.MOVIE
                val poster = o.optJSONObject("i")?.optString("imageUrl", "").orEmpty()
                if (!isValidPosterUrl(poster)) continue
                val y = o.optInt("y", 0)
                if (y > currentYear) continue
                if (title.isNotBlank()) {
                    out.add(MediaItem(id = id, title = title, category = cat, posterUrl = poster, year = if (y > 0) y.toString() else "", imdbId = id))
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getDetail(item: MediaItem, targetLangIso: String = "id"): MediaDetail = withContext(Dispatchers.IO) {
        val type = if (item.category == MediaCategory.MOVIE) "movie" else "series"
        val imdb = item.imdbId.ifBlank { item.id }
        val jsonText = executeGet("$baseUrl/meta/$type/$imdb.json")
        var updatedItem = item
        var castNames = emptyList<String>()
        var runtime = ""
        var trailerYtId = item.trailerYoutubeId
        val cinemetaEpisodes = mutableListOf<EpisodeItem>()

        if (jsonText != null) {
            try {
                val meta = JSONObject(jsonText).optJSONObject("meta")
                if (meta != null) {
                    val genres = meta.optJSONArray("genres").toStringList()
                    castNames = meta.optJSONArray("cast").toStringList().take(8)
                    runtime = meta.optString("runtime", "")
                    val trailers = meta.optJSONArray("trailers")
                    if (trailers != null && trailers.length() > 0) {
                        val src = trailers.optJSONObject(0)?.optString("source", "").orEmpty()
                        if (src.isNotBlank()) trailerYtId = src
                    }
                    updatedItem = item.copy(
                        title = meta.optString("name", item.title).ifBlank { item.title },
                        posterUrl = meta.optString("poster", item.posterUrl).ifBlank { item.posterUrl },
                        backdropUrl = meta.optString("background", item.backdropUrl).ifBlank { item.backdropUrl },
                        overview = meta.optString("description", item.overview).ifBlank { item.overview },
                        year = meta.optString("releaseInfo", item.year).ifBlank { item.year },
                        rating = meta.optString("imdbRating", item.rating).ifBlank { item.rating },
                        genres = if (genres.isNotEmpty()) genres else item.genres,
                        trailerYoutubeId = trailerYtId
                    )
                    val videos = meta.optJSONArray("videos")
                    if (videos != null) {
                        for (i in 0 until videos.length()) {
                            val v = videos.optJSONObject(i) ?: continue
                            val season = v.optInt("season", 1)
                            val epNum = v.optInt("episode", v.optInt("number", i + 1))
                            if (season <= 0 || epNum <= 0) continue
                            cinemetaEpisodes.add(
                                EpisodeItem(
                                    id = v.optString("id", "$imdb:$season:$epNum"),
                                    season = season,
                                    episode = epNum,
                                    title = v.optString("name", v.optString("title", "Episode $epNum")).ifBlank { "Episode $epNum" },
                                    overview = v.optString("overview", ""),
                                    thumbnailUrl = v.optString("thumbnail", ""),
                                    released = v.optString("released", "").take(10)
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        if (trailerYtId.isBlank()) {
            trailerYtId = WyzieSubtitleResolver.resolveYoutubeTrailerId(updatedItem.title, updatedItem.year)
        }

        val translatedOverview = SynopsisTranslator.translateToLanguage(updatedItem.overview, targetLangIso)
        updatedItem = updatedItem.copy(overview = translatedOverview, trailerYoutubeId = trailerYtId)

        val castMembers = coroutineScope {
            castNames.map { actorName ->
                async { CastMember(name = actorName, photoUrl = fetchWikiActorPhoto(actorName)) }
            }.awaitAll()
        }

        val finalEpisodes = if (item.category != MediaCategory.MOVIE && imdb.startsWith("tt")) {
            val tvmazeEps = fetchTvMazeEpisodes(imdb)
            if (tvmazeEps.size > cinemetaEpisodes.size) tvmazeEps else cinemetaEpisodes
        } else {
            cinemetaEpisodes
        }

        val sortedEps = finalEpisodes
            .distinctBy { "${it.season}:${it.episode}" }
            .sortedWith(compareBy<EpisodeItem> { it.season }.thenBy { it.episode })

        val primaryGenre = updatedItem.genres.firstOrNull() ?: "Action"
        val similar = getByGenre(type, primaryGenre, updatedItem.category)
            .filter { it.id != updatedItem.id && isValidPosterUrl(it.posterUrl) }
            .take(14)

        MediaDetail(
            item = updatedItem.copy(totalEpisodes = sortedEps.size),
            cast = castMembers,
            runtime = runtime,
            trailerYoutubeId = trailerYtId,
            episodes = sortedEps,
            similar = similar
        )
    }

    private fun fetchWikiActorPhoto(name: String): String {
        return try {
            val slug = URLEncoder.encode(name.trim().replace(" ", "_"), "UTF-8")
            val body = executeGet("https://en.wikipedia.org/api/rest_v1/page/summary/$slug") ?: return ""
            JSONObject(body).optJSONObject("thumbnail")?.optString("source", "").orEmpty()
        } catch (_: Exception) { "" }
    }

    private fun fetchTvMazeEpisodes(imdbId: String): List<EpisodeItem> {
        return try {
            val lookup = executeGet("https://api.tvmaze.com/lookup/shows?imdb=$imdbId") ?: return emptyList()
            val showId = JSONObject(lookup).optInt("id", 0)
            if (showId <= 0) return emptyList()
            val epsJson = executeGet("https://api.tvmaze.com/shows/$showId/episodes") ?: return emptyList()
            val arr = JSONArray(epsJson)
            val out = mutableListOf<EpisodeItem>()
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val season = e.optInt("season", 1)
                val number = e.optInt("number", 0)
                if (season <= 0 || number <= 0) continue
                val summary = e.optString("summary", "").replace(Regex("<[^>]*>"), "").trim()
                val img = e.optJSONObject("image")?.optString("medium", "").orEmpty()
                out.add(
                    EpisodeItem(
                        id = "$imdbId:$season:$number",
                        season = season,
                        episode = number,
                        title = e.optString("name", "Episode $number").ifBlank { "Episode $number" },
                        overview = summary,
                        thumbnailUrl = img,
                        released = e.optString("airdate", "")
                    )
                )
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun fetchCatalogPaged(
        type: String,
        catalogId: String,
        category: MediaCategory,
        page: Int,
        forceFresh: Boolean
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val skip = page * 20
        val url = if (skip > 0) "$baseUrl/catalog/$type/$catalogId/skip=$skip.json"
        else "$baseUrl/catalog/$type/$catalogId.json"
        fetchCatalogUrl(url, category, forceFresh = forceFresh)
    }

    private fun isValidPosterUrl(url: String): Boolean {
        val u = url.trim()
        return u.startsWith("http://") || u.startsWith("https://")
    }

    private fun fetchCatalogUrl(url: String, category: MediaCategory, forceFresh: Boolean = false): List<MediaItem> {
        val body = executeGet(url, forceFresh) ?: return emptyList()
        return try {
            val metas = JSONObject(body).optJSONArray("metas") ?: return emptyList()
            val list = mutableListOf<MediaItem>()
            for (i in 0 until metas.length()) {
                val obj = metas.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                val name = obj.optString("name", "")
                if (id.isBlank() || name.isBlank()) continue
                val poster = obj.optString("poster", "").trim()
                val backdrop = obj.optString("background", "").trim()
                val effectivePoster = if (isValidPosterUrl(poster)) poster else if (isValidPosterUrl(backdrop)) backdrop else ""
                if (effectivePoster.isBlank()) continue
                val lowerName = name.lowercase()
                if (junkKeywords.any { lowerName.contains(it) }) continue

                val yearStr = obj.optString("releaseInfo", obj.optString("year", ""))
                val firstYear = Regex("\\d{4}").find(yearStr)?.value?.toIntOrNull()
                if (category == MediaCategory.MOVIE && firstYear != null && firstYear > currentYear) continue
                val rating = obj.optString("imdbRating", "")
                if (category == MediaCategory.MOVIE && rating.isBlank() && firstYear == currentYear) continue
                list.add(
                    MediaItem(
                        id = id,
                        title = name,
                        category = category,
                        posterUrl = effectivePoster,
                        backdropUrl = backdrop,
                        overview = obj.optString("description", ""),
                        year = yearStr,
                        rating = rating,
                        genres = obj.optJSONArray("genres").toStringList(),
                        imdbId = if (id.startsWith("tt")) id else ""
                    )
                )
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    private fun executeGet(url: String, forceFresh: Boolean = false): String? {
        return try {
            val builder = Request.Builder().url(url).header("User-Agent", HttpClientProvider.USER_AGENT)
            if (forceFresh) builder.cacheControl(CacheControl.FORCE_NETWORK)
            HttpClientProvider.client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) { null }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until length()) {
            val v = optString(i, "")
            if (v.isNotBlank()) out.add(v)
        }
        return out
    }
}
