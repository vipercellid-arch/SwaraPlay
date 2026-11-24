package com.swara.play.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swara.play.model.ConnectedDevice
import com.swara.play.model.ContentRestrictionPrefs
import com.swara.play.model.SubtitleStylePrefs
import com.swara.play.model.DeviceIntegrationPrefs
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.OfflineDownloadItem
import com.swara.play.model.SubtitleLanguagePref
import com.swara.play.model.UserProfile
import com.swara.play.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.swaraPlayDataStore by preferencesDataStore(name = "swaraplay_v6_prefs")

class WatchStorage(private val context: Context) {

    private val keyFavorites = stringPreferencesKey("favorites_json")
    private val keyHistory = stringPreferencesKey("history_json")
    private val keyResume = stringPreferencesKey("resume_json")
    private val keyDownloads = stringPreferencesKey("downloads_json")
    private val keyDoh = booleanPreferencesKey("doh_enabled")
    private val keySubLang = stringPreferencesKey("subtitle_lang_pref")
    private val keyProfileName = stringPreferencesKey("profile_name")
    private val keyProfilePhoto = stringPreferencesKey("profile_photo_uri")
    private val keyJoinedDate = stringPreferencesKey("profile_joined_date")
    private val keyWatchMinutes = intPreferencesKey("total_watch_minutes")
    private val keyPipEnabled = booleanPreferencesKey("pip_mini_player_enabled")
    private val keyDevBg = booleanPreferencesKey("dev_sync_bg")
    private val keyDevNotify = booleanPreferencesKey("dev_sync_notify")
    private val keyDevPlayer = booleanPreferencesKey("dev_sync_player")
    private val keySavedDevices = stringPreferencesKey("saved_devices_json")
    private val keyBlockAdult18 = booleanPreferencesKey("block_adult_18")
    private val keyBlockLgbt = booleanPreferencesKey("block_lgbt")
    private val keySubSize = floatPreferencesKey("sub_font_size")
    private val keySubPos = floatPreferencesKey("sub_bottom_pos")
    private val keySubColor = longPreferencesKey("sub_text_color")
    private val keySubBg = booleanPreferencesKey("sub_show_bg")

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
    val downloadsFlow: Flow<List<OfflineDownloadItem>> = context.swaraPlayDataStore.data.map { prefs -> decodeDownloadsList(prefs[keyDownloads].orEmpty()) }
    val dohEnabledFlow: Flow<Boolean> = context.swaraPlayDataStore.data.map { prefs -> prefs[keyDoh] ?: true }
    val pipEnabledFlow: Flow<Boolean> = context.swaraPlayDataStore.data.map { prefs -> prefs[keyPipEnabled] ?: true }
    val deviceIntegrationPrefsFlow: Flow<DeviceIntegrationPrefs> = context.swaraPlayDataStore.data.map { prefs ->
        DeviceIntegrationPrefs(
            keepConnectedBackground = prefs[keyDevBg] ?: false,
            notifyWhenTvSyncs = prefs[keyDevNotify] ?: true,
            showInPlayerMenu = prefs[keyDevPlayer] ?: true
        )
    }
    val savedDevicesFlow: Flow<List<ConnectedDevice>> = context.swaraPlayDataStore.data.map { prefs ->
        decodeDevices(prefs[keySavedDevices].orEmpty())
    }
    val contentRestrictionFlow: Flow<ContentRestrictionPrefs> = context.swaraPlayDataStore.data.map { prefs ->
        ContentRestrictionPrefs(
            blockAdult18 = prefs[keyBlockAdult18] ?: false,
            blockLgbt = prefs[keyBlockLgbt] ?: false
        )
    }
    val subtitleStyleFlow: Flow<SubtitleStylePrefs> = context.swaraPlayDataStore.data.map { prefs ->
        SubtitleStylePrefs(
            fontSizeScale = prefs[keySubSize] ?: 1.0f,
            bottomPosition = prefs[keySubPos] ?: 0.10f,
            textColorHex = prefs[keySubColor] ?: 0xFFFFFFFFL,
            showBackground = prefs[keySubBg] ?: true
        )
    }
    val subtitleLangFlow: Flow<SubtitleLanguagePref> = context.swaraPlayDataStore.data.map { prefs ->
        runCatching { SubtitleLanguagePref.valueOf(prefs[keySubLang] ?: "INDONESIAN") }.getOrDefault(SubtitleLanguagePref.INDONESIAN)
    }

    suspend fun saveUserProfile(displayName: String, photoUri: String?) {
        context.swaraPlayDataStore.edit { prefs ->
            prefs[keyProfileName] = displayName.trim().ifBlank { "Teves" }
            if (photoUri != null) prefs[keyProfilePhoto] = photoUri
            if (prefs[keyJoinedDate].isNullOrBlank()) prefs[keyJoinedDate] = todayJoinedString()
        }
    }

    suspend fun addWatchMinute(minutes: Int = 1) {
        context.swaraPlayDataStore.edit { prefs -> prefs[keyWatchMinutes] = (prefs[keyWatchMinutes] ?: 0) + minutes }
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

    suspend fun saveOfflineDownload(item: OfflineDownloadItem) {
        context.swaraPlayDataStore.edit { prefs ->
            val current = decodeDownloadsList(prefs[keyDownloads].orEmpty()).toMutableList()
            current.removeAll { it.id == item.id }
            current.add(0, item)
            prefs[keyDownloads] = encodeDownloadsList(current)
        }
    }

    suspend fun removeOfflineDownload(id: String) {
        context.swaraPlayDataStore.edit { prefs ->
            val current = decodeDownloadsList(prefs[keyDownloads].orEmpty()).toMutableList()
            current.removeAll { it.id == id }
            prefs[keyDownloads] = encodeDownloadsList(current)
        }
    }

    suspend fun clearHistoryAndResume() {
        context.swaraPlayDataStore.edit {
            it[keyHistory] = "[]"
            it[keyResume] = "[]"
        }
    }

    suspend fun setDohEnabled(enabled: Boolean) { context.swaraPlayDataStore.edit { it[keyDoh] = enabled } }
    suspend fun setPipEnabled(enabled: Boolean) { context.swaraPlayDataStore.edit { it[keyPipEnabled] = enabled } }
    suspend fun updateContentRestriction(prefs: ContentRestrictionPrefs) {
        context.swaraPlayDataStore.edit {
            it[keyBlockAdult18] = prefs.blockAdult18
            it[keyBlockLgbt] = prefs.blockLgbt
        }
    }
    suspend fun updateSubtitleStyle(style: SubtitleStylePrefs) {
        context.swaraPlayDataStore.edit {
            it[keySubSize] = style.fontSizeScale
            it[keySubPos] = style.bottomPosition
            it[keySubColor] = style.textColorHex
            it[keySubBg] = style.showBackground
        }
    }
    suspend fun updateDeviceIntegrationPrefs(prefs: DeviceIntegrationPrefs) {
        context.swaraPlayDataStore.edit {
            it[keyDevBg] = prefs.keepConnectedBackground
            it[keyDevNotify] = prefs.notifyWhenTvSyncs
            it[keyDevPlayer] = prefs.showInPlayerMenu
        }
    }
    suspend fun addSavedDevice(device: ConnectedDevice) {
        context.swaraPlayDataStore.edit { prefs ->
            val current = decodeDevices(prefs[keySavedDevices].orEmpty()).toMutableList()
            current.removeAll { it.id == device.id || it.host == device.host }
            current.add(0, device)
            val arr = JSONArray()
            for (d in current.take(10)) {
                arr.put(JSONObject().put("id", d.id).put("name", d.name).put("host", d.host).put("port", d.port).put("deviceType", d.deviceType))
            }
            prefs[keySavedDevices] = arr.toString()
        }
    }
    suspend fun removeSavedDevice(deviceId: String) {
        context.swaraPlayDataStore.edit { prefs ->
            val current = decodeDevices(prefs[keySavedDevices].orEmpty()).filterNot { it.id == deviceId }
            val arr = JSONArray()
            for (d in current) {
                arr.put(JSONObject().put("id", d.id).put("name", d.name).put("host", d.host).put("port", d.port).put("deviceType", d.deviceType))
            }
            prefs[keySavedDevices] = arr.toString()
        }
    }
    private fun decodeDevices(raw: String): List<ConnectedDevice> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ConnectedDevice>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(ConnectedDevice(o.optString("id"), o.optString("name"), o.optString("host"), o.optInt("port", 8765), o.optString("deviceType", "TV")))
            }
            list
        } catch (_: Exception) { emptyList() }
    }
    suspend fun setSubtitleLang(pref: SubtitleLanguagePref) { context.swaraPlayDataStore.edit { it[keySubLang] = pref.name } }

    private fun encodeMediaList(items: List<MediaItem>): String {
        val arr = JSONArray()
        for (item in items) {
            arr.put(
                JSONObject()
                    .put("id", item.id).put("title", item.title).put("category", item.category.name)
                    .put("posterUrl", item.posterUrl).put("backdropUrl", item.backdropUrl)
                    .put("overview", item.overview).put("year", item.year).put("rating", item.rating)
                    .put("imdbId", item.imdbId).put("aniListId", item.aniListId ?: -1)
                    .put("malId", item.malId ?: -1).put("trailerYoutubeId", item.trailerYoutubeId)
                    .put("totalEpisodes", item.totalEpisodes)
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
                list.add(
                    MediaItem(
                        id = o.optString("id", ""), title = o.optString("title", ""), category = cat,
                        posterUrl = o.optString("posterUrl", ""), backdropUrl = o.optString("backdropUrl", ""),
                        overview = o.optString("overview", ""), year = o.optString("year", ""),
                        rating = o.optString("rating", ""), imdbId = o.optString("imdbId", ""),
                        aniListId = o.optInt("aniListId", -1).let { if (it > 0) it else null },
                        malId = o.optInt("malId", -1).let { if (it > 0) it else null },
                        trailerYoutubeId = o.optString("trailerYoutubeId", ""),
                        totalEpisodes = o.optInt("totalEpisodes", 0)
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
                    .put("category", p.category.name).put("imdbId", p.imdbId)
                    .put("aniListId", p.aniListId ?: -1).put("malId", p.malId ?: -1)
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
                list.add(
                    WatchProgress(
                        mediaId = o.optString("mediaId", ""), title = o.optString("title", ""),
                        posterUrl = o.optString("posterUrl", ""), category = cat,
                        imdbId = o.optString("imdbId", ""),
                        aniListId = o.optInt("aniListId", -1).let { if (it > 0) it else null },
                        malId = o.optInt("malId", -1).let { if (it > 0) it else null },
                        season = o.optInt("season", 1), episode = o.optInt("episode", 1),
                        positionMs = o.optLong("positionMs", 0L), durationMs = o.optLong("durationMs", 0L),
                        updatedAt = o.optLong("updatedAt", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    private fun encodeDownloadsList(items: List<OfflineDownloadItem>): String {
        val arr = JSONArray()
        for (d in items) {
            arr.put(
                JSONObject()
                    .put("id", d.id).put("mediaId", d.mediaId).put("title", d.title)
                    .put("posterUrl", d.posterUrl).put("category", d.category.name)
                    .put("season", d.season).put("episode", d.episode)
                    .put("localVideoPath", d.localVideoPath).put("localSubtitlePath", d.localSubtitlePath)
                    .put("fileSizeBytes", d.fileSizeBytes).put("downloadedAt", d.downloadedAt)
            )
        }
        return arr.toString()
    }

    private fun decodeDownloadsList(raw: String): List<OfflineDownloadItem> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<OfflineDownloadItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val cat = runCatching { MediaCategory.valueOf(o.optString("category", "MOVIE")) }.getOrDefault(MediaCategory.MOVIE)
                list.add(
                    OfflineDownloadItem(
                        id = o.optString("id", ""), mediaId = o.optString("mediaId", ""),
                        title = o.optString("title", ""), posterUrl = o.optString("posterUrl", ""),
                        category = cat, season = o.optInt("season", 1), episode = o.optInt("episode", 1),
                        localVideoPath = o.optString("localVideoPath", ""),
                        localSubtitlePath = o.optString("localSubtitlePath", ""),
                        fileSizeBytes = o.optLong("fileSizeBytes", 0L),
                        downloadedAt = o.optLong("downloadedAt", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) { emptyList() }
    }
}
