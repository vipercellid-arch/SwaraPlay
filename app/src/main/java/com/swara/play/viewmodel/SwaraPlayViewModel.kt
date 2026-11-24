package com.swara.play.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swara.play.data.local.WatchStorage
import com.swara.play.data.network.HttpClientProvider
import com.swara.play.data.provider.OfflineDownloadManager
import com.swara.play.data.repository.SwaraPlayRepository
import com.swara.play.data.updater.GitHubReleaseUpdater
import com.swara.play.model.AppReleaseInfo
import com.swara.play.model.EpisodeItem
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaDetail
import com.swara.play.model.MediaItem
import com.swara.play.model.OfflineDownloadItem
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
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val nextPage: Int = 1,
    val selectedCategory: MediaCategory = MediaCategory.ALL,
    val realtimeMovies: List<MediaItem> = emptyList(),
    val realtimeSeries: List<MediaItem> = emptyList(),
    val movies: List<MediaItem> = emptyList(),
    val series: List<MediaItem> = emptyList(),
    val asianDramas: List<MediaItem> = emptyList(),
    val animeTrending: List<MediaItem> = emptyList(),
    val animeTopRated: List<MediaItem> = emptyList(),
    val actionMovies: List<MediaItem> = emptyList(),
    val horrorMovies: List<MediaItem> = emptyList(),
    val extraCatalog: List<MediaItem> = emptyList(),
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
    val offlineItem: OfflineDownloadItem? = null,
    val sniffedStreamUrl: String = "",
    val subtitleEnabled: Boolean = true,
    val subtitleOffsetSec: Float = 0f,
    val synchronizedVttContent: String = "",
    val isDownloadingMedia: Boolean = false,
    val mediaDownloadProgress: Float = 0f,
    val downloadStatusMessage: String? = null,
    val isLoading: Boolean = false
) {
    val activeSource: StreamSource?
        get() = sources.getOrNull(activeSourceIndex)
}

class SwaraPlayViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SwaraPlayRepository()
    private val storage = WatchStorage(application.applicationContext)
    private val downloadManager = OfflineDownloadManager(application.applicationContext)
    private val updater = GitHubReleaseUpdater()

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _exploreResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val exploreResults: StateFlow<List<MediaItem>> = _exploreResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoadingMoreExplore = MutableStateFlow(false)
    val isLoadingMoreExplore: StateFlow<Boolean> = _isLoadingMoreExplore.asStateFlow()

    private var explorePage = 0
    private var exploreHasMore = true
    private var lastExploreCategory = MediaCategory.ALL
    private var lastExploreArea = "Semua Area"
    private var lastExploreGenre = "Semua Kategori"
    private var lastExploreSort = 0
    private var lastExploreQuery = ""

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
    val offlineDownloads: StateFlow<List<OfflineDownloadItem>> = storage.downloadsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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
            _homeState.value = _homeState.value.copy(isLoading = true, errorMessage = null, nextPage = 1, hasMore = true, extraCatalog = emptyList())
            try {
                val bundle = repository.loadHomeCatalog()
                _homeState.value = _homeState.value.copy(
                    isLoading = false,
                    realtimeMovies = bundle.realtimeMovies,
                    realtimeSeries = bundle.realtimeSeries,
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
        _homeState.value = _homeState.value.copy(selectedCategory = category, extraCatalog = emptyList(), nextPage = 1, hasMore = true)
    }

    fun loadMoreHomeCatalog() {
        val st = _homeState.value
        if (st.isLoading || st.isLoadingMore || !st.hasMore) return
        viewModelScope.launch {
            _homeState.value = st.copy(isLoadingMore = true)
            val page = st.nextPage
            val more = runCatching { repository.loadMoreHomeCatalog(st.selectedCategory, page) }.getOrDefault(emptyList())
            val merged = (st.extraCatalog + more).distinctBy { it.id }
            _homeState.value = _homeState.value.copy(
                isLoadingMore = false,
                extraCatalog = merged,
                nextPage = page + 1,
                hasMore = more.isNotEmpty()
            )
        }
    }

    fun search(query: String, category: MediaCategory) {
        lastExploreQuery = query.trim()
        viewModelScope.launch {
            if (lastExploreQuery.isBlank()) {
                loadFilteredExplore(category, lastExploreArea, lastExploreGenre, lastExploreSort)
                return@launch
            }
            _isSearching.value = true
            exploreHasMore = false
            _exploreResults.value = repository.searchAll(lastExploreQuery, category)
            _isSearching.value = false
        }
    }

    fun loadFilteredExplore(category: MediaCategory, area: String, genre: String, sortIndex: Int) {
        lastExploreCategory = category
        lastExploreArea = area
        lastExploreGenre = genre
        lastExploreSort = sortIndex
        lastExploreQuery = ""
        explorePage = 0
        exploreHasMore = true
        viewModelScope.launch {
            _isSearching.value = true
            val raw = repository.browseFiltered(category, area, genre, 0)
            _exploreResults.value = applySort(raw, sortIndex)
            explorePage = 1
            exploreHasMore = raw.size >= 12
            _isSearching.value = false
        }
    }

    fun loadMoreExplore() {
        if (_isSearching.value || _isLoadingMoreExplore.value || !exploreHasMore || lastExploreQuery.isNotBlank()) return
        viewModelScope.launch {
            _isLoadingMoreExplore.value = true
            val more = runCatching {
                repository.browseFiltered(lastExploreCategory, lastExploreArea, lastExploreGenre, explorePage)
            }.getOrDefault(emptyList())
            if (more.isEmpty()) {
                exploreHasMore = false
            } else {
                val combined = (_exploreResults.value + more).distinctBy { it.id }
                _exploreResults.value = applySort(combined, lastExploreSort)
                explorePage += 1
            }
            _isLoadingMoreExplore.value = false
        }
    }

    private fun applySort(list: List<MediaItem>, sortIndex: Int): List<MediaItem> = when (sortIndex) {
        1 -> list.sortedByDescending { it.year }
        2 -> list.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
        else -> list
    }

    fun openDetail(item: MediaItem) {
        _selectedDetail.value = MediaDetail(item = item, trailerYoutubeId = item.trailerYoutubeId)
        viewModelScope.launch {
            _isLoadingDetail.value = true
            val langIso = subtitleLangPref.value.resolveIsoCode()
            _selectedDetail.value = repository.getMediaDetail(item, langIso)
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
        val initialSources = repository.getSilentSources(item, season, episode, langIso)

        _playerState.value = PlayerUiState(
            mediaItem = item,
            season = season,
            episode = episode,
            episodes = initialEps,
            similar = initialSimilar,
            sources = initialSources,
            activeSourceIndex = 0,
            offlineItem = null,
            sniffedStreamUrl = "",
            subtitleEnabled = true,
            subtitleOffsetSec = 0f,
            synchronizedVttContent = "",
            downloadStatusMessage = null,
            isLoading = false
        )

        viewModelScope.launch {
            // Ensure anime has IMDb fallback ID resolved for maximum playback reliability & subtitle sync
            val enrichedItem = repository.ensureAnimeImdbId(item)
            if (enrichedItem.imdbId != item.imdbId) {
                val upgradedSources = repository.getSilentSources(enrichedItem, season, episode, langIso)
                _playerState.value = _playerState.value.copy(mediaItem = enrichedItem, sources = upgradedSources)
            }

            // Fetch single best synchronized VTT subtitle in background
            val vtt = repository.fetchSynchronizedSubtitleVtt(enrichedItem, season, episode, langIso)
            if (vtt.isNotBlank()) {
                _playerState.value = _playerState.value.copy(synchronizedVttContent = vtt)
            }

            storage.recordHistory(enrichedItem)
            storage.addWatchMinute(1)
            storage.saveWatchProgress(
                WatchProgress(
                    mediaId = enrichedItem.id, title = enrichedItem.title, posterUrl = enrichedItem.posterUrl,
                    category = enrichedItem.category, imdbId = enrichedItem.imdbId, aniListId = enrichedItem.aniListId,
                    malId = enrichedItem.malId, season = season, episode = episode
                )
            )
            if (initialEps.isEmpty() || initialSimilar.isEmpty()) {
                val detail = repository.getMediaDetail(enrichedItem, langIso)
                _playerState.value = _playerState.value.copy(
                    episodes = if (initialEps.isEmpty()) detail.episodes else initialEps,
                    similar = if (initialSimilar.isEmpty()) detail.similar else initialSimilar
                )
            }
        }
    }

    fun toggleSubtitleEnabled() {
        _playerState.value = _playerState.value.copy(subtitleEnabled = !_playerState.value.subtitleEnabled)
    }

    fun adjustSubtitleOffset(deltaSec: Float) {
        val next = (_playerState.value.subtitleOffsetSec + deltaSec).coerceIn(-15f, 15f)
        _playerState.value = _playerState.value.copy(subtitleOffsetSec = next)
    }

    fun resetSubtitleOffset() {
        _playerState.value = _playerState.value.copy(subtitleOffsetSec = 0f)
    }

    fun startOfflinePlayback(offline: OfflineDownloadItem) {
        val item = MediaItem(
            id = offline.mediaId,
            title = offline.title,
            category = offline.category,
            posterUrl = offline.posterUrl
        )
        _playerState.value = PlayerUiState(
            mediaItem = item,
            season = offline.season,
            episode = offline.episode,
            offlineItem = offline,
            isLoading = false
        )
    }

    fun onSniffedMediaUrl(url: String) {
        if (_playerState.value.sniffedStreamUrl.isBlank()) {
            _playerState.value = _playerState.value.copy(sniffedStreamUrl = url)
        }
    }

    fun downloadCurrentMedia() {
        val st = _playerState.value
        val item = st.mediaItem ?: return
        val streamUrl = st.sniffedStreamUrl
        if (streamUrl.isBlank()) {
            _playerState.value = st.copy(downloadStatusMessage = "Putar video beberapa detik dulu agar aliran video siap diunduh.")
            return
        }
        viewModelScope.launch {
            _playerState.value = _playerState.value.copy(isDownloadingMedia = true, mediaDownloadProgress = 0.05f, downloadStatusMessage = "Mengunduh untuk tontonan offline...")
            val result = downloadManager.downloadStreamToOffline(
                item = item,
                season = st.season,
                episode = st.episode,
                streamUrl = streamUrl,
                referer = st.activeSource?.referer.orEmpty(),
                langIso = subtitleLangPref.value.resolveIsoCode()
            ) { prog ->
                _playerState.value = _playerState.value.copy(mediaDownloadProgress = prog)
            }
            if (result != null) {
                storage.saveOfflineDownload(result)
                _playerState.value = _playerState.value.copy(isDownloadingMedia = false, downloadStatusMessage = "Tersimpan di Pustaka Saya > Unduhan")
            } else {
                _playerState.value = _playerState.value.copy(isDownloadingMedia = false, downloadStatusMessage = "Sumber video ini tidak mendukung unduh langsung.")
            }
        }
    }

    fun deleteOfflineDownload(item: OfflineDownloadItem) {
        viewModelScope.launch {
            downloadManager.deleteDownload(item)
            storage.removeOfflineDownload(item.id)
        }
    }

    fun silentNextSource() {
        val st = _playerState.value
        if (st.activeSourceIndex + 1 < st.sources.size) {
            _playerState.value = st.copy(activeSourceIndex = st.activeSourceIndex + 1, sniffedStreamUrl = "")
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
            if (rel != null) updater.showUpdateNotification(getApplication(), rel)
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
