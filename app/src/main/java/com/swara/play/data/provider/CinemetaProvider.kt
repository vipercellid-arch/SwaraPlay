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

    private fun curatedAsianDramas(): List<MediaItem> = listOf(
        MediaItem("tt10919420", "Squid Game", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt10919420/img", "https://images.metahub.space/background/medium/tt10919420/img", "Ratusan pemain yang terlilit utang mengikuti permainan anak-anak mematikan demi hadiah miliaran won.", "2021–2025", "8.0", listOf("Thriller", "Drama"), imdbId = "tt10919420"),
        MediaItem("tt27668559", "Queen of Tears", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt27668559/img", "https://images.metahub.space/background/medium/tt27668559/img", "Kisah cinta penuh ujian antara pewaris konglomerat dan pengacara muda dari desa.", "2024", "8.2", listOf("Romance", "Drama"), imdbId = "tt27668559"),
        MediaItem("tt31806037", "When Life Gives You Tangerines", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt31806037/img", "https://images.metahub.space/background/medium/tt31806037/img", "Perjalanan hidup penuh haru dua jiwa dari Pulau Jeju melintasi empat musim kehidupan.", "2025", "9.1", listOf("Romance", "Drama"), imdbId = "tt31806037"),
        MediaItem("tt21344706", "The Glory", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt21344706/img", "https://images.metahub.space/background/medium/tt21344706/img", "Seorang wanita menyusun rencana balas dendam matang terhadap para perundungnya semasa sekolah.", "2022–2023", "8.1", listOf("Thriller", "Drama"), imdbId = "tt21344706"),
        MediaItem("tt10795658", "Alice in Borderland", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt10795658/img", "https://images.metahub.space/background/medium/tt10795658/img", "Arisu dan teman-temannya terjebak di Tokyo kosong dan harus bertahan hidup lewat permainan maut.", "2020–2025", "7.8", listOf("Sci-Fi", "Thriller"), imdbId = "tt10795658"),
        MediaItem("tt14169960", "All of Us Are Dead", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt14169960/img", "https://images.metahub.space/background/medium/tt14169960/img", "Sekelompok siswa SMA berjuang bertahan hidup saat wabah zombi melanda sekolah mereka.", "2022–", "7.5", listOf("Horror", "Action"), imdbId = "tt14169960"),
        MediaItem("tt24640580", "Moving", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt24640580/img", "https://images.metahub.space/background/medium/tt24640580/img", "Anak-anak berkekuatan super dan orang tua mereka bersembunyi dari ancaman agen rahasia.", "2023", "8.4", listOf("Action", "Sci-Fi"), imdbId = "tt24640580"),
        MediaItem("tt10850932", "Crash Landing on You", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt10850932/img", "https://images.metahub.space/background/medium/tt10850932/img", "Pewaris Korea Selatan tersesat di Korea Utara akibat kecelakaan paralayang dan ditolong perwira militer.", "2019–2020", "8.7", listOf("Romance", "Comedy"), imdbId = "tt10850932"),
        MediaItem("tt20859920", "Alchemy of Souls", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt20859920/img", "https://images.metahub.space/background/medium/tt20859920/img", "Penyihir tangguh yang jiwanya terperangkap dalam tubuh lemah bertemu bangsawan muda.", "2022–2023", "8.7", listOf("Fantasy", "Romance"), imdbId = "tt20859920"),
        MediaItem("tt30988739", "Lovely Runner", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt30988739/img", "https://images.metahub.space/background/medium/tt30988739/img", "Penggemar setia kembali ke masa lalu untuk menyelamatkan idola favoritnya dari nasib tragis.", "2024", "8.6", listOf("Romance", "Fantasy"), imdbId = "tt30988739"),
        MediaItem("tt13433812", "Vincenzo", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt13433812/img", "https://images.metahub.space/background/medium/tt13433812/img", "Pengacara mafia Italia keturunan Korea menghadapi konglomerat korup dengan caranya sendiri.", "2021", "8.4", listOf("Crime", "Comedy"), imdbId = "tt13433812"),
        MediaItem("tt14819828", "Business Proposal", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt14819828/img", "https://images.metahub.space/background/medium/tt14819828/img", "Kencan buta menggantikan sahabat ternyata mempertemukannya dengan CEO perusahaannya sendiri.", "2022", "8.1", listOf("Romance", "Comedy"), imdbId = "tt14819828"),
        MediaItem("tt20234568", "Weak Hero Class 1", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt20234568/img", "https://images.metahub.space/background/medium/tt20234568/img", "Siswa teladan bertubuh kecil melawan kekerasan di sekolah menggunakan kecerdasan dan strategi.", "2022–2025", "8.5", listOf("Action", "Drama"), imdbId = "tt20234568"),
        MediaItem("tt5994364", "Goblin", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt5994364/img", "https://images.metahub.space/background/medium/tt5994364/img", "Makhluk abadi mencari pengantin manusianya untuk mengakhiri kutukan keabadian.", "2016–2017", "8.6", listOf("Fantasy", "Romance"), imdbId = "tt5994364"),
        MediaItem("tt6611916", "Kingdom", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt6611916/img", "https://images.metahub.space/background/medium/tt6611916/img", "Putra mahkota Joseon menyelidiki wabah misterius yang membangkitkan orang mati.", "2019–2020", "8.3", listOf("Horror", "Action"), imdbId = "tt6611916"),
        MediaItem("tt11612120", "Sweet Home", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt11612120/img", "https://images.metahub.space/background/medium/tt11612120/img", "Penghuni apartemen berjuang bertahan hidup saat manusia berubah menjadi monster ganas.", "2020–2024", "7.3", listOf("Horror", "Sci-Fi"), imdbId = "tt11612120"),
        MediaItem("tt20869502", "Extraordinary Attorney Woo", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt20869502/img", "https://images.metahub.space/background/medium/tt20869502/img", "Pengacara jenius dengan spektrum autisme memecahkan kasus hukum rumit di firma bergengsi.", "2022", "8.6", listOf("Drama", "Comedy"), imdbId = "tt20869502"),
        MediaItem("tt11239552", "Itaewon Class", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt11239552/img", "https://images.metahub.space/background/medium/tt11239552/img", "Mantan narapidana membangun restoran di Itaewon untuk menantang konglomerat makanan terbesar.", "2020", "8.2", listOf("Drama"), imdbId = "tt11239552"),
        MediaItem("tt10220588", "Hotel Del Luna", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt10220588/img", "https://images.metahub.space/background/medium/tt10220588/img", "Manajer hotel elit bekerja di hotel misterius yang hanya melayani para arwah.", "2019", "8.1", listOf("Fantasy", "Romance"), imdbId = "tt10220588"),
        MediaItem("tt29569035", "My Demon", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt29569035/img", "https://images.metahub.space/background/medium/tt29569035/img", "Iblis yang kehilangan kekuatannya terikat pernikahan kontrak dengan pewaris berhati dingin.", "2023–2024", "7.7", listOf("Romance", "Fantasy"), imdbId = "tt29569035"),
        MediaItem("tt30133466", "Marry My Husband", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt30133466/img", "https://images.metahub.space/background/medium/tt30133466/img", "Diberi kesempatan kembali 10 tahun ke masa lalu, ia mengubah takdir pengkhianatan suaminya.", "2024", "7.9", listOf("Drama", "Romance"), imdbId = "tt30133466"),
        MediaItem("tt12879522", "My Name", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt12879522/img", "https://images.metahub.space/background/medium/tt12879522/img", "Demi membalas kematian ayahnya, seorang wanita menyusup ke kepolisian sebagai mata-mata kartel.", "2021", "7.8", listOf("Action", "Crime"), imdbId = "tt12879522"),
        MediaItem("tt14979052", "Happiness", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt14979052/img", "https://images.metahub.space/background/medium/tt14979052/img", "Penghuni apartemen bertingkat dikarantina ketat akibat penyakit menular misterius.", "2021", "7.9", listOf("Thriller", "Horror"), imdbId = "tt14979052"),
        MediaItem("tt4925000", "Descendants of the Sun", MediaCategory.ASIAN_DRAMA, "https://images.metahub.space/poster/medium/tt4925000/img", "https://images.metahub.space/background/medium/tt4925000/img", "Kisah cinta kapten pasukan khusus dan dokter bedah di daerah konflik.", "2016", "8.2", listOf("Romance", "Action"), imdbId = "tt4925000")
    )

    suspend fun getAsianDramas(page: Int = 0): List<MediaItem> = withContext(Dispatchers.IO) {
        val collected = LinkedHashMap<String, MediaItem>()
        if (page == 0) {
            for (c in curatedAsianDramas()) collected[c.id] = c
        }
        val allQueries = listOf(
            "the trauma code", "study group", "a shop for killers", "parasyte the grey",
            "gyeongseong creature", "bloodhounds", "twinkling watermelon", "hidden love",
            "the untamed", "first frost", "girl from nowhere", "f4 thailand",
            "hospital playlist", "reply 1988", "signal", "mouse",
            "twenty five twenty one", "mr sunshine", "flower of evil", "the worst of evil"
        )
        val startIdx = page * 6
        if (startIdx < allQueries.size) {
            val batch = allQueries.subList(startIdx, minOf(startIdx + 6, allQueries.size))
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
                async { ActorProfileProvider.resolveCastMember(actorName) }
            }.awaitAll()
        }

        val tvPair = if (item.category != MediaCategory.MOVIE && imdb.startsWith("tt")) {
            fetchTvMazeEpisodesAndSeasons(imdb)
        } else {
            emptyList<EpisodeItem>() to emptyMap<Int, String>()
        }
        val finalEpisodes = if (tvPair.first.size >= cinemetaEpisodes.size && tvPair.first.isNotEmpty()) tvPair.first else cinemetaEpisodes
        val seasonNamesMap = tvPair.second

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
            similar = similar,
            seasonNames = seasonNamesMap
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
                        imdbId = if (id.startsWith("tt")) id else "",
                        isAdult18 = ContentTagHelper.detectAdult18(name, obj.optString("description", ""), obj.optJSONArray("genres").toStringList()),
                        isLgbt = ContentTagHelper.detectLgbt(name, obj.optString("description", ""), obj.optJSONArray("genres").toStringList())
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
