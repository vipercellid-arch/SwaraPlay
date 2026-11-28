package com.swara.play.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swara.play.data.local.WatchStorage
import com.swara.play.data.network.DeviceIntegrationManager
import com.swara.play.data.network.HttpClientProvider
import com.swara.play.data.provider.OfflineDownloadManager
import com.swara.play.data.repository.SwaraPlayRepository
import com.swara.play.data.updater.GitHubReleaseUpdater
import com.swara.play.model.ActorProfile
import com.swara.play.model.CalendarDaySchedule
import com.swara.play.model.UserProfileAccount
import com.swara.play.model.AppReleaseInfo
import com.swara.play.model.CastMember
import com.swara.play.model.ConnectedDevice
import com.swara.play.model.ContentRestrictionPrefs
import com.swara.play.model.DeviceIntegrationPrefs
import com.swara.play.model.EpisodeItem
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaDetail
import com.swara.play.model.MediaItem
import com.swara.play.model.OfflineDownloadItem
import com.swara.play.model.StreamSource
import com.swara.play.model.SubtitleLanguagePref
import com.swara.play.model.SubtitleStylePrefs
import com.swara.play.model.UserProfile
import com.swara.play.model.AppUiPrefs
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
    val koreanStars: List<CastMember> = emptyList(),
    val hollywoodStars: List<CastMember> = emptyList(),
    val extraCatalog: List<MediaItem> = emptyList(),
    val errorMessage: String? = null
)

data class PlayerUiState(
    val mediaItem: MediaItem? = null,
    val season: Int = 1,
    val episode: Int = 1,
    val episodes: List<EpisodeItem> = emptyList(),
    val similar: List<MediaItem> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val sources: List<StreamSource> = emptyList(),
    val activeSourceIndex: Int = 0,
    val directStreamUrl: String = "",
    val directStreamReferer: String = "",
    val directStreamOrigin: String = "",
    val activeServerName: String = "",
    val streamErrorMessage: String? = null,
    val offlineItem: OfflineDownloadItem? = null,
    val sniffedStreamUrl: String = "",
    val subtitleEnabled: Boolean = true,
    val activeSubtitleLangIso: String = "id",
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
    val deviceManager = DeviceIntegrationManager(application.applicationContext)

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

    private val _calendarDays = MutableStateFlow<List<CalendarDaySchedule>>(emptyList())
    val calendarDays: StateFlow<List<CalendarDaySchedule>> = _calendarDays.asStateFlow()

    private val _isLoadingCalendar = MutableStateFlow(false)
    val isLoadingCalendar: StateFlow<Boolean> = _isLoadingCalendar.asStateFlow()

    private val _isLoadingDetail = MutableStateFlow(false)
    val isLoadingDetail: StateFlow<Boolean> = _isLoadingDetail.asStateFlow()

    private val _selectedActorProfile = MutableStateFlow<ActorProfile?>(null)
    val selectedActorProfile: StateFlow<ActorProfile?> = _selectedActorProfile.asStateFlow()

    private val _isLoadingActor = MutableStateFlow(false)
    val isLoadingActor: StateFlow<Boolean> = _isLoadingActor.asStateFlow()

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
    val pipEnabled: StateFlow<Boolean> = storage.pipEnabledFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val betaProgramEnabled: StateFlow<Boolean> = storage.betaProgramEnabledFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val deviceIntegrationPrefs: StateFlow<DeviceIntegrationPrefs> = storage.deviceIntegrationPrefsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeviceIntegrationPrefs())
    val savedDevices: StateFlow<List<ConnectedDevice>> = storage.savedDevicesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val contentRestriction: StateFlow<ContentRestrictionPrefs> = storage.contentRestrictionFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContentRestrictionPrefs())
    val subtitleStyle: StateFlow<SubtitleStylePrefs> = storage.subtitleStyleFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubtitleStylePrefs())
    val subtitleLangPref: StateFlow<SubtitleLanguagePref> = storage.subtitleLangFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubtitleLanguagePref.INDONESIAN)

    init {
        viewModelScope.launch { storage.dohEnabledFlow.collect { enabled -> HttpClientProvider.dohEnabled = enabled } }
        refreshHome()
        loadFilteredExplore(MediaCategory.ALL, "Semua Area", "Semua Kategori", 0)
        deviceManager.registerThisDevice()
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
                    horrorMovies = bundle.horrorMovies,
                    koreanStars = bundle.koreanStars,
                    hollywoodStars = bundle.hollywoodStars
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
            _homeState.value = _homeState.value.copy(isLoadingMore = false, extraCatalog = merged, nextPage = page + 1, hasMore = more.isNotEmpty())
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
        lastExploreCategory = category; lastExploreArea = area; lastExploreGenre = genre; lastExploreSort = sortIndex
        lastExploreQuery = ""; explorePage = 0; exploreHasMore = true
        viewModelScope.launch {
            _isSearching.value = true
            val raw = repository.browseFiltered(category, area, genre, 0)
            _exploreResults.value = applySort(raw, sortIndex)
            explorePage = 1; exploreHasMore = raw.size >= 12
            _isSearching.value = false
        }
    }

    fun loadMoreExplore() {
        if (_isSearching.value || _isLoadingMoreExplore.value || !exploreHasMore || lastExploreQuery.isNotBlank()) return
        viewModelScope.launch {
            _isLoadingMoreExplore.value = true
            val more = runCatching { repository.browseFiltered(lastExploreCategory, lastExploreArea, lastExploreGenre, explorePage) }.getOrDefault(emptyList())
            if (more.isEmpty()) exploreHasMore = false
            else {
                _exploreResults.value = applySort((_exploreResults.value + more).distinctBy { it.id }, lastExploreSort)
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

    fun loadReleaseCalendar() {
        if (_calendarDays.value.isNotEmpty() || _isLoadingCalendar.value) return
        viewModelScope.launch {
            _isLoadingCalendar.value = true
            _calendarDays.value = runCatching { repository.getReleaseCalendarDays() }.getOrDefault(emptyList())
            _isLoadingCalendar.value = false
        }
    }

    fun selectProfile(profileId: String) {
        updateUiPrefs { it.copy(activeProfileId = profileId) }
    }

    fun addUserProfile(name: String, isKids: Boolean) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val newProf = UserProfileAccount(
            id = "p_${System.currentTimeMillis()}",
            name = clean,
            avatarEmoji = if (isKids) "🧸" else "🍿",
            isKids = isKids
        )
        updateUiPrefs { p ->
            p.copy(profiles = p.profiles + newProf, activeProfileId = newProf.id)
        }
    }

    fun openDetail(item: MediaItem) {
        viewModelScope.launch { runCatching { repository.preResolveStreams(item, 1, 1) } }
        _selectedDetail.value = MediaDetail(item = item, trailerYoutubeId = item.trailerYoutubeId)
        viewModelScope.launch {
            _isLoadingDetail.value = true
            // Stage 1: Instant Cinemeta metadata & episodes (< 300ms)
            val fast = runCatching { repository.getMediaDetailFast(item) }.getOrNull()
            if (fast != null && _selectedDetail.value?.item?.id == item.id) {
                _selectedDetail.value = fast
                _isLoadingDetail.value = false
            }
            // Stage 2: Parallel background enrichment (ID translation, cast photos, TVMaze stills, trailer)
            val langIso = subtitleLangPref.value.resolveIsoCode()
            val full = runCatching { repository.getMediaDetail(item, langIso) }.getOrNull()
            if (full != null && _selectedDetail.value?.item?.id == item.id) {
                _selectedDetail.value = full
            }
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
        val initialCast = if (_selectedDetail.value?.item?.id == item.id) _selectedDetail.value?.cast.orEmpty() else emptyList()

        _playerState.value = PlayerUiState(
            mediaItem = item,
            season = season,
            episode = episode,
            episodes = initialEps,
            similar = initialSimilar,
            cast = initialCast,
            sources = emptyList(),
            activeSourceIndex = 0,
            directStreamUrl = "",
            directStreamReferer = "",
            directStreamOrigin = "",
            activeServerName = "",
            streamErrorMessage = null,
            offlineItem = null,
            sniffedStreamUrl = "",
            subtitleEnabled = true,
            activeSubtitleLangIso = langIso,
            synchronizedVttContent = "",
            downloadStatusMessage = null,
            isLoading = true
        )

        if (initialEps.isEmpty() || initialSimilar.isEmpty() || initialCast.isEmpty()) {
            viewModelScope.launch {
                runCatching {
                    val det = repository.getMediaDetail(item, "id")
                    _playerState.value = _playerState.value.copy(
                        mediaItem = if (_playerState.value.mediaItem?.id == item.id) det.item else _playerState.value.mediaItem,
                        episodes = if (_playerState.value.episodes.isEmpty()) det.episodes else _playerState.value.episodes,
                        similar = if (_playerState.value.similar.isEmpty()) det.similar else _playerState.value.similar,
                        cast = if (_playerState.value.cast.isEmpty()) det.cast else _playerState.value.cast
                    )
                }
            }
        }

        viewModelScope.launch {
            val enrichedItem = repository.ensureAnimeImdbId(item)
            val resolvedList = repository.resolveProgressiveStreams(enrichedItem, season, episode) { firstFast ->
                val fastSource = StreamSource(
                    id = firstFast.providerId,
                    url = firstFast.streamUrl,
                    referer = firstFast.referer,
                    origin = firstFast.origin,
                    label = "${firstFast.providerName} • ${firstFast.qualityLabel}",
                    subtitleUrl = firstFast.subtitleVttUrl
                )
                if (_playerState.value.mediaItem?.id == item.id && _playerState.value.directStreamUrl.isBlank()) {
                    _playerState.value = _playerState.value.copy(
                        mediaItem = enrichedItem,
                        sources = listOf(fastSource),
                        activeSourceIndex = 0,
                        directStreamUrl = fastSource.url,
                        directStreamReferer = fastSource.referer,
                        directStreamOrigin = fastSource.origin,
                        activeServerName = fastSource.label,
                        streamErrorMessage = null,
                        isLoading = false
                    )
                    viewModelScope.launch {
                        val fastVtt = repository.fetchBestSynchronizedVtt(enrichedItem, season, episode, langIso, fastSource.subtitleUrl)
                        if (fastVtt.isNotBlank() && _playerState.value.mediaItem?.id == item.id) {
                            _playerState.value = _playerState.value.copy(synchronizedVttContent = fastVtt)
                        }
                    }
                }
            }

            val prefOrder = uiPrefs.value.serverOrder.map { it.lowercase() }
            val streamSources = resolvedList.map { r ->
                StreamSource(
                    id = r.providerId,
                    url = r.streamUrl,
                    referer = r.referer,
                    origin = r.origin,
                    label = "${r.providerName} • ${r.qualityLabel}",
                    subtitleUrl = r.subtitleVttUrl
                )
            }.sortedBy { s ->
                val lbl = s.label.lowercase()
                val idx = prefOrder.indexOfFirst { lbl.contains(it) }
                if (idx >= 0) idx else 999
            }

            val first = streamSources.firstOrNull()
            if (first != null) {
                val currentUrl = _playerState.value.directStreamUrl
                if (currentUrl.isNotBlank()) {
                    val activeIdx = streamSources.indexOfFirst { it.url == currentUrl }.coerceAtLeast(0)
                    _playerState.value = _playerState.value.copy(
                        mediaItem = enrichedItem,
                        sources = streamSources,
                        activeSourceIndex = activeIdx,
                        isLoading = false
                    )
                } else {
                    _playerState.value = _playerState.value.copy(
                        mediaItem = enrichedItem,
                        sources = streamSources,
                        activeSourceIndex = 0,
                        directStreamUrl = first.url,
                        directStreamReferer = first.referer,
                        directStreamOrigin = first.origin,
                        activeServerName = first.label,
                        streamErrorMessage = null,
                        sniffedStreamUrl = first.url,
                        isLoading = false
                    )
                }
            } else {
                _playerState.value = _playerState.value.copy(
                    mediaItem = enrichedItem,
                    sources = emptyList(),
                    directStreamUrl = "",
                    activeServerName = "",
                    streamErrorMessage = "Belum ditemukan aliran video langsung dari server EncDec untuk judul ini. Ketuk Coba Lagi atau pilih episode lain.",
                    isLoading = false
                )
            }

            val directSubUrl = first?.subtitleUrl.orEmpty()
            val vtt = repository.fetchBestSynchronizedVtt(enrichedItem, season, episode, langIso, directSubUrl)
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

    fun selectPlayerServer(index: Int) {
        val st = _playerState.value
        val target = st.sources.getOrNull(index) ?: return
        _playerState.value = st.copy(
            activeSourceIndex = index,
            directStreamUrl = target.url,
            directStreamReferer = target.referer,
            directStreamOrigin = target.origin,
            activeServerName = target.label,
            streamErrorMessage = null,
            sniffedStreamUrl = target.url,
            isLoading = false
        )
    }

    fun retryCurrentPlayback() {
        val st = _playerState.value
        val item = st.mediaItem ?: return
        startPlayback(item, st.season, st.episode, st.episodes, st.similar)
    }

    fun selectPlayerSubtitleLanguage(langIso: String?) {
        if (langIso == null) {
            _playerState.value = _playerState.value.copy(subtitleEnabled = false)
            return
        }
        val st = _playerState.value
        val item = st.mediaItem ?: return
        _playerState.value = st.copy(subtitleEnabled = true, activeSubtitleLangIso = langIso)
        viewModelScope.launch {
            val vtt = repository.fetchBestSynchronizedVtt(item, st.season, st.episode, langIso, st.activeSource?.subtitleUrl.orEmpty())
            if (vtt.isNotBlank()) {
                _playerState.value = _playerState.value.copy(synchronizedVttContent = vtt)
            }
        }
    }

    fun onDirectStreamFailed() {
        // 100% Pure Native ExoPlayer auto-failover to next EncDecEndpoints server
        val st = _playerState.value
        val nextIdx = st.activeSourceIndex + 1
        if (nextIdx < st.sources.size) {
            selectPlayerServer(nextIdx)
        } else {
            _playerState.value = st.copy(
                directStreamUrl = "",
                isLoading = false,
                streamErrorMessage = "Server EncDec aktif gagal memutar aliran ini. Ketuk Coba Lagi atau ganti server."
            )
        }
    }

    fun startOfflinePlayback(offline: OfflineDownloadItem) {
        val item = MediaItem(id = offline.mediaId, title = offline.title, category = offline.category, posterUrl = offline.posterUrl)
        _playerState.value = PlayerUiState(mediaItem = item, season = offline.season, episode = offline.episode, offlineItem = offline, isLoading = false)
    }

    fun downloadCurrentMedia() {
        val st = _playerState.value
        val item = st.mediaItem ?: return
        val streamUrl = st.sniffedStreamUrl.ifBlank { st.directStreamUrl }
        if (streamUrl.isBlank()) {
            _playerState.value = st.copy(downloadStatusMessage = "Putar video beberapa detik dulu agar aliran video siap diunduh.")
            return
        }
        viewModelScope.launch {
            _playerState.value = _playerState.value.copy(isDownloadingMedia = true, mediaDownloadProgress = 0.05f, downloadStatusMessage = "Mengunduh untuk tontonan offline...")
            val result = downloadManager.downloadStreamToOffline(
                item = item, season = st.season, episode = st.episode,
                streamUrl = streamUrl, referer = st.directStreamReferer.ifBlank { st.activeSource?.referer.orEmpty() },
                langIso = subtitleLangPref.value.resolveIsoCode()
            ) { prog -> _playerState.value = _playerState.value.copy(mediaDownloadProgress = prog) }
            if (result != null) {
                storage.saveOfflineDownload(result)
                _playerState.value = _playerState.value.copy(isDownloadingMedia = false, downloadStatusMessage = "Tersimpan di Pustaka Saya > Unduhan")
            } else {
                _playerState.value = _playerState.value.copy(isDownloadingMedia = false, downloadStatusMessage = "Sumber video ini tidak mendukung unduh langsung.")
            }
        }
    }

    fun deleteOfflineDownload(item: OfflineDownloadItem) = viewModelScope.launch {
        downloadManager.deleteDownload(item)
        storage.removeOfflineDownload(item.id)
    }

    fun openActorProfile(actor: CastMember) {
        _selectedActorProfile.value = ActorProfile(name = actor.name, photoUrl = actor.photoUrl)
        viewModelScope.launch {
            _isLoadingActor.value = true
            _selectedActorProfile.value = repository.loadActorProfile(actor, subtitleLangPref.value.resolveIsoCode())
            _isLoadingActor.value = false
        }
    }

    fun dismissActorProfile() { _selectedActorProfile.value = null }
    fun saveUserProfile(name: String, photoUri: String?) = viewModelScope.launch { storage.saveUserProfile(name, photoUri) }
    fun toggleFavorite(item: MediaItem) = viewModelScope.launch { storage.toggleFavorite(item) }
    fun clearHistoryAndCache() = viewModelScope.launch { storage.clearHistoryAndResume() }
    fun setDohEnabled(enabled: Boolean) = viewModelScope.launch { HttpClientProvider.dohEnabled = enabled; storage.setDohEnabled(enabled) }
    fun setPipEnabled(enabled: Boolean) = viewModelScope.launch { storage.setPipEnabled(enabled) }
    fun setBetaProgramEnabled(enabled: Boolean) = viewModelScope.launch {
        storage.setBetaProgramEnabled(enabled)
        checkUpdates(silent = false, overrideBeta = enabled)
    }
    fun updateContentRestriction(prefs: ContentRestrictionPrefs) = viewModelScope.launch { storage.updateContentRestriction(prefs) }
    fun updateSubtitleStyle(style: SubtitleStylePrefs) = viewModelScope.launch { storage.updateSubtitleStyle(style) }
    fun updateDeviceIntegrationPrefs(prefs: DeviceIntegrationPrefs) = viewModelScope.launch { storage.updateDeviceIntegrationPrefs(prefs) }
    fun addManualDevice(name: String, host: String) = viewModelScope.launch {
        val cleanHost = host.trim()
        if (cleanHost.isBlank()) return@launch
        val cleanName = name.trim().ifBlank { "SwaraPlay TV ($cleanHost)" }
        storage.addSavedDevice(ConnectedDevice(id = "$cleanHost:8765", name = cleanName, host = cleanHost, port = 8765, deviceType = "TV"))
    }
    fun saveDiscoveredDevice(device: ConnectedDevice) = viewModelScope.launch { storage.addSavedDevice(device) }
    fun removeSavedDevice(deviceId: String) = viewModelScope.launch { storage.removeSavedDevice(deviceId) }
    fun castCurrentPlaybackTo(device: ConnectedDevice, onResult: (Boolean) -> Unit) {
        val st = _playerState.value
        val item = st.mediaItem ?: return
        viewModelScope.launch { onResult(deviceManager.castMediaToDevice(device, item, st.season, st.episode)) }
    }
    fun setSubtitleLangPref(pref: SubtitleLanguagePref) = viewModelScope.launch { storage.setSubtitleLang(pref) }

    fun checkUpdates(silent: Boolean = false, overrideBeta: Boolean? = null) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val allowBeta = overrideBeta ?: betaProgramEnabled.value
            val rel = updater.checkForUpdate(includePrerelease = allowBeta)
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

    val uiPrefs: StateFlow<AppUiPrefs> = storage.uiPrefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiPrefs())

    fun updateUiPrefs(transform: (AppUiPrefs) -> AppUiPrefs) {
        viewModelScope.launch {
            storage.saveUiPrefs(transform(uiPrefs.value))
        }
    }

    fun generateAccountKey(): String {
        val digits = (1..16).map { (0..9).random() }.joinToString("")
        updateUiPrefs { it.copy(accountKey = digits) }
        return digits
    }

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        updateUiPrefs { p ->
            p.copy(recentSearches = (listOf(q) + p.recentSearches.filterNot { it.equals(q, true) }).take(8))
        }
    }

    fun removeRecentSearch(query: String) {
        updateUiPrefs { p -> p.copy(recentSearches = p.recentSearches.filterNot { it == query }) }
    }

    fun removeResumeProgress(mediaId: String) {
        viewModelScope.launch { storage.removeResumeProgress(mediaId) }
    }

}
