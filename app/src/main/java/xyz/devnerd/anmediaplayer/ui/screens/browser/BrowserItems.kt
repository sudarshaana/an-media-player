package xyz.devnerd.anmediaplayer.ui.screens.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.devnerd.anmediaplayer.data.Entry
import xyz.devnerd.anmediaplayer.data.EntryType
import xyz.devnerd.anmediaplayer.data.cleanTitle
import xyz.devnerd.anmediaplayer.ui.components.Poster
import xyz.devnerd.anmediaplayer.ui.components.coverBrush

/** Poster hero banner shown atop a media folder (cover image + title overlaid). */
@Composable
fun MediaHero(imageUrl: String?, title: String, sub: String, seed: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .aspectRatio(16f / 9f)
            .background(coverBrush(seed)),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (imageUrl != null) {
            coil3.compose.AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f),
                ),
            ),
        )
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

fun entryIcon(type: EntryType, isImageFolder: Boolean): ImageVector = when {
    isImageFolder -> Icons.Outlined.Image
    type == EntryType.DIR -> Icons.Outlined.Folder
    type == EntryType.VIDEO -> Icons.Filled.PlayCircle
    type == EntryType.SUBTITLE -> Icons.Outlined.Subtitles
    type == EntryType.IMAGE -> Icons.Outlined.Image
    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

private data class TileColors(val bg: Color, val fg: Color)

@Composable
private fun tileColors(type: EntryType): TileColors = with(MaterialTheme.colorScheme) {
    when (type) {
        EntryType.DIR -> TileColors(secondaryContainer, onSecondaryContainer)
        EntryType.VIDEO -> TileColors(primaryContainer, onPrimaryContainer)
        EntryType.SUBTITLE -> TileColors(tertiaryContainer, onTertiaryContainer)
        else -> TileColors(surfaceContainerHighest, onSurfaceVariant)
    }
}

/** 52dp leading tile: real thumbnail if available, else gradient art / flat tone. */
@Composable
private fun LeadingTile(entry: Entry, artSeed: String?, thumbUrl: String?, watched: Boolean) {
    val colors = tileColors(entry.type)
    val showArt = artSeed != null
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)).background(colors.bg), contentAlignment = Alignment.Center) {
        if (showArt) Box(Modifier.size(52.dp).background(coverBrush(artSeed!!)))
        if (thumbUrl != null) {
            coil3.compose.AsyncImage(
                model = thumbUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(52.dp),
            )
        }
        Icon(
            imageVector = entryIcon(entry.type, isImageFolder = showArt && entry.isDir),
            contentDescription = null,
            tint = if (showArt || thumbUrl != null) Color.White.copy(alpha = if (thumbUrl != null) 0f else 1f) else colors.fg,
            modifier = Modifier.size(24.dp),
        )
        if (watched) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
fun BrowseListRow(
    entry: Entry,
    meta: String,
    artSeed: String?,
    thumbUrl: String?,
    watched: Boolean,
    pct: Float,
    res: String?,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingTile(entry, artSeed, thumbUrl, watched)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                if (entry.isDir) cleanTitle(entry.name) else entry.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W700),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
            if (pct > 1f && pct < 96f) {
                Box(Modifier.padding(top = 7.dp).width(160.dp).height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        if (res != null) {
            Box(Modifier.padding(start = 8.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text(res, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (entry.isDir) {
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).size(20.dp))
        } else {
            IconButton(onClick = onMenu, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun BrowseGridCard(
    entry: Entry,
    posterSeed: String,
    icon: ImageVector,
    label: String,
    sub: String,
    watched: Boolean,
    pct: Float,
    res: String?,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Poster(
            seed = posterSeed,
            icon = icon,
            label = label,
            watched = watched,
            progress = if (pct > 1f && pct < 96f) pct.toInt() else null,
            badge = res,
            imageUrl = imageUrl,
        )
        Text(
            sub,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
    }
}
