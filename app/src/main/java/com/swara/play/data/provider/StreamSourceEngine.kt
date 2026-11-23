package com.swara.play.data.provider

import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.StreamSource

class StreamSourceEngine {

    fun buildSilentSources(
        item: MediaItem,
        season: Int = 1,
        episode: Int = 1,
        subtitleLangIso: String = "id"
    ): List<StreamSource> {
        val lang = subtitleLangIso.ifBlank { "id" }
        return if (item.category == MediaCategory.ANIME && item.aniListId != null) {
            val aid = item.aniListId
            listOf(
                StreamSource("a1", "https://vidsrc.icu/embed/anime/$aid/$episode/0?ds_lang=$lang", "https://vidsrc.icu/"),
                StreamSource("a2", "https://player.autoembed.cc/embed/anime/$aid/$episode?lang=$lang", "https://player.autoembed.cc/"),
                StreamSource("a3", "https://vidsrc.cc/v2/embed/anime/ani$aid/$episode/sub", "https://vidsrc.cc/"),
                StreamSource("a4", "https://vidsrc.icu/embed/anime/$aid/$episode/1", "https://vidsrc.icu/")
            )
        } else {
            val id = item.imdbId.ifBlank { item.id }
            val isMovie = item.category == MediaCategory.MOVIE
            listOf(
                StreamSource(
                    "s1",
                    if (isMovie) "https://vidsrc.me/embed/movie?imdb=$id&ds_lang=$lang"
                    else "https://vidsrc.me/embed/tv?imdb=$id&season=$season&episode=$episode&ds_lang=$lang",
                    "https://vidsrc.me/"
                ),
                StreamSource(
                    "s2",
                    if (isMovie) "https://vidsrc.icu/embed/movie/$id?ds_lang=$lang"
                    else "https://vidsrc.icu/embed/tv/$id/$season/$episode?ds_lang=$lang",
                    "https://vidsrc.icu/"
                ),
                StreamSource(
                    "s3",
                    if (isMovie) "https://www.2embed.cc/embed/$id"
                    else "https://www.2embed.cc/embedtv/$id&s=$season&e=$episode",
                    "https://www.2embed.cc/"
                ),
                StreamSource(
                    "s4",
                    if (isMovie) "https://moviesapi.club/movie/$id"
                    else "https://moviesapi.club/tv/$id-$season-$episode",
                    "https://moviesapi.club/"
                ),
                StreamSource(
                    "s5",
                    if (isMovie) "https://player.autoembed.cc/embed/movie/$id"
                    else "https://player.autoembed.cc/embed/tv/$id/$season/$episode",
                    "https://player.autoembed.cc/"
                ),
                StreamSource(
                    "s6",
                    if (isMovie) "https://multiembed.mov/?video_id=$id"
                    else "https://multiembed.mov/?video_id=$id&s=$season&e=$episode",
                    "https://multiembed.mov/"
                )
            )
        }
    }
}
