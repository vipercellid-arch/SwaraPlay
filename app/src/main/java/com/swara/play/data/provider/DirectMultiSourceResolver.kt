package com.swara.play.data.provider

import com.swara.play.model.MediaItem

/**
 * SwaraPlay v10.0.0 — 100% Pure EncDecEndpoints Multi-Server Resolver
 * (VidLink, Cinejoy, Videasy, Nexa, MeowTV, yFlix+Rapidshare, Hexa, VidFast, VidCore, VidUp, KissKH).
 */
object DirectMultiSourceResolver {
    suspend fun resolveAllDirectStreams(item: MediaItem, season: Int, episode: Int): List<DirectStreamResult> {
        return EncDecStreamResolver.resolveAllMovieSeriesStreams(item, season, episode)
    }
}
