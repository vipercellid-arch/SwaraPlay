package com.swara.play.data.provider

import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.EpisodeItem
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaDetail
import com.swara.play.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AniListProvider {
    private val endpoint = "https://graphql.anilist.co"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getTrendingAnime(page: Int = 1): List<MediaItem> = fetchSorted("TRENDING_DESC", page)
    suspend fun getTopRatedAnime(page: Int = 1): List<MediaItem> = fetchSorted("SCORE_DESC", page)
    suspend fun getPopularAnime(page: Int = 1): List<MediaItem> = fetchSorted("POPULARITY_DESC", page)

    private suspend fun fetchSorted(sort: String, page: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val query = """
            query {
              Page(page: $safePage, perPage: 28) {
                media(type: ANIME, sort: $sort, status_in: [FINISHED, RELEASING], isAdult: false) {
                  id
                  idMal
                  title { romaji english }
                  coverImage { large extraLarge }
                  bannerImage
                  description(asHtml: false)
                  seasonYear
                  averageScore
                  genres
                  episodes
                  trailer { id site }
                  nextAiringEpisode { episode }
                }
              }
            }
        """.trimIndent()
        queryMediaList(query, null)
    }

    suspend fun searchAnime(searchText: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val clean = searchText.trim()
        if (clean.isEmpty()) return@withContext emptyList()
        val query = """
            query (${'$'}search: String) {
              Page(page: 1, perPage: 28) {
                media(type: ANIME, search: ${'$'}search, sort: POPULARITY_DESC, isAdult: false) {
                  id
                  idMal
                  title { romaji english }
                  coverImage { large extraLarge }
                  bannerImage
                  description(asHtml: false)
                  seasonYear
                  averageScore
                  genres
                  episodes
                  trailer { id site }
                  nextAiringEpisode { episode }
                }
              }
            }
        """.trimIndent()
        queryMediaList(query, JSONObject().put("search", clean))
    }

    suspend fun resolveImdbIdForAniList(aniListId: Int?): String = withContext(Dispatchers.IO) {
        if (aniListId == null || aniListId <= 0) return@withContext ""
        try {
            val req = Request.Builder()
                .url("https://arm.haglund.dev/api/v2/anilist?id=$aniListId")
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext ""
                val text = resp.body?.string() ?: return@withContext ""
                val imdb = JSONObject(text).optString("imdb", "")
                if (imdb.startsWith("tt")) imdb else ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun getAnimeDetail(item: MediaItem, targetLangIso: String = "id"): MediaDetail = withContext(Dispatchers.IO) {
        val count = if (item.totalEpisodes > 0) item.totalEpisodes.coerceAtMost(1500) else 12
        val eps = (1..count).map { num ->
            EpisodeItem(
                id = "${item.id}:1:$num",
                season = 1,
                episode = num,
                title = "Episode $num",
                thumbnailUrl = item.backdropUrl.ifBlank { item.posterUrl }
            )
        }
        val resolvedImdb = item.imdbId.ifBlank { resolveImdbIdForAniList(item.aniListId) }
        val ytId = item.trailerYoutubeId.ifBlank { WyzieSubtitleResolver.resolveYoutubeTrailerId(item.title, item.year) }
        val translatedOverview = SynopsisTranslator.translateToLanguage(item.overview, targetLangIso)
        val similar = getTrendingAnime(1).filter { it.id != item.id }.take(14)
        MediaDetail(
            item = item.copy(overview = translatedOverview, imdbId = resolvedImdb, trailerYoutubeId = ytId),
            trailerYoutubeId = ytId,
            episodes = eps,
            similar = similar
        )
    }

    private fun queryMediaList(query: String, variables: JSONObject?): List<MediaItem> {
        return try {
            val payload = JSONObject().put("query", query)
            if (variables != null) payload.put("variables", variables)
            val req = Request.Builder()
                .url(endpoint)
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val text = resp.body?.string() ?: return emptyList()
                val mediaArr = JSONObject(text)
                    .optJSONObject("data")
                    ?.optJSONObject("Page")
                    ?.optJSONArray("media") ?: return emptyList()
                val items = mutableListOf<MediaItem>()
                for (i in 0 until mediaArr.length()) {
                    val m = mediaArr.optJSONObject(i) ?: continue
                    val id = m.optInt("id", 0)
                    if (id == 0) continue
                    val idMal = m.optInt("idMal", 0).let { if (it > 0) it else null }
                    val titleObj = m.optJSONObject("title")
                    val romaji = titleObj?.optString("romaji", "").takeIf { !it.isNullOrBlank() && it != "null" }.orEmpty()
                    val title = titleObj?.optString("english", "")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: romaji.ifBlank { "Anime #$id" }
                    val cover = m.optJSONObject("coverImage")
                    val poster = cover?.optString("extraLarge", "")?.ifBlank { cover.optString("large", "") }.orEmpty()
                    if (!poster.startsWith("http")) continue
                    val banner = m.optString("bannerImage", "").takeIf { it != "null" }.orEmpty()
                    val score = m.optInt("averageScore", 0)
                    val ratingStr = if (score > 0) String.format("%.1f", score / 10.0) else ""
                    val genresArr = m.optJSONArray("genres")
                    val genres = mutableListOf<String>()
                    if (genresArr != null) {
                        for (g in 0 until genresArr.length()) genres.add(genresArr.optString(g, ""))
                    }
                    val trailerObj = m.optJSONObject("trailer")
                    val ytTrailer = if (trailerObj?.optString("site", "").equals("youtube", true)) {
                        trailerObj?.optString("id", "").orEmpty()
                    } else ""
                    val totalDeclared = m.optInt("episodes", 0)
                    val nextAiring = m.optJSONObject("nextAiringEpisode")?.optInt("episode", 0) ?: 0
                    val airedCount = when {
                        nextAiring > 1 -> nextAiring - 1
                        totalDeclared > 0 -> totalDeclared
                        else -> 12
                    }
                    val rawDesc = m.optString("description", "")
                        .replace(Regex("""<[^>]*>"""), "")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                        .trim()
                    items.add(
                        MediaItem(
                            id = "anilist:$id",
                            title = title,
                            category = MediaCategory.ANIME,
                            posterUrl = poster,
                            backdropUrl = banner,
                            overview = rawDesc,
                            year = m.optInt("seasonYear", 0).let { if (it > 0) it.toString() else "" },
                            rating = ratingStr,
                            genres = genres.filter { it.isNotBlank() },
                            aniListId = id,
                            malId = idMal,
                            romajiTitle = romaji,
                            trailerYoutubeId = ytTrailer,
                            totalEpisodes = airedCount
                        )
                    )
                }
                items
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
