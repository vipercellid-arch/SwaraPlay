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
            val mal = item.malId
            val imdb = item.imdbId
            val list = mutableListOf(
                StreamSource("a1", "https://vidsrc.icu/embed/anime/$aid/$episode/0?ds_lang=$lang", "https://vidsrc.icu/"),
                StreamSource("a2", "https://vidlink.pro/anime/$aid/$episode/sub?autoplay=true&primaryColor=ffffff", "https://vidlink.pro/"),
                StreamSource("a3", "https://vidsrc.cc/v2/embed/anime/ani$aid/$episode/sub", "https://vidsrc.cc/"),
                StreamSource("a4", "https://player.autoembed.cc/embed/anime/$aid/$episode?lang=$lang", "https://player.autoembed.cc/"),
                StreamSource("a5", "https://player.videasy.net/anime/$aid/$episode?dub=false&overlay=true", "https://player.videasy.net/")
            )
            if (mal != null && mal > 0) {
                list.add(2, StreamSource("a_mal", "https://vidsrc.cc/v2/embed/anime/$mal/$episode/sub", "https://vidsrc.cc/"))
            }
            if (imdb.startsWith("tt")) {
                list.add(3, StreamSource("a_imdb1", "https://vidsrc.me/embed/tv?imdb=$imdb&season=$season&episode=$episode&ds_lang=$lang", "https://vidsrc.me/"))
                list.add(StreamSource("a_imdb2", "https://www.2embed.cc/embedtv/$imdb&s=$season&e=$episode", "https://www.2embed.cc/"))
            }
            list
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
                    if (isMovie) "https://player.videasy.net/movie/$id"
                    else "https://player.videasy.net/tv/$id/$season/$episode",
                    "https://player.videasy.net/"
                ),
                StreamSource(
                    "s4",
                    if (isMovie) "https://www.2embed.cc/embed/$id"
                    else "https://www.2embed.cc/embedtv/$id&s=$season&e=$episode",
                    "https://www.2embed.cc/"
                ),
                StreamSource(
                    "s5",
                    if (isMovie) "https://moviesapi.club/movie/$id"
                    else "https://moviesapi.club/tv/$id-$season-$episode",
                    "https://moviesapi.club/"
                ),
                StreamSource(
                    "s6",
                    if (isMovie) "https://player.autoembed.cc/embed/movie/$id"
                    else "https://player.autoembed.cc/embed/tv/$id/$season/$episode",
                    "https://player.autoembed.cc/"
                )
            )
        }
    }
}
