package com.swara.play.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.swara.play.ui.theme.CinemaCrimsonSoft
import com.swara.play.ui.theme.OledBlack
import com.swara.play.ui.theme.OledBorder
import com.swara.play.ui.theme.OledGlass
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
    BottomNavTab("settings", "Profil", Icons.Default.Person)
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
    val dohEnabled by viewModel.dohEnabled.collectAsState()
    val performanceMode by viewModel.performanceMode.collectAsState()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .statusBarsPadding()
    ) {
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize()
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
                    onBrowseGenre = { genre -> viewModel.browseByGenre(genre) },
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
                    dohEnabled = dohEnabled,
                    performanceMode = performanceMode,
                    customAddons = customAddons,
                    updateStatusText = updateStatusText,
                    onToggleAdBlock = { viewModel.setAdBlockEnabled(it) },
                    onToggleDoh = { viewModel.setDohEnabled(it) },
                    onSelectPerformanceMode = { viewModel.setPerformanceMode(it) },
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

        if (showBottomBar) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(OledGlass)
                    .border(1.dp, OledBorder, RoundedCornerShape(28.dp))
                    .padding(vertical = 8.dp, horizontal = 8.dp)
            ) {
                bottomTabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) CinemaCrimsonSoft else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                if (!selected) {
                                    navController.navigate(tab.route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) CinemaCrimson else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = tab.label,
                            color = if (selected) CinemaCrimson else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
