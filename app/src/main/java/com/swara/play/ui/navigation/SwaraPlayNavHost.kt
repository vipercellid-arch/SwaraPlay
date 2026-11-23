package com.swara.play.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.swara.play.model.MediaItem
import com.swara.play.ui.components.UpdateDialog
import com.swara.play.ui.screens.DetailScreen
import com.swara.play.ui.screens.ExploreScreen
import com.swara.play.ui.screens.HomeScreen
import com.swara.play.ui.screens.LibraryScreen
import com.swara.play.ui.screens.PlayerScreen
import com.swara.play.ui.screens.SettingsScreen
import com.swara.play.ui.theme.CinemaCrimson
import com.swara.play.ui.theme.OledBlack
import com.swara.play.ui.theme.OledSurface
import com.swara.play.ui.theme.TextPrimary
import com.swara.play.ui.theme.TextSecondary
import com.swara.play.viewmodel.SwaraPlayViewModel

private data class BottomNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    BottomNavTab("home", "Beranda", Icons.Default.Home),
    BottomNavTab("explore", "Jelajah", Icons.Default.Search),
    BottomNavTab("library", "Pustaka", Icons.Default.VideoLibrary),
    BottomNavTab("settings", "Setelan", Icons.Default.Settings)
)

@Composable
fun SwaraPlayNavHost(viewModel: SwaraPlayViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "home"
    val showBottomBar = currentRoute in bottomTabs.map { it.route }

    val homeState by viewModel.homeState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val selectedDetail by viewModel.selectedDetail.collectAsState()
    val isLoadingDetail by viewModel.isLoadingDetail.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState()
    val resumeList by viewModel.resumeList.collectAsState()
    val customAddons by viewModel.customAddons.collectAsState()
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsState()
    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val updateStatusText by viewModel.updateStatusText.collectAsState()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    val pendingUpdate = availableUpdate
    if (pendingUpdate != null) {
        UpdateDialog(
            release = pendingUpdate,
            isDownloading = isDownloadingUpdate,
            downloadProgress = downloadProgress,
            onInstallClick = { viewModel.downloadAndInstallUpdate() },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    Scaffold(
        containerColor = OledBlack,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = OledSurface) {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CinemaCrimson,
                                selectedTextColor = CinemaCrimson,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = OledBlack
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    state = homeState,
                    resumeList = resumeList,
                    onCategorySelected = { viewModel.selectHomeCategory(it) },
                    onMediaClick = { item ->
                        viewModel.openDetail(item)
                        navController.navigate("detail")
                    },
                    onResumeClick = { prog ->
                        val media = MediaItem(
                            id = prog.mediaId,
                            title = prog.title,
                            category = prog.category,
                            posterUrl = prog.posterUrl,
                            imdbId = prog.imdbId,
                            aniListId = prog.aniListId
                        )
                        viewModel.preparePlayback(media, prog.season, prog.episode)
                        navController.navigate("player")
                    },
                    onRefresh = { viewModel.refreshHome() }
                )
            }
            composable("explore") {
                ExploreScreen(
                    results = searchResults,
                    isSearching = isSearching,
                    onSearch = { q, cat -> viewModel.search(q, cat) },
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
                    onMediaClick = { item ->
                        viewModel.openDetail(item)
                        navController.navigate("detail")
                    },
                    onResumeClick = { prog ->
                        val media = MediaItem(
                            id = prog.mediaId,
                            title = prog.title,
                            category = prog.category,
                            posterUrl = prog.posterUrl,
                            imdbId = prog.imdbId,
                            aniListId = prog.aniListId
                        )
                        viewModel.preparePlayback(media, prog.season, prog.episode)
                        navController.navigate("player")
                    },
                    onClearHistory = { viewModel.clearHistory() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    adBlockEnabled = adBlockEnabled,
                    customAddons = customAddons,
                    updateStatusText = updateStatusText,
                    onToggleAdBlock = { viewModel.setAdBlockEnabled(it) },
                    onAddAddon = { viewModel.addStremioAddon(it) },
                    onRemoveAddon = { viewModel.removeStremioAddon(it) },
                    onCheckUpdate = { viewModel.checkUpdates(silent = false) }
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
                    onPlay = { item, season, ep ->
                        viewModel.preparePlayback(item, season, ep)
                        navController.navigate("player")
                    }
                )
            }
            composable("player") {
                PlayerScreen(
                    state = playerState,
                    adBlockEnabled = adBlockEnabled,
                    onBack = { navController.popBackStack() },
                    onSelectServer = { viewModel.selectServer(it) },
                    onSelectSubtitle = { viewModel.selectSubtitle(it) },
                    onProgressUpdate = { pos, dur -> viewModel.updatePlaybackPosition(pos, dur) }
                )
            }
        }
    }
}
