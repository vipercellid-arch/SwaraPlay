package com.swara.play.data.repository

import com.swara.play.data.provider.ActorProfileProvider
import com.swara.play.data.provider.AniListProvider
import com.swara.play.data.provider.CinemetaProvider
import com.swara.play.data.provider.StreamSourceEngine
import com.swara.play.data.provider.WyzieSubtitleResolver
import com.swara.play.model.ActorProfile
import com.swara.play.model.CastMember
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaDetail
import com.swara.play.model.MediaItem
import com.swara.play.model.StreamSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class HomeCatalogBundle(
    val realtimeMovies: List<MediaItem>,
    val realtimeSeries: List<MediaItem>,
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
        val rtMoviesDeferred = async { cinemeta.getRealtimeTrendingMovies() }
        val rtSeriesDeferred = async { cinemeta.getRealtimeTrendingSeries() }
        val moviesDeferred = async { cinemeta.getTopMovies(0) }
        val seriesDeferred = async { cinemeta.getTopSeries(0) }
        val dramasDeferred = async { cinemeta.getAsianDramas(0) }
        val animeTrendDeferred = async { aniList.getTrendingAnime(1) }
        val animeTopDeferred = async { aniList.getTopRatedAnime(1) }
        val actionDeferred = async { cinemeta.getByGenre("movie", "Action", MediaCategory.MOVIE, 0) }
        val horrorDeferred = async { cinemeta.getByGenre("movie", "Horror", MediaCategory.MOVIE, 0) }
        HomeCatalogBundle(
            realtimeMovies = rtMoviesDeferred.await(),
            realtimeSeries = rtSeriesDeferred.await(),
            movies = moviesDeferred.await(),
            series = seriesDeferred.await(),
            asianDramas = dramasDeferred.await(),
            animeTrending = animeTrendDeferred.await(),
            animeTopRated = animeTopDeferred.await(),
            actionMovies = actionDeferred.await(),
            horrorMovies = horrorDeferred.await()
        )
    }

    suspend fun loadMoreHomeCatalog(category: MediaCategory, page: Int): List<MediaItem> = coroutineScope {
        when (category) {
            MediaCategory.MOVIE -> cinemeta.getTopMovies(page)
            MediaCategory.SERIES -> cinemeta.getTopSeries(page)
            MediaCategory.ASIAN_DRAMA -> cinemeta.getAsianDramas(page)
            MediaCategory.ANIME -> aniList.getPopularAnime(page + 1)
            MediaCategory.ALL -> {
                val m = async { cinemeta.getTopMovies(page) }
                val s = async { cinemeta.getTopSeries(page) }
                val a = async { aniList.getTrendingAnime(page + 1) }
                (m.await().take(12) + s.await().take(10) + a.await().take(10)).distinctBy { it.id }
            }
        }
    }

    suspend fun browseFiltered(category: MediaCategory, area: String, genre: String, page: Int = 0): List<MediaItem> = coroutineScope {
        val queryArea = when (area) {
            "Korea Selatan" -> "korean"
            "Jepang" -> "japanese"
            "Cina" -> "chinese"
            "Thailand" -> "thai"
            "Indonesia" -> "indonesia"
            else -> ""
        }
        if (queryArea.isNotBlank()) {
            return@coroutineScope if (page == 0) searchAll(queryArea, category) else emptyList()
        }
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
            val m = async { cinemeta.getByGenre("movie", mapped, MediaCategory.MOVIE, page) }
            val s = async { cinemeta.getByGenre("series", mapped, MediaCategory.SERIES, page) }
            return@coroutineScope (m.await().take(18) + s.await().take(18)).distinctBy { it.id }
        }
        when (category) {
            MediaCategory.MOVIE -> cinemeta.getTopMovies(page)
            MediaCategory.SERIES -> cinemeta.getTopSeries(page)
            MediaCategory.ASIAN_DRAMA -> cinemeta.getAsianDramas(page)
            MediaCategory.ANIME -> aniList.getTrendingAnime(page + 1)
            MediaCategory.ALL -> {
                val m = async { cinemeta.getTopMovies(page) }
                val d = async { cinemeta.getAsianDramas(page) }
                val a = async { aniList.getTrendingAnime(page + 1) }
                (m.await().take(12) + d.await().take(10) + a.await().take(12)).distinctBy { it.id }
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

    suspend fun getMediaDetail(item: MediaItem, targetLangIso: String = "id"): MediaDetail {
        return if (item.category == MediaCategory.ANIME) aniList.getAnimeDetail(item, targetLangIso)
        else cinemeta.getDetail(item, targetLangIso)
    }

    suspend fun ensureAnimeImdbId(item: MediaItem): MediaItem {
        if (item.category != MediaCategory.ANIME || item.imdbId.startsWith("tt")) return item
        val viaArm = aniList.resolveImdbIdForAniList(item.aniListId)
        val finalImdb = viaArm.ifBlank { cinemeta.lookupImdbIdForTitle(item.title) }
        return if (finalImdb.startsWith("tt")) item.copy(imdbId = finalImdb) else item
    }

    suspend fun fetchRankedSynchronizedVtts(item: MediaItem, season: Int, episode: Int, langIso: String): List<String> {
        return WyzieSubtitleResolver.fetchRankedSynchronizedVtts(item, season, episode, langIso)
    }

    suspend fun loadActorProfile(actor: CastMember, targetLangIso: String = "id"): ActorProfile {
        return ActorProfileProvider.loadFullActorProfile(actor, targetLangIso)
    }

    fun getSilentSources(item: MediaItem, season: Int, episode: Int, subLangIso: String): List<StreamSource> {
        return sourceEngine.buildSilentSources(item, season, episode, subLangIso)
    }
}
