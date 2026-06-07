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
