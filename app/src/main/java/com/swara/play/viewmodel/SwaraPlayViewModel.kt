package com.swara.play.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swara.play.data.local.WatchStorage
import com.swara.play.data.network.HttpClientProvider
import com.swara.play.data.repository.SwaraPlayRepository
import com.swara.play.data.updater.GitHubReleaseUpdater
import com.swara.play.model.AppReleaseInfo
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaDetail
import com.swara.play.model.MediaItem
import com.swara.play.model.PerformanceMode
import com.swara.play.model.StreamServer
import com.swara.play.model.SubtitleTrack
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
    val errorMessage: String? = null
)

data class PlayerUiState(
    val mediaItem: MediaItem? = null,
    val season: Int = 1,
    val episode: Int = 1,
    val servers: List<StreamServer> = emptyList(),
    val activeServer: StreamServer? = null,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val activeSubtitle: SubtitleTrack? = null,
    val startPositionMs: Long = 0L,
    val isLoading: Boolean = false
)

class SwaraPlayViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SwaraPlayRepository()
    private val storage = WatchStorage(application.applicationContext)
    private val updater = GitHubReleaseUpdater()

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

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

    private val _updateStatusText = MutableStateFlow<String?>(null)
    val updateStatusText: StateFlow<String?> = _updateStatusText.asStateFlow()

    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

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
            storage.dohEnabledFlow.collect { enabled ->
                HttpClientProvider.dohEnabled = enabled
            }
        }
        refreshHome()
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
                    animeTopRated = bundle.animeTopRated
                )
            } catch (_: Exception) {
                _homeState.value = _homeState.value.copy(
                    isLoading = false,
                    errorMessage = "Gagal memuat katalog. Coba aktifkan DNS-over-HTTPS di Profil."
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
                _searchResults.value = emptyList()
                return@launch
            }
            _isSearching.value = true
            _searchResults.value = repository.searchAll(query, category)
            _isSearching.value = false
        }
    }

    fun browseByGenre(genre: String) {
        viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = repository.browseGenre(genre)
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

    fun preparePlayback(item: MediaItem, season: Int = 1, episode: Int = 1) {
        val savedResume = resumeList.value.firstOrNull {
            it.mediaId == item.id && it.season == season && it.episode == episode
        }
        _playerState.value = PlayerUiState(
            mediaItem = item,
            season = season,
            episode = episode,
            startPositionMs = savedResume?.positionMs ?: 0L,
            isLoading = true
        )
        viewModelScope.launch {
            storage.recordHistory(item)
            storage.saveWatchProgress(
                WatchProgress(
                    mediaId = item.id,
                    title = item.title,
                    posterUrl = item.posterUrl,
                    category = item.category,
                    imdbId = item.imdbId,
                    aniListId = item.aniListId,
                    season = season,
                    episode = episode,
                    positionMs = savedResume?.positionMs ?: 0L,
                    durationMs = savedResume?.durationMs ?: 0L
                )
            )
            val servers = repository.getServers(item, season, episode, customAddons.value)
            val subs = repository.getSubtitles(item, season, episode)
            val preferredSub = subs.firstOrNull { it.languageCode == "id" || it.languageCode == "ind" }
                ?: subs.firstOrNull()
            _playerState.value = _playerState.value.copy(
                servers = servers,
                activeServer = servers.firstOrNull(),
                subtitles = subs,
                activeSubtitle = preferredSub,
                isLoading = false
            )
        }
    }

    fun selectServer(server: StreamServer) {
        _playerState.value = _playerState.value.copy(activeServer = server)
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
                    mediaId = item.id,
                    title = item.title,
                    posterUrl = item.posterUrl,
                    category = item.category,
                    imdbId = item.imdbId,
                    aniListId = item.aniListId,
                    season = current.season,
                    episode = current.episode,
                    positionMs = positionMs,
                    durationMs = durationMs
                )
            )
        }
    }

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
            if (!silent) {
                _updateStatusText.value = if (rel != null) {
                    "Versi baru tersedia: ${rel.tagName}"
                } else {
                    "SwaraPlay sudah menggunakan versi terbaru."
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _availableUpdate.value = null
    }

    fun downloadAndInstallUpdate() {
        val rel = _availableUpdate.value ?: return
        viewModelScope.launch {
            _isDownloadingUpdate.value = true
            _downloadProgress.value = 0f
            val file = updater.downloadApk(getApplication(), rel) { p -> _downloadProgress.value = p }
            _isDownloadingUpdate.value = false
            if (file != null) {
                updater.launchInstaller(getApplication(), file)
                _availableUpdate.value = null
            } else {
                _updateStatusText.value = "Gagal mengunduh APK pembaruan."
            }
        }
    }
}
