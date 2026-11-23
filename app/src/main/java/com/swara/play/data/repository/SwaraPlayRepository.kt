package com.swara.play.data.repository

import com.swara.play.data.provider.AniListProvider
import com.swara.play.data.provider.CinemetaProvider
import com.swara.play.data.provider.StreamServerProvider
import com.swara.play.data.provider.WyzieSubtitleProvider
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaDetail
import com.swara.play.model.MediaItem
import com.swara.play.model.StreamServer
import com.swara.play.model.SubtitleTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class HomeCatalogBundle(
    val movies: List<MediaItem>,
    val series: List<MediaItem>,
    val asianDramas: List<MediaItem>,
    val anime: List<MediaItem>
)

class SwaraPlayRepository(
    private val cinemeta: CinemetaProvider = CinemetaProvider(),
    private val aniList: AniListProvider = AniListProvider(),
    private val streamProvider: StreamServerProvider = StreamServerProvider(),
    private val wyzie: WyzieSubtitleProvider = WyzieSubtitleProvider()
) {
    suspend fun loadHomeCatalog(): HomeCatalogBundle = coroutineScope {
        val moviesDeferred = async { cinemeta.getTopMovies() }
        val seriesDeferred = async { cinemeta.getTopSeries() }
        val dramasDeferred = async { cinemeta.getAsianDramas() }
        val animeDeferred = async { aniList.getTrendingAnime() }
        HomeCatalogBundle(
            movies = moviesDeferred.await(),
            series = seriesDeferred.await(),
            asianDramas = dramasDeferred.await(),
            anime = animeDeferred.await()
        )
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
        return if (item.category == MediaCategory.ANIME) {
            aniList.buildAnimeDetail(item)
        } else {
            cinemeta.getDetail(item)
        }
    }

    suspend fun getServers(
        item: MediaItem,
        season: Int,
        episode: Int,
        customAddons: List<String>
    ): List<StreamServer> {
        return streamProvider.resolveServers(item, season, episode, customAddons)
    }

    suspend fun getSubtitles(
        item: MediaItem,
        season: Int?,
        episode: Int?
    ): List<SubtitleTrack> {
        val isSeries = item.category != MediaCategory.MOVIE
        return wyzie.fetchSubtitles(
            imdbId = item.imdbId,
            season = if (isSeries) season else null,
            episode = if (isSeries) episode else null
        )
    }
}
