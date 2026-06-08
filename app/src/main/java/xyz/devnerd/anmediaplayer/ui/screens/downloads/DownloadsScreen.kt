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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xyz.devnerd.anmediaplayer.data.Download
import xyz.devnerd.anmediaplayer.data.DownloadState
import xyz.devnerd.anmediaplayer.data.DownloadsStore
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    onPlay: (Download) -> Unit = {},
) {
    val ctx = LocalContext.current
    val items = DownloadsStore.items
    var filter by remember { mutableStateOf(DlFilter.ALL) }
    var removing by remember { mutableStateOf<Download?>(null) }

    val shown = items.asReversed().filter { filter.match(it.state) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Downloads") }, windowInsets = androidx.compose.foundation.layout.WindowInsets(0)) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(top = inner.calculateTopPadding())) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
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
                        onPause = { DownloadsStore.pause(d.id) },
                        onResume = { DownloadsStore.resume(d.id) },
                        onRemove = { removing = d },
                    )
                }
            }
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
}

private val GreenMark = Color(0xFF2E9E4F)
private val RedMark = Color(0xFFD7263D)

@Composable
private fun Thumb(d: Download) {
    // Persisted cover (resolved at enqueue); fall back to a live walk-up for older items.
    val cover = d.coverUrl ?: rememberMediaPoster(d.server, d.path)
    Box(Modifier.width(56.dp).height(35.dp).clip(RoundedCornerShape(10.dp)).background(coverBrush(d.file)), contentAlignment = Alignment.Center) {
        if (cover != null) {
            coil3.compose.AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Outlined.Movie, null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(20.dp))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadRow(d: Download, onPlay: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onRemove: () -> Unit) {
    val playable = d.state == DownloadState.DONE && d.localUri != null
    Row(
        // Playable row: tap/center plays, long-press removes (so the X stays
        // reachable on TV, where the row-level clickable otherwise shadows it).
        Modifier.fillMaxWidth().then(if (playable) Modifier.focusHighlight(RoundedCornerShape(16.dp)) else Modifier).clip(RoundedCornerShape(16.dp)).then(if (playable) Modifier.combinedClickable(onClick = onPlay, onLongClick = onRemove) else Modifier).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Thumb(d)
        Column(Modifier.weight(1f)) {
            Text(d.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        when (d.state) {
            DownloadState.DOWNLOADING -> IconButton(onClick = onPause, modifier = Modifier.focusHighlight(CircleShape)) { Icon(Icons.Outlined.Pause, "Pause", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            DownloadState.PAUSED -> IconButton(onClick = onResume, modifier = Modifier.focusHighlight(CircleShape)) { Icon(Icons.Filled.PlayArrow, "Resume", tint = MaterialTheme.colorScheme.primary) }
            DownloadState.FAILED -> IconButton(onClick = onResume, modifier = Modifier.focusHighlight(CircleShape)) { Icon(Icons.Outlined.Refresh, "Retry", tint = MaterialTheme.colorScheme.primary) }
            else -> {}
        }
        IconButton(onClick = onRemove, modifier = Modifier.focusHighlight(CircleShape)) { Icon(Icons.Outlined.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
