package com.swara.play.ui.navigation

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.swara.play.BuildConfig
import com.swara.play.R
import com.swara.play.model.MediaCategory
import com.swara.play.model.MediaItem
import com.swara.play.model.SWARA_PROVIDERS
import com.swara.play.ui.components.ActorProfileDialog
import com.swara.play.ui.components.DeviceIntegrationPopup
import com.swara.play.ui.components.QrisDonateDialog
import com.swara.play.ui.components.SwaraAboutPopup
import com.swara.play.ui.components.SwaraDeveloperPopup
import com.swara.play.ui.components.SwaraUpdatePopup
import com.swara.play.ui.screens.CategoryCatalogScreen
import com.swara.play.ui.screens.DetailScreen
import com.swara.play.ui.screens.ExploreScreen
import com.swara.play.ui.screens.HomeScreen
import com.swara.play.ui.screens.LibraryScreen
import com.swara.play.ui.screens.PlayerScreen
import com.swara.play.ui.screens.SettingsScreen
import com.swara.play.ui.theme.LocalSwaraPalette
import com.swara.play.ui.theme.SwaraPlayTheme
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.viewmodel.SwaraPlayViewModel

private data class TopPillTab(val route: String, val label: String)

private val cinejoyTopTabs = listOf(
    TopPillTab("home", "Beranda"),
    TopPillTab("movies", "Film"),
    TopPillTab("series", "Serial TV"),
    TopPillTab("lists", "Daftar Saya")
)

@Composable
fun SwaraPlayNavHost(
    viewModel: SwaraPlayViewModel,
    isInPipMode: Boolean = false,
    onUpdatePipEligibility: (Boolean) -> Unit = {},
    onRequestPip: () -> Unit = {}
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "home"

    val uiModeManager = remember(context) { context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager }
    val isTv = BuildConfig.IS_TV_BUILD || uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

    val uiPrefs by viewModel.uiPrefs.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val homeState by viewModel.homeState.collectAsState()
    val exploreResults by viewModel.exploreResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingMoreExplore by viewModel.isLoadingMoreExplore.collectAsState()
    val selectedDetail by viewModel.selectedDetail.collectAsState()
    val isLoadingDetail by viewModel.isLoadingDetail.collectAsState()
    val selectedActorProfile by viewModel.selectedActorProfile.collectAsState()
    val isLoadingActor by viewModel.isLoadingActor.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState()
    val resumeList by viewModel.resumeList.collectAsState()
    val offlineDownloads by viewModel.offlineDownloads.collectAsState()
    val dohEnabled by viewModel.dohEnabled.collectAsState()
    val pipEnabled by viewModel.pipEnabled.collectAsState()
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()
    val subtitleLangPref by viewModel.subtitleLangPref.collectAsState()
    val devicePrefs by viewModel.deviceIntegrationPrefs.collectAsState()
    val savedDevices by viewModel.savedDevices.collectAsState()
    val discoveredDevices by viewModel.deviceManager.discoveredDevices.collectAsState()
    val isScanningDevices by viewModel.deviceManager.isScanning.collectAsState()
    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    var showUpdatePopup by remember { mutableStateOf(false) }
    var showAboutPopup by remember { mutableStateOf(false) }
    var showDeveloperPopup by remember { mutableStateOf(false) }
    var showDonatePopup by remember { mutableStateOf(false) }
    var showDevicePopup by remember { mutableStateOf(false) }
    var activeProviderGenre by remember { mutableStateOf("Semua") }

    LaunchedEffect(currentRoute, pipEnabled) {
        onUpdatePipEligibility(currentRoute == "player" && pipEnabled)
    }

    SwaraPlayTheme(themeId = uiPrefs.themeId) {
        val pal = LocalSwaraPalette.current
        val showTopBar = !isInPipMode && currentRoute != "player" && currentRoute != "detail"

        if (showUpdatePopup) {
            SwaraUpdatePopup(
                availableUpdate = availableUpdate,
                isChecking = isCheckingUpdate,
                isDownloading = isDownloadingUpdate,
                downloadProgress = downloadProgress,
                onCheckNow = { viewModel.checkUpdates(silent = false) },
                onDownloadInstall = { viewModel.downloadAndInstallUpdate() },
                onOpenAbout = { showUpdatePopup = false; showAboutPopup = true },
                onOpenDonate = { showUpdatePopup = false; showDonatePopup = true },
                onDismiss = { showUpdatePopup = false }
            )
        }
        if (showAboutPopup) {
            SwaraAboutPopup(
                onOpenDonate = { showAboutPopup = false; showDonatePopup = true },
                onOpenDev = { showAboutPopup = false; showDeveloperPopup = true },
                onDismiss = { showAboutPopup = false }
            )
        }
        if (showDeveloperPopup) {
            SwaraDeveloperPopup(onDismiss = { showDeveloperPopup = false })
        }
        if (showDonatePopup) {
            QrisDonateDialog(onDismiss = { showDonatePopup = false })
        }
        if (showDevicePopup) {
            DeviceIntegrationPopup(
                prefs = devicePrefs,
                savedDevices = savedDevices,
                discoveredDevices = discoveredDevices,
                isScanning = isScanningDevices,
                isFromPlayer = currentRoute == "player",
                onStartScan = { viewModel.deviceManager.startScanning() },
                onStopScan = { viewModel.deviceManager.stopScanning() },
                onUpdatePrefs = { viewModel.updateDeviceIntegrationPrefs(it) },
                onAddManualDevice = { name, host -> viewModel.addManualDevice(name, host) },
                onSelectDevice = { dev ->
                    viewModel.saveDiscoveredDevice(dev)
                    if (currentRoute == "player") viewModel.castCurrentPlaybackTo(dev) {}
                },
                onRemoveDevice = { viewModel.removeSavedDevice(it) },
                onDismiss = {
                    viewModel.deviceManager.stopScanning()
                    showDevicePopup = false
                }
            )
        }
        selectedActorProfile?.let { actorProf ->
            ActorProfileDialog(
                profile = actorProf,
                isLoading = isLoadingActor,
                onSelectMedia = { media ->
                    viewModel.dismissActorProfile()
                    viewModel.openDetail(media)
                    navController.navigate("detail")
                },
                onDismiss = { viewModel.dismissActorProfile() }
            )
        }

        fun openMediaDetail(item: MediaItem) {
            viewModel.openDetail(item)
            navController.navigate("detail")
        }

        fun navToTab(route: String) {
            navController.navigate(route) {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(pal.bg)) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        state = homeState,
                        uiPrefs = uiPrefs,
                        favorites = favorites,
                        resumeList = resumeList,
                        isTv = isTv,
                        onMediaClick = { openMediaDetail(it) },
                        onPlayMedia = { item, s, e ->
                            viewModel.startPlayback(item, s, e)
                            navController.navigate("player")
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onResumeClick = { prog ->
                            val item = MediaItem(
                                id = prog.mediaId,
                                title = prog.title,
                                category = prog.category,
                                posterUrl = prog.posterUrl,
                                imdbId = prog.imdbId,
                                aniListId = prog.aniListId,
                                malId = prog.malId
                            )
                            viewModel.startPlayback(item, prog.season, prog.episode)
                            navController.navigate("player")
                        },
                        onRemoveResume = { viewModel.removeResumeProgress(it) },
                        onProviderClick = { provId ->
                            val prov = SWARA_PROVIDERS.firstOrNull { it.id == provId }
                            activeProviderGenre = prov?.movieGenre ?: "Semua"
                            navToTab("movies")
                        }
                    )
                }

                composable("movies") {
                    CategoryCatalogScreen(
                        title = "Film Populer",
                        category = MediaCategory.MOVIE,
                        initialGenre = activeProviderGenre,
                        items = exploreResults,
                        isLoading = isSearching,
                        isLoadingMore = isLoadingMoreExplore,
                        isTv = isTv,
                        onLoadFilter = { cat, g -> viewModel.loadFilteredExplore(cat, "Semua", g, 0) },
                        onLoadMore = { viewModel.loadMoreExplore() },
                        onMediaClick = { openMediaDetail(it) }
                    )
                }

                composable("series") {
                    CategoryCatalogScreen(
                        title = "Serial TV Populer",
                        category = MediaCategory.SERIES,
                        initialGenre = "Semua",
                        items = exploreResults,
                        isLoading = isSearching,
                        isLoadingMore = isLoadingMoreExplore,
                        isTv = isTv,
                        onLoadFilter = { cat, g -> viewModel.loadFilteredExplore(cat, "Semua", g, 0) },
                        onLoadMore = { viewModel.loadMoreExplore() },
                        onMediaClick = { openMediaDetail(it) }
                    )
                }

                composable("lists") {
                    LibraryScreen(
                        favorites = favorites,
                        history = history,
                        offlineDownloads = offlineDownloads,
                        isTv = isTv,
                        onMediaClick = { openMediaDetail(it) },
                        onPlayOffline = { dl ->
                            viewModel.startOfflinePlayback(dl)
                            navController.navigate("player")
                        },
                        onDeleteOffline = { viewModel.deleteOfflineDownload(it) }
                    )
                }

                composable("search") {
                    ExploreScreen(
                        results = exploreResults,
                        trendingToday = (homeState.movies.take(10) + homeState.series.take(8)).distinctBy { it.id },
                        recentSearches = uiPrefs.recentSearches,
                        isSearching = isSearching,
                        isTv = isTv,
                        onSearch = { viewModel.search(it, MediaCategory.ALL) },
                        onSaveRecent = { viewModel.addRecentSearch(it) },
                        onRemoveRecent = { viewModel.removeRecentSearch(it) },
                        onMediaClick = { openMediaDetail(it) }
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        uiPrefs = uiPrefs,
                        userProfile = userProfile,
                        subtitleStyle = subtitleStyle,
                        dohEnabled = dohEnabled,
                        pipEnabled = pipEnabled,
                        isTv = isTv,
                        onUpdateUiPrefs = { viewModel.updateUiPrefs(it) },
                        onUpdateSubtitleStyle = { viewModel.updateSubtitleStyle(it) },
                        onSetDoh = { viewModel.setDohEnabled(it) },
                        onSetPip = { viewModel.setPipEnabled(it) },
                        onClearHistory = { viewModel.clearHistoryAndCache() },
                        onOpenDevices = { showDevicePopup = true },
                        onOpenUpdate = { showUpdatePopup = true },
                        onOpenAbout = { showAboutPopup = true },
                        onOpenDonate = { showDonatePopup = true }
                    )
                }

                composable("detail") {
                    DetailScreen(
                        detail = selectedDetail,
                        uiPrefs = uiPrefs,
                        isLoading = isLoadingDetail,
                        isFavorite = selectedDetail?.item?.let { itItem -> favorites.any { it.id == itItem.id } } ?: false,
                        isTv = isTv,
                        onBack = { navController.popBackStack() },
                        onPlay = { item, s, e, eps ->
                            viewModel.startPlayback(item, s, e, eps)
                            navController.navigate("player")
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onActorClick = { viewModel.openActorProfile(it) },
                        onSimilarClick = { openMediaDetail(it) }
                    )
                }

                composable("player") {
                    PlayerScreen(
                        state = playerState,
                        preferredLangIso = subtitleLangPref.resolveIsoCode(),
                        isInPipMode = isInPipMode,
                        pipEnabled = pipEnabled,
                        subtitleStyle = subtitleStyle,
                        isFavorite = playerState.mediaItem?.let { itItem -> favorites.any { it.id == itItem.id } } ?: false,
                        showDeviceButton = devicePrefs.showInPlayerMenu,
                        onBack = { navController.popBackStack() },
                        onToggleFavorite = { item -> viewModel.toggleFavorite(item) },
                        onTogglePipSetting = { viewModel.setPipEnabled(it) },
                        onRequestPip = onRequestPip,
                        onOpenDeviceIntegration = { showDevicePopup = true },
                        onSelectEpisode = { item, s, e -> viewModel.startPlayback(item, s, e, playerState.episodes) },
                        onSelectSimilar = { item ->
                            viewModel.openDetail(item)
                            navController.navigate("detail")
                        },
                        onSelectServer = { viewModel.selectPlayerServer(it) },
                        onRetryPlayback = { viewModel.retryCurrentPlayback() },
                        onDownloadClick = { viewModel.downloadCurrentMedia() },
                        onSelectSubtitleLanguage = { viewModel.selectPlayerSubtitleLanguage(it) },
                        onUpdateSubtitleStyle = { viewModel.updateSubtitleStyle(it) },
                        onDirectStreamError = { viewModel.onDirectStreamFailed() },
                        onActorClick = { viewModel.openActorProfile(it) }
                    )
                }
            }

            // FLOATING CINEJOY TOP PILL NAVBAR (1:1 WITH WEB — ZERO BOTTOM NAV BAR!)
            if (showTopBar) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = if (isTv) 48.dp else 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(pal.glass)
                            .border(1.dp, pal.border, RoundedCornerShape(99.dp))
                            .focusable()
                            .clickable { navToTab("home") }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.swaraplay_logo),
                            contentDescription = "SwaraPlay",
                            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp))
                        )
                        if (isTv) {
                            Spacer(Modifier.width(8.dp))
                            Text("SwaraPlay", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(pal.glass)
                            .border(1.dp, pal.border, RoundedCornerShape(99.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(4.dp)
                    ) {
                        cinejoyTopTabs.forEach { tab ->
                            val active = currentRoute == tab.route
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (active) Color.White else Color.Transparent)
                                    .focusable()
                                    .clickable {
                                        if (tab.route == "movies") activeProviderGenre = "Semua"
                                        navToTab(tab.route)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    tab.label,
                                    color = if (active) Color.Black else TextSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (currentRoute == "search") Color.White else Color(0x18FFFFFF))
                                .focusable()
                                .clickable { navToTab("search") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Pencarian",
                                tint = if (currentRoute == "search") Color.Black else Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (currentRoute == "settings") Color.White else Color(0x18FFFFFF))
                                .focusable()
                                .clickable { navToTab("settings") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Pengaturan",
                                tint = if (currentRoute == "settings") Color.Black else Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
