package xyz.devnerd.anmediaplayer.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import xyz.devnerd.anmediaplayer.data.AppRepo
import xyz.devnerd.anmediaplayer.data.DownloadsStore
import xyz.devnerd.anmediaplayer.data.prettyName
import xyz.devnerd.anmediaplayer.settings.AppSettings
import xyz.devnerd.anmediaplayer.settings.BrowseView
import xyz.devnerd.anmediaplayer.ui.nav.TopDest
import xyz.devnerd.anmediaplayer.ui.screens.browser.BrowserScreen
import xyz.devnerd.anmediaplayer.ui.screens.player.PlaybackRequest
import xyz.devnerd.anmediaplayer.ui.screens.player.PlayerHost
import xyz.devnerd.anmediaplayer.ui.screens.downloads.DownloadsScreen
import xyz.devnerd.anmediaplayer.ui.screens.home.HomeScreen
import xyz.devnerd.anmediaplayer.ui.screens.servers.ConnectScreen
import xyz.devnerd.anmediaplayer.ui.screens.servers.ServersScreen
import xyz.devnerd.anmediaplayer.ui.screens.settings.SettingsActions
import xyz.devnerd.anmediaplayer.ui.screens.settings.SettingsScreen

private const val ROUTE_CONNECT = "connect"

private fun browseRoute(server: String, path: List<String>): String {
    val p = if (path.isEmpty()) "_" else Uri.encode(path.joinToString("/"))
    return "browse/$server/$p"
}

@Composable
fun App(
    settings: AppSettings,
    settingsActions: SettingsActions,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val onTopDest = TopDest.entries.any { d -> currentDest?.hierarchy?.any { it.route == d.route } == true }

    var playback by remember { mutableStateOf<PlaybackRequest?>(null) }
    val play: (String, List<String>, String, Int?) -> Unit = { server, path, file, dur ->
        playback = PlaybackRequest(server, path, file, dur ?: 0)
    }

    // Check saved-server reachability on launch / when the list changes.
    androidx.compose.runtime.LaunchedEffect(AppRepo.servers.size) {
        xyz.devnerd.anmediaplayer.data.ServerHealth.checkAll(AppRepo.servers.toList())
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // Hide the bottom bar for full-screen routes (Connect, Browser, later Player).
            AnimatedVisibility(
                visible = onTopDest,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NavigationBar {
                    TopDest.entries.forEach { dest ->
                        val selected = currentDest?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.switchTab(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = dest.label,
                                )
                            },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDest.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopDest.HOME.route) {
                HomeScreen(
                    modifier = Modifier.fillMaxSize(),
                    bookmarks = AppRepo.bookmarks,
                    onOpenConnect = { navController.navigate(ROUTE_CONNECT) },
                    onManageServers = { navController.switchTab(TopDest.SERVERS.route) },
                    onOpenBrowse = { server, path -> navController.navigate(browseRoute(server, path)) },
                    onOpenServer = { navController.navigate(browseRoute(it, emptyList())) },
                    onPlay = { item -> playback = PlaybackRequest(item.server, item.path, item.file, item.durSec) },
                )
            }
            composable(TopDest.SERVERS.route) {
                ServersScreen(
                    modifier = Modifier.fillMaxSize(),
                    onAddServer = { navController.navigate(ROUTE_CONNECT) },
                    onOpenServer = { navController.navigate(browseRoute(it, emptyList())) },
                )
            }
            composable(TopDest.DOWNLOADS.route) {
                DownloadsScreen(
                    modifier = Modifier.fillMaxSize(),
                    wifiOnly = settings.wifiOnly,
                    onPlay = play,
                    onManage = { navController.switchTab(TopDest.SETTINGS.route) },
                )
            }
            composable(TopDest.SETTINGS.route) {
                SettingsScreen(settings = settings, actions = settingsActions, modifier = Modifier.fillMaxSize())
            }
            composable(ROUTE_CONNECT) {
                ConnectScreen(
                    onClose = { navController.popBackStack() },
                    onConnected = { server ->
                        navController.navigate(browseRoute(server, emptyList())) {
                            popUpTo(ROUTE_CONNECT) { inclusive = true }
                        }
                    },
                )
            }
            composable("browse/{server}/{path}") { entry ->
                val server = entry.arguments?.getString("server") ?: "home"
                val raw = entry.arguments?.getString("path") ?: "_"
                val path = if (raw == "_") emptyList() else Uri.decode(raw).split("/")
                BrowserScreen(
                    serverId = server,
                    path = path,
                    navPattern = settings.navPattern,
                    initialView = settings.browseView.key,
                    isWatched = { key, dur -> AppRepo.isWatched(key, dur) },
                    getProgress = { key -> AppRepo.getProgress(key) },
                    isBookmarked = { s, p -> AppRepo.isBookmarked(s, p) },
                    onToggleBookmark = { s, p -> AppRepo.toggleBookmark(s, p) },
                    onOpenFolder = { s, p -> navController.navigate(browseRoute(s, p)) },
                    onPlay = play,
                    onDownload = { e ->
                        val np = prettyName(e.name)
                        DownloadsStore.add(server, path, e.name, np.primary, np.secondary.ifBlank { e.name }, e.size ?: 0, e.durSec ?: 0)
                    },
                    onSetView = { g -> settingsActions.onBrowseView(if (g) BrowseView.GRID else BrowseView.LIST) },
                    onPlayEpisode = { pl, start -> playback = PlaybackRequest(server, start.path, start.file, start.durSec, playlist = pl) },
                    onUp = { navController.popBackStack() },
                )
            }
        }
    }

        playback?.let { req ->
            PlayerHost(request = req, settings = settings, onClose = { playback = null })
        }
    }
}

private fun NavController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
