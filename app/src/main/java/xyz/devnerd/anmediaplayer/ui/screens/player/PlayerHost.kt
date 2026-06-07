package xyz.devnerd.anmediaplayer.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import xyz.devnerd.anmediaplayer.data.AppRepo
import xyz.devnerd.anmediaplayer.data.Entry
import xyz.devnerd.anmediaplayer.data.MediaRepo
import xyz.devnerd.anmediaplayer.data.prettyName
import xyz.devnerd.anmediaplayer.data.progressKey
import xyz.devnerd.anmediaplayer.data.source.matchSubtitle
import xyz.devnerd.anmediaplayer.data.source.naturalVideoSort
import xyz.devnerd.anmediaplayer.settings.AppSettings
import xyz.devnerd.anmediaplayer.settings.ResumeBehavior

data class PlaybackRequest(
    val serverId: String,
    val path: List<String>,
    val file: String,
    val durSec: Int,
    val resumeImmediately: Boolean = false,
)

@Composable
fun PlayerHost(request: PlaybackRequest, settings: AppSettings, onClose: () -> Unit) {
    val activity = LocalContext.current as? Activity

    DisposableEffect(Unit) {
        val prevOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = prevOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var file by remember { mutableStateOf(request.file) }
    var durSec by remember { mutableIntStateOf(request.durSec) }
    var ended by remember { mutableStateOf(false) }
    var restartToken by remember { mutableIntStateOf(0) }

    // Load the folder so we have the playlist + matching subtitle.
    var entries by remember(request.path.joinToString("/")) { mutableStateOf<List<Entry>?>(null) }
    LaunchedEffect(request.serverId, request.path.joinToString("/")) {
        entries = runCatching { MediaRepo.list(request.serverId, request.path) }.getOrDefault(emptyList())
    }

    val loaded = entries
    if (loaded == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        return
    }

    val playlist = naturalVideoSort(loaded)
    val idx = playlist.indexOfFirst { it.name == file }
    val nextFile = playlist.getOrNull(idx + 1)?.name
    val prevFile = playlist.getOrNull(idx - 1)?.name

    val key = progressKey(request.serverId, request.path, file)
    val saved = AppRepo.getProgress(key)

    val decision = remember(file, restartToken) {
        val pos = AppRepo.getProgress(progressKey(request.serverId, request.path, file))
        val eligible = pos > 30 && durSec > 0 && pos < durSec - 90
        val isFirst = file == request.file && restartToken == 0
        when {
            !isFirst -> 0 to false
            request.resumeImmediately && eligible -> pos to false
            settings.resumeBehavior == ResumeBehavior.ASK && eligible -> 0 to true
            eligible -> pos to false
            else -> 0 to false
        }
    }
    var startAt by remember(file, restartToken) { mutableIntStateOf(decision.first) }
    var asking by remember(file, restartToken) { mutableStateOf(decision.second) }

    val np = prettyName(file)
    val streamUrl = MediaRepo.fileUrl(request.serverId, request.path, file)
    val subtitleUrl = matchSubtitle(loaded, file)?.let { MediaRepo.fileUrl(request.serverId, request.path, it.name) }

    fun playNext() {
        val n = playlist.getOrNull(idx + 1) ?: return
        durSec = n.durSec ?: 0; ended = false; restartToken = 0; file = n.name
    }
    fun playPrev() {
        val p = playlist.getOrNull(idx - 1) ?: return
        durSec = p.durSec ?: 0; ended = false; restartToken = 0; file = p.name
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            asking -> ResumeDialog(
                posSec = saved, durSec = durSec,
                onResume = { startAt = saved; asking = false },
                onStartOver = { startAt = 0; asking = false },
                onDismiss = onClose,
            )
            ended -> EndPanel(
                finishedFile = file, nextFile = nextFile, autoPlay = settings.autoPlayNext,
                onNext = { playNext() }, onRestart = { ended = false; restartToken++ }, onBack = onClose, onClose = onClose,
            )
            else -> key(file, restartToken) {
                PlayerScreen(
                    title = np.primary,
                    subtitleLabel = np.secondary.ifBlank { file },
                    streamUrl = streamUrl,
                    subtitleUrl = subtitleUrl,
                    startAt = startAt,
                    durSec = durSec,
                    layout = settings.playerLayout,
                    subtitlesDefault = settings.subtitlesDefault,
                    keepScreenOn = settings.keepScreenOn,
                    hasPrev = prevFile != null,
                    hasNext = nextFile != null,
                    onPrev = { playPrev() },
                    onNext = { playNext() },
                    onClose = onClose,
                    onEnded = { ended = true },
                    saveProgress = { pos, dur -> AppRepo.saveProgress(request.serverId, request.path, file, pos, dur, System.currentTimeMillis()) },
                )
            }
        }
    }
}
