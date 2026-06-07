package xyz.devnerd.anmediaplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import xyz.devnerd.anmediaplayer.data.MovieInfo
import xyz.devnerd.anmediaplayer.data.OmdbRepo

/** Fetch (once, cached) movie/series metadata for a folder/file name. Null name = disabled. */
@Composable
fun rememberMovieInfo(name: String?): MovieInfo? {
    if (name.isNullOrBlank()) return null
    var info by remember(name) { mutableStateOf(OmdbRepo.cached(name)) }
    LaunchedEffect(name) { info = OmdbRepo.fetch(name) }
    return info
}

/** Small ⭐ IMDb-rating pill for overlay on dark cover art. */
@Composable
fun RatingChip(rating: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xCCF5C518)).padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, null, tint = Color(0xFF1A1A1A), modifier = Modifier.size(11.dp).padding(end = 2.dp))
        Text(rating, style = MaterialTheme.typography.labelSmall, color = Color(0xFF1A1A1A))
    }
}
