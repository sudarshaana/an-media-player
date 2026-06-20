package xyz.devnerd.anmediaplayer.ui.screens.downloads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xyz.devnerd.anmediaplayer.data.Download
import xyz.devnerd.anmediaplayer.data.DownloadState
import xyz.devnerd.anmediaplayer.data.DownloadsStore
import xyz.devnerd.anmediaplayer.data.LocalVideo
import xyz.devnerd.anmediaplayer.data.fmtSize
import xyz.devnerd.anmediaplayer.data.relativeTime
import xyz.devnerd.anmediaplayer.ui.components.coverBrush
import xyz.devnerd.anmediaplayer.ui.components.focusHighlight
import xyz.devnerd.anmediaplayer.ui.components.rememberMediaPoster

private enum class DlFilter(val label: String, val match: (DownloadState) -> Boolean) {
    ALL("All", { true }),
    DOWNLOADED("Downloaded", { it == DownloadState.DONE }),
    DOWNLOADING("Downloading", { it == DownloadState.DOWNLOADING || it == DownloadState.PAUSED }),
    QUEUED("Queued", { it == DownloadState.QUEUED }),
    FAILED("Failed", { it == DownloadState.FAILED }),
}

private enum class DlTab(val label: String) { DOWNLOADS("Downloads"), LOCAL("Local Files") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    onPlay: (Download) -> Unit = {},
    onPlayLocal: (LocalVideo, List<LocalVideo>) -> Unit = { _, _ -> },
) {
    val ctx = LocalContext.current
    val items = DownloadsStore.items
    var filter by remember { mutableStateOf(DlFilter.ALL) }
    var removing by remember { mutableStateOf<Download?>(null) }
    var infoFor by remember { mutableStateOf<Download?>(null) }
    var menuFor by remember { mutableStateOf<Download?>(null) }
    var tab by remember { mutableStateOf(DlTab.DOWNLOADS) }
    var localSearching by remember { mutableStateOf(false) }
    var localQuery by remember { mutableStateOf("") }
    var localSortBy by remember { mutableStateOf(LocalSortBy.TIME) }
    var localAsc by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    val shown = items.asReversed().filter { filter.match(it.state) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (tab == DlTab.LOCAL && localSearching) {
                TopAppBar(
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                    navigationIcon = { IconButton(onClick = { localSearching = false; localQuery = "" }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                    title = {
                        BasicTextField(
                            value = localQuery,
                            onValueChange = { localQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.W600, fontSize = MaterialTheme.typography.titleMedium.fontSize),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner ->
                                if (localQuery.isEmpty()) Text("Search local videos", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                inner()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    actions = {
                        if (localQuery.isNotEmpty()) IconButton(onClick = { localQuery = "" }) { Icon(Icons.Outlined.Close, "Clear") }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Files") },
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                    actions = {
                        if (tab == DlTab.LOCAL) {
                            IconButton(onClick = { localSearching = true }) { Icon(Icons.Outlined.Search, "Search") }
                            Box {
                                IconButton(onClick = { sortMenuOpen = true }) { Icon(Icons.Outlined.FilterList, "Filter") }
                                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                    LocalSortBy.entries.forEach { s ->
                                        val on = s == localSortBy
                                        DropdownMenuItem(
                                            text = { Text(s.label) },
                                            leadingIcon = { Icon(s.icon, null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                                            trailingIcon = { if (on) Icon(if (localAsc) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null, tint = MaterialTheme.colorScheme.primary) },
                                            onClick = { if (on) localAsc = !localAsc else { localSortBy = s; localAsc = s == LocalSortBy.NAME } },
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(top = inner.calculateTopPadding())) {
            TabRow(selectedTabIndex = tab.ordinal) {
                DlTab.entries.forEach { t -> Tab(selected = t == tab, onClick = { tab = t }, text = { Text(t.label) }) }
            }
            when (tab) {
                DlTab.DOWNLOADS -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                    item {
                        LazyRow(contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DlFilter.entries.size) { i ->
                                val f = DlFilter.entries[i]
                                FilterChip(selected = f == filter, onClick = { filter = f }, label = { Text(f.label) }, modifier = Modifier.focusHighlight(RoundedCornerShape(8.dp)))
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Download, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                                }
                                Text("Nothing here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(shown, key = { it.id }) { d ->
                            DownloadRow(
                                d,
                                onPlay = { onPlay(d) },
                                menuExpanded = menuFor?.id == d.id,
                                onMore = { menuFor = d },
                                onDismissMenu = { menuFor = null },
                                onPause = { DownloadsStore.pause(d.id); menuFor = null },
                                onResume = { DownloadsStore.resume(d.id); menuFor = null },
                                onRestart = { DownloadsStore.restart(ctx, d.id); menuFor = null },
                                onInfo = { infoFor = d; menuFor = null },
                                onShare = {
                                    menuFor = null
                                    d.localUri?.let { localUri ->
                                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(d.file.substringAfterLast('.', "")) ?: "video/*"
                                            putExtra(android.content.Intent.EXTRA_STREAM, shareUriFor(ctx, localUri))
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        ctx.startActivity(android.content.Intent.createChooser(send, "Share ${d.title}"))
                                    }
                                },
                                onRemove = { removing = d; menuFor = null },
                            )
                        }
                    }
                }
                DlTab.LOCAL -> LocalFilesTab(modifier = Modifier.fillMaxSize(), onPlay = onPlayLocal, query = localQuery, sortBy = localSortBy, asc = localAsc)
            }
        }
    }

    removing?.let { d ->
        var alsoDelete by remember(d.id) { mutableStateOf(false) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove download?") },
            text = {
                Column {
                    Text(d.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp).clickable { alsoDelete = !alsoDelete },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        androidx.compose.material3.Checkbox(checked = alsoDelete, onCheckedChange = { alsoDelete = it })
                        Text("Also delete file from device", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { DownloadsStore.remove(ctx, d.id, alsoDelete); removing = null }) { Text("Remove") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }

    infoFor?.let { d ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { infoFor = null },
            title = { Text(d.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoBlock("Source", (d.path + d.file).joinToString("/"))
                    if (d.localUri != null) InfoBlock("Location", localDisplayPath(d.localUri))
                    InfoRow("Size", fmtSize(d.size))
                    if (d.durSec > 0) InfoRow("Duration", "${d.durSec / 60}m ${d.durSec % 60}s")
                    InfoRow("State", d.state.name)
                    if (d.state == DownloadState.DONE) InfoRow("Completed", relativeTime(d.completedAt))
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { infoFor = null }) { Text("Close") } },
        )
    }
}

/** Resolves a stored [Download.localUri] (`file://…` app storage, or `content://…` SAF doc) to a human-readable path. */
private fun localDisplayPath(localUri: String): String {
    val uri = android.net.Uri.parse(localUri)
    return when (uri.scheme) {
        "file" -> uri.path ?: localUri
        "content" -> runCatching {
            val docId = android.provider.DocumentsContract.getDocumentId(uri)
            val (volume, relPath) = docId.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val root = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
            "$root/$relPath"
        }.getOrDefault(localUri)
        else -> localUri
    }
}

/** Builds a shareable content:// Uri for a download — file:// app-storage paths need a FileProvider grant. */
private fun shareUriFor(ctx: android.content.Context, localUri: String): android.net.Uri {
    val uri = android.net.Uri.parse(localUri)
    return if (uri.scheme == "file") {
        androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", java.io.File(uri.path!!))
    } else uri
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Label-above-value layout for long unbroken strings (paths) that would otherwise get clipped in a side-by-side row. */
@Composable
private fun InfoBlock(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
    }
}

private val GreenMark = Color(0xFF2E9E4F)
private val RedMark = Color(0xFFD7263D)

@Composable
private fun Thumb(d: Download) {
    // Persisted cover (resolved at enqueue); fall back to a live walk-up for older items.
    val cover = d.coverUrl ?: rememberMediaPoster(d.server, d.path)
    Box(Modifier.width(96.dp).height(60.dp).clip(RoundedCornerShape(12.dp)).background(coverBrush(d.file)), contentAlignment = Alignment.Center) {
        if (cover != null) {
            coil3.compose.AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Outlined.Movie, null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(28.dp))
        }
        // Corner status mark: green when downloaded, red when failed.
        when (d.state) {
            DownloadState.DONE -> StatusMark(GreenMark, Icons.Filled.Check)
            DownloadState.FAILED -> StatusMark(RedMark, Icons.Filled.PriorityHigh)
            else -> {}
        }
    }
}

@Composable
private fun BoxScope.StatusMark(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.align(Alignment.BottomEnd).padding(2.dp).size(15.dp).clip(CircleShape)
            .background(color).border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(9.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DownloadRow(
    d: Download,
    onPlay: () -> Unit,
    menuExpanded: Boolean,
    onMore: () -> Unit,
    onDismissMenu: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onInfo: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
) {
    val playable = d.state == DownloadState.DONE && d.localUri != null
    Row(
        // Row always shows ripple; tap plays when playable, long-press removes
        // (X stays reachable on TV, where row-level clickable otherwise shadows it).
        Modifier.fillMaxWidth().focusHighlight(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = if (playable) onPlay else onMore, onLongClick = onRemove)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Thumb(d)
        Column(Modifier.weight(1f)) {
            Text(d.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val p = d.progress ?: 0
            when (d.state) {
                DownloadState.DONE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = GreenMark, modifier = Modifier.size(13.dp))
                    Text("${fmtSize(d.size)} · ${relativeTime(d.completedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DownloadState.FAILED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(13.dp))
                    Text("Failed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                DownloadState.QUEUED -> Text("Queued", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DownloadState.DOWNLOADING, DownloadState.PAUSED -> {
                    val label = if (d.state == DownloadState.PAUSED) "Paused · $p%" else "$p%  ·  ${fmtSize((d.size * (1 - p / 100.0)).toLong())} left"
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(p / 100f).background(if (d.state == DownloadState.PAUSED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary))
                    }
                }
            }
        }
        Box {
            IconButton(onClick = onMore, modifier = Modifier.focusHighlight(CircleShape)) {
                Icon(Icons.Outlined.MoreVert, "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
                when (d.state) {
                    DownloadState.DOWNLOADING -> DropdownMenuItem(text = { Text("Pause") }, leadingIcon = { Icon(Icons.Outlined.PauseCircleOutline, null) }, onClick = onPause)
                    DownloadState.PAUSED -> DropdownMenuItem(text = { Text("Resume") }, leadingIcon = { Icon(Icons.Outlined.PlayCircleOutline, null) }, onClick = onResume)
                    DownloadState.FAILED -> DropdownMenuItem(text = { Text("Retry") }, leadingIcon = { Icon(Icons.Outlined.Autorenew, null) }, onClick = onResume)
                    DownloadState.DONE -> DropdownMenuItem(text = { Text("Restart") }, leadingIcon = { Icon(Icons.Outlined.RestartAlt, null) }, onClick = onRestart)
                    DownloadState.QUEUED -> {}
                }
                DropdownMenuItem(text = { Text("Info") }, leadingIcon = { Icon(Icons.Outlined.Info, null) }, onClick = onInfo)
                if (playable) DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = onShare)
                DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) }, onClick = onRemove)
            }
        }
    }
}
