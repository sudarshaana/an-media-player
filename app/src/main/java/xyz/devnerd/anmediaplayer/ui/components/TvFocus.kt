package xyz.devnerd.anmediaplayer.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Visible D-pad focus state for TV: a bright border + slight scale when focused.
 * Put it BEFORE `.clickable {}` so the clickable's focus drives it.
 */
fun Modifier.focusHighlight(shape: Shape = RoundedCornerShape(14.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .then(if (focused) Modifier.scale(1.04f).border(3.dp, accent, shape) else Modifier)
}
