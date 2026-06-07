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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random
import xyz.devnerd.anmediaplayer.data.Download
import xyz.devnerd.anmediaplayer.data.DownloadState
import xyz.devnerd.anmediaplayer.data.DownloadsStore
import xyz.devnerd.anmediaplayer.data.fmtSize
import xyz.devnerd.anmediaplayer.ui.components.coverBrush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    wifiOnly: Boolean = true,
    onPlay: (String, List<String>, String, Int?) -> Unit = { _, _, _, _ -> },
    onManage: () -> Unit = {},
) {
    val items = DownloadsStore.items

    // animate active downloads
    LaunchedEffect(Unit) {
        while (true) {
            delay(1100)
            items.toList().forEach { d ->
                if (d.state == DownloadState.DOWNLOADING) {
                    val np = (d.progress ?: 0) + 4 + Random.nextInt(0, 5)
                    if (np >= 100) DownloadsStore.setState(d.id, DownloadState.DONE, progress = 100, whenLabel = "Just now")
                    else DownloadsStore.setState(d.id, DownloadState.DOWNLOADING, progress = np)
                }
            }
        }
    }

    val active = items.filter { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED }
    val done = items.filter { it.state == DownloadState.DONE }
    val used = done.sumOf { it.size }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Downloads") }, actions = { IconButton(onClick = {}) { Icon(Icons.Outlined.MoreVert, "More") } }) },
    ) { inner ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding(), bottom = 28.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 0.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 12.dp),
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

            if (active.isNotEmpty()) {
                item { SectionLabel("ACTIVE") }
                items(active, key = { it.id }) { d -> ActiveRow(d) }
            }

            item { SectionLabel("ON THIS DEVICE") }
            if (done.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Download, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                        }
                        Text("Nothing downloaded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(done, key = { it.id }) { d -> DoneRow(d, onPlay = { onPlay(d.server, d.path, d.file, d.durSec) }, onRemove = { DownloadsStore.remove(d.id) }) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 10.dp))

@Composable
private fun Thumb(file: String, done: Boolean, size: Int = 56) {
    Box(Modifier.width(size.dp).height((size * 0.62f).dp).clip(RoundedCornerShape(10.dp)).background(coverBrush(file)), contentAlignment = Alignment.Center) {
        Icon(if (done) Icons.Filled.PlayArrow else Icons.Outlined.Download, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ActiveRow(d: Download) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Thumb(d.file, done = false)
            Column(Modifier.weight(1f)) {
                Text(d.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val p = d.progress ?: 0
                Text(
                    if (d.state == DownloadState.QUEUED) "Queued" else "$p%  ·  ${fmtSize((d.size * (1 - p / 100.0)).toLong())} left  ·  8.4 MB/s",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
                )
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    if (d.state == DownloadState.DOWNLOADING) Box(Modifier.fillMaxHeight().fillMaxWidth((d.progress ?: 0) / 100f).background(MaterialTheme.colorScheme.primary))
                }
            }
            IconButton(onClick = {
                if (d.state == DownloadState.DOWNLOADING) DownloadsStore.setState(d.id, DownloadState.QUEUED)
                else DownloadsStore.remove(d.id)
            }) {
                Icon(if (d.state == DownloadState.DOWNLOADING) Icons.Filled.Pause else Icons.Outlined.Close, if (d.state == DownloadState.DOWNLOADING) "Pause" else "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DoneRow(d: Download, onPlay: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onPlay).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Thumb(d.file, done = true)
        Column(Modifier.weight(1f)) {
            Text(d.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(d.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 3.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                Text("${fmtSize(d.size)} · ${d.whenLabel ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
