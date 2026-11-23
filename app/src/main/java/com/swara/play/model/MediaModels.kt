package com.swara.play.model

enum class MediaCategory(val label: String) {
    ALL("Semua"),
    MOVIE("Film"),
    SERIES("Serial TV"),
    ASIAN_DRAMA("Drama Asia"),
    ANIME("Anime")
}

enum class PerformanceMode(val label: String, val desc: String) {
    AUTO("Otomatis", "Seimbang antara kelancaran & suhu perangkat"),
    ANTI_HEAT("Anti-Panas", "Hemat baterai & jaga suhu tetap dingin"),
    MAX("Maksimal", "Prioritas respons cepat & gambar tajam")
}

data class UserProfile(
    val displayName: String = "Teves",
    val photoUri: String = "",
    val joinedDate: String = "Bergabung 2026"
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
    val totalEpisodes: Int = 0
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
    val cast: List<String> = emptyList(),
    val runtime: String = "",
    val episodes: List<EpisodeItem> = emptyList()
)

data class WatchProgress(
    val mediaId: String,
    val title: String,
    val posterUrl: String,
    val category: MediaCategory,
    val imdbId: String,
    val aniListId: Int? = null,
    val season: Int = 1,
    val episode: Int = 1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}
