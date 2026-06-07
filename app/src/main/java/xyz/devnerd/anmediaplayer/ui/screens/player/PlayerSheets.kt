package xyz.devnerd.anmediaplayer.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(
    resolution: String?,
    videoCodec: String?,
    audioLabel: String?,
    repeatOne: Boolean,
    onToggleRepeat: () -> Unit,
    onLock: () -> Unit,
    onResize: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader("More", onDismiss)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().clickable { onToggleRepeat() }.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                androidx.compose.material3.Icon(Icons.Outlined.Repeat, null, tint = if (repeatOne) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Repeat", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = repeatOne, onCheckedChange = { onToggleRepeat() })
            }
            if (onDownload != null) MoreAction(Icons.Outlined.Download, "Download", onDownload)
            MoreAction(Icons.Outlined.Lock, "Lock screen", onLock)
            MoreAction(Icons.Outlined.AspectRatio, "Resize", onResize)
            androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            InfoRow("Resolution", resolution ?: "—")
            InfoRow("Video", videoCodec ?: "—")
            InfoRow("Audio", audioLabel ?: "—")
            Box(Modifier.height(32.dp))
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
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
        Text(v, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSheet(items: List<String>, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader("Playlist  ·  ${items.size}", onDismiss)
        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
            itemsIndexed(items) { i, label ->
                val on = i == current
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(i) }.padding(horizontal = 24.dp, vertical = 12.dp),
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
        Box(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(title: String, tracks: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit, footer: String? = null) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                    modifier = Modifier.clickable { onDismiss() }.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            Box(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeedSheet(speeds: List<Float>, selected: Float, onPick: (Float) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
