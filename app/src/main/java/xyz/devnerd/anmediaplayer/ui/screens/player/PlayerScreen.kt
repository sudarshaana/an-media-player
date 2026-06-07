package xyz.devnerd.anmediaplayer.ui.screens.player

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import xyz.devnerd.anmediaplayer.data.PrettyName
import xyz.devnerd.anmediaplayer.data.fmtDur
import xyz.devnerd.anmediaplayer.settings.PlayerLayout
import kotlin.math.roundToInt

private enum class PlayerSheet { SUBTITLE, AUDIO, SPEED, RESIZE }
private data class Gesture(val left: Boolean, val value: Float)
private data class SeekFx(val forward: Boolean, val token: Long)

private fun subtitleMime(url: String): String = when (url.substringAfterLast('.').lowercase()) {
    "vtt" -> MimeTypes.TEXT_VTT
    "ssa", "ass" -> MimeTypes.TEXT_SSA
    else -> MimeTypes.APPLICATION_SUBRIP
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    title: String,
    subtitleLabel: String,
    streamUrl: String,
    subtitleUrl: String?,
    startAt: Int,
    durSec: Int,
    layout: PlayerLayout,
    subtitlesDefault: Boolean,
    keepScreenOn: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onEnded: () -> Unit,
    saveProgress: (Int, Int) -> Unit,
) {
    val context = LocalContext.current

    val exo = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            val item = MediaItem.Builder().setUri(streamUrl).apply {
                if (subtitleUrl != null) setSubtitleConfigurations(
                    listOf(
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                            .setMimeType(subtitleMime(subtitleUrl))
                            .setLanguage("en")
                            .setSelectionFlags(if (subtitlesDefault) C.SELECTION_FLAG_DEFAULT else 0)
                            .build(),
                    ),
                )
            }.build()
            setMediaItem(item)
            prepare()
            seekTo(startAt * 1000L)
            playWhenReady = true
        }
    }

    var time by remember(streamUrl) { mutableFloatStateOf(startAt.toFloat()) }
    var duration by remember(streamUrl) { mutableIntStateOf(durSec) }
    var bufferedPct by remember(streamUrl) { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var showUI by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var subIndex by remember { mutableIntStateOf(if (subtitlesDefault) 1 else 0) }
    var audioIndex by remember { mutableIntStateOf(0) }
    var resize by remember { mutableStateOf("fit") }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var seekFx by remember { mutableStateOf<SeekFx?>(null) }
    var gesture by remember { mutableStateOf<Gesture?>(null) }
    var uiPoke by remember { mutableIntStateOf(0) }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) onEnded()
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener); exo.release() }
    }

    // poll position/duration/buffer
    LaunchedEffect(exo) {
        while (true) {
            if (!scrubbing) {
                time = exo.currentPosition / 1000f
                val d = exo.duration
                if (d > 0) duration = (d / 1000).toInt()
                bufferedPct = if (exo.duration > 0) exo.bufferedPosition.toFloat() / exo.duration else 0f
            }
            saveProgress(time.toInt(), duration)
            delay(500)
        }
    }
    // auto-hide
    LaunchedEffect(showUI, playing, sheet, uiPoke) {
        if (showUI && playing && sheet == null && !locked) { delay(3400); showUI = false }
    }

    fun poke() { showUI = true; uiPoke++ }
    fun seekBy(d: Int) { exo.seekTo((exo.currentPosition + d * 1000L).coerceAtLeast(0)) }
    fun togglePlay() { if (exo.isPlaying) exo.pause() else exo.play(); poke() }
    fun applySub(idx: Int) {
        subIndex = idx
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, idx == 0).build()
    }
    fun applySpeed(s: Float) { speed = s; exo.setPlaybackSpeed(s) }

    val subTracks = listOf("Off", if (subtitleUrl != null) "English  ·  ${subtitleUrl.substringAfterLast('/')}" else "English (auto)", "English (SDH)", "Español")
    val audioTracks = listOf("English  ·  5.1 EAC3", "Japanese  ·  2.0 AAC", "Director commentary")
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val resizes = listOf("fit" to "Fit", "fill" to "Fill", "zoom" to "Zoom")

    val cinema = layout == PlayerLayout.CINEMA
    val minimal = layout == PlayerLayout.MINIMAL
    val onDark = if (cinema) MaterialTheme.colorScheme.onSurface else Color.White
    val pct = if (duration > 0) (time / duration).coerceIn(0f, 1f) else 0f
    val buffered = bufferedPct.coerceIn(pct, 1f)
    val np = PrettyName(title, subtitleLabel)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // ── video surface (Media3) ──
        val resizeMode = when (resize) {
            "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        Box(
            Modifier
                .fillMaxSize()
                .then(if (cinema) Modifier.fillMaxHeight(0.46f).align(Alignment.TopCenter) else Modifier)
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { showUI = !showUI; if (showUI) uiPoke++ },
                        onDoubleTap = { off ->
                            if (locked) return@detectTapGestures
                            val third = size.width / 3f
                            if (off.x < third || off.x > 2 * third) {
                                val fwd = off.x > 2 * third
                                seekBy(if (fwd) 10 else -10)
                                seekFx = SeekFx(fwd, off.x.toLong() + time.toLong())
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        player = exo
                        useController = false
                        setKeepScreenOn(keepScreenOn)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.resizeMode = resizeMode },
                modifier = Modifier.fillMaxSize(),
            )
            if (buffering) CircularProgressIndicator(color = Color.White)
        }

        // gesture zones (brightness left, volume right)
        if (!locked && !cinema) {
            Box(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.34f)
                    .pointerInput(Unit) {
                        var v = 0.7f
                        detectVerticalDragGestures(onDragStart = { v = 0.7f; gesture = Gesture(true, v) }, onDragEnd = { gesture = null }) { _, dy -> v = (v - dy / 600f).coerceIn(0f, 1f); gesture = Gesture(true, v) }
                    },
            )
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.34f)
                    .pointerInput(Unit) {
                        var v = 0.6f
                        detectVerticalDragGestures(onDragStart = { v = 0.6f; gesture = Gesture(false, v) }, onDragEnd = { gesture = null }) { _, dy -> v = (v - dy / 600f).coerceIn(0f, 1f); gesture = Gesture(false, v) }
                    },
            )
        }

        seekFx?.let { fx ->
            LaunchedEffect(fx.token) { delay(500); if (seekFx?.token == fx.token) seekFx = null }
            Box(Modifier.fillMaxSize().padding(horizontal = 44.dp), contentAlignment = if (fx.forward) Alignment.CenterEnd else Alignment.CenterStart) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (fx.forward) Icons.Outlined.Forward10 else Icons.Outlined.Replay10, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Text(if (fx.forward) "+10s" else "-10s", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }

        gesture?.let { g ->
            Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = if (g.left) Alignment.CenterStart else Alignment.CenterEnd) {
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(if (g.left) Icons.Outlined.Brightness6 else Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Box(Modifier.width(6.dp).height(110.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(g.value).clip(RoundedCornerShape(3.dp)).background(Color.White))
                    }
                    Text("${(g.value * 100).roundToInt()}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }

        // ── locked overlay ──
        if (locked) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { showUI = !showUI } }, contentAlignment = Alignment.Center) {
                AnimatedVisibility(showUI, enter = fadeIn(), exit = fadeOut()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures { locked = false; poke() } }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Lock, "Unlock", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Text("Tap to unlock", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            return@Box
        }

        AnimatedVisibility(showUI, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            TopChrome(np.primary, subtitleLabel, cinema, onClose)
        }
        if (!minimal) {
            AnimatedVisibility(showUI, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                CenterTransport(playing, hasPrev, hasNext, onPrev, onNext, onBack10 = { seekBy(-10) }, onFwd10 = { seekBy(10) }, onToggle = { togglePlay() })
            }
        }
        if (cinema) {
            AnimatedVisibility(showUI, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                CinemaDeck(
                    np = np, hasPrev = hasPrev, hasNext = hasNext, time = time, duration = duration, pct = pct, buffered = buffered,
                    playing = playing, subOn = subIndex > 0, speed = speed, resizeLabel = resizes.first { it.first == resize }.second,
                    onScrub = { f -> scrubbing = true; time = f * duration }, onScrubEnd = { exo.seekTo((time * 1000).toLong()); scrubbing = false; poke() },
                    onPrev = onPrev, onNext = onNext, onBack10 = { seekBy(-10) }, onFwd10 = { seekBy(10) }, onToggle = { togglePlay() },
                    onSheet = { sheet = it }, onLock = { locked = true; showUI = false },
                )
            }
        } else {
            AnimatedVisibility(showUI, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                BottomControls(
                    minimal = minimal, time = time, duration = duration, pct = pct, buffered = buffered, scrubbing = scrubbing,
                    playing = playing, subOn = subIndex > 0, speed = speed, resizeLabel = resizes.first { it.first == resize }.second,
                    onScrub = { f -> scrubbing = true; time = f * duration }, onScrubEnd = { exo.seekTo((time * 1000).toLong()); scrubbing = false; poke() },
                    onToggle = { togglePlay() }, onSheet = { sheet = it }, onLock = { locked = true; showUI = false },
                )
            }
        }
    }

    when (sheet) {
        PlayerSheet.SUBTITLE -> TrackSheet("Subtitles", subTracks, subIndex, onPick = { applySub(it); sheet = null }, onDismiss = { sheet = null }, footer = "Load subtitle file…")
        PlayerSheet.AUDIO -> TrackSheet("Audio track", audioTracks, audioIndex, onPick = { audioIndex = it; sheet = null }, onDismiss = { sheet = null })
        PlayerSheet.SPEED -> SpeedSheet(speeds, speed, onPick = { applySpeed(it); sheet = null }, onDismiss = { sheet = null })
        PlayerSheet.RESIZE -> ResizeSheet(resizes, resize, onPick = { resize = it; sheet = null }, onDismiss = { sheet = null })
        null -> Unit
    }
}

@Composable
private fun TopChrome(title: String, subtitle: String, cinema: Boolean, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (cinema) Modifier else Modifier.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))))
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.KeyboardArrowDown, "Close", tint = Color.White) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
        }
        IconButton(onClick = {}) { Icon(Icons.Outlined.Cast, "Cast", tint = Color.White) }
        IconButton(onClick = {}) { Icon(Icons.Outlined.PictureInPictureAlt, "PiP", tint = Color.White) }
        IconButton(onClick = {}) { Icon(Icons.Outlined.MoreVert, "More", tint = Color.White) }
    }
}

@Composable
private fun CenterTransport(playing: Boolean, hasPrev: Boolean, hasNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onBack10: () -> Unit, onFwd10: () -> Unit, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        IconButton(onClick = onPrev, enabled = hasPrev) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.35f), modifier = Modifier.size(30.dp)) }
        IconButton(onClick = onBack10) { Icon(Icons.Outlined.Replay10, "-10s", tint = Color.White, modifier = Modifier.size(34.dp)) }
        Box(Modifier.size(76.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape).pointerInput(Unit) { detectTapGestures { onToggle() } }, contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = Color.White, modifier = Modifier.size(40.dp))
        }
        IconButton(onClick = onFwd10) { Icon(Icons.Outlined.Forward10, "+10s", tint = Color.White, modifier = Modifier.size(34.dp)) }
        IconButton(onClick = onNext, enabled = hasNext) { Icon(Icons.Filled.SkipNext, "Next", tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f), modifier = Modifier.size(30.dp)) }
    }
}

@Composable
fun Scrubber(time: Float, duration: Int, pct: Float, buffered: Float, scrubbing: Boolean, onDark: Color, cinema: Boolean, onScrub: (Float) -> Unit, onScrubEnd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(fmtDur(time.toInt()), style = MaterialTheme.typography.labelMedium, color = onDark, modifier = Modifier.width(44.dp))
        BoxWithConstraints(
            Modifier.weight(1f).height(28.dp)
                .pointerInput(duration) {
                    detectHorizontalDragGestures(onDragEnd = onScrubEnd, onDragStart = { off -> onScrub((off.x / size.width).coerceIn(0f, 1f)) }) { change, _ -> onScrub((change.position.x / size.width).coerceIn(0f, 1f)) }
                }
                .pointerInput(duration) { detectTapGestures { off -> onScrub((off.x / size.width).coerceIn(0f, 1f)); onScrubEnd() } },
            contentAlignment = Alignment.CenterStart,
        ) {
            val track = if (cinema) MaterialTheme.colorScheme.surfaceContainerHighest else Color.White.copy(alpha = 0.25f)
            val bufC = if (cinema) MaterialTheme.colorScheme.outlineVariant else Color.White.copy(alpha = 0.4f)
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(track))
            Box(Modifier.fillMaxWidth(buffered).height(5.dp).clip(RoundedCornerShape(3.dp)).background(bufC))
            Box(Modifier.fillMaxWidth(pct).height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
            Box(Modifier.fillMaxWidth(pct), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.size(if (scrubbing) 20.dp else 14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
        }
        Text(fmtDur(duration), style = MaterialTheme.typography.labelMedium, color = if (cinema) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.75f), modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, active: Boolean, onDark: Color, cinema: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).pointerInput(Unit) { detectTapGestures { onClick() } }.padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, label, tint = if (active) MaterialTheme.colorScheme.primary else onDark, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.primary else if (cinema) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun ControlRow(subOn: Boolean, speed: Float, resizeLabel: String, onDark: Color, cinema: Boolean, onSheet: (PlayerSheet) -> Unit, onLock: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        ControlButton(Icons.Outlined.ClosedCaption, if (subOn) "On" else "Subtitles", subOn, onDark, cinema) { onSheet(PlayerSheet.SUBTITLE) }
        ControlButton(Icons.AutoMirrored.Filled.VolumeUp, "Audio", false, onDark, cinema) { onSheet(PlayerSheet.AUDIO) }
        ControlButton(Icons.Outlined.Speed, if (speed == 1f) "Speed" else "${speed}×", speed != 1f, onDark, cinema) { onSheet(PlayerSheet.SPEED) }
        ControlButton(Icons.Outlined.AspectRatio, resizeLabel, resizeLabel != "Fit", onDark, cinema) { onSheet(PlayerSheet.RESIZE) }
        ControlButton(Icons.Outlined.Lock, "Lock", false, onDark, cinema) { onLock() }
    }
}

@Composable
private fun BottomControls(
    minimal: Boolean, time: Float, duration: Int, pct: Float, buffered: Float, scrubbing: Boolean,
    playing: Boolean, subOn: Boolean, speed: Float, resizeLabel: String,
    onScrub: (Float) -> Unit, onScrubEnd: () -> Unit, onToggle: () -> Unit, onSheet: (PlayerSheet) -> Unit, onLock: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
            .padding(start = if (minimal) 8.dp else 16.dp, end = if (minimal) 8.dp else 16.dp, top = 40.dp, bottom = 14.dp),
    ) {
        if (minimal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggle) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onSheet(PlayerSheet.SUBTITLE) }) { Icon(Icons.Outlined.ClosedCaption, "Subtitles", tint = if (subOn) MaterialTheme.colorScheme.primary else Color.White) }
                IconButton(onClick = { onSheet(PlayerSheet.SPEED) }) { Icon(Icons.Outlined.Speed, "Speed", tint = if (speed != 1f) MaterialTheme.colorScheme.primary else Color.White) }
                IconButton(onClick = {}) { Icon(Icons.Outlined.Fullscreen, "Fullscreen", tint = Color.White) }
            }
        }
        Scrubber(time, duration, pct, buffered, scrubbing, Color.White, cinema = false, onScrub = onScrub, onScrubEnd = onScrubEnd)
        if (!minimal) Box(Modifier.padding(top = 6.dp)) { ControlRow(subOn, speed, resizeLabel, Color.White, cinema = false, onSheet = onSheet, onLock = onLock) }
    }
}

@Composable
private fun CinemaDeck(
    np: xyz.devnerd.anmediaplayer.data.PrettyName, hasPrev: Boolean, hasNext: Boolean, time: Float, duration: Int, pct: Float, buffered: Float,
    playing: Boolean, subOn: Boolean, speed: Float, resizeLabel: String,
    onScrub: (Float) -> Unit, onScrubEnd: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onBack10: () -> Unit, onFwd10: () -> Unit, onToggle: () -> Unit,
    onSheet: (PlayerSheet) -> Unit, onLock: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(np.primary, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(np.secondary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Scrubber(time, duration, pct, buffered, scrubbing = false, onDark = MaterialTheme.colorScheme.onSurface, cinema = true, onScrub = onScrub, onScrubEnd = onScrubEnd)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev, enabled = hasPrev) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = MaterialTheme.colorScheme.onSurface) }
            IconButton(onClick = onBack10) { Icon(Icons.Outlined.Replay10, "-10s", tint = MaterialTheme.colorScheme.onSurface) }
            Box(Modifier.padding(horizontal = 18.dp).size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).pointerInput(Unit) { detectTapGestures { onToggle() } }, contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = onFwd10) { Icon(Icons.Outlined.Forward10, "+10s", tint = MaterialTheme.colorScheme.onSurface) }
            IconButton(onClick = onNext, enabled = hasNext) { Icon(Icons.Filled.SkipNext, "Next", tint = MaterialTheme.colorScheme.onSurface) }
        }
        ControlRow(subOn, speed, resizeLabel, MaterialTheme.colorScheme.onSurface, cinema = true, onSheet = onSheet, onLock = onLock)
    }
}
