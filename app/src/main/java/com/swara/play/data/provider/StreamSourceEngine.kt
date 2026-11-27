package com.swara.play.data.provider

import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.StreamSource

/**
 * SwaraPlay v9.7.0 Stable — Unblocked EncDec-Ecosystem Fallback Player Engine:
 * Uses VidLink.pro & VidFast.pro (both unblocked in Indonesia, replacing DNS-blocked videasy).
 */
class StreamSourceEngine {

    fun buildSilentSources(
        item: MediaItem,
        season: Int = 1,
        episode: Int = 1,
        subtitleLangIso: String = "id"
    ): List<StreamSource> {
        return if (item.category == MediaCategory.ANIME && item.aniListId != null) {
            val aid = item.aniListId
            listOf(
                StreamSource("enc_vidlink_anime", "https://vidlink.pro/anime/$aid/$episode/sub", "https://vidlink.pro/"),
                StreamSource("enc_vidfast_anime", "https://vidfast.pro/anime/$aid/$episode?sub=true", "https://vidfast.pro/")
            )
        } else {
            val id = item.imdbId.ifBlank { item.id }
            val isMovie = item.category == MediaCategory.MOVIE
            listOf(
                StreamSource(
                    "enc_vidlink",
                    if (isMovie) "https://vidlink.pro/movie/$id?sub=id"
                    else "https://vidlink.pro/tv/$id/$season/$episode?sub=id",
                    "https://vidlink.pro/"
                ),
                StreamSource(
                    "enc_vidfast",
                    if (isMovie) "https://vidfast.pro/movie/$id"
                    else "https://vidfast.pro/tv/$id/$season/$episode",
                    "https://vidfast.pro/"
                )
            )
        }
    }
}
