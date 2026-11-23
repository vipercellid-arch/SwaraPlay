package com.swara.play.data.provider

import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.StreamKind
import com.swara.play.model.StreamServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

class StreamServerProvider {

    suspend fun resolveServers(
        item: MediaItem,
        season: Int = 1,
        episode: Int = 1,
        customStremioAddons: List<String> = emptyList()
    ): List<StreamServer> = withContext(Dispatchers.IO) {
        val servers = mutableListOf<StreamServer>()

        if (item.imdbId.isNotBlank() && customStremioAddons.isNotEmpty()) {
            val type = if (item.category == MediaCategory.MOVIE) "movie" else "series"
            val streamId = if (item.category == MediaCategory.MOVIE) item.imdbId else "${item.imdbId}:$season:$episode"
            for (addonBase in customStremioAddons) {
                val cleanBase = addonBase.trim().removeSuffix("/manifest.json").removeSuffix("/")
                if (cleanBase.isBlank()) continue
                servers.addAll(fetchStremioStreams(cleanBase, type, streamId))
            }
        }

        if (item.category == MediaCategory.ANIME && item.aniListId != null) {
            val aid = item.aniListId
            servers.addAll(
                listOf(
                    StreamServer(
                        id = "anime_vidsrc_icu_sub",
                        name = "Server 1 — VidSrc.icu Anime (Sub)",
                        providerTag = "AniList Native • Stabil",
                        url = "https://vidsrc.icu/embed/anime/$aid/$episode/0",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://vidsrc.icu/"
                    ),
                    StreamServer(
                        id = "anime_autoembed",
                        name = "Server 2 — AutoEmbed Anime",
                        providerTag = "Multi Mirror • Sub Indo",
                        url = "https://player.autoembed.cc/embed/anime/$aid/$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://player.autoembed.cc/"
                    ),
                    StreamServer(
                        id = "anime_vidsrc_icu_dub",
                        name = "Server 3 — VidSrc.icu Anime (Dub)",
                        providerTag = "English Dub",
                        url = "https://vidsrc.icu/embed/anime/$aid/$episode/1",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://vidsrc.icu/",
                        isDub = true
                    ),
                    StreamServer(
                        id = "anime_vidsrc_cc",
                        name = "Server 4 — VidSrc.cc (Cadangan)",
                        providerTag = "Cadangan AniList",
                        url = "https://vidsrc.cc/v2/embed/anime/ani$aid/$episode/sub",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://vidsrc.cc/"
                    )
                )
            )
        } else {
            val id = item.imdbId.ifBlank { item.id }
            val isMovie = item.category == MediaCategory.MOVIE
            servers.addAll(
                listOf(
                    StreamServer(
                        id = "vidsrc_icu",
                        name = "Server 1 — VidSrc.icu (Utama)",
                        providerTag = "IMDb Native • Sub Indo",
                        url = if (isMovie) "https://vidsrc.icu/embed/movie/$id"
                        else "https://vidsrc.icu/embed/tv/$id/$season/$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://vidsrc.icu/"
                    ),
                    StreamServer(
                        id = "vidsrc_me",
                        name = "Server 2 — VidSrc.me HD",
                        providerTag = "IMDb Native • Cepat",
                        url = if (isMovie) "https://vidsrc.me/embed/movie?imdb=$id"
                        else "https://vidsrc.me/embed/tv?imdb=$id&season=$season&episode=$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://vidsrc.me/"
                    ),
                    StreamServer(
                        id = "twoembed_cc",
                        name = "Server 3 — 2Embed.cc",
                        providerTag = "IMDb Native • Multi Player",
                        url = if (isMovie) "https://www.2embed.cc/embed/$id"
                        else "https://www.2embed.cc/embedtv/$id&s=$season&e=$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://www.2embed.cc/"
                    ),
                    StreamServer(
                        id = "moviesapi",
                        name = "Server 4 — MoviesAPI HD",
                        providerTag = "IMDb Native • Stabil",
                        url = if (isMovie) "https://moviesapi.club/movie/$id"
                        else "https://moviesapi.club/tv/$id-$season-$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://moviesapi.club/"
                    ),
                    StreamServer(
                        id = "autoembed",
                        name = "Server 5 — AutoEmbed",
                        providerTag = "IMDb Native • Cadangan",
                        url = if (isMovie) "https://player.autoembed.cc/embed/movie/$id"
                        else "https://player.autoembed.cc/embed/tv/$id/$season/$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "HD",
                        referer = "https://player.autoembed.cc/"
                    ),
                    StreamServer(
                        id = "multiembed",
                        name = "Server 6 — SuperEmbed",
                        providerTag = "MultiEmbed • Global",
                        url = if (isMovie) "https://multiembed.mov/?video_id=$id"
                        else "https://multiembed.mov/?video_id=$id&s=$season&e=$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "HD",
                        referer = "https://multiembed.mov/"
                    ),
                    StreamServer(
                        id = "smashystream",
                        name = "Server 7 — SmashyStream",
                        providerTag = "Cadangan Terakhir",
                        url = if (isMovie) "https://player.smashy.stream/movie/$id"
                        else "https://player.smashy.stream/tv/$id?s=$season&e=$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "HD",
                        referer = "https://player.smashy.stream/"
                    )
                )
            )
        }
        servers
    }

    private fun fetchStremioStreams(baseUrl: String, type: String, streamId: String): List<StreamServer> {
        return try {
            val req = Request.Builder().url("$baseUrl/stream/$type/$streamId.json").header("User-Agent", HttpClientProvider.USER_AGENT).build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val text = resp.body?.string() ?: return emptyList()
                val arr = JSONObject(text).optJSONArray("streams") ?: return emptyList()
                val out = mutableListOf<StreamServer>()
                for (i in 0 until arr.length().coerceAtMost(8)) {
                    val s = arr.optJSONObject(i) ?: continue
                    val directUrl = s.optString("url", "")
                    if (directUrl.startsWith("http")) {
                        val title = s.optString("title", s.optString("name", "Stremio Stream ${i + 1}")).replace("\n", " • ").take(48)
                        out.add(
                            StreamServer(
                                id = "stremio_${i}_${directUrl.hashCode()}",
                                name = title,
                                providerTag = "Stremio • ExoPlayer Native",
                                url = directUrl,
                                kind = StreamKind.DIRECT_EXO,
                                quality = if (title.contains("4K", true) || title.contains("2160")) "4K" else "1080p"
                            )
                        )
                    }
                }
                out
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
