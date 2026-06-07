package xyz.devnerd.anmediaplayer.ui.screens.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import xyz.devnerd.anmediaplayer.data.Entry
import xyz.devnerd.anmediaplayer.data.EpisodeRef
import xyz.devnerd.anmediaplayer.data.SeasonRef
import xyz.devnerd.anmediaplayer.data.buildSeriesPlaylist
import xyz.devnerd.anmediaplayer.data.fmtDate
import xyz.devnerd.anmediaplayer.data.fmtDur
import xyz.devnerd.anmediaplayer.data.fmtSize
import xyz.devnerd.anmediaplayer.data.loadEpisodes
import xyz.devnerd.anmediaplayer.data.prettyName
import xyz.devnerd.anmediaplayer.data.progressKey
import xyz.devnerd.anmediaplayer.ui.components.coverBrush
import xyz.devnerd.anmediaplayer.ui.screens.browser.MediaHero

@Composable
fun SeriesView(
    serverId: String,
    seriesPath: List<String>,
    title: String,
    posterUrl: String?,
    seasons: List<SeasonRef>,
    isWatched: (String, Int?) -> Boolean,
    getProgress: (String) -> Int,
    onPlayEpisode: (List<EpisodeRef>, EpisodeRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableIntStateOf(0) }
    val season = seasons.getOrElse(selected) { seasons.first() }
    var episodes by remember(season.path) { mutableStateOf<List<Entry>?>(null) }
    LaunchedEffect(season.path) { episodes = loadEpisodes(serverId, season.path) }
    val scope = rememberCoroutineScope()

    // Same season number appearing twice = language/dub variants → don't chain across them.
    val hasVariants = seasons.groupingBy { it.number }.eachCount().any { it.value > 1 }

    fun play(ep: Entry) {
        scope.launch {
            val pl = if (hasVariants) {
                loadEpisodes(serverId, season.path).map { EpisodeRef(season.path, it.name, it.durSec ?: 0) }
            } else {
                buildSeriesPlaylist(serverId, seasons)
            }
            val start = pl.firstOrNull { it.path == season.path && it.file == ep.name }
                ?: EpisodeRef(season.path, ep.name, ep.durSec ?: 0)
            onPlayEpisode(pl, start)
        }
    }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 24.dp)) {
        item { MediaHero(posterUrl, title, "${seasons.size} season${if (seasons.size > 1) "s" else ""}", seriesPath.lastOrNull() ?: title) }

        if (seasons.size > 1) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons.size) { i ->
                        FilterChip(
                            selected = i == selected,
                            onClick = { selected = i },
                            label = { Text(seasons[i].label) },
                        )
                    }
                }
            }
        }

        val eps = episodes
        if (eps == null) {
            item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (eps.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Text("No episodes", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        } else {
            items(eps, key = { it.name }) { ep ->
                val key = progressKey(serverId, season.path, ep.name)
                val pct = if (ep.durSec != null && ep.durSec > 0) getProgress(key).toFloat() / ep.durSec * 100 else 0f
                EpisodeRow(
                    ep = ep,
                    posterUrl = posterUrl,
                    seed = ep.name,
                    watched = isWatched(key, ep.durSec),
                    pct = pct,
                    onClick = { play(ep) },
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Entry, posterUrl: String?, seed: String, watched: Boolean, pct: Float, onClick: () -> Unit) {
    val np = prettyName(ep.name)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(92.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(coverBrush(seed)), contentAlignment = Alignment.Center) {
            if (posterUrl != null) AsyncImage(model = posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(26.dp))
            if (watched) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(11.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(np.primary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(ep.durSec?.takeIf { it > 0 }?.let { fmtDur(it) }, ep.size?.let { fmtSize(it) }, ep.mtime?.let { fmtDate(it) }).joinToString("  ·  ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (pct > 1f && pct < 96f) Box(Modifier.padding(top = 6.dp).width(160.dp).height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth(pct / 100f).height(3.dp).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}
