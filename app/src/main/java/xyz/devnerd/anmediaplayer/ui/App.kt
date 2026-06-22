package xyz.devnerd.anmediaplayer.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import xyz.devnerd.anmediaplayer.data.EpisodeRef
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
import xyz.devnerd.anmediaplayer.ui.screens.servers.SuggestedServersScreen
import xyz.devnerd.anmediaplayer.ui.screens.settings.SettingsActions
import xyz.devnerd.anmediaplayer.ui.screens.settings.SettingsScreen

private const val ROUTE_CONNECT = "connect"
private const val ROUTE_EDIT = "edit"
private const val ROUTE_SUGGESTED = "suggested"

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
    val onBrowse = currentDest?.route?.startsWith("browse") == true
    var navVisible by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(currentDest?.route) { navVisible = true }

    val appContext = androidx.compose.ui.platform.LocalContext.current
    val isTv = remember(appContext) { xyz.devnerd.anmediaplayer.ui.components.isTvDevice(appContext) }
    var playback by remember { mutableStateOf<PlaybackRequest?>(null) }
    val play: (String, List<String>, String, Int?) -> Unit = { server, path, file, dur ->
        playback = PlaybackRequest(server, path, file, dur ?: 0)
    }

    // Check saved-server reachability on launch / when the list changes.
    androidx.compose.runtime.LaunchedEffect(AppRepo.servers.size) {
        xyz.devnerd.anmediaplayer.data.ServerHealth.checkAll(AppRepo.servers.toList())
    }

    val tvIndication = xyz.devnerd.anmediaplayer.ui.components.rememberTvFocusIndication()
    androidx.compose.runtime.CompositionLocalProvider(
        xyz.devnerd.anmediaplayer.ui.components.LocalIsTv provides isTv,
        // On TV, paint a visible focus ring on every clickable element so D-pad
        // selection is visible. Phones keep the default ripple.
        *(if (isTv) arrayOf(androidx.compose.foundation.LocalIndication provides tvIndication) else emptyArray()),
    ) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // Hide the bottom bar for full-screen routes (Connect, Browser, later Player).
            // Shrink/expand (not slide): the bar's measured height animates, so the
            // Scaffold's content padding tracks it smoothly — no revealed band, no snap.
            AnimatedVisibility(
                visible = (onTopDest || onBrowse) && navVisible,
                enter = expandVertically(expandFrom = androidx.compose.ui.Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = androidx.compose.ui.Alignment.Top) + fadeOut(),
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
                    onFindServers = { navController.navigate(ROUTE_SUGGESTED) },
                    onOpenBrowse = { server, path -> navController.navigate(browseRoute(server, path)) },
                    onOpenServer = { navController.navigate(browseRoute(it, emptyList())) },
                    onPlay = { item -> playback = PlaybackRequest(item.server, item.path, item.file, item.durSec) },
                    onEditServer = { navController.navigate("$ROUTE_EDIT/$it") },
                )
            }
            composable(TopDest.SERVERS.route) {
                ServersScreen(
                    modifier = Modifier.fillMaxSize(),
                    onAddServer = { navController.navigate(ROUTE_CONNECT) },
                    onEditServer = { navController.navigate("$ROUTE_EDIT/$it") },
                    onFindServers = { navController.navigate(ROUTE_SUGGESTED) },
                    onOpenServer = { navController.navigate(browseRoute(it, emptyList())) },
                )
            }
            composable(TopDest.DOWNLOADS.route) {
                DownloadsScreen(
                    modifier = Modifier.fillMaxSize(),
                    onPlay = { d ->
                        playback = if (d.localUri != null) PlaybackRequest(d.server, d.path, d.file, d.durSec, directUrl = d.localUri, localCoverUrl = d.coverUrl)
                        else PlaybackRequest(d.server, d.path, d.file, d.durSec)
                    },
                    onPlayLocal = { v, all ->
                        val playlist = all.map { lv ->
                            EpisodeRef(
                                listOf(lv.id.toString()), lv.displayName, (lv.durationMs / 1000).toInt(),
                                directUrl = lv.uri.toString(), portrait = lv.height > lv.width,
                            )
                        }
                        playback = PlaybackRequest(
                            "local", listOf(v.id.toString()), v.displayName, (v.durationMs / 1000).toInt(),
                            playlist = playlist, directUrl = v.uri.toString(), portrait = v.height > v.width,
                        )
                    },
                )
            }
            composable(TopDest.SETTINGS.route) {
                SettingsScreen(settings = settings, actions = settingsActions, modifier = Modifier.fillMaxSize())
            }
            composable(ROUTE_SUGGESTED) {
                SuggestedServersScreen(
                    onClose = { navController.popBackStack() },
                    onBrowse = { server -> navController.navigate(browseRoute(server, emptyList())) },
                )
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
            composable("$ROUTE_EDIT/{id}") { entry ->
                val id = entry.arguments?.getString("id")
                val server = id?.let { AppRepo.serverById(it) }
                if (server == null) {
                    androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    ConnectScreen(
                        editServer = server,
                        onClose = { navController.popBackStack() },
                        onConnected = { navController.popBackStack() },
                    )
                }
            }
            composable("browse/{server}/{path}") { entry ->
                val server = entry.arguments?.getString("server") ?: "home"
                val raw = entry.arguments?.getString("path") ?: "_"
                val path = if (raw == "_") emptyList() else Uri.decode(raw).split("/")
                val serverObj = AppRepo.serverById(server)
                if (serverObj != null && serverObj.parser == "WEB") {
                    xyz.devnerd.anmediaplayer.ui.screens.browser.WebViewBrowserScreen(
                        server = serverObj,
                        onPlay = { url ->
                            playback = PlaybackRequest(
                                server, emptyList(), Uri.decode(url.substringBefore('?').substringAfterLast('/')), 0,
                                directUrl = url,
                            )
                        },
                        onUp = { navController.popBackStack() },
                        onNavVisible = { navVisible = it },
                    )
                    return@composable
                }
                BrowserScreen(
                    serverId = server,
                    path = path,
                    navPattern = settings.navPattern,
                    initialView = settings.browseView.key,
                    initialSortKey = settings.sortKey,
                    initialSortAsc = settings.sortAsc,
                    onSetSort = settingsActions.onSetSort,
                    isWatched = { key, dur -> AppRepo.isWatched(key, dur) },
                    getProgress = { key -> AppRepo.getProgress(key) },
                    isBookmarked = { s, p -> AppRepo.isBookmarked(s, p) },
                    onToggleBookmark = { s, p -> AppRepo.toggleBookmark(s, p) },
                    onOpenFolder = { s, p -> navController.navigate(browseRoute(s, p)) },
                    onPlay = play,
                    onDownload = { e, downloadPath ->
                        val np = prettyName(e.name)
                        val url = xyz.devnerd.anmediaplayer.data.MediaRepo.fileUrl(server, downloadPath, e.name)
                        DownloadsStore.enqueue(appContext, server, downloadPath, e.name, np.primary, np.secondary.ifBlank { e.name }, e.size ?: 0, e.durSec ?: 0, url, settings.wifiOnly, settings.downloadDir)
                        android.widget.Toast.makeText(appContext, "Added to downloads", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onSetView = { g -> settingsActions.onBrowseView(if (g) BrowseView.GRID else BrowseView.LIST) },
                    onNavVisible = { navVisible = it },
                    showTips = !settings.browserTipsSeen,
                    onTipsSeen = settingsActions.onBrowserTipsSeen,
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
}

private fun NavController.switchTab(route: String) {
    // Always land on the tab root, popping any open browse stack (no saveState/
    // restoreState — that re-restored the browse and made Home appear "stuck").
    navigate(route) {
        popUpTo(graph.startDestinationId) { inclusive = false }
        launchSingleTop = true
    }
}
