package com.swara.play.data.provider

import com.swara.play.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * SwaraPlay v9.6.0 Stable — Pure New Direct Stream Resolver (Movies, Series, Asian Drama):
 * Uses ONLY the new EncDecEndpoints ecosystem (Cinejoy api.shegu.st).
 * All legacy third-party providers have been completely removed.
 */
object DirectMultiSourceResolver {
    suspend fun resolveDirectStream(item: MediaItem, season: Int, episode: Int): DirectStreamResult? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(6500L) {
            EncDecStreamResolver.resolveViaCinejoy(item, season, episode)
        }
    }
}
