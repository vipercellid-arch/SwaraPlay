package com.swara.play.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.PerformanceMode
import com.swara.play.model.UserProfile
import com.swara.play.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.swaraPlayDataStore by preferencesDataStore(name = "swaraplay_v4_prefs")

class WatchStorage(private val context: Context) {

    private val keyFavorites = stringPreferencesKey("favorites_json")
    private val keyHistory = stringPreferencesKey("history_json")
    private val keyResume = stringPreferencesKey("resume_json")
    private val keyAddons = stringPreferencesKey("stremio_addons_json")
    private val keyAdBlock = booleanPreferencesKey("adblock_enabled")
    private val keyDoh = booleanPreferencesKey("doh_enabled")
    private val keyPerfMode = stringPreferencesKey("performance_mode")
    private val keyProfileName = stringPreferencesKey("profile_name")
    private val keyProfilePhoto = stringPreferencesKey("profile_photo_uri")
    private val keyJoinedDate = stringPreferencesKey("profile_joined_date")
    private val keyWatchMinutes = intPreferencesKey("total_watch_minutes")

    private fun todayJoinedString(): String {
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
        return "Bergabung ${fmt.format(Date())}"
    }

    val userProfileFlow: Flow<UserProfile> = context.swaraPlayDataStore.data.map { prefs ->
        UserProfile(
            displayName = prefs[keyProfileName]?.takeIf { it.isNotBlank() } ?: "Teves",
            photoUri = prefs[keyProfilePhoto].orEmpty(),
            joinedDate = prefs[keyJoinedDate]?.takeIf { it.isNotBlank() } ?: todayJoinedString()
        )
    }

    val watchMinutesFlow: Flow<Int> = context.swaraPlayDataStore.data.map { prefs -> prefs[keyWatchMinutes] ?: 0 }
    val favoritesFlow: Flow<List<MediaItem>> = context.swaraPlayDataStore.data.map { prefs -> decodeMediaList(prefs[keyFavorites].orEmpty()) }
    val historyFlow: Flow<List<MediaItem>> = context.swaraPlayDataStore.data.map { prefs -> decodeMediaList(prefs[keyHistory].orEmpty()) }
    val resumeFlow: Flow<List<WatchProgress>> = context.swaraPlayDataStore.data.map { prefs -> decodeProgressList(prefs[keyResume].orEmpty()) }
    val addonsFlow: Flow<List<String>> = context.swaraPlayDataStore.data.map { prefs -> decodeStringList(prefs[keyAddons].orEmpty()) }
    val adBlockEnabledFlow: Flow<Boolean> = context.swaraPlayDataStore.data.map { prefs -> prefs[keyAdBlock] ?: true }
    val dohEnabledFlow: Flow<Boolean> = context.swaraPlayDataStore.data.map { prefs -> prefs[keyDoh] ?: true }
    val performanceModeFlow: Flow<PerformanceMode> = context.swaraPlayDataStore.data.map { prefs ->
        runCatching { PerformanceMode.valueOf(prefs[keyPerfMode] ?: "AUTO") }.getOrDefault(PerformanceMode.AUTO)
    }

    suspend fun saveUserProfile(displayName: String, photoUri: String?) {
        context.swaraPlayDataStore.edit { prefs ->
            prefs[keyProfileName] = displayName.trim().ifBlank { "Teves" }
            if (photoUri != null) prefs[keyProfilePhoto] = photoUri
            if (prefs[keyJoinedDate].isNullOrBlank()) prefs[keyJoinedDate] = todayJoinedString()
        }
    }

    suspend fun addWatchMinute(minutes: Int = 1) {
        context.swaraPlayDataStore.edit { prefs ->
            prefs[keyWatchMinutes] = (prefs[keyWatchMinutes] ?: 0) + minutes
        }
    }

    suspend fun toggleFavorite(item: MediaItem) {
        context.swaraPlayDataStore.edit { prefs ->
            val current = decodeMediaList(prefs[keyFavorites].orEmpty()).toMutableList()
            val idx = current.indexOfFirst { it.id == item.id }
            if (idx >= 0) current.removeAt(idx) else current.add(0, item)
            prefs[keyFavorites] = encodeMediaList(current.take(120))
        }
    }

    suspend fun recordHistory(item: MediaItem) {
        context.swaraPlayDataStore.edit { prefs ->
            val current = decodeMediaList(prefs[keyHistory].orEmpty()).toMutableList()
            current.removeAll { it.id == item.id }
            current.add(0, item)
            prefs[keyHistory] = encodeMediaList(current.take(80))
        }
    }

    suspend fun saveWatchProgress(progress: WatchProgress) {
        context.swaraPlayDataStore.edit { prefs ->
            val current = decodeProgressList(prefs[keyResume].orEmpty()).toMutableList()
            current.removeAll { it.mediaId == progress.mediaId }
            current.add(0, progress.copy(updatedAt = System.currentTimeMillis()))
            prefs[keyResume] = encodeProgressList(current.take(40))
        }
    }

    suspend fun clearHistory() { context.swaraPlayDataStore.edit { it[keyHistory] = "[]" } }
    suspend fun setAdBlockEnabled(enabled: Boolean) { context.swaraPlayDataStore.edit { it[keyAdBlock] = enabled } }
    suspend fun setDohEnabled(enabled: Boolean) { context.swaraPlayDataStore.edit { it[keyDoh] = enabled } }
    suspend fun setPerformanceMode(mode: PerformanceMode) { context.swaraPlayDataStore.edit { it[keyPerfMode] = mode.name } }

    suspend fun addStremioAddon(url: String) {
        val clean = url.trim()
        if (clean.isBlank()) return
        context.swaraPlayDataStore.edit { prefs ->
            val current = decodeStringList(prefs[keyAddons].orEmpty()).toMutableList()
            if (!current.contains(clean)) current.add(clean)
            prefs[keyAddons] = encodeStringList(current)
        }
    }

    suspend fun removeStremioAddon(url: String) {
        context.swaraPlayDataStore.edit { prefs ->
            val current = decodeStringList(prefs[keyAddons].orEmpty()).toMutableList()
            current.removeAll { it == url }
            prefs[keyAddons] = encodeStringList(current)
        }
    }

    private fun encodeMediaList(items: List<MediaItem>): String {
        val arr = JSONArray()
        for (item in items) {
            arr.put(
                JSONObject()
                    .put("id", item.id).put("title", item.title).put("category", item.category.name)
                    .put("posterUrl", item.posterUrl).put("backdropUrl", item.backdropUrl)
                    .put("overview", item.overview).put("year", item.year).put("rating", item.rating)
                    .put("imdbId", item.imdbId).put("aniListId", item.aniListId ?: -1).put("totalEpisodes", item.totalEpisodes)
            )
        }
        return arr.toString()
    }

    private fun decodeMediaList(raw: String): List<MediaItem> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<MediaItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val cat = runCatching { MediaCategory.valueOf(o.optString("category", "MOVIE")) }.getOrDefault(MediaCategory.MOVIE)
                val aniId = o.optInt("aniListId", -1).let { if (it > 0) it else null }
                list.add(
                    MediaItem(
                        id = o.optString("id", ""), title = o.optString("title", ""), category = cat,
                        posterUrl = o.optString("posterUrl", ""), backdropUrl = o.optString("backdropUrl", ""),
                        overview = o.optString("overview", ""), year = o.optString("year", ""),
                        rating = o.optString("rating", ""), imdbId = o.optString("imdbId", ""),
                        aniListId = aniId, totalEpisodes = o.optInt("totalEpisodes", 0)
                    )
                )
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    private fun encodeProgressList(items: List<WatchProgress>): String {
        val arr = JSONArray()
        for (p in items) {
            arr.put(
                JSONObject()
                    .put("mediaId", p.mediaId).put("title", p.title).put("posterUrl", p.posterUrl)
                    .put("category", p.category.name).put("imdbId", p.imdbId).put("aniListId", p.aniListId ?: -1)
                    .put("season", p.season).put("episode", p.episode).put("positionMs", p.positionMs)
                    .put("durationMs", p.durationMs).put("updatedAt", p.updatedAt)
            )
        }
        return arr.toString()
    }

    private fun decodeProgressList(raw: String): List<WatchProgress> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<WatchProgress>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val cat = runCatching { MediaCategory.valueOf(o.optString("category", "MOVIE")) }.getOrDefault(MediaCategory.MOVIE)
                val aniId = o.optInt("aniListId", -1).let { if (it > 0) it else null }
                list.add(
                    WatchProgress(
                        mediaId = o.optString("mediaId", ""), title = o.optString("title", ""),
                        posterUrl = o.optString("posterUrl", ""), category = cat,
                        imdbId = o.optString("imdbId", ""), aniListId = aniId,
                        season = o.optInt("season", 1), episode = o.optInt("episode", 1),
                        positionMs = o.optLong("positionMs", 0L), durationMs = o.optLong("durationMs", 0L),
                        updatedAt = o.optLong("updatedAt", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    private fun encodeStringList(items: List<String>): String = JSONArray().apply { items.forEach { put(it) } }.toString()
    private fun decodeStringList(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optString(it, "").takeIf { s -> s.isNotBlank() } }
        } catch (_: Exception) { emptyList() }
    }
}
