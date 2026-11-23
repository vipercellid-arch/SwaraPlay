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
import com.swara.play.model.StreamSource
import com.swara.play.model.SubtitleLanguagePref
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
    val similar: List<MediaItem> = emptyList(),
    val sources: List<StreamSource> = emptyList(),
    val activeSourceIndex: Int = 0,
    val isLoading: Boolean = false
) {
    val activeSource: StreamSource?
        get() = sources.getOrNull(activeSourceIndex)
}

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

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    val userProfile: StateFlow<UserProfile> = storage.userProfileFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())
    val watchMinutes: StateFlow<Int> = storage.watchMinutesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val favorites: StateFlow<List<MediaItem>> = storage.favoritesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history: StateFlow<List<MediaItem>> = storage.historyFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val resumeList: StateFlow<List<WatchProgress>> = storage.resumeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val dohEnabled: StateFlow<Boolean> = storage.dohEnabledFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val subtitleLangPref: StateFlow<SubtitleLanguagePref> = storage.subtitleLangFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubtitleLanguagePref.DEVICE)

    init {
        viewModelScope.launch { storage.dohEnabledFlow.collect { enabled -> HttpClientProvider.dohEnabled = enabled } }
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
                _homeState.value = _homeState.value.copy(isLoading = false, errorMessage = "Gagal memuat katalog. Periksa koneksi internet Anda.")
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

    fun startPlayback(
        item: MediaItem,
        season: Int = 1,
        episode: Int = 1,
        knownEpisodes: List<EpisodeItem> = emptyList(),
        knownSimilar: List<MediaItem> = emptyList()
    ) {
        val langIso = subtitleLangPref.value.resolveIsoCode()
        val initialEps = if (knownEpisodes.isNotEmpty()) knownEpisodes else _selectedDetail.value?.episodes.orEmpty()
        val initialSimilar = if (knownSimilar.isNotEmpty()) knownSimilar else _selectedDetail.value?.similar.orEmpty()
        val sources = repository.getSilentSources(item, season, episode, langIso)

        _playerState.value = PlayerUiState(
            mediaItem = item,
            season = season,
            episode = episode,
            episodes = initialEps,
            similar = initialSimilar,
            sources = sources,
            activeSourceIndex = 0,
            isLoading = false
        )

        viewModelScope.launch {
            storage.recordHistory(item)
            storage.addWatchMinute(1)
            storage.saveWatchProgress(
                WatchProgress(
                    mediaId = item.id, title = item.title, posterUrl = item.posterUrl,
                    category = item.category, imdbId = item.imdbId, aniListId = item.aniListId,
                    season = season, episode = episode
                )
            )
            if (initialEps.isEmpty() || initialSimilar.isEmpty()) {
                val detail = repository.getMediaDetail(item)
                _playerState.value = _playerState.value.copy(
                    episodes = if (initialEps.isEmpty()) detail.episodes else initialEps,
                    similar = if (initialSimilar.isEmpty()) detail.similar else initialSimilar
                )
            }
        }
    }

    fun silentNextSource() {
        val st = _playerState.value
        if (st.activeSourceIndex + 1 < st.sources.size) {
            _playerState.value = st.copy(activeSourceIndex = st.activeSourceIndex + 1)
        }
    }

    fun saveUserProfile(name: String, photoUri: String?) = viewModelScope.launch { storage.saveUserProfile(name, photoUri) }
    fun toggleFavorite(item: MediaItem) = viewModelScope.launch { storage.toggleFavorite(item) }
    fun clearHistoryAndCache() = viewModelScope.launch { storage.clearHistoryAndResume() }
    fun setDohEnabled(enabled: Boolean) = viewModelScope.launch {
        HttpClientProvider.dohEnabled = enabled
        storage.setDohEnabled(enabled)
    }
    fun setSubtitleLangPref(pref: SubtitleLanguagePref) = viewModelScope.launch { storage.setSubtitleLang(pref) }

    fun checkUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val rel = updater.checkForUpdate()
            _availableUpdate.value = rel
            _isCheckingUpdate.value = false
            if (rel != null) {
                updater.showUpdateNotification(getApplication(), rel)
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val rel = _availableUpdate.value ?: return
        viewModelScope.launch {
            _isDownloadingUpdate.value = true
            _downloadProgress.value = 0f
            val file = updater.downloadApk(getApplication(), rel) { p -> _downloadProgress.value = p }
            _isDownloadingUpdate.value = false
            if (file != null) updater.launchInstaller(getApplication(), file)
        }
    }
}
