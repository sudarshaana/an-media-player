package xyz.devnerd.anmediaplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.devnerd.anmediaplayer.data.posterFor

/** Deterministic diagonal cover gradient seeded from a name (posterFor c1 → c2). */
fun coverBrush(seed: String): Brush {
    val p = posterFor(seed)
    return Brush.linearGradient(
        colors = listOf(p.c1, p.c2),
        start = Offset.Zero,
        end = Offset.Infinite,
    )
}

/** Bottom-up readability scrim used over covers. */
private val labelScrim = Brush.verticalGradient(
    0.4f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.55f),
)

/**
 * Library poster card: gradient cover, scrim, corner glyph, optional label/sub,
 * badge, watched check, and a bottom progress bar. Mirrors Poster() in ui.jsx.
 */
@Composable
fun Poster(
    seed: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    sub: String? = null,
    ratio: Float = 2f / 3f,
    cornerRadius: Int = 12,
    icon: ImageVector = Icons.Outlined.Movie,
    watched: Boolean = false,
    progress: Int? = null,
    badge: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(coverBrush(seed)),
    ) {
        Box(Modifier.fillMaxSize().background(labelScrim))

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.32f),
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )

        if (label != null) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }

        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(badge, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }

        if (watched) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Watched",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (progress != null && progress > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.Black.copy(alpha = 0.4f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress / 100f)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
