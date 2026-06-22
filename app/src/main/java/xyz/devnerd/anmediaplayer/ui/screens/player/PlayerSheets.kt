package xyz.devnerd.anmediaplayer.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import xyz.devnerd.anmediaplayer.ui.components.focusHighlight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Sheet header: title + a D-pad-reachable close (X). TVs can't tap the scrim. */
@Composable
private fun SheetHeader(title: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * Centered option dialog for the player. Near-transparent dark panel so the
 * video stays visible behind, while content keeps full M3 contrast.
 */
@Composable
private fun PlayerDialog(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.width(380.dp).heightIn(max = 440.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
            shadowElevation = 12.dp,
        ) {
            Column(content = content)
        }
    }
}

/**
 * Inline action strip (not a dialog) — sits directly on the player surface,
 * under the top chrome, so the video stays fully visible and interactive.
 */
@Composable
fun MoreActionsBar(
    repeatOne: Boolean,
    onToggleRepeat: () -> Unit,
    onLock: () -> Unit,
    onResize: () -> Unit,
    onSpeed: () -> Unit,
    onShare: () -> Unit,
    onPiP: () -> Unit,
    onInfo: () -> Unit,
    onDownload: (() -> Unit)? = null,
    castSlot: (@Composable () -> Unit)? = null,
) {
    Box(
        Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.78f)),
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MoreActionItem(
                icon = if (repeatOne) Icons.Filled.Repeat else Icons.Outlined.Repeat,
                label = "Repeat",
                active = repeatOne,
                onClick = onToggleRepeat,
            )
            if (onDownload != null) MoreActionItem(Icons.Outlined.Download, "Download", onClick = onDownload)
            MoreActionItem(Icons.Outlined.Lock, "Lock", onClick = onLock)
            MoreActionItem(Icons.Outlined.AspectRatio, "Resize", onClick = onResize)
            MoreActionItem(Icons.Outlined.Speed, "Speed", onClick = onSpeed)
            if (castSlot != null) MoreActionSlot("Cast", content = castSlot)
            MoreActionItem(Icons.Outlined.PictureInPictureAlt, "Popup", onClick = onPiP)
            MoreActionItem(Icons.Outlined.Share, "Share", onClick = onShare)
            MoreActionItem(Icons.Outlined.Info, "Info", onClick = onInfo)
        }
    }
}

/** Small details dialog — opened via the Info action in [MoreActionsBar]. */
@Composable
fun InfoDialog(resolution: String?, videoCodec: String?, audioLabel: String?, onDismiss: () -> Unit) {
    PlayerDialog(onDismiss) {
        SheetHeader("Details", onDismiss)
        Column {
            InfoRow("Resolution", resolution ?: "—")
            InfoRow("Video", videoCodec ?: "—")
            InfoRow("Audio", audioLabel ?: "—")
            Box(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MoreAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        androidx.compose.material3.Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MoreActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    MoreActionSlot(label, onClick = onClick) {
        androidx.compose.material3.Icon(
            icon, null,
            tint = if (active) MaterialTheme.colorScheme.primary else Color.White,
        )
    }
}

@Composable
private fun MoreActionSlot(
    label: String,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.width(72.dp)
            .focusHighlight(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { content() }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
        Text(v, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Full-screen episode/playlist picker (a Dialog, not a bottom sheet). */
@Composable
fun PlaylistSheet(items: List<String>, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Playlist  ·  ${items.size}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                androidx.compose.material3.HorizontalDivider()
                val listState = androidx.compose.foundation.lazy.rememberLazyListState(
                    initialFirstVisibleItemIndex = current.coerceAtLeast(0),
                )
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
                    itemsIndexed(items) { i, label ->
                        val on = i == current
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(i) }
                                .then(if (on) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                if (on) Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary)
                                else Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (on) FontWeight.W700 else FontWeight.W500),
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(title: String, tracks: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit, footer: String? = null, onFooter: (() -> Unit)? = null) {
    PlayerDialog(onDismiss) {
        SheetHeader(title, onDismiss)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            tracks.forEachIndexed { i, tk ->
                val on = i == selected
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(i) }.padding(horizontal = 24.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.width(22.dp)) {
                        if (on) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        tk,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (on) FontWeight.W700 else FontWeight.W500),
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (footer != null) {
                Text(
                    footer,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { (onFooter ?: onDismiss)() }.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            Box(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleLoadSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searching: Boolean,
    error: String?,
    results: List<xyz.devnerd.anmediaplayer.data.OnlineSubtitle>,
    onPickResult: (Int) -> Unit,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerDialog(onDismiss) {
        SheetHeader("Load subtitle", onDismiss)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            MoreAction(Icons.Outlined.FileOpen, "Choose file from device", onPickFile)
            androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text(
                "Search online",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    placeholder = { Text("Search title…") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
                )
                IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Search") }
            }
            when {
                searching -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
                error != null -> Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                results.isEmpty() -> Text("No results yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                else -> results.forEachIndexed { i, r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPickResult(i) }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(Icons.Outlined.Subtitles, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(r.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(r.language.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Box(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeedSheet(speeds: List<Float>, selected: Float, onPick: (Float) -> Unit, onDismiss: () -> Unit) {
    PlayerDialog(onDismiss) {
        SheetHeader("Playback speed", onDismiss)
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 4,
        ) {
            speeds.forEach { s ->
                val on = s == selected
                Surface(
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onPick(s) },
                ) {
                    Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("${s}×", style = MaterialTheme.typography.titleSmall, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Box(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResizeSheet(resizes: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    PlayerDialog(onDismiss) {
        SheetHeader("Resize mode", onDismiss)
        resizes.forEach { (id, label) ->
            val on = id == selected
            Row(
                Modifier.fillMaxWidth().clickable { onPick(id) }.padding(horizontal = 24.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Outlined.AspectRatio, null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (on) FontWeight.W700 else FontWeight.W500), color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                if (on) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        Box(Modifier.height(24.dp))
    }
}
