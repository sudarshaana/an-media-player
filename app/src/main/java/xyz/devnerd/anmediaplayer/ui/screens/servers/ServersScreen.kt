package xyz.devnerd.anmediaplayer.ui.screens.servers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.devnerd.anmediaplayer.data.AppRepo
import xyz.devnerd.anmediaplayer.data.Server

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    modifier: Modifier = Modifier,
    onOpenServer: (String) -> Unit = {},
    onAddServer: () -> Unit = {},
    onFindServers: () -> Unit = {},
) {
    val servers = AppRepo.servers
    var menuFor by remember { mutableStateOf<Server?>(null) }
    val ctx = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Servers") },
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                actions = {
                    IconButton(onClick = onFindServers) { Icon(Icons.Outlined.TravelExplore, "Find servers for my ISP") }
                    IconButton(onClick = {}) { Icon(Icons.Outlined.History, "History") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add server") },
                icon = { Icon(Icons.Outlined.Add, null) },
                onClick = onAddServer,
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(servers, key = { it.id }) { s ->
                ServerRow(s = s, onOpen = { onOpenServer(s.id) }, onMenu = { menuFor = s })
            }
            item { HintCard() }
        }
    }

    menuFor?.let { server ->
        ServerContextSheet(
            server = server,
            onDismiss = { menuFor = null },
            onOpen = { onOpenServer(server.id); menuFor = null },
            onEdit = { menuFor = null },
            onCopy = {
                copyToClipboard(ctx, server.url)
                Toast.makeText(ctx, "Address copied", Toast.LENGTH_SHORT).show()
                menuFor = null
            },
            onFavorite = { AppRepo.toggleFavorite(server.id); menuFor = null },
            onRemove = { AppRepo.removeServer(server.id); menuFor = null },
        )
    }
}

@Composable
private fun ServerRow(s: Server, onOpen: () -> Unit, onMenu: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Dns, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (s.favorite) Icon(Icons.Filled.Star, "Favorite", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                }
                Text(s.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    xyz.devnerd.anmediaplayer.ui.components.ServerStatusBadge(xyz.devnerd.anmediaplayer.data.ServerHealth.status[s.id])
                    if (s.auth) Chip("${s.user}", leading = Icons.Outlined.Lock)
                    Chip(s.parser)
                }
            }
            IconButton(onClick = onMenu) { Icon(Icons.Outlined.MoreVert, "More") }
        }
    }
}

@Composable
private fun Chip(text: String, primary: Boolean = false, leading: ImageVector? = null) {
    val bg = if (primary) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (primary) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (leading != null) Icon(leading, null, tint = fg, modifier = Modifier.size(11.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

@Composable
private fun HintCard() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Browse any HTTP index, h5ai, FTP or WebDAV listing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerContextSheet(
    server: Server,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onFavorite: () -> Unit,
    onRemove: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                server.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SheetAction(Icons.AutoMirrored.Outlined.OpenInNew, "Open", onOpen)
            SheetAction(Icons.Outlined.Edit, "Edit", onEdit)
            SheetAction(Icons.Outlined.ContentCopy, "Copy address", onCopy)
            SheetAction(
                if (server.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                if (server.favorite) "Unfavorite" else "Favorite",
                onFavorite,
            )
            SheetAction(Icons.Outlined.Delete, "Remove", onRemove, destructive = true)
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit, destructive: Boolean = false) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, null, tint = tint)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("server address", text))
}
