package com.swara.play.ui.navigation

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import com.swara.play.ui.components.QrisDonateDialog
import com.swara.play.ui.components.UpdateDialog
import com.swara.play.ui.screens.DetailScreen
import com.swara.play.ui.screens.ExploreScreen
import com.swara.play.ui.screens.HomeScreen
import com.swara.play.ui.screens.LibraryScreen
import com.swara.play.ui.screens.PlayerScreen
import com.swara.play.ui.screens.SettingsScreen
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
    NavTab("settings", "Profil", Icons.Default.Person)
)

@Composable
fun SwaraPlayNavHost(viewModel: SwaraPlayViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "home"
    val showDock = currentRoute in mainTabs.map { it.route }

    val uiModeManager = remember(context) { context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager }
    val isTv = BuildConfig.IS_TV_BUILD || uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

    var showDonateSheet by remember { mutableStateOf(false) }

    val userProfile by viewModel.userProfile.collectAsState()
    val watchMinutes by viewModel.watchMinutes.collectAsState()
    val homeState by viewModel.homeState.collectAsState()
    val exploreResults by viewModel.exploreResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val selectedDetail by viewModel.selectedDetail.collectAsState()
    val isLoadingDetail by viewModel.isLoadingDetail.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState()
    val resumeList by viewModel.resumeList.collectAsState()
    val customAddons by viewModel.customAddons.collectAsState()
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsState()
    val dohEnabled by viewModel.dohEnabled.collectAsState()
    val performanceMode by viewModel.performanceMode.collectAsState()
    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
    val updateStatusText by viewModel.updateStatusText.collectAsState()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    val pendingUpdate = availableUpdate
    if (showUpdateDialog && pendingUpdate != null) {
        UpdateDialog(
            release = pendingUpdate,
            isDownloading = isDownloadingUpdate,
            downloadProgress = downloadProgress,
            onInstallClick = { viewModel.downloadAndInstallUpdate() },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    if (showDonateSheet) {
        QrisDonateDialog(onDismiss = { showDonateSheet = false })
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(SwaraBlack).statusBarsPadding()) {
        val isWideLayout = maxWidth >= 600.dp || isTv

        NavHost(navController = navController, startDestination = "home", modifier = Modifier.fillMaxSize()) {
            composable("home") {
                HomeScreen(
                    state = homeState,
                    resumeList = resumeList,
                    availableUpdate = availableUpdate,
                    isWideLayout = isWideLayout,
                    isTv = isTv,
                    onCategorySelected = { viewModel.selectHomeCategory(it) },
                    onMediaClick = { item ->
                        viewModel.openDetail(item)
                        navController.navigate("detail")
                    },
                    onResumeClick = { prog ->
                        val media = MediaItem(
                            id = prog.mediaId, title = prog.title, category = prog.category,
                            posterUrl = prog.posterUrl, imdbId = prog.imdbId, aniListId = prog.aniListId
                        )
                        viewModel.preparePlayback(media, prog.season, prog.episode)
                        navController.navigate("player")
                    },
                    onOpenSearch = { navigateToTab("explore") },
                    onOpenLibrary = { navigateToTab("library") },
                    onOpenDonate = { showDonateSheet = true },
                    onOpenUpdateDialog = { viewModel.openUpdateDialog() },
                    onRefresh = { viewModel.refreshHome() }
                )
            }
            composable("explore") {
                ExploreScreen(
                    results = exploreResults,
                    isSearching = isSearching,
                    isWideLayout = isWideLayout,
                    onSearch = { q, cat -> viewModel.search(q, cat) },
                    onFilterChanged = { cat, area, genre, sort -> viewModel.loadFilteredExplore(cat, area, genre, sort) },
                    onMediaClick = { item ->
                        viewModel.openDetail(item)
                        navController.navigate("detail")
                    }
                )
            }
            composable("library") {
                LibraryScreen(
                    resumeList = resumeList,
                    favorites = favorites,
                    history = history,
                    isWideLayout = isWideLayout,
                    onMediaClick = { item ->
                        viewModel.openDetail(item)
                        navController.navigate("detail")
                    },
                    onResumeClick = { prog ->
                        val media = MediaItem(
                            id = prog.mediaId, title = prog.title, category = prog.category,
                            posterUrl = prog.posterUrl, imdbId = prog.imdbId, aniListId = prog.aniListId
                        )
                        viewModel.preparePlayback(media, prog.season, prog.episode)
                        navController.navigate("player")
                    },
                    onClearHistory = { viewModel.clearHistory() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    userProfile = userProfile,
                    watchMinutes = watchMinutes,
                    adBlockEnabled = adBlockEnabled,
                    dohEnabled = dohEnabled,
                    performanceMode = performanceMode,
                    customAddons = customAddons,
                    updateStatusText = updateStatusText,
                    onSaveProfile = { name, photo -> viewModel.saveUserProfile(name, photo) },
                    onToggleAdBlock = { viewModel.setAdBlockEnabled(it) },
                    onToggleDoh = { viewModel.setDohEnabled(it) },
                    onSelectPerformanceMode = { viewModel.setPerformanceMode(it) },
                    onAddAddon = { viewModel.addStremioAddon(it) },
                    onRemoveAddon = { viewModel.removeStremioAddon(it) },
                    onCheckUpdate = { viewModel.checkUpdates(silent = false) },
                    onOpenDonate = { showDonateSheet = true }
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
                    onPlay = { item, season, ep, eps ->
                        viewModel.preparePlayback(item, season, ep, eps)
                        navController.navigate("player")
                    }
                )
            }
            composable("player") {
                PlayerScreen(
                    state = playerState,
                    adBlockEnabled = adBlockEnabled,
                    onBack = { navController.popBackStack() },
                    onSelectEpisode = { item, s, e -> viewModel.preparePlayback(item, s, e, playerState.episodes) },
                    onSelectServer = { viewModel.selectServer(it) },
                    onAutoFallback = { viewModel.fallbackToNextServer() },
                    onSelectSubtitle = { viewModel.selectSubtitle(it) },
                    onProgressUpdate = { pos, dur -> viewModel.updatePlaybackPosition(pos, dur) }
                )
            }
        }

        // Swara-style Compact Centered Floating Pill Dock (identical monochrome DNA to Swara Music)
        if (showDock) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(SwaraGlass)
                    .border(0.75.dp, SwaraSubtleBorder, RoundedCornerShape(32.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                mainTabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
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
