package com.swara.play.data.provider

import com.swara.play.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class DirectStreamResult(
    val streamUrl: String,
    val referer: String = "",
    val subtitleVttUrl: String = "",
    val isWebEmbed: Boolean = false
)

/**
 * SwaraPlay v9.6.0 Stable — Pure New Anime Direct Resolver:
 * Uses ONLY the new EncDecEndpoints ecosystem (AnimeKai enc-dec.app/db/kai + enc-kai/dec-kai).
 */
object AnimeDirectResolver {
    suspend fun resolveDirectAnimeStream(item: MediaItem, episode: Int): DirectStreamResult? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(6500L) {
            EncDecStreamResolver.resolveViaAnimeKai(item, episode)
        }
    }
}
