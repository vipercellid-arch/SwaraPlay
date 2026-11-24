package com.swara.play.model

import java.util.Locale

enum class MediaCategory(val label: String) {
    ALL("Semua"),
    MOVIE("Film"),
    SERIES("Serial TV"),
    ASIAN_DRAMA("Drama Asia"),
    ANIME("Anime")
}

enum class SubtitleLanguagePref(val code: String, val label: String) {
    DEVICE("auto", "Ikuti Bahasa Perangkat"),
    INDONESIAN("id", "Bahasa Indonesia"),
    ENGLISH("en", "English");

    fun resolveIsoCode(): String {
        if (this != DEVICE) return code
        val sys = Locale.getDefault().language.lowercase()
        return when (sys) {
            "in", "id" -> "id"
            "ms" -> "ms"
            else -> sys.ifBlank { "id" }
        }
    }
}

data class UserProfile(
    val displayName: String = "Teves",
    val photoUri: String = "",
    val joinedDate: String = "Bergabung 2026"
)

data class CastMember(
    val name: String,
    val photoUrl: String = "",
    val imdbPersonId: String = ""
)

data class ActorProfile(
    val name: String,
    val photoUrl: String = "",
    val subtitle: String = "",
    val biography: String = "",
    val filmography: List<MediaItem> = emptyList(),
    val albumPhotos: List<String> = emptyList()
)

data class ConnectedDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 8765,
    val deviceType: String = "TV"
)

data class DeviceIntegrationPrefs(
    val keepConnectedBackground: Boolean = false,
    val notifyWhenTvSyncs: Boolean = true,
    val showInPlayerMenu: Boolean = true
)

data class ContentRestrictionPrefs(
    val blockAdult18: Boolean = false,
    val blockLgbt: Boolean = false
)

data class SubtitleStylePrefs(
    val fontSizeScale: Float = 1.0f,
    val bottomPosition: Float = 0.10f,
    val textColorHex: Long = 0xFFFFFFFFL,
    val showBackground: Boolean = true
)

data class MediaItem(
    val id: String,
    val title: String,
    val category: MediaCategory,
    val posterUrl: String,
    val backdropUrl: String = "",
    val overview: String = "",
    val year: String = "",
    val rating: String = "",
    val genres: List<String> = emptyList(),
    val imdbId: String = "",
    val aniListId: Int? = null,
    val malId: Int? = null,
    val romajiTitle: String = "",
    val trailerYoutubeId: String = "",
    val totalEpisodes: Int = 0,
    val isAdult18: Boolean = false,
    val isLgbt: Boolean = false
)

data class EpisodeItem(
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val overview: String = "",
    val thumbnailUrl: String = "",
    val released: String = ""
)

data class MediaDetail(
    val item: MediaItem,
    val cast: List<CastMember> = emptyList(),
    val runtime: String = "",
    val trailerYoutubeId: String = "",
    val episodes: List<EpisodeItem> = emptyList(),
    val similar: List<MediaItem> = emptyList()
)

data class WatchProgress(
    val mediaId: String,
    val title: String,
    val posterUrl: String,
    val category: MediaCategory,
    val imdbId: String,
    val aniListId: Int? = null,
    val malId: Int? = null,
    val season: Int = 1,
    val episode: Int = 1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

data class OfflineDownloadItem(
    val id: String,
    val mediaId: String,
    val title: String,
    val posterUrl: String,
    val category: MediaCategory,
    val season: Int = 1,
    val episode: Int = 1,
    val localVideoPath: String,
    val localSubtitlePath: String = "",
    val fileSizeBytes: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis()
)
