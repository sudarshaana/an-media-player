package xyz.devnerd.anmediaplayer.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.devnerd.anmediaplayer.data.AppRepo
import xyz.devnerd.anmediaplayer.data.Bookmark
import xyz.devnerd.anmediaplayer.data.ContinueItem
import xyz.devnerd.anmediaplayer.data.Server
import xyz.devnerd.anmediaplayer.data.cleanTitle
import xyz.devnerd.anmediaplayer.ui.components.coverBrush
import xyz.devnerd.anmediaplayer.ui.components.focusHighlight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    bookmarks: List<Bookmark> = AppRepo.bookmarks,
    onPlay: (ContinueItem) -> Unit = {},
    onOpenBrowse: (server: String, path: List<String>) -> Unit = { _, _ -> },
    onOpenServer: (String) -> Unit = {},
    onOpenConnect: () -> Unit = {},
    onManageServers: () -> Unit = {},
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val servers: List<Server> = AppRepo.servers
    val cont = AppRepo.continueItems()
        .map { it to ((it.posSec.toFloat() / it.durSec) * 100).toInt() }
    fun offlineToast(serverId: String) {
        val n = AppRepo.serverById(serverId)?.name ?: "Server"
        android.widget.Toast.makeText(ctx, "$n is offline", android.widget.Toast.LENGTH_SHORT).show()
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // header
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
            Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text("Watch", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        }

        if (cont.isNotEmpty()) {
            SectionHead("Continue watching")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(cont.size) { i ->
                    val (item, pct) = cont[i]
                    val offline = xyz.devnerd.anmediaplayer.data.ServerHealth.isOffline(item.server)
                    ContinueCard(item, pct, offline = offline, onClick = { if (offline) offlineToast(item.server) else onPlay(item) })
                }
            }
        }

        if (bookmarks.isNotEmpty()) {
            SectionHead("Bookmarks")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(bookmarks.size) { i ->
                    val b = bookmarks[i]
                    val name = b.path.lastOrNull() ?: AppRepo.serverById(b.server)?.name ?: "Folder"
                    val thumb = xyz.devnerd.anmediaplayer.ui.components.rememberFolderThumb(b.server, b.path)
                    val offline = xyz.devnerd.anmediaplayer.data.ServerHealth.isOffline(b.server)
                    // Category cover only for top-level (server-root) folders, never deep paths.
                    val catCover = if (b.path.size == 1) xyz.devnerd.anmediaplayer.ui.components.categoryCover(name) else null
                    BookmarkCard(name = name, imageModel = thumb ?: catCover, offline = offline, onClick = { if (offline) offlineToast(b.server) else onOpenBrowse(b.server, b.path) })
                }
            }
        }

        if (servers.isNotEmpty()) {
            SectionHead("Your servers", action = "Manage", onAction = onManageServers)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEach { s ->
                    ServerRow(serverId = s.id, name = s.name, url = s.url, auth = s.auth, onClick = { onOpenServer(s.id) })
                }
            }
        }

        if (cont.isEmpty() && bookmarks.isEmpty() && servers.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 80.dp, start = 32.dp, end = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("No servers yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("Add a server to browse and play remote media.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.Button(onClick = onOpenConnect, modifier = Modifier.padding(top = 4.dp)) { Text("Add server") }
            }
        }
    }
}

@Composable
private fun SectionHead(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ContinueCard(item: ContinueItem, pct: Int, offline: Boolean, onClick: () -> Unit) {
    val minsLeft = ((item.durSec - item.posSec) / 60).coerceAtLeast(0)
    Column(Modifier.width(270.dp).focusHighlight(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(coverBrush(item.title)),
        ) {
            if (item.coverUrl != null) {
                coil3.compose.AsyncImage(
                    model = item.coverUrl,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.5f))),
                ),
            )
            if (offline) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
                OfflinePill(Modifier.align(Alignment.Center))
            } else {
                Box(
                    Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text("$minsLeft min left", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(pct / 100f).height(4.dp).background(MaterialTheme.colorScheme.primary))
            }
        }
        Column(Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BookmarkCard(name: String, imageModel: Any?, offline: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(140.dp)
            .aspectRatio(2f / 3f)
            .focusHighlight(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(coverBrush(name))
            .clickable(onClick = onClick),
    ) {
        if (imageModel != null) {
            coil3.compose.AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.9f)),
            ),
        )
        if (offline) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
            OfflinePill(Modifier.align(Alignment.Center))
        }
        Icon(Icons.Filled.Bookmark, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp))
        Text(
            cleanTitle(name),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W700),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun ServerRow(serverId: String, name: String, url: String, auth: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().focusHighlight(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Dns, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (auth) Icon(Icons.Outlined.Lock, "Requires sign-in", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            xyz.devnerd.anmediaplayer.ui.components.ServerStatusBadge(xyz.devnerd.anmediaplayer.data.ServerHealth.status[serverId])
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun OfflinePill(modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = Color.White, modifier = Modifier.size(15.dp))
        Text("Offline", style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

private fun greeting(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..20 -> "Good evening"
    else -> "Good night"
}
