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
    INDONESIAN("id", "Bahasa Indonesia");

    fun resolveIsoCode(): String = "id"
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
    val fontSizeScale: Float = 1.35f,
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
    val isLgbt: Boolean = false,
    val logoUrl: String = ""
)

data class EpisodeItem(
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val overview: String = "",
    val thumbnailUrl: String = "",
    val released: String = "",
    val runtime: String = ""
)

data class MediaDetail(
    val item: MediaItem,
    val cast: List<CastMember> = emptyList(),
    val runtime: String = "",
    val trailerYoutubeId: String = "",
    val episodes: List<EpisodeItem> = emptyList(),
    val similar: List<MediaItem> = emptyList(),
    val seasonNames: Map<Int, String> = emptyMap()
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


enum class AppThemeId(val key: String, val label: String) {
    DEFAULT("default", "Default (Cinejoy Hijau)"),
    OLED("oled", "OLED Hitam Pekat"),
    MIDNIGHT("midnight", "Midnight Biru"),
    AMETHYST("amethyst", "Amethyst Ungu"),
    EMERALD("emerald", "Emerald Zamrud");

    companion object {
        fun fromKey(k: String): AppThemeId = entries.firstOrNull { it.key == k } ?: DEFAULT
    }
}

data class AppUiPrefs(
    val themeId: AppThemeId = AppThemeId.DEFAULT,
    val episodeGridMode: Boolean = false,
    val useImageLogos: Boolean = true,
    val showTrailers: Boolean = true,
    val spoilerFreeMode: Boolean = false,
    val autoplayNext: Boolean = true,
    val seekSeconds: Int = 10,
    val accountKey: String = "",
    val recentSearches: List<String> = emptyList()
)

data class StreamingProviderInfo(
    val id: String,
    val name: String,
    val shortLabel: String,
    val accentHex: Long,
    val movieGenre: String,
    val seriesGenre: String
)

val SWARA_PROVIDERS = listOf(
    StreamingProviderInfo("netflix", "Netflix", "N", 0xFFE50914L, "Thriller", "Drama"),
    StreamingProviderInfo("prime", "Amazon Prime Video", "prime", 0xFF00A8E1L, "Action", "Action"),
    StreamingProviderInfo("disney", "Disney+", "Disney+", 0xFF113CCFL, "Animation", "Sci-Fi"),
    StreamingProviderInfo("appletvplus", "Apple TV+", "tv+", 0xFFF5F5F7L, "Sci-Fi", "Sci-Fi"),
    StreamingProviderInfo("appletv", "Apple TV", "tv", 0xFFD4D4D8L, "Drama", "Crime"),
    StreamingProviderInfo("hulu", "Hulu", "hulu", 0xFF1CE783L, "Comedy", "Comedy"),
    StreamingProviderInfo("hbomax", "HBO Max", "max", 0xFF7B2CBFL, "Fantasy", "Crime"),
    StreamingProviderInfo("paramount", "Paramount+", "P+", 0xFF0064FFL, "Adventure", "Action"),
    StreamingProviderInfo("peacock", "Peacock", "peacock", 0xFFFACC15L, "Comedy", "Mystery"),
    StreamingProviderInfo("crunchyroll", "Crunchyroll", "CR", 0xFFF47521L, "Animation", "Animation"),
    StreamingProviderInfo("starz", "Starz", "STARZ", 0xFF38BDF8L, "Crime", "Drama"),
    StreamingProviderInfo("amc", "AMC+", "AMC+", 0xFF22C55EL, "Horror", "Thriller"),
    StreamingProviderInfo("mgm", "MGM+", "MGM+", 0xFFEAB308L, "Action", "Sci-Fi"),
    StreamingProviderInfo("ytpremium", "YouTube Premium", "YT+", 0xFFFF0000L, "Documentary", "Comedy"),
    StreamingProviderInfo("youtube", "YouTube", "YT", 0xFFEF4444L, "Family", "Documentary"),
    StreamingProviderInfo("tubi", "Tubi TV", "tubi", 0xFFFA382FL, "Horror", "Mystery"),
    StreamingProviderInfo("pluto", "Pluto TV", "pluto", 0xFFFFF200L, "Western", "Crime")
)
