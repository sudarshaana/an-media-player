package xyz.devnerd.anmediaplayer.ui.screens.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List as ListIcon
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xyz.devnerd.anmediaplayer.data.AppRepo
import xyz.devnerd.anmediaplayer.data.Entry
import xyz.devnerd.anmediaplayer.data.EntryType
import xyz.devnerd.anmediaplayer.data.MediaRepo
import xyz.devnerd.anmediaplayer.data.cleanTitle
import xyz.devnerd.anmediaplayer.data.fmtDate
import xyz.devnerd.anmediaplayer.data.fmtDur
import xyz.devnerd.anmediaplayer.data.fmtSize
import xyz.devnerd.anmediaplayer.data.naturalCompare
import xyz.devnerd.anmediaplayer.data.prettyName
import xyz.devnerd.anmediaplayer.data.progressKey
import xyz.devnerd.anmediaplayer.settings.NavPattern
import xyz.devnerd.anmediaplayer.ui.components.ImageViewer
import xyz.devnerd.anmediaplayer.ui.components.rememberFolderThumb

private enum class SortBy(val label: String, val icon: ImageVector) {
    NAME("Name", Icons.Outlined.SortByAlpha),
    DATE("Date modified", Icons.Outlined.Schedule),
    SIZE("Size", Icons.Outlined.Storage),
    TYPE("Type", Icons.Outlined.Category),
}

private val RES = Regex("(2160p|1080p|720p|480p)", RegexOption.IGNORE_CASE)
private fun resFor(name: String): String? = RES.find(name)?.value
private val ENC = Regex("(x265|x264|hevc|av1|h\\.?264|h\\.?265|vp9|xvid|divx)", RegexOption.IGNORE_CASE)
private fun encodingOf(name: String): String? = ENC.find(name)?.value?.uppercase()?.replace(".", "")
private fun mtimeEpoch(s: String?): Long = s?.replace("-", "")?.toLongOrNull() ?: 0L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    serverId: String,
    path: List<String>,
    navPattern: NavPattern,
    initialView: String, // "list" | "grid"
    initialSortKey: String = "date",
    initialSortAsc: Boolean = false,
    onSetSort: (String, Boolean) -> Unit = { _, _ -> },
    isWatched: (String, Int?) -> Boolean,
    getProgress: (String) -> Int,
    isBookmarked: (String, List<String>) -> Boolean,
    onToggleBookmark: (String, List<String>) -> Unit,
    onOpenFolder: (String, List<String>) -> Unit,
    onPlay: (String, List<String>, String, Int?) -> Unit,
    onDownload: (Entry) -> Unit = {},
    onSetView: (Boolean) -> Unit = {},
    onNavVisible: (Boolean) -> Unit = {},
    showTips: Boolean = false,
    onTipsSeen: () -> Unit = {},
    onPlayEpisode: (List<xyz.devnerd.anmediaplayer.data.EpisodeRef>, xyz.devnerd.anmediaplayer.data.EpisodeRef) -> Unit = { _, _ -> },
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = xyz.devnerd.anmediaplayer.ui.components.LocalIsTv.current
    val server = AppRepo.serverById(serverId)
    val pathKey = path.joinToString("/")

    var grid by remember { mutableStateOf(initialView == "grid") }
    var loading by remember(pathKey) { mutableStateOf(true) }
    var errorMsg by remember(pathKey) { mutableStateOf<String?>(null) }
    var loaded by remember(pathKey) { mutableStateOf<List<Entry>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(SortBy.entries.firstOrNull { it.name.equals(initialSortKey, true) } ?: SortBy.DATE) }
    var asc by remember { mutableStateOf(initialSortAsc) }
    var sortOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Entry?>(null) }
    var imageViewer by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pinFolder: (Entry) -> Unit = { e ->
        val was = isBookmarked(serverId, path + e.name)
        onToggleBookmark(serverId, path + e.name)
        android.widget.Toast.makeText(ctx, if (was) "Removed bookmark" else "Bookmarked", android.widget.Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(serverId, pathKey, refreshKey) {
        loading = true; errorMsg = null
        runCatching { MediaRepo.list(serverId, path) }
            .onSuccess { loaded = it; loading = false }
            .onFailure { errorMsg = it.message ?: "Couldn't load this folder"; loading = false }
    }

    val dir = if (asc) 1 else -1
    var entries = loaded.sortedWith(Comparator { a, b ->
        if (a.isDir != b.isDir) return@Comparator if (a.isDir) -1 else 1
        val r = when (sortBy) {
            SortBy.NAME -> naturalCompare(a.name, b.name)
            SortBy.SIZE -> (a.size ?: 0).compareTo(b.size ?: 0)
            SortBy.DATE -> mtimeEpoch(a.mtime).compareTo(mtimeEpoch(b.mtime))
            SortBy.TYPE -> a.type.name.compareTo(b.type.name).let { if (it != 0) it else naturalCompare(a.name, b.name) }
        }
        r * dir
    })
    if (query.isNotBlank()) entries = entries.filter { it.name.contains(query, ignoreCase = true) }
    val gridEntries = entries.filter { it.isDir || it.type == EntryType.VIDEO }

    val title = if (path.isNotEmpty()) cleanTitle(path.last()) else (server?.name ?: "Browse")
    val bookmarked = path.isNotEmpty() && isBookmarked(serverId, path)
    // Cover image inside the current folder → poster for this folder's video files + hero.
    val coverEntry = remember(loaded) { loaded.firstOrNull { it.type == EntryType.IMAGE } }
    val folderImageUrl = remember(loaded) { coverEntry?.let { MediaRepo.fileUrl(serverId, path, it.name) } }
    val hasVideos = remember(loaded) { loaded.any { it.type == EntryType.VIDEO } }
    val seasons = remember(loaded) { xyz.devnerd.anmediaplayer.data.detectSeasons(loaded, path) }
    val isSeries = seasons.isNotEmpty() && !searching
    val showHero = path.isNotEmpty() && folderImageUrl != null && hasVideos
    val heroSub = remember(pathKey) {
        val raw = path.lastOrNull() ?: ""
        listOfNotNull(Regex("(19|20)\\d{2}").find(raw)?.value, resFor(raw)).joinToString(" · ")
    }

    fun fileMeta(e: Entry): String {
        val parts = mutableListOf<String>()
        if (e.isDir) {
            parts.add("Folder")
        } else {
            e.size?.let { parts.add(fmtSize(it)) }
            if (e.type == EntryType.VIDEO) {
                e.durSec?.let { parts.add(fmtDur(it)) }
                encodingOf(e.name)?.let { parts.add(it) }
            }
            if (e.type == EntryType.SUBTITLE) parts.add("Subtitle")
            if (e.type == EntryType.IMAGE) parts.add("Image")
        }
        e.mtime?.let { parts.add(fmtDate(it)) }
        return parts.joinToString("  ·  ")
    }

    // Metadata lookup only for confident movie/series titles (release year present);
    // never category/listing/season folders or episode files.
    fun metaNameFor(e: Entry): String? =
        if ((e.type == EntryType.VIDEO || e.isDir) && xyz.devnerd.anmediaplayer.data.OmdbRepo.isLikelyTitle(e.name)) e.name else null

    fun open(e: Entry) {
        when {
            e.isDir -> onOpenFolder(serverId, path + e.name)
            e.type == EntryType.VIDEO -> onPlay(serverId, path, e.name, e.durSec)
            e.type == EntryType.IMAGE -> imageViewer = MediaRepo.fileUrl(serverId, path, e.name)
        }
    }

    // Scroll-aware bottom nav: shown by default + at the top, hides while scrolling
    // down, reappears on scroll up. Hoisted list/grid states drive it by direction
    // (robust against fling momentum, unlike raw scroll-delta sniffing).
    val navCb by androidx.compose.runtime.rememberUpdatedState(onNavVisible)
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val activeIndex = if (grid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
    val activeOffset = if (grid) gridState.firstVisibleItemScrollOffset else listState.firstVisibleItemScrollOffset
    var prevIndex by remember { mutableIntStateOf(0) }
    var prevOffset by remember { mutableIntStateOf(0) }
    LaunchedEffect(activeIndex, activeOffset) {
        val atTop = activeIndex == 0 && activeOffset < 8
        val scrolledUp = activeIndex < prevIndex || (activeIndex == prevIndex && activeOffset < prevOffset)
        navCb(atTop || scrolledUp)
        prevIndex = activeIndex; prevOffset = activeOffset
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (searching) {
                SearchBar(
                    query = query,
                    onQuery = { query = it },
                    onClose = { searching = false; query = "" },
                )
            } else {
                TopAppBar(
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                    navigationIcon = { IconButton(onClick = onUp) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Up") } },
                    title = {
                        Column {
                            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                            if (navPattern == NavPattern.BACKSTACK && server != null) {
                                Text(server.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    actions = {
                        val actionBtn = Modifier.size(40.dp)
                        val actionIcon = Modifier.size(19.dp)
                        IconButton(onClick = { searching = true }, modifier = actionBtn) { Icon(Icons.Outlined.Search, "Search", modifier = actionIcon) }
                        if (path.isNotEmpty()) {
                            IconButton(onClick = { onToggleBookmark(serverId, path) }, modifier = actionBtn) {
                                Icon(
                                    if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    "Bookmark folder",
                                    tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = actionIcon,
                                )
                            }
                        }
                        IconButton(onClick = { grid = !grid; onSetView(grid) }, modifier = actionBtn) {
                            Icon(if (grid) Icons.AutoMirrored.Outlined.ListIcon else Icons.Outlined.GridView, "Toggle view", modifier = actionIcon)
                        }
                        IconButton(onClick = { sortOpen = true }, modifier = actionBtn) { Icon(sortBy.icon, "Sort: ${sortBy.label}", modifier = actionIcon) }
                    },
                )
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (navPattern == NavPattern.BREADCRUMB && !searching) {
                Breadcrumb(serverName = server?.name ?: "", path = path, onJump = { i -> onOpenFolder(serverId, path.take(i)) })
            }
            PullToRefreshBox(
                isRefreshing = loading && loaded.isNotEmpty(),
                onRefresh = { refreshKey++ },
                modifier = Modifier.weight(1f).fillMaxSize(),
            ) {
            when {
                loading -> Skeleton(list = !grid)
                errorMsg != null -> ErrorState(errorMsg!!)
                isSeries -> xyz.devnerd.anmediaplayer.ui.screens.series.SeriesView(
                    serverId = serverId,
                    seriesPath = path,
                    title = title,
                    posterUrl = folderImageUrl,
                    seasons = seasons,
                    isWatched = isWatched,
                    getProgress = getProgress,
                    onPlayEpisode = onPlayEpisode,
                    modifier = Modifier.fillMaxSize(),
                )
                entries.isEmpty() -> EmptyState(searching = query.isNotBlank())
                !grid -> LazyColumn(state = listState, contentPadding = PaddingValues(start = if (isTv) 32.dp else 8.dp, end = if (isTv) 32.dp else 8.dp, top = 2.dp, bottom = if (isTv) 56.dp else 24.dp)) {
                    if (showHero) item { MediaHero(folderImageUrl, title, heroSub, path.lastOrNull() ?: title) }
                    items(entries.filter { !(showHero && it.name == coverEntry?.name) }, key = { it.name }) { e ->
                        val key = progressKey(serverId, path, e.name)
                        val isVid = e.type == EntryType.VIDEO
                        val pct = if (isVid && e.durSec != null) getProgress(key).toFloat() / e.durSec * 100 else 0f
                        val artSeed = if (isVid) e.name else null
                        val thumb = when {
                            e.isDir -> rememberFolderThumb(serverId, path + e.name)
                            isVid -> folderImageUrl
                            e.type == EntryType.IMAGE -> MediaRepo.fileUrl(serverId, path, e.name)
                            else -> null
                        }
                        BrowseListRow(
                            entry = e,
                            meta = fileMeta(e),
                            artSeed = artSeed,
                            thumbModel = thumb ?: if (e.isDir && path.isEmpty()) xyz.devnerd.anmediaplayer.ui.components.categoryCover(e.name) else null,
                            watched = isVid && isWatched(key, e.durSec),
                            pct = pct,
                            res = if (isVid) resFor(e.name) else null,
                            onClick = { open(e) },
                            onMenu = { menuFor = e },
                            onLongClick = if (e.isDir) ({ pinFolder(e) }) else null,
                            pinned = e.isDir && isBookmarked(serverId, path + e.name),
                            metaName = metaNameFor(e),
                        )
                    }
                }
                // TV is wide → more columns + overscan-safe padding so the last
                // row clears the screen edge.
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(if (isTv) 5 else 2),
                    contentPadding = PaddingValues(start = if (isTv) 32.dp else 16.dp, end = if (isTv) 32.dp else 16.dp, top = if (isTv) 24.dp else 8.dp, bottom = if (isTv) 56.dp else 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (showHero) item(span = { GridItemSpan(maxLineSpan) }) { MediaHero(folderImageUrl, title, heroSub, path.lastOrNull() ?: title) }
                    items(gridEntries, key = { it.name }) { e ->
                        val key = progressKey(serverId, path, e.name)
                        val isVid = e.type == EntryType.VIDEO
                        val pct = if (isVid && e.durSec != null) getProgress(key).toFloat() / e.durSec * 100 else 0f
                        val np = if (isVid) prettyName(e.name) else null
                        val label = if (e.isDir) cleanTitle(e.name) else (np?.primary ?: e.name)
                        val chips = if (e.isDir) {
                            listOfNotNull(e.mtime?.let { fmtDate(it) })
                        } else {
                            listOfNotNull(if (isVid) resFor(e.name) else null, e.size?.let { fmtSize(it) }, if (isVid) encodingOf(e.name) else null)
                        }
                        val thumb = when {
                            e.isDir -> rememberFolderThumb(serverId, path + e.name)
                            isVid -> folderImageUrl
                            else -> null
                        }
                        BrowseGridCard(
                            entry = e,
                            posterSeed = e.name,
                            label = label,
                            chips = chips,
                            watched = isVid && isWatched(key, e.durSec),
                            pct = pct,
                            imageModel = thumb ?: if (e.isDir && path.isEmpty()) xyz.devnerd.anmediaplayer.ui.components.categoryCover(e.name) else null,
                            onClick = { open(e) },
                            onLongClick = if (e.isDir) ({ pinFolder(e) }) else ({ menuFor = e }),
                            onMenu = if (!e.isDir) ({ menuFor = e }) else null,
                            pinned = e.isDir && isBookmarked(serverId, path + e.name),
                            metaName = metaNameFor(e),
                        )
                    }
                }
            }
            }
        }
    }

    if (sortOpen) {
        ModalBottomSheet(onDismissRequest = { sortOpen = false }) {
            Text("Sort by", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SortBy.entries.forEach { s ->
                val on = s == sortBy
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (on) asc = !asc else { sortBy = s; asc = (s == SortBy.NAME || s == SortBy.TYPE) }
                        onSetSort(sortBy.name.lowercase(), asc)
                    }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(s.icon, null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (on) FontWeight.W700 else FontWeight.W500), color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    if (on) Icon(if (asc) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Box(Modifier.height(24.dp))
        }
    }

    menuFor?.let { e ->
        FileContextSheet(
            entry = e,
            onDismiss = { menuFor = null },
            onPlay = { onPlay(serverId, path, e.name, e.durSec); menuFor = null },
            onDownload = { onDownload(e); menuFor = null },
        )
    }

    imageViewer?.let { url -> ImageViewer(url = url, onClose = { imageViewer = null }) }

    // First-run tips — phone only, shown once. Persist on dismiss.
    var tipsDismissed by remember { mutableStateOf(false) }
    if (!isTv && showTips && !tipsDismissed) {
        BrowserTipsSheet(onDismiss = { tipsDismissed = true; onTipsSeen() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTipsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
            Text("Quick tips", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text("A few gestures to get around faster.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
            TipRow(Icons.Outlined.BookmarkBorder, "Bookmark a folder", "Long-press any folder to pin it to Home.")
            TipRow(Icons.Outlined.SwapVert, "Sort the list", "Tap the sort button to reorder by name, date, size or type.")
            TipRow(Icons.Outlined.GridView, "List or grid", "Switch between list and grid view from the top bar.")
            TipRow(Icons.Outlined.Download, "Download a file", "Tap the ⋮ menu on a file to save it for offline.")
            TipRow(Icons.Outlined.Palette, "Theme & accent", "Change theme and accent colour in Settings ▸ Appearance.")
            androidx.compose.material3.Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Got it") }
        }
        Box(Modifier.height(16.dp))
    }
}

@Composable
private fun TipRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileContextSheet(entry: Entry, onDismiss: () -> Unit, onPlay: () -> Unit, onDownload: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(entry.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), maxLines = 1)
        if (entry.type == EntryType.VIDEO) {
            ActionRow(Icons.Outlined.PlayArrow, "Play", onPlay)
            ActionRow(Icons.Outlined.Download, "Download", onDownload)
        } else {
            ActionRow(Icons.Outlined.Download, "Download", onDownload)
        }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    TopAppBar(
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
        title = {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.W600, fontSize = MaterialTheme.typography.titleMedium.fontSize),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search this folder", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        actions = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "Clear") }
        },
    )
}

@Composable
private fun Breadcrumb(serverName: String, path: List<String>, onJump: (Int) -> Unit) {
    val crumbs = listOf(serverName) + path
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(crumbs.size) { i ->
            val last = i == crumbs.size - 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                val labelText = if (i == 0) serverName else cleanTitle(crumbs[i])
                Surface(
                    color = if (last) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).then(if (last) Modifier else Modifier.clickable { onJump(i) }),
                ) {
                    Text(
                        labelText,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (last) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Skeleton(list: Boolean) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (list) {
            repeat(8) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                        Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                    }
                }
            }
        } else {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(2) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                            Box(Modifier.fillMaxWidth(0.7f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(if (searching) Icons.Outlined.Search else Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(34.dp))
        }
        Text(if (searching) "No matches" else "Empty folder", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            if (searching) "Nothing here matches your search." else "This directory has no listable entries.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(34.dp))
        }
        Text("Couldn't load folder", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
