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
import xyz.devnerd.anmediaplayer.data.EpisodeRef
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
    /** Cross-folder playlist (e.g. a whole series). Null = use the file's folder siblings. */
    val playlist: List<EpisodeRef>? = null,
    /** Direct local file/content URI for offline playback (bypasses the server). */
    val directUrl: String? = null,
    /** Poster resolved at download time — used for continue-watching since offline playback never hits the server. */
    val localCoverUrl: String? = null,
    /** Taller-than-wide source (local portrait clips) — rotate the player to portrait instead of forcing landscape. */
    val portrait: Boolean = false,
)

@Composable
fun PlayerHost(request: PlaybackRequest, settings: AppSettings, onClose: () -> Unit) {
    val activity = LocalContext.current as? Activity

    DisposableEffect(Unit) {
        val prevOrientation = activity?.requestedOrientation
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

    var curPath by remember { mutableStateOf(request.path) }
    var file by remember { mutableStateOf(request.file) }
    var durSec by remember { mutableIntStateOf(request.durSec) }
    var ended by remember { mutableStateOf(false) }
    var restartToken by remember { mutableIntStateOf(0) }

    // Pinned offline playback (a single downloaded file): never touch the server — a remote
    // listing call here would hang/fail with no network and leave the player stuck on the spinner.
    val pinnedOffline = curPath == request.path && file == request.file && request.directUrl != null
    // A Local Files playlist carries its own URI per entry — every item in it is offline, always.
    val allLocalPlaylist = request.playlist?.isNotEmpty() == true && request.playlist.all { it.directUrl != null }
    val skipRemoteListing = pinnedOffline || allLocalPlaylist

    // Listing of the current episode's folder — for subtitle match (+ folder-siblings playlist).
    val curKeyPath = curPath.joinToString("/")
    var entries by remember(curKeyPath) { mutableStateOf<List<Entry>?>(null) }
    LaunchedEffect(request.serverId, curKeyPath, skipRemoteListing) {
        entries = if (skipRemoteListing) emptyList() else runCatching { MediaRepo.list(request.serverId, curPath) }.getOrDefault(emptyList())
    }
    val loaded = entries
    if (loaded == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        return
    }

    val playlist: List<EpisodeRef> = request.playlist
        ?: naturalVideoSort(loaded).map { EpisodeRef(curPath, it.name, it.durSec ?: 0) }
    val idx = playlist.indexOfFirst { it.path == curPath && it.file == file }
    val nextEp = playlist.getOrNull(idx + 1)
    val prevEp = playlist.getOrNull(idx - 1)
    val curDirectUrl = playlist.getOrNull(idx)?.directUrl ?: request.directUrl?.takeIf { pinnedOffline }
    val offline = curDirectUrl != null
    val curPortrait = playlist.getOrNull(idx)?.portrait ?: (request.portrait && pinnedOffline)
    LaunchedEffect(curPortrait) {
        activity?.requestedOrientation = if (curPortrait) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    // Decide start position + whether to offer a non-blocking "Resume from …" pill.
    val decision = remember(file, curKeyPath, restartToken) {
        val pkey = progressKey(request.serverId, curPath, file)
        val pos = AppRepo.getProgress(pkey)
        // Fall back to the duration stored with the saved progress when the listing
        // had no durSec (common for h5ai/autoindex) — else the pill never appears.
        val effDur = if (durSec > 0) durSec else AppRepo.getProgressDur(pkey)
        // Eligible = watched a bit but not finished (>=92% = complete → no prompt).
        // If duration is unknown (0), still offer resume for meaningful progress.
        val eligible = pos > 30 && (effDur <= 0 || pos.toFloat() / effDur < 0.92f)
        val isFirst = file == request.file && curPath == request.path && restartToken == 0
        when {
            !isFirst || !eligible -> 0 to null                              // from start, no prompt
            settings.resumeBehavior == ResumeBehavior.ASK -> 0 to pos       // play from start + offer resume
            else -> pos to null                                             // RESUME setting → auto-resume
        }
    }
    val startAt = decision.first
    val resumePromptSec = decision.second

    val np = prettyName(file)
    val streamUrl = curDirectUrl ?: MediaRepo.fileUrl(request.serverId, curPath, file)
    // Downloads carry no sibling subtitle/cover — those live on the server, never fetched offline.
    val subtitleUrl = if (offline) null else matchSubtitle(loaded, file)?.let { MediaRepo.fileUrl(request.serverId, curPath, it.name) }
    val coverUrl = if (offline) request.localCoverUrl else loaded.firstOrNull { it.type == xyz.devnerd.anmediaplayer.data.EntryType.IMAGE }?.let { MediaRepo.fileUrl(request.serverId, curPath, it.name) }

    fun goTo(ep: EpisodeRef?) {
        ep ?: return
        durSec = ep.durSec; ended = false; restartToken = 0; curPath = ep.path; file = ep.file
    }

    // Download the current file — only when streaming from a server (not already offline).
    val context = LocalContext.current
    val downloadAction: (() -> Unit)? = if (!offline) ({
        val size = loaded.firstOrNull { it.name == file }?.size ?: 0
        xyz.devnerd.anmediaplayer.data.DownloadsStore.enqueue(
            context, request.serverId, curPath, file, np.primary, np.secondary.ifBlank { file },
            size, durSec, MediaRepo.fileUrl(request.serverId, curPath, file), settings.wifiOnly, settings.downloadDir,
        )
        android.widget.Toast.makeText(context, "Added to downloads", android.widget.Toast.LENGTH_SHORT).show()
    }) else null

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            ended -> EndPanel(
                finishedFile = file, nextFile = nextEp?.file, autoPlay = settings.autoPlayNext,
                onNext = { goTo(nextEp) }, onRestart = { ended = false; restartToken++ }, onBack = onClose, onClose = onClose,
            )
            else -> key(curKeyPath, file, restartToken) {
                PlayerScreen(
                    title = np.primary,
                    subtitleLabel = np.secondary.ifBlank { file },
                    streamUrl = streamUrl,
                    subtitleUrl = subtitleUrl,
                    startAt = startAt,
                    resumeFromSec = resumePromptSec,
                    durSec = durSec,
                    layout = settings.playerLayout,
                    subtitlesDefault = settings.subtitlesDefault,
                    keepScreenOn = settings.keepScreenOn,
                    hasPrev = prevEp != null,
                    hasNext = nextEp != null,
                    playlist = playlist.map { it.file.substringBeforeLast('.') },
                    currentIndex = idx,
                    onSelectIndex = { i -> goTo(playlist.getOrNull(i)) },
                    onPrev = { goTo(prevEp) },
                    onNext = { goTo(nextEp) },
                    onClose = onClose,
                    onEnded = { ended = true },
                    onDownload = downloadAction,
                    saveProgress = { pos, dur -> AppRepo.saveProgress(request.serverId, curPath, file, pos, dur, System.currentTimeMillis(), coverUrl) },
                )
            }
        }
    }
}
