package xyz.devnerd.anmediaplayer.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import xyz.devnerd.anmediaplayer.data.fmtDur
import xyz.devnerd.anmediaplayer.data.prettyName
import xyz.devnerd.anmediaplayer.ui.components.coverBrush
import xyz.devnerd.anmediaplayer.ui.components.focusHighlight

@Composable
fun ResumeDialog(posSec: Int, durSec: Int, onResume: () -> Unit, onStartOver: () -> Unit, onDismiss: () -> Unit) {
    val pct = (posSec.toFloat() / durSec).coerceIn(0f, 1f)
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp).width(300.dp)) {
                Text("Resume playback?", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Row(Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                    Text("You stopped at ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtDur(posSec), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), color = MaterialTheme.colorScheme.onSurface)
                    Text(" of ${fmtDur(durSec)}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth(pct).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
                val resumeFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) { runCatching { resumeFocus.requestFocus() } }
                Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onResume, modifier = Modifier.fillMaxWidth().focusRequester(resumeFocus).focusHighlight(RoundedCornerShape(20.dp))) {
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp)); Text("  Resume from ${fmtDur(posSec)}")
                    }
                    FilledTonalButton(onClick = onStartOver, modifier = Modifier.fillMaxWidth().focusHighlight(RoundedCornerShape(20.dp))) {
                        Icon(Icons.Outlined.Replay, null, modifier = Modifier.size(18.dp)); Text("  Start over")
                    }
                }
            }
        }
    }
}

@Composable
fun EndPanel(
    finishedFile: String,
    nextFile: String?,
    autoPlay: Boolean,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val np = prettyName(finishedFile)
    val nextNp = nextFile?.let { prettyName(it) }
    var count by remember(nextFile) { mutableIntStateOf(8) }
    LaunchedEffect(nextFile, autoPlay) {
        if (nextFile == null || !autoPlay) return@LaunchedEffect
        count = 8
        while (count > 0) { delay(1000); count--; if (count == 0) onNext() }
    }

    Box(Modifier.fillMaxSize().background(coverBrush(finishedFile))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Icon(Icons.Outlined.Close, "Close", tint = Color.White) }
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
            Column {
                Text("FINISHED", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
                Text(np.primary, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(np.secondary, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
            }
            if (nextNp != null && nextFile != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp)).clickable(onClick = onNext).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.width(116.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(coverBrush(nextFile)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("UP NEXT", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                        Text(nextNp.primary, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                        Text(nextNp.secondary, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                    }
                    if (autoPlay) {
                        Box(Modifier.size(40.dp).clip(CircleShape).border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("$count", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        }
                    }
                }
            } else {
                Text("That was the last item in this folder.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (nextFile != null) {
                    Button(onClick = onNext) { Icon(Icons.Filled.SkipNext, null, modifier = Modifier.size(18.dp)); Text(if (autoPlay) "  Play next now" else "  Play next") }
                }
                OutlinedButton(onClick = onRestart, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                    Icon(Icons.Outlined.Replay, null, modifier = Modifier.size(18.dp)); Text("  Restart")
                }
                TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Icon(Icons.AutoMirrored.Outlined.List, null, modifier = Modifier.size(18.dp)); Text("  Back to list")
                }
            }
        }
    }
}
