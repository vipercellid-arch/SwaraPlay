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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.swara.play.model.isKidsFriendly
import com.swara.play.ui.components.ActorProfileDialog
import com.swara.play.ui.components.AnimatedAmbientBackground
import com.swara.play.ui.components.DeviceIntegrationPopup
import com.swara.play.ui.components.QrisDonateDialog
import com.swara.play.ui.components.SwaraAboutPopup
import com.swara.play.ui.components.SwaraDeveloperPopup
import com.swara.play.ui.components.SwaraUpdatePopup
import com.swara.play.ui.screens.CalendarScreen
import com.swara.play.ui.screens.CategoryCatalogScreen
import com.swara.play.ui.screens.DetailScreen
import com.swara.play.ui.screens.ExploreScreen
import com.swara.play.ui.screens.HomeScreen
import com.swara.play.ui.screens.LibraryScreen
import com.swara.play.ui.screens.PlayerScreen
import com.swara.play.ui.screens.ProfilePickerScreen
import com.swara.play.ui.screens.SettingsScreen
import com.swara.play.ui.theme.LocalSwaraPalette
import com.swara.play.ui.theme.SwaraPlayTheme
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.viewmodel.SwaraPlayViewModel

private data class TopPillTab(val route: String, val label: String)
private data class MobileBottomTab(val route: String, val label: String, val icon: ImageVector)

private val cinejoyTopTabs = listOf(
    TopPillTab("home", "Beranda"),
    TopPillTab("movies", "Film"),
    TopPillTab("series", "Serial TV"),
    TopPillTab("lists", "Daftar Saya"),
    TopPillTab("calendar", "Kalender")
)

private val mobileGlassTabs = listOf(
    MobileBottomTab("home", "Beranda", Icons.Default.Home),
    MobileBottomTab("movies", "Film", Icons.Default.Movie),
    MobileBottomTab("series", "Serial", Icons.Default.LiveTv),
    MobileBottomTab("calendar", "Kalender", Icons.Default.DateRange),
    MobileBottomTab("search", "Cari", Icons.Default.Search),
    MobileBottomTab("settings", "Setelan", Icons.Default.Settings)
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
    val calendarDays by viewModel.calendarDays.collectAsState()
    val isLoadingCalendar by viewModel.isLoadingCalendar.collectAsState()
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

    val kidsFilteredExplore = remember(exploreResults, uiPrefs.isKidsMode) {
        if (uiPrefs.isKidsMode) exploreResults.filter { it.isKidsFriendly() } else exploreResults
    }

    LaunchedEffect(currentRoute, pipEnabled) {
        onUpdatePipEligibility(currentRoute == "player" && pipEnabled)
    }

    SwaraPlayTheme(themeId = uiPrefs.themeId) {
        val pal = LocalSwaraPalette.current
        val showNavChrome = !isInPipMode && currentRoute != "player" && currentRoute != "detail" && currentRoute != "profiles"

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

        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(pal.bg)) {
            val isMobileCompact = !isTv && maxWidth < 600.dp

            NavHost(
                navController = navController,
                startDestination = if (isTv) "profiles" else "home"
            ) {
                composable("profiles") {
                    ProfilePickerScreen(
                        profiles = uiPrefs.profiles,
                        activeProfileId = uiPrefs.activeProfileId,
                        isTv = isTv,
                        onSelectProfile = { id ->
                            viewModel.selectProfile(id)
                            navController.navigate("home") {
                                popUpTo("profiles") { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onAddProfile = { name, kids ->
                            viewModel.addUserProfile(name, kids)
                            navController.navigate("home") {
                                popUpTo("profiles") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }

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
                    AnimatedAmbientBackground {
                        CategoryCatalogScreen(
                            title = if (uiPrefs.isKidsMode) "Film Animasi & Keluarga" else "Film Populer",
                            category = MediaCategory.MOVIE,
                            initialGenre = if (uiPrefs.isKidsMode) "Animasi" else activeProviderGenre,
                            items = kidsFilteredExplore,
                            isLoading = isSearching,
                            isLoadingMore = isLoadingMoreExplore,
                            isTv = isTv,
                            onLoadFilter = { cat, g -> viewModel.loadFilteredExplore(cat, "Semua", if (uiPrefs.isKidsMode) "Animasi" else g, 0) },
                            onLoadMore = { viewModel.loadMoreExplore() },
                            onMediaClick = { openMediaDetail(it) }
                        )
                    }
                }

                composable("series") {
                    AnimatedAmbientBackground {
                        CategoryCatalogScreen(
                            title = if (uiPrefs.isKidsMode) "Serial Animasi & Anak" else "Serial TV Populer",
                            category = MediaCategory.SERIES,
                            initialGenre = if (uiPrefs.isKidsMode) "Animasi" else "Semua",
                            items = kidsFilteredExplore,
                            isLoading = isSearching,
                            isLoadingMore = isLoadingMoreExplore,
                            isTv = isTv,
                            onLoadFilter = { cat, g -> viewModel.loadFilteredExplore(cat, "Semua", if (uiPrefs.isKidsMode) "Animasi" else g, 0) },
                            onLoadMore = { viewModel.loadMoreExplore() },
                            onMediaClick = { openMediaDetail(it) }
                        )
                    }
                }

                composable("lists") {
                    AnimatedAmbientBackground {
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
                }

                composable("calendar") {
                    CalendarScreen(
                        days = calendarDays,
                        fallbackMovies = homeState.movies,
                        fallbackAnime = homeState.animeTrending,
                        isLoading = isLoadingCalendar,
                        isKidsMode = uiPrefs.isKidsMode,
                        isTv = isTv,
                        onLoadCalendar = { viewModel.loadReleaseCalendar() },
                        onMediaClick = { openMediaDetail(it) }
                    )
                }

                composable("search") {
                    AnimatedAmbientBackground {
                        val trendingBase = (homeState.movies.take(10) + homeState.series.take(8)).distinctBy { it.id }
                        ExploreScreen(
                            results = kidsFilteredExplore,
                            trendingToday = if (uiPrefs.isKidsMode) trendingBase.filter { it.isKidsFriendly() } else trendingBase,
                            recentSearches = uiPrefs.recentSearches,
                            isSearching = isSearching,
                            isTv = isTv,
                            onSearch = { viewModel.search(it, MediaCategory.ALL) },
                            onSaveRecent = { viewModel.addRecentSearch(it) },
                            onRemoveRecent = { viewModel.removeRecentSearch(it) },
                            onMediaClick = { openMediaDetail(it) }
                        )
                    }
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
                        onOpenDonate = { showDonatePopup = true },
                        onOpenProfiles = { navController.navigate("profiles") }
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
                        onActorClick = { viewModel.openActorProfile(it) },
                        uiPrefs = uiPrefs,
                        onUpdateUiPrefs = { viewModel.updateUiPrefs(it) }
                    )
                }
            }

            // TOP BAR: Full Top Pill Navbar on Tablet/Desktop/TV; Minimal Brand + Profile Pill on Mobile HP
            if (showNavChrome) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = if (isTv) 48.dp else 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(pal.glass)
                            .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(99.dp))
                            .focusable()
                            .clickable { navToTab("home") }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.swaraplay_logo),
                            contentDescription = "SwaraPlay",
                            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("SwaraPlay", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Black)
                    }

                    if (!isMobileCompact) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(pal.glass)
                                .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(99.dp))
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
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (uiPrefs.isKidsMode) pal.brandAccent.copy(alpha = 0.25f) else Color(0x18FFFFFF))
                                    .border(1.dp, if (uiPrefs.isKidsMode) pal.brandAccent else Color.Transparent, RoundedCornerShape(99.dp))
                                    .focusable()
                                    .clickable { navController.navigate("profiles") }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "${uiPrefs.activeProfile.avatarEmoji} ${uiPrefs.activeProfile.name}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                    } else {
                        // Mobile HP Top-Right Compact Profile Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (uiPrefs.isKidsMode) pal.brandAccent.copy(alpha = 0.28f) else pal.glass)
                                .border(1.dp, if (uiPrefs.isKidsMode) pal.brandAccent else Color(0x30FFFFFF), RoundedCornerShape(99.dp))
                                .clickable { navController.navigate("profiles") }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "${uiPrefs.activeProfile.avatarEmoji} ${uiPrefs.activeProfile.name}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // MOBILE FLOATING FROSTED-GLASS BOTTOM NAVIGATION DOCK (Phone ONLY — Tablet/TV untouched!)
            if (showNavChrome && isMobileCompact) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xD8141A18), Color(0xE80A0E0D))
                            )
                        )
                        .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(99.dp))
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    mobileGlassTabs.forEach { tab ->
                        val active = currentRoute == tab.route
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (active) Color.White else Color.Transparent)
                                .clickable {
                                    if (tab.route == "movies") activeProviderGenre = "Semua"
                                    navToTab(tab.route)
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (active) Color.Black else Color(0xCCFFFFFF),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = tab.label,
                                color = if (active) Color.Black else Color(0xB8FFFFFF),
                                fontSize = 9.5.sp,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
