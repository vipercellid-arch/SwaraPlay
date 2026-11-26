package com.swara.play.ui.navigation

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.swara.play.BuildConfig
import com.swara.play.model.MediaItem
import com.swara.play.ui.components.ActorProfileDialog
import com.swara.play.ui.components.DeviceIntegrationPopup
import com.swara.play.ui.components.QrisDonateDialog
import com.swara.play.ui.components.SwaraAboutPopup
import com.swara.play.ui.components.SwaraDeveloperPopup
import com.swara.play.ui.components.SwaraUpdatePopup
import com.swara.play.ui.screens.DetailScreen
import com.swara.play.ui.screens.ExploreScreen
import com.swara.play.ui.screens.HomeScreen
import com.swara.play.ui.screens.LibraryScreen
import com.swara.play.ui.screens.PlayerScreen
import com.swara.play.ui.screens.ProfileScreen
import com.swara.play.ui.screens.SettingsDetailScreen
import com.swara.play.ui.theme.SwaraBlack
import com.swara.play.ui.theme.SwaraGlass
import com.swara.play.ui.theme.SwaraSubtleBorder
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.viewmodel.SwaraPlayViewModel

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

private val mainTabs = listOf(
    NavTab("home", "Beranda", Icons.Default.Home),
    NavTab("explore", "Daftar Konten", Icons.Default.GridView),
    NavTab("library", "Pustaka", Icons.Default.VideoLibrary),
    NavTab("profile", "Profil", Icons.Default.Person)
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
    val showDock = !isInPipMode && (currentRoute in mainTabs.map { it.route })

    val uiModeManager = remember(context) { context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager }
    val isTv = BuildConfig.IS_TV_BUILD || uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

    var showUpdatePopup by remember { mutableStateOf(false) }
    var showAboutPopup by remember { mutableStateOf(false) }
    var showDeveloperPopup by remember { mutableStateOf(false) }
    var showDonatePopup by remember { mutableStateOf(false) }
    var showDevicePopup by remember { mutableStateOf(false) }

    val userProfile by viewModel.userProfile.collectAsState()
    val watchMinutes by viewModel.watchMinutes.collectAsState()
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
    val contentRestriction by viewModel.contentRestriction.collectAsState()
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()
    val devicePrefs by viewModel.deviceIntegrationPrefs.collectAsState()
    val savedDevices by viewModel.savedDevices.collectAsState()
    val discoveredDevices by viewModel.deviceManager.discoveredDevices.collectAsState()
    val isScanningDevices by viewModel.deviceManager.isScanning.collectAsState()
    val subtitleLangPref by viewModel.subtitleLangPref.collectAsState()
    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    // Enable auto-PiP when user exits app ONLY while watching video & PiP setting is ON
    LaunchedEffect(currentRoute, pipEnabled) {
        onUpdatePipEligibility(currentRoute == "player" && pipEnabled)
    }

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

    if (showDeveloperPopup) SwaraDeveloperPopup(onDismiss = { showDeveloperPopup = false })
    if (showDonatePopup) QrisDonateDialog(onDismiss = { showDonatePopup = false })

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
            onAddManualDevice = { n, ip -> viewModel.addManualDevice(n, ip) },
            onSelectDevice = { dev ->
                viewModel.saveDiscoveredDevice(dev)
                if (currentRoute == "player") {
                    viewModel.castCurrentPlaybackTo(dev) { ok ->
                        val msg = if (ok) "Tontonan dikirim ke ${dev.name}" else "Perangkat ${dev.name} tersimpan — pastikan SwaraPlay aktif di Wi-Fi yang sama"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    showDevicePopup = false
                } else {
                    Toast.makeText(context, "Terhubung ke ${dev.name}", Toast.LENGTH_SHORT).show()
                }
            },
            onRemoveDevice = { viewModel.removeSavedDevice(it) },
            onDismiss = { showDevicePopup = false }
        )
    }

    selectedActorProfile?.let { actorProf ->
        ActorProfileDialog(
            profile = actorProf,
            isLoading = isLoadingActor,
            onSelectMedia = { media ->
                viewModel.dismissActorProfile()
                viewModel.startPlayback(media, 1, 1)
                navController.navigate("player")
            },
            onDismiss = { viewModel.dismissActorProfile() }
        )
    }

    fun navigateToTab(route: String) {
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(SwaraBlack)) {
        val isWideLayout = maxWidth >= 600.dp || isTv

        NavHost(navController = navController, startDestination = "home", modifier = Modifier.fillMaxSize()) {
            composable("home") {
                HomeScreen(
                    state = homeState,
                    contentRestriction = contentRestriction,
                    userProfile = userProfile,
                    availableUpdate = availableUpdate,
                    isWideLayout = isWideLayout,
                    isTv = isTv,
                    onCategorySelected = { viewModel.selectHomeCategory(it) },
                    onMediaClick = { item ->
                        viewModel.startPlayback(item, 1, 1)
                        navController.navigate("player")
                    },
                    onActorClick = { actor -> viewModel.openActorProfile(actor) },
                    onOpenProfile = { navigateToTab("profile") },
                    onOpenSearch = { navigateToTab("explore") },
                    onOpenUpdatePopup = { showUpdatePopup = true },
                    onRefresh = { viewModel.refreshHome() },
                    onLoadMore = { viewModel.loadMoreHomeCatalog() }
                )
            }
            composable("explore") {
                ExploreScreen(
                    results = exploreResults.filter { !(contentRestriction.blockAdult18 && it.isAdult18) && !(contentRestriction.blockLgbt && it.isLgbt) },
                    isSearching = isSearching,
                    isLoadingMore = isLoadingMoreExplore,
                    isWideLayout = isWideLayout,
                    onSearch = { q, cat -> viewModel.search(q, cat) },
                    onFilterChanged = { cat, area, genre, sort -> viewModel.loadFilteredExplore(cat, area, genre, sort) },
                    onLoadMore = { viewModel.loadMoreExplore() },
                    onMediaClick = { item ->
                        viewModel.startPlayback(item, 1, 1)
                        navController.navigate("player")
                    }
                )
            }
            composable("library") {
                LibraryScreen(
                    resumeList = resumeList,
                    favorites = favorites,
                    history = history,
                    offlineDownloads = offlineDownloads,
                    isWideLayout = isWideLayout,
                    onMediaClick = { item ->
                        viewModel.startPlayback(item, 1, 1)
                        navController.navigate("player")
                    },
                    onResumeClick = { prog ->
                        val media = MediaItem(
                            id = prog.mediaId, title = prog.title, category = prog.category,
                            posterUrl = prog.posterUrl, imdbId = prog.imdbId,
                            aniListId = prog.aniListId, malId = prog.malId
                        )
                        viewModel.startPlayback(media, prog.season, prog.episode)
                        navController.navigate("player")
                    },
                    onOfflinePlayClick = { dl ->
                        viewModel.startOfflinePlayback(dl)
                        navController.navigate("player")
                    },
                    onDeleteDownload = { viewModel.deleteOfflineDownload(it) },
                    onClearHistory = { viewModel.clearHistoryAndCache() }
                )
            }
            composable("profile") {
                ProfileScreen(
                    userProfile = userProfile,
                    watchMinutes = watchMinutes,
                    onSaveProfile = { name, photo -> viewModel.saveUserProfile(name, photo) },
                    onOpenFullSettings = { navController.navigate("settings_full") },
                    onOpenUpdatePopup = { showUpdatePopup = true; viewModel.checkUpdates(silent = false) },
                    onOpenAboutPopup = { showAboutPopup = true },
                    onOpenDeveloperPopup = { showDeveloperPopup = true },
                    onOpenDonatePopup = { showDonatePopup = true },
                    onOpenDeviceIntegration = { showDevicePopup = true },
                    history = history,
                    onMediaClick = { item ->
                        viewModel.startPlayback(item, 1, 1)
                        navController.navigate("player")
                    }
                )
            }
            composable("settings_full") {
                SettingsDetailScreen(
                    dohEnabled = dohEnabled,
                    pipEnabled = pipEnabled,
                    contentRestriction = contentRestriction,
                    subtitleLangPref = subtitleLangPref,
                    onBack = { navController.popBackStack() },
                    onToggleDoh = { viewModel.setDohEnabled(it) },
                    onTogglePip = { viewModel.setPipEnabled(it) },
                    onUpdateContentRestriction = { viewModel.updateContentRestriction(it) },
                    onSelectSubtitleLang = { viewModel.setSubtitleLangPref(it) },
                    onClearHistoryAndCache = { viewModel.clearHistoryAndCache() },
                    onOpenUpdatePopup = { showUpdatePopup = true; viewModel.checkUpdates(silent = false) }
                )
            }
            composable("detail") {
                val isFav = selectedDetail?.item?.id?.let { id -> favorites.any { it.id == id } } ?: false
                DetailScreen(
                    detail = selectedDetail,
                    isLoading = isLoadingDetail,
                    isFavorite = isFav,
                    onBack = { navController.popBackStack() },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onSelectSimilar = { sim -> viewModel.openDetail(sim) },
                    onActorClick = { actor -> viewModel.openActorProfile(actor) },
                    onPlay = { item, season, ep, eps, sims ->
                        viewModel.startPlayback(item, season, ep, eps, sims)
                        navController.navigate("player")
                    }
                )
            }
            composable("player") {
                val isFav = playerState.mediaItem?.id?.let { id -> favorites.any { it.id == id } } ?: false
                PlayerScreen(
                    state = playerState,
                    preferredLangIso = subtitleLangPref.resolveIsoCode(),
                    isInPipMode = isInPipMode,
                    pipEnabled = pipEnabled,
                    subtitleStyle = subtitleStyle,
                    isFavorite = isFav,
                    showDeviceButton = devicePrefs.showInPlayerMenu,
                    onBack = { navController.popBackStack() },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onTogglePipSetting = { viewModel.setPipEnabled(it) },
                    onRequestPip = onRequestPip,
                    onOpenDeviceIntegration = { showDevicePopup = true },
                    onSelectEpisode = { item, s, e -> viewModel.startPlayback(item, s, e, playerState.episodes, playerState.similar) },
                    onSelectSimilar = { sim -> viewModel.startPlayback(sim, 1, 1) },
                    onMediaUrlSniffed = { url -> viewModel.onSniffedMediaUrl(url) },
                    onDownloadClick = { viewModel.downloadCurrentMedia() },
                    onSelectSubtitleLanguage = { viewModel.selectPlayerSubtitleLanguage(it) },
                    onUpdateSubtitleStyle = { viewModel.updateSubtitleStyle(it) },
                    onDirectStreamError = { viewModel.onDirectStreamFailed() },
                    onSilentFallback = { viewModel.silentNextSource() },
                    onActorClick = { actor -> viewModel.openActorProfile(actor) }
                )
            }
        }

        if (showDock) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (isWideLayout) 16.dp else 12.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(SwaraGlass)
                    .border(0.75.dp, SwaraSubtleBorder, RoundedCornerShape(32.dp))
                    .padding(horizontal = if (isWideLayout) 14.dp else 11.dp, vertical = if (isWideLayout) 8.dp else 6.dp)
            ) {
                mainTabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(if (isWideLayout) 44.dp else 40.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.Transparent)
                            .clickable { navigateToTab(tab.route) }
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) Color.Black else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
