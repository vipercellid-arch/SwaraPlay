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

    suspend fun getTrendingAnime(): List<MediaItem> = fetchSorted("TRENDING_DESC")
    suspend fun getTopRatedAnime(): List<MediaItem> = fetchSorted("SCORE_DESC")

    private suspend fun fetchSorted(sort: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val query = """
            query {
              Page(page: 1, perPage: 28) {
                media(type: ANIME, sort: $sort, status_in: [FINISHED, RELEASING], isAdult: false) {
                  id
                  title { romaji english }
                  coverImage { large extraLarge }
                  bannerImage
                  description(asHtml: false)
                  seasonYear
                  averageScore
                  genres
                  episodes
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
              Page(page: 1, perPage: 24) {
                media(type: ANIME, search: ${'$'}search, sort: POPULARITY_DESC, isAdult: false) {
                  id
                  title { romaji english }
                  coverImage { large extraLarge }
                  bannerImage
                  description(asHtml: false)
                  seasonYear
                  averageScore
                  genres
                  episodes
                  nextAiringEpisode { episode }
                }
              }
            }
        """.trimIndent()
        queryMediaList(query, JSONObject().put("search", clean))
    }

    suspend fun getAnimeDetail(item: MediaItem): MediaDetail = withContext(Dispatchers.IO) {
        val count = if (item.totalEpisodes > 0) item.totalEpisodes.coerceAtMost(1200) else 12
        val eps = (1..count).map { num ->
            EpisodeItem(
                id = "${item.id}:1:$num",
                season = 1,
                episode = num,
                title = "Episode $num",
                thumbnailUrl = item.backdropUrl.ifBlank { item.posterUrl }
            )
        }
        val similar = getTrendingAnime().filter { it.id != item.id }.take(14)
        MediaDetail(item = item, episodes = eps, similar = similar)
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
                    val titleObj = m.optJSONObject("title")
                    val title = titleObj?.optString("english", "")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: titleObj?.optString("romaji", "Anime #$id").orEmpty()
                    val cover = m.optJSONObject("coverImage")
                    val poster = cover?.optString("extraLarge", "")?.ifBlank { cover.optString("large", "") }.orEmpty()
                    val banner = m.optString("bannerImage", "").takeIf { it != "null" }.orEmpty()
                    val score = m.optInt("averageScore", 0)
                    val ratingStr = if (score > 0) String.format("%.1f", score / 10.0) else ""
                    val genresArr = m.optJSONArray("genres")
                    val genres = mutableListOf<String>()
                    if (genresArr != null) {
                        for (g in 0 until genresArr.length()) genres.add(genresArr.optString(g, ""))
                    }
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
