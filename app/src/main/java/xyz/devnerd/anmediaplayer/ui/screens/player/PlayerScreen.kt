package xyz.devnerd.anmediaplayer.ui.screens.player

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ErrorOutline
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.devnerd.anmediaplayer.data.PrettyName
import xyz.devnerd.anmediaplayer.data.fmtDur
import xyz.devnerd.anmediaplayer.settings.PlayerLayout
import xyz.devnerd.anmediaplayer.ui.components.LocalIsTv
import xyz.devnerd.anmediaplayer.ui.components.focusHighlight
import kotlin.math.roundToInt

private enum class PlayerSheet { SUBTITLE, SUBTITLE_LOAD, AUDIO, SPEED, RESIZE }
private data class Gesture(val left: Boolean, val value: Float)
private data class SeekFx(val forward: Boolean, val token: Long)

@OptIn(UnstableApi::class)
private data class AudioOpt(val group: androidx.media3.common.Tracks.Group, val trackIndex: Int, val label: String, val selected: Boolean)

@OptIn(UnstableApi::class)
private fun audioOptions(tracks: androidx.media3.common.Tracks): List<AudioOpt> = buildList {
    var n = 1
    tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { g ->
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            val f = g.getTrackFormat(i)
            val lang = f.language?.let { java.util.Locale(it).displayLanguage.ifBlank { it } }
            val parts = listOfNotNull(f.label, lang, f.codecs?.substringBefore('.')?.uppercase(), if (f.channelCount > 0) "${f.channelCount}ch" else null)
            add(AudioOpt(g, i, parts.joinToString(" · ").ifBlank { "Audio $n" }, g.isTrackSelected(i)))
            n++
        }
    }
}

@OptIn(UnstableApi::class)
private data class SubOpt(val group: androidx.media3.common.Tracks.Group, val trackIndex: Int, val label: String)

@OptIn(UnstableApi::class)
private fun subOptions(tracks: androidx.media3.common.Tracks): List<SubOpt> = buildList {
    var n = 1
    tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { g ->
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            val f = g.getTrackFormat(i)
            val lang = f.language?.let { java.util.Locale(it).displayLanguage.ifBlank { it } }
            val parts = listOfNotNull(f.label, lang)
            add(SubOpt(g, i, parts.joinToString(" · ").ifBlank { "Subtitle $n" }))
            n++
        }
    }
}

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
    resumeFromSec: Int? = null,
    durSec: Int,
    layout: PlayerLayout,
    subtitlesDefault: Boolean,
    keepScreenOn: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    playlist: List<String> = emptyList(),
    currentIndex: Int = -1,
    onSelectIndex: (Int) -> Unit = {},
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onEnded: () -> Unit,
    onDownload: (() -> Unit)? = null,
    saveProgress: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val isTv = LocalIsTv.current
    val activity = context as? android.app.Activity
    val audio = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    fun applyBrightness(v: Float) {
        activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = v.coerceIn(0.02f, 1f) } }
    }
    fun applyVolume(v: Float) {
        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (v * maxVol).roundToInt().coerceIn(0, maxVol), 0)
    }
    fun currentBrightness(): Float = activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.5f
    fun currentVolume(): Float = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / maxVol

    // Cast (Google default receiver). Null when Play Services Cast unavailable.
    val castContext = remember { runCatching { com.google.android.gms.cast.framework.CastContext.getSharedInstance(context) }.getOrNull() }
    val castPlayer = remember(castContext) { castContext?.let { androidx.media3.cast.CastPlayer(it) } }

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
    var playerError by remember(streamUrl) { mutableStateOf<String?>(null) }
    var showUI by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var subIndex by remember { mutableIntStateOf(if (subtitlesDefault) 1 else 0) }
    var audioOpts by remember { mutableStateOf<List<AudioOpt>>(emptyList()) }
    var subOpts by remember { mutableStateOf<List<SubOpt>>(emptyList()) }
    var resize by remember { mutableStateOf("fit") }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var showPlaylist by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var repeatOne by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var seekFx by remember { mutableStateOf<SeekFx?>(null) }
    var gesture by remember { mutableStateOf<Gesture?>(null) }
    var ffActive by remember { mutableStateOf(false) }
    var uiPoke by remember { mutableIntStateOf(0) }
    var resumeOffer by remember(streamUrl) { mutableStateOf(resumeFromSec) }
    LaunchedEffect(resumeOffer) { if (resumeOffer != null) { delay(8000); resumeOffer = null } }

    androidx.activity.compose.BackHandler {
        when {
            sheet != null -> sheet = null
            showPlaylist -> showPlaylist = false
            locked -> locked = false
            else -> onClose()
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                buffering = false
                showUI = true
                playerError = when (error.errorCode) {
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "Media not found on server."
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connection to server failed."
                    androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Video format not supported on this device."
                    else -> "Playback failed. ${error.errorCodeName}"
                }
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                audioOpts = audioOptions(tracks)
                subOpts = subOptions(tracks)
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener); exo.release()
            activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = -1f } }
        }
    }

    // Pause playback when app goes to background, unless PiP keeps the player visible.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exo) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && activity?.isInPictureInPictureMode != true) exo.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Hand off to / from Cast when a session connects.
    DisposableEffect(castPlayer, streamUrl) {
        val l = object : androidx.media3.cast.SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                runCatching {
                    castPlayer?.apply { setMediaItem(MediaItem.fromUri(streamUrl)); playWhenReady = true; prepare(); seekTo(exo.currentPosition) }
                    exo.pause()
                }
            }
            override fun onCastSessionUnavailable() { exo.play() }
        }
        castPlayer?.setSessionAvailabilityListener(l)
        onDispose { castPlayer?.setSessionAvailabilityListener(null); castPlayer?.release() }
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
            // While the resume pill is offered, don't overwrite the saved position.
            if (resumeOffer == null) saveProgress(time.toInt(), duration)
            delay(500)
        }
    }
    // auto-hide — never while buffering/errored or the More popup is open, so controls stay reachable.
    LaunchedEffect(showUI, playing, sheet, uiPoke, buffering, playerError, showMore) {
        if (showUI && playing && sheet == null && !locked && !buffering && playerError == null && !showMore) { delay(3400); showUI = false }
    }
    LaunchedEffect(buffering) { if (buffering) { showUI = true; uiPoke++ } }

    fun enterPip() {
        if (android.os.Build.VERSION.SDK_INT >= 26 && activity != null) {
            showUI = false
            val params = android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build()
            runCatching { activity.enterPictureInPictureMode(params) }
        }
    }

    fun poke() { showUI = true; uiPoke++ }
    fun seekBy(d: Int) { exo.seekTo((exo.currentPosition + d * 1000L).coerceAtLeast(0)) }
    fun togglePlay() { if (exo.isPlaying) exo.pause() else exo.play(); poke() }
    fun applySub(idx: Int) {
        subIndex = idx
        val on = idx != 0
        var b = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on)
        val o = if (on) subOpts.getOrNull(idx - 1) else null
        b = if (o != null) {
            b.setOverrideForType(androidx.media3.common.TrackSelectionOverride(o.group.mediaTrackGroup, o.trackIndex))
        } else {
            b.setPreferredTextLanguage(if (on) "en" else null).setSelectUndeterminedTextLanguage(on)
        }
        exo.trackSelectionParameters = b.build()
    }
    fun applySpeed(s: Float) { speed = s; exo.setPlaybackSpeed(s) }
    fun applyAudio(i: Int) {
        val o = audioOpts.getOrNull(i) ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(o.group.mediaTrackGroup, o.trackIndex)).build()
    }

    // Newly loaded (local file or online download) subtitle awaiting selection once its
    // track shows up in subOpts after the player re-reads the media item.
    var pendingSubSelect by remember { mutableStateOf(false) }
    fun addSubtitle(uri: Uri, mime: String, label: String) {
        val current = exo.currentMediaItem ?: return
        val sub = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mime)
            .setLanguage("en")
            .setLabel(label)
            .build()
        val existing = current.localConfiguration?.subtitleConfigurations.orEmpty()
        val newItem = current.buildUpon().setSubtitleConfigurations(existing + sub).build()
        pendingSubSelect = true
        exo.replaceMediaItem(0, newItem)
    }
    LaunchedEffect(subOpts) {
        if (pendingSubSelect && subOpts.isNotEmpty()) {
            pendingSubSelect = false
            applySub(subOpts.size)
        }
    }

    var subQuery by remember { mutableStateOf(title) }
    var subSearching by remember { mutableStateOf(false) }
    var subSearchError by remember { mutableStateOf<String?>(null) }
    var subResults by remember { mutableStateOf<List<xyz.devnerd.anmediaplayer.data.OnlineSubtitle>>(emptyList()) }
    val subScope = rememberCoroutineScope()
    fun searchOnlineSubs() {
        if (subQuery.isBlank()) return
        subSearching = true
        subSearchError = null
        subScope.launch {
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) { xyz.devnerd.anmediaplayer.data.OpenSubtitlesApi.search(subQuery) }
            subSearching = false
            result.onSuccess { subResults = it; if (it.isEmpty()) subSearchError = "No subtitles found." }
                .onFailure { subSearchError = it.message ?: "Search failed." }
        }
    }
    fun pickOnlineSub(i: Int) {
        val r = subResults.getOrNull(i) ?: return
        subScope.launch {
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) { xyz.devnerd.anmediaplayer.data.OpenSubtitlesApi.download(r.fileId) }
            result.onSuccess { dl -> addSubtitle(Uri.parse(dl.url), subtitleMime(dl.fileName), r.label); sheet = null }
                .onFailure { subSearchError = it.message ?: "Download failed." }
        }
    }
    val pickSubtitleFile = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            } ?: uri.lastPathSegment ?: "subtitle.srt"
            addSubtitle(uri, subtitleMime(name), name)
            sheet = null
        }
    }

    var defaultSubApplied by remember(streamUrl) { mutableStateOf(false) }
    LaunchedEffect(exo) { if (subtitlesDefault) applySub(1) }
    // Re-apply once real text tracks are known, so the explicit override matches what's
    // actually selected (the initial applySub above only has a language-guess fallback).
    LaunchedEffect(subOpts) {
        if (subtitlesDefault && !defaultSubApplied && subOpts.isNotEmpty()) {
            defaultSubApplied = true
            applySub(1)
        }
    }

    val subTracks = listOf("Off") + subOpts.map { it.label }
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val resizes = listOf("fit" to "Fit", "fill" to "Fill", "zoom" to "Zoom")

    val cinema = layout == PlayerLayout.CINEMA
    val minimal = layout == PlayerLayout.MINIMAL
    val onDark = if (cinema) MaterialTheme.colorScheme.onSurface else Color.White
    val pct = if (duration > 0) (time / duration).coerceIn(0f, 1f) else 0f
    val buffered = bufferedPct.coerceIn(pct, 1f)
    val np = PrettyName(title, subtitleLabel)

    // D-pad / TV remote handling.
    val tvFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val playFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { tvFocus.requestFocus() } }
    // When controls hide, pull focus back to the root so the remote keeps working.
    // When they show on TV, land focus on the play button so Center toggles play.
    LaunchedEffect(showUI, locked) {
        if (!showUI || locked) runCatching { tvFocus.requestFocus() }
        else if (isTv) { delay(50); runCatching { playFocus.requestFocus() } }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(tvFocus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Hardware media keys: always active regardless of UI state.
                when (ev.key) {
                    androidx.compose.ui.input.key.Key.MediaPlayPause -> { togglePlay(); return@onPreviewKeyEvent true }
                    androidx.compose.ui.input.key.Key.MediaPlay -> { exo.play(); poke(); return@onPreviewKeyEvent true }
                    androidx.compose.ui.input.key.Key.MediaPause -> { exo.pause(); poke(); return@onPreviewKeyEvent true }
                    androidx.compose.ui.input.key.Key.MediaFastForward -> { seekBy(10); poke(); return@onPreviewKeyEvent true }
                    androidx.compose.ui.input.key.Key.MediaRewind -> { seekBy(-10); poke(); return@onPreviewKeyEvent true }
                }
                if (locked) {
                    return@onPreviewKeyEvent when (ev.key) {
                        androidx.compose.ui.input.key.Key.DirectionCenter, androidx.compose.ui.input.key.Key.Enter -> { locked = false; poke(); true }
                        else -> { showUI = true; true }
                    }
                }
                if (showUI) {
                    // Controls visible: keep them alive and let the focus system move
                    // between / activate the on-screen buttons (return false = not consumed).
                    poke(); false
                } else {
                    // Controls hidden: D-pad drives playback directly.
                    when (ev.key) {
                        androidx.compose.ui.input.key.Key.DirectionCenter, androidx.compose.ui.input.key.Key.Enter -> { poke(); true }
                        androidx.compose.ui.input.key.Key.DirectionLeft -> { seekBy(-10); seekFx = SeekFx(false, time.toLong()); true }
                        androidx.compose.ui.input.key.Key.DirectionRight -> { seekBy(10); seekFx = SeekFx(true, time.toLong()); true }
                        androidx.compose.ui.input.key.Key.DirectionUp, androidx.compose.ui.input.key.Key.DirectionDown -> { poke(); true }
                        else -> false
                    }
                }
            },
    ) {
        // ── video surface (Media3) ──
        val resizeMode = when (resize) {
            "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        Box(
            Modifier
                .fillMaxSize()
                .then(if (cinema) Modifier.fillMaxHeight(0.46f).align(Alignment.TopCenter) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        player = exo
                        useController = false
                        isClickable = false
                        isFocusable = false
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.resizeMode = resizeMode; it.keepScreenOn = keepScreenOn && playing },
                modifier = Modifier.fillMaxSize(),
            )
            if (buffering && playerError == null) CircularProgressIndicator(color = Color.White)
            playerError?.let { msg ->
                Column(
                    Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }

        // Single gesture overlay ABOVE the PlayerView (PlayerView consumes touch).
        // Two pointerInput on the SAME node: tap/double-tap/long-press + vertical drag
        // (left half = brightness, right half = volume). Standard, reliable pattern.
        Box(
            Modifier
                .fillMaxSize()
                .then(if (cinema) Modifier.fillMaxHeight(0.46f).align(Alignment.TopCenter) else Modifier)
                .pointerInput(locked, speed) {
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
                        onLongPress = { if (!locked) { ffActive = true; exo.setPlaybackSpeed(2f) } },
                        onPress = {
                            tryAwaitRelease()
                            if (ffActive) { ffActive = false; exo.setPlaybackSpeed(speed) }
                        },
                    )
                }
                .pointerInput(locked, cinema) {
                    if (locked || cinema) return@pointerInput
                    val edgeMargin = 56.dp.toPx()
                    var left = true
                    var v = 0.5f
                    var active = false
                    detectVerticalDragGestures(
                        onDragStart = { off ->
                            active = off.y in edgeMargin..(size.height - edgeMargin)
                            left = off.x < size.width / 2f
                            v = if (left) currentBrightness() else currentVolume()
                            gesture = if (active) Gesture(left, v) else null
                        },
                        onDragEnd = { gesture = null },
                        onDragCancel = { gesture = null },
                    ) { _, dy ->
                        if (!active) return@detectVerticalDragGestures
                        v = (v - dy / 600f).coerceIn(0f, 1f)
                        if (left) applyBrightness(v) else applyVolume(v)
                        gesture = Gesture(left, v)
                    }
                },
        )

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
            Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = if (g.left) Alignment.CenterEnd else Alignment.CenterStart) {
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

        if (ffActive) {
            Box(Modifier.fillMaxSize().padding(top = 56.dp), contentAlignment = Alignment.TopCenter) {
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("2× speed", style = MaterialTheme.typography.labelMedium, color = Color.White)
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

        // Scrim behind the More popup — any tap outside it closes the popup.
        if (showMore) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { showMore = false } })
        }

        AnimatedVisibility(showUI, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Box(Modifier.fillMaxWidth()) {
                TopChrome(
                    np.primary, subtitleLabel, cinema, onClose,
                    onPlaylist = if (playlist.size > 1) ({ showPlaylist = true }) else null,
                    onMore = { showMore = !showMore },
                    subOn = subIndex > 0,
                    onSubtitle = { sheet = PlayerSheet.SUBTITLE },
                    onAudio = { sheet = PlayerSheet.AUDIO },
                )
                Box(Modifier.fillMaxWidth().padding(top = 48.dp, end = 4.dp), contentAlignment = Alignment.TopEnd) {
                    AnimatedVisibility(showMore, enter = fadeIn(), exit = fadeOut()) {
                        val vf = exo.videoFormat
                        MoreActionsBar(
                            repeatOne = repeatOne,
                            onToggleRepeat = { repeatOne = !repeatOne; exo.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF },
                            onLock = { locked = true; showUI = false; showMore = false },
                            onResize = { sheet = PlayerSheet.RESIZE; showMore = false },
                            onSpeed = { sheet = PlayerSheet.SPEED; showMore = false },
                            onShare = {
                                showMore = false
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, streamUrl)
                                }
                                context.startActivity(android.content.Intent.createChooser(send, "Share"))
                            },
                            onPiP = { showMore = false; enterPip() },
                            onInfo = { showInfo = true; showMore = false },
                            onDownload = onDownload?.let { dl -> { dl(); showMore = false } },
                            castSlot = if (castContext != null && !isTv) ({ CastRouteButton() }) else null,
                        )
                    }
                }
            }
        }
        if (!minimal) {
            AnimatedVisibility(showUI, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                CenterTransport(playing, hasPrev, hasNext, onPrev, onNext, onSeek = { seekBy(it) }, onToggle = { togglePlay() }, playFocus = playFocus)
            }
        }
        if (cinema) {
            AnimatedVisibility(showUI, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                CinemaDeck(
                    np = np, hasPrev = hasPrev, hasNext = hasNext, time = time, duration = duration, pct = pct, buffered = buffered,
                    playing = playing,
                    onScrub = { f -> scrubbing = true; time = f * duration }, onScrubEnd = { exo.seekTo((time * 1000).toLong()); scrubbing = false; poke() },
                    onPrev = onPrev, onNext = onNext, onSeek = { seekBy(it) }, onToggle = { togglePlay() },
                )
            }
        } else {
            AnimatedVisibility(showUI, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                BottomControls(
                    minimal = minimal, time = time, duration = duration, pct = pct, buffered = buffered, scrubbing = scrubbing,
                    playing = playing, subOn = subIndex > 0, speed = speed,
                    onScrub = { f -> scrubbing = true; time = f * duration }, onScrubEnd = { exo.seekTo((time * 1000).toLong()); scrubbing = false; poke() },
                    onToggle = { togglePlay() }, onSheet = { sheet = it },
                )
            }
        }

        // Non-blocking resume pill (top-center). Plays from start; click to jump.
        resumeOffer?.let { pos ->
            val resumeFocus = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(pos) { if (isTv) runCatching { resumeFocus.requestFocus() } }
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 28.dp)
                    .focusRequester(resumeFocus)
                    .focusHighlight(RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = 0.6f))
                    .clickable { exo.seekTo(pos * 1000L); resumeOffer = null }
                    .padding(start = 24.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Replay10, null, tint = Color.White, modifier = Modifier.size(26.dp))
                Text("Resume from ${fmtDur(pos)}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                IconButton(onClick = { resumeOffer = null }, modifier = Modifier.focusHighlight(CircleShape).size(40.dp)) {
                    Icon(Icons.Outlined.Close, "Dismiss", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
                }
            }
        }
    }

    when (sheet) {
        PlayerSheet.SUBTITLE -> TrackSheet(
            "Subtitles", subTracks, subIndex,
            onPick = { applySub(it); sheet = null },
            onDismiss = { sheet = null },
            footer = "Load subtitle file…",
            onFooter = { sheet = PlayerSheet.SUBTITLE_LOAD },
        )
        PlayerSheet.SUBTITLE_LOAD -> SubtitleLoadSheet(
            query = subQuery,
            onQueryChange = { subQuery = it },
            onSearch = { searchOnlineSubs() },
            searching = subSearching,
            error = subSearchError,
            results = subResults,
            onPickResult = { pickOnlineSub(it) },
            onPickFile = { pickSubtitleFile.launch(arrayOf("*/*")) },
            onDismiss = { sheet = null },
        )
        PlayerSheet.AUDIO -> TrackSheet(
            "Audio track",
            audioOpts.map { it.label }.ifEmpty { listOf("Default") },
            audioOpts.indexOfFirst { it.selected }.coerceAtLeast(0),
            onPick = { applyAudio(it); sheet = null },
            onDismiss = { sheet = null },
        )
        PlayerSheet.SPEED -> SpeedSheet(speeds, speed, onPick = { applySpeed(it); sheet = null }, onDismiss = { sheet = null })
        PlayerSheet.RESIZE -> ResizeSheet(resizes, resize, onPick = { resize = it; sheet = null }, onDismiss = { sheet = null })
        null -> Unit
    }

    if (showPlaylist) PlaylistSheet(playlist, currentIndex, onPick = { onSelectIndex(it); showPlaylist = false }, onDismiss = { showPlaylist = false })

    if (showInfo) {
        val vf = exo.videoFormat
        InfoDialog(
            resolution = vf?.let { if (it.width > 0) "${it.width}×${it.height}" else null },
            videoCodec = vf?.codecs ?: vf?.sampleMimeType?.substringAfter('/')?.uppercase(),
            audioLabel = audioOpts.firstOrNull { it.selected }?.label,
            onDismiss = { showInfo = false },
        )
    }
}

@Composable
private fun TopChrome(
    title: String, subtitle: String, cinema: Boolean, onClose: () -> Unit,
    onPlaylist: (() -> Unit)? = null, onMore: (() -> Unit)? = null,
    subOn: Boolean = false, onSubtitle: (() -> Unit)? = null, onAudio: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (cinema) Modifier else Modifier.background(Color.Black.copy(alpha = 0.45f)))
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.focusHighlight(CircleShape)) { Icon(Icons.Outlined.KeyboardArrowDown, "Close", tint = Color.White) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
        }
        if (onPlaylist != null) IconButton(onClick = onPlaylist, modifier = Modifier.focusHighlight(CircleShape)) { Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, "Playlist", tint = Color.White) }
        if (onSubtitle != null) IconButton(onClick = onSubtitle, modifier = Modifier.focusHighlight(CircleShape)) { Icon(Icons.Outlined.ClosedCaption, "Subtitles", tint = if (subOn) MaterialTheme.colorScheme.primary else Color.White) }
        if (onAudio != null) IconButton(onClick = onAudio, modifier = Modifier.focusHighlight(CircleShape)) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "Audio", tint = Color.White) }
        IconButton(onClick = { onMore?.invoke() }, modifier = Modifier.focusHighlight(CircleShape)) { Icon(Icons.Outlined.MoreVert, "More", tint = Color.White) }
    }
}

@Composable
private fun CenterTransport(playing: Boolean, hasPrev: Boolean, hasNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onSeek: (Int) -> Unit, onToggle: () -> Unit, playFocus: androidx.compose.ui.focus.FocusRequester? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        IconButton(onClick = onPrev, enabled = hasPrev, modifier = Modifier.focusHighlight(CircleShape)) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.35f), modifier = Modifier.size(30.dp)) }
        SeekButton(forward = false, tint = Color.White, iconSize = 34.dp, onSeek = onSeek)
        Box(
            Modifier.size(76.dp)
                .then(if (playFocus != null) Modifier.focusRequester(playFocus) else Modifier)
                .focusHighlight(CircleShape)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = Color.White, modifier = Modifier.size(40.dp))
        }
        SeekButton(forward = true, tint = Color.White, iconSize = 34.dp, onSeek = onSeek)
        IconButton(onClick = onNext, enabled = hasNext, modifier = Modifier.focusHighlight(CircleShape)) { Icon(Icons.Filled.SkipNext, "Next", tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f), modifier = Modifier.size(30.dp)) }
    }
}

/**
 * Seek button with TV long-press support: a tap (or short D-pad click) seeks
 * ±10s; holding D-pad center auto-repeats with acceleration for continuous
 * fast-forward/rewind. Uses key auto-repeat (repeatCount) so it works from a
 * remote where pointer long-press gestures don't fire.
 */
@Composable
private fun SeekButton(forward: Boolean, tint: Color, iconSize: Dp, onSeek: (Int) -> Unit) {
    val dir = if (forward) 1 else -1
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var longActive by remember { mutableStateOf(false) }
    IconButton(
        onClick = { onSeek(dir * 10) },
        modifier = Modifier
            .focusHighlight(CircleShape)
            .onPreviewKeyEvent { ev ->
                val center = ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter
                if (!center) return@onPreviewKeyEvent false
                when (ev.type) {
                    KeyEventType.KeyDown -> {
                        // first KeyDown arms a long-press timer; auto-repeat re-enters
                        // here while holding but the job-guard ignores those.
                        if (holdJob == null) {
                            holdJob = scope.launch {
                                delay(350) // hold threshold before continuous seek kicks in
                                longActive = true
                                var step = 10
                                while (isActive) {
                                    onSeek(dir * step)
                                    delay(170)
                                    if (step < 40) step += 5 // accelerate while held
                                }
                            }
                        }
                        false // don't consume — short press still clicks via onClick
                    }
                    KeyEventType.KeyUp -> {
                        holdJob?.cancel(); holdJob = null
                        if (longActive) { longActive = false; true } else false // consume up only after a long-seek
                    }
                    else -> false
                }
            },
    ) {
        Icon(
            if (forward) Icons.Outlined.Forward10 else Icons.Outlined.Replay10,
            if (forward) "+10s" else "-10s",
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
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
private fun BottomControls(
    minimal: Boolean, time: Float, duration: Int, pct: Float, buffered: Float, scrubbing: Boolean,
    playing: Boolean, subOn: Boolean, speed: Float,
    onScrub: (Float) -> Unit, onScrubEnd: () -> Unit, onToggle: () -> Unit, onSheet: (PlayerSheet) -> Unit,
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
    }
}

@Composable
private fun CinemaDeck(
    np: xyz.devnerd.anmediaplayer.data.PrettyName, hasPrev: Boolean, hasNext: Boolean, time: Float, duration: Int, pct: Float, buffered: Float,
    playing: Boolean,
    onScrub: (Float) -> Unit, onScrubEnd: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onSeek: (Int) -> Unit, onToggle: () -> Unit,
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
            IconButton(onClick = onPrev, enabled = hasPrev, modifier = Modifier.focusHighlight(CircleShape)) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = MaterialTheme.colorScheme.onSurface) }
            SeekButton(forward = false, tint = MaterialTheme.colorScheme.onSurface, iconSize = 24.dp, onSeek = onSeek)
            Box(Modifier.padding(horizontal = 18.dp).focusHighlight(CircleShape).size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { onToggle() }, contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
            }
            SeekButton(forward = true, tint = MaterialTheme.colorScheme.onSurface, iconSize = 24.dp, onSeek = onSeek)
            IconButton(onClick = onNext, enabled = hasNext, modifier = Modifier.focusHighlight(CircleShape)) { Icon(Icons.Filled.SkipNext, "Next", tint = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun CastRouteButton() {
    AndroidView(
        // MediaRouteButton needs an AppCompat theme; its theme-helper can still throw
        // ("background can not be translucent") on some device themes — fall back to an
        // empty view rather than crash the player.
        factory = { ctx ->
            runCatching {
                val themed = androidx.appcompat.view.ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)
                androidx.mediarouter.app.MediaRouteButton(themed).apply {
                    com.google.android.gms.cast.framework.CastButtonFactory.setUpMediaRouteButton(themed, this)
                } as android.view.View
            }.getOrElse { android.view.View(ctx) }
        },
        modifier = Modifier.size(48.dp).padding(12.dp),
    )
}
