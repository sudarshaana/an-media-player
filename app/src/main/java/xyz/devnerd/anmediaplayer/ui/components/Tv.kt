package xyz.devnerd.anmediaplayer.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** True on Android TV / Leanback devices (no touch, D-pad navigation). */
fun isTvDevice(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/** Provided at the app root; read with `LocalIsTv.current` to branch TV-specific UI. */
val LocalIsTv = staticCompositionLocalOf { false }

/**
 * D-pad focus indication for TV: paints a visible ring + faint fill on the focused
 * element. Provided as `LocalIndication` at the app root on TV so every clickable
 * Surface/row/card/button across the app shows where focus currently is — phones
 * keep the default ripple. Without this, D-pad focus moves invisibly and reads as
 * "nothing happens" on a remote.
 */
@Composable
fun rememberTvFocusIndication(): Indication {
    val color = MaterialTheme.colorScheme.primary
    return remember(color) { TvFocusIndication(color) }
}

private class TvFocusIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        TvFocusNode(interactionSource, color)

    override fun equals(other: Any?) = other is TvFocusIndication && other.color == color
    override fun hashCode() = color.hashCode()
}

private class TvFocusNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
) : Modifier.Node(), DrawModifierNode {
    private var focused = false

    override fun onAttach() {
        coroutineScope.launch {
            var count = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> count++
                    is FocusInteraction.Unfocus -> count--
                }
                val now = count > 0
                if (now != focused) {
                    focused = now
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (focused) {
            val stroke = 3.dp.toPx()
            val radius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
            drawRoundRect(color = color.copy(alpha = 0.16f), cornerRadius = radius)
            drawRoundRect(
                color = color,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = radius,
                style = Stroke(width = stroke),
            )
        }
    }
}
