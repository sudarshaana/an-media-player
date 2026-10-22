package xyz.devnerd.anmediaplayer.data

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import xyz.devnerd.anmediaplayer.data.source.HttpMediaSource

private val Context.repoStore: DataStore<Preferences> by preferencesDataStore(name = "repo")

/** key = "server|path/file" */
fun progressKey(server: String, path: List<String>, file: String): String =
    "$server|${(path + file).joinToString("/")}"

data class ProgressRecord(
    val server: String,
    val path: List<String>,
    val file: String,
    val pos: Int,
    val dur: Int,
    val updated: Long,
    val cover: String? = null,
)

/**
 * App-wide persisted state: saved servers, bookmarks, playback progress.
 * Snapshot-backed so Compose recomposes; writes flush to DataStore.
 * Replaces the old in-memory fake seeds.
 */
object AppRepo {
    private lateinit var ctx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val KEY_SERVERS = stringPreferencesKey("servers")
    private val KEY_BOOKMARKS = stringPreferencesKey("bookmarks")
    private val KEY_PROGRESS = stringPreferencesKey("progress")
    private val KEY_SHORTCUTS = stringPreferencesKey("shortcuts")

    val servers = mutableStateListOf<Server>()
    val bookmarks = mutableStateListOf<Bookmark>()
    val progress = mutableStateMapOf<String, ProgressRecord>()
    val shortcuts = mutableStateListOf<Shortcut>()

    fun init(context: Context) {
        ctx = context.applicationContext
        // initial synchronous load so first frame has data
        runBlocking {
            val prefs = ctx.repoStore.data.first()
            servers.addAll(parseServers(prefs[KEY_SERVERS]))
            bookmarks.addAll(parseBookmarks(prefs[KEY_BOOKMARKS]))
            progress.putAll(parseProgress(prefs[KEY_PROGRESS]))
            shortcuts.addAll(parseShortcuts(prefs[KEY_SHORTCUTS]))
        }
    }

    // ── shortcuts (pinned folders on Home) ──
    fun isShortcut(server: String, path: List<String>) = shortcuts.any { it.server == server && it.path == path }
    fun addShortcut(server: String, path: List<String>, name: String) {
        if (isShortcut(server, path)) return
        shortcuts.add(Shortcut(server, path, name)); persistShortcuts()
    }
    fun removeShortcut(key: String) { shortcuts.removeAll { it.key == key }; persistShortcuts() }
    fun renameShortcut(key: String, name: String) {
        val i = shortcuts.indexOfFirst { it.key == key }
        if (i >= 0) { shortcuts[i] = shortcuts[i].copy(name = name); persistShortcuts() }
    }
    private fun persistShortcuts() {
        val snap = shortcuts.toList()
        scope.launch { ctx.repoStore.edit { it[KEY_SHORTCUTS] = serializeShortcuts(snap) } }
    }
    private fun serializeShortcuts(list: List<Shortcut>) = JSONArray().apply {
        list.forEach { put(JSONObject().apply { put("server", it.server); put("path", JSONArray(it.path)); put("name", it.name) }) }
    }.toString()
    private fun parseShortcuts(raw: String?): List<Shortcut> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i); val pa = o.getJSONArray("path")
                Shortcut(o.getString("server"), (0 until pa.length()).map { pa.getString(it) }, o.getString("name"))
            }
        }.getOrDefault(emptyList())
    }

    // ── servers ──
    /** Ephemeral servers for "Browse without saving" — resolvable by id, not persisted, not shown in the Servers list. */
    private val transient = mutableStateMapOf<String, Server>()

    fun addServer(s: Server) { servers.removeAll { it.id == s.id }; servers.add(s); persistServers() }
    fun removeServer(id: String) { servers.removeAll { it.id == id }; persistServers() }
    fun toggleFavorite(id: String) {
        val i = servers.indexOfFirst { it.id == id }
        if (i >= 0) { servers[i] = servers[i].copy(favorite = !servers[i].favorite); persistServers() }
    }
    /** Register a server resolvable for browsing only — no persistence, hidden from the saved list. */
    fun addTransient(s: Server) { if (servers.none { it.id == s.id }) transient[s.id] = s }
    fun serverById(id: String) = servers.firstOrNull { it.id == id } ?: transient[id]
    fun isSaved(id: String) = servers.any { it.id == id }

    private fun persistServers() {
        val snapshot = servers.toList()
        scope.launch { ctx.repoStore.edit { it[KEY_SERVERS] = serializeServers(snapshot) } }
    }

    // ── bookmarks ──
    fun isBookmarked(server: String, path: List<String>) = bookmarks.any { it.server == server && it.path == path }
    fun toggleBookmark(server: String, path: List<String>) {
        val i = bookmarks.indexOfFirst { it.server == server && it.path == path }
        if (i >= 0) bookmarks.removeAt(i) else bookmarks.add(Bookmark(server, path))
        val snapshot = bookmarks.toList()
        scope.launch { ctx.repoStore.edit { it[KEY_BOOKMARKS] = serializeBookmarks(snapshot) } }
    }

    // ── progress ──
    fun getProgress(key: String): Int = progress[key]?.pos ?: 0
    /** Duration stored with the last save (0 if none) — fallback when a listing has no durSec. */
    fun getProgressDur(key: String): Int = progress[key]?.dur ?: 0
    fun isWatched(key: String, dur: Int?): Boolean {
        if (dur == null || dur == 0) return false
        val p = progress[key] ?: return false
        return p.pos.toFloat() / dur >= 0.92f
    }
    fun saveProgress(server: String, path: List<String>, file: String, pos: Int, dur: Int, nowMs: Long, cover: String? = null) {
        val key = progressKey(server, path, file)
        val keepCover = cover ?: progress[key]?.cover
        progress[key] = ProgressRecord(server, path, file, pos, dur, nowMs, keepCover)
        val snapshot = progress.values.toList()
        scope.launch { ctx.repoStore.edit { it[KEY_PROGRESS] = serializeProgress(snapshot) } }
    }

    /** In-progress items for the Continue-watching shelf, newest first. */
    fun continueItems(): List<ContinueItem> = progress.values
        .filter { it.dur > 0 && it.pos > 5 && it.pos.toFloat() / it.dur < 0.96f }
        .sortedByDescending { it.updated }
        .take(50)
        .map {
            val np = prettyName(it.file)
            ContinueItem(it.server, it.path, it.file, it.pos, it.dur, np.primary, np.secondary, if (np.ep != null) "episode" else "movie", it.cover)
        }

    // ── (de)serialization ──
    private fun serializeServers(list: List<Server>) = JSONArray().apply {
        list.forEach { s ->
            put(JSONObject().apply {
                put("id", s.id); put("name", s.name); put("url", s.url); put("protocol", s.protocol)
                put("auth", s.auth); put("user", s.user ?: JSONObject.NULL); put("password", s.password ?: JSONObject.NULL)
                put("parser", s.parser); put("lastUsed", s.lastUsed); put("favorite", s.favorite)
            })
        }
    }.toString()

    private fun parseServers(raw: String?): List<Server> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Server(
                            id = o.getString("id"), name = o.getString("name"), url = o.getString("url"),
                            protocol = o.optString("protocol", "HTTP"), auth = o.optBoolean("auth", false),
                            user = o.optString("user").ifBlank { null }, password = o.optString("password").ifBlank { null },
                            parser = o.optString("parser", "auto"), lastUsed = o.optString("lastUsed", ""),
                            favorite = o.optBoolean("favorite", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeBookmarks(list: List<Bookmark>) = JSONArray().apply {
        list.forEach { put(JSONObject().apply { put("server", it.server); put("path", JSONArray(it.path)) }) }
    }.toString()

    private fun parseBookmarks(raw: String?): List<Bookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val p = o.getJSONArray("path")
                    add(Bookmark(o.getString("server"), List(p.length()) { p.getString(it) }))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeProgress(list: List<ProgressRecord>) = JSONArray().apply {
        list.forEach { r ->
            put(JSONObject().apply {
                put("server", r.server); put("path", JSONArray(r.path)); put("file", r.file)
                put("pos", r.pos); put("dur", r.dur); put("updated", r.updated); put("cover", r.cover ?: JSONObject.NULL)
            })
        }
    }.toString()

    private fun parseProgress(raw: String?): Map<String, ProgressRecord> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            buildMap {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val p = o.getJSONArray("path")
                    val path = List(p.length()) { p.getString(it) }
                    val r = ProgressRecord(o.getString("server"), path, o.getString("file"), o.getInt("pos"), o.getInt("dur"), o.optLong("updated"), o.optString("cover").ifBlank { null })
                    put(progressKey(r.server, r.path, r.file), r)
                }
            }
        }.getOrDefault(emptyMap())
    }
}

/** Listing facade over the HTTP source, resolving servers from AppRepo. */
object MediaRepo {
    private val http = HttpMediaSource()

    suspend fun list(serverId: String, path: List<String>): List<Entry> {
        val s = AppRepo.serverById(serverId) ?: error("Unknown server")
        return http.list(s, path)
    }

    fun fileUrl(serverId: String, path: List<String>, file: String): String {
        val s = AppRepo.serverById(serverId) ?: return ""
        return http.fileUrl(s, path, file)
    }

    /** URL of the first image inside a folder (poster/cover), or null. */
    suspend fun firstImageUrl(serverId: String, path: List<String>): String? {
        val s = AppRepo.serverById(serverId) ?: return null
        val entries = runCatching { http.list(s, path) }.getOrDefault(emptyList())
        val img = entries.firstOrNull { it.type == EntryType.IMAGE } ?: return null
        return http.fileUrl(s, path, img.name)
    }

    /** Poster for a media file's folder: image in its folder, else walk up parents. */
    suspend fun posterUrl(serverId: String, path: List<String>, levelsUp: Int = 1): String? {
        var p = path
        var up = 0
        while (true) {
            firstImageUrl(serverId, p)?.let { return it }
            if (p.isEmpty() || up >= levelsUp) return null
            p = p.dropLast(1); up++
        }
    }
}

/** Lazy cache of folder cover-image URLs (snapshot-backed for Compose). */
object ThumbCache {
    private val cache = mutableStateMapOf<String, String?>()
    private val inflight = mutableSetOf<String>()

    fun key(serverId: String, path: List<String>) = "$serverId|${path.joinToString("/")}"
    fun cached(key: String): String? = cache[key]
    fun has(key: String): Boolean = cache.containsKey(key)

    suspend fun resolve(serverId: String, path: List<String>) {
        val k = key(serverId, path)
        if (cache.containsKey(k) || k in inflight) return
        inflight.add(k)
        cache[k] = runCatching { MediaRepo.firstImageUrl(serverId, path) }.getOrNull()
        inflight.remove(k)
    }
}

/** Outcome of [DownloadsStore.enqueue], so callers can tell the user what actually happened. */
enum class EnqueueResult {
    ADDED, RETRIED, ALREADY;

    fun toastText() = when (this) {
        ADDED -> if (DownloadsStore.waitingForWifi) "Queued — waiting for Wi-Fi" else "Added to downloads"
        RETRIED -> "Retrying download"
        ALREADY -> "Already in downloads"
    }
}

/** Pausable downloads via OkHttp (HTTP Range resume) → app external Movies dir. Persisted. */
object DownloadsStore {
    val items = mutableStateListOf<Download>()
    /** True while "Wi-Fi only" is on and the active network is metered — queued items wait. */
    var waitingForWifi by mutableStateOf(false)
        private set
    private lateinit var appCtx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    // In-flight HTTP calls — cancelling one unblocks a pending socket read immediately (coroutine cancel alone can't).
    private val calls = java.util.concurrent.ConcurrentHashMap<String, okhttp3.Call>()
    private val client = okhttp3.OkHttpClient()
    private val KEY_DOWNLOADS = stringPreferencesKey("downloads")
    @Volatile private var wifiOnly = true
    @Volatile private var unmetered = true
    /** Concurrent transfers; the rest wait as QUEUED and start, oldest first, as slots free up. */
    private const val MAX_ACTIVE = 2
    // Guards queue decisions (start/pump/pause-all) — job completions arrive on IO threads.
    private val queueLock = Any()
    private var lastNotifyAt = 0L

    fun init(context: Context) {
        appCtx = context.applicationContext
        runBlocking { items.addAll(parseDownloads(appCtx.repoStore.data.first()[KEY_DOWNLOADS])) }
        // Jobs don't survive process death — show interrupted ones as paused (resumable). QUEUED stays queued.
        items.toList().forEach { if (it.state == DownloadState.DOWNLOADING) update(it.id, DownloadState.PAUSED) }
        scope.launch {
            xyz.devnerd.anmediaplayer.settings.SettingsRepository(appCtx).settings
                .map { it.wifiOnly }.distinctUntilChanged()
                .collect { wifiOnly = it; onNetworkPolicyChanged() }
        }
        val cm = appCtx.getSystemService(android.net.ConnectivityManager::class.java)
        runCatching {
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                    unmetered = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    onNetworkPolicyChanged()
                }
                override fun onLost(network: android.net.Network) { unmetered = false; onNetworkPolicyChanged() }
            })
        }
    }

    private fun networkAllowed() = !wifiOnly || unmetered

    /** Wi-Fi gate flipped: park running transfers as QUEUED, or kick off everything that was waiting. */
    private fun onNetworkPolicyChanged() = synchronized(queueLock) {
        val allowed = networkAllowed()
        waitingForWifi = !allowed
        if (allowed) {
            pumpQueue()
        } else {
            items.filter { it.state == DownloadState.DOWNLOADING }.forEach { stopTransfer(it.id); update(it.id, DownloadState.QUEUED) }
        }
    }

    /** Fill free transfer slots from the queue, oldest first. */
    private fun pumpQueue() = synchronized(queueLock) {
        if (!networkAllowed()) return
        for (q in items.filter { it.state == DownloadState.QUEUED }) {
            if (items.count { it.state == DownloadState.DOWNLOADING } >= MAX_ACTIVE) break
            start(q.id)
        }
    }

    private fun persist() {
        if (!::appCtx.isInitialized) return
        val snap = items.toList()
        scope.launch { appCtx.repoStore.edit { it[KEY_DOWNLOADS] = serializeDownloads(snap) } }
    }

    // Strip only path separators / filesystem-illegal chars; keep unicode (CJK, Bengali, accents, …) intact.
    private fun safeName(file: String) = file.replace(Regex("[\\x00-\\x1f/\\\\:*?\"<>|]"), "_")
    private fun moviesDir() = appCtx.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)

    /** Partial/working file, unique per download (same filename in two folders must not share bytes). */
    private fun partFor(d: Download): java.io.File {
        val f = java.io.File(moviesDir(), "${d.id.removePrefix("dl_")}_${safeName(d.file)}")
        // Older builds named the partial after the file alone — adopt it so paused items keep their bytes.
        val legacy = java.io.File(moviesDir(), safeName(d.file))
        if (!f.exists() && legacy.exists() && d.state != DownloadState.DONE) legacy.renameTo(f)
        return f
    }

    private fun idFor(server: String, path: List<String>, file: String) = "dl_${(server + path.joinToString("/") + file).hashCode()}"

    fun enqueue(ctx: Context, server: String, path: List<String>, file: String, title: String, sub: String, size: Long, durSec: Int, url: String, destDir: String? = null): EnqueueResult {
        if (url.isBlank()) return EnqueueResult.ALREADY
        val id = idFor(server, path, file)
        items.firstOrNull { it.id == id }?.let { existing ->
            if (existing.state != DownloadState.FAILED) return EnqueueResult.ALREADY
            resume(id); return EnqueueResult.RETRIED
        }
        items.add(Download(id, title, sub, file, size, DownloadState.QUEUED, 0, 0L, server, path, durSec, url, 0, null, destDir))
        persist(); start(id)
        // Resolve a poster (folder image, walking up to the show root) for the list thumbnail.
        scope.launch { MediaRepo.posterUrl(server, path)?.let { updateCover(id, it) } }
        return EnqueueResult.ADDED
    }

    private fun updateCover(id: String, coverUrl: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        items[i] = items[i].copy(coverUrl = coverUrl)
        persist()
    }

    /** Cancel the coroutine and its HTTP call; the job itself removes its [jobs] entry when it unwinds. */
    private fun stopTransfer(id: String) {
        jobs[id]?.cancel()
        calls.remove(id)?.cancel()
    }

    fun pause(id: String) { stopTransfer(id); update(id, DownloadState.PAUSED); maybeStopService() }
    fun resume(id: String) { DownloadService.cancelResult(appCtx, id); start(id) }

    /** Notification "Pause" — hold everything running or waiting, so a freed slot doesn't start the next one. */
    fun pauseAll() = synchronized(queueLock) {
        items.filter { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED }.forEach { pause(it.id) }
    }

    /** Wipe any existing bytes (partial or completed) and re-download from scratch. */
    fun restart(ctx: Context, id: String) {
        stopTransfer(id)
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        val item = items[i]
        deleteBytes(ctx, item, includeFinished = true)
        items[i] = item.copy(state = DownloadState.QUEUED, progress = 0, downloadedBytes = 0, localUri = null, completedAt = 0)
        persist()
        start(id)
    }

    private fun start(id: String): Unit = synchronized(queueLock) {
        val cur = items.firstOrNull { it.id == id } ?: return
        if (cur.state == DownloadState.DOWNLOADING && jobs[id]?.isActive == true) return
        val busy = items.count { it.state == DownloadState.DOWNLOADING && it.id != id }
        if (!networkAllowed() || busy >= MAX_ACTIVE) { update(id, DownloadState.QUEUED); return }
        update(id, DownloadState.DOWNLOADING)
        maybeStartService()
        // A just-cancelled job may still be unwinding (closing its file) — wait for it so two writers never share the partial.
        val prev = jobs[id]
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            prev?.join()
            runCatching { downloadLoop(id) }.onFailure {
                // A cancelled call surfaces as IOException — only a live job's error is a real failure.
                if (it !is kotlinx.coroutines.CancellationException && kotlinx.coroutines.currentCoroutineContext().isActive) update(id, DownloadState.FAILED)
            }
        }
        jobs[id] = job
        job.invokeOnCompletion { jobs.remove(id, job); pumpQueue(); maybeStopService() }
        job.start()
    }

    /** Foreground service exempts the transfer from background process freezing/network throttling. */
    private fun maybeStartService() {
        if (::appCtx.isInitialized) ContextCompat.startForegroundService(appCtx, Intent(appCtx, DownloadService::class.java))
    }

    private fun maybeStopService() {
        if (jobs.values.none { it.isActive } && ::appCtx.isInitialized) appCtx.stopService(Intent(appCtx, DownloadService::class.java))
    }

    /** Ongoing notification: combined progress of running transfers. Throttled — Android drops bursts. */
    private fun refreshNotification(force: Boolean = false) {
        if (!::appCtx.isInitialized) return
        val active = items.filter { it.state == DownloadState.DOWNLOADING }
        if (active.isEmpty()) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastNotifyAt < 1000) return
        lastNotifyAt = now
        val total = active.sumOf { it.size }
        val done = active.sumOf { it.downloadedBytes }
        val pct = if (total > 0) (done * 100 / total).toInt() else active.first().progress ?: 0
        val queued = items.count { it.state == DownloadState.QUEUED }
        val title = if (active.size == 1) active.first().title else "Downloading ${active.size} files"
        val text = buildString {
            append("$pct%")
            if (total > 0) append(" · ${fmtSize(done)} of ${fmtSize(total)}")
            if (active.size == 1 && active.first().sub.isNotBlank() && active.first().sub != active.first().file) append(" · ${active.first().sub}")
            if (queued > 0) append(" · $queued queued")
        }
        DownloadService.notifyProgress(appCtx, title, text, pct, indeterminate = total <= 0 && pct == 0, pauseLabel = if (active.size + queued > 1) "Pause all" else "Pause")
    }

    private suspend fun downloadLoop(id: String) {
        val d = items.firstOrNull { it.id == id } ?: return
        val out = partFor(d)
        val have = if (out.exists()) out.length() else 0L
        val req = okhttp3.Request.Builder().url(d.url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        val call = client.newCall(req).also { calls[id] = it }
        val job = kotlinx.coroutines.currentCoroutineContext()
        val active = { job.isActive }
        try {
            transfer(d, out, have, call, active)
        } finally {
            calls.remove(id, call)
        }
    }

    private fun transfer(d: Download, out: java.io.File, have: Long, call: okhttp3.Call, active: () -> Boolean) {
        val id = d.id
        call.execute().use { resp ->
            // 416 on a resume = nothing left past our byte count: the partial is already whole.
            if (resp.code == 416 && have > 0) { finish(d, out, have); return }
            if (!resp.isSuccessful) { update(id, DownloadState.FAILED); return }
            val body = resp.body ?: run { update(id, DownloadState.FAILED); return }
            val append = resp.code == 206
            val base = if (append) have else 0L
            // Only the server's Content-Length is exact; listing sizes (h5ai "1550541 KB") are rounded.
            val expected = body.contentLength().takeIf { it > 0 }?.let { base + it }
            val total = expected ?: d.size
            var written = base
            java.io.FileOutputStream(out, append).use { sink ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var lastTick = base
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        if (!active()) { sink.flush(); return } // paused/cancelled → keep partial
                        sink.write(buf, 0, n); written += n
                        if (written - lastTick > 512 * 1024) {
                            lastTick = written
                            val pct = if (total > 0) (written * 100 / total).toInt().coerceIn(0, 100) else 0
                            reportProgress(id, pct, written)
                        }
                    }
                    sink.flush()
                }
            }
            if (!active()) return
            // Stream ended short of Content-Length → truncated; fail (Retry resumes from the partial).
            if (expected != null && written < expected) throw java.io.IOException("Truncated: $written of $expected bytes")
            finish(d, out, written)
        }
    }

    /** Move the completed partial to its final home and mark DONE. */
    private fun finish(d: Download, out: java.io.File, bytes: Long) {
        // Move into the user's chosen folder (SAF) if set; else into the public Downloads/AnMediaPlayer folder.
        val finalUri = d.destDir?.let { tree ->
            runCatching {
                val dir = androidx.documentfile.provider.DocumentFile.fromTreeUri(appCtx, android.net.Uri.parse(tree)) ?: return@runCatching null
                val doc = dir.createFile(mimeFor(d.file), d.file) ?: return@runCatching null
                appCtx.contentResolver.openOutputStream(doc.uri)?.use { os -> out.inputStream().use { it.copyTo(os) } }
                out.delete()
                doc.uri.toString()
            }.getOrNull()
        } ?: moveToPublicDownloads(out, d.file)
        update(d.id, DownloadState.DONE, 100, System.currentTimeMillis(), localUri = finalUri, downloadedBytes = bytes)
    }

    /** No custom folder picked → land in the shared Download/AnMediaPlayer folder (visible to other apps), unicode name preserved. */
    private fun moveToPublicDownloads(src: java.io.File, displayName: String): String {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeFor(displayName))
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/AnMediaPlayer")
                }
                val uri = appCtx.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching null
                appCtx.contentResolver.openOutputStream(uri)?.use { os -> src.inputStream().use { it.copyTo(os) } }
                src.delete()
                uri.toString()
            } else {
                @Suppress("DEPRECATION")
                val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "AnMediaPlayer").apply { mkdirs() }
                val dest = java.io.File(dir, displayName)
                src.copyTo(dest, overwrite = true)
                src.delete()
                android.net.Uri.fromFile(dest).toString()
            }
        }.getOrNull() ?: android.net.Uri.fromFile(src).toString()
    }

    private fun mimeFor(file: String) = when (file.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"; "mkv" -> "video/x-matroska"; "avi" -> "video/x-msvideo"; "webm" -> "video/webm"
        "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "srt" -> "application/x-subrip"
        else -> "application/octet-stream"
    }

    /** Progress tick from a transfer — dropped unless still DOWNLOADING, so a late tick can't undo a pause. */
    private fun reportProgress(id: String, pct: Int, written: Long) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0 || items[i].state != DownloadState.DOWNLOADING) return
        items[i] = items[i].copy(progress = pct, downloadedBytes = written)
        refreshNotification()
    }

    private fun update(id: String, state: DownloadState, progress: Int? = null, completedAt: Long? = null, localUri: String? = null, downloadedBytes: Long? = null) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        val prev = items[i]
        items[i] = prev.copy(
            state = state,
            progress = progress ?: prev.progress,
            completedAt = completedAt ?: prev.completedAt,
            localUri = localUri ?: prev.localUri,
            downloadedBytes = downloadedBytes ?: prev.downloadedBytes,
        )
        if (prev.state != state || prev.localUri != items[i].localUri) persist()
        if (state == DownloadState.DOWNLOADING) refreshNotification(force = prev.state != state)
        if (prev.state == DownloadState.DOWNLOADING && (state == DownloadState.DONE || state == DownloadState.FAILED)) {
            DownloadService.notifyResult(appCtx, items[i], ok = state == DownloadState.DONE)
        }
    }

    /** A Local Files delete already removed the file via MediaStore — drop the matching Downloads row, file already gone. */
    fun removeByLocalPath(ctx: Context, path: String) {
        val match = items.firstOrNull { d -> d.localUri?.startsWith("file://") == true && android.net.Uri.parse(d.localUri).path == path }
        match?.let { remove(ctx, it.id, deleteFile = false) }
    }

    /** Delete the partial (always safe) and, if [includeFinished], the completed file wherever it landed. */
    private fun deleteBytes(ctx: Context, item: Download, includeFinished: Boolean) {
        runCatching { partFor(item).delete() }
        if (!includeFinished) return
        val uri = item.localUri ?: return
        when {
            uri.startsWith("content://") -> runCatching { ctx.contentResolver.delete(android.net.Uri.parse(uri), null, null) }
            uri.startsWith("file://") -> runCatching { android.net.Uri.parse(uri).path?.let { java.io.File(it).delete() } }
        }
    }

    fun remove(ctx: Context, id: String, deleteFile: Boolean = false) {
        stopTransfer(id)
        DownloadService.cancelResult(ctx, id)
        val item = items.firstOrNull { it.id == id }
        // A partial is useless once its row is gone — always drop it; finished files only when asked.
        if (item != null) deleteBytes(ctx, item, includeFinished = deleteFile)
        items.removeAll { it.id == id }
        persist()
        maybeStopService()
    }

    private fun serializeDownloads(list: List<Download>): String = JSONArray().apply {
        list.forEach { d ->
            put(JSONObject().apply {
                put("id", d.id); put("title", d.title); put("sub", d.sub); put("file", d.file)
                put("size", d.size); put("state", d.state.name); put("progress", d.progress ?: JSONObject.NULL)
                put("completedAt", d.completedAt); put("server", d.server); put("path", JSONArray(d.path))
                put("durSec", d.durSec); put("url", d.url); put("downloadedBytes", d.downloadedBytes); put("localUri", d.localUri ?: JSONObject.NULL); put("destDir", d.destDir ?: JSONObject.NULL); put("coverUrl", d.coverUrl ?: JSONObject.NULL)
            })
        }
    }.toString()

    private fun parseDownloads(s: String?): List<Download> {
        if (s.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(s)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val pathArr = o.getJSONArray("path")
                val path = (0 until pathArr.length()).map { pathArr.getString(it) }
                Download(
                    o.getString("id"), o.getString("title"), o.getString("sub"), o.getString("file"),
                    o.getLong("size"), DownloadState.valueOf(o.getString("state")),
                    if (o.isNull("progress")) null else o.getInt("progress"),
                    o.optLong("completedAt", 0), o.getString("server"), path, o.getInt("durSec"),
                    o.optString("url"), o.optLong("downloadedBytes", 0),
                    o.optString("localUri").ifBlank { null },
                    o.optString("destDir").ifBlank { null },
                    o.optString("coverUrl").ifBlank { null },
                )
            }
        }.getOrDefault(emptyList())
    }
}
