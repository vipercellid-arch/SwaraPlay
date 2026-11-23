package com.swara.play.data.repository

import com.swara.play.data.provider.AniListProvider
import com.swara.play.data.provider.CinemetaProvider
import com.swara.play.data.provider.StreamSourceEngine
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaDetail
import com.swara.play.model.MediaItem
import com.swara.play.model.StreamSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class HomeCatalogBundle(
    val movies: List<MediaItem>,
    val series: List<MediaItem>,
    val asianDramas: List<MediaItem>,
    val animeTrending: List<MediaItem>,
    val animeTopRated: List<MediaItem>,
    val actionMovies: List<MediaItem>,
    val horrorMovies: List<MediaItem>
)

class SwaraPlayRepository(
    private val cinemeta: CinemetaProvider = CinemetaProvider(),
    private val aniList: AniListProvider = AniListProvider(),
    private val sourceEngine: StreamSourceEngine = StreamSourceEngine()
) {
    suspend fun loadHomeCatalog(): HomeCatalogBundle = coroutineScope {
        val moviesDeferred = async { cinemeta.getTopMovies() }
        val seriesDeferred = async { cinemeta.getTopSeries() }
        val dramasDeferred = async { cinemeta.getAsianDramas() }
        val animeTrendDeferred = async { aniList.getTrendingAnime() }
        val animeTopDeferred = async { aniList.getTopRatedAnime() }
        val actionDeferred = async { cinemeta.getByGenre("movie", "Action", MediaCategory.MOVIE) }
        val horrorDeferred = async { cinemeta.getByGenre("movie", "Horror", MediaCategory.MOVIE) }
        HomeCatalogBundle(
            movies = moviesDeferred.await(),
            series = seriesDeferred.await(),
            asianDramas = dramasDeferred.await(),
            animeTrending = animeTrendDeferred.await(),
            animeTopRated = animeTopDeferred.await(),
            actionMovies = actionDeferred.await(),
            horrorMovies = horrorDeferred.await()
        )
    }

    suspend fun browseFiltered(category: MediaCategory, area: String, genre: String): List<MediaItem> = coroutineScope {
        val queryArea = when (area) {
            "Korea Selatan" -> "korean"
            "Jepang" -> "japanese"
            "Cina" -> "chinese"
            "Thailand" -> "thai"
            "Indonesia" -> "indonesia"
            else -> ""
        }
        if (queryArea.isNotBlank()) return@coroutineScope searchAll(queryArea, category)
        if (genre != "Semua Kategori") {
            val mapped = when (genre) {
                "Aksi" -> "Action"
                "Romansa" -> "Romance"
                "Fantasi" -> "Fantasy"
                "Animasi" -> "Animation"
                "Misteri" -> "Mystery"
                "Fiksi Ilmiah" -> "Sci-Fi"
                "Horor" -> "Horror"
                "Komedi" -> "Comedy"
                else -> genre
            }
            val m = async { cinemeta.getByGenre("movie", mapped, MediaCategory.MOVIE) }
            val s = async { cinemeta.getByGenre("series", mapped, MediaCategory.SERIES) }
            return@coroutineScope (m.await().take(18) + s.await().take(18))
        }
        when (category) {
            MediaCategory.MOVIE -> cinemeta.getTopMovies()
            MediaCategory.SERIES -> cinemeta.getTopSeries()
            MediaCategory.ASIAN_DRAMA -> cinemeta.getAsianDramas()
            MediaCategory.ANIME -> aniList.getTrendingAnime()
            MediaCategory.ALL -> {
                val m = async { cinemeta.getTopMovies() }
                val d = async { cinemeta.getAsianDramas() }
                val a = async { aniList.getTrendingAnime() }
                (m.await().take(12) + d.await().take(12) + a.await().take(12))
            }
        }
    }

    suspend fun searchAll(query: String, category: MediaCategory): List<MediaItem> = coroutineScope {
        when (category) {
            MediaCategory.ANIME -> aniList.searchAnime(query)
            MediaCategory.MOVIE -> cinemeta.search(query).filter { it.category == MediaCategory.MOVIE }
            MediaCategory.SERIES, MediaCategory.ASIAN_DRAMA ->
                cinemeta.search(query).filter { it.category != MediaCategory.MOVIE }
            MediaCategory.ALL -> {
                val cDeferred = async { cinemeta.search(query) }
                val aDeferred = async { aniList.searchAnime(query) }
                cDeferred.await() + aDeferred.await()
            }
        }
    }

    suspend fun getMediaDetail(item: MediaItem): MediaDetail {
        return if (item.category == MediaCategory.ANIME) aniList.getAnimeDetail(item)
        else cinemeta.getDetail(item)
    }

    fun getSilentSources(item: MediaItem, season: Int, episode: Int, subLangIso: String): List<StreamSource> {
        return sourceEngine.buildSilentSources(item, season, episode, subLangIso)
    }
}
