package com.swara.play.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swara.play.data.local.WatchStorage
import com.swara.play.data.network.HttpClientProvider
import com.swara.play.data.repository.SwaraPlayRepository
import com.swara.play.data.updater.GitHubReleaseUpdater
import com.swara.play.model.AppReleaseInfo
import com.swara.play.model.EpisodeItem
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaDetail
import com.swara.play.model.MediaItem
import com.swara.play.model.PerformanceMode
import com.swara.play.model.StreamServer
import com.swara.play.model.SubtitleTrack
import com.swara.play.model.UserProfile
import com.swara.play.model.WatchProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val selectedCategory: MediaCategory = MediaCategory.ALL,
    val movies: List<MediaItem> = emptyList(),
    val series: List<MediaItem> = emptyList(),
    val asianDramas: List<MediaItem> = emptyList(),
    val animeTrending: List<MediaItem> = emptyList(),
    val animeTopRated: List<MediaItem> = emptyList(),
    val actionMovies: List<MediaItem> = emptyList(),
    val horrorMovies: List<MediaItem> = emptyList(),
    val errorMessage: String? = null
)

data class PlayerUiState(
    val mediaItem: MediaItem? = null,
    val season: Int = 1,
    val episode: Int = 1,
    val episodes: List<EpisodeItem> = emptyList(),
    val servers: List<StreamServer> = emptyList(),
    val activeServer: StreamServer? = null,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val activeSubtitle: SubtitleTrack? = null,
    val startPositionMs: Long = 0L,
    val isLoading: Boolean = false,
    val fallbackNotice: String? = null
)

class SwaraPlayViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SwaraPlayRepository()
    private val storage = WatchStorage(application.applicationContext)
    private val updater = GitHubReleaseUpdater()

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _exploreResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val exploreResults: StateFlow<List<MediaItem>> = _exploreResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _selectedDetail = MutableStateFlow<MediaDetail?>(null)
    val selectedDetail: StateFlow<MediaDetail?> = _selectedDetail.asStateFlow()

    private val _isLoadingDetail = MutableStateFlow(false)
    val isLoadingDetail: StateFlow<Boolean> = _isLoadingDetail.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    private val _availableUpdate = MutableStateFlow<AppReleaseInfo?>(null)
    val availableUpdate: StateFlow<AppReleaseInfo?> = _availableUpdate.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _updateStatusText = MutableStateFlow<String?>(null)
    val updateStatusText: StateFlow<String?> = _updateStatusText.asStateFlow()

    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    val userProfile: StateFlow<UserProfile> = storage.userProfileFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile()
    )
    val watchMinutes: StateFlow<Int> = storage.watchMinutesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0
    )
    val favorites: StateFlow<List<MediaItem>> = storage.favoritesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val history: StateFlow<List<MediaItem>> = storage.historyFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val resumeList: StateFlow<List<WatchProgress>> = storage.resumeFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val customAddons: StateFlow<List<String>> = storage.addonsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val adBlockEnabled: StateFlow<Boolean> = storage.adBlockEnabledFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )
    val dohEnabled: StateFlow<Boolean> = storage.dohEnabledFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )
    val performanceMode: StateFlow<PerformanceMode> = storage.performanceModeFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), PerformanceMode.AUTO
    )

    init {
        viewModelScope.launch {
            storage.dohEnabledFlow.collect { enabled -> HttpClientProvider.dohEnabled = enabled }
        }
        refreshHome()
        loadFilteredExplore(MediaCategory.ALL, "Semua Area", "Semua Kategori", 0)
        checkUpdates(silent = true)
    }

    fun refreshHome() {
        viewModelScope.launch {
            _homeState.value = _homeState.value.copy(isLoading = true, errorMessage = null)
            try {
                val bundle = repository.loadHomeCatalog()
                _homeState.value = _homeState.value.copy(
                    isLoading = false,
                    movies = bundle.movies,
                    series = bundle.series,
                    asianDramas = bundle.asianDramas,
                    animeTrending = bundle.animeTrending,
                    animeTopRated = bundle.animeTopRated,
                    actionMovies = bundle.actionMovies,
                    horrorMovies = bundle.horrorMovies
                )
            } catch (_: Exception) {
                _homeState.value = _homeState.value.copy(
                    isLoading = false,
                    errorMessage = "Gagal memuat katalog. Periksa koneksi atau aktifkan DoH di Pengaturan."
                )
            }
        }
    }

    fun selectHomeCategory(category: MediaCategory) {
        _homeState.value = _homeState.value.copy(selectedCategory = category)
    }

    fun search(query: String, category: MediaCategory) {
        viewModelScope.launch {
            if (query.isBlank()) {
                loadFilteredExplore(category, "Semua Area", "Semua Kategori", 0)
                return@launch
            }
            _isSearching.value = true
            _exploreResults.value = repository.searchAll(query, category)
            _isSearching.value = false
        }
    }

    fun loadFilteredExplore(category: MediaCategory, area: String, genre: String, sortIndex: Int) {
        viewModelScope.launch {
            _isSearching.value = true
            val raw = repository.browseFiltered(category, area, genre)
            _exploreResults.value = when (sortIndex) {
                1 -> raw.sortedByDescending { it.year }
                2 -> raw.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
                else -> raw
            }
            _isSearching.value = false
        }
    }

    fun openDetail(item: MediaItem) {
        _selectedDetail.value = MediaDetail(item = item)
        viewModelScope.launch {
            _isLoadingDetail.value = true
            _selectedDetail.value = repository.getMediaDetail(item)
            _isLoadingDetail.value = false
        }
    }

    fun preparePlayback(item: MediaItem, season: Int = 1, episode: Int = 1, knownEpisodes: List<EpisodeItem> = emptyList()) {
        val savedResume = resumeList.value.firstOrNull {
            it.mediaId == item.id && it.season == season && it.episode == episode
        }
        val initialEpisodes = if (knownEpisodes.isNotEmpty()) knownEpisodes else _selectedDetail.value?.episodes.orEmpty()
        _playerState.value = PlayerUiState(
            mediaItem = item,
            season = season,
            episode = episode,
            episodes = initialEpisodes,
            startPositionMs = savedResume?.positionMs ?: 0L,
            isLoading = true,
            fallbackNotice = null
        )
        viewModelScope.launch {
            storage.recordHistory(item)
            storage.addWatchMinute(1)
            storage.saveWatchProgress(
                WatchProgress(
                    mediaId = item.id, title = item.title, posterUrl = item.posterUrl,
                    category = item.category, imdbId = item.imdbId, aniListId = item.aniListId,
                    season = season, episode = episode,
                    positionMs = savedResume?.positionMs ?: 0L, durationMs = savedResume?.durationMs ?: 0L
                )
            )
            val resolvedEps = if (initialEpisodes.isEmpty() && item.category != MediaCategory.MOVIE) {
                repository.getMediaDetail(item).episodes
            } else {
                initialEpisodes
            }
            val servers = repository.getServers(item, season, episode, customAddons.value)
            val subs = repository.getSubtitles(item, season, episode)
            val preferredSub = subs.firstOrNull { it.languageCode == "id" || it.languageCode == "ind" } ?: subs.firstOrNull()
            _playerState.value = _playerState.value.copy(
                episodes = resolvedEps,
                servers = servers,
                activeServer = servers.firstOrNull(),
                subtitles = subs,
                activeSubtitle = preferredSub,
                isLoading = false
            )
        }
    }

    fun selectServer(server: StreamServer) {
        _playerState.value = _playerState.value.copy(activeServer = server, fallbackNotice = null)
    }

    fun fallbackToNextServer() {
        val st = _playerState.value
        val idx = st.servers.indexOfFirst { it.id == st.activeServer?.id }
        if (idx >= 0 && idx + 1 < st.servers.size) {
            val next = st.servers[idx + 1]
            _playerState.value = st.copy(
                activeServer = next,
                fallbackNotice = "Server sebelumnya tidak merespons — beralih otomatis ke ${next.name}"
            )
        }
    }

    fun selectSubtitle(track: SubtitleTrack?) {
        _playerState.value = _playerState.value.copy(activeSubtitle = track)
    }

    fun updatePlaybackPosition(positionMs: Long, durationMs: Long) {
        val current = _playerState.value
        val item = current.mediaItem ?: return
        viewModelScope.launch {
            storage.saveWatchProgress(
                WatchProgress(
                    mediaId = item.id, title = item.title, posterUrl = item.posterUrl,
                    category = item.category, imdbId = item.imdbId, aniListId = item.aniListId,
                    season = current.season, episode = current.episode,
                    positionMs = positionMs, durationMs = durationMs
                )
            )
        }
    }

    fun saveUserProfile(name: String, photoUri: String?) = viewModelScope.launch { storage.saveUserProfile(name, photoUri) }
    fun toggleFavorite(item: MediaItem) = viewModelScope.launch { storage.toggleFavorite(item) }
    fun clearHistory() = viewModelScope.launch { storage.clearHistory() }
    fun setAdBlockEnabled(enabled: Boolean) = viewModelScope.launch { storage.setAdBlockEnabled(enabled) }
    fun setDohEnabled(enabled: Boolean) = viewModelScope.launch {
        HttpClientProvider.dohEnabled = enabled
        storage.setDohEnabled(enabled)
    }
    fun setPerformanceMode(mode: PerformanceMode) = viewModelScope.launch { storage.setPerformanceMode(mode) }
    fun addStremioAddon(url: String) = viewModelScope.launch { storage.addStremioAddon(url) }
    fun removeStremioAddon(url: String) = viewModelScope.launch { storage.removeStremioAddon(url) }

    fun checkUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _updateStatusText.value = "Memeriksa pembaruan di GitHub Releases..."
            val rel = updater.checkForUpdate()
            _availableUpdate.value = rel
            if (rel != null) {
                updater.showUpdateNotification(getApplication(), rel)
                if (!silent) _showUpdateDialog.value = true
                _updateStatusText.value = "Versi baru tersedia: ${rel.tagName}"
            } else if (!silent) {
                _updateStatusText.value = "SwaraPlay sudah menggunakan versi terbaru."
            }
        }
    }

    fun openUpdateDialog() { if (_availableUpdate.value != null) _showUpdateDialog.value = true }
    fun dismissUpdateDialog() { _showUpdateDialog.value = false }

    fun downloadAndInstallUpdate() {
        val rel = _availableUpdate.value ?: return
        viewModelScope.launch {
            _isDownloadingUpdate.value = true
            _downloadProgress.value = 0f
            val file = updater.downloadApk(getApplication(), rel) { p -> _downloadProgress.value = p }
            _isDownloadingUpdate.value = false
            if (file != null) {
                updater.launchInstaller(getApplication(), file)
                _showUpdateDialog.value = false
            } else {
                _updateStatusText.value = "Gagal mengunduh APK pembaruan."
            }
        }
    }
}
