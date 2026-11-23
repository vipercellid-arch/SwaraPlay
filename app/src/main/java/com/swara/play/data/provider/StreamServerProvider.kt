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

        // 1. Custom Stremio Addon Direct Streams (ExoPlayer)
        if (item.imdbId.isNotBlank() && customStremioAddons.isNotEmpty()) {
            val type = if (item.category == MediaCategory.MOVIE) "movie" else "series"
            val streamId = if (item.category == MediaCategory.MOVIE) item.imdbId else "${item.imdbId}:$season:$episode"
            for (addonBase in customStremioAddons) {
                val cleanBase = addonBase.trim().removeSuffix("/manifest.json").removeSuffix("/")
                if (cleanBase.isBlank()) continue
                val resolved = fetchStremioStreams(cleanBase, type, streamId)
                servers.addAll(resolved)
            }
        }

        // 2. Multi-Server AdBlock Embeds (inspired by Streambert, MovieBox-TUI & Zenshin)
        if (item.category == MediaCategory.ANIME && item.aniListId != null) {
            val aid = item.aniListId
            servers.add(
                StreamServer(
                    id = "anime_vidsrc_sub",
                    name = "SwaraAnime Sub (Utama)",
                    providerTag = "AniList • AdBlock",
                    url = "https://vidsrc.cc/v2/embed/anime/ani$aid/$episode/sub",
                    kind = StreamKind.EMBED_WEBVIEW,
                    quality = "1080p",
                    referer = "https://vidsrc.cc/"
                )
            )
            servers.add(
                StreamServer(
                    id = "anime_vidsrc_dub",
                    name = "SwaraAnime Dub",
                    providerTag = "AniList • AdBlock",
                    url = "https://vidsrc.cc/v2/embed/anime/ani$aid/$episode/dub",
                    kind = StreamKind.EMBED_WEBVIEW,
                    quality = "1080p",
                    referer = "https://vidsrc.cc/"
                )
            )
            servers.add(
                StreamServer(
                    id = "anime_autoembed",
                    name = "AutoEmbed Anime",
                    providerTag = "Mirror 2",
                    url = "https://player.autoembed.cc/embed/anime/$aid/$episode",
                    kind = StreamKind.EMBED_WEBVIEW,
                    quality = "HD",
                    referer = "https://player.autoembed.cc/"
                )
            )
        } else {
            val id = item.imdbId.ifBlank { item.id }
            val isMovie = item.category == MediaCategory.MOVIE
            servers.add(
                StreamServer(
                    id = "vidsrc_cc",
                    name = "Server 1 — Cinema HD",
                    providerTag = "VidSrc • AdBlock",
                    url = if (isMovie) "https://vidsrc.cc/v2/embed/movie/$id"
                    else "https://vidsrc.cc/v2/embed/tv/$id/$season/$episode",
                    kind = StreamKind.EMBED_WEBVIEW,
                    quality = "1080p",
                    referer = "https://vidsrc.cc/"
                )
            )
            servers.add(
                StreamServer(
                    id = "vidsrc_me",
                    name = "Server 2 — Fast Mirror",
                    providerTag = "VidSrc.xyz • AdBlock",
                    url = if (isMovie) "https://vidsrc.xyz/embed/movie/$id"
                    else "https://vidsrc.xyz/embed/tv/$id/$season-$episode",
                    kind = StreamKind.EMBED_WEBVIEW,
                    quality = "1080p",
                    referer = "https://vidsrc.xyz/"
                )
            )
            servers.add(
                StreamServer(
                    id = "superembed",
                    name = "Server 3 — MultiStream",
                    providerTag = "MultiEmbed",
                    url = if (isMovie) "https://multiembed.mov/?video_id=$id"
                    else "https://multiembed.mov/?video_id=$id&s=$season&e=$episode",
                    kind = StreamKind.EMBED_WEBVIEW,
                    quality = "HD",
                    referer = "https://multiembed.mov/"
                )
            )
            servers.add(
                StreamServer(
                    id = "autoembed",
                    name = "Server 4 — Cadangan",
                    providerTag = "AutoEmbed",
                    url = if (isMovie) "https://player.autoembed.cc/embed/movie/$id"
                    else "https://player.autoembed.cc/embed/tv/$id/$season/$episode",
                    kind = StreamKind.EMBED_WEBVIEW,
                    quality = "HD",
                    referer = "https://player.autoembed.cc/"
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
                                providerTag = "Stremio • ExoPlayer",
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
