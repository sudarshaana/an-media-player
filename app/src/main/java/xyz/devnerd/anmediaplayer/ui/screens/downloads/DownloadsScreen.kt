package xyz.devnerd.anmediaplayer.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import xyz.devnerd.anmediaplayer.ui.components.coverBrush

private enum class DlFilter(val label: String, val match: (DownloadState) -> Boolean) {
    ALL("All", { true }),
    DOWNLOADED("Downloaded", { it == DownloadState.DONE }),
    DOWNLOADING("Downloading", { it == DownloadState.DOWNLOADING }),
    QUEUED("Queued", { it == DownloadState.QUEUED }),
    FAILED("Failed", { it == DownloadState.FAILED }),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    wifiOnly: Boolean = true,
    onPlay: (Download) -> Unit = {},
    onManage: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val items = DownloadsStore.items
    var filter by remember { mutableStateOf(DlFilter.ALL) }

    LaunchedEffect(Unit) { while (true) { DownloadsStore.refresh(ctx); delay(1000) } }

    val done = items.filter { it.state == DownloadState.DONE }
    val used = done.sumOf { it.size }
    val shown = items.filter { filter.match(it.state) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Downloads") }, windowInsets = androidx.compose.foundation.layout.WindowInsets(0)) },
    ) { inner ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding(), bottom = 28.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(if (wifiOnly) Icons.Outlined.Wifi else Icons.Outlined.Public, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (wifiOnly) "Wi-Fi only" else "Wi-Fi & mobile data", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("${fmtSize(used)} used · ${done.size} items offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onManage) { Text("Manage") }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DlFilter.entries.size) { i ->
                        val f = DlFilter.entries[i]
                        FilterChip(selected = f == filter, onClick = { filter = f }, label = { Text(f.label) })
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
                items(shown, key = { it.id }) { d -> DownloadRow(d, onPlay = { onPlay(d) }, onRemove = { DownloadsStore.remove(ctx, d.id) }) }
            }
        }
    }
}

@Composable
private fun Thumb(file: String, done: Boolean) {
    Box(Modifier.width(56.dp).height(35.dp).clip(RoundedCornerShape(10.dp)).background(coverBrush(file)), contentAlignment = Alignment.Center) {
        Icon(if (done) Icons.Filled.PlayArrow else Icons.Outlined.Download, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DownloadRow(d: Download, onPlay: () -> Unit, onRemove: () -> Unit) {
    val playable = d.state == DownloadState.DONE && d.localUri != null
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).then(if (playable) Modifier.clickable(onClick = onPlay) else Modifier).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Thumb(d.file, done = d.state == DownloadState.DONE)
        Column(Modifier.weight(1f)) {
            Text(d.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val p = d.progress ?: 0
            when (d.state) {
                DownloadState.DONE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                    Text("${fmtSize(d.size)} · ${d.whenLabel ?: "Saved"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DownloadState.FAILED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(13.dp))
                    Text("Failed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                DownloadState.QUEUED -> Text("Queued", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DownloadState.DOWNLOADING -> {
                    Text("$p%  ·  ${fmtSize((d.size * (1 - p / 100.0)).toLong())} left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(p / 100f).background(MaterialTheme.colorScheme.primary))
                    }
                }
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
