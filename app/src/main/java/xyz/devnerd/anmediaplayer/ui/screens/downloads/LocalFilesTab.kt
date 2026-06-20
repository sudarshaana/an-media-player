package xyz.devnerd.anmediaplayer.ui.screens.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.devnerd.anmediaplayer.data.DownloadsStore
import xyz.devnerd.anmediaplayer.data.LocalVideo
import xyz.devnerd.anmediaplayer.data.LocalVideosStore
import xyz.devnerd.anmediaplayer.data.DeleteAction
import xyz.devnerd.anmediaplayer.data.fmtDur
import xyz.devnerd.anmediaplayer.data.fmtSize
import xyz.devnerd.anmediaplayer.data.relativeTime
import xyz.devnerd.anmediaplayer.ui.components.focusHighlight

private val videoPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasVideoPermission(ctx: android.content.Context) =
    ContextCompat.checkSelfPermission(ctx, videoPermission) == PackageManager.PERMISSION_GRANTED

enum class LocalSortBy(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    NAME("Name", Icons.Outlined.SortByAlpha),
    TIME("Time", Icons.Outlined.Schedule),
    SIZE("Size", Icons.Outlined.Storage),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFilesTab(
    modifier: Modifier = Modifier,
    onPlay: (LocalVideo, List<LocalVideo>) -> Unit,
    query: String = "",
    sortBy: LocalSortBy = LocalSortBy.TIME,
    asc: Boolean = false,
) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasVideoPermission(ctx)) }
    var videos by remember { mutableStateOf<List<LocalVideo>?>(null) }
    var menuFor by remember { mutableStateOf<LocalVideo?>(null) }
    var deleting by remember { mutableStateOf<LocalVideo?>(null) }
    var details by remember { mutableStateOf<LocalVideo?>(null) }
    var pendingDelete by remember { mutableStateOf<LocalVideo?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
    }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(videoPermission) }
    LaunchedEffect(granted) { if (granted) videos = LocalVideosStore.scan(ctx) }

    val shown = videos?.filter { query.isBlank() || it.displayName.contains(query, ignoreCase = true) }
        ?.sortedWith(
            when (sortBy) {
                LocalSortBy.NAME -> compareBy { it.displayName.lowercase() }
                LocalSortBy.TIME -> compareBy { it.dateModified }
                LocalSortBy.SIZE -> compareBy { it.sizeBytes }
            },
        )
        ?.let { if (asc) it else it.asReversed() }

    // System consent flow for deleting media the app doesn't own (API 30+, and API 29 fallback).
    val deleteConsentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val v = pendingDelete
        pendingDelete = null
        if (result.resultCode == android.app.Activity.RESULT_OK && v != null) {
            videos = videos?.filterNot { it.id == v.id }
            DownloadsStore.removeByLocalPath(ctx, v.path)
        }
    }

    fun performDelete(v: LocalVideo) {
        when (val action = LocalVideosStore.delete(ctx, v)) {
            is DeleteAction.Deleted -> {
                videos = videos?.filterNot { it.id == v.id }
                DownloadsStore.removeByLocalPath(ctx, v.path)
            }
            is DeleteAction.NeedsConsent -> {
                pendingDelete = v
                deleteConsentLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(action.intentSender).build())
            }
            is DeleteAction.Failed -> android.widget.Toast.makeText(ctx, "Couldn't delete file", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    PullToRefreshBox(
        modifier = modifier.fillMaxSize(),
        isRefreshing = refreshing,
        onRefresh = {
            if (!granted) return@PullToRefreshBox
            refreshing = true
        },
    ) {
        when {
            !granted -> EmptyState(Modifier, "Permission needed", "Allow access to videos to see files stored on this device.") {
                permLauncher.launch(videoPermission)
            }
            videos == null -> EmptyState(Modifier, "Scanning…", "Looking for videos on this device.")
            videos!!.isEmpty() -> EmptyState(Modifier, "No local videos", "Videos found on this device will show up here.")
            shown != null && shown.isEmpty() -> EmptyState(Modifier, "No matches", "No local videos match \"$query\".")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
                items(shown!!, key = { it.id }) { v ->
                    LocalVideoRow(
                        v,
                        onPlay = { onPlay(v, shown) },
                        menuExpanded = menuFor?.id == v.id,
                        onMore = { menuFor = v },
                        onDismissMenu = { menuFor = null },
                        onDetails = { details = v; menuFor = null },
                        onShare = {
                            menuFor = null
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = v.mimeType ?: "video/*"
                                putExtra(android.content.Intent.EXTRA_STREAM, v.uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(android.content.Intent.createChooser(send, "Share ${v.displayName}"))
                        },
                        onDelete = { deleting = v; menuFor = null },
                    )
                }
            }
        }
    }

    LaunchedEffect(refreshing) {
        if (refreshing) {
            videos = LocalVideosStore.scan(ctx)
            delay(300)
            refreshing = false
        }
    }

    deleting?.let { v ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete video?") },
            text = { Text("${v.displayName} will be permanently removed from this device.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { performDelete(v); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    details?.let { v -> DetailsDialog(v, onDismiss = { details = null }) }
}

@Composable
private fun EmptyState(modifier: Modifier, title: String, body: String, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.VideoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 16.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        if (onAction != null) Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text("Grant access") }
    }
}

@Composable
private fun LocalVideoRow(
    v: LocalVideo,
    onPlay: () -> Unit,
    menuExpanded: Boolean,
    onMore: () -> Unit,
    onDismissMenu: () -> Unit,
    onDetails: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().focusHighlight(RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).clickable(onClick = onPlay).padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VideoThumb(v)
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(v.displayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(relativeTime(v.dateModified), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Box {
            IconButton(onClick = onMore, modifier = Modifier.focusHighlight(androidx.compose.foundation.shape.CircleShape).size(36.dp)) {
                Icon(Icons.Outlined.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Anchored to this Box (the icon itself), so it opens right under/over the icon, not the whole row.
            DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(text = { Text("Details") }, leadingIcon = { Icon(Icons.Outlined.Info, null) }, onClick = onDetails)
                DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = onShare)
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = onDelete)
            }
        }
    }
}

private val videoThumbCache = android.util.LruCache<Long, Bitmap>(60)

@Composable
private fun VideoThumb(v: LocalVideo) {
    val ctx = LocalContext.current
    var bmp by remember(v.id) { mutableStateOf(videoThumbCache.get(v.id)) }
    LaunchedEffect(v.id) {
        if (bmp == null) {
            bmp = withContext(Dispatchers.IO) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(v.uri, android.util.Size(440, 280), null)
                    else @Suppress("DEPRECATION") MediaStore.Video.Thumbnails.getThumbnail(ctx.contentResolver, v.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                }.getOrNull()
            }?.also { videoThumbCache.put(v.id, it) }
        }
    }
    Box(Modifier.width(132.dp).height(80.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        val b = bmp
        if (b != null) {
            androidx.compose.foundation.Image(b.asImageBitmap(), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.VideoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).size(24.dp))
        }
        // Gradient keeps the duration/size legible over bright thumbnail frames.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f))))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                "${fmtDur((v.durationMs / 1000).toInt())} · ${fmtSize(v.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailsDialog(v: LocalVideo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var codec by remember(v.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(v.id) { codec = withContext(Dispatchers.IO) { LocalVideosStore.videoCodec(ctx, v.uri) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(v.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailBlock("Path", v.path.ifBlank { v.uri.toString() })
                DetailRow("Size", fmtSize(v.sizeBytes))
                DetailRow("Duration", fmtDur((v.durationMs / 1000).toInt()))
                DetailRow("Modified", relativeTime(v.dateModified))
                if (v.width > 0 && v.height > 0) DetailRow("Resolution", "${v.width}×${v.height}")
                DetailRow("Codec", codec ?: "—")
                DetailRow("Format", v.mimeType ?: "—")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Label-above-value layout for long unbroken strings (paths) that would otherwise get clipped in a side-by-side row. */
@Composable
private fun DetailBlock(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
    }
}
