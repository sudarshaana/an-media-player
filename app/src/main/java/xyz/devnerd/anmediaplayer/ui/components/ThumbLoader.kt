package xyz.devnerd.anmediaplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import xyz.devnerd.anmediaplayer.data.ThumbCache

/** Lazily resolve + observe a folder's cover-image URL (null until known/none). */
@Composable
fun rememberFolderThumb(serverId: String, path: List<String>): String? {
    val key = ThumbCache.key(serverId, path)
    LaunchedEffect(key) { ThumbCache.resolve(serverId, path) }
    return ThumbCache.cached(key)
}

/**
 * Poster for a media file's folder: first image in its folder, else walk up
 * parent folders (TV-series episodes live in a season subfolder whose poster
 * sits at the show root). Returns first hit, gradient-fallback when none.
 */
@Composable
fun rememberMediaPoster(serverId: String, path: List<String>, levelsUp: Int = 1): String? {
    // Fixed number of composable calls so recomposition stays stable.
    val thumbs = (0..levelsUp).map { up ->
        rememberFolderThumb(serverId, if (up <= path.size) path.dropLast(up) else emptyList())
    }
    return thumbs.firstOrNull { it != null }
}
