package com.swara.play.data.provider

import com.swara.play.model.MediaItem

data class DirectStreamResult(
    val providerId: String = "encdec",
    val providerName: String = "EncDec",
    val streamUrl: String,
    val referer: String = "",
    val origin: String = "",
    val subtitleVttUrl: String = "",
    val qualityLabel: String = "HD"
)

/**
 * SwaraPlay v10.0.0 — 100% Pure EncDecEndpoints Native Anime Resolver
 * (AnimeKai + MegaUp, MegaPlay, Nexa Anime).
 */
object AnimeDirectResolver {
    suspend fun resolveAllAnimeStreams(item: MediaItem, episode: Int): List<DirectStreamResult> {
        return EncDecStreamResolver.resolveAllAnimeStreams(item, episode)
    }
}
