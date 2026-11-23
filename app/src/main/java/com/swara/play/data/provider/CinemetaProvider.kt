package com.swara.play.data.provider

import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.EpisodeItem
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaDetail
import com.swara.play.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class CinemetaProvider {
    private val baseUrl = "https://v3-cinemeta.strem.io"

    suspend fun getTopMovies(): List<MediaItem> = fetchCatalog("movie", "top", MediaCategory.MOVIE)

    suspend fun getTopSeries(): List<MediaItem> = fetchCatalog("series", "top", MediaCategory.SERIES)

    suspend fun getByGenre(type: String, genre: String, category: MediaCategory): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(genre, "UTF-8")
            fetchCatalogUrl("$baseUrl/catalog/$type/top/genre=$encoded.json", category)
        }

    suspend fun getAsianDramas(): List<MediaItem> = withContext(Dispatchers.IO) {
        val queries = listOf("squid game", "all of us are dead", "the glory", "alice in borderland", "crash landing", "sweet home", "kingdom")
        val collected = LinkedHashMap<String, MediaItem>()
        for (q in queries) {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val items = fetchCatalogUrl("$baseUrl/catalog/series/top/search=$encoded.json", MediaCategory.ASIAN_DRAMA)
            for (item in items.take(5)) {
                if (!collected.containsKey(item.id)) {
                    collected[item.id] = item
                }
            }
            if (collected.size >= 24) break
        }
        collected.values.toList()
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val movies = fetchCatalogUrl("$baseUrl/catalog/movie/top/search=$encoded.json", MediaCategory.MOVIE)
        val series = fetchCatalogUrl("$baseUrl/catalog/series/top/search=$encoded.json", MediaCategory.SERIES)
        val combined = (movies.take(15) + series.take(15))
        if (combined.isNotEmpty()) return@withContext combined
        searchImdbFallback(clean)
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
                val year = o.optInt("y", 0).let { if (it > 0) it.toString() else "" }
                if (title.isNotBlank()) {
                    out.add(
                        MediaItem(
                            id = id,
                            title = title,
                            category = cat,
                            posterUrl = poster,
                            year = year,
                            imdbId = id
                        )
                    )
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getDetail(item: MediaItem): MediaDetail = withContext(Dispatchers.IO) {
        val type = if (item.category == MediaCategory.MOVIE) "movie" else "series"
        val imdb = item.imdbId.ifBlank { item.id }
        val url = "$baseUrl/meta/$type/$imdb.json"
        val jsonText = executeGet(url) ?: return@withContext MediaDetail(item = item)
        try {
            val root = JSONObject(jsonText)
            val meta = root.optJSONObject("meta") ?: return@withContext MediaDetail(item = item)
            val genres = meta.optJSONArray("genres").toStringList()
            val cast = meta.optJSONArray("cast").toStringList().take(8)
            val runtime = meta.optString("runtime", "")
            val updatedItem = item.copy(
                title = meta.optString("name", item.title).ifBlank { item.title },
                posterUrl = meta.optString("poster", item.posterUrl).ifBlank { item.posterUrl },
                backdropUrl = meta.optString("background", item.backdropUrl).ifBlank { item.backdropUrl },
                overview = meta.optString("description", item.overview).ifBlank { item.overview },
                year = meta.optString("releaseInfo", item.year).ifBlank { item.year },
                rating = meta.optString("imdbRating", item.rating).ifBlank { item.rating },
                genres = if (genres.isNotEmpty()) genres else item.genres
            )
            val episodes = mutableListOf<EpisodeItem>()
            val videos = meta.optJSONArray("videos")
            if (videos != null) {
                for (i in 0 until videos.length()) {
                    val v = videos.optJSONObject(i) ?: continue
                    val season = v.optInt("season", 1)
                    val epNum = v.optInt("episode", v.optInt("number", i + 1))
                    if (season <= 0) continue
                    episodes.add(
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
            MediaDetail(
                item = updatedItem,
                cast = cast,
                runtime = runtime,
                episodes = episodes.sortedWith(compareBy<EpisodeItem> { it.season }.thenBy { it.episode })
            )
        } catch (_: Exception) {
            MediaDetail(item = item)
        }
    }

    private suspend fun fetchCatalog(type: String, catalogId: String, category: MediaCategory): List<MediaItem> =
        withContext(Dispatchers.IO) {
            fetchCatalogUrl("$baseUrl/catalog/$type/$catalogId.json", category)
        }

    private fun fetchCatalogUrl(url: String, category: MediaCategory): List<MediaItem> {
        val body = executeGet(url) ?: return emptyList()
        return try {
            val root = JSONObject(body)
            val metas = root.optJSONArray("metas") ?: return emptyList()
            val list = mutableListOf<MediaItem>()
            for (i in 0 until metas.length()) {
                val obj = metas.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                val name = obj.optString("name", "")
                if (id.isBlank() || name.isBlank()) continue
                list.add(
                    MediaItem(
                        id = id,
                        title = name,
                        category = category,
                        posterUrl = obj.optString("poster", ""),
                        backdropUrl = obj.optString("background", ""),
                        overview = obj.optString("description", ""),
                        year = obj.optString("releaseInfo", obj.optString("year", "")),
                        rating = obj.optString("imdbRating", ""),
                        genres = obj.optJSONArray("genres").toStringList(),
                        imdbId = if (id.startsWith("tt")) id else ""
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun executeGet(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
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
