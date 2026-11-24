package com.swara.play.data.provider

import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.StreamSource

class StreamSourceEngine {

    private fun buildAnimeSlug(item: MediaItem): String {
        val base = item.romajiTitle.ifBlank { item.title }
        return base.lowercase()
            .replace("'", "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }

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
            val slug = buildAnimeSlug(item)
            val list = mutableListOf<StreamSource>()

            // Priority 1 & 2: Slug-based anime players (friendly to Android WebView, zero Cloudflare Turnstile block)
            if (slug.isNotBlank()) {
                list.add(StreamSource("a_slug2", "https://anime.autoembed.cc/embed/$slug-episode-$episode", "https://anime.autoembed.cc/"))
            }
            // Priority 3: vidsrc.cc AniList direct
            list.add(StreamSource("a_vcc", "https://vidsrc.cc/v2/embed/anime/ani$aid/$episode/sub", "https://vidsrc.cc/"))
            // Priority 4: MAL ID direct
            if (mal != null && mal > 0) {
                list.add(StreamSource("a_mal", "https://vidsrc.cc/v2/embed/anime/$mal/$episode/sub", "https://vidsrc.cc/"))
            }
            // Priority 5: IMDb fallback (works reliably for One Piece, Jujutsu Kaisen, Demon Slayer, AoT, etc.)
            if (imdb.startsWith("tt")) {
                list.add(StreamSource("a_imdb1", "https://vidsrc.me/embed/tv?imdb=$imdb&season=$season&episode=$episode&ds_lang=$lang", "https://vidsrc.me/"))
                list.add(StreamSource("a_imdb2", "https://www.2embed.cc/embedtv/$imdb&s=$season&e=$episode", "https://www.2embed.cc/"))
            }
            // Priority 6 & 7: Additional AniList backups
            list.add(StreamSource("a_embedsu", "https://embed.su/embed/anime/$aid/$episode", "https://embed.su/"))
            list.add(StreamSource("a_videasy", "https://player.videasy.net/anime/$aid/$episode?dub=false&overlay=true", "https://player.videasy.net/"))
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
                    if (isMovie) "https://vidsrc.cc/v2/embed/movie/$id"
                    else "https://vidsrc.cc/v2/embed/tv/$id/$season/$episode",
                    "https://vidsrc.cc/"
                ),
                StreamSource(
                    "s_vip",
                    if (isMovie) "https://vidsrc.vip/embed/movie/$id"
                    else "https://vidsrc.vip/embed/tv/$id/$season/$episode",
                    "https://vidsrc.vip/"
                ),
                StreamSource(
                    "s_embedsu",
                    if (isMovie) "https://embed.su/embed/movie/$id"
                    else "https://embed.su/embed/tv/$id/$season/$episode",
                    "https://embed.su/"
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
