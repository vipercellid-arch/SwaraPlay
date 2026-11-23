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

        // 1. Custom Stremio Addon Direct Streams (Native Media3 ExoPlayer)
        if (item.imdbId.isNotBlank() && customStremioAddons.isNotEmpty()) {
            val type = if (item.category == MediaCategory.MOVIE) "movie" else "series"
            val streamId = if (item.category == MediaCategory.MOVIE) item.imdbId else "${item.imdbId}:$season:$episode"
            for (addonBase in customStremioAddons) {
                val cleanBase = addonBase.trim().removeSuffix("/manifest.json").removeSuffix("/")
                if (cleanBase.isBlank()) continue
                servers.addAll(fetchStremioStreams(cleanBase, type, streamId))
            }
        }

        // 2. Prioritized Working Servers (VidLink / Embed.su / AutoEmbed first; VidSrc.cc last fallback)
        if (item.category == MediaCategory.ANIME && item.aniListId != null) {
            val aid = item.aniListId
            servers.addAll(
                listOf(
                    StreamServer(
                        id = "anime_vidlink_sub",
                        name = "Server 1 — VidLink Anime (Sub)",
                        providerTag = "Fast CDN • Auto-Play",
                        url = "https://vidlink.pro/anime/$aid/$episode/sub",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://vidlink.pro/"
                    ),
                    StreamServer(
                        id = "anime_autoembed_sub",
                        name = "Server 2 — AutoEmbed Anime",
                        providerTag = "Stabil • Multi Mirror",
                        url = "https://player.autoembed.cc/embed/anime/$aid/$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://player.autoembed.cc/"
                    ),
                    StreamServer(
                        id = "anime_vidlink_dub",
                        name = "Server 3 — VidLink Anime (Dub)",
                        providerTag = "English Dub",
                        url = "https://vidlink.pro/anime/$aid/$episode/dub",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://vidlink.pro/",
                        isDub = true
                    ),
                    StreamServer(
                        id = "anime_vidsrc_sub",
                        name = "Server 4 — VidSrc Anime (Cadangan)",
                        providerTag = "Mirror Cadangan",
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
                        id = "vidlink_pro",
                        name = "Server 1 — VidLink Fast (Utama)",
                        providerTag = "VidLink.pro • Sub Indo",
                        url = if (isMovie) "https://vidlink.pro/movie/$id"
                        else "https://vidlink.pro/tv/$id/$season/$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://vidlink.pro/"
                    ),
                    StreamServer(
                        id = "embed_su",
                        name = "Server 2 — Embed.su HD",
                        providerTag = "Multi Source • Stabil",
                        url = if (isMovie) "https://embed.su/embed/movie/$id"
                        else "https://embed.su/embed/tv/$id/$season/$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://embed.su/"
                    ),
                    StreamServer(
                        id = "autoembed",
                        name = "Server 3 — AutoEmbed",
                        providerTag = "AutoEmbed • Cepat",
                        url = if (isMovie) "https://player.autoembed.cc/embed/movie/$id"
                        else "https://player.autoembed.cc/embed/tv/$id/$season/$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://player.autoembed.cc/"
                    ),
                    StreamServer(
                        id = "vidsrc_xyz",
                        name = "Server 4 — VidSrc.xyz",
                        providerTag = "VidSrc Mirror • AdBlock",
                        url = if (isMovie) "https://vidsrc.xyz/embed/movie/$id"
                        else "https://vidsrc.xyz/embed/tv/$id/$season-$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://vidsrc.xyz/"
                    ),
                    StreamServer(
                        id = "multiembed",
                        name = "Server 5 — SuperEmbed",
                        providerTag = "MultiEmbed • VIP Free",
                        url = if (isMovie) "https://multiembed.mov/?video_id=$id"
                        else "https://multiembed.mov/?video_id=$id&s=$season&e=$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "HD",
                        referer = "https://multiembed.mov/"
                    ),
                    StreamServer(
                        id = "smashystream",
                        name = "Server 6 — SmashyStream",
                        providerTag = "Global CDN",
                        url = if (isMovie) "https://player.smashy.stream/movie/$id"
                        else "https://player.smashy.stream/tv/$id?s=$season&e=$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "HD",
                        referer = "https://player.smashy.stream/"
                    ),
                    StreamServer(
                        id = "vidsrc_cc",
                        name = "Server 7 — VidSrc.cc (Cadangan)",
                        providerTag = "Cadangan Terakhir",
                        url = if (isMovie) "https://vidsrc.cc/v2/embed/movie/$id"
                        else "https://vidsrc.cc/v2/embed/tv/$id/$season/$episode",
                        kind = StreamKind.EMBED_WEBVIEW,
                        quality = "1080p",
                        referer = "https://vidsrc.cc/"
                    )
                )
            )
        }
        servers
    }

    private fun fetchStremioStreams(baseUrl: String, type: String, streamId: String): List<StreamServer> {
        return try {
            val req = Request.Builder()
                .url("$baseUrl/stream/$type/$streamId.json")
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val text = resp.body?.string() ?: return emptyList()
                val arr = JSONObject(text).optJSONArray("streams") ?: return emptyList()
                val out = mutableListOf<StreamServer>()
                for (i in 0 until arr.length().coerceAtMost(8)) {
                    val s = arr.optJSONObject(i) ?: continue
                    val directUrl = s.optString("url", "")
                    if (directUrl.startsWith("http")) {
                        val title = s.optString("title", s.optString("name", "Stremio Stream ${i + 1}"))
                            .replace("\n", " • ")
                            .take(48)
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
