package com.swara.play.data.provider

import com.swara.play.data.network.HttpClientProvider
import com.swara.play.model.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray

class WyzieSubtitleProvider {
    private val baseUrl = "https://sub.wyzie.io/search"

    suspend fun fetchSubtitles(
        imdbId: String,
        season: Int? = null,
        episode: Int? = null
    ): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        if (imdbId.isBlank()) return@withContext emptyList()
        val url = buildString {
            append(baseUrl)
            append("?id=")
            append(imdbId)
            if (season != null && episode != null && season > 0 && episode > 0) {
                append("&season=").append(season)
                append("&episode=").append(episode)
            }
        }
        try {
            val req = Request.Builder().url(url).header("User-Agent", HttpClientProvider.USER_AGENT).build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val raw = resp.body?.string() ?: return@withContext emptyList()
                val arr = JSONArray(raw)
                val tracks = mutableListOf<SubtitleTrack>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val subUrl = obj.optString("url", "")
                    if (subUrl.isBlank()) continue
                    val lang = obj.optString("language", "en").lowercase()
                    val display = obj.optString("display", lang.uppercase())
                    val format = obj.optString("format", "srt")
                    tracks.add(
                        SubtitleTrack(
                            id = obj.optString("id", "wyzie_$i"),
                            languageCode = lang,
                            languageLabel = normalizeLanguage(lang, display),
                            url = subUrl,
                            format = format,
                            source = "Wyzie"
                        )
                    )
                }
                tracks.sortedWith(
                    compareBy<SubtitleTrack> {
                        when (it.languageCode) {
                            "id", "ind" -> 0
                            "ms", "may" -> 1
                            "en", "eng" -> 2
                            else -> 3
                        }
                    }.thenBy { it.languageLabel }
                ).take(25)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalizeLanguage(code: String, fallback: String): String {
        return when (code.lowercase()) {
            "id", "ind" -> "Bahasa Indonesia"
            "en", "eng" -> "English"
            "ms", "may" -> "Bahasa Melayu"
            "ja", "jpn" -> "Japanese"
            "ko", "kor" -> "Korean"
            else -> fallback.ifBlank { code.uppercase() }
        }
    }
}
